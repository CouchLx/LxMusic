package com.example.lxmusic

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sin

/**
 * 探针：打印 AudioPlaybackCallback 里的播放配置，确认：
 * - 本应用自己的播放器是否出现在列表里
 * - uid/isActive/playerState 这些 @SystemApi 字段能否反射读到（公开 SDK 只剩 audioAttributes）
 * - 其它应用播放媒体时列表如何变化（用于 MIUI 焦点失效时的兜底方案）
 */
@RunWith(AndroidJUnit4::class)
class AudioPlaybackProbeTest {

    private val tag = "LxProbe"

    @Test
    fun probePlaybackConfigs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val am = context.getSystemService(AudioManager::class.java)

        fun call(config: AudioPlaybackConfiguration, method: String): Any? =
            runCatching {
                AudioPlaybackConfiguration::class.java
                    .getMethod(method)
                    .invoke(config)
            }.getOrElse { "err:${it.javaClass.simpleName}" }

        fun logConfig(stage: String, config: AudioPlaybackConfiguration) {
            Log.i(
                tag,
                "[$stage] attrs usage=${config.audioAttributes?.usage} " +
                    "contentType=${config.audioAttributes?.contentType} " +
                    "| isActive=${call(config, "isActive")} uid=${call(config, "getClientUid")} " +
                    "pid=${call(config, "getClientPid")} state=${call(config, "getPlayerState")} " +
                    "type=${call(config, "getPlayerType")}"
            )
        }

        fun dump(stage: String) {
            val configs = am.activePlaybackConfigurations
            Log.i(tag, "== $stage size=${configs.size} ==")
            configs.forEach { logConfig(stage, it) }
        }

        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                Log.i(tag, "-- callback size=${configs.size} --")
                configs.forEach { logConfig("callback", it) }
            }
        }
        am.registerAudioPlaybackCallback(callback, Handler(Looper.getMainLooper()))

        // 自己播一段 WAV，观察本应用播放器的配置形态
        val wav = createTestWav(File(context.cacheDir, "probe.wav"))
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val latch = CountDownLatch(1)
        var controller: MediaController? = null
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
            latch.countDown()
        }, ContextCompat.getMainExecutor(context))
        latch.await(20, TimeUnit.SECONDS)
        val c = controller
        if (c != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                c.setMediaItem(MediaItem.fromUri(Uri.fromFile(wav)))
                c.prepare()
                c.play()
            }
        }

        repeat(10) { index ->
            Thread.sleep(5_000)
            dump("t=${(index + 1) * 5}s")
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runCatching { c?.pause() }
            runCatching { c?.release() }
        }
        am.unregisterAudioPlaybackCallback(callback)
    }

    private fun createTestWav(file: File): File {
        val sampleRate = 44_100
        val channels = 2
        val seconds = 60
        val totalFrames = sampleRate * seconds
        val dataSize = totalFrames * channels * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * 2)
        buffer.putShort((channels * 2).toShort())
        buffer.putShort(16)
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        for (frame in 0 until totalFrames) {
            val sample = (sin(2.0 * Math.PI * 440.0 * frame / sampleRate) * 400).toInt().toShort()
            buffer.putShort(sample)
            buffer.putShort(sample)
        }
        file.writeBytes(buffer.array())
        return file
    }
}
