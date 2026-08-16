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
 * File: moe.ouom.neriplayer.core.player.service/MediaSessionVolumePolicy (adapted for LxMusic)
 */

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import com.example.lxmusic.usb.UsbExclusiveLog
import com.example.lxmusic.usb.session.UsbExclusiveSessionController
import kotlin.math.roundToInt

/**
 * USB 独占音量路由（对齐 NeriPlayer 的「写进系统」方案）：
 * 独占激活且非比特完美时，通过独立的框架 MediaSession + VolumeProvider
 * 接管系统音量控制（音量键 / 锁屏音量条 / 通知媒体条），
 * 变化直接驱动独占音量（playerVolume，与设置页专属音量条同一通道）。
 * 退出独占或比特完美时交还系统。
 */
internal object UsbExclusiveVolumeRoutingSession {

    private const val SESSION_TAG = "LxMusicUsbExclusiveVolume"

    private var mediaSession: MediaSession? = null

    /** 路由是否激活 */
    fun isActive(): Boolean = mediaSession != null

    /**
     * 按当前状态刷新路由会话：enabled=true 时启用（幂等），false 时停用并释放。
     * @param active 是否应接管系统音量（独占激活 && 会话打开 && 非比特完美）
     */
    fun refresh(context: Context, active: Boolean) {
        if (active) {
            enable(context)
        } else {
            disable()
        }
    }

    /** 同步播放状态（播放/暂停/进度）。不重复 setActive：仅在 enable 时激活一次，
     *  避免高频切换激活状态干扰系统媒体栈（MIUI 上会导致媒体按键会话/焦点被抢）。 */
    fun syncPlaybackState(playing: Boolean, positionMs: Long) {
        val session = mediaSession ?: return
        runCatching {
            val playbackState = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SEEK_TO
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    positionMs.coerceAtLeast(0L),
                    if (playing) 1f else 0f,
                    SystemClock.elapsedRealtime()
                )
                .build()
            session.setPlaybackState(playbackState)
        }
    }

    private fun enable(context: Context) {
        if (mediaSession != null) return
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val providerMaxIndex = usbExclusiveVolumeProviderMaxIndex(minVolume, maxVolume)
        // 初始音量对齐设置页专属音量条（独占音量等级），不再跟随系统音量
        val providerCurrentIndex = (
            UsbExclusiveSessionController.volumeState.value.playerVolume *
                providerMaxIndex
            ).roundToInt().coerceIn(0, providerMaxIndex)
        val provider = UsbExclusiveLockScreenVolumeProvider(
            maxVolume = providerMaxIndex,
            initialVolume = providerCurrentIndex,
            onVolumeFractionChanged = { fraction ->
                // 音量键/锁屏音量条 → 直接驱动独占音量（与设置页专属音量条同一通道）
                UsbExclusiveSessionController.setPlayerVolume(fraction)
                UsbExclusiveSystemVolumeBridge.updateSessionVolumeFraction(fraction)
            }
        )
        val session = runCatching {
            MediaSession(appContext, SESSION_TAG).apply {
                setPlaybackToRemote(provider)
                setActive(true)
            }
        }.getOrElse { error ->
            UsbExclusiveLog.e(
                "LxUsbVolumeRoute",
                "failed to create volume routing session: ${error.message}"
            )
            return
        }
        mediaSession = session
        syncPlaybackState(playing = false, positionMs = 0L)
        UsbExclusiveLog.i(
            "LxUsbVolumeRoute",
            "USB exclusive volume routing enabled (max=$providerMaxIndex, current=$providerCurrentIndex)"
        )
    }

    private fun disable() {
        val session = mediaSession ?: return
        mediaSession = null
        runCatching {
            session.setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_NONE, 0L, 0f)
                    .build()
            )
            session.setPlaybackToLocal(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            session.setActive(false)
        }
        runCatching { session.release() }
        UsbExclusiveLog.i("LxUsbVolumeRoute", "USB exclusive volume routing disabled")
    }
}

/** 音量键 / 音量条 回调 → 音量比例 */
internal class UsbExclusiveLockScreenVolumeProvider(
    maxVolume: Int,
    initialVolume: Int,
    private val onVolumeFractionChanged: (Float) -> Unit
) : VolumeProvider(
    VOLUME_CONTROL_ABSOLUTE,
    maxVolume.coerceAtLeast(1),
    initialVolume.coerceIn(0, maxVolume.coerceAtLeast(1))
) {
    override fun onAdjustVolume(direction: Int) {
        updateVolume(
            adjustedUsbExclusiveVolumeProviderIndex(
                currentIndex = currentVolume,
                providerMaxIndex = maxVolume,
                direction = direction
            )
        )
    }

    override fun onSetVolumeTo(volume: Int) {
        updateVolume(volume)
    }

    private fun updateVolume(volume: Int) {
        val nextVolume = volume.coerceIn(0, maxVolume)
        if (nextVolume == currentVolume) return
        setCurrentVolume(nextVolume)
        onVolumeFractionChanged(
            usbExclusiveVolumeFractionFromProviderIndex(nextVolume, maxVolume)
        )
    }
}

internal fun usbExclusiveVolumeProviderMaxIndex(minVolume: Int, maxVolume: Int): Int {
    return (maxVolume - minVolume).coerceAtLeast(1)
}

internal fun usbExclusiveVolumeProviderCurrentIndex(
    currentVolume: Int,
    minVolume: Int,
    maxVolume: Int
): Int {
    val providerMax = usbExclusiveVolumeProviderMaxIndex(minVolume, maxVolume)
    return (currentVolume.coerceIn(minVolume, maxVolume) - minVolume)
        .coerceIn(0, providerMax)
}

internal fun usbExclusiveVolumeFractionFromProviderIndex(
    providerIndex: Int,
    providerMaxIndex: Int
): Float {
    val maxIndex = providerMaxIndex.coerceAtLeast(1)
    return (providerIndex.coerceIn(0, maxIndex).toFloat() / maxIndex.toFloat())
        .coerceIn(0f, 1f)
}

internal fun usbExclusiveVolumeProviderIndexFromFraction(
    volumeFraction: Float,
    providerMaxIndex: Int
): Int {
    val maxIndex = providerMaxIndex.coerceAtLeast(1)
    return (volumeFraction.coerceIn(0f, 1f) * maxIndex.toFloat())
        .toInt()
        .coerceIn(0, maxIndex)
}

internal fun adjustedUsbExclusiveVolumeProviderIndex(
    currentIndex: Int,
    providerMaxIndex: Int,
    direction: Int
): Int {
    val maxIndex = providerMaxIndex.coerceAtLeast(1)
    val current = currentIndex.coerceIn(0, maxIndex)
    return when (direction) {
        AudioManager.ADJUST_RAISE -> (current + 1).coerceAtMost(maxIndex)
        AudioManager.ADJUST_LOWER -> (current - 1).coerceAtLeast(0)
        AudioManager.ADJUST_MUTE -> 0
        AudioManager.ADJUST_UNMUTE -> if (current == 0) 1 else current
        AudioManager.ADJUST_TOGGLE_MUTE -> if (current == 0) 1 else 0
        else -> current
    }
}
