package com.example.lxmusic.usb.transport

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
 * File: moe.ouom.neriplayer.core.player.usb.transport/UsbExclusiveRuntimeReport (adapted for LxMusic)
 */

/** native 运行时报告解析：`key=value` 空格分隔，v1/v2 字段共存（与 liblxmusic_usb.so 输出对齐） */

internal enum class UsbExclusiveFeedbackMode { Disabled, Explicit, Implicit }

internal enum class UsbExclusiveFeedbackState { Disabled, Priming, Acquiring, Locked, Holdover, Relocking, Failed }

internal enum class UsbExclusiveRecoveryAction {
    None,
    Holdover,
    Relock,
    SameHandleRearm,
    SwitchNativeCandidate,
    FreshOpen,
    StopPreserveIntent
}

internal enum class UsbExclusiveRecoveryActionOwner { None, Native, Kotlin }

internal enum class UsbExclusiveErrorCode {
    None,
    OpenDeferred,
    NoCompatibleFormat,
    SampleRateNegotiationFailed,
    DeviceDetached,
    ClaimInterfaceFailed,
    SetAltFailed,
    TransferFirstCompletionTimeout,
    TransferCompletionStalled,
    IsoPacketErrorBurst,
    FeedbackPayloadInvalid,
    FeedbackPacketCapacityExceeded,
    FeedbackInitialLockTimeout,
    FeedbackTransferFailed,
    FeedbackLost,
    ImplicitFeedbackTransferFailed,
    CancelDrainTimeout,
    Quarantined,
    TransportFailed,
    NativeInternalError
}

internal val UsbExclusiveErrorCode.requiresFreshNativeOpen: Boolean
    get() = when (this) {
        UsbExclusiveErrorCode.DeviceDetached,
        UsbExclusiveErrorCode.ClaimInterfaceFailed,
        UsbExclusiveErrorCode.SetAltFailed,
        UsbExclusiveErrorCode.TransferFirstCompletionTimeout,
        UsbExclusiveErrorCode.TransferCompletionStalled,
        UsbExclusiveErrorCode.IsoPacketErrorBurst,
        UsbExclusiveErrorCode.TransportFailed,
        UsbExclusiveErrorCode.FeedbackInitialLockTimeout,
        UsbExclusiveErrorCode.FeedbackTransferFailed,
        UsbExclusiveErrorCode.FeedbackLost,
        UsbExclusiveErrorCode.ImplicitFeedbackTransferFailed,
        UsbExclusiveErrorCode.CancelDrainTimeout,
        UsbExclusiveErrorCode.Quarantined,
        UsbExclusiveErrorCode.NativeInternalError -> true
        else -> false
    }

internal val UsbExclusiveErrorCode.allowsAlternativeOutputRetry: Boolean
    get() = when (this) {
        UsbExclusiveErrorCode.NoCompatibleFormat,
        UsbExclusiveErrorCode.SampleRateNegotiationFailed -> true
        else -> false
    }

internal val UsbExclusiveErrorCode.isRecoverableTransportFailure: Boolean
    get() = when (this) {
        UsbExclusiveErrorCode.TransferFirstCompletionTimeout,
        UsbExclusiveErrorCode.TransferCompletionStalled,
        UsbExclusiveErrorCode.IsoPacketErrorBurst,
        UsbExclusiveErrorCode.TransportFailed,
        UsbExclusiveErrorCode.FeedbackInitialLockTimeout,
        UsbExclusiveErrorCode.FeedbackTransferFailed,
        UsbExclusiveErrorCode.FeedbackLost,
        UsbExclusiveErrorCode.ImplicitFeedbackTransferFailed -> true
        else -> false
    }

internal val UsbExclusiveRecoveryAction.isKotlinTerminalAction: Boolean
    get() = this == UsbExclusiveRecoveryAction.FreshOpen ||
        this == UsbExclusiveRecoveryAction.StopPreserveIntent

