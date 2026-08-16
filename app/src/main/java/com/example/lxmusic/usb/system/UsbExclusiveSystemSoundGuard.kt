package com.example.lxmusic.usb.system

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.core.player.usb.system/UsbExclusiveSystemSoundGuard (adapted for LxMusic)
 */

import android.content.Context
import com.example.lxmusic.usb.UsbExclusiveLog

/**
 * 记录独占会话生命周期, 但不修改系统全局音量
 *
 * 全局静音会影响其他应用, 而且截图, 键盘等系统音效会反复触发回调
 */
internal object UsbExclusiveSystemSoundGuard {
    private const val TAG = "LxUsbSoundGuard"
    private val lock = Any()
    private var active = false

    fun activate(context: Context, reason: String) {
        val first = synchronized(lock) {
            val wasActive = active
            active = true
            !wasActive
        }
        UsbExclusiveLog.i(
            TAG,
            "activate reason=$reason first=$first package=${context.applicationContext.packageName}"
        )
    }

    fun releaseWhenNativeIdle(context: Context, reason: String) {
        release(context, reason)
    }

    fun forceRelease(context: Context, reason: String) {
        release(context, reason)
    }

    private fun release(context: Context, reason: String) {
        val released = synchronized(lock) {
            if (!active) return
            active = false
            true
        }
        if (released) {
            UsbExclusiveLog.i(
                TAG,
                "release reason=$reason package=${context.applicationContext.packageName}"
            )
        }
    }
}
