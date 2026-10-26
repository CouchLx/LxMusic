package com.example.lxmusic.audio.eq

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 均衡器全局控制器（对应 BoomingMusic 的 EqualizerManager）。
 *
 * 职责：
 * 1. 用 SharedPreferences 持久化全部均衡器设置；
 * 2. 绑定 PlayerService 里 ExoPlayer 的 audioSessionId，创建/切换 [EqEngine]；
 * 3. 向 Compose 页面发布 [EqUiState]（页面只读状态、调 setXxx）。
 *
 * 线程约束：所有 public 方法都可在任意线程调用，内部统一切到主线程串行执行，
 * 避免音效 API 与状态更新竞争。
 */
object EqualizerController {

    private const val TAG = "LxEqualizer"
    private const val PREFS_NAME = "equalizer"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_BAND_COUNT = "band_count"
    private const val KEY_LEVELS = "levels"
    private const val KEY_PRESET = "preset"
    private const val KEY_PRO_MODE = "pro_mode"
    private const val KEY_BASS_ENABLED = "bass_enabled"
    private const val KEY_BASS_STRENGTH = "bass_strength"
    private const val KEY_VIRTUALIZER_ENABLED = "virtualizer_enabled"
    private const val KEY_VIRTUALIZER_STRENGTH = "virtualizer_strength"
    private const val KEY_LOUDNESS_ENABLED = "loudness_enabled"
    private const val KEY_LOUDNESS_GAIN = "loudness_gain"
    private const val KEY_COMPRESSOR = "compressor"
    private const val KEY_LIMITER = "limiter"

    private val mainHandler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var initialized = false

    private var engine: EqEngine? = null
    private var sessionId = 0

    private val _state = MutableStateFlow(EqUiState())
    val state: StateFlow<EqUiState> = _state.asStateFlow()

    // ==================== 设置镜像 ====================
    private var eqSupported = true
    private var enabled = false
    private var bandCount = EqPresets.DEFAULT_BAND_COUNT
    private var levels: List<Float> = List(bandCount) { 0f }
    private var presetName = EqPresets.FLAT_NAME
    private var proMode = false
    private var bassBoost = EqEffectState(range = 0f..1000f)
    private var virtualizer = EqEffectState(range = 0f..1000f)
    private var loudness = EqEffectState(range = 0f..40f)
    private var compressor = CompressorParams()
    private var limiter = LimiterParams()

    private var highResOutput = false
    private var usbExclusive = false

    // ==================== 生命周期 ====================

    /** 应用启动时调用一次（PlayerService.onCreate） */
    fun initialize(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        appContext = app
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        initialized = true
        readFromPrefs()
        publish()
        querySupportedEffects()
    }

    /**
     * 绑定播放会话：ExoPlayer 的 audioSessionId 变化时调用
     * （PlayerService 里 Player.Listener.onAudioSessionIdChanged）。
     */
    fun attachSession(audioSessionId: Int) {
        runOnMain {
            if (audioSessionId == sessionId && engine != null) return@runOnMain
            releaseEngine()
            sessionId = audioSessionId
            ensureEngine()
            publish()
        }
    }

    /** 播放服务销毁时释放引擎（设置保留，下次播放重新挂载） */
    fun release() {
        runOnMain {
            releaseEngine()
            sessionId = 0
            publish()
        }
    }

    /** 重新读取输出相关开关（高解析/USB 独占），打开页面时刷新提示 */
    fun refreshOutputConstraints() {
        runOnMain {
            readOutputConstraints()
            publish()
        }
    }

    // ==================== 页面可调用的设置接口 ====================

    fun setEnabled(newEnabled: Boolean) {
        runOnMain {
            if (enabled == newEnabled) return@runOnMain
            enabled = newEnabled
            persist { putBoolean(KEY_ENABLED, enabled) }
            engine?.setEnabled(enabled)
            pushEffectsToEngine()
            publish()
        }
    }

    /** 拖动频段（不写盘，松手时调用 [commitLevels]） */
    fun setBandGain(index: Int, gainDb: Float) {
        runOnMain {
            if (index !in levels.indices) return@runOnMain
            levels = levels.toMutableList().also { it[index] = gainDb }
            presetName = EqPresets.CUSTOM_NAME
            engine?.setBandGain(index, gainDb)
            publish()
        }
    }