/** 解析后的运行时指标（planner / sink 恢复策略的输入） */
internal data class UsbExclusiveRuntimeMetrics(
    val reportVersion: Int = 1,
    val reportValid: Boolean = true,
    val reportInvalidReason: String? = null,
    val feedbackMode: UsbExclusiveFeedbackMode = UsbExclusiveFeedbackMode.Disabled,
    val feedbackState: UsbExclusiveFeedbackState = UsbExclusiveFeedbackState.Disabled,
    val transportRunning: Boolean = false,
    val feedbackReady: Boolean = false,
    val realPcmReleased: Boolean = false,
    val canAcceptPcm: Boolean = false,
    val playbackReady: Boolean? = null,
    val feedbackReusable: Boolean? = null,
    val terminalFailure: Boolean? = null,
    val transportFailed: Boolean = false,
    val deviceOnline: Boolean = true,
    val errorCode: UsbExclusiveErrorCode = UsbExclusiveErrorCode.None,
    val lastError: String? = null,
    val recommendedAction: UsbExclusiveRecoveryAction = UsbExclusiveRecoveryAction.None,
    val actionId: Long? = null,
    val actionGeneration: Long? = null,
    val actionOwner: UsbExclusiveRecoveryActionOwner = UsbExclusiveRecoveryActionOwner.None,
    val actionLatched: Boolean? = null,
    val nativeStreamGeneration: Long? = null,
    val recoveryEpoch: Long? = null,
    val candidateId: String? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val bits: Int? = null,
    val subslotBytes: Int? = null,
    val transferBytes: Long? = null,
    val lastTransferBytes: Long? = null,
    val completedTransfers: Long? = null,
    val inFlightTransfers: Long? = null,
    val pcmLevelBytes: Long? = null,
    val pcmCapacityBytes: Long? = null,
    val pcmFreeBytes: Long? = null,
    val pcmBackpressureEvents: Long? = null,
    val pcmBackpressureTotalMs: Long? = null,
    val pcmBackpressureCurrentMs: Long? = null,
    val pcmBackpressureMaxMs: Long? = null,
    val playerSignalFrames: Long? = null,
    val playerSilentFrames: Long? = null,
    val playerSignalBytes: Long? = null,
    val playerDroppedBytes: Long? = null,
    val playerUnderrunBytes: Long? = null,
    val playerZeroFillBytes: Long? = null,
    val playerPausedZeroFillBytes: Long? = null,
    val completedAudioFrames: Long? = null,
    val queuedFrames: Long? = null,
    val outputPeak: Float? = null,
    val lastOutputPeak: Float? = null,
    val channel0OutputPeak: Float? = null,
    val channel1OutputPeak: Float? = null,
    val lastChannel0OutputPeak: Float? = null,
    val lastChannel1OutputPeak: Float? = null
) {
    val outputFrameBytes: Int?
        get() {
            val channelCount = channels ?: return null
            val subslot = subslotBytes ?: return null
            if (channelCount <= 0 || subslot <= 0) return null
            return channelCount * subslot
        }

    val isQueueFull: Boolean
        get() {
            val capacity = pcmCapacityBytes ?: return false
            val level = pcmLevelBytes ?: return false
            if (capacity <= 0L) return false
            return level >= capacity
        }

    val hasPcmQueue: Boolean
        get() {
            val level = pcmLevelBytes ?: return false
            val capacity = pcmCapacityBytes ?: return false
            return level > 0L && capacity > 0L
        }

    /**
     * 传输健康度：仅真实故障（transportFailed / terminalFailure / errorCode / lastError）判定不健康。
     * 注意：不把 playbackReady == false 视为故障 —— 传输未启动（写路径预滚后启动）或
     * explicit feedback 时钟仍在锁定（Priming/Acquiring）时 playbackReady 为 false 是正常瞬态，
     * 将其当作故障会导致 sink 无限重开会话、传输永远无法启动。
     * 真正的传输故障由 native 事件循环超时（first_completion_timeout / completion_stalled）上报。
     */
    val hasHealthyTransport: Boolean
        get() {
            if (transportFailed) return false
            if (terminalFailure == true) return false
            if (errorCode != UsbExclusiveErrorCode.None) return false
            if (!lastError.isNullOrBlank() && lastError != "none") return false
            return true
        }

    /** 良性背压：队列满但传输健康 */
    val isBenignBackpressure: Boolean
        get() = isQueueFull && hasHealthyTransport

    val hasKotlinTerminalRecoveryAction: Boolean
        get() = recommendedAction.isKotlinTerminalAction
}

private val reportKeyRegex = Regex("(?:^|\\s)([a-zA-Z0-9_]+)=([^\\s]+)")

internal fun String.valueAfter(key: String): String? {
    return reportKeyRegex.find(this)?.let { match ->
        if (match.groupValues[1] == key) {
            match.groupValues[2]
        } else {
            reportKeyRegex.findAll(this).firstOrNull {
                it.groupValues[1] == key
            }?.groupValues?.getOrNull(2)
        }
    }
}

