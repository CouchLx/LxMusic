package com.example.lxmusic.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 定时自动备份调度：开关/周期/上次结果都存在统一的 settings SharedPreferences 里，
 * 周期任务交给 WorkManager（重启后仍有效，不需要额外权限）。
 */
object AutoBackupScheduler {
    private const val PREFS_NAME = "settings"
    const val KEY_ENABLED = "auto_backup_enabled"
    const val KEY_INTERVAL_DAYS = "auto_backup_interval_days"
    const val KEY_LAST_TIME = "auto_backup_last_time"
    const val KEY_LAST_SUCCESS = "auto_backup_last_success"
    const val KEY_LAST_MESSAGE = "auto_backup_last_message"

    /** 可选周期（天 → 文案） */
    val INTERVAL_OPTIONS = listOf(
        1 to "每天",
        3 to "每 3 天",
        7 to "每周",
        14 to "每 2 周",
        30 to "每月"
    )

    private const val WORK_NAME = "lxmusic_auto_backup"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun intervalDays(context: Context): Int =
        prefs(context).getInt(KEY_INTERVAL_DAYS, 1).coerceIn(1, 365)

    fun intervalLabel(days: Int): String =
        INTERVAL_OPTIONS.firstOrNull { it.first == days }?.second ?: "每 $days 天"

    fun lastRunTime(context: Context): Long = prefs(context).getLong(KEY_LAST_TIME, 0L)

    fun lastRunSuccess(context: Context): Boolean = prefs(context).getBoolean(KEY_LAST_SUCCESS, true)

    fun lastRunMessage(context: Context): String =
        prefs(context).getString(KEY_LAST_MESSAGE, "") ?: ""

    /** 开启或修改周期：立即按新周期重建任务 */
    fun schedule(context: Context, days: Int) {
        val safeDays = days.coerceIn(1, 365)
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, true)
            .putInt(KEY_INTERVAL_DAYS, safeDays)
            .apply()
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(safeDays.toLong(), TimeUnit.DAYS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** 关闭开关：取消任务（不删除已导出的备份文件） */
    fun cancel(context: Context) {
        prefs(context).edit().putBoolean(KEY_ENABLED, false).apply()
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * App 启动自愈：开关开着但任务丢失（被系统清理/重装）时补建。
     * 用 KEEP 而不是 UPDATE，避免每次启动都把计时重置导致备份永远不触发。
     */
    fun ensureScheduled(context: Context) {
        if (!isEnabled(context)) return
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            intervalDays(context).toLong(), TimeUnit.DAYS
        ).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun recordResult(context: Context, success: Boolean, message: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_TIME, System.currentTimeMillis())
            .putBoolean(KEY_LAST_SUCCESS, success)
            .putString(KEY_LAST_MESSAGE, message)
            .apply()
    }
}
