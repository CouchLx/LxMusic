@file:Suppress("DEPRECATION")

package com.example.lxmusic.audio.eq

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlin.math.roundToInt

/**
 * 均衡器引擎抽象（移植自 BoomingMusic 的 EQEngine）。
 *
 * 两种实现：
 * - [BasicEqEngine]：系统 `android.media.audiofx.Equalizer`（5 段左右，全版本可用）
 * - [DynamicsProcessingEqEngine]：`DynamicsProcessing`（API 28+，5/10/15/32 段，
 *   额外支持多段压缩与限制器）
 *
 * 引擎直接绑定播放会话（ExoPlayer 的 audioSessionId），所有 set 方法都做了
 * 异常兜底：部分机型/输出通道不支持对应音效时只是静默失败，不影响播放。
 */
abstract class EqEngine(val sessionId: Int) {

    private val bassBoost: BassBoost? =
        execOpCatchingWithTag("Create BassBoost") { BassBoost(0, sessionId) }

    private val virtualizer: Virtualizer? =
        execOpCatchingWithTag("Create Virtualizer") { Virtualizer(0, sessionId) }

    private val loudnessEnhancer: LoudnessEnhancer? =
        execOpCatchingWithTag("Create LoudnessEnhancer") { LoudnessEnhancer(sessionId) }

    abstract val isEnabled: Boolean
    /** 底层音效对象是否创建成功（失败时调用方回退基础引擎） */
    abstract val isAvailable: Boolean
    abstract val isMultiBandCompressorSupported: Boolean
    abstract val isLimiterSupported: Boolean
    abstract val bandCapabilities: EqBandCapabilities
    abstract fun setEnabled(isEnabled: Boolean)
    abstract fun setLevels(levels: List<Float>)
    abstract fun setBandGain(bandIndex: Int, bandGainInDecibels: Float)
    abstract fun setBandCount(bandCount: Int): Boolean
    abstract fun setInputGain(inputGain: Float): Boolean
    abstract fun setCompressor(params: CompressorParams): Boolean
    abstract fun setLimiter(params: LimiterParams): Boolean

    fun setVirtualizerState(state: EqEffectState): Boolean {
        return execOpCatching(virtualizer, false) { effect ->
            if (effect.enabled != state.enabled) {
                effect.enabled = state.enabled
                if (!state.enabled) {
                    effect.setStrength(0)
                    return@execOpCatching true
                }
            }
            val roundedStrength = state.value.toInt().toShort()
            if (effect.roundedStrength != roundedStrength) {
                effect.setStrength(roundedStrength)
            }
            true
        }
    }

    fun setBassBoostState(state: EqEffectState): Boolean {
        return execOpCatching(bassBoost, false) { effect ->
            if (effect.enabled != state.enabled) {
                effect.enabled = state.enabled
                if (!state.enabled) {
                    effect.setStrength(0)
                    return@execOpCatching true
                }
            }
            val roundedStrength = state.value.toInt().toShort()
            if (effect.roundedStrength != roundedStrength) {
                effect.setStrength(roundedStrength)
            }
            true
        }
    }

    fun setLoudnessGainState(state: EqEffectState): Boolean {
        return execOpCatching(loudnessEnhancer, false) { effect ->
            if (effect.enabled != state.enabled) {
                effect.enabled = state.enabled
                if (!state.enabled) {
                    effect.setTargetGain(0)
                    return@execOpCatching true
                }
            }
            // LoudnessEnhancer 的 targetGain 单位是 0.01dB
            val targetGain = (state.value * 100f).coerceIn(0f, 4000f).roundToInt()
            if (effect.targetGain != targetGain.toFloat()) {
                effect.setTargetGain(targetGain)
            }
            true
        }
    }

    open fun release() {
        execOpCatching(bassBoost, Unit) { it.release() }
        execOpCatching(virtualizer, Unit) { it.release() }
        execOpCatching(loudnessEnhancer, Unit) { it.release() }
    }

    protected inline fun <R> execOpCatchingWithTag(tag: String, callback: () -> R): R? {
        synchronized(this) {
            return try {
                callback()
            } catch (t: Throwable) {
                Log.d(TAG, "$tag 失败", t)
                null
            }
        }
    }

