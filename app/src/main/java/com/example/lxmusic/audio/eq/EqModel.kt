package com.example.lxmusic.audio.eq

import androidx.compose.runtime.Immutable
import java.util.Locale

/**
 * 均衡器频段（移植自 BoomingMusic 的 EqBand）：
 * 只描述某个频段的当前增益与可调范围，具体生效由 EqEngine 负责。
 */
@Immutable
data class EqBand(
    val index: Int,
    val gainDb: Float,
    val frequencyHz: Int,
    val range: ClosedFloatingPointRange<Float>
) {
    val readableFrequency: String
        get() = if (frequencyHz >= 1000) {
            String.format(Locale.ROOT, "%.1f kHz", frequencyHz / 1000f)
        } else {
            "$frequencyHz Hz"
        }
}

/** 均衡器预设：levels 与对应段数的中心频率一一对应 */
@Immutable
data class EqPreset(
    val name: String,
    val levels: List<Float>
) {
    /**
     * 把预设重采样到目标频段数（低音增强/自定义等预设只有 5/10/15 段，
     * 32 段时按"最近中心频率"取原预设增益，保证曲线形状一致）。
     */
    fun levelsFor(targetFrequencies: IntArray): List<Float> {
        if (levels.isEmpty()) return List(targetFrequencies.size) { 0f }
        if (levels.size == targetFrequencies.size) return levels
        val sourceFrequencies = EqPresets.frequencies(levels.size)
        return targetFrequencies.map { target ->
            var bestIndex = 0
            var bestDistance = Int.MAX_VALUE
            sourceFrequencies.forEachIndexed { index, source ->
                val distance = kotlin.math.abs(source - target)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }
            levels[bestIndex.coerceIn(levels.indices)]
        }
    }
}

/** 单个音效（低音增强 / 环绕声 / 响度增强）的开关与强度 */
@Immutable
data class EqEffectState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val value: Float = 0f,
    val range: ClosedFloatingPointRange<Float> = 0f..1000f
) {
    val isUsable: Boolean get() = supported && enabled
}

/** 压缩器（DynamicsProcessing MBC）参数，范围取自 BoomingMusic */
@Immutable
data class CompressorParams(
    val enabled: Boolean = false,
    val attackTimeMs: Float = 3f,
    val releaseTimeMs: Float = 80f,
    val kneeWidth: Float = 0f,
    val noiseGateThreshold: Float = -80f,
    val preGain: Float = 0f,
    val postGain: Float = 0f,
    val ratio: Float = 2f,
    val expanderRatio: Float = 1f,
    val threshold: Float = -12f
) {
    companion object {
        val ATTACK_RANGE = 0.1f..1000f
        val RELEASE_RANGE = 0.1f..1000f
        val KNEE_RANGE = 0f..50f
        val NOISE_GATE_RANGE = -100f..0f
        val GAIN_RANGE = -10f..10f
        val RATIO_RANGE = 1f..50f
        val THRESHOLD_RANGE = -50f..0f
    }
}

/** 限制器（DynamicsProcessing limiter）参数 */
@Immutable
data class LimiterParams(
    val enabled: Boolean = false,
    val attackTimeMs: Float = 1f,
    val releaseTimeMs: Float = 60f,
    val postGain: Float = 0f,
    val ratio: Float = 10f,
    val threshold: Float = -2f
) {
    companion object {
        val ATTACK_RANGE = 0.1f..1000f
        val RELEASE_RANGE = 0.1f..1000f
        val GAIN_RANGE = -10f..10f
        val RATIO_RANGE = 1f..50f
        val THRESHOLD_RANGE = -50f..0f
    }
}

/**
 * 均衡器页面的完整 UI 状态快照。
 * 由 EqualizerController 统一发布，页面只订阅不持有引擎。
 */
@Immutable
data class EqUiState(
    val supported: Boolean = true,
    val enabled: Boolean = false,
    /** 引擎已绑定到播放会话：false 时页面仍可调参，但要提示"暂未生效" */
    val active: Boolean = false,
    val engineLabel: String = "等待播放",
    val bandCount: Int = EqPresets.DEFAULT_BAND_COUNT,
    /** 当前引擎支持的频段数选项（5/10/15/32） */
    val availableBandCounts: List<Int> = EqPresets.AVAILABLE_BAND_COUNTS,
    val bandRange: ClosedFloatingPointRange<Float> = EqPresets.DEFAULT_BAND_RANGE,
    val bands: List<EqBand> = emptyList(),
    val presetName: String = EqPresets.FLAT_NAME,
    val proMode: Boolean = false,
    /** 引擎是否支持多段压缩 + 限制器（DynamicsProcessing 才有） */
    val proSupported: Boolean = true,
    val bassBoost: EqEffectState = EqEffectState(),
    val virtualizer: EqEffectState = EqEffectState(),
    val loudness: EqEffectState = EqEffectState(range = 0f..40f),
    val compressor: CompressorParams = CompressorParams(),
    val limiter: LimiterParams = LimiterParams(),
    /** USB 独占 / 高解析输出下均衡器无法生效时的提示文案 */
    val notice: String? = null
) {
    val isUsable: Boolean get() = supported && active
}

