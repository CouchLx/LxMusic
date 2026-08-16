package com.example.lxmusic.usb.device

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
 * File: moe.ouom.neriplayer.core.player.usb.device/UsbExclusiveDeviceIdentity (adapted for LxMusic)
 */

import android.hardware.usb.UsbDevice
import com.example.lxmusic.usb.DEFAULT_USB_EXCLUSIVE_DEVICE_KEY

internal fun UsbDevice.usbExclusiveDeviceKey(): String {
    return buildUsbExclusiveDeviceKey(
        vendorId = vendorId,
        productId = productId,
        label = stableUsbDeviceLabel()
    )
}

internal fun UsbDevice.matchesUsbExclusiveDeviceKey(deviceKey: String): Boolean {
    if (deviceKey == DEFAULT_USB_EXCLUSIVE_DEVICE_KEY) return true
    val selection = parseUsbExclusiveDeviceKey(deviceKey) ?: return false
    // 精确匹配：VID + PID + 标签
    if (vendorId == selection.vendorId &&
        productId == selection.productId &&
        normalizedDeviceLabel(stableUsbDeviceLabel()) == selection.label
    ) return true
    // 回退匹配：仅 VID + PID（兼容旧格式存储的设备键，如使用 deviceName 的情况）
    if (vendorId == selection.vendorId && productId == selection.productId) return true
    return false
}

internal enum class UsbExclusiveDeviceSelectionOutcome {
    SELECTED,
    NONE,
    AMBIGUOUS
}

internal data class UsbExclusiveDeviceSelectionResult<T>(
    val outcome: UsbExclusiveDeviceSelectionOutcome,
    val device: T?
)

/**
 * 统一 host 与音频侧的独占设备选择判定：
 * - 指定设备：精确匹配；多命中拒绝；仅一候选且 allowSingleFallback 时退回
 * - auto：仅当恰好一个候选时选中；多个候选返回 AMBIGUOUS（拒绝猜测，交由上层回退普通音频）
 */
internal fun <T> selectUsbExclusiveDevice(
    candidates: List<T>,
    selectedDeviceKey: String,
    allowSingleFallback: Boolean,
    matches: (T) -> Boolean
): UsbExclusiveDeviceSelectionResult<T> {
    if (selectedDeviceKey != DEFAULT_USB_EXCLUSIVE_DEVICE_KEY) {
        val matched = candidates.filter(matches)
        return when {
            matched.size == 1 -> UsbExclusiveDeviceSelectionResult(
                UsbExclusiveDeviceSelectionOutcome.SELECTED,
                matched.first()
            )
            matched.size > 1 -> UsbExclusiveDeviceSelectionResult(
                UsbExclusiveDeviceSelectionOutcome.AMBIGUOUS,
                null
            )
            allowSingleFallback && candidates.size == 1 -> UsbExclusiveDeviceSelectionResult(
                UsbExclusiveDeviceSelectionOutcome.SELECTED,
                candidates.first()
            )
            else -> UsbExclusiveDeviceSelectionResult(
                UsbExclusiveDeviceSelectionOutcome.NONE,
                null
            )
        }
    }
    return when (candidates.size) {
        0 -> UsbExclusiveDeviceSelectionResult(UsbExclusiveDeviceSelectionOutcome.NONE, null)
        1 -> UsbExclusiveDeviceSelectionResult(
            UsbExclusiveDeviceSelectionOutcome.SELECTED,
            candidates.first()
        )
        else -> UsbExclusiveDeviceSelectionResult(
            UsbExclusiveDeviceSelectionOutcome.AMBIGUOUS,
            null
        )
    }
}

private data class UsbExclusiveDeviceSelection(
    val vendorId: Int,
    val productId: Int,
    val label: String
)

private fun buildUsbExclusiveDeviceKey(
    vendorId: Int,
    productId: Int,
    label: String
): String {
    return "usb:$vendorId:$productId:${normalizedDeviceLabel(label)}"
}

private fun parseUsbExclusiveDeviceKey(deviceKey: String): UsbExclusiveDeviceSelection? {
    val parts = deviceKey.split(':', limit = 4)
    if (parts.size != 4 || parts[0] != "usb") return null
    val vendorId = parts[1].toIntOrNull() ?: return null
    val productId = parts[2].toIntOrNull() ?: return null
    val label = parts[3].takeIf(String::isNotBlank) ?: return null
    return UsbExclusiveDeviceSelection(vendorId, productId, label)
}

private fun UsbDevice.stableUsbDeviceLabel(): String {
    return productName
        ?.takeIf(String::isNotBlank)
        ?: manufacturerName?.takeIf(String::isNotBlank)
        ?: deviceName
}

private fun normalizedDeviceLabel(value: String): String {
    return value.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
}