    /** 松手落盘（避免拖动过程中每秒几十次写入） */
    fun commitLevels() {
        runOnMain {
            persist {
                putString(KEY_LEVELS, encodeLevels(levels))
                putString(KEY_PRESET, presetName)
            }
        }
    }

    fun applyPreset(preset: EqPreset) {
        runOnMain {
            levels = preset.levelsFor(EqPresets.frequencies(bandCount))
            presetName = preset.name
            engine?.setLevels(levels)
            persist {
                putString(KEY_LEVELS, encodeLevels(levels))
                putString(KEY_PRESET, presetName)
            }
            publish()
        }
    }

    fun setBandCount(newBandCount: Int) {
        runOnMain {
            if (newBandCount == bandCount) return@runOnMain
            bandCount = newBandCount
            levels = resizeLevels(levels, newBandCount)
            engine?.setBandCount(newBandCount)
            // 段数切换会重建 DynamicsProcessing 实例，音效/压缩/限制参数需要重新下发
            pushEffectsToEngine()
            persist {
                putInt(KEY_BAND_COUNT, bandCount)
                putString(KEY_LEVELS, encodeLevels(levels))
            }
            publish()
        }
    }

    fun setProMode(newProMode: Boolean) {
        runOnMain {
            if (proMode == newProMode) return@runOnMain
            proMode = newProMode
            persist { putBoolean(KEY_PRO_MODE, proMode) }
            if (!proMode) {
                // 退出专业模式：压缩/限制全部关闭，避免残留音染
                compressor = compressor.copy(enabled = false)
                limiter = limiter.copy(enabled = false)
            }
            pushEffectsToEngine()
            publish()
        }
    }

    fun setBassBoost(enabled: Boolean? = null, strength: Float? = null) {
        runOnMain {
            bassBoost = bassBoost.copy(
                enabled = enabled ?: bassBoost.enabled,
                value = strength ?: bassBoost.value
            )
            persist {
                putBoolean(KEY_BASS_ENABLED, bassBoost.enabled)
                putFloat(KEY_BASS_STRENGTH, bassBoost.value)
            }
            pushEffectsToEngine()
            publish()
        }
    }

    fun setVirtualizer(enabled: Boolean? = null, strength: Float? = null) {
        runOnMain {
            virtualizer = virtualizer.copy(
                enabled = enabled ?: virtualizer.enabled,
                value = strength ?: virtualizer.value
            )
            persist {
                putBoolean(KEY_VIRTUALIZER_ENABLED, virtualizer.enabled)
                putFloat(KEY_VIRTUALIZER_STRENGTH, virtualizer.value)
            }
            pushEffectsToEngine()
            publish()
        }
    }

    fun setLoudnessGain(enabled: Boolean? = null, gainDb: Float? = null) {
        runOnMain {
            loudness = loudness.copy(
                enabled = enabled ?: loudness.enabled,
                value = gainDb ?: loudness.value
            )
            persist {
                putBoolean(KEY_LOUDNESS_ENABLED, loudness.enabled)
                putFloat(KEY_LOUDNESS_GAIN, loudness.value)
            }
            pushEffectsToEngine()
            publish()
        }
    }

    fun setCompressor(
        enabled: Boolean? = null,
        threshold: Float? = null,
        ratio: Float? = null,
        attackTimeMs: Float? = null,
        releaseTimeMs: Float? = null,
        kneeWidth: Float? = null,
        noiseGateThreshold: Float? = null,
        preGain: Float? = null,
        postGain: Float? = null
    ) {
        runOnMain {
            val next = compressor.copy(
                enabled = enabled ?: compressor.enabled,
                threshold = threshold ?: compressor.threshold,
                ratio = ratio ?: compressor.ratio,
                attackTimeMs = attackTimeMs ?: compressor.attackTimeMs,
                releaseTimeMs = releaseTimeMs ?: compressor.releaseTimeMs,
                kneeWidth = kneeWidth ?: compressor.kneeWidth,
                noiseGateThreshold = noiseGateThreshold ?: compressor.noiseGateThreshold,
                preGain = preGain ?: compressor.preGain,
                postGain = postGain ?: compressor.postGain
            )
            if (next == compressor) return@runOnMain
            compressor = next
            persist { putString(KEY_COMPRESSOR, encodeCompressor(next)) }
            pushEffectsToEngine()
            publish()
        }
    }

