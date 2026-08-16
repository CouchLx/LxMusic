package com.example.lxmusic.usb.transport

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
 * File: moe.ouom.neriplayer.core.player.usb.transport/UsbExclusiveNativeState (adapted for LxMusic)
 */

/** USB 独占原生会话状态（驱动 UI 诊断显示） */
data class UsbExclusiveNativeState(
    val available: Boolean = false,
    val opened: Boolean = false,
    val streaming: Boolean = false,
    val paused: Boolean = false,
    val transitioning: Boolean = false,
    val source: String = "idle",
    val handle: Long = 0L,
    val selectedDeviceName: String? = null,
    val inputFormat: String = "none",
    val outputFormat: String = "none",
    val outputSampleRate: Int = 0,
    val bufferDurationMs: Int = 250,
    val completedAudioFrames: Long = 0L,
    val queuedAudioFrames: Long = 0L,
    val pcmFreeBytes: Long = 0L,
    val pcmCapacityBytes: Long = 0L,
    val pcmLevelBytes: Long = 0L,
    val outputPeak: Float = 0f,
    val playbackReady: Boolean? = null,
    val terminalFailure: Boolean? = null,
    val recommendedAction: String? = null,
    val feedbackMode: String = "disabled",
    val feedbackState: String = "disabled",
    val runtimeReport: String = "idle",
    val lastError: String? = null,
    val transportFailed: Boolean = false,
    val deviceOnline: Boolean = true,
    val errorCode: String? = null,
    val actionId: Long? = null,
    val actionGeneration: Long? = null,
    val actionOwner: String? = null,
    val actionLatched: Boolean? = null,
    val feedbackReady: Boolean? = null,
    val noDeviceObserved: Boolean? = null,
    val detachConfirmed: Boolean? = null
)
