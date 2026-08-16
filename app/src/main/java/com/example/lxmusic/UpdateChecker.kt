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
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** 更新信息（对应发布时生成的 publish/version.json） */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val desc: String,
    val apkUrl: String
)

/**
 * 应用更新检查器
 *
 * 借鉴 lx-music 的多镜像模式：优先使用构建时注入的自定义更新地址
 * （keystore.properties 的 LX_UPDATE_VERSION_URL），否则依次尝试
 * GitHub raw / jsdelivr / 国内加速镜像读取 publish/version.json。
 *
 * version.json 由 GitHub Actions 在发布时自动生成：
 * { "versionCode": 3, "versionName": "3.7.56", "desc": "...", "apkUrl": "..." }
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val PREF_NAME = "update_prefs"
    private const val KEY_LAST_PROMPT_VERSION = "last_prompt_version"
    private const val KEY_LAST_PROMPT_TIME = "last_prompt_time"

    /** 镜像间隔：同版本 7 天内不重复弹窗 */
    private const val PROMPT_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

    private const val GITHUB_OWNER = "CouchLx"
    private const val GITHUB_REPO = "LxMusic"

    private val mirrorUrls: List<String> by lazy {
        buildList {
            // 1. 构建注入的自定义地址（自己的服务器，最高优先级）
            BuildConfig.LX_UPDATE_VERSION_URL.takeIf { it.isNotBlank() }?.let { add(it) }
            // 2. GitHub 官方 raw
            add("https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/master/publish/version.json")
            // 3. jsdelivr CDN
            add("https://cdn.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@master/publish/version.json")
            // 4. 国内加速镜像
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
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 检查是否有新版本。逐个镜像尝试，全部失败返回 null（静默，不打扰用户）。
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        for (url in mirrorUrls) {
            try {
                val request = Request.Builder().url(url).build()
                checkClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val info = UpdateInfo(
                            versionCode = json.getInt("versionCode"),
                            versionName = json.getString("versionName"),
                            desc = json.optString("desc", ""),
                            apkUrl = json.getString("apkUrl")
                        )
                        if (info.versionCode > BuildConfig.VERSION_CODE) {
                            return@withContext info
                        }
                        return@withContext null // 已是最新
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "镜像不可用: $url - ${e.message}")
            }
        }
        null
    }

    /**
     * 是否应该弹更新提示：同版本 7 天内只提示一次（避免每次启动都打扰）。
     */
    fun shouldPrompt(context: Context, versionCode: Int): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastVersion = prefs.getInt(KEY_LAST_PROMPT_VERSION, -1)
        val lastTime = prefs.getLong(KEY_LAST_PROMPT_TIME, 0)
        if (lastVersion == versionCode && System.currentTimeMillis() - lastTime < PROMPT_INTERVAL_MS) {
            return false
        }
        prefs.edit()
            .putInt(KEY_LAST_PROMPT_VERSION, versionCode)
            .putLong(KEY_LAST_PROMPT_TIME, System.currentTimeMillis())
            .apply()
        return true
    }

    /**
     * 下载 APK 到缓存目录，带进度回调（IO 线程）。
     * @return 下载成功的文件，失败返回 null
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            downloadClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "下载失败: HTTP ${resp.code}")
                    return@withContext null
                }
                val total = resp.body?.contentLength() ?: -1L
                val file = File(context.cacheDir, "lxmusic_update_${System.currentTimeMillis()}.apk")
                resp.body?.byteStream()?.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
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
     * 通过系统安装器安装 APK（需要 FileProvider）。
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
