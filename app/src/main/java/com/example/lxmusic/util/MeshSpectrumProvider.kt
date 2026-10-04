@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package com.example.lxmusic.util

/*
 * MeiloX Mesh 网格渐变背景的频谱源适配层（LxMusic）。
 *
 * 与 MeiloX 原版（Media3 1.6 AudioOutput 路线）不同，这里把 PcmSpectrumAnalyzer
 * 挂在本项目已有的 TeeAudioProcessor PCM 分流点上（PlayerService 音频链路最前端）：
 * 普通输出与 USB 独占输出都能拿到 PCM，无需 RECORD_AUDIO 权限。
 *
 * 数据流：
 *   PCM tee → PcmSpectrumAnalyzer（音频线程，三频段 IIR，~20ms 窗）
 *           → spectrum Flow（UI 收集时以 33ms 节奏采样 + SpectrumEnvelope 归一化）
 *           → MeshBackgroundView.updateSpectrum()
 */
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MeshSpectrumProvider {
    private const val TAG = "MeshSpectrumProvider"

    private val lock = Any()
    private var analyzer: PcmSpectrumAnalyzer? = null
    private var sampleRateHz: Int = 44100

    // 已写入分析器的累计帧数（相当于 MeiloX AudioOutput 的 output position）
    private var writtenFrames: Long = 0L

    // 仅当 Mesh 背景可见时才做逐样本分析（冷流收集即开启，取消即挂起）
    @Volatile
    private var analyzing = false

    private val scratchPower = FloatArray(3)

    /**
     * 组合 PCM 分流器：先喂原有 AudioReactive（Hyper 背景/鼓点行为不变），
     * 再喂 Mesh 频谱分析器。PlayerService 用它替换原来的 AudioReactive.teeSink。
     */
    val pcmSink = object : TeeAudioProcessor.AudioBufferSink {
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            AudioReactive.teeSink.flush(sampleRateHz, channelCount, encoding)
            synchronized(lock) {
                this@MeshSpectrumProvider.sampleRateHz = sampleRateHz
                writtenFrames = 0L
                analyzer = if (PcmSpectrumAnalyzer.bytesPerSample(encoding) > 0) {
                    try {
                        PcmSpectrumAnalyzer(sampleRateHz, channelCount, encoding)
                    } catch (e: Exception) {
                        Log.w(TAG, "Spectrum analyzer init failed: ${e.message}")
                        null
                    }
                } else {
                    null // 大端等不支持的编码：Mesh 背景退化为纯色流动
                }
            }
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            AudioReactive.teeSink.handleBuffer(buffer)
            if (!analyzing) return
            val currentAnalyzer = analyzer ?: return
            val start = buffer.position()
            val end = buffer.limit()
            if (end <= start) return
            synchronized(lock) {
                val dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                currentAnalyzer.consume(dup, start, end, writtenFrames)
                val bytesPerFrame = currentAnalyzer.bytesPerFrame
                if (bytesPerFrame > 0) {
                    writtenFrames += (end - start) / bytesPerFrame
                }
            }
        }
    }

    /**
     * UI 采样节奏的频谱流：收集期间才开启分析，离开播放页自动停止（省电）。
     * 与 MeiloX PlaybackBeatMeter.spectrum 相同的 33ms 节奏与包络算法。
     */
    val spectrum: Flow<PlaybackSpectrum> = flow {
        analyzing = true
        val power = FloatArray(3)
        val envelope = SpectrumEnvelope()
        var previousNs = System.nanoTime()
        try {
            while (true) {
                val now = System.nanoTime()
                currentPower(power)
                emit(envelope.update(power, (now - previousNs) / 1e9f))
                previousNs = now
                delay(33L)
            }
        } finally {
            analyzing = false
            synchronized(lock) {
                analyzer?.suspendAnalysis()
            }
        }
    }

    private fun currentPower(destination: FloatArray) {
        synchronized(lock) {
            val currentAnalyzer = analyzer
            if (currentAnalyzer == null) {
                destination.fill(0f)
                return
            }
            val positionUs = writtenFrames * 1_000_000L / sampleRateHz
            currentAnalyzer.powerAt(positionUs, destination)
        }
    }
}