object EqPresets {

    const val FLAT_NAME = "平直"
    const val CUSTOM_NAME = "自定义"

    const val DEFAULT_BAND_COUNT = 10
    val DEFAULT_BAND_RANGE = -15f..15f
    val AVAILABLE_BAND_COUNTS = listOf(5, 10, 15, 32)

    private val FREQUENCIES_5 = intArrayOf(60, 230, 910, 3600, 14000)
    private val FREQUENCIES_10 =
        intArrayOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    private val FREQUENCIES_15 = intArrayOf(
        25, 40, 63, 100, 160, 250, 400, 630, 1000,
        1600, 2500, 4000, 6300, 10000, 16000
    )
    private val FREQUENCIES_32 = intArrayOf(
        16, 20, 25, 31, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500,
        630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000,
        10000, 12500, 16000, 20000
    )

    fun frequencies(bandCount: Int): IntArray = when (bandCount) {
        5 -> FREQUENCIES_5
        15 -> FREQUENCIES_15
        32 -> FREQUENCIES_32
        else -> FREQUENCIES_10
    }

    /** 内置预设（5/10/15 段各自一套，与 BoomingMusic 一致，仅名称中文化） */
    fun builtIn(bandCount: Int): List<EqPreset> = when (bandCount) {
        5 -> listOf(
            EqPreset(FLAT_NAME, listOf(0f, 0f, 0f, 0f, 0f)),
            EqPreset("低音增强", listOf(11f, 6f, -1f, -2.5f, -0.5f)),
            EqPreset("人声", listOf(-2.5f, 2f, 9f, 4f, -0.5f)),
            EqPreset("古典", listOf(4f, 0.5f, 0.5f, 2.5f, 4.5f)),
            EqPreset("爵士", listOf(4f, 2f, 1f, 3.5f, 1.5f)),
            EqPreset("流行", listOf(0.5f, 6f, 2f, 0.5f, 4.5f)),
            EqPreset("摇滚", listOf(7f, 2.5f, -2f, 3.5f, 7.5f)),
            EqPreset("高音增强", listOf(-2f, -0.5f, 2f, 7f, 11f))
        )

        15 -> listOf(
            EqPreset(FLAT_NAME, List(15) { 0f }),
            EqPreset(
                "低音增强",
                listOf(13f, 12f, 10f, 8f, 6f, 3f, 0f, -2f, -3f, -3f, -2f, -1f, 0f, 0f, 0f)
            ),
            EqPreset(
                "人声",
                listOf(-4f, -3f, -2f, 0f, 4f, 8f, 11f, 12f, 9f, 6f, 3f, 1f, 0f, -1f, -1f)
            ),
            EqPreset(
                "古典",
                listOf(6f, 5f, 3f, 2f, 1f, 0f, 0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 7f)
            ),
            EqPreset(
                "爵士",
                listOf(4f, 5f, 4f, 3f, 2f, 1f, 0f, 2f, 4f, 5f, 4f, 3f, 2f, 1f, 0f)
            ),
            EqPreset(
                "流行",
                listOf(-1f, 0f, 3f, 5f, 7f, 5f, 2f, 0f, -1f, 0f, 2f, 4f, 5f, 6f, 6f)
            ),
            EqPreset(
                "摇滚",
                listOf(9f, 8f, 6f, 4f, 2f, 0f, -2f, -3f, -2f, 0f, 3f, 6f, 8f, 9f, 10f)
            ),
            EqPreset(
                "高音增强",
                listOf(-3f, -2f, -1f, 0f, 1f, 2f, 3f, 4f, 6f, 8f, 10f, 12f, 13f, 14f, 14f)
            )
        )

        else -> listOf(
            EqPreset(FLAT_NAME, List(10) { 0f }),
            EqPreset("低音增强", listOf(12f, 10f, 8f, 4f, 0f, -2f, -3f, -2f, -1f, 0f)),
            EqPreset("人声", listOf(-3f, -2f, 0f, 4f, 8f, 10f, 6f, 2f, 0f, -1f)),
            EqPreset("古典", listOf(5f, 3f, 1f, 0f, 0f, 1f, 2f, 3f, 4f, 5f)),
            EqPreset("爵士", listOf(3f, 5f, 3f, 1f, 0f, 2f, 4f, 3f, 2f, 1f)),
            EqPreset("流行", listOf(-1f, 2f, 5f, 7f, 4f, 0f, -1f, 2f, 4f, 5f)),
            EqPreset("摇滚", listOf(8f, 6f, 4f, 1f, -2f, -2f, 2f, 5f, 7f, 8f)),
            EqPreset("高音增强", listOf(-2f, -2f, -1f, 0f, 1f, 3f, 6f, 8f, 10f, 12f))
        )
    }

    fun flat(bandCount: Int): EqPreset = EqPreset(FLAT_NAME, List(bandCount) { 0f })
}
