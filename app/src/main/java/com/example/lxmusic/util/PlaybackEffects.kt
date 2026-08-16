package com.example.lxmusic.util

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
 * File: moe.ouom.neriplayer.core.player.engine/VolumeNormalizationAudioProcessor +
 *       StereoBalanceAudioProcessor (adapted for LxMusic)
 */

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ==================== 声道平衡 ====================

const val DEFAULT_PLAYBACK_VOLUME_BALANCE = 0.0f
const val MIN_PLAYBACK_VOLUME_BALANCE = -1.0f
const val MAX_PLAYBACK_VOLUME_BALANCE = 1.0f

fun normalizePlaybackVolumeBalance(value: Float): Float {
    if (!value.isFinite()) return DEFAULT_PLAYBACK_VOLUME_BALANCE
    return ((value * 100f).roundToInt() / 100f)
        .coerceIn(MIN_PLAYBACK_VOLUME_BALANCE, MAX_PLAYBACK_VOLUME_BALANCE)
}

internal object PlaybackVolumeBalanceState {
    @Volatile
    private var balance = DEFAULT_PLAYBACK_VOLUME_BALANCE

    fun update(balance: Float) {
        this.balance = normalizePlaybackVolumeBalance(balance)
    }

    fun current(): Float = balance
}

internal data class StereoBalanceGains(
    val left: Float,
    val right: Float
) {
    val isCentered: Boolean
        get() = left == 1f && right == 1f
}

internal fun stereoBalanceGains(balance: Float): StereoBalanceGains {
    val normalized = normalizePlaybackVolumeBalance(balance)
    return StereoBalanceGains(
        left = if (normalized > 0f) 1f - normalized else 1f,
        right = if (normalized < 0f) 1f + normalized else 1f
    )
}

/** 声道平衡：左右声道音量比例（-1..1，0 居中） */
@UnstableApi
internal class StereoBalanceAudioProcessor(
    private val balanceProvider: () -> Float = PlaybackVolumeBalanceState::current
) : BaseAudioProcessor() {

    private var encoding = C.ENCODING_PCM_16BIT

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.channelCount != STEREO_CHANNEL_COUNT) {
            return AudioFormat.NOT_SET
        }
        if (
            inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            return AudioFormat.NOT_SET
        }
        encoding = inputAudioFormat.encoding
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) return

        val outputBuffer = replaceOutputBuffer(inputSize)
        val gains = stereoBalanceGains(balanceProvider())
        if (gains.isCentered) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        if (encoding == C.ENCODING_PCM_FLOAT) {
            processFloat(inputBuffer, outputBuffer, gains)
        } else {
            processPcm16(inputBuffer, outputBuffer, gains)
        }
        outputBuffer.flip()
    }

    private fun processPcm16(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        gains: StereoBalanceGains
    ) {
        while (inputBuffer.remaining() >= STEREO_PCM16_FRAME_BYTES) {
            val left = inputBuffer.short
            val right = inputBuffer.short
            outputBuffer.putShort(scalePcm16(left, gains.left))
            outputBuffer.putShort(scalePcm16(right, gains.right))
        }
        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }
    }

    private fun processFloat(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        gains: StereoBalanceGains
    ) {
        val order = inputBuffer.order()
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        while (inputBuffer.remaining() >= STEREO_FLOAT_FRAME_BYTES) {
            val left = inputBuffer.float
            val right = inputBuffer.float
            outputBuffer.putFloat((left * gains.left).coerceIn(-1f, 1f))
            outputBuffer.putFloat((right * gains.right).coerceIn(-1f, 1f))
        }
        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }
        inputBuffer.order(order)
        outputBuffer.order(order)
    }

    private fun scalePcm16(sample: Short, gain: Float): Short {
        return (sample.toInt() * gain)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }

    companion object {
        private const val STEREO_CHANNEL_COUNT = 2
        private const val BYTES_PER_PCM16_SAMPLE = 2
        private const val BYTES_PER_FLOAT_SAMPLE = 4
        private const val STEREO_PCM16_FRAME_BYTES = STEREO_CHANNEL_COUNT * BYTES_PER_PCM16_SAMPLE
        private const val STEREO_FLOAT_FRAME_BYTES = STEREO_CHANNEL_COUNT * BYTES_PER_FLOAT_SAMPLE
    }
}

// ==================== 响度均衡 ====================

internal data class PlaybackVolumeNormalizationSnapshot(
    val enabled: Boolean,
    val generation: Long
)

internal object PlaybackVolumeNormalizationState {
    private val ref = AtomicReference(
        PlaybackVolumeNormalizationSnapshot(enabled = false, generation = 0L)
    )

