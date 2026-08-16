package com.example.lxmusic.usb.session

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
 * File: moe.ouom.neriplayer.core.player.usb.session/UsbExclusiveSessionController (adapted for LxMusic)
 */

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import com.example.lxmusic.usb.UsbExclusiveBitDepthMode
import com.example.lxmusic.usb.UsbExclusiveLog
import com.example.lxmusic.usb.UsbExclusiveSampleRateMode
import com.example.lxmusic.usb.UsbExclusiveSettingsStore
import com.example.lxmusic.usb.device.openPermittedUsbAudioDevice
import com.example.lxmusic.usb.sink.UsbExclusiveOutputFormatResolver
import com.example.lxmusic.usb.system.UsbExclusiveSystemVolumeBridge
import com.example.lxmusic.usb.system.UsbExclusiveVolumeState
import com.example.lxmusic.usb.system.usbExclusiveEffectiveNativeVolume
import com.example.lxmusic.usb.transport.UsbExclusiveErrorCode
import com.example.lxmusic.usb.transport.UsbExclusiveIoGate
import com.example.lxmusic.usb.transport.UsbExclusiveNativeBridge
import com.example.lxmusic.usb.transport.UsbExclusiveNativeState
import com.example.lxmusic.usb.transport.UsbExclusiveRecoveryAction
import com.example.lxmusic.usb.transport.UsbExclusiveRuntimeMetrics
import com.example.lxmusic.usb.transport.booleanField
import com.example.lxmusic.usb.transport.requiresFreshNativeOpen
import com.example.lxmusic.usb.transport.usbRuntimeMetrics
import com.example.lxmusic.usb.transport.valueAfter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TAG = "LxUsbSession"

/** USB 独占会话控制器（单例）：设备打开 / PCM 写入 / 播放暂停 / 打开门与重开冷却 / 拔插恢复 / 运行时恢复 */
internal object UsbExclusiveSessionController {

    private const val OPEN_GATE_FAILURE_FUSE_MS = 8_000L          // open 失败后的打开门冷却
    private const val DETACH_FUSE_MS = 18_000L                    // 拔出熔断（打开门 + 重开冷却）
    private const val REOPEN_TRANSIENT_COOLDOWN_MS = 8_000L       // 瞬时故障自动重开冷却
    private const val REOPEN_TRANSPORT_COOLDOWN_MS = 8_000L       // 传输故障自动重开冷却
    private const val PLAYER_PCM_RECONFIGURE_CLOSE_GATE_MS = 750L
    private const val EMERGENCY_CLOSE_WAIT_MS = 1_500L
    private const val NATIVE_START_PREROLL_MS = 300L
    private const val NATIVE_CLOSE_TIMEOUT_MS = 2_000L            // native 关闭超时（防止挂死阻塞渲染线程）
    private const val NATIVE_OPEN_TIMEOUT_MS = 2_500L             // native 打开超时（libusb 控制传输在 USB 挂死时可能无限阻塞）
    private const val SOFT_RESTART_TIMEOUT_MS = 250L              // 软重启等待超时：超过即放行渲染线程，任务在后台线程继续

    private val _state = MutableStateFlow(UsbExclusiveNativeState())
    val state: StateFlow<UsbExclusiveNativeState> = _state

    private val _volumeState = MutableStateFlow(UsbExclusiveVolumeState())
    val volumeState: StateFlow<UsbExclusiveVolumeState> = _volumeState

    private val ioGate = UsbExclusiveIoGate()
    private val transportCommandGate = UsbExclusiveTransportCommandGate()

    private var appContext: Context? = null
    private var handle: Long = 0L
    private var connection: UsbDeviceConnection? = null
    private var usbDevice: UsbDevice? = null

    // 打开门：限制新打开的发起（失败冷却 / 拔出熔断），与自动重开冷却分开
    private var openGateBlockUntilMs = 0L
    private var openGateBlockReason: String? = null
    private var openAttempts = 0

    // 自动重开冷却：传输失败后自动重开的最小间隔
    private var reopenCooldownUntilMs = 0L

    private var nativeCloseInFlightCount = 0
    private var transitioning = false

    // 最近一次 prepare 的输入格式（重开时复用）
    private var lastInputSampleRate = 48_000
    private var lastInputChannelCount = 2
    private var lastInputEncoding = 2

    private var inputFormat = "none"
    private var outputSampleRate = 0
    private var outputChannels = 0
    private var outputBits = 0
    private var outputSubslot = 0
    private var bufferDurationMs = 250
    private var appInForeground = true
    private var nativePlaying = false
    private var nativePausedByPlayControl = false

    /** 软重启进行中：写路径跳过自动启动传输，避免与后台重启任务竞争 play/pause */
    @Volatile
    private var softRestarting = false
    private var volume = 1f
    private var focusMuted = false
    private var bitPerfect = false
    private var systemVolumeFraction = 1f
    private var prerollThresholdBytes = 0

    private val lock = Any()

