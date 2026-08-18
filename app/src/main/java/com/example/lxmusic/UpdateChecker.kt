package com.example.lxmusic

import android.content.Context
import android.content.Intent
import android.net.Uri
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
     * 下载 APK 到应用缓存目录（带进度与大小统计）
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LxMusic-Android-App")
                .build()
            downloadClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "下载失败: HTTP ${resp.code}")
                    return@withContext null
                }
                val total = resp.body?.contentLength() ?: -1L
                val file = File(context.cacheDir, "lxmusic_update.apk")
                if (file.exists()) file.delete()

                resp.body?.byteStream()?.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(16384)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
                if (file.length() > 0) file else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载异常: ${e.message}")
            null
        }
    }

    /**
     * 调用系统包安装器安装 APK
     */
    fun installApk(context: Context, apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
