package com.example.lxmusic.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * USB 设备权限助手：请求设备授权（ACTION_USB_PERMISSION），
 * 结果通过 permissionResult StateFlow 通知设置页刷新状态。
 * 单例：首次 use() 时注册广播接收器。
 */
internal object UsbExclusivePermissionManager {

    private const val ACTION_USB_PERMISSION = "com.example.lxmusic.USB_PERMISSION"
    private const val TAG = "LxUsbPermission"
    private const val PERMISSION_REQUEST_COOLDOWN_MS = 3_000L

    private val _permissionResult = MutableStateFlow<String?>(null)
    val permissionResult: StateFlow<String?> = _permissionResult

    private var receiverRegistered = false
    private var lastRequestKey: String? = null
    private var lastRequestAtMs: Long = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val device: UsbDevice? =
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val deviceLabel = device?.let { "VID ${it.vendorId} / PID ${it.productId}" } ?: "unknown"
            val message = if (granted) {
                "已授权 USB 设备：$deviceLabel"
            } else {
                "USB 设备授权被拒绝：$deviceLabel"
            }
            Log.i(TAG, message)
            UsbExclusiveLog.i(TAG, message)
            _permissionResult.value = message
            // 授权成功后自动重试独占播放（对齐 NeriPlayer：权限授予 → 重试 → 自动打开原生会话）
            if (granted) {
                context.sendBroadcast(Intent("com.example.lxmusic.SETTINGS_CHANGED"))
            }
        }
    }

    /** 注册广播接收器（幂等），页面可见期间调用 */
    fun register(context: Context) {
        if (receiverRegistered) return
        synchronized(this) {
            if (receiverRegistered) return
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            receiverRegistered = true
        }
    }

    /** 反注册（幂等），页面销毁时调用 */
    fun unregister(context: Context) {
        if (!receiverRegistered) return
        synchronized(this) {
            if (!receiverRegistered) return
            runCatching { context.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
    }

    /** 请求指定设备授权；返回 false 表示设备不可用/管理器异常/冷却中 */
    fun requestPermission(context: Context, device: UsbDevice): Boolean {
        val usbManager = context.applicationContext.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return false
        if (usbManager.hasPermission(device)) return true

        // 3s 冷却：同一设备 key 限流，避免反复弹系统授权框
        val requestKey = "${device.vendorId}:${device.productId}:${device.deviceName}"
        val now = android.os.SystemClock.elapsedRealtime()
        if (requestKey == lastRequestKey && now - lastRequestAtMs < PERMISSION_REQUEST_COOLDOWN_MS) {
            return false
        }
        lastRequestKey = requestKey
        lastRequestAtMs = now

        val flags = if (Build.VERSION.SDK_INT >= 31) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            flags
        )
        return try {
            usbManager.requestPermission(device, permissionIntent)
            true
        } catch (error: Exception) {
            Log.e(TAG, "requestPermission failed", error)
            UsbExclusiveLog.e(TAG, "requestPermission failed: ${error.message}")
            false
        }
    }
}
