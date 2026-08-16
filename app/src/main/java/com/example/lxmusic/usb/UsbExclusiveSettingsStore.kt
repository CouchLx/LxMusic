package com.example.lxmusic.usb

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
 * File: moe.ouom.neriplayer.data.settings/UsbExclusiveSettingsStore (adapted for LxMusic, SharedPreferences)
 */

import android.content.Context
import android.content.SharedPreferences

/**
 * USB 独占播放设置存储（SharedPreferences 版，替代 Neri 的 DataStore + 快照双写）
 * 总开关 key 与旧版 usb_exclusive_playback 复用，其余字段独立 key。
 */
class UsbExclusiveSettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    object Keys {
        const val USB_EXCLUSIVE_PLAYBACK = "usb_exclusive_playback"
        const val DEVICE_KEY = "usb_exclusive_device_key"
        const val SAMPLE_RATE_MODE = "usb_exclusive_sample_rate_mode"
        const val BIT_DEPTH_MODE = "usb_exclusive_bit_depth_mode"
        const val BIT_PERFECT = "usb_exclusive_bit_perfect"
        const val BUFFER_PROFILE = "usb_exclusive_buffer_profile"
        const val UNSUPPORTED_FORMAT_POLICY = "usb_exclusive_unsupported_format_policy"
        const val SAMPLE_RATE_COMPATIBILITY = "usb_exclusive_sample_rate_compatibility"
        const val BIT_DEPTH_COMPATIBILITY = "usb_exclusive_bit_depth_compatibility"
        const val CHANNEL_COMPATIBILITY = "usb_exclusive_channel_compatibility"
        const val FOREGROUND_BUFFER_MS = "usb_exclusive_foreground_buffer_ms"
        const val BACKGROUND_BUFFER_MS = "usb_exclusive_background_buffer_ms"
        const val VOLUME_RISK_THRESHOLD_DBFS = "usb_exclusive_volume_risk_threshold_dbfs"
        const val SYNC_DRIFT_CORRECTION = "usb_exclusive_sync_drift_correction"
        const val PLAYER_VOLUME = "usb_exclusive_player_volume"
    }

    fun isEnabled(): Boolean = prefs.getBoolean(Keys.USB_EXCLUSIVE_PLAYBACK, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Keys.USB_EXCLUSIVE_PLAYBACK, enabled).apply()
    }

    fun read(): UsbExclusivePreferences {
        return UsbExclusivePreferences.fromStorageValues(
            selectedDeviceKey = prefs.getString(Keys.DEVICE_KEY, null),
            sampleRateMode = prefs.getString(Keys.SAMPLE_RATE_MODE, null),
            bitDepthMode = prefs.getString(Keys.BIT_DEPTH_MODE, null),
            bitPerfect = prefs.getBoolean(Keys.BIT_PERFECT, DEFAULT_USB_EXCLUSIVE_BIT_PERFECT),
            bufferProfile = prefs.getString(Keys.BUFFER_PROFILE, null),
            unsupportedFormatPolicy = prefs.getString(Keys.UNSUPPORTED_FORMAT_POLICY, null),
            sampleRateCompatibilityEnabled = prefs.getBoolean(
                Keys.SAMPLE_RATE_COMPATIBILITY,
                DEFAULT_USB_EXCLUSIVE_SAMPLE_RATE_COMPATIBILITY
            ),
            bitDepthCompatibilityEnabled = prefs.getBoolean(
                Keys.BIT_DEPTH_COMPATIBILITY,
                DEFAULT_USB_EXCLUSIVE_BIT_DEPTH_COMPATIBILITY
            ),
            channelCompatibilityEnabled = prefs.getBoolean(
                Keys.CHANNEL_COMPATIBILITY,
                DEFAULT_USB_EXCLUSIVE_CHANNEL_COMPATIBILITY
            ),
            foregroundBufferMs = prefs.getInt(
                Keys.FOREGROUND_BUFFER_MS,
                DEFAULT_USB_EXCLUSIVE_FOREGROUND_BUFFER_MS
            ),
            backgroundBufferMs = prefs.getInt(
                Keys.BACKGROUND_BUFFER_MS,
                DEFAULT_USB_EXCLUSIVE_BACKGROUND_BUFFER_MS
            ),
            volumeRiskThresholdDbfs = prefs.getInt(
                Keys.VOLUME_RISK_THRESHOLD_DBFS,
                DEFAULT_USB_EXCLUSIVE_VOLUME_RISK_THRESHOLD_DBFS
            ),
            syncDriftCorrectionEnabled = prefs.getBoolean(
                Keys.SYNC_DRIFT_CORRECTION,
                DEFAULT_USB_EXCLUSIVE_SYNC_DRIFT_CORRECTION
            )
        )
    }

    fun write(preferences: UsbExclusivePreferences) {
        prefs.edit()
            .putString(Keys.DEVICE_KEY, preferences.selectedDeviceKey)
            .putString(Keys.SAMPLE_RATE_MODE, preferences.sampleRateMode.storageValue)
            .putString(Keys.BIT_DEPTH_MODE, preferences.bitDepthMode.storageValue)
            .putBoolean(Keys.BIT_PERFECT, preferences.bitPerfect)
            .putString(Keys.BUFFER_PROFILE, preferences.bufferProfile.storageValue)
            .putString(
                Keys.UNSUPPORTED_FORMAT_POLICY,
                preferences.unsupportedFormatPolicy.storageValue
            )
            .putBoolean(
                Keys.SAMPLE_RATE_COMPATIBILITY,
                preferences.sampleRateCompatibilityEnabled
            )
            .putBoolean(
                Keys.BIT_DEPTH_COMPATIBILITY,
                preferences.bitDepthCompatibilityEnabled
            )
            .putBoolean(Keys.CHANNEL_COMPATIBILITY, preferences.channelCompatibilityEnabled)
            .putInt(Keys.FOREGROUND_BUFFER_MS, preferences.foregroundBufferMs)
            .putInt(Keys.BACKGROUND_BUFFER_MS, preferences.backgroundBufferMs)
            .putInt(Keys.VOLUME_RISK_THRESHOLD_DBFS, preferences.volumeRiskThresholdDbfs)
            .putBoolean(Keys.SYNC_DRIFT_CORRECTION, preferences.syncDriftCorrectionEnabled)
            .apply()
    }

    fun setDeviceKey(key: String) {
        prefs.edit().putString(Keys.DEVICE_KEY, normalizeUsbExclusiveDeviceKey(key)).apply()
    }

    fun setSampleRateMode(mode: UsbExclusiveSampleRateMode) {
        prefs.edit().putString(Keys.SAMPLE_RATE_MODE, mode.storageValue).apply()
    }

    fun setBitDepthMode(mode: UsbExclusiveBitDepthMode) {
        prefs.edit().putString(Keys.BIT_DEPTH_MODE, mode.storageValue).apply()
    }

    fun setBitPerfect(enabled: Boolean) {
        prefs.edit().putBoolean(Keys.BIT_PERFECT, enabled).apply()
    }

    fun setUnsupportedFormatPolicy(policy: UsbExclusiveUnsupportedFormatPolicy) {
        prefs.edit().putString(Keys.UNSUPPORTED_FORMAT_POLICY, policy.storageValue).apply()
    }

    fun setSampleRateCompatibility(enabled: Boolean) {
        prefs.edit().putBoolean(Keys.SAMPLE_RATE_COMPATIBILITY, enabled).apply()
    }

    fun setBitDepthCompatibility(enabled: Boolean) {
        prefs.edit().putBoolean(Keys.BIT_DEPTH_COMPATIBILITY, enabled).apply()
    }

    fun setChannelCompatibility(enabled: Boolean) {
        prefs.edit().putBoolean(Keys.CHANNEL_COMPATIBILITY, enabled).apply()
    }

    fun setForegroundBufferMs(value: Int) {
        prefs.edit()
            .putInt(Keys.FOREGROUND_BUFFER_MS, normalizeUsbExclusiveForegroundBufferMs(value))
            .apply()
    }

    fun setBackgroundBufferMs(value: Int) {
        prefs.edit()
            .putInt(Keys.BACKGROUND_BUFFER_MS, normalizeUsbExclusiveBackgroundBufferMs(value))
            .apply()
    }

    fun setVolumeRiskThresholdDbfs(value: Int) {
        prefs.edit()
            .putInt(Keys.VOLUME_RISK_THRESHOLD_DBFS, normalizeUsbExclusiveVolumeRiskThresholdDbfs(value))
            .apply()
    }

    fun setSyncDriftCorrection(enabled: Boolean) {
        prefs.edit().putBoolean(Keys.SYNC_DRIFT_CORRECTION, enabled).apply()
    }

    /** 专属独占音量（0-1，= level/300）：仅设置页音量条可调，独立于系统音量；默认 -40dB（level 100） */
    fun getPlayerVolume(): Float = prefs.getFloat(Keys.PLAYER_VOLUME, 1f / 3f).coerceIn(0f, 1f)

    fun setPlayerVolume(value: Float) {
        prefs.edit().putFloat(Keys.PLAYER_VOLUME, value.coerceIn(0f, 1f)).apply()
    }
}