    protected inline fun <A, R> execOpCatching(argument: A?, default: R, callback: (A) -> R): R {
        synchronized(this) {
            if (argument == null) return default
            return try {
                callback(argument)
            } catch (t: Throwable) {
                Log.d(TAG, "音效操作失败", t)
                default
            }
        }
    }

    companion object {
        // inline 辅助函数会读取该常量，需对编译器公开
        @PublishedApi
        internal const val TAG = "LxEqEngine"
    }
}

/** 系统自带 Equalizer（Android 全版本可用，段数由设备决定） */
class BasicEqEngine(sessionId: Int) : EqEngine(sessionId) {

    private var equalizer: Equalizer? = execOpCatchingWithTag("Create Equalizer") {
        Equalizer(0, sessionId)
    }

    override val isEnabled: Boolean
        get() = execOpCatching(equalizer, false) { it.enabled }

    override val isAvailable: Boolean get() = equalizer != null

    override val isMultiBandCompressorSupported: Boolean = false
    override val isLimiterSupported: Boolean = false

    override val bandCapabilities: EqBandCapabilities
        get() = execOpCatching(equalizer, EqBandCapabilities.Empty) { eq ->
            val bandCount = eq.numberOfBands.toInt()
            val bandLevelRange = eq.bandLevelRange.map { (it / MILLIBELS_FACTOR).toFloat() }
            val bandFrequenciesInHz = (0 until bandCount).map {
                // getCenterFreq 返回的是毫赫兹
                eq.getCenterFreq(it.toShort()) / MILLIHERTZ_FACTOR
            }
            EqBandCapabilities(
                bandRange = bandLevelRange[0]..bandLevelRange[1],
                bandConfigurations = listOf(
                    EqBandCapabilities.BandConfiguration(bandCount, bandFrequenciesInHz.toIntArray())
                ),
                maxBandCountInNormalMode = bandCount
            )
        }

    override fun setEnabled(isEnabled: Boolean) {
        execOpCatching(equalizer, Unit) { eq ->
            if (eq.enabled != isEnabled) eq.enabled = isEnabled
        }
    }

    override fun setLevels(levels: List<Float>) {
        execOpCatching(equalizer, Unit) { eq ->
            val bandCount = eq.numberOfBands.toInt()
            if (bandCount > 0 && levels.size == bandCount) {
                for (i in 0 until bandCount) {
                    val bandGainInMillibels = (levels[i] * MILLIBELS_FACTOR).roundToInt()
                    eq.setBandLevel(i.toShort(), bandGainInMillibels.toShort())
                }
            }
        }
    }

    override fun setBandGain(bandIndex: Int, bandGainInDecibels: Float) {
        execOpCatching(equalizer, Unit) { eq ->
            val bandGainInMillibels = (bandGainInDecibels * MILLIBELS_FACTOR).roundToInt()
            eq.setBandLevel(bandIndex.toShort(), bandGainInMillibels.toShort())
        }
    }

    override fun setBandCount(bandCount: Int): Boolean = false

    override fun setInputGain(inputGain: Float): Boolean = false

    override fun setCompressor(params: CompressorParams): Boolean = false

    override fun setLimiter(params: LimiterParams): Boolean = false

    override fun release() {
        super.release()
        execOpCatching(equalizer, Unit) { it.release() }
        equalizer = null
    }

    companion object {
        private const val MILLIHERTZ_FACTOR = 1000
        private const val MILLIBELS_FACTOR = 100
    }
}

/** DynamicsProcessing 引擎：多段图示均衡 + 多段压缩 + 限制器 */
@RequiresApi(Build.VERSION_CODES.P)
class DynamicsProcessingEqEngine(sessionId: Int, bandCount: Int) : EqEngine(sessionId) {

    private var dynamicsProcessing: android.media.audiofx.DynamicsProcessing? =
        createDynamicsProcessing(bandCount)

    private var appliedCompressor: CompressorParams? = null
    private var appliedLimiter: LimiterParams? = null

    override val isMultiBandCompressorSupported: Boolean = true
    override val isLimiterSupported: Boolean = true

    override val isEnabled: Boolean
        get() = execOpCatching(dynamicsProcessing, false) { it.enabled }

    override val isAvailable: Boolean get() = dynamicsProcessing != null

