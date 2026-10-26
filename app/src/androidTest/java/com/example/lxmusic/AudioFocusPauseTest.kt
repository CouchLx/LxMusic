package com.example.lxmusic

import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
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
 * 「其它应用播放时自动暂停」的真实链路验证：
 * 让 PlayerService 播放一段本地 WAV，然后用另一个音频焦点客户端请求 AUDIOFOCUS_GAIN
 * （等价于其它音乐/视频 App 开始播放），断言播放器自动暂停。
 */
@RunWith(AndroidJUnit4::class)
class AudioFocusPauseTest {

    private lateinit var context: Context
    private var controller: MediaController? = null
    private var focusRequest: AudioFocusRequest? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // 固定测试前提：自动暂停开启，淡入淡出/交叉淡化关闭（避免音量渐变干扰）
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("playback_pause_on_other_app", true)
            .putBoolean("playback_fade_in", false)
            .putBoolean("playback_crossfade_next", false)
            .commit()
    }

    @After
    fun tearDown() {
        focusRequest?.let { req -> runCatching { audioManager().abandonAudioFocusRequest(req) } }
        focusRequest = null
        controller?.let { c ->
            onMain { runCatching { c.pause() } }
            onMain { runCatching { c.release() } }
        }
        controller = null
    }

    @Test
    fun otherAppFocusLossPausesPlayback() {
        val wav = createTestWav(File(context.cacheDir, "focus_test.wav"))
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val connected = CountDownLatch(1)
        future.addListener({
            runCatching { controller = future.get() }
            connected.countDown()
        }, ContextCompat.getMainExecutor(context))
        assertTrue("控制器连接超时", connected.await(20, TimeUnit.SECONDS))
        val c = controller ?: error("MediaController 为空")

        onMain {
            c.setMediaItem(MediaItem.fromUri(Uri.fromFile(wav)))
            c.prepare()
            c.play()
        }
        assertTrue("播放未开始", waitUntil(10_000) { onMainValue { c.isPlaying } })

        // 模拟另一个应用开始播放媒体：独立的焦点客户端请求 GAIN
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        val result = audioManager().requestAudioFocus(request)
        assertTrue(
            "焦点请求失败 result=$result",
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        )

        assertTrue(
            "其它客户端拿到焦点后，本应用没有自动暂停",
            waitUntil(5_000) { onMainValue { !c.isPlaying && !c.playWhenReady } }
        )
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
            Thread.sleep(100)
        }
        return condition()
    }

    private fun audioManager(): AudioManager =
        context.getSystemService(AudioManager::class.java)

    /** 生成 20 秒低音量双声道正弦波 WAV（16-bit / 44.1kHz） */
    private fun createTestWav(file: File): File {
        val sampleRate = 44_100
        val channels = 2
        val seconds = 20
        val totalFrames = sampleRate * seconds
        val dataSize = totalFrames * channels * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * 2)
        buffer.putShort((channels * 2).toShort())
        buffer.putShort(16)
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        for (frame in 0 until totalFrames) {
            val sample = (sin(2.0 * Math.PI * 440.0 * frame / sampleRate) * 800).toInt().toShort()
            buffer.putShort(sample)
            buffer.putShort(sample)
        }
        file.writeBytes(buffer.array())
        return file
    }
}
