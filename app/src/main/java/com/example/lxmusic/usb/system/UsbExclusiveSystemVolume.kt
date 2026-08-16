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
 * File: moe.ouom.neriplayer.core.player.usb.system/UsbExclusiveSystemVolume (adapted for LxMusic)
 */

import com.example.lxmusic.usb.DEFAULT_USB_EXCLUSIVE_BIT_PERFECT
import kotlin.math.pow

internal const val USB_EXCLUSIVE_SYSTEM_VOLUME_EXPONENT = 2.0

/** USB 独占音量采用 300 级 / 每级 0.2 dB，范围 -60.0 dB ~ 0.0 dB。
 *  dB 线性刻度下人耳感知均匀，低区精细、高区粗放；0.0 dB = 满增益。 */
internal const val USB_EXCLUSIVE_VOLUME_DB_MIN = -60.0
internal const val USB_EXCLUSIVE_VOLUME_DB_MAX = 0.0
internal const val USB_EXCLUSIVE_VOLUME_DB_STEP = 0.2
internal const val USB_EXCLUSIVE_VOLUME_LEVELS = 300

/** 音量等级(0-1，= level/300) → dB 值（-60..0） */
internal fun usbExclusiveVolumeLevelToDb(level: Float): Double {
    val v = level.coerceIn(0f, 1f)
    return USB_EXCLUSIVE_VOLUME_DB_MIN + v * (USB_EXCLUSIVE_VOLUME_DB_MAX - USB_EXCLUSIVE_VOLUME_DB_MIN)
}

/** 音量等级(0-1) → 线性增益（10^(dB/20)） */
internal fun usbExclusiveVolumeLevelToGain(level: Float): Float {
    val db = usbExclusiveVolumeLevelToDb(level)
    if (db <= USB_EXCLUSIVE_VOLUME_DB_MIN) return 0f
    return 10.0.pow(db / 20.0).toFloat().coerceIn(0f, 1f)
}

/** USB 独占会话音量状态（供设置页展示与调节） */
internal data class UsbExclusiveVolumeState(
    val playerVolume: Float = 1f,
    val systemVolumeFraction: Float = 1f,
    val effectiveVolume: Float = 1f,
    val bitPerfect: Boolean = false,
    val focusMuted: Boolean = false
)

internal data class UsbExclusiveSystemVolumeBridgeSubscription(
    val generation: Long
)

/** 会话音量桥：锁屏/蓝牙音量键 → native 增益的通道 */
internal object UsbExclusiveSystemVolumeBridge {
    private data class ActiveSubscription(
        val token: UsbExclusiveSystemVolumeBridgeSubscription,
        val listener: (Float?) -> Unit
    )

    private val lock = Any()
    private var nextGeneration = 0L
    private var activeSubscription: ActiveSubscription? = null
    private var sessionVolumeFraction: Float? = null

    fun subscribe(listener: (Float?) -> Unit): UsbExclusiveSystemVolumeBridgeSubscription {
        val token: UsbExclusiveSystemVolumeBridgeSubscription
        val currentVolume: Float?
        synchronized(lock) {
            token = UsbExclusiveSystemVolumeBridgeSubscription(++nextGeneration)
            activeSubscription = ActiveSubscription(token, listener)
            currentVolume = sessionVolumeFraction
        }
        listener(currentVolume)
        return token
    }

    fun unsubscribe(token: UsbExclusiveSystemVolumeBridgeSubscription?) {
        if (token == null) return
        synchronized(lock) {
            if (activeSubscription?.token == token) {
                activeSubscription = null
            }
        }
    }

    fun updateSessionVolumeFraction(volumeFraction: Float) {
        val normalized = volumeFraction.coerceIn(0f, 1f)
        val listener = synchronized(lock) {
            sessionVolumeFraction = normalized
            activeSubscription?.listener
        }
        listener?.invoke(normalized)
    }

    fun currentSessionVolumeFractionOrNull(): Float? {
        return synchronized(lock) { sessionVolumeFraction }
    }

    fun clearSessionVolumeFraction() {
        val listener = synchronized(lock) {
            if (sessionVolumeFraction == null) return
            sessionVolumeFraction = null
            activeSubscription?.listener
        }
        listener?.invoke(null)
    }
}

/** 有效 native 音量 = dB 曲线(播放器音量等级) × 系统音量²；比特完美时恒 1.0 */
internal fun usbExclusiveEffectiveNativeVolume(
    playerVolume: Float,
    systemVolumeFraction: Float,
    bitPerfect: Boolean = DEFAULT_USB_EXCLUSIVE_BIT_PERFECT
): Float {
    if (bitPerfect) return 1f
    val playerGain = usbExclusiveVolumeLevelToGain(playerVolume)
    return playerGain * usbExclusiveSystemVolumeGain(systemVolumeFraction)
}

internal fun usbExclusiveSystemVolumeGain(volumeFraction: Float): Float {
    val normalized = volumeFraction.coerceIn(0f, 1f)
    return normalized.toDouble()
        .pow(USB_EXCLUSIVE_SYSTEM_VOLUME_EXPONENT)
        .toFloat()
        .coerceIn(0f, 1f)
}
