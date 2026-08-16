package com.example.lxmusic

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager

/**
 * 应用入口：启动时同步 USB 插入自动响应组件状态
 */
class LxMusicApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        applyUsbAttachHandlingComponentState()
    }

    /**
     * 同步 activity-alias 启用/禁用状态：
     * 用户在设置页关闭"自动响应 USB 插入"后，alias 被禁用，系统不再在插入小尾巴时拉起应用。
     */
    fun applyUsbAttachHandlingComponentState() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val handlingEnabled = prefs.getBoolean("usb_exclusive_auto_attach_handling", true)
        val component = ComponentName(this, UsbDeviceAttachHandling.ACTIVITY_ALIAS_NAME)
        val desiredState = if (handlingEnabled) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        runCatching {
            val current = packageManager.getComponentEnabledSetting(component)
            if (current != desiredState) {
                packageManager.setComponentEnabledSetting(
                    component,
                    desiredState,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