    fun updateEnabled(enabled: Boolean) {
        while (true) {
            val current = ref.get()
            if (current.enabled == enabled) return
            val next = PlaybackVolumeNormalizationSnapshot(
                enabled = enabled,
                generation = current.generation + 1L
            )
            if (ref.compareAndSet(current, next)) return
        }
    }

    fun resetForNewTrack() {
        while (true) {
            val current = ref.get()
            val next = current.copy(generation = current.generation + 1L)
            if (ref.compareAndSet(current, next)) return
        }
    }

    fun current(): PlaybackVolumeNormalizationSnapshot = ref.get()
}

/** 响度均衡：自动增益 + 限幅，平衡不同歌曲音量 */
@UnstableApi
internal class VolumeNormalizationAudioProcessor(
    private val stateProvider: () -> PlaybackVolumeNormalizationSnapshot =
        PlaybackVolumeNormalizationState::current
) : BaseAudioProcessor() {
    // 开启高解析度输出时音源会以 PCM_FLOAT 进链, 这里按编码切换归一化器
    // 避免只支持 16-bit 时对 float 直接返回 NOT_SET 让响度归一化被整段旁路
    private var normalizer: VolumeNormalizer = Pcm16VolumeNormalizer()
    private val reusableStats = Pcm16LevelStats()
    private var sampleRate = 0
    private var channelCount = 0
    private var appliedGeneration = Long.MIN_VALUE

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (
            inputAudioFormat.channelCount <= 0 ||
            inputAudioFormat.sampleRate <= 0
        ) {
            return AudioFormat.NOT_SET
        }
        normalizer = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT ->
                normalizer as? Pcm16VolumeNormalizer ?: Pcm16VolumeNormalizer()
            C.ENCODING_PCM_FLOAT ->
                normalizer as? FloatVolumeNormalizer ?: FloatVolumeNormalizer()
            // 其它编码 (如 24/32-bit 整数 PCM) 不在处理链支持范围内, 安全旁路
            else -> return AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) return

        val outputBuffer = replaceOutputBuffer(inputSize)
        val state = stateProvider()
        if (state.generation != appliedGeneration) {
            normalizer.reset()
            appliedGeneration = state.generation
        }
        if (!state.enabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        normalizer.process(
            inputBuffer = inputBuffer,
            outputBuffer = outputBuffer,
            sampleRate = sampleRate,
            channelCount = channelCount,
            stats = reusableStats
        )
        outputBuffer.flip()
    }

    override fun onReset() {
        sampleRate = 0
        channelCount = 0
        normalizer.reset()
        appliedGeneration = Long.MIN_VALUE
    }
}

/**
 * 与采样格式无关的响度归一化核心: 增益追踪, 限幅包络等纯数学逻辑集中在此
 * 采样点的读取/写入由子类按 PCM_16BIT 或 PCM_FLOAT 实现
 */
internal abstract class VolumeNormalizer {
    private var currentGain = 1f
    private var limiterGain = 1f
    private var accumulatedSumSquares = 0.0
    private var analyzedSampleCount = 0L
    private var analyzedPeak = 0f
    private var limiterEnvelope = FloatArray(0)

    /** 单个采样点的字节数 (16-bit=2, float=4) */
    protected abstract val bytesPerSample: Int

    /** 统计当前缓冲区的 rms/peak/采样点数, 不得移动 buffer 的 position */
    protected abstract fun analyze(buffer: ByteBuffer, stats: Pcm16LevelStats)

    /** 按绝对字节偏移读取归一化到 [-1, 1] 的采样值, 不得移动 buffer 的 position */
    protected abstract fun readNormalizedAt(buffer: ByteBuffer, byteIndex: Int): Float

    /** 顺序读取一个采样, 乘增益后写出, 读写各前进一个采样 */
    protected abstract fun writeScaledSample(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        gain: Float
    )

    fun reset() {
        currentGain = 1f
        limiterGain = 1f
        accumulatedSumSquares = 0.0
        analyzedSampleCount = 0L
        analyzedPeak = 0f
    }

