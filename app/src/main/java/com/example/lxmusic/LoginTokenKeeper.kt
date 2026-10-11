package com.example.lxmusic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 客户端登录 token 保活。
 *
 * 酷狗 token 有有效期（约 1 个月），过期后取流 / 收藏 / 歌单写入等登录态接口全部失效，
 * 用户必须重新走验证码登录。但酷狗提供 `/login/token` 接口，
 * "刷新登录状态，可以延长 token 过期时间"——即滑动续期：只要在过期前调用一次，
 * 有效期重新起算，可无限保活。
 *
 * 本类在应用启动时（MainActivity 初始化处）被检查一次：距上次续期超过
 * REFRESH_INTERVAL_MS 才真正发请求，成功后把新 token 写回 auth prefs
 * 与全局 [KuGouApi.token]，用户无感。
 *
 * 注意：上游文档明确"不要频繁调登录接口，可能被风控，登录状态还在就不要重复调"——
 * 这里只做天级低频续期，绝不轮询；失败不更新续期时间，下次启动自然重试
 * （token 真正已过期时刷新会一直失败，此时只能重新登录，无法自动恢复）。
 */
object LoginTokenKeeper {

    private const val PREFS_NAME = "auth"
    private const val KEY_TOKEN = "token"
    private const val KEY_USERID = "userid"
    private const val KEY_LAST_REFRESH = "last_token_refresh"

    // 续期间隔：7 天。token 约 1 个月过期，7 天一次留足安全余量，
    // 同时远低于风控敏感频率（绝不分钟/小时级轮询）。
    private const val REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

    /** 记录一次"新鲜 token"时间（登录成功时调用，避免登录当天又触发续期） */
    fun markFresh(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_REFRESH, System.currentTimeMillis()).apply()
    }

    /**
     * 是否需要续期：已登录 且 距上次续期超过 7 天。
     * 老版本登录的用户没有 last_token_refresh 记录（读 0），首次升级启动即续期一次，
     * 正好把他们手上快过期/已过期的 token 尝试续上。
     */
    fun shouldRefresh(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, "") ?: ""
        if (token.isBlank()) return false
        val last = prefs.getLong(KEY_LAST_REFRESH, 0)
        return System.currentTimeMillis() - last > REFRESH_INTERVAL_MS
    }

    /**
     * 调酷狗 `/login/token` 刷新登录并把新 token 写回。
     * 成功返回新 token；失败返回异常（不改动本地 token，保持原状可继续重试）。
     */
    suspend fun refresh(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val token = prefs.getString(KEY_TOKEN, "") ?: ""
            val userid = prefs.getLong(KEY_USERID, 0)
            if (token.isBlank()) return@withContext Result.failure(Exception("未登录"))

            val resp = KuGouApi.service.refreshLoginToken(token, userid)
            val newToken = resp.data?.token
            if (resp.status == 1 && !newToken.isNullOrBlank()) {
                val newUserid = resp.data?.userid?.takeIf { it > 0 } ?: userid
                prefs.edit()
                    .putString(KEY_TOKEN, newToken)
                    .putLong(KEY_USERID, newUserid)
                    .putLong(KEY_LAST_REFRESH, System.currentTimeMillis())
                    .apply()
                // 同步全局鉴权：请求 Cookie 每次动态读取 KuGouApi.token，无需额外同步
                KuGouApi.token = newToken
                KuGouApi.userid = newUserid.toString()
                Log.d("LxMusic", "登录 token 已续期: userid=$newUserid")
                Result.success(newToken)
            } else {
                Log.w("LxMusic", "登录 token 续期失败: status=${resp.status}, errcode=${resp.errcode}")
                Result.failure(Exception("刷新失败 status=${resp.status}"))
            }
        } catch (e: Exception) {
            // HTTP 4xx/5xx 时服务器会在响应 body 里带具体错误（如酷狗 error_code），
            // 打印出来便于排查（仅日志，不影响逻辑）
            val body = (e as? retrofit2.HttpException)?.let { ex ->
                runCatching { ex.response()?.errorBody()?.string() }.getOrNull()
            }
            if (body != null) {
                Log.e("LxMusic", "登录 token 续期异常: HTTP ${(e as retrofit2.HttpException).code()}, body=$body")
            } else {
                Log.e("LxMusic", "登录 token 续期异常", e)
            }
            Result.failure(e)
        }
    }
}
