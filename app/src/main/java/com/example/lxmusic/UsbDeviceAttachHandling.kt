package com.example.lxmusic

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * USB 设备插入自动响应的 activity-alias 状态管理
 * 对齐 NeriPlayer 的 UsbDeviceAttachHandling：
 * 通过 PackageManager 动态启停 activity-alias，
 * 控制系统是否在插入 USB DAC 时自动拉起应用。
 */
internal object UsbDeviceAttachHandling {

    const val ACTIVITY_ALIAS_NAME = "com.example.lxmusic.UsbDeviceAttachedActivityAlias"

    fun applyComponentState(context: Context, handlingEnabled: Boolean) {
        val appContext = context.applicationContext
        val component = ComponentName(appContext.packageName, ACTIVITY_ALIAS_NAME)
        val desiredState = if (handlingEnabled) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        runCatching {
            val current = appContext.packageManager.getComponentEnabledSetting(component)
            if (current != desiredState) {
                appContext.packageManager.setComponentEnabledSetting(
                    component,
                    desiredState,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }

    fun shouldProcessUsbDeviceAttachedAction(
        action: String?,
        handlingEnabled: Boolean
    ): Boolean {
        return action != android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED || handlingEnabled
    }
}
