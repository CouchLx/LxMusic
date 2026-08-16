package com.example.lxmusic.usb.sink

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
 * File: moe.ouom.neriplayer.core.player.usb.sink/UsbExclusiveAudioSink (adapted for LxMusic)
 */

import android.content.Context
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import com.example.lxmusic.usb.UsbExclusiveLog
import com.example.lxmusic.usb.UsbExclusiveSettingsStore
import com.example.lxmusic.usb.session.UsbExclusiveSessionController
import com.example.lxmusic.usb.transport.UsbExclusiveRecoveryAction
import com.example.lxmusic.usb.transport.UsbExclusiveRuntimeMetrics
import com.example.lxmusic.usb.transport.valueAfter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

private const val TAG = "LxUsbSink"
private const val DIRECT_SCRATCH_CAPACITY_BYTES = 256 * 1024
private const val NATIVE_START_PREROLL_MS = 300L
private const val NATIVE_BACKPRESSURE_REFRESH_INTERVAL_MS = 250L
private const val SHORT_FOCUS_NATIVE_FAILURE_HOLD_MS = 700L
private const val SHORT_FOCUS_NATIVE_RESTART_MAX_ATTEMPTS = 2
private const val NATIVE_BACKPRESSURE_SOFT_RESTART_MIN_INTERVAL_MS = 1_500L
private const val NATIVE_BACKPRESSURE_SOFT_RESTART_MAX_ATTEMPTS = 2
private const val FIRST_COMPLETION_STALL_RECOVERY_MIN_MS = 220L
private const val FIRST_COMPLETION_STALL_RECOVERY_MAX_ATTEMPTS = 1
private const val CONTINUOUS_WRITE_FAILURE_GIVE_UP_THRESHOLD = 6
private const val NATIVE_GIVE_UP_COOLDOWN_MS = 30_000L
private const val ISO_ERROR_SAMPLE_INTERVAL_MS = 5_000L       // 等时传输错误采样间隔
// 重开阈值已放宽：native 同步模式漂移控制器（估计式速率修正）在错误出现后
// 几秒内即可收敛，重开只作为控制器无法跟上的最后兜底。
// 实测（NK1 MAX 同步小尾巴）：硬件抖动产生的 iso 错误呈突发-停止形态且错误率
// 极低（<0.05%），软重启对这类错误无效（重启后错误照涨），反而打断播放 →
// 阈值大幅放宽，只拦截真正恶化的连接问题（单窗口爆发 ≥500 / 连续 8 窗口递增
// 且单窗口增量 ≥60）。漂移修正关闭时此监测基本不会触发。
private const val ISO_ERROR_BURST_THRESHOLD = 500L          // 单窗口错误数超阈值 → clean reopen
private const val ISO_ERROR_DRIFT_WINDOWS = 8               // 连续 N 窗口错误递增（漂移加速）→ 提前 reopen
private const val ISO_ERROR_DRIFT_MIN_DELTA = 60L           // 漂移型触发的最低单窗口增量

/**
 * USB 独占 AudioSink 装饰器：
 * - 开启时绕过 fallbackSink，把 PCM 直接写入 native USB 管线（分片写入 + 背压等待 + 自动恢复）
 * - 关闭/打开失败/不兼容时委托 fallbackSink（系统 AudioTrack + 处理链）
 */