internal fun String.booleanField(key: String): Boolean? {
    return valueAfter(key)?.let { token ->
        when (token) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
}

private fun String.longField(key: String): Long? {
    return valueAfter(key)?.toLongOrNull()
}

private fun String.floatField(key: String): Float? {
    return valueAfter(key)?.toFloatOrNull()
}

private fun String.intField(key: String): Int? {
    return valueAfter(key)?.toIntOrNull()
}

/** 解析 native 运行时报告为结构化指标（对齐 liblxmusic_usb.so 的 v2 字段） */
internal fun String.usbRuntimeMetrics(): UsbExclusiveRuntimeMetrics {
    if (isBlank()) {
        return UsbExclusiveRuntimeMetrics(reportValid = false, reportInvalidReason = "empty_report")
    }
    val fields = reportKeyRegex.findAll(this)
        .map { it.groupValues[1] to it.groupValues[2] }
        .toList()
    val fieldMap = fields.toMap()
    if (fieldMap.isEmpty()) {
        return UsbExclusiveRuntimeMetrics(reportValid = false, reportInvalidReason = "unparsable_report")
    }
    val reportVersion = fieldMap["reportVersion"]?.toIntOrNull() ?: 1
    var reportValid = true
    var invalidReason: String? = null
    if (reportVersion >= 2 && fieldMap["reportBuildError"] != null) {
        reportValid = false
        invalidReason = fieldMap["reportBuildError"]
    }
    val parsed = UsbExclusiveRuntimeMetrics(
        reportVersion = reportVersion,
        reportValid = reportValid,
        reportInvalidReason = invalidReason,
        feedbackMode = parseFeedbackMode(fieldMap["feedbackMode"]),
        feedbackState = parseFeedbackState(fieldMap["feedbackState"]),
        transportRunning = fieldMap["transportRunning"]?.toBooleanStrictOrNull() == true ||
            fieldMap["running"]?.toBooleanStrictOrNull() == true,
        feedbackReady = fieldMap["feedbackReady"]?.toBooleanStrictOrNull() == true,
        realPcmReleased = fieldMap["realPcmReleased"]?.toBooleanStrictOrNull() == true,
        canAcceptPcm = fieldMap["canAcceptPcm"]?.toBooleanStrictOrNull() == true,
        playbackReady = fieldMap["playbackReady"]?.toBooleanStrictOrNull(),
        feedbackReusable = fieldMap["feedbackReusable"]?.toBooleanStrictOrNull(),
        terminalFailure = fieldMap["terminalFailure"]?.toBooleanStrictOrNull(),
        transportFailed = fieldMap["transportFailed"]?.toBooleanStrictOrNull() == true,
        deviceOnline = fieldMap["deviceOnline"]?.toBooleanStrictOrNull() != false,
        errorCode = usbExclusiveErrorCode(fieldMap),
        lastError = fieldMap["lastError"]?.takeUnless { it == "none" || it.isBlank() },
        recommendedAction = parseRecoveryAction(fieldMap["recommendedAction"]),
        actionId = fieldMap["actionId"]?.toLongOrNull()?.takeIf { it > 0L },
        actionGeneration = fieldMap["actionGeneration"]?.toLongOrNull(),
        actionOwner = parseActionOwner(fieldMap["actionOwner"]),
        actionLatched = fieldMap["actionLatched"]?.toBooleanStrictOrNull(),
        nativeStreamGeneration = fieldMap["nativeStreamGeneration"]?.toLongOrNull(),
        recoveryEpoch = fieldMap["recoveryEpoch"]?.toLongOrNull(),
        candidateId = fieldMap["candidateId"]?.takeUnless { it == "none" || it.isBlank() },
        sampleRate = fieldMap["sampleRate"]?.toIntOrNull()?.takeIf { it > 0 },
        channels = fieldMap["channels"]?.toIntOrNull()?.takeIf { it > 0 },
        bits = fieldMap["bits"]?.toIntOrNull()?.takeIf { it > 0 },
        subslotBytes = fieldMap["subslotBytes"]?.toIntOrNull()?.takeIf { it > 0 },
        transferBytes = fieldMap["transferBytes"]?.toLongOrNull()?.takeIf { it > 0L },
        lastTransferBytes = fieldMap["lastTransferBytes"]?.toLongOrNull()?.takeIf { it > 0L },
        completedTransfers = fieldMap["completedTransfers"]?.toLongOrNull(),
        inFlightTransfers = fieldMap["inFlight"]?.toLongOrNull(),
        pcmLevelBytes = parsePcmLevel(fieldMap["pcmLevel"]),
        pcmCapacityBytes = parsePcmCapacity(fieldMap["pcmLevel"]),
        pcmFreeBytes = fieldMap["pcmFreeBytes"]?.toLongOrNull(),
        pcmBackpressureEvents = fieldMap["pcmBackpressureEvents"]?.toLongOrNull(),
        pcmBackpressureTotalMs = fieldMap["pcmBackpressureTotalMs"]?.toLongOrNull(),
        pcmBackpressureCurrentMs = fieldMap["pcmBackpressureCurrentMs"]?.toLongOrNull(),
        pcmBackpressureMaxMs = fieldMap["pcmBackpressureMaxMs"]?.toLongOrNull(),
        playerSignalFrames = fieldMap["playerSignalFrames"]?.toLongOrNull(),
        playerSilentFrames = fieldMap["playerSilentFrames"]?.toLongOrNull(),
        playerSignalBytes = fieldMap["playerSignalBytes"]?.toLongOrNull(),
        playerDroppedBytes = fieldMap["playerDroppedBytes"]?.toLongOrNull(),
        playerUnderrunBytes = fieldMap["playerUnderrunBytes"]?.toLongOrNull(),
        playerZeroFillBytes = fieldMap["playerZeroFillBytes"]?.toLongOrNull(),
        playerPausedZeroFillBytes = fieldMap["playerPausedZeroFillBytes"]?.toLongOrNull(),
        completedAudioFrames = fieldMap["completedAudioFrames"]?.toLongOrNull(),
        queuedFrames = fieldMap["queuedFrames"]?.toLongOrNull(),
        outputPeak = fieldMap["outputPeak"]?.toFloatOrNull(),
        lastOutputPeak = fieldMap["lastOutputPeak"]?.toFloatOrNull(),
        channel0OutputPeak = fieldMap["channel0OutputPeak"]?.toFloatOrNull(),
        channel1OutputPeak = fieldMap["channel1OutputPeak"]?.toFloatOrNull(),
        lastChannel0OutputPeak = fieldMap["lastChannel0OutputPeak"]?.toFloatOrNull(),
        lastChannel1OutputPeak = fieldMap["lastChannel1OutputPeak"]?.toFloatOrNull()
    )
    if (parsed.errorCode != UsbExclusiveErrorCode.None) {
        return parsed
    }
    if (parsed.transportFailed) {
        return parsed.copy(errorCode = classifyTransportError(fieldMap["lastError"].orEmpty()))
    }
    return parsed
}

/** errorCode= 字段缺失时按 lastError 关键字分类 */
private fun classifyTransportError(lastError: String): UsbExclusiveErrorCode {
    return when {
        lastError.contains("first_completion_timeout") ->
            UsbExclusiveErrorCode.TransferFirstCompletionTimeout
        lastError.contains("completion_stalled") ->
            UsbExclusiveErrorCode.TransferCompletionStalled
        lastError.contains("feedback_initial_lock_timeout") ->
            UsbExclusiveErrorCode.FeedbackInitialLockTimeout
        lastError.contains("feedback_payload") ->
            UsbExclusiveErrorCode.FeedbackPayloadInvalid
        lastError.contains("feedback_transfer") ->
            UsbExclusiveErrorCode.FeedbackTransferFailed
        lastError.contains("feedback_lost") ->
            UsbExclusiveErrorCode.FeedbackLost
        lastError.contains("iso_packet") ->
            UsbExclusiveErrorCode.IsoPacketErrorBurst
        lastError.contains("cancel_drain") ->
            UsbExclusiveErrorCode.CancelDrainTimeout
        lastError.contains("quarantine") ->
            UsbExclusiveErrorCode.Quarantined
        else -> UsbExclusiveErrorCode.TransportFailed
    }
}

private fun parseFeedbackMode(token: String?): UsbExclusiveFeedbackMode {
    return when (token) {
        "explicit" -> UsbExclusiveFeedbackMode.Explicit
        "implicit" -> UsbExclusiveFeedbackMode.Implicit
        else -> UsbExclusiveFeedbackMode.Disabled
    }
}

private fun parseFeedbackState(token: String?): UsbExclusiveFeedbackState {
    return when (token) {
        "Priming" -> UsbExclusiveFeedbackState.Priming
        "Acquiring" -> UsbExclusiveFeedbackState.Acquiring
        "Locked" -> UsbExclusiveFeedbackState.Locked
        "Holdover" -> UsbExclusiveFeedbackState.Holdover
        "Relocking" -> UsbExclusiveFeedbackState.Relocking
        "Failed" -> UsbExclusiveFeedbackState.Failed
        else -> UsbExclusiveFeedbackState.Disabled
    }
}

private fun parseRecoveryAction(token: String?): UsbExclusiveRecoveryAction {
    return when (token?.uppercase()) {
        "HOLDOVER" -> UsbExclusiveRecoveryAction.Holdover
        "RELOCK" -> UsbExclusiveRecoveryAction.Relock
        "SAME_HANDLE_REARM" -> UsbExclusiveRecoveryAction.SameHandleRearm
        "SWITCH_NATIVE_CANDIDATE" -> UsbExclusiveRecoveryAction.SwitchNativeCandidate
        "FRESH_OPEN" -> UsbExclusiveRecoveryAction.FreshOpen
        "STOP_PRESERVE_INTENT" -> UsbExclusiveRecoveryAction.StopPreserveIntent
        else -> UsbExclusiveRecoveryAction.None
    }
}

private fun parseActionOwner(token: String?): UsbExclusiveRecoveryActionOwner {
    return when (token) {
        "native" -> UsbExclusiveRecoveryActionOwner.Native
        "kotlin" -> UsbExclusiveRecoveryActionOwner.Kotlin
        else -> UsbExclusiveRecoveryActionOwner.None
    }
}

private fun parsePcmLevel(token: String?): Long? {
    return token?.substringBefore('/')?.toLongOrNull()
}

private fun parsePcmCapacity(token: String?): Long? {
    return token?.substringAfter('/', missingDelimiterValue = "")?.toLongOrNull()
}

private fun String.toBooleanStrictOrNull(): Boolean? {
    return when (this) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

/** errorCode= 字段的解析（native 报告已带该字段时直接采用） */
private fun usbExclusiveErrorCode(fieldMap: Map<String, String>): UsbExclusiveErrorCode {
    val explicit = fieldMap["errorCode"]
    if (explicit != null) {
        return parseExplicitErrorCode(explicit)
    }
    val lastError = fieldMap["lastError"].orEmpty()
    return when {
        lastError.contains("no_compatible_format", ignoreCase = true) ||
            lastError.contains("native_open_deferred", ignoreCase = true) -> {
            if (lastError.contains("native_open_deferred")) {
                UsbExclusiveErrorCode.OpenDeferred
            } else {
                UsbExclusiveErrorCode.NoCompatibleFormat
            }
        }
        lastError.contains("claim_interface", ignoreCase = true) ->
            UsbExclusiveErrorCode.ClaimInterfaceFailed
        lastError.contains("set_alt", ignoreCase = true) ->
            UsbExclusiveErrorCode.SetAltFailed
        lastError.contains("usb_device_detached", ignoreCase = true) ||
            lastError.contains("deviceonline=false", ignoreCase = true) ->
            UsbExclusiveErrorCode.DeviceDetached
        lastError.contains("transportfailed=true", ignoreCase = true) ->
            classifyTransportError(lastError)
        else -> UsbExclusiveErrorCode.None
    }
}

private fun parseExplicitErrorCode(token: String): UsbExclusiveErrorCode {
    return when (token) {
        "None" -> UsbExclusiveErrorCode.None
        "OpenDeferred" -> UsbExclusiveErrorCode.OpenDeferred
        "NoCompatibleFormat" -> UsbExclusiveErrorCode.NoCompatibleFormat
        "SampleRateNegotiationFailed" -> UsbExclusiveErrorCode.SampleRateNegotiationFailed
        "DeviceDetached" -> UsbExclusiveErrorCode.DeviceDetached
        "ClaimInterfaceFailed" -> UsbExclusiveErrorCode.ClaimInterfaceFailed
        "SetAltFailed" -> UsbExclusiveErrorCode.SetAltFailed
        "TransferFirstCompletionTimeout" -> UsbExclusiveErrorCode.TransferFirstCompletionTimeout
        "TransferCompletionStalled" -> UsbExclusiveErrorCode.TransferCompletionStalled
        "IsoPacketErrorBurst" -> UsbExclusiveErrorCode.IsoPacketErrorBurst
        "FeedbackPayloadInvalid" -> UsbExclusiveErrorCode.FeedbackPayloadInvalid
        "FeedbackPacketCapacityExceeded" -> UsbExclusiveErrorCode.FeedbackPacketCapacityExceeded
        "FeedbackInitialLockTimeout" -> UsbExclusiveErrorCode.FeedbackInitialLockTimeout
        "FeedbackTransferFailed" -> UsbExclusiveErrorCode.FeedbackTransferFailed
        "FeedbackLost" -> UsbExclusiveErrorCode.FeedbackLost
        "ImplicitFeedbackTransferFailed" -> UsbExclusiveErrorCode.ImplicitFeedbackTransferFailed
        "CancelDrainTimeout" -> UsbExclusiveErrorCode.CancelDrainTimeout
        "Quarantined" -> UsbExclusiveErrorCode.Quarantined
        "TransportFailed" -> UsbExclusiveErrorCode.TransportFailed
        else -> UsbExclusiveErrorCode.NativeInternalError
    }
}