    fun setLimiter(
        enabled: Boolean? = null,
        threshold: Float? = null,
        ratio: Float? = null,
        attackTimeMs: Float? = null,
        releaseTimeMs: Float? = null,
        postGain: Float? = null
    ) {
        runOnMain {
            val next = limiter.copy(
                enabled = enabled ?: limiter.enabled,
                threshold = threshold ?: limiter.threshold,
                ratio = ratio ?: limiter.ratio,
                attackTimeMs = attackTimeMs ?: limiter.attackTimeMs,
                releaseTimeMs = releaseTimeMs ?: limiter.releaseTimeMs,
                postGain = postGain ?: limiter.postGain
            )
            if (next == limiter) return@runOnMain
            limiter = next
            persist { putString(KEY_LIMITER, encodeLimiter(next)) }
            pushEffectsToEngine()
            publish()
        }
    }

    fun resetAll() {
        runOnMain {
            bandCount = EqPresets.DEFAULT_BAND_COUNT
            levels = List(bandCount) { 0f }
            presetName = EqPresets.FLAT_NAME
            bassBoost = bassBoost.copy(enabled = false, value = 0f)
            virtualizer = virtualizer.copy(enabled = false, value = 0f)
            loudness = loudness.copy(enabled = false, value = 0f)
            compressor = CompressorParams()
            limiter = LimiterParams()
            engine?.setBandCount(bandCount)
            engine?.setLevels(levels)
            pushEffectsToEngine()
            persist {
                putInt(KEY_BAND_COUNT, bandCount)
                putString(KEY_LEVELS, encodeLevels(levels))
                putString(KEY_PRESET, presetName)
                putBoolean(KEY_BASS_ENABLED, false)
                putFloat(KEY_BASS_STRENGTH, 0f)
                putBoolean(KEY_VIRTUALIZER_ENABLED, false)
                putFloat(KEY_VIRTUALIZER_STRENGTH, 0f)
                putBoolean(KEY_LOUDNESS_ENABLED, false)
                putFloat(KEY_LOUDNESS_GAIN, 0f)
                putString(KEY_COMPRESSOR, encodeCompressor(compressor))
                putString(KEY_LIMITER, encodeLimiter(limiter))
            }
            publish()
        }
    }

    // ==================== 内部：引擎 ====================

    private fun ensureEngine() {
        if (!initialized || !eqSupported || sessionId == 0) return
        if (engine != null) return
        val created = createEqEngine(sessionId, bandCount) ?: run {
            eqSupported = false
            Log.w(TAG, "设备不支持系统均衡器")
            return
        }
        engine = created

        // 基础 Equalizer 的段数由系统决定：对齐 UI 段数与实际能力
        val capabilities = created.bandCapabilities
        val actualBandCount = capabilities.bandConfigurations.firstOrNull()?.bandCount ?: bandCount
        if (!capabilities.hasMultipleBandConfigurations &&
            actualBandCount > 0 && actualBandCount != bandCount
        ) {
            bandCount = actualBandCount
            levels = resizeLevels(levels, bandCount)
            persist {
                putInt(KEY_BAND_COUNT, bandCount)
                putString(KEY_LEVELS, encodeLevels(levels))
            }
        }

        created.setEnabled(false)
        created.setBandCount(bandCount)
        created.setLevels(levels)
        created.setEnabled(enabled)
        pushEffectsToEngine()
    }

    private fun releaseEngine() {
        engine?.let { runCatching { it.release() } }
        engine = null
    }

    private fun pushEffectsToEngine() {
        val engine = engine ?: return
        engine.setEnabled(enabled)
        val engineBandCount = engine.bandCapabilities.bandConfigurations
            .firstOrNull()?.bandCount ?: bandCount
        if (engineBandCount == bandCount) {
            engine.setLevels(levels)
        }
        engine.setBassBoostState(
            if (enabled && bassBoost.isUsable) bassBoost else bassBoost.copy(enabled = false)
        )
        engine.setVirtualizerState(
            if (enabled && virtualizer.isUsable) virtualizer else virtualizer.copy(enabled = false)
        )
        engine.setLoudnessGainState(
            if (enabled && loudness.isUsable) loudness else loudness.copy(enabled = false)
        )
        engine.setCompressor(
            if (enabled && proMode && compressor.enabled) compressor
            else compressor.copy(enabled = false)
        )
        engine.setLimiter(
            if (enabled && proMode && limiter.enabled) limiter
            else limiter.copy(enabled = false)
        )
    }

