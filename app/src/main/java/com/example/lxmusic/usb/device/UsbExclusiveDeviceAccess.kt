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
 * File: moe.ouom.neriplayer.core.player.usb.device/UsbExclusiveDeviceAccess (adapted for LxMusic)
 */

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.example.lxmusic.usb.DEFAULT_USB_EXCLUSIVE_DEVICE_KEY
import com.example.lxmusic.usb.UsbExclusiveLog

private const val TAG = "LxUsbDeviceAccess"

/** 打开已授权且含音频流接口的 USB 音频设备（host 模式 fd 由 libusb wrap） */
internal fun openPermittedUsbAudioDevice(
    context: Context,
    selectedDeviceKey: String = DEFAULT_USB_EXCLUSIVE_DEVICE_KEY
): Pair<UsbDevice, UsbDeviceConnection>? {
    val usbManager = context.applicationContext.getSystemService(Context.USB_SERVICE) as? UsbManager
        ?: return null
    val candidates = usbManager.deviceList.values
        .sortedBy { it.deviceName }
        .filter { device ->
            runCatching { usbManager.hasPermission(device) }.getOrDefault(false) &&
                device.hasAudioStreamingInterface()
        }
    // host 侧持 VID+PID+label 精确匹配，匹配不到不退回其它设备
    val selection = selectUsbExclusiveDevice(
        candidates = candidates,
        selectedDeviceKey = selectedDeviceKey,
        allowSingleFallback = false
    ) { device -> device.matchesUsbExclusiveDeviceKey(selectedDeviceKey) }
    val targetDevice = when (selection.outcome) {
        UsbExclusiveDeviceSelectionOutcome.SELECTED -> selection.device ?: return null
        UsbExclusiveDeviceSelectionOutcome.AMBIGUOUS -> {
            // auto 且在场多个 USB 音频设备：拒绝猜测，交由上层回退普通音频
            UsbExclusiveLog.w(
                TAG,
                "openPermittedUsbAudioDevice(): ambiguous selection, refusing to guess; " +
                    "candidates=${candidates.size} key=$selectedDeviceKey"
            )
            return null
        }
        UsbExclusiveDeviceSelectionOutcome.NONE -> return null
    }
    val connection = usbManager.openDevice(targetDevice) ?: return null
    return targetDevice to connection
}

/** 查询设备是否已获得 USB 授权 */
internal fun UsbDevice.hasUsbExclusivePermission(context: Context): Boolean {
    val usbManager = context.applicationContext.getSystemService(Context.USB_SERVICE) as? UsbManager
        ?: return false
    return runCatching { usbManager.hasPermission(this) }.getOrDefault(false)
}

/** 列出当前全部 USB 音频流设备（已按 deviceName 排序） */
internal fun listUsbAudioDevices(context: Context): List<UsbDevice> {
    val usbManager = context.applicationContext.getSystemService(Context.USB_SERVICE) as? UsbManager
        ?: return emptyList()
    return runCatching {
        usbManager.deviceList.values.filter { device ->
            runCatching { device.hasAudioStreamingInterface() }.getOrDefault(false)
        }.sortedBy { it.deviceName }
    }.getOrDefault(emptyList())
}

internal fun UsbDevice.hasAudioStreamingInterface(): Boolean {
    return (0 until interfaceCount).any { index ->
        val usbInterface = getInterface(index)
        usbInterface.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
            usbInterface.interfaceSubclass == 0x02
    }
}
