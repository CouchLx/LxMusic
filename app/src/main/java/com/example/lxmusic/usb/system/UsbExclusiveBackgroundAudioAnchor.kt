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
 * File: moe.ouom.neriplayer.core.player.usb.system/UsbExclusiveBackgroundAudioAnchor (adapted for LxMusic)
 */

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.example.lxmusic.usb.UsbExclusiveLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 后台音频锚点：App 退到后台时通过系统 AudioTrack 播放 16-bit 载波，
 * 让 USB 音频输出保持活动，避免系统切换/冻结 USB 独占路径。
 * 载波幅度 256（普通媒体增益量化后仍非零），交替符号。
 */
internal enum class UsbExclusiveBackgroundAudioAnchorTransferMode {
    StaticLoop,
    Streaming
}

internal data class UsbExclusiveBackgroundAudioAnchorSpec(
    val name: String,
    val sampleRateHz: Int,
    val channelCount: Int,
    val bufferFrames: Int,
    val transferMode: UsbExclusiveBackgroundAudioAnchorTransferMode
)

private const val USB_EXCLUSIVE_BACKGROUND_ANCHOR_BYTES_PER_SAMPLE = 2
private const val USB_EXCLUSIVE_BACKGROUND_ANCHOR_CARRIER_AMPLITUDE = 256

/**
 * 独占会话激活时始终运行（不分前后台、不分播放/暂停）：锚点的系统 AudioTrack
 * 持续把系统混音输出钉到内置扬声器，系统侧音频路径离开 USB DAC，消除与 libusb
 * 的双写冲突。暂停时 native 传输仍在跑（软静音发静音包），若锚点随 isPlaying
 * 释放，系统侧会重新接管 DAC → 暂停期间 iso 错误持续累积（实测 21→57）。
 * 条件只要求"会话已打开"，独占关闭才停止。
 */
internal fun shouldRunUsbExclusiveBackgroundAudioAnchor(
    appInForeground: Boolean,
    serviceForeground: Boolean,
    usbExclusivePlaybackActive: Boolean
): Boolean {
    return usbExclusivePlaybackActive
}

internal fun usbExclusiveBackgroundAudioAnchorSpecs(): List<UsbExclusiveBackgroundAudioAnchorSpec> {
    return listOf(
        UsbExclusiveBackgroundAudioAnchorSpec(
            name = "stream_48k_stereo",
            sampleRateHz = 48_000,
            channelCount = 2,
            bufferFrames = 4_800,
            transferMode = UsbExclusiveBackgroundAudioAnchorTransferMode.Streaming
        ),
        UsbExclusiveBackgroundAudioAnchorSpec(
            name = "stream_48k_mono",
            sampleRateHz = 48_000,
            channelCount = 1,
            bufferFrames = 4_800,
            transferMode = UsbExclusiveBackgroundAudioAnchorTransferMode.Streaming
        ),
        UsbExclusiveBackgroundAudioAnchorSpec(
            name = "stream_44k_stereo",
            sampleRateHz = 44_100,
            channelCount = 2,
            bufferFrames = 4_410,
            transferMode = UsbExclusiveBackgroundAudioAnchorTransferMode.Streaming
        ),
        UsbExclusiveBackgroundAudioAnchorSpec(
            name = "stream_44k_mono",
            sampleRateHz = 44_100,
            channelCount = 1,
            bufferFrames = 4_410,
            transferMode = UsbExclusiveBackgroundAudioAnchorTransferMode.Streaming
        ),
        UsbExclusiveBackgroundAudioAnchorSpec(
            name = "stream_96k_stereo",
            sampleRateHz = 96_000,
            channelCount = 2,
            bufferFrames = 9_600,
            transferMode = UsbExclusiveBackgroundAudioAnchorTransferMode.Streaming
        )
    )
}

internal object UsbExclusiveBackgroundAudioAnchor {
    private const val TAG = "LxUsbAudioAnchor"
    private const val BYTES_PER_SAMPLE = 2
    private const val STREAM_BUFFER_MULTIPLIER = 2