    private fun querySupportedEffects() {
        Thread {
            val effects = runCatching { AudioEffect.queryEffects()?.toList() }.getOrNull()
            if (effects != null) {
                // 均衡器本身不在这里判定（部分 ROM 不把 DynamicsProcessing 列进描述表），
                // 交给引擎创建结果决定；这里只探测三个可选音效是否可用
                val hasBass = effects.any { it.type == AudioEffect.EFFECT_TYPE_BASS_BOOST }
                val hasVirtualizer = effects.any { it.type == AudioEffect.EFFECT_TYPE_VIRTUALIZER }
                val hasLoudness =
                    effects.any { it.type == AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER }
                mainHandler.post {
                    bassBoost = bassBoost.copy(supported = hasBass)
                    virtualizer = virtualizer.copy(supported = hasVirtualizer)
                    loudness = loudness.copy(supported = hasLoudness)
                    publish()
                }
            } else {
                mainHandler.post { publish() }
            }
        }.also { it.isDaemon = true }.start()
    }

    // ==================== 内部：持久化 ====================

    private fun readOutputConstraints() {
        val settings = appContext?.getSharedPreferences("settings", Context.MODE_PRIVATE)
            ?: return
        highResOutput = settings.getBoolean("playback_high_res", false)
        usbExclusive = settings.getBoolean("usb_exclusive_playback", false)
    }

    private fun readFromPrefs() {
        val prefs = prefs ?: return
        enabled = prefs.getBoolean(KEY_ENABLED, false)
        bandCount = prefs.getInt(KEY_BAND_COUNT, EqPresets.DEFAULT_BAND_COUNT)
            .takeIf { it in EqPresets.AVAILABLE_BAND_COUNTS }
            ?: EqPresets.DEFAULT_BAND_COUNT
        levels = decodeLevels(prefs.getString(KEY_LEVELS, null), bandCount)
        presetName = prefs.getString(KEY_PRESET, EqPresets.FLAT_NAME) ?: EqPresets.FLAT_NAME
        proMode = prefs.getBoolean(KEY_PRO_MODE, false)
        bassBoost = bassBoost.copy(
            enabled = prefs.getBoolean(KEY_BASS_ENABLED, false),
            value = prefs.getFloat(KEY_BASS_STRENGTH, 0f)
        )
        virtualizer = virtualizer.copy(
            enabled = prefs.getBoolean(KEY_VIRTUALIZER_ENABLED, false),
            value = prefs.getFloat(KEY_VIRTUALIZER_STRENGTH, 0f)
        )
        loudness = loudness.copy(
            enabled = prefs.getBoolean(KEY_LOUDNESS_ENABLED, false),
            value = prefs.getFloat(KEY_LOUDNESS_GAIN, 0f)
        )
        compressor = decodeCompressor(prefs.getString(KEY_COMPRESSOR, null))
        limiter = decodeLimiter(prefs.getString(KEY_LIMITER, null))
        readOutputConstraints()
    }

    private fun persist(block: SharedPreferences.Editor.() -> Unit) {
        val editor = prefs?.edit() ?: return
        block(editor)
        editor.apply()
    }

    private fun encodeLevels(levels: List<Float>): String =
        levels.joinToString(",") { String.format(java.util.Locale.US, "%.2f", it) }