    /**
     * native 关闭专用线程池（单线程）：手机深睡后 USB 设备被内核挂起时，
     * libusb 的 submit/cancel/join 可能永久阻塞；关闭必须在超时内完成，
     * 超时则放弃该 handle（不再等待），避免渲染线程/UI 被挂死。
     */
    private val nativeCloseExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "usb-exclusive-native-close").apply { isDaemon = true }
        }

    /**
     * native 打开专用线程池（可缓存）：USB 挂死时 libusb 控制传输可能无限阻塞，
     * 用独立线程 + 超时放弃避免阻塞调用方（渲染器/UI）线程。
     * 单次挂死只泄漏一条线程，后续打开用新线程继续。
     */
    private val nativeOpenExecutor: ExecutorService =
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "usb-exclusive-native-open").apply { isDaemon = true }
        }

    /** 超时保护的 native 打开（挂死时按超时返回 0L，交由熔断/回退逻辑处理） */
    private fun nativeOpenBounded(
        connection: UsbDeviceConnection,
        sampleRate: Int,
        channelCount: Int,
        bitsPerSample: Int,
        subslotBytes: Int
    ): Long {
        return try {
            nativeOpenExecutor.submit<Long> {
                UsbExclusiveNativeBridge.open(
                    connection = connection,
                    sampleRate = sampleRate,
                    channelCount = channelCount,
                    bitsPerSample = bitsPerSample,
                    subslotBytes = subslotBytes
                )
            }.get(NATIVE_OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            UsbExclusiveLog.w(
                TAG,
                "nativeOpen timed out after ${NATIVE_OPEN_TIMEOUT_MS}ms, abandoning attempt: " +
                    "${error.message}"
            )
            0L
        }
    }

    init {
        // 订阅会话音量桥（系统音量 → native 增益，音量²曲线）
        UsbExclusiveSystemVolumeBridge.subscribe { fraction ->
            synchronized(lock) {
                systemVolumeFraction = fraction?.coerceIn(0f, 1f) ?: 1f
                if (handle != 0L) {
                    UsbExclusiveNativeBridge.setPlayerVolume(handle, effectiveVolume())
                }
                publishVolumeStateLocked()
            }
        }
    }

    fun setBitPerfectMode(enabled: Boolean) {
        synchronized(lock) {
            bitPerfect = enabled
            if (handle != 0L) {
                UsbExclusiveNativeBridge.setPlayerVolume(handle, effectiveVolume())
            }
            publishVolumeStateLocked()
        }
    }

    /** 当前是否有打开的原生会话 */
    fun hasOpenSession(): Boolean = synchronized(lock) { handle != 0L }

    /** 查询当前打开门冷却原因 */
    fun openGateReason(): String? = synchronized(lock) { openGateBlockReason }

    /** 查询当前打开门冷却剩余毫秒（0 表示无冷却） */
    fun openGateRemainingMs(): Long = synchronized(lock) {
        val remain = openGateBlockUntilMs - System.currentTimeMillis()
        if (remain > 0) remain else 0L
    }

    // ==================== 设备拔插 ====================

    fun handleUsbDeviceDetached(device: UsbDevice?) {
        synchronized(lock) {
            if (device != null && usbDevice != null && device.deviceId != usbDevice?.deviceId) return
            UsbExclusiveLog.i(TAG, "device detached, fusing native opens")
            markDeviceDetachedLocked()
        }
    }

    fun handleUsbDeviceAttached(context: Context) {
        synchronized(lock) {
            appContext = context.applicationContext
            if (openGateBlockReason == "device_detached") {
                openGateBlockUntilMs = 0L
                openGateBlockReason = null
            }
            reopenCooldownUntilMs = 0L
        }
        UsbExclusiveLog.i(TAG, "device attached, native open unblocked")
    }

    private fun markDeviceDetachedLocked() {
        val current = handle
        if (current != 0L) {
            UsbExclusiveNativeBridge.markDeviceDetached(current)
            closeLocked()
        }
        val now = System.currentTimeMillis()
        openGateBlockUntilMs = now + DETACH_FUSE_MS
        openGateBlockReason = "device_detached"
        reopenCooldownUntilMs = openGateBlockUntilMs
        maintainWakeLockLocked()
        updateStateLocked()
    }

    // ==================== 打开 ====================

    /**
     * 打开原生会话：设备访问 → 候选格式逐个尝试 → prepare。
     * 返回 true 表示会话可用（可能复用旧会话）。
     */
    fun openPlayerPcm(
        context: Context,
        inputSampleRate: Int,
        inputChannelCount: Int,
        inputEncoding: Int
    ): Boolean {
        synchronized(lock) {
            appContext = context.applicationContext
            rememberInputFormat(inputSampleRate, inputChannelCount, inputEncoding)
            if (handle != 0L) {
                // 已有会话：输出采样率跟随歌曲时，采样率变化则重开（避免依赖 native 重采样丢位完美）
                val prefs = UsbExclusiveSettingsStore(context).read()
                val rateChanged = prefs.sampleRateMode == UsbExclusiveSampleRateMode.FOLLOW_SOURCE &&
                    outputSampleRate > 0 && outputSampleRate != inputSampleRate
                if (!rateChanged) {
                    // prepare 新输入格式（输出格式保持，编码按输出 subslot 映射）
                    if (UsbExclusiveNativeBridge.preparePlayerPcm(
                            handle,
                            inputSampleRate,
                            inputChannelCount,
                            inputEncodingForPrepare(inputEncoding, outputSubslot)
                        )
                    ) {
                        // 会话复用：同步设置项（如漂移修正开关）可能已变化，重新应用
                        UsbExclusiveNativeBridge.setSyncDriftEnabled(
                            handle,
                            prefs.syncDriftCorrectionEnabled
                        )
                        inputFormat = describeInputFormat(inputSampleRate, inputChannelCount, inputEncoding)
                        // C++ preparePlayerPcm 会 stopStreamingInternal() 停掉运行中的传输。
                        // 播放中切歌时序为 play → configure → prepare：传输刚启动就被 prepare
                        // 停掉，Kotlin 的 nativePlaying 仍为 true → 写循环以为在跑、native 实际
                        // 已停 → park → 重开死循环（"第三首不能播放"）。此处恢复传输。
                        if (nativePlaying && !nativePausedByPlayControl) {
                            UsbExclusiveLog.i(
                                TAG,
                                "restarting transport after prepare (playing track switch)"
                            )
                            startNativePlaybackLocked()
                        }
                        updateStateLocked()
                        return true
                    }
                    UsbExclusiveLog.w(
                        TAG,
                        "session reuse prepare failed, reopening: rate=$inputSampleRate " +
                            "ch=$inputChannelCount enc=$inputEncoding"
                    )
                } else {
                    UsbExclusiveLog.i(
                        TAG,
                        "sample rate changed, reopening session: $outputSampleRate -> $inputSampleRate"
                    )
                }
                // prepare 失败或采样率变化：关闭重开
                closeLocked()
            }
            return openPlayerPcmLocked(context, inputSampleRate, inputChannelCount, inputEncoding)
        }
    }

    private fun openPlayerPcmLocked(
        context: Context,
        inputSampleRate: Int,
        inputChannelCount: Int,
        inputEncoding: Int
    ): Boolean {
        val now = System.currentTimeMillis()
        if (now < openGateBlockUntilMs) {
            _state.value = _state.value.copy(
                lastError = "open_blocked:${openGateBlockReason ?: "cooldown"}"
            )
            return false
        }

        val prefs = UsbExclusiveSettingsStore(context).read()
        val devicePair = openPermittedUsbAudioDevice(context, prefs.selectedDeviceKey)
        if (devicePair == null) {
            recordOpenFailureLocked("no_permitted_device")
            return false
        }
        val (device, conn) = devicePair

        // 查询 DAC 真实能力（采样率/声道），优先按设备支持列表生成候选
        val usbOutput = com.example.lxmusic.util.UsbAudioManager.usbAudioDevices(context)
            .firstOrNull()
        val supportedRates = usbOutput?.sampleRates?.filter { it > 0 } ?: emptyList()
        val supportedChannels = usbOutput?.channelCounts?.filter { it > 0 } ?: emptyList()

        val candidates = resolveOutputCandidates(
            sourceSampleRate = inputSampleRate.takeIf { it > 0 } ?: 48_000,
            prefsSampleRate = prefs.sampleRateMode.sampleRateHz,
            bitDepthMode = prefs.bitDepthMode,
            channels = inputChannelCount.takeIf { it in 1..2 } ?: 2,
            supportedSampleRates = supportedRates,
            supportedChannelCounts = supportedChannels
        )
        UsbExclusiveLog.i(
            TAG,
            "open attempt: device=${device.productName ?: device.deviceName} " +
                "candidates=${candidates.joinToString(",") { "${it.sampleRate}/${it.channels}/${it.bits}/${it.subslot}" }}"
        )

        transitioning = true
        maintainWakeLockLocked()
        var opened = false
        try {
            for (candidate in candidates) {
                val h = nativeOpenBounded(
                    connection = conn,
                    sampleRate = candidate.sampleRate,
                    channelCount = candidate.channels,
                    bitsPerSample = candidate.bits,
                    subslotBytes = candidate.subslot
                )
                if (h != 0L) {
                    handle = h
                    connection = conn
                    usbDevice = device
                    outputSampleRate = candidate.sampleRate
                    outputChannels = candidate.channels
                    outputBits = candidate.bits
                    outputSubslot = candidate.subslot
                    openAttempts = 0
                    openGateBlockUntilMs = 0L
                    openGateBlockReason = null
                    opened = true
                    UsbExclusiveLog.i(
                        TAG,
                        "candidate open ok: rate=${candidate.sampleRate} ch=${candidate.channels} " +
                            "bits=${candidate.bits} subslot=${candidate.subslot} handle=$h"
                    )
                    break
                }
                UsbExclusiveLog.w(
                    TAG,
                    "candidate open failed: rate=${candidate.sampleRate} ch=${candidate.channels} " +
                        "bits=${candidate.bits} subslot=${candidate.subslot}"
                )
            }

            if (!opened) {
                runCatching { conn.close() }
                recordOpenFailureLocked(UsbExclusiveNativeBridge.lastOpenError().ifBlank { "open_failed" })
                return false
            }

            // 配置缓冲 + 预滚阈值
            bufferDurationMs = prefs.bufferDurationMs(appInForeground)
            UsbExclusiveNativeBridge.configurePlayerBufferDuration(handle, bufferDurationMs)
            UsbExclusiveNativeBridge.configurePlayerTransferWindow(handle, bufferDurationMs)
            // 同步模式漂移修正开关（默认关闭：部分同步 DAC 的错误不是速率失配，
            // 主动修正会负反馈恶化；反馈模式（异步 DAC）下 native 自动忽略此开关）
            UsbExclusiveNativeBridge.setSyncDriftEnabled(handle, prefs.syncDriftCorrectionEnabled)
            prerollThresholdBytes = (NATIVE_START_PREROLL_MS * outputSampleRate / 1000 *
                outputChannels * outputSubslot).toInt()

            // 重开时恢复会话参数
            UsbExclusiveNativeBridge.setPlayerVolume(handle, effectiveVolume())
            UsbExclusiveNativeBridge.setPlayerFocusMuted(handle, focusMuted)

            val prepared = UsbExclusiveNativeBridge.preparePlayerPcm(
                handle,
                inputSampleRate,
                inputChannelCount,
                inputEncodingForPrepare(inputEncoding, outputSubslot)
            )
            if (!prepared) {
                UsbExclusiveLog.w(
                    TAG,
                    "prepare failed, closing session: rate=$inputSampleRate ch=$inputChannelCount " +
                        "enc=$inputEncoding mapped=${inputEncodingForPrepare(inputEncoding, outputSubslot)} " +
                        "output=${outputSampleRate}/${outputChannels}/${outputBits}/${outputSubslot}"
                )
                closeLocked()
                recordOpenFailureLocked("player_pcm_prepare_failed")
                return false
            }
            inputFormat = describeInputFormat(inputSampleRate, inputChannelCount, inputEncoding)
            UsbExclusiveLog.i(
                TAG,
                "opened native session: rate=$outputSampleRate ch=$outputChannels bits=$outputBits " +
                    "subslot=$outputSubslot buffer=${bufferDurationMs}ms"
            )
            // 会话打开成功：立即通知 PlayerService 刷新 USB 锚点/路由。
            // 采样率切换重开会话时锚点会先释放（hasOpenSession=false），若等 3s 轮询
            // 兜底重启，窗口期内系统侧会重新接管 USB DAC → 与 libusb 双写 → 沙沙/爆音。
            notifyUsbStateChangedLocked()
            return true
        } finally {
            transitioning = false
            maintainWakeLockLocked()
            updateStateLocked()
        }
    }

    /** 通知 PlayerService 刷新锚点/路由（会话打开/关闭时立即触发，不等 3s 轮询） */
    private fun notifyUsbStateChangedLocked() {
        val ctx = appContext ?: return
        runCatching {
            ctx.sendBroadcast(Intent("com.example.lxmusic.SETTINGS_CHANGED"))
        }
    }

    private fun recordOpenFailureLocked(reason: String) {
        openAttempts++
        openGateBlockUntilMs = System.currentTimeMillis() + OPEN_GATE_FAILURE_FUSE_MS
        openGateBlockReason = reason
        _state.value = _state.value.copy(
            lastError = reason,
            opened = false,
            handle = 0L
        )
        UsbExclusiveLog.w(TAG, "native open failed ($reason), attempts=$openAttempts")
    }

    // ==================== 重开（自动恢复 / sink 请求） ====================

    /**
     * 请求重开原生会话（sink 恢复路径调用）。
     * 遵守重开冷却：冷却期内返回 false，由调用方稍后重试。
     */
    fun requestNativeReopen(context: Context, reason: String): Boolean {
        synchronized(lock) {
            appContext = context.applicationContext
            val now = System.currentTimeMillis()
            if (now < reopenCooldownUntilMs) {
                UsbExclusiveLog.d(
                    TAG,
                    "reopen deferred reason=$reason cooldownRemainingMs=${reopenCooldownUntilMs - now}"
                )
                return false
            }
            UsbExclusiveLog.i(TAG, "native reopen requested reason=$reason")
            closeLocked()
            val reopened = openPlayerPcmLocked(
                context,
                lastInputSampleRate,
                lastInputChannelCount,
                lastInputEncoding
            )
            UsbExclusiveLog.i(TAG, "native reopen completed reason=$reason opened=$reopened")
            // 重开成功后同样进入冷却：防止 sink 恢复循环高频重开（每次重开都会执行
            // 完整 native open + 控制传输，高频重开会把 USB 栈打挂：wrap_sys_device LIBUSB_ERROR_IO）
            if (reopened) {
                reopenCooldownUntilMs = System.currentTimeMillis() + REOPEN_TRANSPORT_COOLDOWN_MS
            }
            return reopened
        }
    }

    /** 确认原生建议的 Kotlin 终端恢复动作（FreshOpen / StopPreserveIntent） */
    fun acknowledgeRecoveryAction(recoveryGeneration: Long, recoveryActionId: Long) {
        val h = synchronized(lock) { handle }
        if (h == 0L || recoveryGeneration < 0L || recoveryActionId < 0L) return
        UsbExclusiveNativeBridge.acknowledgeRecoveryAction(h, recoveryGeneration, recoveryActionId)
    }

    private fun rememberInputFormat(sampleRate: Int, channels: Int, encoding: Int) {
        if (sampleRate > 0) lastInputSampleRate = sampleRate
        if (channels in 1..2) lastInputChannelCount = channels
        if (encoding > 0) lastInputEncoding = encoding
    }

    /**
     * 输入编码映射：FLOAT 输入按当前输出 subslot 映射为 16/24/32-bit 整数编码，
     * 与 sink 写路径的软件量化对齐；非 FLOAT 原样透传。
     */
    private fun inputEncodingForPrepare(inputEncoding: Int, subslotBytes: Int): Int {
        return UsbExclusiveOutputFormatResolver.preparedInputPcmFormat(
            inputEncoding = inputEncoding,
            subslotBytes = subslotBytes
        )?.encoding ?: inputEncoding
    }

    // ==================== 写入 / 播放控制 ====================

    fun writePlayerPcm(buffer: ByteBuffer, offset: Int, size: Int): Int {
        val h = synchronized(lock) { handle }
        if (h == 0L) return 0
        if (!ioGate.tryEnterWrite()) return 0
        try {
            var written = UsbExclusiveNativeBridge.writePlayerPcm(h, buffer, offset, size, effectiveVolume())
            if (written > 0 && !nativePlaying && !softRestarting) {
                maybeStartTransportLocked()
            } else if (written == 0 && !nativePlaying && !softRestarting) {
                // 写入返回 0 且传输未启动：USB 端点可能需要先 play() 才能接受数据
                // 尝试启动传输后重试一次
                startNativePlaybackLocked()
                written = UsbExclusiveNativeBridge.writePlayerPcm(h, buffer, offset, size, effectiveVolume())
                if (written > 0 && !softRestarting) {
                    maybeStartTransportLocked()
                }
            }
            return written
        } finally {
            ioGate.exitWrite()
            maintainWakeLockLocked()
        }
    }

    /**
     * 传输启动改为"数据驱动"：只要有数据且传输未运行就尝试启动。
     * 修复死锁：播放器因卡顿调 sink.pause() 置位 nativePausedByPlayControl 后，
     * 渲染器仍在喂数据（handleBuffer 持续调用），但旧逻辑被该标志挡住永远不启动传输
     * → 队列写满 → park → 重开 → 再写满……无限循环（日志特征：transportStarted=false
     * 全程、无 "transport started"、playerOutputBytes=0）。
     * 用户主动暂停时渲染器停止喂数据（无写入），不会触发此处，天然安全。
     */
    private fun maybeStartTransportLocked() {
        if (nativePlaying || handle == 0L) return
        val freeBytes = UsbExclusiveNativeBridge.playerPcmFreeBytes(handle) ?: return
        val capacityBytes = freeBytes + queuedBytesLocked()
        if (capacityBytes > 0 && (capacityBytes - freeBytes) >= prerollThresholdBytes.coerceAtLeast(1)) {
            startNativePlaybackLocked()
        } else {
            maybeLogPrerollDiagnostics(freeBytes, capacityBytes)
        }
    }

    @Volatile
    private var lastPrerollDiagnosticsLogMs = 0L

    /** 诊断：预滚不达标导致传输无法启动时打印（限频 2s） */
    private fun maybeLogPrerollDiagnostics(freeBytes: Long, capacityBytes: Long) {
        val now = System.currentTimeMillis()
        if (now - lastPrerollDiagnosticsLogMs < 2_000L) return
        lastPrerollDiagnosticsLogMs = now
        val queued = queuedBytesLocked()
        UsbExclusiveLog.w(
            TAG,
            "diag:preroll queued=$queued threshold=$prerollThresholdBytes " +
                "free=$freeBytes capacity=$capacityBytes"
        )
    }

    private fun queuedBytesLocked(): Long {
        val frames = UsbExclusiveNativeBridge.queuedPlayerFrames(handle)
        return frames * outputChannels * outputSubslot
    }

    fun playPlayerPcm() {
        synchronized(lock) {
            nativePausedByPlayControl = false
            if (handle == 0L) return
            if (!transportCommandGate.tryAcquire()) return
            try {
                startNativePlaybackLocked()
            } finally {
                transportCommandGate.release()
                maintainWakeLockLocked()
            }
        }
    }

    /**
     * 同句柄软重启（渲染线程安全）：pause→play 传输，保留会话/接口 claim/反馈时钟。
     * 在独立线程执行（native stopStreamingInternal 含 eventThread.join + cancel drain +
     * alt 切换，可能阻塞秒级），渲染线程最多等待 [SOFT_RESTART_TIMEOUT_MS]，
     * 超时后任务仍在后台完成；暂停期间写入的数据进 ring 不丢失，play 恢复后继续消费。
     * JNI 调用不持有会话锁（并发 close 时由 C++ acquireHandle 保护返回 false），
     * 避免写线程/渲染线程被锁阻塞。
     */
    fun requestSoftRestart(reason: String): Boolean {
        val h = synchronized(lock) { handle }
        if (h == 0L) return false
        softRestarting = true
        return try {
            nativeOpenExecutor.submit<Boolean> {
                val paused = UsbExclusiveNativeBridge.pausePlayerPcm(h)
                val restarted = paused && UsbExclusiveNativeBridge.playPlayerPcm(h)
                synchronized(lock) {
                    softRestarting = false
                    if (handle == h) {
                        nativePausedByPlayControl = false
                        nativePlaying = restarted
                        maintainWakeLockLocked()
                        updateStateLocked()
                    }
                }
                UsbExclusiveLog.i(
                    TAG,
                    "soft restart completed reason=$reason handle=$h restarted=$restarted"
                )
                restarted
            }.get(SOFT_RESTART_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            UsbExclusiveLog.w(
                TAG,
                "soft restart timed out after ${SOFT_RESTART_TIMEOUT_MS}ms " +
                    "reason=$reason (${error.message}), continuing in background"
            )
            false
        }
    }

    private fun startNativePlaybackLocked() {
        if (handle == 0L) return
        nativePausedByPlayControl = false
        if (UsbExclusiveNativeBridge.playPlayerPcm(handle)) {
            nativePlaying = true
            UsbExclusiveLog.i(TAG, "transport started")
        } else {
            UsbExclusiveLog.w(TAG, "transport start rejected by native")
        }
        maintainWakeLockLocked()
        updateStateLocked()
    }

    fun pausePlayerPcm() {
        synchronized(lock) {
            nativePausedByPlayControl = true
            nativePlaying = false
            if (handle == 0L) return
            if (!transportCommandGate.tryAcquire()) return
            try {
                UsbExclusiveNativeBridge.pausePlayerPcm(handle)
            } finally {
                transportCommandGate.release()
                maintainWakeLockLocked()
                updateStateLocked()
            }
        }
    }

    fun flushPlayerPcm() {
        synchronized(lock) {
            nativePlaying = false
            if (handle == 0L) return
            if (!transportCommandGate.tryAcquire()) return
            try {
                UsbExclusiveNativeBridge.flushPlayerPcm(handle)
            } finally {
                transportCommandGate.release()
                maintainWakeLockLocked()
                updateStateLocked()
            }
        }
    }

    fun setPlayerVolume(playerVolume: Float) {
        synchronized(lock) {
            volume = playerVolume.coerceIn(0f, 1f)
            if (handle != 0L) {
                UsbExclusiveNativeBridge.setPlayerVolume(handle, effectiveVolume())
            }
            publishVolumeStateLocked()
        }
    }

    fun setPlayerFocusMuted(muted: Boolean) {
        synchronized(lock) {
            focusMuted = muted
            if (handle != 0L) {
                UsbExclusiveNativeBridge.setPlayerFocusMuted(handle, muted)
            }
            publishVolumeStateLocked()
        }
    }

    private fun effectiveVolume(): Float {
        if (focusMuted) return 0f
        // 独占音量独立于系统音量：系统音量键/通知栏媒体条不再影响 native 增益，
        // 有效音量只由设置页专属音量条（playerVolume）决定。
        return usbExclusiveEffectiveNativeVolume(
            playerVolume = volume,
            systemVolumeFraction = 1f,
            bitPerfect = bitPerfect
        )
    }

    private fun publishVolumeStateLocked() {
        _volumeState.value = UsbExclusiveVolumeState(
            playerVolume = volume,
            systemVolumeFraction = systemVolumeFraction,
            effectiveVolume = effectiveVolume(),
            bitPerfect = bitPerfect,
            focusMuted = focusMuted
        )
    }

    /** 播放中即时调整 ring 缓冲时长（native resizeRingDuration 安全保留已有数据） */
    fun configureBufferDuration(durationMs: Int) {
        synchronized(lock) {
            bufferDurationMs = durationMs
            if (handle != 0L) {
                UsbExclusiveNativeBridge.configurePlayerBufferDuration(handle, durationMs)
                UsbExclusiveNativeBridge.configurePlayerTransferWindow(handle, durationMs)
            }
        }
    }

    /** 设置页即时应用同步漂移修正开关（会话存在时立即生效） */
    fun applySyncDriftCorrection(enabled: Boolean) {
        synchronized(lock) {
            val h = handle
            if (h != 0L) {
                UsbExclusiveNativeBridge.setSyncDriftEnabled(h, enabled)
            }
        }
    }

    fun setAppInForeground(foreground: Boolean, context: Context) {
        synchronized(lock) {
            if (appInForeground == foreground) return
            appInForeground = foreground
            appContext = context.applicationContext
            if (handle == 0L) return
            val prefs = UsbExclusiveSettingsStore(context).read()
            val newBufferMs = prefs.bufferDurationMs(foreground)
            // 缩容（后台→前台）时若 ring 水位超过新容量，native resizeRingDuration
            // 会丢弃超出部分并计入 droppedBytes → 播放跳变 + 传输状态误报"异常丢帧"。
            // 水位过高时跳过本次缩容，保持大缓冲，待会话重建/水位下降后再生效。
            if (newBufferMs < bufferDurationMs) {
                val metrics = runtimeMetrics()
                val levelBytes = metrics?.pcmLevelBytes ?: 0L
                val rate = outputSampleRate.coerceAtLeast(1)
                val frameBytes = outputChannels.coerceAtLeast(1) * outputSubslot.coerceAtLeast(1)
                val newCapacityBytes = newBufferMs.toLong() * rate * frameBytes / 1000L
                if (levelBytes > newCapacityBytes * 9 / 10) {
                    UsbExclusiveLog.w(
                        TAG,
                        "defer buffer shrink ${bufferDurationMs}ms->${newBufferMs}ms " +
                            "(level=$levelBytes > 90% of new capacity=$newCapacityBytes)"
                    )
                    return
                }
            }
            bufferDurationMs = newBufferMs
            // 播放中即时应用：native resizeRingDuration 双锁保护、保留已有数据，
            // 前台→后台扩缓冲、后台→前台缩缓冲均安全。前台 UI 渲染抢 CPU 时
            // 大缓冲提供欠载容差（前台 iso 错误多于后台的主因）。
            UsbExclusiveNativeBridge.configurePlayerBufferDuration(handle, bufferDurationMs)
            UsbExclusiveNativeBridge.configurePlayerTransferWindow(handle, bufferDurationMs)
            updateStateLocked()
        }
    }

    fun completedPositionUs(): Long {
        val h = synchronized(lock) { handle }
        if (h == 0L) return 0L
        // 采样率兜底：重开/复用间隙 outputSampleRate 可能为 0，用输入采样率替代
        val rate = outputSampleRate.takeIf { it > 0 } ?: lastInputSampleRate
        if (rate <= 0) return 0L
        val frames = UsbExclusiveNativeBridge.completedAudioFrames(h)
        return frames * 1_000_000L / rate
    }

    /** native 完成帧数（sink 位置锚定用；无会话返回 0） */
    fun completedAudioFramesNative(): Long {
        val h = synchronized(lock) { handle }
        if (h == 0L) return 0L
        return UsbExclusiveNativeBridge.completedAudioFrames(h)
    }

    /** 原生传输是否已启动（sink 写路径规划用） */
    fun isNativeTransportStarted(): Boolean = synchronized(lock) { nativePlaying }

    /** 当前会话的运行时指标（sink 写路径每次迭代获取；无会话返回 null） */
    fun runtimeMetrics(): UsbExclusiveRuntimeMetrics? {
        val h = synchronized(lock) { handle }
        if (h == 0L) return null
        val report = UsbExclusiveNativeBridge.runtimeReport(h)
        return report.usbRuntimeMetrics()
    }

    /** 当前会话的原始 native 运行时报告（诊断用；无会话返回 null） */
    fun rawRuntimeReport(): String? {
        val h = synchronized(lock) { handle }
        if (h == 0L) return null
        return UsbExclusiveNativeBridge.runtimeReport(h)
    }

    /** 保留播放意图关闭会话（StopPreserveIntent：不加打开门冷却） */
    fun closeSessionPreserveIntent() {
        synchronized(lock) {
            closeLocked()
        }
    }

    // ==================== 关闭 ====================

    fun closePlayerPcm() {
        synchronized(lock) {
            closeLocked()
        }
    }

    fun stopPlayerPcmSession(reason: String) {
        synchronized(lock) {
            openGateBlockUntilMs = System.currentTimeMillis() + OPEN_GATE_FAILURE_FUSE_MS
            openGateBlockReason = reason
            closeLocked()
        }
    }

    fun forceStopAllSessions() {
        synchronized(lock) {
            closeLocked()
        }
    }

    private fun closeLocked() {
        val h = handle
        handle = 0L
        nativePlaying = false
        transitioning = true
        maintainWakeLockLocked()
        if (h != 0L) {
            nativeCloseInFlightCount++
            try {
                ioGate.close()
                ioGate.awaitDrained(EMERGENCY_CLOSE_WAIT_MS)
                val closedInTime = runCatching {
                    nativeCloseExecutor.submit {
                        UsbExclusiveNativeBridge.stop(h)
                        UsbExclusiveNativeBridge.close(h)
                    }.get(NATIVE_CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                }.isSuccess
                if (!closedInTime) {
                    // USB 挂死：native 关闭超时，放弃该 handle（内核恢复/拔线后会自行清理）
                    UsbExclusiveLog.w(
                        TAG,
                        "native close timed out after ${NATIVE_CLOSE_TIMEOUT_MS}ms, " +
                            "abandoning handle=$h"
                    )
                }
            } finally {
                nativeCloseInFlightCount--
            }
            ioGate.open()
        }
        runCatching { connection?.close() }
        connection = null
        usbDevice = null
        transitioning = false
        maintainWakeLockLocked()
        updateStateLocked()
    }

    // ==================== 唤醒锁 ====================

    private fun maintainWakeLockLocked() {
        val context = appContext ?: return
        val shouldHold = shouldHoldUsbExclusiveWakeLock(
            streaming = nativePlaying && !nativePausedByPlayControl,
            transitioning = transitioning,
            transportCommandInFlight = transportCommandGate.isHeld(),
            nativeCloseInFlightCount = nativeCloseInFlightCount
        )
        if (shouldHold) {
            if (!UsbExclusiveWakeLock.isHeld()) {
                UsbExclusiveWakeLock.acquire(context, "usb_exclusive_playback")
            }
        } else if (UsbExclusiveWakeLock.isHeld()) {
            UsbExclusiveWakeLock.release("usb_exclusive_idle")
        }
    }

    // ==================== 状态 ====================

    private fun updateStateLocked() {
        val current = _state.value
        _state.value = current.copy(
            available = handle != 0L,
            opened = handle != 0L,
            streaming = nativePlaying,
            paused = nativePausedByPlayControl,
            transitioning = transitioning,
            handle = handle,
            source = if (handle != 0L) "player_pcm" else "idle",
            selectedDeviceName = usbDevice?.productName ?: usbDevice?.deviceName,
            inputFormat = inputFormat,
            outputFormat = if (handle != 0L) {
                "rate=$outputSampleRate channels=$outputChannels bits=$outputBits subslot=$outputSubslot"
            } else {
                "none"
            },
            outputSampleRate = outputSampleRate,
            bufferDurationMs = bufferDurationMs,
            lastError = if (handle == 0L) openGateBlockReason else null
        )
    }

    fun refreshRuntime() {
        val h = synchronized(lock) { handle }
        if (h == 0L) return
        val report = UsbExclusiveNativeBridge.runtimeReport(h)
        val frames = UsbExclusiveNativeBridge.completedAudioFrames(h)
        val queued = UsbExclusiveNativeBridge.queuedPlayerFrames(h)
        val metrics = report.usbRuntimeMetrics()
        // 从原始报告解析 pcmLevel=levelBytes/capacityBytes（slash 分隔格式）
        val pcmLevelPair = report.valueAfter("pcmLevel")?.split("/")
        val levelBytes = pcmLevelPair?.firstOrNull()?.toLongOrNull() ?: 0L
        val capacityBytes = pcmLevelPair?.getOrNull(1)?.toLongOrNull() ?: 0L
        synchronized(lock) {
            if (handle != h) return
            _state.value = _state.value.copy(
                runtimeReport = report,
                completedAudioFrames = frames,
                queuedAudioFrames = queued,
                pcmLevelBytes = levelBytes,
                pcmCapacityBytes = capacityBytes,
                playbackReady = metrics.playbackReady ?: report.contains("playbackReady=true"),
                terminalFailure = metrics.terminalFailure ?: report.contains("terminalFailure=true"),
                transportFailed = metrics.transportFailed,
                deviceOnline = metrics.deviceOnline,
                errorCode = metrics.errorCode.name.takeUnless { it == "None" },
                actionId = metrics.actionId,
                actionGeneration = metrics.actionGeneration,
                actionOwner = metrics.actionOwner.name.takeUnless { it == "None" },
                actionLatched = metrics.actionLatched,
                feedbackReady = metrics.feedbackReady,
                noDeviceObserved = report.booleanField("noDeviceObserved"),
                detachConfirmed = report.booleanField("detachConfirmed")
            )
            maybeHandleRuntimeFailureLocked(metrics)
        }
    }

    /**
     * 运行时指标驱动的会话级恢复：
     * - native 建议 Kotlin 终端动作（FreshOpen / StopPreserveIntent）时执行并确认（ack）；
     * - 传输不可恢复（transportFailed / terminalFailure / 需要新开的 errorCode）时自动重开。
     */
    private fun maybeHandleRuntimeFailureLocked(metrics: UsbExclusiveRuntimeMetrics) {
        if (handle == 0L) return
        val now = System.currentTimeMillis()
        if (now < reopenCooldownUntilMs) return

        val recommendedAction = metrics.recommendedAction
        val actionId = metrics.actionId
        val actionGeneration = metrics.actionGeneration
        if (recommendedAction != UsbExclusiveRecoveryAction.None &&
            actionId != null && actionId > 0L &&
            actionGeneration != null && actionGeneration >= 0L
        ) {
            when (recommendedAction) {
                UsbExclusiveRecoveryAction.FreshOpen -> {
                    UsbExclusiveLog.i(TAG, "runtime recommended FreshOpen, reopening")
                    reopenCooldownUntilMs = now + REOPEN_TRANSIENT_COOLDOWN_MS
                    val sessionBeforeReopen = handle
                    closeLocked()
                    openPlayerPcmLocked(
                        appContext ?: return,
                        lastInputSampleRate,
                        lastInputChannelCount,
                        lastInputEncoding
                    )
                    UsbExclusiveNativeBridge.acknowledgeRecoveryAction(
                        sessionBeforeReopen, actionGeneration, actionId
                    )
                }

                UsbExclusiveRecoveryAction.StopPreserveIntent -> {
                    UsbExclusiveLog.i(TAG, "runtime recommended StopPreserveIntent, closing session")
                    val sessionBeforeStop = handle
                    closeLocked()
                    UsbExclusiveNativeBridge.acknowledgeRecoveryAction(
                        sessionBeforeStop, actionGeneration, actionId
                    )
                }

                else -> Unit
            }
            return
        }

        if (metrics.transportFailed || metrics.terminalFailure == true ||
            metrics.errorCode.requiresFreshNativeOpen
        ) {
            UsbExclusiveLog.w(
                TAG,
                "runtime failure detected, reopening: ${metrics.errorCode.name} " +
                    "lastError=${metrics.lastError}"
            )
            reopenCooldownUntilMs = now + REOPEN_TRANSPORT_COOLDOWN_MS
            closeLocked()
            openPlayerPcmLocked(
                appContext ?: return,
                lastInputSampleRate,
                lastInputChannelCount,
                lastInputEncoding
            )
        }
    }

    private fun describeInputFormat(sampleRate: Int, channels: Int, encoding: Int): String {
        return "rate=$sampleRate channels=$channels encoding=$encoding"
    }

    // ==================== 候选格式 ====================

    private data class OutputCandidate(
        val sampleRate: Int,
        val channels: Int,
        val bits: Int,
        val subslot: Int
    )

    private fun resolveOutputCandidates(
        sourceSampleRate: Int,
        prefsSampleRate: Int?,
        bitDepthMode: UsbExclusiveBitDepthMode,
        channels: Int,
        supportedSampleRates: Collection<Int> = emptyList(),
        supportedChannelCounts: Collection<Int> = emptyList()
    ): List<OutputCandidate> {
        val requestedRate = prefsSampleRate ?: sourceSampleRate
        val deviceRates = supportedSampleRates
            .asSequence()
            .filter { it in 8_000..768_000 }
            .distinct()
            .toList()
        val rates = buildList {
            add(requestedRate)
            if (deviceRates.isNotEmpty()) {
                // 设备能力优先：紧跟目标速率之后按距离排序
                addAll(
                    deviceRates.sortedWith(
                        compareBy<Int> { kotlin.math.abs(it.toLong() - requestedRate.toLong()) }
                            .thenByDescending { it }
                    )
                )
            } else {
                addAll(listOf(48_000, 44_100, 96_000, 88_200, 192_000, 176_400, 384_000, 352_800))
            }
        }.filter { it in 8_000..768_000 }.distinct()

        // 候选位深/子槽：
        // 24/3 优先：多数 UAC2 DAC（含 CX31993 系）只有 24/3 的 alt setting，
        // 先试 24/4 会执行一次完整的 claim+alt+协商失败循环，反复执行会让
        // 廉价芯片的 USB 引擎进入退化状态（丢包/假性断开）。24/3 成功即不再尝试 24/4。
        val depthCandidates: List<Pair<Int, Int>> = when (bitDepthMode) {
            UsbExclusiveBitDepthMode.BIT_16 -> listOf(16 to 2)
            UsbExclusiveBitDepthMode.BIT_24 -> listOf(24 to 3, 24 to 4)
            UsbExclusiveBitDepthMode.BIT_32 -> listOf(32 to 4, 24 to 3, 24 to 4, 16 to 2)
            UsbExclusiveBitDepthMode.AUTO -> listOf(24 to 3, 24 to 4, 16 to 2, 32 to 4)
        }

        val resolvedChannels = if (supportedChannelCounts.isNotEmpty()) {
            channels.takeIf { it in supportedChannelCounts } ?: 2.takeIf { it in supportedChannelCounts }
                ?: supportedChannelCounts.firstOrNull()
        } else {
            channels
        } ?: 2

        return rates.flatMap { rate ->
            depthCandidates.map { (bits, subslot) ->
                OutputCandidate(rate, resolvedChannels, bits, subslot)
            }
        }
    }

    fun nativeState(): UsbExclusiveNativeState = _state.value
}
