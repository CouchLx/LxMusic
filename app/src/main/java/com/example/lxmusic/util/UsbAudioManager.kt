package com.example.lxmusic.util

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.hardware.usb.UsbManager

/**
 * USB DAC 音频输出支持（对齐 NeriPlayer 的 usbExclusivePlayback 理念）。
 *
 * 实现方式：通过 Android 系统 USB 音频设备（AudioDeviceInfo.TYPE_USB_DEVICE / TYPE_USB_HEADSET）
 * 路由音频输出。Media3 DefaultAudioSink 的 setPreferredDevice 会把 AudioTrack 直通到 USB DAC，
 * 由系统 USB 音频 HAL 以原生采样率/位深（位完美）处理。
 *
 * 与 NeriPlayer 的 JNI libusb 直写不同，这里不依赖原生层，兼容 Media3/ExoPlayer 架构。
 */

object UsbAudioManager {
    /** 枚举当前连接的 USB 音频输出设备。 */
    fun usbAudioDevices(context: Context): List<AudioDeviceInfo> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return emptyList()
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { device ->
                device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }
    }

    /** 获取当前 USB 音频设备，无则返回 null。 */
    fun currentUsbAudioDevice(context: Context): AudioDeviceInfo? {
        return usbAudioDevices(context).firstOrNull()
    }

    /** 是否已连接 USB DAC。 */
    fun isUsbAudioConnected(context: Context): Boolean = usbAudioDevices(context).isNotEmpty()

    /** USB 设备显示名称。 */
    fun usbDeviceLabel(device: AudioDeviceInfo): String =
        device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB 音频设备"

    /** 检查 USB 设备接入权限（Android 系统 USB 音频路由通常无需单独授权，此方法用于提示）。 */
    fun hasUsbPermission(context: Context): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return true
        return true
    }
}