    fun process(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        sampleRate: Int,
        channelCount: Int,
        stats: Pcm16LevelStats = Pcm16LevelStats()
    ) {
        analyze(inputBuffer, stats)
        if (stats.sampleCount == 0 || sampleRate <= 0 || channelCount <= 0) {
            outputBuffer.put(inputBuffer)
            return
        }

        val frameCount = stats.sampleCount / channelCount
        if (frameCount == 0) {
            outputBuffer.put(inputBuffer)
            return
        }
        val blockDurationSeconds = frameCount.toFloat() / sampleRate
        val targetGain = observeAndResolveTargetGain(stats)
        val timeConstantSeconds = if (targetGain < currentGain) {
            GAIN_REDUCTION_TIME_SECONDS
        } else {
            GAIN_INCREASE_TIME_SECONDS
        }
        val smoothing = smoothingFactor(blockDurationSeconds, timeConstantSeconds)
        val nextGain = currentGain + (targetGain - currentGain) * smoothing
        val gainStepPerFrame = (nextGain - currentGain) / frameCount
        ensureLimiterEnvelopeCapacity(frameCount)
        buildLimiterEnvelope(
            inputBuffer = inputBuffer,
            sampleRate = sampleRate,
            channelCount = channelCount,
            frameCount = frameCount,
            baseGainStep = gainStepPerFrame
        )

        repeat(frameCount) { frameIndex ->
            val gain = limiterEnvelope[frameIndex]
            repeat(channelCount) {
                writeScaledSample(inputBuffer, outputBuffer, gain)
            }
        }
        val tailGain = limiterEnvelope[frameCount - 1]
        while (inputBuffer.hasRemaining()) {
            if (inputBuffer.remaining() >= bytesPerSample) {
                writeScaledSample(inputBuffer, outputBuffer, tailGain)
            } else {
                outputBuffer.put(inputBuffer.get())
            }
        }
        currentGain = nextGain
        limiterGain = tailGain
    }

    internal fun observeAndResolveTargetGain(stats: Pcm16LevelStats): Float {
        if (stats.rms < SILENCE_GATE_RMS) return currentGain
        accumulatedSumSquares += stats.rms * stats.rms * stats.sampleCount
        analyzedSampleCount += stats.sampleCount
        analyzedPeak = maxOf(analyzedPeak, stats.peak)
        val integratedRms = sqrt(accumulatedSumSquares / analyzedSampleCount).toFloat()
        val rmsGain = (TARGET_RMS / integratedRms).coerceIn(MIN_GAIN, MAX_GAIN)
        val peakGain = resolvePeakSafeGain(analyzedPeak)
        return min(rmsGain, peakGain).coerceIn(MIN_GAIN, MAX_GAIN)
    }

    private fun resolvePeakSafeGain(peak: Float): Float {
        if (peak <= 0f) return MAX_GAIN
        return (PEAK_CEILING / peak).coerceAtMost(MAX_GAIN)
    }

    private fun ensureLimiterEnvelopeCapacity(frameCount: Int) {
        if (limiterEnvelope.size >= frameCount) return
        limiterEnvelope = FloatArray(Integer.highestOneBit(frameCount - 1).coerceAtLeast(1) shl 1)
    }

    private fun buildLimiterEnvelope(
        inputBuffer: ByteBuffer,
        sampleRate: Int,
        channelCount: Int,
        frameCount: Int,
        baseGainStep: Float
    ) {
        val frameSizeBytes = channelCount * bytesPerSample
        val inputStart = inputBuffer.position()
        repeat(frameCount) { frameIndex ->
            val frameStart = inputStart + frameIndex * frameSizeBytes
            var framePeak = 0f
            repeat(channelCount) { channelIndex ->
                val sample = readNormalizedAt(
                    inputBuffer,
                    frameStart + channelIndex * bytesPerSample
                )
                framePeak = maxOf(framePeak, abs(sample))
            }
            val baseGain = currentGain + baseGainStep * (frameIndex + 1)
            limiterEnvelope[frameIndex] = min(baseGain, resolvePeakSafeGain(framePeak))
        }

        val attackStep = limiterGainStep(sampleRate, LIMITER_ATTACK_TIME_SECONDS)
        for (frameIndex in frameCount - 2 downTo 0) {
            limiterEnvelope[frameIndex] = min(
                limiterEnvelope[frameIndex],
                limiterEnvelope[frameIndex + 1] + attackStep
            )
        }

        val releaseStep = limiterGainStep(sampleRate, LIMITER_RELEASE_TIME_SECONDS)
        var previousGain = limiterGain
        repeat(frameCount) { frameIndex ->
            val releasedGain = min(limiterEnvelope[frameIndex], previousGain + releaseStep)
            limiterEnvelope[frameIndex] = releasedGain
            previousGain = releasedGain
        }
    }

    private fun limiterGainStep(sampleRate: Int, durationSeconds: Float): Float {
        val durationFrames = sampleRate * durationSeconds
        if (durationFrames <= 1f) return MAX_GAIN - MIN_GAIN
        return (MAX_GAIN - MIN_GAIN) / durationFrames
    }

