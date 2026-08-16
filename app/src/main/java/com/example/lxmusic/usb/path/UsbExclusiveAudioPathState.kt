package com.example.lxmusic.usb.path

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
 * File: moe.ouom.neriplayer.core.player.usb.path/UsbExclusiveAudioPathState (adapted for LxMusic)
 */

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 音频路径状态：请求路径（系统/USB 独占）与有效路径的可见状态。
 * 用于在 USB 失败回退时向界面/诊断呈现当前路径及原因。
 */
data class UsbExclusiveAudioPathState(
    val requestedPath: String = REQUESTED_SYSTEM,
    val effectivePath: String = EFFECTIVE_SYSTEM,
    val fallbackReason: String? = null,
    val inputFormat: String = "none",
    val requestedPlaybackParameters: String = "speed=1.0 pitch=1.0",
    val skipSilence: Boolean = false,
    val sinkPlaying: Boolean = false,
    val nativePaused: Boolean = false,
    val requestedVolume: Float = 1f,
    val generation: Long = 0L
) {
    companion object {
        const val REQUESTED_SYSTEM = "SYSTEM_AUDIO"
        const val REQUESTED_NATIVE_USB = "NATIVE_USB"
        const val EFFECTIVE_SYSTEM = "SYSTEM_AUDIO"
        const val EFFECTIVE_NATIVE_USB = "NATIVE_USB"
    }
}

object UsbExclusiveAudioPathTracker {
    private val _state = MutableStateFlow(UsbExclusiveAudioPathState())
    val state: StateFlow<UsbExclusiveAudioPathState> = _state.asStateFlow()
    @Volatile
    private var forcedSystemFallbackReason: String? = null

    fun updateRequested(enabled: Boolean) {
        forcedSystemFallbackReason = null
        _state.update { current ->
            current.copy(
                requestedPath = if (enabled) {
                    UsbExclusiveAudioPathState.REQUESTED_NATIVE_USB
                } else {
                    UsbExclusiveAudioPathState.REQUESTED_SYSTEM
                },
                fallbackReason = null,
                generation = current.generation + 1L
            )
        }
    }

    fun forceSystemFallback(reason: String) {
        forcedSystemFallbackReason = reason
        _state.update { current -> current.copy(fallbackReason = reason) }
    }

    fun clearForcedSystemFallback() {
        forcedSystemFallbackReason = null
        _state.update { current -> current.copy(fallbackReason = null) }
    }

    fun forcedSystemFallbackReason(): String? = forcedSystemFallbackReason

    fun updateConfigured(
        usingNative: Boolean,
        fallbackReason: String?,
        inputFormat: String
    ) {
        _state.update { current ->
            current.copy(
                effectivePath = if (usingNative) {
                    UsbExclusiveAudioPathState.EFFECTIVE_NATIVE_USB
                } else {
                    UsbExclusiveAudioPathState.EFFECTIVE_SYSTEM
                },
                fallbackReason = fallbackReason,
                inputFormat = inputFormat,
                nativePaused = usingNative && !current.sinkPlaying,
                generation = current.generation + 1L
            )
        }
    }

    fun updatePlaybackParameters(speed: Float, pitch: Float) {
        _state.update { current ->
            current.copy(requestedPlaybackParameters = "speed=$speed pitch=$pitch")
        }
    }

    fun updateSkipSilence(enabled: Boolean) {
        _state.update { current -> current.copy(skipSilence = enabled) }
    }

    fun updatePlaying(playing: Boolean, usingNative: Boolean) {
        _state.update { current ->
            current.copy(
                sinkPlaying = playing,
                nativePaused = usingNative && !playing
            )
        }
    }

    fun updateNativePaused(paused: Boolean, sinkPlaying: Boolean) {
        _state.update { current ->
            current.copy(
                sinkPlaying = sinkPlaying,
                nativePaused = paused
            )
        }
    }

    fun updateVolume(volume: Float) {
        _state.update { current -> current.copy(requestedVolume = volume) }
    }
}