    private class ActiveAnchor(
        val track: AudioTrack,
        val spec: UsbExclusiveBackgroundAudioAnchorSpec,
        val silence: ByteArray,
        val carrier: ByteArray,
        val preferredBuiltInOutputId: Int?,
        val volumeGuardToken: UsbExclusiveBackgroundAudioAnchorVolumeGuardToken?
    ) {
        @Volatile
        var carrierActive = false

        @Volatile
        var routedOutputId: Int? = null

        @Volatile
        var routedOutputType: Int? = null

        var streamWriterRunning: AtomicBoolean? = null

        var streamWriter: Thread? = null
    }

    private val lock = Any()
    private var activeAnchor: ActiveAnchor? = null

    fun start(context: Context, reason: String): Boolean {
        synchronized(lock) {
            val existing = activeAnchor
            if (existing != null && existing.track.state == AudioTrack.STATE_INITIALIZED) {
                return resume(existing, reason)
            }
            releaseLocked("replace_unusable:$reason")

            val volumeGuardToken = UsbExclusiveBackgroundAudioAnchorVolumeGuard.acquire(context)
            val created = createAnchor(context, reason, volumeGuardToken)
            if (created == null) {
                UsbExclusiveBackgroundAudioAnchorVolumeGuard.release(volumeGuardToken)
                return false
            }
            activeAnchor = created
            return resume(created, reason)
        }
    }

    fun stop(reason: String) {
        synchronized(lock) {
            releaseLocked(reason)
        }
    }

    fun isActive(): Boolean {
        return synchronized(lock) {
            activeAnchor?.track?.let {
                it.state == AudioTrack.STATE_INITIALIZED && it.playState == AudioTrack.PLAYSTATE_PLAYING
            } == true
        }
    }

    fun diagnosticSummary(): String {
        return synchronized(lock) {
            val anchor = activeAnchor ?: return@synchronized "inactive"
            val playing = anchor.track.state == AudioTrack.STATE_INITIALIZED &&
                anchor.track.playState == AudioTrack.PLAYSTATE_PLAYING
            "playing=$playing spec=${anchor.spec.name} carrier=${anchor.carrierActive} " +
                "target=${anchor.preferredBuiltInOutputId ?: "none"} " +
                "route=${anchor.routedOutputId ?: "none"}/" +
                "${anchor.routedOutputType ?: "none"} " +
                "writer=${anchor.streamWriter?.isAlive == true}"
        }
    }

    private fun createAnchor(
        context: Context,
        reason: String,
        volumeGuardToken: UsbExclusiveBackgroundAudioAnchorVolumeGuardToken?
    ): ActiveAnchor? {
        for (spec in usbExclusiveBackgroundAudioAnchorSpecs()) {
            val anchor = createAnchor(context, reason, spec, volumeGuardToken)
            if (anchor != null) return anchor
        }
        UsbExclusiveLog.w(TAG, "no compatible background media anchor reason=$reason")
        return null
    }

