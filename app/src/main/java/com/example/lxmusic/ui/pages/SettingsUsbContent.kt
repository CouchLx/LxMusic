package com.example.lxmusic.ui.pages

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.lxmusic.UsbDeviceAttachHandling
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import androidx.media3.common.C
import com.example.lxmusic.LxMusicApplication
import com.example.lxmusic.usb.UsbExclusiveBitDepthMode
import com.example.lxmusic.usb.UsbExclusiveBufferProfile
import com.example.lxmusic.usb.UsbExclusiveLog
import com.example.lxmusic.usb.UsbExclusivePermissionManager
import com.example.lxmusic.usb.UsbExclusiveSampleRateMode
import com.example.lxmusic.usb.UsbExclusiveSettingsStore
import com.example.lxmusic.usb.UsbExclusiveUnsupportedFormatPolicy
import com.example.lxmusic.usb.device.hasUsbExclusivePermission
import com.example.lxmusic.usb.device.listUsbAudioDevices
import com.example.lxmusic.usb.device.usbExclusiveDeviceKey
import com.example.lxmusic.usb.session.UsbExclusiveSessionController
import com.example.lxmusic.usb.system.USB_EXCLUSIVE_VOLUME_DB_MIN
import com.example.lxmusic.usb.transport.UsbExclusiveNativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * USB 独占播放设置页（对齐参考设计：大卡片 + 分割线分组）：
 * 状态与开关 / USB 音频设备 / 音质策略 / 运行日志
 */