    override val bandCapabilities: EqBandCapabilities
        get() = EqBandCapabilities(
            bandRange = BAND_RANGE,
            bandConfigurations = arrayOf(5, 10, 15, 32).map { count ->
                EqBandCapabilities.BandConfiguration(count, EqPresets.frequencies(count))
            },
            maxBandCountInNormalMode = 15
        )

    override fun setEnabled(isEnabled: Boolean) {
        execOpCatching(dynamicsProcessing, Unit) {
            if (it.enabled != isEnabled) it.enabled = isEnabled
        }
    }

    override fun setLevels(levels: List<Float>) {
        execOpCatching(dynamicsProcessing, Unit) { dp ->
            for (channelIndex in 0 until dp.channelCount) {
                val preEq = dp.getPreEqByChannelIndex(channelIndex)
                if (preEq.bandCount == levels.size) {
                    levels.forEachIndexed { bandIndex, gain ->
                        val band = preEq.getBand(bandIndex)
                        if (band.gain != gain) {
                            band.gain = gain
                            preEq.setBand(bandIndex, band)
                        }
                    }
                    dp.setPreEqByChannelIndex(channelIndex, preEq)
                }
            }
        }
    }

    override fun setBandGain(bandIndex: Int, bandGainInDecibels: Float) {
        execOpCatching(dynamicsProcessing, Unit) { dp ->
            for (channelIndex in 0 until dp.channelCount) {
                val preEq = dp.getPreEqByChannelIndex(channelIndex)
                if (bandIndex < preEq.bandCount) {
                    val band = preEq.getBand(bandIndex)
                    if (band.gain != bandGainInDecibels) {
                        band.gain = bandGainInDecibels
                        preEq.setBand(bandIndex, band)
                    }
                    dp.setPreEqByChannelIndex(channelIndex, preEq)
                }
            }
        }
    }

    override fun setBandCount(bandCount: Int): Boolean {
        val current = dynamicsProcessing ?: return false
        // 段数没变：直接返回，避免无谓重建导致的瞬间爆音
        val alreadyConfigured = runCatching {
            current.getPreEqByChannelIndex(current.channelCount - 1).bandCount == bandCount
        }.getOrDefault(false)
        if (alreadyConfigured) return true

        val oldIsEnabled = isEnabled
        runCatching {
            current.setEnabled(false)
            current.release()
        }
        dynamicsProcessing = createDynamicsProcessing(bandCount)
        return execOpCatching(dynamicsProcessing, false) { newDp ->
            newDp.setEnabled(oldIsEnabled)
            newDp.getPreEqByChannelIndex(newDp.channelCount - 1).bandCount == bandCount
        }
    }

    override fun setInputGain(inputGain: Float): Boolean {
        return execOpCatching(dynamicsProcessing, false) { dp ->
            dp.setInputGainAllChannelsTo(inputGain)
            true
        }
    }

    override fun setCompressor(params: CompressorParams): Boolean {
        if (appliedCompressor == params) return false
        appliedCompressor = params
        return execOpCatching(dynamicsProcessing, false) { dp ->
            for (channelIndex in 0 until dp.channelCount) {
                val mbc = dp.getMbcByChannelIndex(channelIndex)
                if (mbc.isEnabled != params.enabled) mbc.isEnabled = params.enabled
                for (bandIndex in 0 until mbc.bandCount) {
                    val band = mbc.getBand(bandIndex)
                    band.ratio = params.ratio
                    band.expanderRatio = params.expanderRatio
                    band.threshold = params.threshold
                    band.attackTime = params.attackTimeMs
                    band.releaseTime = params.releaseTimeMs
                    band.preGain = params.preGain
                    band.postGain = params.postGain
                    band.kneeWidth = params.kneeWidth
                    band.noiseGateThreshold = params.noiseGateThreshold
                    mbc.setBand(bandIndex, band)
                }
                dp.setMbcByChannelIndex(channelIndex, mbc)
            }
            true
        }
    }

    override fun setLimiter(params: LimiterParams): Boolean {
        if (appliedLimiter == params) return false
        appliedLimiter = params
        return execOpCatching(dynamicsProcessing, false) { dp ->
            for (channelIndex in 0 until dp.channelCount) {
                val limiter = dp.getLimiterByChannelIndex(channelIndex)
                limiter.isEnabled = params.enabled
                limiter.ratio = params.ratio
                limiter.threshold = params.threshold
                limiter.attackTime = params.attackTimeMs
                limiter.releaseTime = params.releaseTimeMs
                limiter.postGain = params.postGain
                dp.setLimiterByChannelIndex(channelIndex, limiter)
            }
            true
        }
    }

