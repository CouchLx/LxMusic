package com.example.lxmusic.backup

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.lxmusic.CollectionBackupIO
import com.example.lxmusic.CollectionDao
import java.io.File
import java.io.FileOutputStream

/**
 * Download/LxMusic 备份目录的读写与清理。
 *
 * - Android 10+（分区存储）：走 MediaStore.Downloads，无需任何存储权限；
 * - Android 9-：直写公共下载目录，需要 WRITE_EXTERNAL_STORAGE 运行时权限，未授权直接失败并返回原因。
 */
object BackupStorage {
    const val FOLDER = "LxMusic"
    /** 自动/手动/更新前备份统一前缀，清理旧备份时只认这个前缀，不碰用户其它文件 */
    const val BACKUP_PREFIX = "lxmusic_backup_"
    /** 保留的自动备份份数 */
    const val MAX_KEEP = 20

    data class BackupFile(val name: String, val uri: Uri?, val sizeBytes: Long, val addedAt: Long)

    sealed class WriteResult {
        data class Success(val displayName: String, val locationLabel: String) : WriteResult()
        data class Failure(val reason: String) : WriteResult()
    }

    private val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER"

    /** 是否有权限写入（Android 10+ 走 MediaStore 不需要权限） */
    fun hasWriteAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 把当前本地收藏导出到 Download/LxMusic/[displayName] */
    suspend fun writeBackup(context: Context, displayName: String, dao: CollectionDao): WriteResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, displayName, dao)
        } else {
            writeDirect(context, displayName, dao)
        }
    }

    private suspend fun writeViaMediaStore(context: Context, displayName: String, dao: CollectionDao): WriteResult {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
            null
        } ?: return WriteResult.Failure("无法在下载目录创建备份文件")

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                CollectionBackupIO.export(dao, out)
            } ?: throw IllegalStateException("无法打开备份文件输出流")
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            WriteResult.Success(displayName, "下载/LxMusic/$displayName")
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            WriteResult.Failure(e.message ?: "写入备份文件失败")
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun writeDirect(context: Context, displayName: String, dao: CollectionDao): WriteResult {
        if (!hasWriteAccess(context)) return WriteResult.Failure("未授予存储权限，无法写入下载目录")
        return try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                FOLDER
            )
            if (!dir.exists() && !dir.mkdirs()) return WriteResult.Failure("无法创建 下载/LxMusic 目录")
            val file = File(dir, displayName)
            FileOutputStream(file).use { out ->
                CollectionBackupIO.export(dao, out)
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("application/json"), null)
            WriteResult.Success(displayName, file.absolutePath)
        } catch (e: Exception) {
            WriteResult.Failure(e.message ?: "写入备份文件失败")
        }
    }

    /** 列出目录内由本应用生成的备份文件，按时间倒序（最新在前） */
    fun listBackups(context: Context): List<BackupFile> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listViaMediaStore(context)
        } else {
            listDirect()
        }
    }

    private fun listViaMediaStore(context: Context): List<BackupFile> {
        val result = mutableListOf<BackupFile>()
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_ADDED
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("$relativePath%", "$BACKUP_PREFIX%")
        runCatching {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                "${MediaStore.Downloads.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)
                while (cursor.moveToNext()) {
                    result.add(
                        BackupFile(
                            name = cursor.getString(nameCol) ?: continue,
                            uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(idCol)),
                            sizeBytes = cursor.getLong(sizeCol),
                            addedAt = cursor.getLong(dateCol) * 1000L
                        )
                    )
                }
            }
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun listDirect(): List<BackupFile> {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            FOLDER
        )
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.startsWith(BACKUP_PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { BackupFile(it.name, Uri.fromFile(it), it.length(), it.lastModified()) }
            ?: emptyList()
    }

    /** 只保留最新 keep 份备份，其余删除（删除失败静默跳过，例如非本应用创建的文件） */
    fun pruneOldBackups(context: Context, keep: Int = MAX_KEEP) {
        val backups = listBackups(context)
        if (backups.size <= keep) return
        backups.drop(keep).forEach { old ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val uri = old.uri ?: return@runCatching
                    context.contentResolver.delete(uri, null, null)
                } else {
                    val path = old.uri?.path ?: return@runCatching
                    File(path).delete()
                }
            }
        }
    }
}
