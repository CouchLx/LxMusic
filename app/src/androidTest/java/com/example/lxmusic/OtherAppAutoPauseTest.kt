package com.example.lxmusic

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin

/**
 * 「其它应用播放时自动暂停」的兜底链路验证（不依赖音频焦点）：
 * 本应用播放本地 WAV → 外部启动抖音播放媒体 → 断言本应用暂停。
 * 测试要求 MIUI 的 key_ignore_music_focus_req=1（焦点丢失不下发）才能真正验证兜底。
 */
@RunWith(AndroidJUnit4::class)
class OtherAppAutoPauseTest {

    @Test
    fun otherAppPlaybackPausesMusic() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("playback_pause_on_other_app", true)
            .putBoolean("playback_fade_in", false)
            .putBoolean("playback_crossfade_next", false)
            .commit()

        val wav = createTestWav(File(context.cacheDir, "auto_pause_test.wav"))
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val latch = CountDownLatch(1)
        var controller: MediaController? = null
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
            latch.countDown()
        }, ContextCompat.getMainExecutor(context))
        assertTrue("控制器连接超时", latch.await(20, TimeUnit.SECONDS))
        val c = controller ?: error("MediaController 为空")

        onMain {
            c.setMediaItem(MediaItem.fromUri(Uri.fromFile(wav)))
            c.prepare()
            c.play()
        }
        assertTrue("播放未开始", waitUntil(10_000) { onMainValue { c.isPlaying } })

        // 基线校准在起播 1.5s 后进行，观察 5s 确认没有误判
        Thread.sleep(5_000)
        assertTrue("起播 5s 内被误暂停（兜底误判）", onMainValue { c.isPlaying })

        // 外部会在这段时间启动抖音播放媒体
        val paused = waitUntil(35_000) { onMainValue { !c.isPlaying && !c.playWhenReady } }
        assertTrue("其它应用播放媒体后没有自动暂停（兜底未生效）", paused)

        onMain {
            runCatching { c.pause() }
            runCatching { c.release() }
        }
    }

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private fun onMainValue(block: () -> Boolean): Boolean {
        val result = AtomicBoolean(false)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result.set(block()) }
        return result.get()
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(200)
        }
        return condition()
    }

    /** 90 秒低音量双声道正弦波 WAV */
    private fun createTestWav(file: File): File {
        val sampleRate = 44_100
        val channels = 2
        val seconds = 90
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
            val sample = (sin(2.0 * Math.PI * 440.0 * frame / sampleRate) * 300).toInt().toShort()
            buffer.putShort(sample)
            buffer.putShort(sample)
        }
        file.writeBytes(buffer.array())
        return file
    }
}
