package com.example.lxmusic

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 激活状态的 UI 可观察版本：MainActivity 用它决定「未激活精简界面 / 激活完整界面」。
 * 由 VipConfigManager 在启动恢复、激活成功、解绑时更新。
 */
object ActivationState {
    var canUseVip by mutableStateOf(false)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    fun update(value: Boolean) {
        // activate / refreshToken 在 IO 线程完成，Compose 状态必须在主线程写入
        if (Looper.myLooper() == Looper.getMainLooper()) {
            canUseVip = value
        } else {
            mainHandler.post { canUseVip = value }
        }
    }
}

/**
 * VIP 配置管理器
 *
 * 功能：
 * 1. 激活码验证与设备绑定
 * 2. 自动从服务器拉取最新 VIP token
 * 3. 防止二次传播（设备绑定）
 */
object VipConfigManager {

    private const val PREFS_NAME = "vip_config"
    private const val KEY_ACTIVATED = "vip_activated"
    private const val KEY_ACTIVATE_CODE = "activate_code"
    private const val KEY_DEVICE_ID = "bound_device_id"
    private const val KEY_VIP_TOKEN = "vip_token"
    private const val KEY_VIP_USERID = "vip_userid"
    private const val KEY_LAST_REFRESH = "last_refresh_time"
    private const val KEY_REFRESH_INTERVAL = "refresh_interval_ms"
    private const val KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled"

    // 默认刷新间隔：1天
    private const val DEFAULT_REFRESH_INTERVAL = 24 * 60 * 60 * 1000L

    // 激活状态
    data class VipStatus(
        val isActivated: Boolean,
        val isDeviceBound: Boolean,
        val canUseVip: Boolean,
        val lastRefreshTime: Long,
        val activateCode: String?,
        val message: String = ""
    )

