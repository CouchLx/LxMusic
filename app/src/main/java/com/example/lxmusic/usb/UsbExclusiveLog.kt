package com.example.lxmusic.usb

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * USB 独占内存环形日志：USB 各层日志同时写入 Logcat 与内存环形缓冲，
 * 供设置页"运行日志"面板查看/复制，用于诊断 USB 独占无效问题。
 */
internal object UsbExclusiveLog {

    private const val TAG = "LxUsbLog"
    private const val MAX_ENTRIES = 300

    data class Entry(
        val timeMs: Long,
        val tag: String,
        val level: Char,
        val message: String
    ) {
        fun format(timestamp: SimpleDateFormat): String =
            "${timestamp.format(Date(timeMs))} $level/$tag: $message"
    }

    private val entries = ArrayDeque<Entry>()
    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun clear() {
        synchronized(this) { entries.clear() }
    }

    fun snapshot(): List<Entry> {
        synchronized(this) { return entries.toList() }
    }

    /** 按时间正序拼接全部日志（供日志面板/复制使用） */
    fun formattedSnapshot(): String {
        synchronized(this) {
            return entries.joinToString("\n") { it.format(timestampFormat) }
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        append(tag, 'D', message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        append(tag, 'I', message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        append(tag, 'W', message)
    }

    fun e(tag: String, message: String) {
        Log.e(tag, message)
        append(tag, 'E', message)
    }

    private fun append(tag: String, level: Char, message: String) {
        synchronized(this) {
            entries.addLast(Entry(System.currentTimeMillis(), tag, level, message))
            while (entries.size > MAX_ENTRIES) {
                entries.removeFirst()
            }
        }
    }
}
