@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.lxmusic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.database.ContentObserver
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.DefaultMediaNotificationProvider
import com.example.lxmusic.util.AudioReactive
import com.example.lxmusic.util.PlaybackVolumeBalanceState
import com.example.lxmusic.util.PlaybackVolumeNormalizationState
import com.example.lxmusic.util.StereoBalanceAudioProcessor
import com.example.lxmusic.util.UsbAudioManager
import com.example.lxmusic.util.UsbRoutingAudioSink
import com.example.lxmusic.util.VolumeNormalizationAudioProcessor
import com.example.lxmusic.usb.UsbExclusiveLog
import com.example.lxmusic.usb.UsbExclusivePermissionManager
import com.example.lxmusic.usb.sink.UsbExclusiveAudioSink
import com.example.lxmusic.usb.session.UsbExclusiveSessionController
import com.example.lxmusic.usb.system.UsbExclusiveBackgroundAudioAnchor
import com.example.lxmusic.usb.system.UsbExclusiveVolumeRoutingSession
import com.example.lxmusic.usb.system.shouldRunUsbExclusiveBackgroundAudioAnchor
import com.example.lxmusic.usb.device.listUsbAudioDevices
import com.example.lxmusic.usb.device.hasUsbExclusivePermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var usbRoutingSink: UsbRoutingAudioSink? = null
    private var usbExclusiveSink: UsbExclusiveAudioSink? = null
    private var idleHandler: Handler? = null
    private var idleShutdownRunnable: Runnable? = null
    private var settingsChangeReceiver: BroadcastReceiver? = null
    private var noisyReceiver: BroadcastReceiver? = null
    private var usbAttachReceiver: BroadcastReceiver? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var volumeFadeJob: Job? = null
    private var noisyConfirmAttempts = 0
    private var noisyPausePending = false
    private var lastUsbExclusiveSetting = false
    private var volumeObserver: ContentObserver? = null
    private var volumeChangeReceiver: BroadcastReceiver? = null
    private var lastUsbRouteRebuildAtMs = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var routingRefreshJob: Job? = null

    companion object {
        const val CHANNEL_ID = "lxmusic_playback_channel"
        const val CHANNEL_NAME = "音乐播放"
        const val CHANNEL_DESCRIPTION = "音乐播放控制通知"
        const val NOTIFICATION_ID = 1001

        /** 当前解码音频格式（采样率/位深/编码），UI 经 ViewModel 轮询读取 */
        @Volatile
        var lastAudioFormat: androidx.media3.common.Format? = null

        @Volatile
        private var appInForeground = true

        @Volatile
        private var anchorServiceInstance: PlayerService? = null

        /** MainActivity 前台状态变化时通知（驱动后台音频锚点） */
        fun setAppInForeground(foreground: Boolean) {
            appInForeground = foreground
            anchorServiceInstance?.updateBackgroundAnchor()
        }
    }

    private fun usbExclusivePlaybackActive(): Boolean {
        return getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("usb_exclusive_playback", false) &&
            UsbExclusiveSessionController.hasOpenSession() &&
            UsbExclusiveSessionController.isNativeTransportStarted()
    }

    /** 独占是否激活（开关开启且 native 会话已打开） */
    private fun usbExclusiveActive(): Boolean {
        return getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("usb_exclusive_playback", false) &&
            UsbExclusiveSessionController.hasOpenSession()
    }

    /** 内置扬声器设备（独占激活时系统路由的落脚点） */
    private fun builtInSpeakerOutput(): AudioDeviceInfo? {
        return runCatching {
            (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                ?.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        }.getOrNull()
    }

    /**
     * 独占激活时把系统路由钉到内置扬声器：native libusb 直驱 USB DAC 期间，
     * 系统侧 DefaultAudioSink 再被 setPreferredDevice 到 USB DAC 会与 libusb
     * 双主体并发写同一等时端点 → 等时包错误 → 前台沙沙声（后台正常是因为
     * 后台锚点把系统流钉到了内置输出）。独占未激活/回退时延迟恢复 USB 路由
     * （系统 HAL 重新接管 USB DAC 是异步的，立即切回会导致 AudioTrack
     * 配置到未就绪的 DAC → 扬声器异响）。
     */
    private fun refreshUsbRouteForExclusiveState() {
        val exclusive = usbExclusiveActive()
        if (exclusive) {
            usbRoutingSink?.setForceBuiltInOutput(true, builtInSpeakerOutput())
        } else {
            usbRoutingSink?.setForceBuiltInOutput(false)
            scheduleUsbRouteRestore()
        }
    }

    private var usbRouteRestorePending = false

    /** 独占关闭后延迟恢复 USB 路由（去重，约 2s 一次） */
    private fun scheduleUsbRouteRestore() {
        if (usbRouteRestorePending) return
        usbRouteRestorePending = true
        idleHandler?.postDelayed({
            usbRouteRestorePending = false
            runCatching { usbRoutingSink?.refreshUsbDevice() }
        }, 2_000L)
    }

    /**
     * USB 音频锚点：独占会话激活期间（不分前后台、不分播放/暂停）通过系统
     * AudioTrack 播放载波，把系统混音输出钉到内置扬声器，让系统侧离开 USB DAC
     * ——消除与 libusb 双写同一等时端点造成的 iso 包错误（前台沙沙声）。
     * 暂停时传输仍在跑（软静音发静音包），若锚点随 isPlaying 释放，系统侧会
     * 重新接管 DAC 与 libusb 双写 → 暂停期间 iso 错误持续累积（日志：暂停时
     * isoPacketErrors 21→57）。条件放宽为"会话已打开"即可，独占关闭才停止。
     */
    private fun updateBackgroundAnchor() {
        val shouldRun = shouldRunUsbExclusiveBackgroundAudioAnchor(
            appInForeground = appInForeground,
            serviceForeground = mediaSession?.player?.isPlaying == true,
            usbExclusivePlaybackActive = usbExclusiveActive()
        )
        if (shouldRun) {
            UsbExclusiveBackgroundAudioAnchor.start(this, "usb_exclusive_active")
        } else {
            UsbExclusiveBackgroundAudioAnchor.stop("usb_exclusive_inactive_or_idle")
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        anchorServiceInstance = this
        idleHandler = Handler(Looper.getMainLooper())

        // 创建通知渠道（Android 8.0+ 必需）
        createNotificationChannel()

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        lastUsbExclusiveSetting = prefs.getBoolean("usb_exclusive_playback", false)

        // 监听设置变更：USB 独占开关切换时刷新音频路由，空闲退出分钟数变更时重新调度
        val settingsFilter = IntentFilter("com.example.lxmusic.SETTINGS_CHANGED")
        settingsChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshUsbRouteForExclusiveState()
                // 延迟二次刷新：native 独占会话关闭后内核驱动重新挂载是异步的，
                // USB 设备需要几百毫秒才会回到 AudioManager 列表
                idleHandler?.postDelayed({
                    runCatching { refreshUsbRouteForExclusiveState() }
                }, 400L)
                // USB 独占开关变化：重建播放管线（stop → prepare 触发 sink 重新 configure）
                val usbEnabled = prefs.getBoolean("usb_exclusive_playback", false)
                if (usbEnabled != lastUsbExclusiveSetting) {
                    lastUsbExclusiveSetting = usbEnabled
                    rebuildPlaybackRoute()
                }
                updateBackgroundAnchor()
                updateUsbExclusiveVolumeRouting()
                // 同步 USB 插入自动响应 alias 状态
                applyComponentStateForAttachHandling(prefs)
            }
        }
        runCatching {
            registerReceiver(settingsChangeReceiver, settingsFilter)
        }

        // USB 设备拔插监听：拔出熔断 + 插入解除熔断
        val usbFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        usbAttachReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val device = if (Build.VERSION.SDK_INT >= 33) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, android.hardware.usb.UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        UsbExclusiveSessionController.handleUsbDeviceDetached(device)
                        updateBackgroundAnchor()
                        updateUsbExclusiveVolumeRouting()
                        // 拔出后刷新路由（设备从 AudioManager 消失 → 回到默认输出）
                        usbRoutingSink?.refreshUsbDevice()
                        // 拔出后若正在独占播放：停止（保留意图，等待重插恢复由用户触发）
                        if (lastUsbExclusiveSetting) {
                            mediaSession?.player?.pause()
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        UsbExclusiveSessionController.handleUsbDeviceAttached(this@PlayerService)
                        updateBackgroundAnchor()
                        updateUsbExclusiveVolumeRouting()
                        // 插入后延迟刷新路由：等系统 USB 音频 HAL 完成探测、
                        // 设备进入 AudioManager 列表后再 setPreferredDevice，
                        // 否则系统路径会继续走扬声器
                        idleHandler?.postDelayed({
                            runCatching { usbRoutingSink?.refreshUsbDevice() }
                        }, 400L)
                        if (lastUsbExclusiveSetting) {
                            // 自动请求权限（对齐 NeriPlayer：插入即授权，3s 冷却限流）
                            UsbExclusivePermissionManager.register(this@PlayerService)
                            val devices = listUsbAudioDevices(this@PlayerService)
                            val unpermitted = devices.firstOrNull {
                                !it.hasUsbExclusivePermission(this@PlayerService)
                            }
                            if (unpermitted != null) {
                                UsbExclusivePermissionManager.requestPermission(
                                    this@PlayerService,
                                    unpermitted
                                )
                            }
                            // 插入/授权后不重建播放管线：stop→seekTo→prepare 会打断当前状态，
                            // 即使播放器处于暂停也会触发渲染器预滚喂数据 → native 传输启动出声
                            // （"暂停中却自动播放、显示暂停但声音在播"）。独占会话由用户
                            // 主动播放/切歌时 sink.configure 自然打开，无需此处干预。
                        }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                registerReceiver(usbAttachReceiver, usbFilter, Context.RECEIVER_NOT_EXPORTED)
            }
        } else {
            runCatching {
                registerReceiver(usbAttachReceiver, usbFilter)
            }
        }

        // 蓝牙/耳机断开自动停止（对齐 Neri：采样确认防误判）
        if (prefs.getBoolean("playback_bluetooth_stop", true)) {
            val noisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            noisyReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    handleBecomingNoisy()
                }
            }
            if (Build.VERSION.SDK_INT >= 33) {
                runCatching {
                    registerReceiver(noisyReceiver, noisyFilter, Context.RECEIVER_NOT_EXPORTED)
                }
            } else {
                runCatching {
                    registerReceiver(noisyReceiver, noisyFilter)
                }
            }
        }

        // 息屏且未在播放时主动关闭 USB 独占会话：
        // 手机深睡后 USB 设备会被内核挂起/复位，libusb 会话跨休眠会挂死
        // （之后点歌永远加载、开关无效，只有拔线才能恢复），息屏关闭可避免跨休眠持有。
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_SCREEN_OFF) return
                if (UsbExclusiveSessionController.isNativeTransportStarted()) {
                    // 正在独占播放：wakelock 持有中，不会深睡，保持会话
                    return
                }
                if (UsbExclusiveSessionController.hasOpenSession()) {
                    UsbExclusiveLog.i(
                        "LxUsbSession",
                        "screen off, closing idle exclusive session"
                    )
                    UsbExclusiveSessionController.forceStopAllSessions()
                }
            }
        }
        val screenOffFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(screenOffReceiver, screenOffFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(screenOffReceiver, screenOffFilter)
            }
        }

        // 同步响度均衡 / 声道平衡状态（AudioProcessor 全局状态，实时生效）
        PlaybackVolumeNormalizationState.updateEnabled(
            prefs.getBoolean("playback_volume_normalization", false)
        )
        PlaybackVolumeBalanceState.update(prefs.getFloat("playback_volume_balance", 0f))

        // 系统音量观察（USB 独占时系统音量² 驱动 native 增益）
        setupVolumeObserver()

        // 恢复上次保存的独占音量（设置页滑杆保存；默认 -40dB）
        UsbExclusiveSessionController.setPlayerVolume(
            prefs.getFloat("usb_exclusive_player_volume", 1f / 3f).coerceIn(0f, 1f)
        )

        // 同步 USB 插入自动响应组件状态
        applyComponentStateForAttachHandling(prefs)

        // 音频焦点（抢占模式启用时主动请求）
        setupAudioFocus(prefs.getBoolean("playback_preempt_focus", false))

        // 高解析度输出（重启生效）
        val highResolution = prefs.getBoolean("playback_high_res", false)

        // 设置通知提供者，使用应用图标
        val notificationProvider = DefaultMediaNotificationProvider(this).apply {
            setSmallIcon(R.mipmap.ic_launcher_foreground)
        }
        setMediaNotificationProvider(notificationProvider)

        val dataSourceFactory = SongDataSource.Factory()
        val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)

        // 支持 USB DAC 路由：覆写 buildAudioSink，把输出直通到 USB 音频设备
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                // 处理链：响度均衡 → 声道平衡 → 可视化 Tee（分流 PCM 到 AudioReactive 驱动背景律动）
                val volumeNormalization = VolumeNormalizationAudioProcessor()
                val balance = StereoBalanceAudioProcessor()
                val tee = TeeAudioProcessor(AudioReactive.teeSink)
                val delegate = DefaultAudioSink.Builder(context)
                    .setAudioProcessors(
                        arrayOf<AudioProcessor>(volumeNormalization, balance, tee)
                    )
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
                // 系统路由：只要检测到 USB 音频设备就路由到它（与独占开关无关）。
                // 独占开关只决定是否走 native 直驱；即使独占未启用/回退，
                // 声音也应从 USB DAC 耳机出来，而不是手机扬声器。
                val routingSink = UsbRoutingAudioSink(delegate) {
                    UsbAudioManager.currentUsbAudioDevice(this@PlayerService)
                }
                routingSink.refreshUsbDevice()
                usbRoutingSink = routingSink
                // USB 独占装饰器：开启时绕过系统 sink 直驱 USB 设备
                val exclusiveSink = UsbExclusiveAudioSink(
                    context = this@PlayerService,
                    fallbackSink = routingSink
                )
                usbExclusiveSink = exclusiveSink
                return exclusiveSink
            }
        }

        // 高解析度输出：优先 float 输出（32-bit，重启生效）
        if (highResolution) {
            runCatching { renderersFactory.setEnableAudioFloatOutput(true) }
        }

        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        // 添加播放器监听器，确保播放时启动前台服务 + 暂停时调度空闲退出
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                lastAudioFormat = null
            }

            override fun onEvents(p: Player, events: Player.Events) {
                // 更新当前解码音频格式（采样率/位深/编码/码率），供播放器页显示
                var sourceFormat: androidx.media3.common.Format? = null
                for (group in p.currentTracks.groups) {
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && group.isSelected) {
                        sourceFormat = group.getTrackFormat(0)
                        break
                    }
                }
                if (sourceFormat != null) {
                    lastAudioFormat = sourceFormat
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startForegroundIfNeeded()
                    cancelIdleShutdown()
                    // 播放时刷新系统路由：USB 音频设备可能晚注册（内核驱动
                    // 重新挂载/系统 HAL 探测），确保系统路径从小尾巴耳机出声
                    usbRoutingSink?.refreshUsbDevice()
                    // 独占激活时系统路由钉内置扬声器（避免与 libusb 双写 USB DAC 等时端点）
                    refreshUsbRouteForExclusiveState()
                    // 独占播放期间持有音频焦点：其他 app 接管时暂停独占，避免双写
                    if (usbExclusiveActive()) {
                        acquireAudioFocus()
                    }
                    // 播放期间每 3s 轮询刷新路由：覆盖设备晚注册/重新注册场景，
                    // 避免独占回退后系统路径落回扬声器
                    routingRefreshJob?.cancel()
                    routingRefreshJob = serviceScope.launch {
                        while (true) {
                            delay(3000)
                            if (mediaSession?.player?.isPlaying != true) break
                            runCatching { refreshUsbRouteForExclusiveState() }
                            // 兜底同步锚点：自动恢复播放时 onIsPlayingChanged 早于 native
                            // 会话打开（hasOpenSession=false），锚点未启动 → 系统侧与 libusb
                            // 双写 USB DAC → iso 错误 → 沙沙声。轮询兜底让会话打开后
                            // 3s 内自动启动锚点（把系统流钉到内置扬声器）。
                            runCatching { updateBackgroundAnchor() }
                        }
                    }
                    // 独占已开启但 USB 未授权：播放时请求（前台服务场景弹窗可靠显示），
                    // 授权成功后 PlayerService 的权限接收器会自动重试独占打开
                    val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                    if (prefs.getBoolean("usb_exclusive_playback", false)) {
                        UsbExclusivePermissionManager.register(this@PlayerService)
                        val unpermitted = listUsbAudioDevices(this@PlayerService).firstOrNull {
                            !it.hasUsbExclusivePermission(this@PlayerService)
                        }
                        if (unpermitted != null) {
                            UsbExclusivePermissionManager.requestPermission(
                                this@PlayerService,
                                unpermitted
                            )
                        }
                    }
                    // 播放淡入：开始播放时音量渐变（对齐 Neri）
                    val fadeInEnabled = getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .getBoolean("playback_fade_in", false)
                    val fadeInMs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .getInt("playback_fade_in_ms", 500)
                    if (fadeInEnabled && fadeInMs > 0) {
                        startVolumeFade(player, fadeInMs)
                    }
                } else {
                    routingRefreshJob?.cancel()
                    routingRefreshJob = null
                    abandonAudioFocus()
                    scheduleIdleShutdownIfNeeded()
                }
                updateBackgroundAnchor()
                updateUsbExclusiveVolumeRouting()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateUsbExclusiveVolumeRouting()
            }
        })
    }

    // ==================== 播放淡入淡出 ====================

    private fun rebuildPlaybackRoute() {
        val player = mediaSession?.player ?: return
        val now = System.currentTimeMillis()
        if (now - lastUsbRouteRebuildAtMs < 500) return
        lastUsbRouteRebuildAtMs = now
        val wasPlaying = player.playWhenReady
        val position = player.currentPosition
        val index = player.currentMediaItemIndex
        serviceScope.launch {
            player.stop()
            delay(400) // 等待 sink/系统音频释放
            if (index >= 0 && player.mediaItemCount > 0) {
                player.seekTo(index, position)
                player.prepare()
                if (wasPlaying) player.play()
            }
            // 重建后按独占状态重新决定系统路由（独占激活 → 钉内置）
            refreshUsbRouteForExclusiveState()
        }
    }

    private fun setupVolumeObserver() {
        val audioManager = getSystemService(AudioManager::class.java)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                updateUsbSystemVolume()
            }
        }
        volumeObserver = observer
        runCatching {
            contentResolver.registerContentObserver(
                android.provider.Settings.System.CONTENT_URI,
                true,
                observer
            )
        }
        // 广播路径（比 Settings.System 观察更可靠，覆盖部分 ROM 不写设置表的情况）
        // 注：AudioManager.VOLUME_CHANGED_ACTION 在部分新 SDK stub 中隐藏，直接使用常量值
        volumeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != "android.media.VOLUME_CHANGED_ACTION") return
                updateUsbSystemVolume()
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(
                    volumeChangeReceiver,
                    IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(
                    volumeChangeReceiver,
                    IntentFilter("android.media.VOLUME_CHANGED_ACTION")
                )
            }
        }
        updateUsbSystemVolume()
    }

    private fun updateUsbSystemVolume() {
        val audioManager = getSystemService(AudioManager::class.java)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val fraction = if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
            0f
        } else {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
        }
        com.example.lxmusic.usb.system.UsbExclusiveSystemVolumeBridge
            .updateSessionVolumeFraction(fraction.coerceIn(0f, 1f))
    }

    private fun applyComponentStateForAttachHandling(prefs: android.content.SharedPreferences) {
        val handlingEnabled = prefs.getBoolean("usb_exclusive_auto_attach_handling", true)
        UsbDeviceAttachHandling.applyComponentState(this, handlingEnabled)
    }

    /**
     * USB 独占音量路由（默认关闭）：独占激活且非比特完美且用户开启时，
     * 用框架 MediaSession + VolumeProvider 接管系统音量（音量键/锁屏条 → native 增益）。
     * 默认走系统音量观察（VOLUME_CHANGED_ACTION 广播）即可生效；框架会话在部分 ROM
     * （如 MIUI）会干扰媒体会话栈导致播放卡顿，故默认关闭，由设置页手动开启。
     */
    private fun updateUsbExclusiveVolumeRouting() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val bitPerfect = prefs.getBoolean("usb_exclusive_bit_perfect", false)
        val routingEnabled = prefs.getBoolean("usb_exclusive_system_volume_routing", false)
        val active = routingEnabled &&
            prefs.getBoolean("usb_exclusive_playback", false) &&
            !bitPerfect &&
            UsbExclusiveSessionController.isNativeTransportStarted()
        UsbExclusiveVolumeRoutingSession.refresh(this, active)
        val player = mediaSession?.player
        if (player != null) {
            UsbExclusiveVolumeRoutingSession.syncPlaybackState(
                playing = player.isPlaying,
                positionMs = player.currentPosition
            )
        }
    }

    private fun startVolumeFade(player: Player, durationMs: Int) {
        volumeFadeJob?.cancel()
        volumeFadeJob = serviceScope.launch {
            val steps = 20
            val stepDelay = (durationMs / steps).coerceAtLeast(1)
            player.volume = 0f
            repeat(steps) { step ->
                delay(stepDelay.toLong())
                player.volume = ((step + 1).toFloat() / steps).coerceAtMost(1f)
            }
            player.volume = 1f
        }    }

    // ==================== 蓝牙/耳机断开自动停止 ====================

    private fun handleBecomingNoisy() {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady) return
        if (noisyPausePending) return
        noisyPausePending = true
        noisyConfirmAttempts = 0
        // 采样确认：3 次采样（间隔 300ms），期间恢复播放则取消
        serviceScope.launch {
            repeat(3) {
                delay(300)
                if (!player.playWhenReady) {
                    noisyPausePending = false
                    return@launch
                }
                noisyConfirmAttempts++
            }
            noisyPausePending = false
            val stillNoisy = !isAudioRouteRestored()
            if (stillNoisy && player.playWhenReady) {
                player.pause()
            }
        }
    }

    private fun isAudioRouteRestored(): Boolean {
        return runCatching {
            val audioManager = getSystemService(AudioManager::class.java)
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            // 蓝牙重新连上或用户切回有线输出视为已恢复
            devices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            }
        }.getOrDefault(true)
    }

    // ==================== 音频焦点 ====================

    private fun setupAudioFocus(preempt: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val audioManager = getSystemService(AudioManager::class.java)
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        // 其他 app 接管音频：释放独占会话并抑制重开，
                        // 避免 libusb 与系统侧双写 USB DAC 等时端点（包错误/沙沙声）
                        usbExclusiveSink?.setFocusInhibited(true)
                        mediaSession?.player?.pause()
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        // 焦点回归：解除抑制，恢复播放由用户操作触发
                        usbExclusiveSink?.setFocusInhibited(false)
                    }
                }
            }
            .build()
        audioFocusRequest = focusRequest
        if (preempt) {
            runCatching { audioManager.requestAudioFocus(focusRequest) }
        }
    }

    fun acquireAudioFocus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val req = audioFocusRequest ?: return
        runCatching {
            getSystemService(AudioManager::class.java).requestAudioFocus(req)
        }
    }

    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val req = audioFocusRequest ?: return
        runCatching {
            getSystemService(AudioManager::class.java).abandonAudioFocusRequest(req)
        }
    }

    // ==================== 暂停后退出播放服务 ====================

    private fun scheduleIdleShutdownIfNeeded() {
        val player = mediaSession?.player ?: return
        if (player.playWhenReady) return // 仍在播放/缓冲中
        if (player.mediaItemCount == 0) return
        val minutes = getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getInt("playback_service_idle_shutdown_minutes", 0)
        if (minutes <= 0) return
        idleShutdownRunnable?.let { idleHandler?.removeCallbacks(it) }
        val runnable = Runnable {
            val p = mediaSession?.player
            if (p != null && !p.isPlaying && !p.playWhenReady) {
                stopSelf()
            }
        }
        idleShutdownRunnable = runnable
        idleHandler?.postDelayed(runnable, minutes * 60_000L)
    }

    private fun cancelIdleShutdown() {
        idleShutdownRunnable?.let { idleHandler?.removeCallbacks(it) }
        idleShutdownRunnable = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                // 确保通知不会被系统隐藏
                setBypassDnd(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundIfNeeded() {
        val player = mediaSession?.player ?: return
        if (player.isPlaying) {
            // 启动前台服务，确保通知栏显示媒体控制
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setContentTitle(player.currentMediaItem?.mediaMetadata?.title ?: "正在播放")
                .setContentText(player.currentMediaItem?.mediaMetadata?.artist ?: "未知歌手")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        anchorServiceInstance = null
        UsbExclusiveBackgroundAudioAnchor.stop("service_destroy")
        cancelIdleShutdown()
        idleHandler?.removeCallbacksAndMessages(null)
        idleHandler = null
        volumeFadeJob?.cancel()
        volumeFadeJob = null
        abandonAudioFocus()
        audioFocusRequest = null
        runCatching {
            settingsChangeReceiver?.let { unregisterReceiver(it) }
        }
        settingsChangeReceiver = null
        runCatching {
            noisyReceiver?.let { unregisterReceiver(it) }
        }
        noisyReceiver = null
        runCatching {
            usbAttachReceiver?.let { unregisterReceiver(it) }
        }
        usbAttachReceiver = null
        runCatching {
            screenOffReceiver?.let { unregisterReceiver(it) }
        }
        screenOffReceiver = null
        runCatching {
            volumeObserver?.let { contentResolver.unregisterContentObserver(it) }
        }
        volumeObserver = null
        runCatching {
            volumeChangeReceiver?.let { unregisterReceiver(it) }
        }
        volumeChangeReceiver = null
        UsbExclusiveVolumeRoutingSession.refresh(this, active = false)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
