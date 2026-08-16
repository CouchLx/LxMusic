package com.example.lxmusic.usb.session

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
 * File: moe.ouom.neriplayer.core.player.usb.session/UsbExclusiveWakeLock (adapted for LxMusic)
 */

import android.content.Context
import android.os.PowerManager
import com.example.lxmusic.usb.UsbExclusiveLog

/** USB 独占播放唤醒锁（后台播放时保持 CPU/传输不被冻结） */
internal object UsbExclusiveWakeLock {
    private const val TAG = "LxUsbWakeLock"
    private const val LOCK_TAG = "LxMusic:UsbExclusivePlayback"
    private const val LEASE_TIMEOUT_MS = 10L * 60L * 1000L
    private val lock = Any()
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire(context: Context, reason: String) {
        synchronized(lock) {
            val playbackWakeLock = wakeLock ?: runCatching { createWakeLock(context) }
                .onFailure { error ->
                    UsbExclusiveLog.w(TAG, "create failed reason=$reason: ${error.message}")
                }
                .getOrNull()
                ?.also { wakeLock = it }
                ?: return
            val alreadyHeld = runCatching { playbackWakeLock.isHeld }.getOrDefault(false)
            runCatching { playbackWakeLock.acquire(LEASE_TIMEOUT_MS) }
                .onSuccess {
                    if (!alreadyHeld) {
                        UsbExclusiveLog.d(TAG, "acquired reason=$reason timeoutMs=$LEASE_TIMEOUT_MS")
                    }
                }
                .onFailure { error ->
                    UsbExclusiveLog.w(TAG, "acquire failed reason=$reason: ${error.message}")
                }
        }
    }

    fun release(reason: String) {
        synchronized(lock) {
            val playbackWakeLock = wakeLock ?: return
            if (!runCatching { playbackWakeLock.isHeld }.getOrDefault(false)) return
            runCatching { playbackWakeLock.release() }
                .onSuccess { UsbExclusiveLog.d(TAG, "released reason=$reason") }
                .onFailure { error ->
                    UsbExclusiveLog.w(TAG, "release failed reason=$reason: ${error.message}")
                }
        }
    }

    fun isHeld(): Boolean {
        return synchronized(lock) {
            wakeLock?.let { runCatching { it.isHeld }.getOrDefault(false) } == true
        }
    }

    private fun createWakeLock(context: Context): PowerManager.WakeLock {
        val powerManager = context.applicationContext
            .getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOCK_TAG).apply {
            setReferenceCounted(false)
        }
    }
}