    private fun createAnchor(
        context: Context,
        reason: String,
        spec: UsbExclusiveBackgroundAudioAnchorSpec,
        volumeGuardToken: UsbExclusiveBackgroundAudioAnchorVolumeGuardToken?
    ): ActiveAnchor? {
        val channelMask = when (spec.channelCount) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> return null
        }
        val bufferBytes = resolveBufferBytes(spec, channelMask)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(spec.sampleRateHz)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setTransferMode(platformTransferMode(spec.transferMode))
                .setBufferSizeInBytes(bufferBytes)
                .build()
        }.onFailure { error ->
            UsbExclusiveLog.w(TAG, "create failed reason=$reason spec=${spec.name}: ${error.message}")
        }.getOrNull() ?: return null

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            UsbExclusiveLog.w(TAG, "create returned uninitialized track reason=$reason spec=${spec.name}")
            releaseTrack(track)
            return null
        }
        val preferredBuiltInOutputId = preferBuiltInOutput(context, track)
        val silence = ByteArray(bufferBytes)
        val carrier = usbExclusiveBackgroundAudioAnchorCarrier(
            bufferBytes = bufferBytes,
            channelCount = spec.channelCount
        )
        val initialized = initializeTrack(track, spec, silence)
        if (!initialized) {
            UsbExclusiveLog.w(TAG, "initialize rejected reason=$reason spec=${spec.name}")
            releaseTrack(track)
            return null
        }
        return ActiveAnchor(
            track = track,
            spec = spec,
            silence = silence,
            carrier = carrier,
            preferredBuiltInOutputId = preferredBuiltInOutputId,
            volumeGuardToken = volumeGuardToken
        )
    }

    private fun resolveBufferBytes(
        spec: UsbExclusiveBackgroundAudioAnchorSpec,
        channelMask: Int
    ): Int {
        val requestedBytes = spec.bufferFrames * spec.channelCount * BYTES_PER_SAMPLE
        if (spec.transferMode == UsbExclusiveBackgroundAudioAnchorTransferMode.StaticLoop) {
            return requestedBytes
        }
        val minBufferBytes = AudioTrack.getMinBufferSize(
            spec.sampleRateHz,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(0)
        return maxOf(requestedBytes, minBufferBytes * STREAM_BUFFER_MULTIPLIER)
    }

    private fun platformTransferMode(
        transferMode: UsbExclusiveBackgroundAudioAnchorTransferMode
    ): Int {
        return when (transferMode) {
            UsbExclusiveBackgroundAudioAnchorTransferMode.StaticLoop -> AudioTrack.MODE_STATIC
            UsbExclusiveBackgroundAudioAnchorTransferMode.Streaming -> AudioTrack.MODE_STREAM
        }
    }

    private fun initializeTrack(
        track: AudioTrack,
        spec: UsbExclusiveBackgroundAudioAnchorSpec,
        payload: ByteArray
    ): Boolean {
        return runCatching {
            when (spec.transferMode) {
                UsbExclusiveBackgroundAudioAnchorTransferMode.StaticLoop -> {
                    val written = track.write(payload, 0, payload.size)
                    if (written != payload.size) return@runCatching false
                    val loopFrames = track.bufferSizeInFrames.coerceAtLeast(0)
                    loopFrames > 0 && track.setLoopPoints(0, loopFrames, -1) == AudioTrack.SUCCESS
                }

                UsbExclusiveBackgroundAudioAnchorTransferMode.Streaming -> true
            }
        }.onFailure { error ->
            UsbExclusiveLog.w(TAG, "initialize failed spec=${spec.name}: ${error.message}")
        }.getOrDefault(false)
    }

    private fun resume(anchor: ActiveAnchor, reason: String): Boolean {
        val track = anchor.track
        val wasPlaying = track.playState == AudioTrack.PLAYSTATE_PLAYING
        val playing = runCatching {
            if (!wasPlaying) {
                track.play()
            }
            track.playState == AudioTrack.PLAYSTATE_PLAYING
        }.onFailure { error ->
            UsbExclusiveLog.w(TAG, "play failed reason=$reason: ${error.message}")
        }.getOrDefault(false)
        if (playing && !wasPlaying) {
            UsbExclusiveBackgroundAudioAnchorVolumeGuard
                .beginRouteObservation(anchor.volumeGuardToken)
        }
        if (playing) {
            ensureStreamingWriter(anchor)
        }
        if (playing && !wasPlaying) {
            UsbExclusiveLog.i(
                TAG,
                "started background media anchor reason=$reason spec=${anchor.spec.name} " +
                    "carrierRequested=${anchor.preferredBuiltInOutputId != null}"
            )
        }
        if (!playing) {
            releaseLocked("play_failed:$reason")
        }
        return playing
    }

    private fun preferBuiltInOutput(context: Context, track: AudioTrack): Int? {
        return runCatching {
            val audioManager = context.applicationContext
                .getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return@runCatching null
            val output = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?: return@runCatching null
            if (!track.setPreferredDevice(output)) {
                UsbExclusiveLog.d(TAG, "built-in output preference rejected")
                null
            } else {
                output.id
            }
        }.onFailure { error ->
            UsbExclusiveLog.w(TAG, "built-in output preference failed: ${error.message}")
        }.getOrNull()
    }

    private fun ensureStreamingWriter(anchor: ActiveAnchor) {
        if (anchor.spec.transferMode != UsbExclusiveBackgroundAudioAnchorTransferMode.Streaming) {
            return
        }
        if (anchor.streamWriter?.isAlive == true) return
        val running = AtomicBoolean(true)
        val track = anchor.track
        val specName = anchor.spec.name
        anchor.streamWriterRunning = running
        anchor.streamWriter = Thread(
            {
                while (running.get()) {
                    val payload = resolveStreamingPayload(anchor)
                    val written = try {
                        track.write(payload, 0, payload.size, AudioTrack.WRITE_BLOCKING)
                    } catch (error: Exception) {
                        if (running.get()) {
                            UsbExclusiveLog.w(TAG, "stream write failed spec=$specName: ${error.message}")
                        }
                        break
                    }
                    if (written < 0) {
                        if (running.get()) {
                            UsbExclusiveLog.w(TAG, "stream write rejected spec=$specName result=$written")
                        }
                        break
                    }
                    if (written == 0) {
                        Thread.yield()
                    }
                }
                running.set(false)
            },
            "LxUsbAudioAnchor"
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun resolveStreamingPayload(anchor: ActiveAnchor): ByteArray {
        val routedOutput = runCatching { anchor.track.routedDevice }.getOrNull()
        val routedOutputId = routedOutput?.id
        val routedOutputType = routedOutput?.type
        val routedToBuiltInOutput = anchor.preferredBuiltInOutputId != null &&
            (routedOutputId == anchor.preferredBuiltInOutputId ||
                routedOutputType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        val carrierActive = anchor.spec.transferMode ==
            UsbExclusiveBackgroundAudioAnchorTransferMode.Streaming &&
            anchor.preferredBuiltInOutputId != null &&
            routedToBuiltInOutput
        val routeChanged = anchor.routedOutputId != routedOutputId ||
            anchor.routedOutputType != routedOutputType
        val carrierChanged = anchor.carrierActive != carrierActive
        anchor.routedOutputId = routedOutputId
        anchor.routedOutputType = routedOutputType
        anchor.carrierActive = carrierActive
        if (routeChanged || carrierChanged) {
            UsbExclusiveLog.i(
                TAG,
                "background media anchor route spec=${anchor.spec.name} " +
                    "target=${anchor.preferredBuiltInOutputId ?: "none"} " +
                    "route=${routedOutputId ?: "none"}/${routedOutputType ?: "none"} " +
                    "carrier=$carrierActive"
            )
        }
        return if (carrierActive) anchor.carrier else anchor.silence
    }

    private fun releaseLocked(reason: String) {
        val anchor = activeAnchor ?: return
        activeAnchor = null
        anchor.streamWriterRunning?.set(false)
        anchor.streamWriter?.interrupt()
        anchor.streamWriter = null
        anchor.streamWriterRunning = null
        releaseTrack(anchor.track)
        UsbExclusiveBackgroundAudioAnchorVolumeGuard.release(anchor.volumeGuardToken)
        UsbExclusiveLog.i(
            TAG,
            "released background media anchor reason=$reason spec=${anchor.spec.name} " +
                "carrier=${anchor.carrierActive}"
        )
    }

    private fun releaseTrack(track: AudioTrack) {
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
            }
            track.release()
        }.onFailure { error ->
            UsbExclusiveLog.w(TAG, "release failed: ${error.message}")
        }
    }
}

private fun usbExclusiveBackgroundAudioAnchorCarrier(
    bufferBytes: Int,
    channelCount: Int
): ByteArray {
    if (bufferBytes <= 0 || channelCount <= 0) return ByteArray(0)

    val carrier = ByteArray(bufferBytes)
    val bytesPerFrame = channelCount * USB_EXCLUSIVE_BACKGROUND_ANCHOR_BYTES_PER_SAMPLE
    var sample = USB_EXCLUSIVE_BACKGROUND_ANCHOR_CARRIER_AMPLITUDE
    var frameOffset = 0
    while (frameOffset + bytesPerFrame <= carrier.size) {
        repeat(channelCount) { channel ->
            val sampleOffset =
                frameOffset + channel * USB_EXCLUSIVE_BACKGROUND_ANCHOR_BYTES_PER_SAMPLE
            carrier[sampleOffset] = sample.toByte()
            carrier[sampleOffset + 1] = (sample shr 8).toByte()
        }
        sample = -sample
        frameOffset += bytesPerFrame
    }
    return carrier
}