    override fun release() {
        super.release()
        execOpCatching(dynamicsProcessing, Unit) { it.setEnabled(false) }
        execOpCatching(dynamicsProcessing, Unit) { it.release() }
        dynamicsProcessing = null
    }

    private fun createDynamicsProcessing(bandCount: Int) =
        execOpCatchingWithTag("Create DynamicsProcessing") {
            val builder = android.media.audiofx.DynamicsProcessing.Config.Builder(
                /* variant */ android.media.audiofx.DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                /* channelCount */ CHANNEL_COUNT,
                /* preEqInUse */ true,
                /* preEqBandCount */ bandCount,
                /* mbcInUse */ true,
                /* mbcBandCount */ bandCount,
                /* postEqInUse */ false,
                /* postEqBandCount */ 0,
                /* limiterInUse */ true
            )
            android.media.audiofx.DynamicsProcessing(0, sessionId, builder.build()).also { dp ->
                val freqInHz = EqPresets.frequencies(bandCount)
                for (channelIndex in 0 until dp.channelCount) {
                    val preEq = dp.getPreEqByChannelIndex(channelIndex)
                    val mbc = dp.getMbcByChannelIndex(channelIndex)
                    for (bandIndex in 0 until bandCount) {
                        preEq.getBand(bandIndex).apply {
                            isEnabled = true
                            cutoffFrequency = freqInHz[bandIndex].toFloat()
                        }
                        mbc.getBand(bandIndex).apply {
                            isEnabled = true
                            cutoffFrequency = freqInHz[bandIndex].toFloat()
                        }
                    }
                    preEq.isEnabled = true
                    mbc.isEnabled = true
                    dp.setPreEqByChannelIndex(channelIndex, preEq)
                    dp.setMbcByChannelIndex(channelIndex, mbc)

                    val limiter = dp.getLimiterByChannelIndex(channelIndex)
                    limiter.isEnabled = true
                    dp.setLimiterByChannelIndex(channelIndex, limiter)
                }
            }
        }

    companion object {
        private const val CHANNEL_COUNT = 2
        private val BAND_RANGE = -15f..15f
    }
}

/** 引擎能力：增益范围 + 支持的频段配置 */
class EqBandCapabilities(
    val bandRange: ClosedFloatingPointRange<Float>,
    val bandConfigurations: List<BandConfiguration>,
    val maxBandCountInNormalMode: Int
) {
    val hasMultipleBandConfigurations: Boolean get() = bandConfigurations.size > 1

    fun getAvailableBandCounts(isProMode: Boolean): List<Int> =
        bandConfigurations
            .filter { it.bandCount <= maxBandCountInNormalMode || isProMode }
            .map { it.bandCount }
            .sorted()

    fun isBandCountSupported(bandCount: Int): Boolean =
        bandConfigurations.any { it.bandCount == bandCount }

    fun getFrequencies(bandCount: Int): IntArray =
        bandConfigurations.firstOrNull { it.bandCount == bandCount }?.bandFrequenciesInHz
            ?: EqPresets.frequencies(bandCount)

    class BandConfiguration(val bandCount: Int, val bandFrequenciesInHz: IntArray)

    companion object {
        val Empty = EqBandCapabilities(-15f..15f, emptyList(), 0)
    }
}

/** 按系统版本选择引擎；返回 null 表示当前设备无法创建 */
fun createEqEngine(sessionId: Int, bandCount: Int): EqEngine? {
    val preferred = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DynamicsProcessingEqEngine(sessionId, bandCount)
        } else {
            BasicEqEngine(sessionId)
        }
    }.onFailure {
        Log.e("LxEqEngine", "创建均衡器引擎失败，回退基础 Equalizer", it)
    }.getOrNull()

    if (preferred?.isAvailable == true) return preferred
    preferred?.release()
    return runCatching { BasicEqEngine(sessionId) }
        .onFailure { Log.e("LxEqEngine", "基础均衡器同样不可用", it) }
        .getOrNull()
        ?.takeIf { it.isAvailable }
}
