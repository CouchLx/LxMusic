package com.example.lxmusic

import android.content.Context

/**
 * 网易云登录态持久化（SharedPreferences "netease_auth"）。
 *
 * 与酷狗侧保持同一套做法：cookie 以原始串存盘，启动时恢复到 [NeteaseApi.authCookie]，
 * 由 NeteaseApi 的拦截器统一注入 Cookie 头（BFF 只做转发，不持有会话）。
 */
object NeteaseAuth {
    private const val PREFS = "netease_auth"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_UID = "uid"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_AVATAR = "avatar"
    private const val KEY_VIP_TYPE = "vip_type"
    private const val KEY_LAST_VERIFY = "last_verify"

    /** 启动校验节流：30 分钟内不重复请求 /user/account */
    private const val VERIFY_INTERVAL_MS = 30 * 60 * 1000L

    data class State(
        val loggedIn: Boolean = false,
        val uid: Long = 0L,
        val nickname: String = "",
        val avatarUrl: String = "",
        val vipType: Int = 0
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 启动时（首帧前）恢复登录态，并把后续 cookie 轮换接上落盘 */
    fun restore(context: Context) {
        val app = context.applicationContext
        NeteaseApi.authCookie = prefs(app).getString(KEY_COOKIE, "") ?: ""
        NeteaseApi.onAuthCookieChanged = { cookie ->
            val sp = prefs(app)
            if (cookie.isBlank()) {
                sp.edit().remove(KEY_COOKIE).apply()
            } else {
                sp.edit().putString(KEY_COOKIE, cookie).apply()
            }
        }
    }

    fun load(context: Context): State {
        val sp = prefs(context)
        val cookie = sp.getString(KEY_COOKIE, "") ?: ""
        val uid = sp.getLong(KEY_UID, 0L)
        return State(
            loggedIn = cookie.isNotBlank() && uid != 0L,
            uid = uid,
            nickname = sp.getString(KEY_NICKNAME, "") ?: "",
            avatarUrl = sp.getString(KEY_AVATAR, "") ?: "",
            vipType = sp.getInt(KEY_VIP_TYPE, 0)
        )
    }

    fun isLoggedIn(context: Context): Boolean = load(context).loggedIn

    fun persist(
        context: Context,
        cookie: String,
        uid: Long,
        nickname: String,
        avatarUrl: String,
        vipType: Int
    ) {
        // 登录接口回的 cookie 串带属性项，统一清洗后再落盘
        val cleanCookie = NeteaseApi.cleanCookie(cookie)
        prefs(context).edit()
            .putString(KEY_COOKIE, cleanCookie)
            .putLong(KEY_UID, uid)
            .putString(KEY_NICKNAME, nickname)
            .putString(KEY_AVATAR, avatarUrl)
            .putInt(KEY_VIP_TYPE, vipType)
            .putLong(KEY_LAST_VERIFY, System.currentTimeMillis())
            .apply()
        // 直接赋值，避免再触发一次落盘回调
        NeteaseApi.authCookie = cleanCookie
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        NeteaseApi.authCookie = ""
    }

    /**
     * 启动时校验 cookie 是否还有效（节流 30 分钟）。
     * 网络异常时保留登录态，只有服务端明确回"未登录"才清，避免断网把账号挤掉。
     */
    suspend fun verifyOnStartup(context: Context) {
        val sp = prefs(context)
        val state = load(context)
        if (!state.loggedIn) return
        val now = System.currentTimeMillis()
        if (now - sp.getLong(KEY_LAST_VERIFY, 0L) < VERIFY_INTERVAL_MS) return

        val resp = runCatching { NeteaseApi.service.userAccount(now) }.getOrNull() ?: return
        val profile = resp.profile
        if (resp.code == 200 && profile != null) {
            sp.edit()
                .putLong(KEY_UID, if (profile.userId != 0L) profile.userId else state.uid)
                .putString(KEY_NICKNAME, profile.nickname ?: state.nickname)
                .putString(KEY_AVATAR, profile.avatarUrl ?: state.avatarUrl)
                .putInt(KEY_VIP_TYPE, profile.vipType)
                .putLong(KEY_LAST_VERIFY, now)
                .apply()
        } else if (resp.code == 301 || resp.code == -462) {
            // cookie 已失效（官方端改密/退出/风控）
            clear(context)
            android.util.Log.d("LxMusic", "网易云登录态已失效，已清理本地 cookie")
        }
    }
}
