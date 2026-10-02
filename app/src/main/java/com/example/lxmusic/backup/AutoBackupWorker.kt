package com.example.lxmusic.backup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.lxmusic.BuildConfig
import com.example.lxmusic.MainActivity
import com.example.lxmusic.MusicDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 备份触发来源，决定文件名前缀 */
enum class BackupReason(val title: String) {
    AUTO("自动备份"),
    MANUAL("手动备份"),
    BEFORE_UPDATE("更新前备份")
}

sealed class BackupOutcome {
    data class Success(val fileName: String, val location: String, val summary: String) : BackupOutcome()
    data class Failure(val reason: String) : BackupOutcome()
}

/** 备份执行核心：导出到 Download/LxMusic 并记录结果（Worker 与「立即备份」共用） */
object AutoBackupRunner {
    private val stampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    suspend fun run(context: Context, reason: BackupReason, notify: Boolean = true): BackupOutcome {
        val dao = MusicDatabase.getDatabase(context).collectionDao()
        val stamp = stampFormat.format(Date())
        val fileName = when (reason) {
            BackupReason.AUTO -> "lxmusic_backup_$stamp.json"
            BackupReason.MANUAL -> "lxmusic_backup_manual_$stamp.json"
            BackupReason.BEFORE_UPDATE ->
                "lxmusic_backup_before_update_v${BuildConfig.VERSION_NAME}_$stamp.json"
        }

        return when (val result = BackupStorage.writeBackup(context, fileName, dao)) {
            is BackupStorage.WriteResult.Success -> {
                // 只保留最近若干份，避免长期占用用户存储
                runCatching { BackupStorage.pruneOldBackups(context) }
                val stats = runCatching { com.example.lxmusic.CollectionBackupIO.stats(dao) }.getOrNull()
                val summary = stats?.summary ?: ""
                AutoBackupScheduler.recordResult(context, success = true, message = result.locationLabel)
                if (notify) notifyFinished(context, reason, success = true, location = result.locationLabel)
                BackupOutcome.Success(result.displayName, result.locationLabel, summary)
            }
            is BackupStorage.WriteResult.Failure -> {
                AutoBackupScheduler.recordResult(context, success = false, message = result.reason)
                if (notify) notifyFinished(context, reason, success = false, failure = result.reason)
                BackupOutcome.Failure(result.reason)
            }
        }
    }

    private fun notifyFinished(
        context: Context,
        backupReason: BackupReason,
        success: Boolean,
        location: String = "",
        failure: String = ""
    ) {
        val text = if (success) {
            "本地收藏已备份到 $location"
        } else {
            "备份失败：$failure。可在 设置-通用设置 中手动导出"
        }
        postNotification(context, "${backupReason.title}${if (success) "完成" else "失败"}", text)
    }

    private fun postNotification(context: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "本地收藏自动备份",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "定时备份本地收藏数据到下载目录" }
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private const val CHANNEL_ID = "lxmusic_auto_backup"
    private const val NOTIFICATION_ID = 0x1301
}

/** 周期任务：到点静默导出到 Download/LxMusic */
class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!AutoBackupScheduler.isEnabled(context)) return Result.success()
        return when (val outcome = AutoBackupRunner.run(context, BackupReason.AUTO)) {
            is BackupOutcome.Success -> Result.success()
            is BackupOutcome.Failure ->
                // 权限类问题重试也没用，直接失败等下一周期；其它（IO/网络存储抖动）允许重试
                if (outcome.reason.contains("权限")) Result.failure() else Result.retry()
        }
    }
}