    companion object {
        const val TARGET_RMS = 0.12589254f
        const val SILENCE_GATE_RMS = 0.00177828f
        const val MIN_GAIN = 0.25118864f
        const val MAX_GAIN = 1.9952623f
        const val PEAK_CEILING = 0.7943282f
        const val GAIN_REDUCTION_TIME_SECONDS = 0.25f
        const val GAIN_INCREASE_TIME_SECONDS = 4f
        const val LIMITER_ATTACK_TIME_SECONDS = 0.005f
        const val LIMITER_RELEASE_TIME_SECONDS = 0.1f
    }
}

internal class Pcm16VolumeNormalizer : VolumeNormalizer() {
    override val bytesPerSample: Int = BYTES_PER_PCM16_SAMPLE

    override fun analyze(buffer: ByteBuffer, stats: Pcm16LevelStats) {
        analyzePcm16(buffer, stats)
    }

    override fun readNormalizedAt(buffer: ByteBuffer, byteIndex: Int): Float {
        return buffer.getShort(byteIndex) / PCM16_FULL_SCALE
    }

    override fun writeScaledSample(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        gain: Float
    ) {
        outputBuffer.putShort(scalePcm16(inputBuffer.short, gain))
    }

    private fun scalePcm16(sample: Short, gain: Float): Short {
        return (sample.toInt() * gain)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }

    companion object {
        private const val BYTES_PER_PCM16_SAMPLE = 2
        private const val PCM16_FULL_SCALE = 32768f
    }
}

internal class FloatVolumeNormalizer : VolumeNormalizer() {
    override val bytesPerSample: Int = BYTES_PER_FLOAT_SAMPLE

    override fun analyze(buffer: ByteBuffer, stats: Pcm16LevelStats) {
        analyzePcmFloat(buffer, stats)
    }

    override fun readNormalizedAt(buffer: ByteBuffer, byteIndex: Int): Float {
        return buffer.getFloat(byteIndex)
    }

    override fun writeScaledSample(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        gain: Float
    ) {
        // float PCM 约定值域为 [-1, 1], 写出前钳位避免应用增益后溢出
        outputBuffer.putFloat((inputBuffer.float * gain).coerceIn(-1f, 1f))
    }
}

internal data class Pcm16LevelStats(
    var rms: Float = 0f,
    var peak: Float = 0f,
    var sampleCount: Int = 0
)

internal fun analyzePcm16(buffer: ByteBuffer): Pcm16LevelStats {
    return analyzePcm16(buffer, Pcm16LevelStats())
}

private fun analyzePcm16(
    buffer: ByteBuffer,
    result: Pcm16LevelStats
): Pcm16LevelStats {
    var sumSquares = 0.0
    var peak = 0.0
    var sampleCount = 0
    var index = buffer.position()
    val lastSampleStart = buffer.limit() - 2
    while (index <= lastSampleStart) {
        val normalized = buffer.getShort(index) / 32768.0
        val absolute = abs(normalized)
        sumSquares += normalized * normalized
        peak = maxOf(peak, absolute)
        sampleCount++
        index += 2
    }
    val rms = if (sampleCount > 0) sqrt(sumSquares / sampleCount).toFloat() else 0f
    result.rms = rms
    result.peak = peak.toFloat()
    result.sampleCount = sampleCount
    return result
}

internal fun analyzePcmFloat(buffer: ByteBuffer): Pcm16LevelStats {
    return analyzePcmFloat(buffer, Pcm16LevelStats())
}

private fun analyzePcmFloat(
    buffer: ByteBuffer,
    result: Pcm16LevelStats
): Pcm16LevelStats {
    var sumSquares = 0.0
    var peak = 0.0
    var sampleCount = 0
    var index = buffer.position()
    val lastSampleStart = buffer.limit() - BYTES_PER_FLOAT_SAMPLE
    while (index <= lastSampleStart) {
        val normalized = buffer.getFloat(index).toDouble()
        val absolute = abs(normalized)
        sumSquares += normalized * normalized
        peak = maxOf(peak, absolute)
        sampleCount++
        index += BYTES_PER_FLOAT_SAMPLE
    }
    val rms = if (sampleCount > 0) sqrt(sumSquares / sampleCount).toFloat() else 0f
    result.rms = rms
    result.peak = peak.toFloat()
    result.sampleCount = sampleCount
    return result
}

internal fun smoothingFactor(durationSeconds: Float, timeConstantSeconds: Float): Float {
    if (durationSeconds <= 0f) return 0f
    if (timeConstantSeconds <= 0f) return 1f
    return (1.0 - exp(-durationSeconds / timeConstantSeconds)).toFloat().coerceIn(0f, 1f)
}

private const val BYTES_PER_FLOAT_SAMPLE = 4
