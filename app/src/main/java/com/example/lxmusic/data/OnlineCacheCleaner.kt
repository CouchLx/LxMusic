package com.example.lxmusic.data

import android.content.Context
import android.util.Log

/**
 * 在线内容缓存清理：解绑激活码/切换账号时调用，
 * 避免未授权状态下首页、排行榜、发现页继续展示旧数据并反复显示"刷新失败"。
 * 只清在线缓存，本地收藏/本地歌单/本地歌曲数据库不动。
 */
object OnlineCacheCleaner {

    private const val TAG = "OnlineCacheCleaner"

    fun clearOnlineCaches(context: Context) {
        runCatching {
            context.getSharedPreferences("home_cache", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        runCatching {
            context.getSharedPreferences("rank_cache", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        runCatching {
            context.getSharedPreferences("discover_recommend_cache", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        // 我的页只清在线歌单缓存（本地排序/本地歌单保留）
        runCatching {
            context.getSharedPreferences("mine_state", Context.MODE_PRIVATE)
                .edit()
                .remove("playlists_json")
                .remove("playlists_json_uid")
                .remove("last_load_auth")
                .apply()
        }
        // 顺便取消挂起的首页刷新，避免未激活时反复尝试
        runCatching {
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("home_pending_refresh", false)
                .apply()
        }
        Log.d(TAG, "在线缓存已清理")
    }
}
