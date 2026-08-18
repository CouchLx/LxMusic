package com.example.lxmusic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 完整更新信息数据结构
 */
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String,
    val title: String = "",
    val desc: String = "",
    val apkUrl: String = "",
    val apkUrls: List<String> = emptyList(),
    val releaseUrl: String = "https://github.com/CouchLx/LxMusic/releases",
    val assetSize: Long = 0L,
    val publishedAt: String = ""
)

/**
 * 应用更新检查器
 *
 * 优先从 GitHub 官方 Releases API / 国内加速镜像获取最新 Release 发布日志与 APK，
 * 并支持 publish/version.json 兜底。
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val PREF_NAME = "update_prefs"
    private const val KEY_IGNORED_VERSION = "ignored_version"

    const val GITHUB_OWNER = "CouchLx"
    const val GITHUB_REPO = "LxMusic"
    const val REPO_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"
    const val RELEASES_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"

    /** GitHub Releases API 探测地址列表（含直连与主流镜像代理） */
    private val releaseApiUrls: List<String> by lazy {
        listOf(
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest",
            "https://ghfast.top/https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest",
            "https://gh-proxy.com/https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        )
    }

    /** publish/version.json 兜底探测地址 */
    private val versionJsonUrls: List<String> by lazy {
        buildList {
            BuildConfig.LX_UPDATE_VERSION_URL.takeIf { it.isNotBlank() }?.let { add(it) }
            add("https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/master/publish/version.json")
            add("https://cdn.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@master/publish/version.json")
            add("https://ghfast.top/https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/master/publish/version.json")
            add("https://gh-proxy.com/https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/master/publish/version.json")
        }
    }

    private val checkClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 语义化版本比对：若 remoteVersion > localVersion 返回 true
     */
    fun isNewerVersion(remoteVersion: String, localVersion: String): Boolean {
        val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V").trim()
        val cleanLocal = localVersion.trim().removePrefix("v").removePrefix("V").trim()
        if (cleanRemote.isBlank()) return false
        if (cleanRemote == cleanLocal) return false

        val remoteParts = cleanRemote.split('.').mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }
        val localParts = cleanLocal.split('.').mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, localParts.size)

        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    /**
     * 检查版本是否被用户设置为「不再提示」
     */
    fun isVersionIgnored(context: Context, versionName: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val ignored = prefs.getString(KEY_IGNORED_VERSION, null) ?: return false
        val cleanIgnored = ignored.trim().removePrefix("v").removePrefix("V")
        val cleanTarget = versionName.trim().removePrefix("v").removePrefix("V")
        return cleanIgnored.isNotBlank() && cleanIgnored == cleanTarget
    }

    /**
     * 设置某个版本为「不再提示」
     */
    fun setVersionIgnored(context: Context, versionName: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_IGNORED_VERSION, versionName).apply()
    }

    /**
     * 检查是否有新版本（优先 GitHub Releases API，次选 version.json）
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        // 1. 尝试从 GitHub Releases API 解析
        for (url in releaseApiUrls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "LxMusic-Android-App")
                    .build()
                checkClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string().orEmpty()
                        val json = JSONObject(bodyStr)
                        val tagName = json.optString("tag_name", "").trim()
                        val releaseTitle = json.optString("name", "").ifBlank { tagName }
                        val releaseBody = json.optString("body", "").trim()
                        val htmlUrl = json.optString("html_url", RELEASES_URL)
                        val publishedAt = json.optString("published_at", "")

                        // 查找 assets 里的 .apk 文件
                        var apkDownloadUrl = ""
                        var assetSize = 0L
                        val assetsArray = json.optJSONArray("assets") ?: JSONArray()
                        for (i in 0 until assetsArray.length()) {
                            val asset = assetsArray.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkDownloadUrl = asset.optString("browser_download_url", "")
                                assetSize = asset.optLong("size", 0L)
                                break
                            }
                        }

                        // 如果没有找到直链 APK，兜底使用 release 主页
                        if (apkDownloadUrl.isBlank()) {
                            apkDownloadUrl = htmlUrl
                        }

                        val hasNew = isNewerVersion(tagName, BuildConfig.VERSION_NAME)
                        if (hasNew) {
                            return@withContext UpdateInfo(
                                versionCode = 0,
                                versionName = tagName.removePrefix("v").removePrefix("V"),
                                title = releaseTitle,
                                desc = releaseBody,
                                apkUrl = apkDownloadUrl,
                                apkUrls = buildMirrorUrls(apkDownloadUrl),
                                releaseUrl = htmlUrl,
                                assetSize = assetSize,
                                publishedAt = publishedAt
                            )
                        } else {
                            return@withContext null // 已是最新版本
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "GitHub Releases API 探测失败: $url - ${e.message}")
            }
        }

        // 2. 兜底尝试 publish/version.json
        for (url in versionJsonUrls) {
            try {
                val request = Request.Builder().url(url).build()
                checkClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val vCode = json.optInt("versionCode", 0)
                        val vName = json.optString("versionName", "")
                        val desc = json.optString("desc", "")
                        val apkUrl = json.optString("apkUrl", "")

                        val isNew = (vCode > BuildConfig.VERSION_CODE) || isNewerVersion(vName, BuildConfig.VERSION_NAME)
                        if (isNew) {
                            return@withContext UpdateInfo(
                                versionCode = vCode,
                                versionName = vName.removePrefix("v").removePrefix("V"),
                                title = "v$vName",
                                desc = desc,
                                apkUrl = apkUrl,
                                apkUrls = parseApkMirrors(json, apkUrl),
                                releaseUrl = RELEASES_URL
                            )
                        }
                        return@withContext null
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "version.json 探测失败: $url - ${e.message}")
            }
        }

        null
    }

    /**
     * 从 GitHub 直链生成国内加速镜像地址列表（直链在最前，镜像兜底）
     * 顺序按实测连通性排列：gh-proxy.com / ghproxy.net 国内多数网络可连，
     * ghfast.top / gh.llkk.cc 部分网络超时放后面。
     */
    fun buildMirrorUrls(directUrl: String): List<String> {
        if (directUrl.isBlank()) return emptyList()
        val result = mutableListOf(directUrl)
        // 常见 GitHub 加速镜像（前缀代理方式），实测可用性从高到低
        val mirrors = listOf(
            "https://gh-proxy.com/",
            "https://ghproxy.net/",
            "https://ghfast.top/",
            "https://gh.llkk.cc/",
            "https://mirror.ghproxy.com/"
        )
        for (mirror in mirrors) {
            val candidate = mirror + directUrl
            if (candidate != directUrl && result.none { it == candidate }) {
                result.add(candidate)
            }
        }
        return result
    }

    /**
     * 解析 version.json 中的 apkMirrors 字段（可选的额外镜像列表），
     * 并自动合并 GitHub 加速镜像。
     */
    private fun parseApkMirrors(json: JSONObject, directUrl: String): List<String> {
        val mirrors = buildMirrorUrls(directUrl).toMutableList()
        val arr = json.optJSONArray("apkMirrors")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val url = arr.optString(i, "").trim()
                if (url.isNotBlank() && mirrors.none { it == url }) mirrors.add(url)
            }
        }
        return mirrors
    }

    /**
     * 下载 APK（多镜像 + 断点续传），下载完成后保存到公共 Download 目录
     *
     * @param urls 候选下载地址列表（按优先级排列），失败自动切换到下一个
     * @param onProgress 进度回调（IO 线程），downloaded/total 字节数
     * @return 可安装的 APK Uri（位于公共 Download 目录），全部镜像都失败返回 null
     */
    suspend fun downloadApk(
        context: Context,
        urls: List<String>,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Uri? = withContext(Dispatchers.IO) {
        // 先下载到应用缓存（支持断点续传）
        val cacheFile = File(context.cacheDir, "lxmusic_update.apk")
        if (cacheFile.exists()) cacheFile.delete()

        var lastError: Exception? = null

        for ((index, url) in urls.withIndex()) {
            // 断点续传：若已有部分内容且不是第一轮，从已下载大小继续
            var resumeFrom = if (index > 0 && cacheFile.exists()) cacheFile.length() else 0L
            try {
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "LxMusic-Android-App")
                if (resumeFrom > 0) {
                    requestBuilder.header("Range", "bytes=$resumeFrom-")
                }
                downloadClient.newCall(requestBuilder.build()).execute().use { resp ->
                    // 若服务器不支持 Range（返回 200 而非 206），删掉已下载部分从头开始
                    if (resumeFrom > 0 && resp.code == 200) {
                        Log.w(TAG, "镜像 $url 不支持断点续传，从头下载")
                        cacheFile.delete()
                        resumeFrom = 0L
                    } else if (!resp.isSuccessful) {
                        Log.e(TAG, "镜像 $url 下载失败: HTTP ${resp.code}")
                        return@use
                    }
                    val total = if (resumeFrom > 0) {
                        resumeFrom + (resp.body?.contentLength() ?: 0L)
                    } else {
                        resp.body?.contentLength() ?: -1L
                    }
                    resp.body?.byteStream()?.use { input ->
                        // 断点续传时用追加模式，避免覆盖已下载部分
                        java.io.FileOutputStream(cacheFile, resumeFrom > 0).use { output ->
                            val buffer = ByteArray(16384)
                            var downloaded = resumeFrom
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                onProgress(downloaded, total)
                            }
                        }
                    }
                    if (cacheFile.length() > 0) {
                        Log.i(TAG, "下载完成: $url (${cacheFile.length()} bytes)")
                        // 优先保存到公共 Download 目录；保存失败时降级用缓存文件（仍可安装）
                        val saved = saveToPublicDownload(context, cacheFile)
                        return@withContext saved ?: FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", cacheFile
                        )
                    }
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "镜像 $url 下载中断: ${e.message}（已下载 ${cacheFile.length()} 字节）")
            }
        }

        if (lastError != null) {
            Log.e(TAG, "所有镜像下载失败: ${lastError?.message}")
        }
        if (cacheFile.exists() && cacheFile.length() > 0) {
            saveToPublicDownload(context, cacheFile) ?: FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", cacheFile
            )
        } else null
    }

    /**
     * 将 APK 保存到公共 Download 目录（用户可见、可找到），返回可安装的 Uri
     *
     * - Android 10+ (API 29+)：通过 MediaStore.Downloads 写入，无需权限
     * - Android 9- (API 28-)：直接写入公共目录（需 WRITE_EXTERNAL_STORAGE 权限，
     *   无权限时降级为缓存文件 + FileProvider）
     */
    private fun saveToPublicDownload(context: Context, source: File): Uri? {
        return try {
            val fileName = "LxMusic_v${BuildConfig.VERSION_NAME}.apk"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+：MediaStore（分区存储，公共目录必须走 MediaStore）
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        source.inputStream().use { it.copyTo(out) }
                    }
                    source.delete()
                    Log.i(TAG, "APK 已保存到公共下载目录: Download/$fileName")
                    uri
                } else null
            } else {
                // Android 9-：直接写公共目录
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val dest = File(downloadsDir, fileName)
                if (dest.exists()) dest.delete()
                source.copyTo(dest, overwrite = true)
                source.delete()
                Log.i(TAG, "APK 已保存到公共下载目录: ${dest.absolutePath}")
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存到公共下载目录失败，保留缓存文件: ${e.message}")
            null
        }
    }

    /**
     * 调用系统包安装器安装 APK
     */
    fun installApk(context: Context, apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