@Composable
fun SettingsUsbContent(
    usbStore: com.example.lxmusic.usb.UsbExclusiveSettingsStore
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(usbStore.isEnabled()) }
    val current = remember { usbStore.read() }
    var sampleRateMode by remember { mutableStateOf(current.sampleRateMode) }
    var bitDepthMode by remember { mutableStateOf(current.bitDepthMode) }
    var bitPerfect by remember { mutableStateOf(current.bitPerfect) }
    var syncDriftCorrection by remember {
        mutableStateOf(current.syncDriftCorrectionEnabled)
    }
    var unsupportedPolicy by remember {
        mutableStateOf(current.unsupportedFormatPolicy)
    }
    var sampleRateCompat by remember { mutableStateOf(current.sampleRateCompatibilityEnabled) }
    var bitDepthCompat by remember { mutableStateOf(current.bitDepthCompatibilityEnabled) }
    var channelCompat by remember { mutableStateOf(current.channelCompatibilityEnabled) }
    var foregroundBuffer by remember { mutableStateOf(current.foregroundBufferMs.toFloat()) }
    var backgroundBuffer by remember { mutableStateOf(current.backgroundBufferMs.toFloat()) }
    var volumeRisk by remember { mutableStateOf(current.volumeRiskThresholdDbfs.toFloat()) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showSampleRateDialog by remember { mutableStateOf(false) }
    var showBitDepthDialog by remember { mutableStateOf(false) }
    var showPolicyDialog by remember { mutableStateOf(false) }
    var showBufferProfileDialog by remember { mutableStateOf(false) }

    var autoAttachHandling by remember {
        mutableStateOf(
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("usb_exclusive_auto_attach_handling", true)
        )
    }

    // ==================== 会话状态（1s 轮询） ====================
    val sessionState by UsbExclusiveSessionController.state.collectAsState()
    var gateRemainingMs by remember { mutableLongStateOf(0L) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var logSnapshot by remember { mutableStateOf("") }
    var showLogs by remember { mutableStateOf(false) }
    var usbDevices by remember { mutableStateOf(listUsbAudioDevices(context)) }

    LaunchedEffect(Unit) {
        // 轮询放 IO 线程：refreshRuntime/openGateRemainingMs 会进入会话锁并调用 JNI
        // （native 侧阻塞持 apiLock），主线程轮询会在 native 长操作期间卡死整个 UI
        while (true) {
            val poll = withContext(Dispatchers.IO) {
                UsbExclusiveSessionController.refreshRuntime()
                UsbExclusiveSessionController.openGateRemainingMs() to
                    UsbExclusiveLog.formattedSnapshot()
            }
            gateRemainingMs = poll.first
            logSnapshot = poll.second
            // 轮询 2s 一次：refreshRuntime 构建 60+ 字段大报告并持 apiLock，
            // 高频轮询与 native 写路径抢锁是前台 iso 错误增多的诱因之一
            delay(2000)
        }
    }

    // 授权结果 → 刷新设备列表
    LaunchedEffect(Unit) {
        UsbExclusivePermissionManager.permissionResult.collect { result ->
            if (result != null) {
                usbDevices = listUsbAudioDevices(context)
                testResult = result
            }
        }
    }

    // 注册权限接收器 + USB 拔插广播刷新设备列表
    DisposableEffect(Unit) {
        UsbExclusivePermissionManager.register(context)
        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                usbDevices = listUsbAudioDevices(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(usbReceiver, filter)
        }
        onDispose {
            runCatching { context.unregisterReceiver(usbReceiver) }
            UsbExclusivePermissionManager.unregister(context)
        }
    }

    fun notifySettingsChanged() {
        context.sendBroadcast(Intent("com.example.lxmusic.SETTINGS_CHANGED"))
    }

    fun requestPermissionFor(device: UsbDevice?) {
        if (device == null) return
        UsbExclusivePermissionManager.requestPermission(context, device)
    }

    fun testConnection() {
        if (testing) return
        if (sessionState.opened) {
            testResult = "已连接：${sessionState.outputFormat}"
            return
        }
        testing = true
        testResult = null
        scope.launch(Dispatchers.IO) {
            val ok = UsbExclusiveSessionController.openPlayerPcm(
                context, 48_000, 2, C.ENCODING_PCM_16BIT
            )
            val result = if (ok) {
                // 成功后保留会话：播放时 sink 复用（prepare 新输入格式）
                val format = UsbExclusiveSessionController.nativeState().outputFormat
                "连接成功：$format"
            } else {
                val gate = UsbExclusiveSessionController.openGateReason()
                val nativeError = UsbExclusiveNativeBridge.lastOpenError().takeIf { it.isNotBlank() }
                val reason = gate ?: nativeError ?: "未知原因"
                "连接失败：$reason"
            }
            withContext(Dispatchers.Main) {
                testing = false
                testResult = result
            }
        }
    }

    // 选择/首台音频设备的授权状态
    val selectedDevice = usbDevices.firstOrNull { device ->
        val key = "usb:${device.vendorId}:${device.productId}:${device.deviceName}"
        current.selectedDeviceKey == key
    } ?: usbDevices.firstOrNull()
    val hasPermission = selectedDevice?.hasUsbExclusivePermission(context) ?: false

    // 查询系统识别的 USB 音频设备能力（采样率/声道/编码）
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val usbAudioDeviceInfo = remember(selectedDevice, usbDevices) {
        if (selectedDevice == null) return@remember null
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { info ->
            info.type == AudioDeviceInfo.TYPE_USB_DEVICE &&
                info.productName?.toString()?.contains(
                    selectedDevice.productName ?: "", ignoreCase = true
                ) == true
        } ?: audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { info ->
            info.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                info.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    // ==================== 状态横幅 ====================
    val (bannerText, bannerColor) = when {
        !enabled -> "已关闭" to MaterialTheme.colorScheme.onSurfaceVariant
        usbDevices.isEmpty() -> "未检测到 USB 音频设备" to MaterialTheme.colorScheme.error
        !hasPermission -> "设备未授权，点击「USB Host 权限」授权后自动重试" to MaterialTheme.colorScheme.error
        sessionState.opened && sessionState.streaming -> "正在播放到 USB 设备" to Color(0xFF2E7D32)
        sessionState.opened -> "已连接，等待播放" to Color(0xFF2E7D32)
        sessionState.lastError != null -> "开启失败：${sessionState.lastError}" to MaterialTheme.colorScheme.error
        else -> "等待 USB 音频设备" to MaterialTheme.colorScheme.primary
    }

    Column {
        // ==================== 卡片 0：设备连接信息（深色卡片，参考图一风格） ====================
        DeviceConnectionCard(
            enabled = enabled,
            sessionState = sessionState,
            selectedDevice = selectedDevice,
            hasPermission = hasPermission
        )
        Spacer(modifier = Modifier.height(24.dp))

        // ==================== 实时传输状态卡片（常驻，中间位置） ====================
        if (enabled) {
            TransmissionStatusCard(sessionState)
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ==================== 卡片 1：独占开关（精简版，只保留开关本身） ====================
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "USB 独占播放",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "绕过系统音频输出，原生驱动直接输出到 USB DAC",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { value ->
                        enabled = value
                        usbStore.setEnabled(value)
                        notifySettingsChanged()
                        if (value && selectedDevice != null && !hasPermission) {
                            requestPermissionFor(selectedDevice)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==================== 卡片 2：USB 音频设备 ====================
        Text(
            text = "USB 音频设备",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // 设备选择
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeviceDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "设备选择",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (current.selectedDeviceKey == "auto") {
                                "自动（检测到 ${usbDevices.size} 个设备）"
                            } else {
                                current.selectedDeviceKey
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // USB Host 权限
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = selectedDevice != null) {
                            selectedDevice?.let { requestPermissionFor(it) }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "USB Host 权限",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = when {
                                selectedDevice == null -> "等待兼容设备"
                                hasPermission -> "已授权，点击重新授权"
                                else -> "未授权，点击授权"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasPermission) {
                                Color(0xFF2E7D32)
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    if (selectedDevice != null) {
                        Text(
                            text = if (hasPermission) "已授权" else "未授权",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (hasPermission) Color(0xFF2E7D32)
                                    else MaterialTheme.colorScheme.error
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // 自动响应 USB 插入（对齐 NeriPlayer：控制 activity-alias 启停）
                UsbSwitchRow(
                    title = "自动响应 USB 插入",
                    subtitle = "插入 DAC 时自动弹出授权对话框并尝试独占播放",
                    checked = autoAttachHandling,
                    onCheckedChange = { value ->
                        autoAttachHandling = value
                        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                            .edit().putBoolean("usb_exclusive_auto_attach_handling", value).apply()
                        // 用真实 context 启停 activity-alias。
                        // 注意：不能 new LxMusicApplication() 实例调实例方法——
                        // 手动 new 的 Application 没有系统注入的 context，调用即空指针闪退
                        val component = ComponentName(
                            context,
                            UsbDeviceAttachHandling.ACTIVITY_ALIAS_NAME
                        )
                        val desired = if (value) {
                            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                        } else {
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        }
                        runCatching {
                            context.packageManager.setComponentEnabledSetting(
                                component,
                                desired,
                                PackageManager.DONT_KILL_APP
                            )
                        }
                        notifySettingsChanged()
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // USB 独占后台权限：跳转系统应用详情/电池设置，设置省电策略（无限制等）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                // 优先跳本应用电池设置（Android 13+），回退应用详情页。
                                // ACTION_APP_BATTERY_SETTINGS 在部分 SDK stub 中隐藏，直接用常量值
                                val intent = if (Build.VERSION.SDK_INT >= 33) {
                                    Intent("android.settings.APP_BATTERY_SETTINGS")
                                        .setData(
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                } else {
                                    Intent(
                                        android.provider.Settings
                                            .ACTION_APPLICATION_DETAILS_SETTINGS
                                    ).setData(
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                }
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }.onFailure {
                                // 回退：应用详情页
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            android.provider.Settings
                                                .ACTION_APPLICATION_DETAILS_SETTINGS
                                        )
                                            .setData(
                                                android.net.Uri.parse("package:${context.packageName}")
                                            )
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "USB 独占后台权限",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "设置省电策略、电池优化白名单，保证后台稳定独占输出",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==================== 卡片 3：音量（独立组件，状态局部化避免拖动时全页重组） ====================
        VolumeCard(
            usbStore = usbStore,
            context = context,
            notifySettingsChanged = ::notifySettingsChanged
        )

        if (showDeviceDialog) {
            AlertDialog(
                onDismissRequest = { showDeviceDialog = false },
                title = { Text("选择 USB 设备") },
                text = {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    usbStore.setDeviceKey("auto")
                                    notifySettingsChanged()
                                    showDeviceDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("自动", modifier = Modifier.weight(1f))
                            if (current.selectedDeviceKey == "auto") {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        usbDevices.forEach { device ->
                            val deviceKey = device.usbExclusiveDeviceKey()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        usbStore.setDeviceKey(deviceKey)
                                        notifySettingsChanged()
                                        showDeviceDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.deviceName ?: "USB 设备",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "VID ${device.vendorId} / PID ${device.productId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (current.selectedDeviceKey == deviceKey) {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        if (usbDevices.isEmpty()) {
                            Text(
                                text = "未检测到 USB 音频设备",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDeviceDialog = false }) { Text("取消") }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==================== 卡片 3：音质策略 ====================
        Text(
            text = "音质策略",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                UsbClickRow(
                    title = "采样率策略",
                    subtitle = sampleRateLabel(sampleRateMode)
                ) { showSampleRateDialog = true }

                UsbDivider()

                UsbClickRow(
                    title = "PCM 位深",
                    subtitle = bitDepthLabel(bitDepthMode)
                ) { showBitDepthDialog = true }

                UsbDivider()

                UsbSwitchRow(
                    title = "比特完美音量",
                    subtitle = "固定 0dB 软件增益，音量由 DAC 硬件控制",
                    checked = bitPerfect,
                    onCheckedChange = { value ->
                        bitPerfect = value
                        usbStore.setBitPerfect(value)
                        notifySettingsChanged()
                    }
                )

                UsbDivider()

                UsbSwitchRow(
                    title = "同步模式漂移修正",
                    subtitle = if (syncDriftCorrection) {
                        "按等时包错误微调发送速率（开启可能引入学习期爆音）"
                    } else {
                        "按标称采样率发送，不主动修正（推荐，可避免学习期爆音）"
                    },
                    checked = syncDriftCorrection,
                    onCheckedChange = { value ->
                        syncDriftCorrection = value
                        usbStore.setSyncDriftCorrection(value)
                        UsbExclusiveSessionController.applySyncDriftCorrection(value)
                        notifySettingsChanged()
                    }
                )

                UsbDivider()

                UsbClickRow(
                    title = "不支持格式处理",
                    subtitle = if (unsupportedPolicy == UsbExclusiveUnsupportedFormatPolicy.CLOSEST_SUPPORTED) {
                        "转换到最接近的格式"
                    } else {
                        "回退系统播放"
                    }
                ) { showPolicyDialog = true }

                UsbDivider()

                // 缓冲策略预设（对齐图3：低延迟/均衡/稳定 + 详情说明）
                UsbClickRow(
                    title = "缓冲策略",
                    subtitle = bufferProfileLabel(current.bufferProfile) +
                        " · " + bufferProfileDescription(current.bufferProfile)
                ) { showBufferProfileDialog = true }

                UsbDivider()

                UsbSwitchRow(
                    title = "采样率兼容",
                    subtitle = "设备不支持时自动匹配最接近的采样率",
                    checked = sampleRateCompat,
                    onCheckedChange = { value ->
                        sampleRateCompat = value
                        usbStore.setSampleRateCompatibility(value)
                        notifySettingsChanged()
                    }
                )

                UsbDivider()

                UsbSwitchRow(
                    title = "位深兼容",
                    subtitle = "设备不支持时自动匹配最接近的位深",
                    checked = bitDepthCompat,
                    onCheckedChange = { value ->
                        bitDepthCompat = value
                        usbStore.setBitDepthCompatibility(value)
                        notifySettingsChanged()
                    }
                )

                UsbDivider()

                UsbSwitchRow(
                    title = "声道兼容",
                    subtitle = "设备不支持时自动匹配声道数",
                    checked = channelCompat,
                    onCheckedChange = { value ->
                        channelCompat = value
                        usbStore.setChannelCompatibility(value)
                        notifySettingsChanged()
                    }
                )

                UsbDivider()

                UsbSliderRow(
                    title = "前台缓冲",
                    value = foregroundBuffer,
                    valueRange = 100f..1000f,
                    steps = 17,
                    valueText = { "${it.toInt()} ms" },
                    onValueChange = { foregroundBuffer = it },
                    onValueChangeFinished = {
                        usbStore.setForegroundBufferMs(foregroundBuffer.toInt())
                        UsbExclusiveSessionController.configureBufferDuration(
                            foregroundBuffer.toInt()
                        )
                        notifySettingsChanged()
                    }
                )
                Text(
                    text = "前台界面渲染占用 CPU，缓冲越大越稳定（默认 500ms，调整即时生效）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )

                UsbDivider()

                UsbSliderRow(
                    title = "后台缓冲",
                    value = backgroundBuffer,
                    valueRange = 200f..3000f,
                    steps = 55,
                    valueText = { "${it.toInt()} ms" },
                    onValueChange = { backgroundBuffer = it },
                    onValueChangeFinished = {
                        usbStore.setBackgroundBufferMs(backgroundBuffer.toInt())
                        UsbExclusiveSessionController.configureBufferDuration(
                            backgroundBuffer.toInt()
                        )
                        notifySettingsChanged()
                    }
                )
                Text(
                    text = "后台界面停止渲染，CPU 空闲，大缓冲可长期稳定播放（默认 1500ms）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )

                UsbDivider()

                UsbSliderRow(
                    title = "音量风险阈值",
                    value = volumeRisk,
                    valueRange = -24f..-1f,
                    steps = 22,
                    valueText = { "${it.toInt()} dBFS" },
                    onValueChange = { volumeRisk = it },
                    onValueChangeFinished = {
                        usbStore.setVolumeRiskThresholdDbfs(volumeRisk.toInt())
                        notifySettingsChanged()
                    }
                )
            }
        }

        if (showSampleRateDialog) {
            AlertDialog(
                onDismissRequest = { showSampleRateDialog = false },
                title = { Text("采样率策略") },
                text = {
                    Column {
                        UsbExclusiveSampleRateMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sampleRateMode = mode
                                        usbStore.setSampleRateMode(mode)
                                        notifySettingsChanged()
                                        showSampleRateDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sampleRateLabel(mode),
                                    modifier = Modifier.weight(1f)
                                )
                                if (sampleRateMode == mode) {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSampleRateDialog = false }) { Text("取消") }
                }
            )
        }

        if (showBitDepthDialog) {
            AlertDialog(
                onDismissRequest = { showBitDepthDialog = false },
                title = { Text("PCM 位深") },
                text = {
                    Column {
                        UsbExclusiveBitDepthMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        bitDepthMode = mode
                                        usbStore.setBitDepthMode(mode)
                                        notifySettingsChanged()
                                        showBitDepthDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = bitDepthLabel(mode),
                                    modifier = Modifier.weight(1f)
                                )
                                if (bitDepthMode == mode) {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBitDepthDialog = false }) { Text("取消") }
                }
            )
        }

        if (showPolicyDialog) {
            AlertDialog(
                onDismissRequest = { showPolicyDialog = false },
                title = { Text("不支持格式处理") },
                text = {
                    Column {
                        listOf(
                            UsbExclusiveUnsupportedFormatPolicy.CLOSEST_SUPPORTED to "转换到最接近的格式",
                            UsbExclusiveUnsupportedFormatPolicy.SYSTEM_FALLBACK to "回退系统播放"
                        ).forEach { (policy, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        unsupportedPolicy = policy
                                        usbStore.setUnsupportedFormatPolicy(policy)
                                        notifySettingsChanged()
                                        showPolicyDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, modifier = Modifier.weight(1f))
                                if (unsupportedPolicy == policy) {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPolicyDialog = false }) { Text("取消") }
                }
            )
        }

        if (showBufferProfileDialog) {
            AlertDialog(
                onDismissRequest = { showBufferProfileDialog = false },
                title = { Text("缓冲策略") },
                text = {
                    Column {
                        UsbExclusiveBufferProfile.entries.forEach { profile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        usbStore.setForegroundBufferMs(
                                            profile.bufferDurationMs.coerceIn(100, 1000)
                                        )
                                        usbStore.setBackgroundBufferMs(
                                            (profile.bufferDurationMs * 1.5).toInt().coerceIn(200, 3000)
                                        )
                                        showBufferProfileDialog = false
                                        notifySettingsChanged()
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bufferProfileLabel(profile),
                                        modifier = Modifier
                                    )
                                    Text(
                                        text = bufferProfileDescription(profile),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                    )
                                }
                                if (current.bufferProfile == profile) {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBufferProfileDialog = false }) { Text("取消") }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==================== 卡片 5：运行日志 ====================
        Text(
            text = "运行日志",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLogs = !showLogs }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "USB 独占日志（${UsbExclusiveLog.snapshot().size} 条）",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        val text = UsbExclusiveLog.formattedSnapshot()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("USB 日志", text))
                        Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
                    }) { Text("复制") }
                    TextButton(onClick = {
                        UsbExclusiveLog.clear()
                        logSnapshot = ""
                    }) { Text("清空") }
                    Icon(
                        imageVector = if (showLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(
                    visible = showLogs,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Text(
                        text = logSnapshot.ifBlank { "（暂无日志）" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==================== 兼容性说明 ====================
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "开启后，播放器将绕过系统音频处理链（均衡器、响度、变速变调等）直接驱动 USB 设备。\n" +
                    "系统音量仍可控制输出增益；开启「比特完美音量」后音量改由 DAC 硬件控制。\n" +
                    "拔插 USB 设备会自动恢复或停止播放。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun UsbDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun UsbStatusRow(title: String, value: String, isError: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun UsbClickRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UsbSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun UsbSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: (Float) -> String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChangeFinished = onValueChangeFinished,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun sampleRateLabel(mode: UsbExclusiveSampleRateMode): String {
    return when (mode) {
        UsbExclusiveSampleRateMode.FOLLOW_SOURCE -> "跟随歌曲"
        else -> mode.sampleRateHz?.formatSampleRate() ?: "${mode.sampleRateHz} Hz"
    }
}

/**
 * 自定义大圆形指示器滑条（替代原生 Material Slider）：
 * 粗圆角轨道 + 渐变填充 + 大圆形指示器（带描边与阴影），
 * 支持点击跳转与水平拖动，适合音量等精细调节场景。
 */
@Composable
private fun VolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)
    val trackColor = if (enabled) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val fillColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val thumbColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val thumbRingColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Canvas(
        modifier = modifier
            .height(48.dp)
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                // 方向锁定手势：按下后先积累位移，水平分量先超过 touchSlop 才接管滑条；
                // 垂直分量先超过则放行给外层滚动容器（上下滑页面经过滑条不再误触音量）。
                // 锁定后消费全部事件（页面不再因水平拖动而滚动），松手提交。
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var horizontalLocked = false
                    var dragged = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.changedToUp()) {
                            if (!dragged) {
                                // 纯点击：跳转到按下位置
                                val f = (down.position.x / size.width).coerceIn(0f, 1f)
                                onValueChange(
                                    valueRange.start +
                                        (valueRange.endInclusive - valueRange.start) * f
                                )
                            }
                            onValueChangeFinished()
                            change.consume()
                            break
                        }
                        if (change.pressed) {
                            if (!horizontalLocked) {
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                val slop = viewConfiguration.touchSlop
                                if (abs(dx) > slop || abs(dy) > slop) {
                                    if (abs(dx) > abs(dy)) {
                                        horizontalLocked = true
                                    } else {
                                        // 垂直滑动：放行给滚动容器，结束本手势
                                        break
                                    }
                                }
                            }
                            if (horizontalLocked) {
                                change.consume()
                                dragged = true
                                val f = (change.position.x / size.width).coerceIn(0f, 1f)
                                onValueChange(
                                    valueRange.start +
                                        (valueRange.endInclusive - valueRange.start) * f
                                )
                            }
                        } else {
                            change.consume()
                            break
                        }
                    }
                }
            }
    ) {
        val trackHeight = 8.dp.toPx()
        val trackTop = (size.height - trackHeight) / 2f
        val thumbRadius = 22.dp.toPx()
        val trackStart = thumbRadius
        val trackWidth = size.width - thumbRadius * 2f
        val thumbX = trackStart + fraction * trackWidth

        // 轨道背景
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(trackStart, trackTop),
            size = Size(trackWidth, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )
        // 已填充部分
        if (fraction > 0f) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(trackStart, trackTop),
                size = Size((thumbX - trackStart).coerceAtLeast(0f), trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f)
            )
        }
        // 大圆形指示器：外圈描边 + 内圈主色 + 中心白点
        drawCircle(
            color = thumbRingColor,
            radius = thumbRadius,
            center = Offset(thumbX, size.height / 2f)
        )
        drawCircle(
            color = thumbColor,
            radius = thumbRadius * 0.72f,
            center = Offset(thumbX, size.height / 2f)
        )
        drawCircle(
            color = Color.White,
            radius = thumbRadius * 0.28f,
            center = Offset(thumbX, size.height / 2f)
        )
    }
}

/**
 * 音量卡片（独立组件）：
 * - 状态局部化：volumeState 只在此组件内收集，拖动音量时仅本卡片重组，
 *   避免整个设置页随每次音量变化高频重组（修复"拖动时页面上下波动"）。
 * - 拖动期间不回写 volumeLevel：LaunchedEffect 只在非拖动时跟随外部
 *   音量变化（初始化/恢复/音量路由），消除滑条与拖动值互相打架的抖动。
 */
@Composable
private fun VolumeCard(
    usbStore: com.example.lxmusic.usb.UsbExclusiveSettingsStore,
    context: Context,
    notifySettingsChanged: () -> Unit
) {
    val volumeState by UsbExclusiveSessionController.volumeState.collectAsState()
    var systemVolumeRouting by remember {
        mutableStateOf(
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("usb_exclusive_system_volume_routing", false)
        )
    }
    // 专属独占音量（0-300 级 × 0.2dB = -60.0~0.0 dB）：仅设置页音量条可调，
    // 独立于系统音量（音量键/媒体条不影响）；拖动过程实时驱动 native 增益
    var volumeLevel by remember {
        mutableStateOf((usbStore.getPlayerVolume() * 300f).coerceIn(0f, 300f))
    }
    var isDraggingVolume by remember { mutableStateOf(false) }
    val currentDb = -60.0 + volumeLevel / 300.0 * 60.0
    val currentPercent = (volumeLevel / 300f * 100f).roundToInt()
    // 实时应用：拖动过程中即时驱动 native 增益（native 侧 80ms 平滑斜坡）
    val applyUsbVolume: (Float) -> Unit = { level ->
        val clamped = level.coerceIn(0f, 300f)
        isDraggingVolume = true
        UsbExclusiveSessionController.setPlayerVolume(clamped / 300f)
        volumeLevel = clamped
    }
    // 松手后持久化当前独占音量（存 0-1 比例 = level/300）
    val persistUsbVolume: () -> Unit = {
        isDraggingVolume = false
        usbStore.setPlayerVolume(volumeLevel / 300f)
    }
    // 独占音量变化（初始化/恢复/音量路由）→ 滑条跟随；拖动中不跟随，
    // 避免用户拖动值与 native 发布值互相回写造成抖动
    LaunchedEffect(volumeState.playerVolume) {
        if (!isDraggingVolume) {
            volumeLevel = (volumeState.playerVolume * 300f).coerceIn(0f, 300f)
        }
    }

    Text(
        text = "音量",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 读回：native 生效增益（含斜坡收敛后的实际增益）换算 dB
            val readbackDb: Double = if (volumeState.bitPerfect) {
                0.0
            } else {
                val gain = volumeState.effectiveVolume
                if (gain <= 0f) USB_EXCLUSIVE_VOLUME_DB_MIN
                else 20.0 * kotlin.math.log10(gain.toDouble())
            }
            // 专属独占音量（0-300 级 × 0.2 dB = -60.0 ~ 0.0 dB）+ 实时显示
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "USB 独占音量",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$currentPercent%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${"%.1f".format(currentDb)} dB",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "300 级 × 0.2 dB（-60.0 ~ 0.0）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                VolumeSlider(
                    value = volumeLevel,
                    onValueChange = { applyUsbVolume(it) },
                    onValueChangeFinished = { persistUsbVolume() },
                    valueRange = 0f..300f,
                    enabled = !volumeState.bitPerfect,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (volumeState.bitPerfect) {
                        "读回 0.0 dB（比特完美：软件增益固定 0 dB，音量由 DAC 硬件控制）"
                    } else {
                        "读回 ${"%.1f".format(readbackDb)} dB"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "专属 USB 独占音量：仅此处可调（拖动实时生效），系统音量键/通知栏媒体条不影响独占输出",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (volumeState.bitPerfect) {
                UsbDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "比特完美已开启：软件增益固定 0 dB，音量由 DAC 硬件控制，滑杆不可用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            UsbDivider()

            // 系统音量路由（试验）：框架 MediaSession + VolumeProvider 接管系统音量，
            // 让音量键在后台也能调独占音量。默认关闭：独占音量仅由本页专属音量条控制，
            // 系统音量键不影响独占输出（与椒盐式行为一致）。
            UsbSwitchRow(
                title = "音量键路由（试验）",
                subtitle = "开启后音量键/锁屏音量条直接驱动独占音量（部分 ROM 可能卡顿，建议保持关闭）",
                checked = systemVolumeRouting,
                onCheckedChange = { value ->
                    systemVolumeRouting = value
                    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .edit().putBoolean("usb_exclusive_system_volume_routing", value).apply()
                    notifySettingsChanged()
                }
            )

            UsbDivider()

            // 系统音量：实时跟随（音量键/锁屏条变化时刷新）
            UsbStatusRow(
                "系统音量",
                "${(volumeState.systemVolumeFraction * 100).toInt()}%"
            )

            UsbDivider()

            // 生效音量（读回 native 实际增益，比特完美时恒 0 dB）
            UsbStatusRow(
                "生效音量",
                if (volumeState.bitPerfect) {
                    "0.0 dB（硬件控制）"
                } else {
                    val gain = volumeState.effectiveVolume
                    if (gain <= 0f) {
                        "%.1f dB".format(USB_EXCLUSIVE_VOLUME_DB_MIN)
                    } else {
                        "%.1f dB".format(20.0 * kotlin.math.log10(gain.toDouble()))
                    }
                }
            )
        }
    }
}

private fun bitDepthLabel(mode: UsbExclusiveBitDepthMode): String {
    return when (mode) {
        UsbExclusiveBitDepthMode.AUTO -> "自动"
        else -> "${mode.bitDepth}-bit"
    }
}

private fun Int.formatSampleRate(): String {
    return if (this % 1_000 == 0) {
        "${this / 1_000} kHz"
    } else {
        "${this / 1_000}.${(this % 1_000) / 100} kHz"
    }
}

private fun bufferProfileLabel(profile: com.example.lxmusic.usb.UsbExclusiveBufferProfile): String {
    return when (profile) {
        com.example.lxmusic.usb.UsbExclusiveBufferProfile.LOW_LATENCY -> "低延迟"
        com.example.lxmusic.usb.UsbExclusiveBufferProfile.BALANCED -> "均衡"
        com.example.lxmusic.usb.UsbExclusiveBufferProfile.STABLE -> "稳定"
    }
}

private fun bufferProfileDescription(profile: com.example.lxmusic.usb.UsbExclusiveBufferProfile): String {
    return when (profile) {
        com.example.lxmusic.usb.UsbExclusiveBufferProfile.LOW_LATENCY -> "前台 250ms / 后台 1500ms，延迟最低但抖动风险高"
        com.example.lxmusic.usb.UsbExclusiveBufferProfile.BALANCED -> "前台 500ms / 后台 1500ms，平衡延迟与稳定性"
        com.example.lxmusic.usb.UsbExclusiveBufferProfile.STABLE -> "前台 850ms / 后台 2000ms，最稳定但延迟较高"
    }
}

private fun unsupportedPolicyLabel(policy: com.example.lxmusic.usb.UsbExclusiveUnsupportedFormatPolicy): String {
    return when (policy) {
        com.example.lxmusic.usb.UsbExclusiveUnsupportedFormatPolicy.CLOSEST_SUPPORTED -> "转换到最接近的格式"
        com.example.lxmusic.usb.UsbExclusiveUnsupportedFormatPolicy.SYSTEM_FALLBACK -> "回退系统播放"
    }
}

private fun Int.audioEncodingLabel(): String {
    return when (this) {
        android.media.AudioFormat.ENCODING_PCM_8BIT -> "PCM 8-bit"
        android.media.AudioFormat.ENCODING_PCM_16BIT -> "PCM 16-bit"
        android.media.AudioFormat.ENCODING_PCM_FLOAT -> "PCM Float"
        android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM 24-bit"
        android.media.AudioFormat.ENCODING_PCM_32BIT -> "PCM 32-bit"
        else -> "encoding=$this"
    }
}

/** 音量显示格式化：1.55 → "1.55"，整数 → "5" */
private fun Float.formatVolume(): String {
    val rounded = (this * 100).roundToInt() / 100f
    return if (rounded == rounded.toInt().toFloat()) {
        rounded.toInt().toString()
    } else {
        "%.2f".format(rounded)
    }
}

/** native runtimeReport `key=value` 字符串取值（与 UsbExclusiveTransportReport.valueAfter 同逻辑） */
private fun String.metricAfter(key: String): String? =
    split(" ").firstOrNull { it.startsWith("$key=") }?.substringAfter("=", "")

/** 传输状态稳定性枚举 */
private enum class TransportStability {
    Offline, Stable, Learning, Minor, Unstable
}

/**
 * 实时传输状态卡片（参考椒盐音乐风格）：
 * 深色卡片 + 缓冲区水位大字 + ISO 错误计数 + 进度条 + 格式/漂移信息。
 * 数据来源：UsbExclusiveSessionController.state 每秒自动轮询。
 */
@Composable
private fun TransmissionStatusCard(
    sessionState: com.example.lxmusic.usb.transport.UsbExclusiveNativeState
) {
    val runtimeReport = sessionState.runtimeReport
    val isStreaming = sessionState.opened && sessionState.streaming

    // 解析关键指标
    val levelBytes = sessionState.pcmLevelBytes
    val capacityBytes = sessionState.pcmCapacityBytes.coerceAtLeast(1L)
    val outputRate = sessionState.outputSampleRate.coerceAtLeast(1)
    val frameBytes = run {
        val fmt = sessionState.outputFormat
        val subslot = fmt.metricAfter("subslot")?.toIntOrNull() ?: 3
        2 * subslot // stereo × subslot
    }
    val waterLevelMs = if (isStreaming) levelBytes * 1000L / (outputRate.toLong() * frameBytes) else 0L

    val isoErrors = runtimeReport.metricAfter("isoPacketErrors")?.toLongOrNull() ?: 0L
    val droppedBytes = runtimeReport.metricAfter("playerDroppedBytes")?.toLongOrNull() ?: 0L
    val driftCorrection = runtimeReport.metricAfter("driftCorrection")?.replace("u", "")?.toLongOrNull() ?: 0L
    val driftPpm = driftCorrection * 0.1814

    val bufferMs = sessionState.bufferDurationMs
    val targetMs = bufferMs.toLong()
    val levelFraction = (waterLevelMs.toFloat() / targetMs.coerceAtLeast(1L)).coerceIn(0f, 1f)

    // 稳定性判定（按严重度分级，指标化）：
    // 1) 无会话 → 未连接；2) 传输失败/终端失败/错误码 → 异常；
    // 3) 等时错误爆发或累计丢帧过大 → 异常；4) 存在轻微错误 → 轻微；
    // 5) 漂移修正非零（仅开启时） → 校准中；6) 否则 → 稳定
    val errorCode = sessionState.errorCode
    val stability = when {
        !sessionState.opened -> TransportStability.Offline
        sessionState.transportFailed || sessionState.terminalFailure == true ->
            TransportStability.Unstable
        errorCode != null && errorCode != "None" -> TransportStability.Unstable
        isoErrors > 500L || droppedBytes > 10_000L -> TransportStability.Unstable
        isoErrors > 0L -> TransportStability.Minor
        driftCorrection != 0L -> TransportStability.Learning
        else -> TransportStability.Stable
    }

    val (stabilityText, stabilityColor) = when (stability) {
        TransportStability.Offline -> "未连接" to Color(0xFF6B7280)
        TransportStability.Stable -> "稳定" to Color(0xFF22C55E)
        TransportStability.Learning -> "时钟校准中" to Color(0xFF3B82F6)
        TransportStability.Minor -> "轻微错误" to Color(0xFFF59E0B)
        TransportStability.Unstable -> "异常" to Color(0xFFEF4444)
    }

    val barColor = when (stability) {
        TransportStability.Offline -> Color(0xFF4B5563)
        TransportStability.Stable -> Color(0xFF22C55E)
        TransportStability.Learning -> Color(0xFF3B82F6)
        TransportStability.Minor -> Color(0xFFF59E0B)
        TransportStability.Unstable -> Color(0xFFEF4444)
    }

    // 异常/轻微的专业指标报告
    val droppedMs = if (droppedBytes > 0L && frameBytes > 0 && outputRate > 0) {
        droppedBytes * 1000L / (outputRate.toLong() * frameBytes)
    } else {
        0L
    }
    val reportText = when (stability) {
        TransportStability.Unstable -> {
            val cause = when {
                sessionState.transportFailed || sessionState.terminalFailure == true ->
                    "传输失败：${errorCode ?: "transport_failed"}"
                isoErrors > 500L || droppedBytes > 10_000L ->
                    "等时包错误 $isoErrors 次，累计丢帧约 ${droppedMs}ms"
                else -> "传输层错误：${errorCode ?: "unknown"}"
            }
            "$cause。建议重新插拔 USB 设备、更换线材或端口后重试"
        }
        TransportStability.Minor -> "等时包错误 $isoErrors 次，累计丢帧约 ${droppedMs}ms（通常无听感影响）"
        TransportStability.Learning -> "正在匹配 DAC 时钟速率，约 5-10 秒后稳定"
        else -> null
    }

    // 深色卡片（与椒盐音乐风格一致）
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1C1C1E),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // 顶部：传输状态标题 + 稳定性指示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "传输状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                if (isStreaming) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(stabilityColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stabilityText,
                            style = MaterialTheme.typography.bodySmall,
                            color = stabilityColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "空闲",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            // 异常/轻微/校准指标报告（常驻展示，非测试连接后）
            if (reportText != null && stability != TransportStability.Offline) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = reportText,
                    style = MaterialTheme.typography.bodySmall,
                    color = stabilityColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 水位数字 + ISO 错误
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${waterLevelMs}ms",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "缓冲区水位",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9CA3AF),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Text(
                    text = "ISO $isoErrors",
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        isoErrors > 500L -> Color(0xFFEF4444)
                        isoErrors > 0L -> Color(0xFFF59E0B)
                        else -> Color(0xFF22C55E)
                    },
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 缓冲区水位进度条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF374151))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(levelFraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 目标/最低
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "目标 ${targetMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF)
                )
                Text(
                    text = "最低 ${(targetMs * 0.2).toLong().coerceAtLeast(0)}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF)
                )
            }

            // 格式 + 漂移（仅流式时显示）
            if (isStreaming) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF374151))
                Spacer(modifier = Modifier.height(12.dp))

                val formatText = run {
                    val fmt = sessionState.outputFormat
                    val rate = fmt.metricAfter("rate") ?: outputRate.toString()
                    val bits = fmt.metricAfter("bits") ?: "?"
                    "PCM ${formatRateHz(rate)} / ${bits}-bit"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD1D5DB)
                    )
                    if (driftCorrection != 0L) {
                        Text(
                            text = "漂移修正 ${String.format("%.0f", driftPpm)}ppm",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/** 格式化采样率：44100 → "44.1 kHz", 48000 → "48.0 kHz" */
private fun formatRateHz(rate: String): String {
    val r = rate.toDoubleOrNull() ?: return rate
    return "${String.format("%.1f", r / 1000.0)} kHz"
}

/**
 * 设备连接信息卡片（参考图一风格）：
 * 深色卡片 + USB EXCLUSIVE 标题 + 设备信息 + 连接状态
 */
@Composable
private fun DeviceConnectionCard(
    enabled: Boolean,
    sessionState: com.example.lxmusic.usb.transport.UsbExclusiveNativeState,
    selectedDevice: UsbDevice?,
    hasPermission: Boolean
) {
    val isConnected = sessionState.opened
    val statusText = when {
        !enabled -> "已关闭"
        isConnected -> "已连接"
        selectedDevice != null && !hasPermission -> "未授权"
        selectedDevice != null -> "等待连接"
        else -> "未检测到设备"
    }
    val statusColor = when {
        !enabled -> Color(0xFF6B7280)
        isConnected -> Color(0xFF22C55E)
        selectedDevice != null && !hasPermission -> Color(0xFFF59E0B)
        else -> Color(0xFF6B7280)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1C1C1E),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // 顶部：USB EXCLUSIVE 标题 + 状态指示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "USB EXCLUSIVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF9CA3AF),
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 设备型号（只显示一次型号，精简）
            val deviceName = selectedDevice?.productName?.toString() ?: "未知设备"
            Text(
                text = deviceName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // OUTPUT LINK 状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) Color(0xFF22C55E) else Color(0xFFF59E0B))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "OUTPUT LINK",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isConnected) "已接管" else "未接管",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) Color(0xFF22C55E) else Color(0xFFF59E0B)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 格式/位深/设备 ID 三列信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // FORMAT 列
                Column {
                    Text(
                        text = "FORMAT",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6B7280),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (isConnected && sessionState.outputFormat.isNotEmpty()) {
                            sessionState.outputFormat.metricAfter("rate")?.let {
                                "PCM ${formatRateHz(it)}"
                            } ?: "播放后显示"
                        } else {
                            "播放后显示"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD1D5DB)
                    )
                }

                // DEPTH 列
                Column {
                    Text(
                        text = "DEPTH",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6B7280),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (isConnected && sessionState.outputFormat.isNotEmpty()) {
                            sessionState.outputFormat.metricAfter("bits")?.let {
                                "${it}-bit"
                            } ?: "待协商"
                        } else {
                            "待协商"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD1D5DB)
                    )
                }

                // USB ID 列
                Column {
                    Text(
                        text = "USB ID",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6B7280),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (selectedDevice != null) {
                            "${selectedDevice.vendorId.toString(16).uppercase()}:${selectedDevice.productId.toString(16).uppercase()}"
                        } else {
                            "--"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD1D5DB)
                    )
                }
            }
        }
    }
}