@OptIn(UnstableApi::class)
class UsbExclusiveAudioSink(
    private val context: Context,
    fallbackSink: AudioSink,
    private val usbEnabledProvider: () -> Boolean = {
        UsbExclusiveSettingsStore(context.applicationContext).isEnabled()
    }
) : ForwardingAudioSink(fallbackSink) {

    private var nativeActive = false
    private var currentFormat: Format? = null
    private var volume = 1f

    @Volatile
    private var playing = false

    /**
     * 音频焦点被抢占（其他 app 播放）时置位：释放独占会话并抑制重开，
     * 避免 libusb 与系统侧双写 USB DAC 等时端点（包错误/沙沙声）。
     * 焦点回归（GAIN）后清除，下次 configure/handleBuffer 正常重开。
     */
    @Volatile
    private var focusInhibited = false

    @Volatile
    private var lastBufferPresentationTimeUs = C.TIME_UNSET

    /** 渲染器 listener（onPositionAdvancing 通知位置锚点） */
    private var sinkListener: AudioSink.Listener? = null

    /**
     * 时间线锚点：流起始 PTS（媒体时间线级，可带 10^9ms 级偏移——渲染器/播放器
     * 时间线同样带该偏移）。位置 = 锚点 + 锚点以来 native 完成帧时长，
     * 与渲染器时间线一致（Media3: currentPositionUs = max(旧值, sink位置)，
     * 归一化成 0 起始会永远小于播放器时间线旧值 → 进度条冻结）。
     */
    private var startMediaTimeUs = C.TIME_UNSET

    private var discontinuityExpected = true

    /** 锚点时的 native 完成帧基线（重开/seek 后刷新） */
    private var completedFramesAtAnchor = 0L

    /** 已提交输入帧数（写循环累计，位置上限用） */
    private var writtenFrames = 0L

    /** 锚点时的已提交输入帧数 */
    private var writtenFramesAtAnchor = 0L

    /** 位置诊断日志限频 */
    @Volatile
    private var lastPosDiagLogMs = 0L

    @Volatile
    private var lastPositionUs = 0L

    @Volatile
    private var streamingLogged = false

    private var inputSampleRate = 48_000
    private var inputFrameBytes = 4

    /** FLOAT 输入时映射后的写入格式（native prepare 编码 + 量化位深）；非 float 为 null */
    private var softwareFloatInputFormat: PreparedUsbInputPcmFormat? = null

    // 兼容性状态：这些特性生效时 native 独占不可用，configure 时回退系统 sink
    private var playbackParameters = PlaybackParameters.DEFAULT
    private var skipSilenceEnabled = false
    private var tunnelingEnabled = false
    private var auxEffectInfo = AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, 0f)

    private val scratch = ByteBuffer.allocateDirect(DIRECT_SCRATCH_CAPACITY_BYTES)
    private val scratchFloat = ByteBuffer.allocateDirect(DIRECT_SCRATCH_CAPACITY_BYTES)

    // 恢复状态
    private val shortFocusFailures = AtomicInteger(0)

    @Volatile
    private var lastOpenSuccessTimeMs = 0L

    private val backpressureRestarts = AtomicInteger(0)

    @Volatile
    private var parkedSinceMs = 0L

    /** 背压期间传输完成基线：completedTransfers 有进展 = 传输在消费，重置计时不重启 */
    private var parkBaselineCompletedTransfers = -1L

    private val firstCompletionRecoveries = AtomicInteger(0)

    @Volatile
    private var firstCompletionStallSinceMs = 0L

    @Volatile
    private var lastDiagnosticsLogMs = 0L

    // FreshOpen 熔断：短时间内连续多次 native 建议 FreshOpen → 放弃独占回退系统
    @Volatile
    private var freshOpenRecoveryCount = 0

    @Volatile
    private var freshOpenRecoveryWindowStartMs = 0L

    /** 连续写入失败计数（会话缺失/写 0 字节/恢复失败），达到阈值回退系统输出，避免播放器永久卡 BUFFERING */
    private val continuousWriteFailures = AtomicInteger(0)

    /** 回退系统输出后的冷却截止（防下一首歌立即重试 native 再次卡死） */
    @Volatile
    private var nativeGiveUpUntilMs = 0L

    /** 等时传输错误爆发监测状态（沙沙声来源：USB 等时包错误 → 音频缺口） */
    @Volatile
    private var lastIsoErrorCount = -1L
    @Volatile
    private var lastIsoErrorSampleMs = 0L
    @Volatile
    private var lastIsoErrorDelta = 0L
    @Volatile
    private var isoErrorDriftStreak = 0

    /** native 漂移修正最近变化追踪（学习期间抑制重开） */
    @Volatile
    private var lastDriftCorrection: Long? = null
    @Volatile
    private var lastDriftChangeMs = 0L

    /** 残余错误追踪：收敛后检测渐进式时钟漂移 */
    @Volatile
    private var residualErrorWindowStart = 0L
    @Volatile
    private var residualErrorCount = 0L

    private companion object {
        const val FRESH_OPEN_GIVE_UP_THRESHOLD = 3
        const val FRESH_OPEN_GIVE_UP_WINDOW_MS = 30_000L
    }

    init {
        scratch.order(ByteOrder.nativeOrder())
        scratchFloat.order(ByteOrder.nativeOrder())
    }

    override fun setListener(listener: AudioSink.Listener) {
        sinkListener = listener
        super.setListener(listener)
    }

    /**
     * 诊断：park / 写入 0 字节时打印完整上下文（限频 15s，避免淹没内存日志环，
     * 让 native 的首错状态/漂移修正关键行保留在 300 条快照内）
     */
    private fun logWritePathDiagnostics(
        stage: String,
        metrics: UsbExclusiveRuntimeMetrics?,
        remainingBytes: Int,
        chunk: Int,
        written: Int
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDiagnosticsLogMs < 15_000L) return
        lastDiagnosticsLogMs = now
        val metricsText = if (metrics != null) {
            "running=${metrics.transportRunning} " +
                "playbackReady=${metrics.playbackReady} " +
                "feedbackState=${metrics.feedbackState} " +
                "feedbackReady=${metrics.feedbackReady} " +
                "realPcm=${metrics.realPcmReleased} " +
                "canAccept=${metrics.canAcceptPcm} " +
                "level=${metrics.pcmLevelBytes}/${metrics.pcmCapacityBytes} " +
                "free=${metrics.pcmFreeBytes} " +
                "xfer=${metrics.transferBytes}/${metrics.lastTransferBytes} " +
                "out=${metrics.sampleRate}/${metrics.channels}/${metrics.bits}/${metrics.subslotBytes} " +
                "err=${metrics.errorCode} " +
                "last=${metrics.lastError} " +
                "tFailed=${metrics.transportFailed} tTerm=${metrics.terminalFailure}"
        } else {
            "null"
        }
        val raw = UsbExclusiveSessionController.rawRuntimeReport()
            ?.takeIf { it.isNotBlank() }
        val rawText = if (raw != null) " raw[$raw]" else ""
        UsbExclusiveLog.w(
            TAG,
            "diag:$stage remaining=$remainingBytes chunk=$chunk written=$written " +
                "srIn=$inputSampleRate frameIn=$inputFrameBytes playing=$playing " +
                "transportStarted=${UsbExclusiveSessionController.isNativeTransportStarted()} " +
                "metrics[$metricsText]$rawText"
        )
    }

    /**
     * 主路径为 native 应用层独占（libusb 直驱 USB DAC，真独占/位完美）；
     * 开关开启即尝试 native，失败熔断（nativeGiveUpUntilMs 冷却）期间回退系统路由兜底。
     */
    private fun shouldUseNative(): Boolean {
        return usbEnabledProvider() &&
            SystemClock.elapsedRealtime() >= nativeGiveUpUntilMs &&
            !focusInhibited
    }

    /** 音频焦点被抢占：释放独占并抑制重开（见 [focusInhibited]） */
    fun setFocusInhibited(inhibited: Boolean) {
        if (focusInhibited == inhibited) return
        focusInhibited = inhibited
        if (inhibited) {
            UsbExclusiveLog.w(TAG, "audio focus inhibited: closing native session")
            nativeActive = false
            playing = false
            UsbExclusiveSessionController.closePlayerPcm()
            resetRecoveryCounters()
        }
    }

    private fun isPcm(format: Format): Boolean {
        return format.pcmEncoding == C.ENCODING_PCM_16BIT ||
            format.pcmEncoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN ||
            format.pcmEncoding == C.ENCODING_PCM_24BIT ||
            format.pcmEncoding == C.ENCODING_PCM_32BIT ||
            format.pcmEncoding == C.ENCODING_PCM_FLOAT
    }

    private fun bytesPerSample(encoding: Int): Int {
        return when (encoding) {
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT -> 4
            // FLOAT：写路径会把 float 量化到映射后的整数位深（与 native prepare 编码一致）
            C.ENCODING_PCM_FLOAT -> softwareFloatInputFormat?.bytesPerSample ?: 4
            else -> 2
        }
    }

    /**
     * 根据当前会话输出格式建立 FLOAT 输入的软件转换目标：
     * 输出 subslot=2/3/4 → 16/24/32-bit 整数编码（与 SessionController 的 prepare 映射一致）。
     */
    private fun updateSoftwareFloatConversionState() {
        val encoding = currentFormat?.pcmEncoding ?: C.ENCODING_PCM_16BIT
        if (encoding != C.ENCODING_PCM_FLOAT) {
            softwareFloatInputFormat = null
            return
        }
        softwareFloatInputFormat = UsbExclusiveOutputFormatResolver.preparedInputPcmFormat(
            inputEncoding = encoding,
            outputDescription = UsbExclusiveSessionController.state.value.outputFormat
        )
        UsbExclusiveLog.i(
            TAG,
            "float software conversion armed: " +
                (softwareFloatInputFormat?.let {
                    "encoding=${it.encoding} bytesPerSample=${it.bytesPerSample}"
                } ?: "unmapped")
        )
    }

    // ==================== 配置 ====================

    override fun getFormatSupport(format: Format): Int {
        return when {
            // 小米等设备声称支持 FLAC 直通（AudioTrack.isDirectPlaybackSupported=true）
            // 但 AudioTrack 实际创建失败（Media3 1.3.x passthrough bug，报
            // "Unable to configure passthrough"）→ 强制走解码路径，无损播放
            format.sampleMimeType == MimeTypes.AUDIO_FLAC ->
                SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
            shouldUseNative() && isPcm(format) -> SINK_FORMAT_SUPPORTED_DIRECTLY
            else -> super.getFormatSupport(format)
        }
    }

    override fun supportsFormat(format: Format): Boolean {
        // FLAC 直通在部分设备（小米 14 / Android 15）上 AudioTrack 配置失败
        // （Media3 1.3.x bug: "Unable to configure passthrough"）。
        // MediaCodecAudioRenderer 以 supportsFormat() 判定是否直通：
        // 返回 false 强制使用 MediaCodec 解码器解码 FLAC 为 PCM 后播放（音质无损）。
        if (format.sampleMimeType == MimeTypes.AUDIO_FLAC) return false
        return getFormatSupport(format) != SINK_FORMAT_UNSUPPORTED
    }

    override fun configure(
        format: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?
    ) {
        currentFormat = format
        if (shouldUseNative() && isPcm(format) && nativeCompatibilityOk(format, outputChannels)) {
            val channels = format.channelCount.takeIf { it in 1..2 } ?: 2
            val sampleRate = format.sampleRate.takeIf { it > 0 } ?: 48_000
            val encoding = format.pcmEncoding.takeIf { it != Format.NO_VALUE }
                ?: C.ENCODING_PCM_16BIT
            val opened = UsbExclusiveSessionController.openPlayerPcm(
                context = context,
                inputSampleRate = sampleRate,
                inputChannelCount = channels,
                inputEncoding = encoding
            )
            if (opened) {
                nativeActive = true
                inputSampleRate = sampleRate
                updateSoftwareFloatConversionState()
                inputFrameBytes = channels * bytesPerSample(encoding)
                UsbExclusiveSessionController.setBitPerfectMode(
                    UsbExclusiveSettingsStore(context).read().bitPerfect
                )
                // 会话刚打开时渲染器可能已喂预滚数据（播放器未 play 时 Media3 也会
                // 喂数据，且不会对从未 play 过的 sink 调 pause → focusMuted 保持 false
                // → fill 消费 ring 发声 → "显示暂停却断续出声"）。
                // 未播放则立即软静音，播放器真正 play() 时解除。
                UsbExclusiveSessionController.setPlayerFocusMuted(!playing)
                // 不重置独占音量：此处 volume 是播放器音量（默认 1f），写进去会把
                // 设置页专属音量条保存的值顶回满格（切歌/重建管线时音量"自己变"）
                UsbExclusiveLog.i(
                    TAG,
                    "native sink configured: ${format.sampleRate}Hz ${format.channelCount}ch " +
                        "enc=$encoding frameBytes=$inputFrameBytes"
                )
                return
            }
            // 打开失败：本次回退系统（下次 configure 会重试 native）
            nativeActive = false
            val gate = UsbExclusiveSessionController.openGateReason()
            UsbExclusiveLog.w(
                TAG,
                "native open failed (reason=${gate ?: "unknown"}), falling back to system sink"
            )
        } else {
            // 开关关闭/格式不兼容/多声道/特性冲突：释放独占会话，走系统
            if (nativeActive || UsbExclusiveSessionController.hasOpenSession()) {
                UsbExclusiveSessionController.closePlayerPcm()
            }
            nativeActive = false
        }
        super.configure(format, specifiedBufferSize, outputChannels)
    }

    /** native 独占兼容性：通道映射/多声道/变速变调/skipSilence/tunneling/aux 生效时回退系统 */
    private fun nativeCompatibilityOk(format: Format, outputChannels: IntArray?): Boolean {
        if (format.channelCount !in 1..2) return false
        if (outputChannels != null) return false
        if (playbackParameters.speed != 1f || playbackParameters.pitch != 1f) return false
        if (skipSilenceEnabled) return false
        if (tunnelingEnabled) return false
        if (auxEffectInfo.effectId != AuxEffectInfo.NO_AUX_EFFECT_ID) return false
        return true
    }

    // ==================== 播放控制 ====================

    override fun play() {
        playing = true
        if (nativeActive) {
            UsbExclusiveLog.i(TAG, "sink play (nativeActive)")
            // 恢复软静音（暂停时只降增益，传输保持运行）
            UsbExclusiveSessionController.setPlayerFocusMuted(false)
            UsbExclusiveSessionController.playPlayerPcm()
            return
        }
        UsbExclusiveLog.i(TAG, "sink play (system)")
        super.play()
    }

    override fun pause() {
        playing = false
        if (nativeActive) {
            // 软暂停：只把 native 增益降为 0（静音），传输保持运行。
            // 修复：ExoPlayer 在 BUFFERING 跳变时会频繁 sink.pause()/play() 抖动，
            // 旧逻辑每次抖动都停/启传输 → 音频断续爆音（"次次沙沙"）。
            // 软暂停下抖动只改增益（native 侧有平滑斜坡），音频无缝连续。
            UsbExclusiveLog.i(TAG, "sink pause (nativeActive, soft-mute)")
            UsbExclusiveSessionController.setPlayerFocusMuted(true)
            return
        }
        UsbExclusiveLog.i(TAG, "sink pause (system)")
        super.pause()
    }

    override fun handleDiscontinuity() {
        if (nativeActive) {
            resetRecoveryCounters()
            // 切歌/流不连续后清除旧歌的 PTS 锚点：否则新歌缓冲期间
            // getCurrentPositionUs 会用旧 PTS 钳制 native 计数，进度条
            // 停在上一首位置并原地波动
            lastBufferPresentationTimeUs = C.TIME_UNSET
            UsbExclusiveSessionController.flushPlayerPcm()
            return
        }
        super.handleDiscontinuity()
    }

    override fun flush() {
        if (nativeActive) {
            resetRecoveryCounters()
            lastBufferPresentationTimeUs = C.TIME_UNSET
            UsbExclusiveSessionController.flushPlayerPcm()
            return
        }
        super.flush()
    }

    override fun reset() {
        if (nativeActive) {
            // 保留会话：曲目切换不重开 USB
            resetRecoveryCounters()
            lastBufferPresentationTimeUs = C.TIME_UNSET
            UsbExclusiveSessionController.flushPlayerPcm()
            return
        }
        super.reset()
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (!nativeActive) {
            return super.getCurrentPositionUs(sourceEnded)
        }
        if (!UsbExclusiveSessionController.hasOpenSession()) {
            return lastPositionUs
        }
        val start = startMediaTimeUs
        if (start == C.TIME_UNSET) {
            // 未锚定（渲染器尚未喂数据）：保持上次位置
            return lastPositionUs
        }
        val completedFrames = UsbExclusiveSessionController.completedAudioFramesNative()
        val outputRate = UsbExclusiveSessionController.state.value.outputSampleRate
        // 消费位置（时间线级）= 锚点 + 锚点以来 native 完成帧时长
        var target = if (outputRate > 0 && completedFrames >= completedFramesAtAnchor) {
            start + (completedFrames - completedFramesAtAnchor) * 1_000_000L / outputRate
        } else {
            start
        }
        // 上限：锚点 + 已提交输入帧时长（位置不超前于已提交数据；暂停时停在提交点）
        if (inputSampleRate > 0 && writtenFrames >= writtenFramesAtAnchor) {
            val writtenUs = start +
                (writtenFrames - writtenFramesAtAnchor) * 1_000_000L / inputSampleRate
            if (target > writtenUs) {
                target = writtenUs
            }
        }
        // 单调推进：seek/切歌/重开时锚点重置或基线刷新，位置只前进不回退
        if (target > lastPositionUs) {
            lastPositionUs = target
        }
        val now2 = SystemClock.elapsedRealtime()
        if (now2 - lastPosDiagLogMs >= 2_000L) {
            lastPosDiagLogMs = now2
            UsbExclusiveLog.i(
                TAG,
                "pos: rate=$outputRate target=${target / 1000}ms " +
                    "completedFrames=$completedFrames anchor=${start / 1000}ms " +
                    "last=${lastPositionUs / 1000}ms"
            )
        }
        return lastPositionUs
    }

    override fun setVolume(volume: Float) {
        this.volume = volume
        if (!nativeActive) {
            super.setVolume(volume)
        }
        // native 独占：播放器音量（ExoPlayer volume 属性，含播放淡入淡出/媒体会话语量）
        // 不再写入独占音量——独占音量只由设置页专属音量条/音量路由控制。
        // 修复：播放淡入（player.volume 0→1）或媒体会话音量同步会把独占音量顶到最大
    }

    override fun setPreferredDevice(device: android.media.AudioDeviceInfo?) {
        // USB 独占模式忽略系统路由
        if (!nativeActive) {
            super.setPreferredDevice(device)
        }
    }

    // ==================== 写路径 ====================

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        if (!nativeActive) {
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }
        ensureUrgentAudioThreadPriority()
        lastBufferPresentationTimeUs = presentationTimeUs
        // 时间线锚定：直接采用渲染器 PTS（时间线级，不归一化），
        // 与播放器时间线一致（Media3 用 max(currentPositionUs, sink位置)）
        if (startMediaTimeUs == C.TIME_UNSET || discontinuityExpected) {
            startMediaTimeUs = presentationTimeUs
            completedFramesAtAnchor = UsbExclusiveSessionController.completedAudioFramesNative()
            writtenFramesAtAnchor = writtenFrames
            discontinuityExpected = false
        }

        if (!UsbExclusiveSessionController.hasOpenSession()) {
            if (focusInhibited) {
                // 焦点被抢占：不重开，等焦点恢复（GAIN）后由后续写入重开
                return false
            }
            // 会话缺失（StopPreserveIntent / 恢复循环等）：无论 playing 状态都尝试重开。
            // 修复：playing=false（播放器 BUFFERING 时 sink.pause 置位）下旧逻辑不重开，
            // 渲染器与播放器互相等待 → 进度条冻结 + 无声的"随机暂停"死锁。
            val reopened = UsbExclusiveSessionController.requestNativeReopen(context, "session_missing")
            if (reopened) {
                onReopenSuccess()
                continuousWriteFailures.set(0)
            } else {
                continuousWriteFailures.incrementAndGet()
                if (continuousWriteFailures.get() >= CONTINUOUS_WRITE_FAILURE_GIVE_UP_THRESHOLD) {
                    fallbackToSystemSink("session_missing_persistent")
                    return false
                }
            }
            sleepQuietly(NATIVE_BACKPRESSURE_REFRESH_INTERVAL_MS)
            return false
        }

        val direct = toDirectBuffer(buffer, buffer.remaining())
        val baseLimit = buffer.limit()
        var fullyConsumed = true
        // 等时传输错误爆发监测（限频 10s，内部采样）
        maybeRecoverIsoErrorBurst()
        while (direct.remaining() > 0) {
            val metrics = writeMetrics()
            if (metrics == null) {
                break
            }

            // 1) 原生建议的 Kotlin 终端恢复动作：执行并确认
            if (maybeExecuteRecoveryAction(metrics)) {
                continue
            }

            // 2) 传输不健康：恢复
            if (!metrics.hasHealthyTransport) {
                recoverAfterRuntimeFailure(metrics)
                if (UsbExclusiveSessionController.hasOpenSession()) {
                    // 恢复成功：重置失败计数
                    continuousWriteFailures.set(0)
                } else {
                    continuousWriteFailures.incrementAndGet()
                    if (continuousWriteFailures.get() >= CONTINUOUS_WRITE_FAILURE_GIVE_UP_THRESHOLD) {
                        fallbackToSystemSink("unhealthy_transport_persistent")
                        return false
                    }
                }
                continue
            }

            // 3) 首次完成卡死兜底
            maybeRecoverFirstCompletionStall(metrics)

            // 4) 规划分片大小（≥4 传输片 / 预滚上限 / 队列水位上限）
            val chunk = UsbExclusivePcmWritePlanner.chooseWriteSize(
                remainingBytes = direct.remaining(),
                inputSampleRate = inputSampleRate,
                inputFrameBytes = inputFrameBytes,
                nativeTransportStarted = UsbExclusiveSessionController.isNativeTransportStarted(),
                playing = playing,
                prerollMs = NATIVE_START_PREROLL_MS,
                metrics = metrics
            )
            if (chunk <= 0) {
                // 5) 队列满：良性背压等待；非良性已在上方恢复
                logWritePathDiagnostics("park", metrics, direct.remaining(), chunk, 0)
                // 看门狗：队列满但传输从未启动 → 停滞（重开循环/时钟异常的死锁特征）。
                // 计数达到阈值回退系统输出，避免 park→重开→写满→park 无限循环。
                if (!metrics.transportRunning &&
                    !UsbExclusiveSessionController.isNativeTransportStarted()
                ) {
                    continuousWriteFailures.incrementAndGet()
                    if (continuousWriteFailures.get() >= CONTINUOUS_WRITE_FAILURE_GIVE_UP_THRESHOLD) {
                        fallbackToSystemSink("parked_without_transport")
                        return false
                    }
                }
                parkBackpressure(metrics)
                // 修复死锁：park 后退出循环返回 false 让渲染器重投，而不是 continue 空转。
                // 旧实现 continue：ring 满且 fill 侧不消费（focusMuted 软暂停/传输停滞）时
                // handleBuffer 永不返回 → 渲染器线程卡死 → ExoPlayer 永久 BUFFERING
                // （"播放几首歌后一直加载"，日志特征：diag:park 每 15s 一条、level 恒满）。
                // 返回 false 后渲染器稍后重投，缓冲判定正常推进，play() 能到达并清除 focusMuted。
                fullyConsumed = false
                break
            }

            val written = UsbExclusiveSessionController.writePlayerPcm(
                direct,
                direct.position(),
                chunk
            )
            if (written > 0) {
                direct.position(direct.position() + written)
                writtenFrames += written / inputFrameBytes.coerceAtLeast(1)
                parkedSinceMs = 0L
                backpressureRestarts.set(0)
                continuousWriteFailures.set(0)
                if (!streamingLogged &&
                    UsbExclusiveSessionController.isNativeTransportStarted()
                ) {
                    streamingLogged = true
                    // 通知渲染器"位置已开始推进"：重置 MediaClock 锚点，
                    // 否则播放器 position 冻结在旧值（进度条不动/显示 0）
                    sinkListener?.onPositionAdvancing(System.currentTimeMillis())
                    UsbExclusiveLog.i(TAG, "native streaming: chunk=$written")
                }
                continue
            }

            // 写入 0 字节：会话可能刚死
            logWritePathDiagnostics("write_zero", null, direct.remaining(), chunk, written)
            val after = UsbExclusiveSessionController.runtimeMetrics()
            if (after != null && !after.hasHealthyTransport) {
                recoverAfterRuntimeFailure(after)
                if (UsbExclusiveSessionController.hasOpenSession()) {
                    // 重开成功：重置失败计数并重试写入
                    continuousWriteFailures.set(0)
                    continue
                }
            }
            // 传输健康但写入被瞬时拒绝（切歌/会话切换的 ioGate/streamSource 状态）：
            // 这是瞬态而非故障——不累计失败计数，短暂等待后让渲染器重投。
            // 旧实现把瞬时拒绝计入连续失败并熔断 → 每次切歌都可能回退系统 sink。
            // 只有"会话缺失重开失败 / 传输不健康"路径才累计失败并熔断。
            sleepQuietly(30L)
            fullyConsumed = false
            break
        }
        if (direct !== buffer) {
            // 部分消费时按实际消费量推进原 buffer position：
            // 未消费部分留给渲染器重投（不能置 baseLimit，否则剩余数据丢失 → 声音缺口）。
            // FLOAT→整数转换后字节数不同（4→3），未消费的 scratch 字节必须按比例换算回
            // 原始字节，否则渲染器重投从错位位置读 → 数据损坏 → 渲染器停喂 → 输出冻结
            // （日志特征：FLOAT 输入歌曲位置冻结、ring 空、欠载静音，16bit 正常）。
            val encoding = currentFormat?.pcmEncoding ?: C.ENCODING_PCM_16BIT
            val origBytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
            val scratchBytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) {
                softwareFloatInputFormat?.bytesPerSample ?: 3
            } else {
                origBytesPerSample
            }
            val unconsumedOrigBytes = if (scratchBytesPerSample == origBytesPerSample) {
                direct.remaining().toLong()
            } else {
                direct.remaining().toLong() * origBytesPerSample / scratchBytesPerSample
            }
            buffer.position(
                (baseLimit - unconsumedOrigBytes)
                    .toInt()
                    .coerceIn(buffer.position(), baseLimit)
            )
        }
        if (!fullyConsumed) {
            // 写入失败/背压且数据未消费完：返回 false 让渲染器重投（恢复后不丢音频）
            return false
        }
        return true
    }

    /**
     * 原生建议 Kotlin 终端动作（FreshOpen / StopPreserveIntent）时执行并 ack。
     * 仅在 latch 生效（actionLatched=true）且 id/generation 合法时处理。
     */
    private fun maybeExecuteRecoveryAction(metrics: UsbExclusiveRuntimeMetrics): Boolean {
        if (metrics.actionLatched != true) return false
        val action = metrics.recommendedAction
        if (action != UsbExclusiveRecoveryAction.FreshOpen &&
            action != UsbExclusiveRecoveryAction.StopPreserveIntent
        ) {
            return false
        }
        val actionId = metrics.actionId ?: return false
        val actionGeneration = metrics.actionGeneration ?: return false
        if (actionId <= 0L || actionGeneration < 0L) return false

        when (action) {
            UsbExclusiveRecoveryAction.FreshOpen -> {
                UsbExclusiveLog.i(
                    TAG,
                    "native requested FreshOpen (gen=$actionGeneration id=$actionId), reopening"
                )
                if (!recordFreshOpenRecoveryAndCheckGiveUp()) {
                    return true
                }
                val reopened = UsbExclusiveSessionController.requestNativeReopen(
                    context, "recovery_action:fresh_open"
                )
                if (reopened) {
                    onReopenSuccess()
                } else {
                    shortFocusFailures.incrementAndGet()
                    sleepQuietly(NATIVE_BACKPRESSURE_REFRESH_INTERVAL_MS)
                }
                UsbExclusiveSessionController.acknowledgeRecoveryAction(
                    actionGeneration, actionId
                )
            }

            UsbExclusiveRecoveryAction.StopPreserveIntent -> {
                UsbExclusiveLog.i(
                    TAG,
                    "native requested StopPreserveIntent (gen=$actionGeneration id=$actionId), " +
                        "closing session"
                )
                UsbExclusiveSessionController.closeSessionPreserveIntent()
                UsbExclusiveSessionController.acknowledgeRecoveryAction(
                    actionGeneration, actionId
                )
            }

            else -> return false
        }
        return true
    }

    /**
     * FreshOpen 恢复计数：30 秒窗口内第 3 次收到 FreshOpen 即视为 native 无法稳定运行，
     * 放弃独占会话并回退系统 sink（返回 false 表示放弃处理，调用方直接消费该动作）。
     */
    private fun recordFreshOpenRecoveryAndCheckGiveUp(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (freshOpenRecoveryWindowStartMs == 0L) {
            freshOpenRecoveryWindowStartMs = now
        }
        if (now - freshOpenRecoveryWindowStartMs > FRESH_OPEN_GIVE_UP_WINDOW_MS) {
            freshOpenRecoveryCount = 0
            freshOpenRecoveryWindowStartMs = now
        }
        freshOpenRecoveryCount++
        if (freshOpenRecoveryCount < FRESH_OPEN_GIVE_UP_THRESHOLD) {
            return true
        }
        // 达到阈值：native 反复建议 FreshOpen，放弃独占，回退系统输出
        UsbExclusiveLog.w(
            TAG,
            "giving up native after $freshOpenRecoveryCount FreshOpen recoveries " +
                "within ${FRESH_OPEN_GIVE_UP_WINDOW_MS}ms, falling back to system sink"
        )
        nativeGiveUpUntilMs = SystemClock.elapsedRealtime() + NATIVE_GIVE_UP_COOLDOWN_MS
        nativeActive = false
        playing = false
        UsbExclusiveSessionController.closePlayerPcm()
        resetRecoveryCounters()
        return false
    }

    /** 连续写入失败达到阈值：放弃独占会话，回退系统输出，避免播放器永久卡 BUFFERING */
    private fun fallbackToSystemSink(reason: String) {
        UsbExclusiveLog.w(
            TAG,
            "giving up native after persistent write failures ($reason), " +
                "falling back to system sink"
        )
        nativeGiveUpUntilMs = SystemClock.elapsedRealtime() + NATIVE_GIVE_UP_COOLDOWN_MS
        nativeActive = false
        playing = false
        UsbExclusiveSessionController.closePlayerPcm()
        // 通知播放服务刷新系统路由：native 独占关闭后内核驱动重新挂载是异步的，
        // 需要等 USB 设备回到 AudioManager 列表后再 setPreferredDevice，
        // 否则系统输出会落在手机扬声器而不是小尾巴耳机
        runCatching {
            context.sendBroadcast(
                android.content.Intent("com.example.lxmusic.SETTINGS_CHANGED")
            )
        }
        // 系统 sink 从未 configure 过（nativeActive 时 configure 提前 return）：
        // 补一次 configure，否则后续 super.handleBuffer 会因 AudioTrack 未创建而异常。
        // FLOAT 输入转 16bit：系统 sink 默认未开 float 输出，直接配 FLOAT 可能抛异常
        // → ExoPlayer onPlayerError → 切歌循环（"有概率播放不了"）。
        currentFormat?.let { format ->
            val safeFormat = if (format.pcmEncoding == C.ENCODING_PCM_FLOAT) {
                format.buildUpon().setPcmEncoding(C.ENCODING_PCM_16BIT).build()
            } else {
                format
            }
            runCatching { super.configure(safeFormat, 0, null) }
        }
        resetRecoveryCounters()
    }

    /**
     * 等时传输错误爆发监测：raw report 的 isoPacketErrors 增长触发 clean reopen。
     * 同步模式（无 feedback）下 host 与 DAC 时钟相位漂移随会话时长累积 →
     * 错误率加速（沙沙加重）。重开清空 DAC FIFO 重新对齐相位，错误从低水平重启。
     * 触发条件（满足其一）：
     *  - 单窗口错误数 ≥ ISO_ERROR_BURST_THRESHOLD（爆发）
     *  - 连续 ISO_ERROR_DRIFT_WINDOWS 个窗口错误递增（漂移型加速，提前打断）
     * 暂停（软静音）时不触发。
     */
    private fun maybeRecoverIsoErrorBurst() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastIsoErrorSampleMs < ISO_ERROR_SAMPLE_INTERVAL_MS) return
        lastIsoErrorSampleMs = now
        val raw = UsbExclusiveSessionController.rawRuntimeReport() ?: return
        val count = raw.valueAfter("isoPacketErrors")?.toLongOrNull() ?: return
        if (lastIsoErrorCount >= 0L) {
            val delta = count - lastIsoErrorCount
            val drifting = delta > 0L && delta >= lastIsoErrorDelta
            isoErrorDriftStreak = if (drifting) isoErrorDriftStreak + 1 else 0
            val burstTrigger = delta >= ISO_ERROR_BURST_THRESHOLD
            val driftTrigger = isoErrorDriftStreak >= ISO_ERROR_DRIFT_WINDOWS &&
                delta >= ISO_ERROR_DRIFT_MIN_DELTA
            // native 同步模式漂移控制器正在学习（修正值还在变化）时不重开：
            // 重开会清零修正状态，让学习从头再来（每首歌前几秒必出错）。
            if ((burstTrigger || driftTrigger) && playing && allowReopenDueToShortFocus() &&
                !driftCorrectionRecentlyChanged()
            ) {
                UsbExclusiveLog.w(
                    TAG,
                    "iso packet error ${if (burstTrigger) "burst" else "drift"}: " +
                        "+$delta in ${ISO_ERROR_SAMPLE_INTERVAL_MS}ms total=$count " +
                        "streak=$isoErrorDriftStreak, soft restart transport"
                )
                // 对齐 Neri：iso 错误爆发优先同句柄软重启（pause→play 重新调度传输），
                // 不再直接完整重开。C++ 同步模式漂移控制器自身会收敛速率修正，
                // 软重启只是重新对齐传输相位；若问题致命，native 会自行发起 FreshOpen。
                val restarted = softRestartNativeTransport("iso_error_burst")
                if (restarted) {
                    onReopenSuccess()
                } else {
                    shortFocusFailures.incrementAndGet()
                }
                isoErrorDriftStreak = 0
                lastIsoErrorDelta = 0L
                return
            }
            lastIsoErrorDelta = delta
        }
        lastIsoErrorCount = count
    }

    /**
     * native 漂移修正是否在近期（4s 内）仍有变化：
     * 变化中 = 控制器还在向 DAC 实际速率收敛，此时重开会打断学习。
     */
    private fun driftCorrectionRecentlyChanged(): Boolean {
        val raw = UsbExclusiveSessionController.rawRuntimeReport() ?: return false
        val current = raw.valueAfter("driftCorrection")?.toLongOrNull() ?: return false
        val now = SystemClock.elapsedRealtime()
        if (lastDriftCorrection == null || current != lastDriftCorrection) {
            lastDriftCorrection = current
            lastDriftChangeMs = now
            return true
        }
        return now - lastDriftChangeMs < 4_000L
    }

    /**
     * 同句柄软重启（对齐 Neri：pause→play 传输，保留会话/接口 claim/反馈时钟状态）。
     * 非终局故障（背压停滞/首完成卡死/iso 错误爆发/stall 类错误）优先软重启，
     * 只有 native 明确建议 FreshOpen 或传输已不可恢复时才走完整重开（requestNativeReopen）。
     * 完整重开会清空反馈时钟锁定学习期（前几秒发静音包）→ 频繁重开 = 周期性静音间隙/爆音。
     */
    private fun softRestartNativeTransport(reason: String): Boolean {
        if (!UsbExclusiveSessionController.hasOpenSession()) return false
        val metrics = UsbExclusiveSessionController.runtimeMetrics()
        if (metrics != null && (metrics.deviceOnline == false || (metrics.inFlightTransfers ?: 0) <= 0)) {
            UsbExclusiveLog.w(
                TAG,
                "soft restart skipped (device offline / no in-flight transfers): $reason " +
                    "deviceOnline=${metrics.deviceOnline} inFlight=${metrics.inFlightTransfers}"
            )
            return false
        }
        // 独立线程执行 + 250ms 超时：native pause/play 含 cancel drain + alt 切换，
        // 在渲染器线程上同步执行会阻塞播放（进度条失灵/一直加载中）
        return UsbExclusiveSessionController.requestSoftRestart(reason)
    }

    /** 写线程提升为 URGENT_AUDIO 优先级（对齐 Neri）：抗调度抖动，减少 PCM 供给延迟 */
    private val audioThreadPriorityConfigured = ThreadLocal<Boolean>()

    private fun ensureUrgentAudioThreadPriority() {
        if (audioThreadPriorityConfigured.get() == true) return
        runCatching {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        }.onSuccess {
            audioThreadPriorityConfigured.set(true)
            UsbExclusiveLog.i(TAG, "USB writer thread priority configured tid=${Process.myTid()}")
        }
    }

    private fun recoverAfterRuntimeFailure(metrics: UsbExclusiveRuntimeMetrics) {
        if (!allowReopenDueToShortFocus()) {
            UsbExclusiveLog.d(TAG, "short-focus hold, deferring reopen")
            sleepQuietly(NATIVE_BACKPRESSURE_REFRESH_INTERVAL_MS)
            return
        }
        val reason = "runtime_failure:${metrics.errorCode.name}"
        UsbExclusiveLog.w(
            TAG,
            "$reason lastError=${metrics.lastError} terminal=${metrics.terminalFailure} " +
                "transportFailed=${metrics.transportFailed}"
        )
        // 分级恢复（对齐 Neri）：stall 类错误（首完成超时/完成停滞）且设备在线时
        // 优先同句柄软重启（不打断反馈时钟学习期）；其余错误走完整重开。
        val stallRecoverable = metrics.errorCode ==
            com.example.lxmusic.usb.transport.UsbExclusiveErrorCode.TransferFirstCompletionTimeout ||
            metrics.errorCode ==
            com.example.lxmusic.usb.transport.UsbExclusiveErrorCode.TransferCompletionStalled
        val softRestarted = if (stallRecoverable) {
            softRestartNativeTransport(reason)
        } else {
            false
        }
        if (softRestarted) {
            onReopenSuccess()
            UsbExclusiveLog.i(TAG, "soft restarted after stall-class failure: $reason")
            return
        }
        val reopened = UsbExclusiveSessionController.requestNativeReopen(context, reason)
        if (reopened) {
            onReopenSuccess()
        } else {
            shortFocusFailures.incrementAndGet()
            sleepQuietly(NATIVE_BACKPRESSURE_REFRESH_INTERVAL_MS)
        }
    }

    /** 写循环内指标节流：完整 runtime report（60+ 字段大字符串 + JNI）与写路径争锁/GC，
     *  高频调用是前台（设置页轮询 + 写循环迭代）iso 错误多于后台的主因之一。
     *  100ms 内复用同一次查询；异常/恢复路径单独强制刷新。 */
    @Volatile
    private var cachedWriteMetrics: UsbExclusiveRuntimeMetrics? = null

    @Volatile
    private var cachedWriteMetricsAtMs = 0L

    private fun writeMetrics(): UsbExclusiveRuntimeMetrics? {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedWriteMetrics
        if (cached != null && now - cachedWriteMetricsAtMs < 100L) {
            return cached
        }
        val fresh = UsbExclusiveSessionController.runtimeMetrics()
        cachedWriteMetrics = fresh
        cachedWriteMetricsAtMs = now
        return fresh
    }

    private fun parkBackpressure(metrics: UsbExclusiveRuntimeMetrics) {
        val now = SystemClock.elapsedRealtime()
        if (parkedSinceMs == 0L) {
            parkedSinceMs = now
            parkBaselineCompletedTransfers = metrics.completedTransfers ?: -1L
        }
        // 传输有消费进展（completedTransfers 增长）→ 良性背压，重置计时不重启
        val completed = metrics.completedTransfers
        if (completed != null && completed > parkBaselineCompletedTransfers) {
            parkedSinceMs = now
            parkBaselineCompletedTransfers = completed
            backpressureRestarts.set(0)
        }
        val parkedMs = now - parkedSinceMs
        // 暂停（软静音）时不触发 soft restart：传输保持运行发静音，pipeline 数据保留。
        // 旧逻辑暂停期间也会 park→soft restart→重开 → 丢数据 + 恢复延迟 + USB 栈反复
        // 重初始化（等时传输错误正反馈恶化 → 沙沙声）。
        // 触发条件已对齐 Neri：1.5s 内 completedTransfers 完全无进展（传输停滞）才恢复，
        // 传输正常消费时仅背压等待，不再误触发软重启打断播放。
        if (playing &&
            parkedMs >= NATIVE_BACKPRESSURE_SOFT_RESTART_MIN_INTERVAL_MS &&
            backpressureRestarts.get() < NATIVE_BACKPRESSURE_SOFT_RESTART_MAX_ATTEMPTS
        ) {
            backpressureRestarts.incrementAndGet()
            parkedSinceMs = 0L
            UsbExclusiveLog.w(
                TAG,
                "backpressure soft restart #${backpressureRestarts.get()} " +
                    "parkedMs=$parkedMs level=${metrics.pcmLevelBytes}/${metrics.pcmCapacityBytes}"
            )
            // 对齐 Neri：背压停滞恢复走同句柄软重启（pause→play 传输），不再完整重开。
            // 完整重开会清空反馈时钟锁定学习期 → 前几秒静音 + 反复重开 = 周期性爆音。
            if (allowReopenDueToShortFocus()) {
                val restarted = softRestartNativeTransport("backpressure_soft_restart")
                if (restarted) {
                    onReopenSuccess()
                } else {
                    shortFocusFailures.incrementAndGet()
                }
            }
        }
        parkBackpressureWaitUs(metrics)
    }

    /**
     * park 等待（对齐 Neri parkForNativeBackpressure）：微秒级，0.5ms~4ms。
     * 旧实现 Thread.sleep(30~250ms) 会让渲染器线程长时间阻塞：ExoPlayer 判定 sink
     * 停滞 → 反复 pause/play 抖动 → 传输启停 → 爆音；且 park 期间 ring 被 fill 侧
     * 排空 → 欠载 → 静音间隙。微秒级 park + 返回 false 让渲染器高频重投，粒度更细。
     */
    private fun parkBackpressureWaitUs(metrics: UsbExclusiveRuntimeMetrics) {
        if (Thread.currentThread() === Looper.getMainLooper().thread) return
        val rate = metrics.sampleRate?.takeIf { it > 0 } ?: inputSampleRate
        if (rate <= 0) return
        val oneFrameUs = 1_000_000L / rate
        val parkUs = oneFrameUs.coerceIn(500L, 4_000L)
        LockSupport.parkNanos(parkUs * 1_000L)
    }

    private fun maybeRecoverFirstCompletionStall(metrics: UsbExclusiveRuntimeMetrics) {
        val started = UsbExclusiveSessionController.isNativeTransportStarted()
        val completedTransfers = metrics.completedTransfers ?: -1L
        val now = SystemClock.elapsedRealtime()
        if (!started || !metrics.transportRunning || completedTransfers > 0L) {
            firstCompletionStallSinceMs = 0L
            return
        }
        if (firstCompletionStallSinceMs == 0L) {
            firstCompletionStallSinceMs = now
            return
        }
        if (now - firstCompletionStallSinceMs >= FIRST_COMPLETION_STALL_RECOVERY_MIN_MS &&
            firstCompletionRecoveries.get() < FIRST_COMPLETION_STALL_RECOVERY_MAX_ATTEMPTS
        ) {
            firstCompletionRecoveries.incrementAndGet()
            firstCompletionStallSinceMs = 0L
            UsbExclusiveLog.w(TAG, "first-completion stall recovery #${firstCompletionRecoveries.get()}")
            // 对齐 Neri：首完成卡死走同句柄软重启（pause→play），不再完整重开。
            // 该场景传输刚启动、反馈时钟正在锁定，重开会再次清零学习期 → 反复静音。
            if (allowReopenDueToShortFocus()) {
                val restarted = softRestartNativeTransport("first_completion_stall")
                if (restarted) {
                    onReopenSuccess()
                } else {
                    shortFocusFailures.incrementAndGet()
                }
            }
        }
    }

    /** 短聚焦熔断：重开后很快又失败时，限制重开频率（700ms 窗口内最多 2 次） */
    private fun allowReopenDueToShortFocus(): Boolean {
        val lastOpen = lastOpenSuccessTimeMs
        if (lastOpen == 0L) return true
        val sinceLastOpenMs = SystemClock.elapsedRealtime() - lastOpen
        if (sinceLastOpenMs >= SHORT_FOCUS_NATIVE_FAILURE_HOLD_MS) {
            shortFocusFailures.set(0)
            return true
        }
        return shortFocusFailures.get() < SHORT_FOCUS_NATIVE_RESTART_MAX_ATTEMPTS
    }

    private fun onReopenSuccess() {
        lastOpenSuccessTimeMs = SystemClock.elapsedRealtime()
        shortFocusFailures.set(0)
        // 重开会话后 native 完成帧从 0 重新累计：刷新基线，
        // 位置由 lastPositionUs 单调保持（不回退、不冻结）
        completedFramesAtAnchor = UsbExclusiveSessionController.completedAudioFramesNative()
    }

    private fun resetRecoveryCounters() {
        shortFocusFailures.set(0)
        backpressureRestarts.set(0)
        firstCompletionRecoveries.set(0)
        continuousWriteFailures.set(0)
        parkedSinceMs = 0L
        parkBaselineCompletedTransfers = -1L
        firstCompletionStallSinceMs = 0L
        lastPositionUs = 0L
        startMediaTimeUs = C.TIME_UNSET
        discontinuityExpected = true
        completedFramesAtAnchor = 0L
        writtenFrames = 0L
        writtenFramesAtAnchor = 0L
        streamingLogged = false
        lastIsoErrorCount = -1L
        lastIsoErrorSampleMs = 0L
        lastIsoErrorDelta = 0L
        isoErrorDriftStreak = 0
        lastDriftCorrection = null
        lastDriftChangeMs = 0L
    }

    private fun sleepQuietly(ms: Long) {
        if (ms <= 0L) return
        try {
            Thread.sleep(ms)
        } catch (ignored: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** 非 Direct 或 float 输入 → 拷贝/量化到 Direct scratch（native 要求 DirectByteBuffer） */
    private fun toDirectBuffer(source: ByteBuffer, size: Int): ByteBuffer {
        val encoding = currentFormat?.pcmEncoding ?: C.ENCODING_PCM_16BIT
        if (encoding == C.ENCODING_PCM_FLOAT) {
            // float → 映射目标位深（16/24/32-bit），与 native prepare 编码一致
            val prepared = softwareFloatInputFormat ?: return source
            val outBytesPerSample = prepared.bytesPerSample
            val outputBytes = size / 4 * outBytesPerSample
            if (outputBytes <= 0 || scratchFloat.capacity() < outputBytes) return source
            scratchFloat.clear()
            val src = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            while (src.remaining() >= 4) {
                val sample = src.float
                val clamped = if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f
                when (prepared.encoding) {
                    C.ENCODING_PCM_16BIT -> scratchFloat.putShort(
                        (clamped * Short.MAX_VALUE).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            .toShort()
                    )
                    C.ENCODING_PCM_24BIT -> {
                        val value = (clamped * 8_388_607f).toInt()
                        scratchFloat.put((value and 0xFF).toByte())
                        scratchFloat.put(((value shr 8) and 0xFF).toByte())
                        scratchFloat.put(((value shr 16) and 0xFF).toByte())
                    }
                    C.ENCODING_PCM_32BIT -> scratchFloat.putInt(
                        (clamped * Int.MAX_VALUE.toFloat()).toInt()
                    )
                    else -> return source
                }
            }
            scratchFloat.flip()
            return scratchFloat
        }
        if (source.isDirect) return source
        if (scratch.capacity() < size) return source
        scratch.clear()
        scratch.put(source.duplicate())
        scratch.flip()
        return scratch
    }

    // ==================== 不兼容特性 ====================

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        this.playbackParameters = playbackParameters
        if (!nativeActive) {
            super.setPlaybackParameters(playbackParameters)
        }
        // USB 独占时变速变调忽略；下次 configure 因兼容性检查回退系统
    }

    override fun setSkipSilenceEnabled(enabled: Boolean) {
        skipSilenceEnabled = enabled
        if (!nativeActive) {
            super.setSkipSilenceEnabled(enabled)
        }
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        this.auxEffectInfo = auxEffectInfo
        if (!nativeActive) {
            super.setAuxEffectInfo(auxEffectInfo)
        }
    }

    override fun enableTunnelingV21() {
        tunnelingEnabled = true
        if (!nativeActive) {
            super.enableTunnelingV21()
        }
    }

    override fun disableTunneling() {
        tunnelingEnabled = false
        if (!nativeActive) {
            super.disableTunneling()
        }
    }
}