    /**
     * 获取设备唯一标识（用于绑定）
     */
    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return hashDeviceId(androidId ?: "unknown")
    }

    private fun hashDeviceId(raw: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 16)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 激活接口地址：配置了 BFF 时走 BFF 代理（api/vip 前缀），
     * 这样 3000 端口可以彻底收回内网，客户端只暴露 BFF 一个入口。
     */
    private fun vipEndpoint(path: String): String {
        val bff = KuGouApi.bffUrl.trim()
        return if (bff.isNotEmpty()) {
            bff.trimEnd('/') + "/api/" + path
        } else {
            KuGouApi.baseUrl.trimEnd('/') + "/" + path
        }
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    /** 同步激活凭证到 BFF 客户端（api 请求头校验用），并更新在线访问开关 */
    private fun syncBffCredential(code: String?, deviceId: String?) {
        BffApi.vipCode = code ?: ""
        BffApi.vipDevice = deviceId ?: ""
        BffApi.onlineAllowed = !code.isNullOrBlank()
    }

    /**
     * 获取当前 VIP 状态
     */
    fun getStatus(context: Context): VipStatus {
        val prefs = getPrefs(context)
        val isActivated = prefs.getBoolean(KEY_ACTIVATED, false)
        val boundDeviceId = prefs.getString(KEY_DEVICE_ID, null)
        val currentDeviceId = getDeviceId(context)
        val isDeviceBound = boundDeviceId == currentDeviceId
        val lastRefresh = prefs.getLong(KEY_LAST_REFRESH, 0)
        val code = prefs.getString(KEY_ACTIVATE_CODE, null)

        val canUse = isActivated && isDeviceBound
        val message = when {
            !isActivated -> "未激活，请输入激活码"
            !isDeviceBound -> "设备未授权，请在原设备上使用或联系管理员"
            else -> "VIP 服务正常"
        }

        return VipStatus(isActivated, isDeviceBound, canUse, lastRefresh, code, message)
    }

    /**
     * 使用激活码激活 VIP
     * @return 成功返回 true，失败返回 false 并可通过 getStatus 查看原因
     */
    suspend fun activate(context: Context, code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val deviceId = getDeviceId(context)
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(vipEndpoint("vip/activate") + "?code=${enc(code)}&device_id=$deviceId")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            Log.d("LxMusic", "activate response: $body")

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("服务器错误: ${response.code}"))
            }

            val json = JSONObject(body)
            val success = json.optBoolean("success", false)
            val message = json.optString("message", "未知错误")

            if (success) {
                val token = json.optString("token", "")
                val userid = json.optString("userid", "")
                val refreshInterval = json.optLong("refresh_interval", DEFAULT_REFRESH_INTERVAL)

                // 保存激活信息
                getPrefs(context).edit().apply {
                    putBoolean(KEY_ACTIVATED, true)
                    putString(KEY_ACTIVATE_CODE, code)
                    putString(KEY_DEVICE_ID, deviceId)
                    putString(KEY_VIP_TOKEN, token)
                    putString(KEY_VIP_USERID, userid)
                    putLong(KEY_LAST_REFRESH, System.currentTimeMillis())
                    putLong(KEY_REFRESH_INTERVAL, refreshInterval)
                    putBoolean(KEY_AUTO_UPDATE_ENABLED, true)
                    apply()
                }

                // 立即应用到 KuGouApi
                applyVipToApi(token, userid)
                syncBffCredential(code, deviceId)
                ActivationState.update(true)

                Result.success(message)
            } else {
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e("LxMusic", "激活失败", e)
            Result.failure(Exception("网络错误: ${e.message}"))
        }
    }

    /**
     * 从服务器刷新 VIP token
     * @return 成功返回 true
     */
    suspend fun refreshToken(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prefs = getPrefs(context)
            val code = prefs.getString(KEY_ACTIVATE_CODE, null)
                ?: return@withContext Result.failure(Exception("未激活"))
            val deviceId = getDeviceId(context)

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(vipEndpoint("vip/config") + "?code=${enc(code)}&device_id=$deviceId")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            Log.d("LxMusic", "refreshToken response: $body")

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("服务器错误: ${response.code}"))
            }

            val json = JSONObject(body)
            val success = json.optBoolean("success", false)

            if (success) {
                val token = json.optString("token", "")
                val userid = json.optString("userid", "")

                if (token.isNotBlank() && userid.isNotBlank()) {
                    prefs.edit().apply {
                        putString(KEY_VIP_TOKEN, token)
                        putString(KEY_VIP_USERID, userid)
                        putLong(KEY_LAST_REFRESH, System.currentTimeMillis())
                        apply()
                    }
                    applyVipToApi(token, userid)
                    syncBffCredential(code, deviceId)
                    Result.success("Token 已更新")
                } else {
                    Result.failure(Exception("服务器返回数据无效"))
                }
            } else {
                val message = json.optString("message", "刷新失败")
                // 如果是设备不匹配，清除激活状态
                if (message.contains("设备") || message.contains("device")) {
                    clearActivation(context)
                }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e("LxMusic", "刷新 token 失败", e)
            Result.failure(Exception("网络错误: ${e.message}"))
        }
    }

    /**
     * 检查是否需要自动刷新
     */
    fun shouldAutoRefresh(context: Context): Boolean {
        val prefs = getPrefs(context)
        val isActivated = prefs.getBoolean(KEY_ACTIVATED, false)
        val autoUpdate = prefs.getBoolean(KEY_AUTO_UPDATE_ENABLED, true)
        if (!isActivated || !autoUpdate) return false

        val lastRefresh = prefs.getLong(KEY_LAST_REFRESH, 0)
        val interval = prefs.getLong(KEY_REFRESH_INTERVAL, DEFAULT_REFRESH_INTERVAL)
        return System.currentTimeMillis() - lastRefresh > interval
    }

    /**
     * 将保存的 VIP 配置应用到 KuGouApi
     */
    fun applySavedVipToApi(context: Context) {
        val prefs = getPrefs(context)
        val status = getStatus(context)
        ActivationState.update(status.canUseVip)
        if (!status.canUseVip) {
            // 未激活/设备不匹配：不携带任何激活凭证（BFF 会按 401 处理）
            syncBffCredential(null, null)
            return
        }

        val token = prefs.getString(KEY_VIP_TOKEN, "") ?: ""
        val userid = prefs.getString(KEY_VIP_USERID, "") ?: ""
        applyVipToApi(token, userid)
        syncBffCredential(prefs.getString(KEY_ACTIVATE_CODE, ""), prefs.getString(KEY_DEVICE_ID, ""))
    }

    private fun applyVipToApi(token: String, userid: String) {
        if (token.isNotBlank() && userid.isNotBlank()) {
            KuGouApi.ownerToken = token
            KuGouApi.ownerUserid = userid
            Log.d("LxMusic", "VIP token 已应用: userid=$userid")
        }
    }

    /**
     * 清除激活状态（退出/解绑）
     */
    fun clearActivation(context: Context) {
        getPrefs(context).edit().clear().apply()
        KuGouApi.ownerToken = ""
        KuGouApi.ownerUserid = ""
        syncBffCredential(null, null)
        // 解绑后清理在线缓存，避免首页/排行榜继续显示旧数据并反复“刷新失败”
        com.example.lxmusic.data.OnlineCacheCleaner.clearOnlineCaches(context)
        // 解绑后重新弹出「使用方式」选择（本地音乐 / 激活）
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().remove("activation_prompt_version").apply()
        ActivationState.update(false)
        Log.d("LxMusic", "VIP 激活状态已清除")
    }

    /**
     * 设置是否启用自动更新
     */
    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_UPDATE_ENABLED, enabled).apply()
    }

    /**
     * 获取是否启用自动更新
     */
    fun isAutoUpdateEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_UPDATE_ENABLED, true)
    }
}