    private fun decodeLevels(raw: String?, count: Int): List<Float> {
        val parsed = raw?.split(',')
            ?.mapNotNull { it.trim().toFloatOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: emptyList()
        return resizeLevels(parsed, count)
    }

    private fun resizeLevels(source: List<Float>, count: Int): List<Float> =
        List(count) { index -> source.getOrElse(index) { 0f } }

    private fun encodeCompressor(params: CompressorParams): String = listOf(
        if (params.enabled) "1" else "0",
        params.threshold,
        params.ratio,
        params.attackTimeMs,
        params.releaseTimeMs,
        params.kneeWidth,
        params.noiseGateThreshold,
        params.preGain,
        params.postGain
    ).joinToString(",")

    private fun decodeCompressor(raw: String?): CompressorParams {
        val values = raw?.split(',') ?: return CompressorParams()
        if (values.size < 9) return CompressorParams()
        fun floatAt(index: Int, fallback: Float) = values[index].toFloatOrNull() ?: fallback
        return CompressorParams(
            enabled = values[0] == "1",
            threshold = floatAt(1, CompressorParams().threshold),
            ratio = floatAt(2, CompressorParams().ratio),
            attackTimeMs = floatAt(3, CompressorParams().attackTimeMs),
            releaseTimeMs = floatAt(4, CompressorParams().releaseTimeMs),
            kneeWidth = floatAt(5, CompressorParams().kneeWidth),
            noiseGateThreshold = floatAt(6, CompressorParams().noiseGateThreshold),
            preGain = floatAt(7, CompressorParams().preGain),
            postGain = floatAt(8, CompressorParams().postGain)
        )
    }

    private fun encodeLimiter(params: LimiterParams): String = listOf(
        if (params.enabled) "1" else "0",
        params.threshold,
        params.ratio,
        params.attackTimeMs,
        params.releaseTimeMs,
        params.postGain
    ).joinToString(",")

    private fun decodeLimiter(raw: String?): LimiterParams {
        val values = raw?.split(',') ?: return LimiterParams()
        if (values.size < 6) return LimiterParams()
        fun floatAt(index: Int, fallback: Float) = values[index].toFloatOrNull() ?: fallback
        return LimiterParams(
            enabled = values[0] == "1",
            threshold = floatAt(1, LimiterParams().threshold),
            ratio = floatAt(2, LimiterParams().ratio),
            attackTimeMs = floatAt(3, LimiterParams().attackTimeMs),
            releaseTimeMs = floatAt(4, LimiterParams().releaseTimeMs),
            postGain = floatAt(5, LimiterParams().postGain)
        )
    }

    // ==================== 内部：发布状态 ====================

    private fun publish() {
        readOutputConstraints()
        val engineCapabilities = engine?.bandCapabilities
        val bandRange = engineCapabilities?.bandRange ?: EqPresets.DEFAULT_BAND_RANGE
        val frequencies = engineCapabilities?.bandConfigurations
            ?.firstOrNull { it.bandCount == bandCount }
            ?.bandFrequenciesInHz
            ?: EqPresets.frequencies(bandCount)
        val headlineLevels = resizeLevels(levels, frequencies.size)
        val bands = frequencies.mapIndexed { index, frequency ->
            EqBand(
                index = index,
                gainDb = headlineLevels[index],
                frequencyHz = frequency,
                range = bandRange
            )
        }
        val availableBandCounts = when {
            engineCapabilities == null -> EqPresets.AVAILABLE_BAND_COUNTS
            engineCapabilities.hasMultipleBandConfigurations ->
                engineCapabilities.getAvailableBandCounts(isProMode = true)
            else -> listOf(
                engineCapabilities.bandConfigurations.firstOrNull()?.bandCount ?: bandCount
            )
        }

        _state.value = EqUiState(
            supported = eqSupported,
            enabled = enabled,
            active = engine?.isEnabled == true,
            engineLabel = engineLabel(),
            bandCount = bandCount,
            availableBandCounts = availableBandCounts,
            bandRange = bandRange,
            bands = bands,
            presetName = presetName,
            proMode = proMode,
            proSupported = engine?.isMultiBandCompressorSupported
                ?: (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P),
            bassBoost = bassBoost,
            virtualizer = virtualizer,
            loudness = loudness,
            compressor = compressor,
            limiter = limiter,
            notice = buildNotice()
        )
    }

    private fun engineLabel(): String = when (val current = engine) {
        null -> if (sessionId == 0) "等待播放" else "未连接"
        is DynamicsProcessingEqEngine -> "DynamicsProcessing · $bandCount 段"
        else -> "系统均衡器 · $bandCount 段"
    }

    private fun buildNotice(): String? = when {
        usbExclusive -> "USB 独占直通会绕过系统音效，均衡器不生效"
        highResOutput -> "已开启 32-bit 高解析输出，部分机型上系统音效可能不生效"
        else -> null
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { runCatching(block).onFailure { Log.e(TAG, "均衡器操作失败", it) } }
        }
    }
}
