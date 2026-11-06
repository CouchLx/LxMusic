@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.lxmusic

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Cloud
import com.example.lxmusic.ui.components.ThemeModeToggleButton
import com.example.lxmusic.ui.components.ThemeRevealOverlay
import com.example.lxmusic.ui.components.AppUpdateDialog
import com.example.lxmusic.ui.components.topbar.ModernHomeTopBar
import com.example.lxmusic.ui.components.topbar.ModernMineTopBar
import com.example.lxmusic.ui.components.topbar.TopBarFadeTailHeight
import com.example.lxmusic.ui.components.topbar.topBarFadeTailBrush
import com.example.lxmusic.ui.components.topbar.lyricEdgeFadeMask
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import coil.compose.rememberAsyncImagePainter
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.request.ImageRequest
import com.example.lxmusic.coil.AudioArtFetcher
import com.example.lxmusic.ui.theme.LxMusicTheme
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.ui.components.FloatingBottomBar
import com.example.lxmusic.ui.components.BottomBarTab
import com.example.lxmusic.data.LiquidGlassSettings
import com.example.lxmusic.data.SettingsRepository
import com.example.lxmusic.ui.components.LocalMusicSidePanel
import com.example.lxmusic.ui.components.MiniPlayerBar
import com.example.lxmusic.ui.components.HapticsProvider
import com.example.lxmusic.ui.components.PlaylistSheet
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.example.lxmusic.ui.pages.HomePage
import com.example.lxmusic.ui.pages.DiscoverPage
import com.example.lxmusic.ui.pages.RankDetailPage
import com.example.lxmusic.ui.pages.SearchPage
import com.example.lxmusic.ui.pages.LoginPage
import com.example.lxmusic.ui.pages.PlayerStage
import com.example.lxmusic.ui.pages.SongListPage
import com.example.lxmusic.ui.pages.PlaylistManagePage
import com.example.lxmusic.ui.pages.PlaylistPage
import com.example.lxmusic.data.search.PublicPlaylistProviders
import com.example.lxmusic.ui.pages.PlaylistDetailPage
import com.example.lxmusic.ui.pages.PublicPlaylistDetailPage
import com.example.lxmusic.ui.pages.PublicAlbumDetailPage
import com.example.lxmusic.ui.pages.GuestMinePage
import com.example.lxmusic.ui.pages.CollectionDetailPage
import com.example.lxmusic.ui.pages.SearchPlaylistDetailPage
import com.example.lxmusic.ui.pages.MinePage
import com.example.lxmusic.ui.pages.SearchSourceOption
import com.example.lxmusic.ui.pages.PlaylistManagerPage
import com.example.lxmusic.ui.effect.PlayerBackdrop
import com.example.lxmusic.ui.pages.LocalMusicPage
import com.example.lxmusic.ui.pages.LocalMusicTopBar
import com.example.lxmusic.ui.pages.SearchTopBar
import com.example.lxmusic.ui.pages.SettingsPage
import com.example.lxmusic.ui.pages.EqualizerPage
import com.example.lxmusic.ui.pages.SoundSettingsPage
import com.example.lxmusic.util.applyPreferredHighRefreshRate
import com.example.lxmusic.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

// ==================== 入口 ====================

/** 播放器展开态：舞台共享背景 + 单页面卡片翻页（0=封面卡 1=歌词卡） */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 强制导航栏透明（兼容小米 MIUI）
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightNavigationBars = true
        }
        // 应用高刷新率偏好
        val activitySettings = SettingsRepository(this)
        applyPreferredHighRefreshRate(activitySettings.preferHighRefreshRate)
        // USB 独占：前后台切换时调整原生缓冲（前台 250ms / 后台 1500ms）
        lifecycle.addObserver(object : androidx.lifecycle.LifecycleEventObserver {
            override fun onStateChanged(
                source: androidx.lifecycle.LifecycleOwner,
                event: androidx.lifecycle.Lifecycle.Event
            ) {
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_START -> {
                        com.example.lxmusic.usb.session.UsbExclusiveSessionController
                            .setAppInForeground(true, this@MainActivity)
                        PlayerService.setAppInForeground(true)
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                        com.example.lxmusic.usb.session.UsbExclusiveSessionController
                            .setAppInForeground(false, this@MainActivity)
                        PlayerService.setAppInForeground(false)
                    }
                    else -> {}
                }
            }
        })
        // 缓存上限（MB→字节，0=不缓存）
        val configuredCacheBytes = activitySettings.maxCacheSizeBytes
        // 插件运行时需要 Application Context（DataSource/歌词仓库无 Context）
        com.example.lxmusic.data.plugin.PluginContext.init(this)
        // 在线服务地址必须在 setContent 之前就绪：启动门禁弹窗需要立即判断是否有内置后端
        KuGouApi.bffUrl = getSharedPreferences("settings", MODE_PRIVATE)
            .getString("bff_url", null)
            ?: BuildConfig.LX_BFF_URL.trim()
        // 身份与激活态同样要在首帧之前恢复到静态变量：
        // 首页首帧就会消费「刷新标记」发起请求，晚一步就会用空身份拉到空数据
        KuGouApi.audioQuality = activitySettings.audioQuality
        val bootAuthPrefs = getSharedPreferences("auth", MODE_PRIVATE)
        val bootToken = bootAuthPrefs.getString("token", "") ?: ""
        if (bootToken.isNotBlank()) {
            KuGouApi.token = bootToken
            KuGouApi.userid = bootAuthPrefs.getLong("userid", 0L).toString()
        }
        VipConfigManager.applySavedVipToApi(this)
        setContent {
            // 配置 Coil ImageLoader，注册音频封面解码器 + 缓存
            val imageLoader = ImageLoader.Builder(this)
                .components { add(AudioArtFetcher.Factory()) }
                .memoryCache {
                    coil.memory.MemoryCache.Builder(this)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    coil.disk.DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizePercent(0.05)
                        .maxSizeBytes(if (configuredCacheBytes > 0L) configuredCacheBytes else 100L * 1024 * 1024)
                        .build()
                }
                .build()

            CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                val ctx = LocalContext.current
                val settingsRepository = remember { SettingsRepository(ctx) }

                // 首次启动初始化：确保默认主题为原生主题，导航栏完全等于背景色
                LaunchedEffect(Unit) {
                    if (!settingsRepository.contains(SettingsRepository.Keys.THEME_MODE)) {
                        settingsRepository.themeMode = "dynamic"
                        settingsRepository.dynamicColor = true
                        settingsRepository.floatingBottomBar = false
                    }
                }

                var dynamicColor by remember { mutableStateOf(settingsRepository.dynamicColor) }
                var themeColorHex by remember { mutableStateOf(settingsRepository.themeColorHex) }
                // 主题设置（Neri 风格：种子色 / 取色风格 / 明暗模式 / 颜色动画）
                var themeSeedColor by remember { mutableStateOf(settingsRepository.themeSeedColor) }
                var themePaletteStyle by remember { mutableStateOf(settingsRepository.themePaletteStyle) }
                var themeDarkMode by remember { mutableStateOf(settingsRepository.themeDarkMode) }
                var themeColorAnimation by remember { mutableStateOf(settingsRepository.themeColorAnimation) }
                // 通用设置
                var uiDensityScale by remember { mutableFloatStateOf(settingsRepository.uiDensityScale) }
                // 原生底栏高度缩放（通用设置里可调，0.8~1.2）
                var bottomBarHeightScale by remember { mutableFloatStateOf(settingsRepository.bottomBarHeightScale) }
                var hapticEnabled by remember { mutableStateOf(settingsRepository.hapticFeedback) }
                var preferHighRefreshRate by remember { mutableStateOf(settingsRepository.preferHighRefreshRate) }
                var favoriteToKugou by remember { mutableStateOf(settingsRepository.favoriteToKugou) }
                var favoriteSyncLocal by remember { mutableStateOf(settingsRepository.favoriteSyncLocal) }
                // 高刷新率实时生效
                LaunchedEffect(preferHighRefreshRate) {
                    applyPreferredHighRefreshRate(preferHighRefreshRate)
                }
                // 动态取色 = 根据当前歌曲专辑封面取色（对齐 NeriPlayer）。
                // 监听当前播放歌曲，从其专辑封面提取种子色，随切歌实时变化。
                val playerViewModel: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                val playerUiState by playerViewModel.uiState.collectAsState()
                val currentSong = playerUiState.currentSong
                // 供插件解析播放地址时补全 musicInfo（歌名/歌手/时长）
                LaunchedEffect(currentSong?.filePath) {
                    com.example.lxmusic.data.plugin.PluginPlayback.currentSong = currentSong
                }
                var albumSeedColorHex by remember { mutableStateOf<String?>(null) }
                // 明暗模式：自动 / 浅色 / 深色（提前算好，供封面色预热使用）
                val resolvedDarkTheme = when (themeDarkMode) {
                    "light" -> false
                    "dark" -> true
                    else -> isSystemInDarkTheme()
                }
                LaunchedEffect(currentSong?.filePath, currentSong?.albumArtUri) {
                    albumSeedColorHex = null
                    val song = currentSong ?: return@LaunchedEffect
                    val albumModel: Any = when {
                        song.albumArtUri != null && (song.albumArtUri.startsWith("/") || song.albumArtUri.startsWith("file://")) ->
                            File(song.albumArtUri.removePrefix("file://"))
                        song.albumArtUri != null -> Uri.parse(song.albumArtUri)
                        else -> File(song.filePath)
                    }
                    albumSeedColorHex = com.example.lxmusic.ui.effect.extractAlbumSeedHex(ctx, imageLoader, albumModel)
                    // 预热背景提色缓存：打开播放器首帧直接用封面色，避免先闪一帧深色兜底
                    com.example.lxmusic.ui.effect.warmAlbumColorCache(ctx, imageLoader, albumModel)
                    // 灵动流体背景的 4 色配色同样预热（否则会先显示默认紫蓝再渐变 1.2s）
                    com.example.lxmusic.ui.effect.warmFluidColorCache(
                        ctx, imageLoader, albumModel, resolvedDarkTheme
                    )
                }

                val view = androidx.compose.ui.platform.LocalView.current
                if (!view.isInEditMode) {
                    androidx.compose.runtime.SideEffect {
                        val window = (view.context as? android.app.Activity)?.window
                        if (window != null) {
                            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                            insetsController.isAppearanceLightStatusBars = !resolvedDarkTheme
                            insetsController.isAppearanceLightNavigationBars = !resolvedDarkTheme
                        }
                    }
                }

                LxMusicTheme(
                    darkTheme = resolvedDarkTheme,
                    dynamicColor = dynamicColor,
                    albumSeedColorHex = albumSeedColorHex,
                    customPrimaryColor = null,
                    seedColorHex = themeSeedColor,
                    paletteStyle = themePaletteStyle,
                    colorAnimation = themeColorAnimation,
                    uiDensityScale = uiDensityScale
                ) {
                    CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground
                    ) {
                        HapticsProvider(enabled = hapticEnabled) {
                            AppScaffold(
                                onDynamicColorChange = { enabled ->
                                    dynamicColor = enabled
                                    settingsRepository.dynamicColor = enabled
                                },
                                onThemeColorChange = { hex ->
                                    themeColorHex = hex
                                    settingsRepository.themeColorHex = hex
                                },
                                themeSeedColor = themeSeedColor,
                                onThemeSeedColorChange = { value ->
                                    themeSeedColor = value
                                    settingsRepository.themeSeedColor = value
                                },
                                themePaletteStyle = themePaletteStyle,
                                onThemePaletteStyleChange = { value ->
                                    themePaletteStyle = value
                                    settingsRepository.themePaletteStyle = value
                                },
                                themeDarkMode = themeDarkMode,
                                onThemeDarkModeChange = { value ->
                                    themeDarkMode = value
                                    settingsRepository.themeDarkMode = value
                                },
                                themeColorAnimation = themeColorAnimation,
                                onThemeColorAnimationChange = { enabled ->
                                    themeColorAnimation = enabled
                                    settingsRepository.themeColorAnimation = enabled
                                },
                                uiDensityScale = uiDensityScale,
                                onUiDensityScaleChange = { value ->
                                    uiDensityScale = value
                                    settingsRepository.uiDensityScale = value
                                },
                                bottomBarHeightScale = bottomBarHeightScale,
                                onBottomBarHeightScaleChange = { value ->
                                    bottomBarHeightScale = value
                                    settingsRepository.bottomBarHeightScale = value
                                },
                                hapticEnabled = hapticEnabled,
                                onHapticEnabledChange = { enabled ->
                                    hapticEnabled = enabled
                                    settingsRepository.hapticFeedback = enabled
                                },
                                preferHighRefreshRate = preferHighRefreshRate,
                                onPreferHighRefreshRateChange = { enabled ->
                                    preferHighRefreshRate = enabled
                                    settingsRepository.preferHighRefreshRate = enabled
                                },
                                favoriteToKugou = favoriteToKugou,
                                onFavoriteToKugouChange = { enabled ->
                                    favoriteToKugou = enabled
                                    settingsRepository.favoriteToKugou = enabled
                                },
                                favoriteSyncLocal = favoriteSyncLocal,
                                onFavoriteSyncLocalChange = { enabled ->
                                    favoriteSyncLocal = enabled
                                    settingsRepository.favoriteSyncLocal = enabled
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // USB DAC 插入时，alias 拉起 MainActivity → 处理 intent → PlayerService 已在广播里处理 attach
        // 这里额外处理：如果应用已在前台，跳转到 USB 设置页
        if (intent?.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val usbEnabled = getSharedPreferences("settings", MODE_PRIVATE)
                .getBoolean("usb_exclusive_playback", false)
            if (usbEnabled) {
                // 通知 PlayerService 处理 attach（已在广播中处理，这里双保险）
                com.example.lxmusic.usb.session.UsbExclusiveSessionController
                    .handleUsbDeviceAttached(this)
            }
        }
    }
}

private suspend fun captureActivitySnapshot(activity: android.app.Activity): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        val window = activity.window ?: return null
        val decorView = window.decorView ?: return null
        if (decorView.width <= 0 || decorView.height <= 0) return null

        val bitmap = android.graphics.Bitmap.createBitmap(
            decorView.width,
            decorView.height,
            android.graphics.Bitmap.Config.ARGB_8888
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            suspendCancellableCoroutine<androidx.compose.ui.graphics.ImageBitmap?> { continuation ->
                android.view.PixelCopy.request(
                    window,
                    bitmap,
                    { copyResult ->
                        if (copyResult == android.view.PixelCopy.SUCCESS) {
                            continuation.resume(bitmap.asImageBitmap())
                        } else {
                            try {
                                val canvas = android.graphics.Canvas(bitmap)
                                decorView.draw(canvas)
                                continuation.resume(bitmap.asImageBitmap())
                            } catch (_: Exception) {
                                continuation.resume(null)
                            }
                        }
                    },
                    android.os.Handler(android.os.Looper.getMainLooper())
                )
            }
        } else {
            val canvas = android.graphics.Canvas(bitmap)
            decorView.draw(canvas)
            bitmap.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}

// ==================== 主框架 ====================

/**
 * 顶栏胶囊左侧的迷你律动条：播放时跳动，暂停时静止为三根小矮条。
 * 暂停分支不建 infiniteTransition，不耗电。
 */
@Composable
private fun HomeEqBars(heights: List<Float>, alpha: Float, tint: Color) {
    Row(
        modifier = Modifier.height(16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .fillMaxHeight(h.coerceIn(0.05f, 1f))
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun HomePlayEqIcon(
    playing: Boolean,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    if (playing) {
        val transition = rememberInfiniteTransition(label = "home_eq")
        val b1 by transition.animateFloat(
            initialValue = 0.25f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
            label = "eq1"
        )
        val b2 by transition.animateFloat(
            initialValue = 0.9f, targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse),
            label = "eq2"
        )
        val b3 by transition.animateFloat(
            initialValue = 0.45f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
            label = "eq3"
        )
        HomeEqBars(listOf(b1, b2, b3), 1f, tint)
    } else {
        // 未播放：显示音符图标（固定尺寸，不参与自适应）
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = tint.copy(alpha = 0.9f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppScaffold(
    onDynamicColorChange: (Boolean) -> Unit = {},
    onThemeColorChange: (String) -> Unit = {},
    themeSeedColor: String = "0061A4",
    onThemeSeedColorChange: (String) -> Unit = {},
    themePaletteStyle: String = "TonalSpot",
    onThemePaletteStyleChange: (String) -> Unit = {},
    themeDarkMode: String = "auto",
    onThemeDarkModeChange: (String) -> Unit = {},
    themeColorAnimation: Boolean = true,
    onThemeColorAnimationChange: (Boolean) -> Unit = {},
    uiDensityScale: Float = 1f,
    onUiDensityScaleChange: (Float) -> Unit = {},
    bottomBarHeightScale: Float = 1f,
    onBottomBarHeightScaleChange: (Float) -> Unit = {},
    hapticEnabled: Boolean = true,
    onHapticEnabledChange: (Boolean) -> Unit = {},
    preferHighRefreshRate: Boolean = false,
    onPreferHighRefreshRateChange: (Boolean) -> Unit = {},
    favoriteToKugou: Boolean = false,
    onFavoriteToKugouChange: (Boolean) -> Unit = {},
    favoriteSyncLocal: Boolean = false,
    onFavoriteSyncLocalChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 横屏检测：页面内容限宽居中用（旋转时 LocalConfiguration 变化会自动重组）
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val playerViewModel: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val uiState by playerViewModel.uiState.collectAsState()
    val audioFormat by playerViewModel.audioFormat.collectAsState()
    // 未激活 = 「本地/插件模式」：有在线服务（BFF）但未激活时启用精简界面。
    // 开源构建（无 BFF）不做激活门禁，isGuest 恒为 false，保持原有界面与逻辑。
    val isGuest = KuGouApi.bffUrl.isNotBlank() && !ActivationState.canUseVip
    // 初始 tab 按模式选择：未激活直接落在搜索（11），避免冷启动先组合一帧首页触发首页请求
    var selectedTab by rememberSaveable { mutableIntStateOf(if (isGuest) 11 else 0) }
    // 两种模式的顶层 tab 集合（未激活没有首页，搜索成为顶层 tab）
    val topLevelTabs = remember(isGuest) { if (isGuest) setOf(11, 1, 2) else setOf(0, 1, 2) }
    // 未激活搜索音源：本地 + 公开元数据搜索 + 已启用插件。
    // selectedTab 变化时重算：从设置页导入插件后回到搜索即可见。
    val guestSearchSources: List<SearchSourceOption>? = if (isGuest) {
        remember(selectedTab) {
            buildList {
                add(SearchSourceOption("local", "本地"))
                add(SearchSourceOption("kugou", "酷狗"))
                add(SearchSourceOption("netease", "网易云"))
                add(SearchSourceOption("tx", "QQ音乐"))
                add(SearchSourceOption("kw", "酷我音乐"))
                com.example.lxmusic.data.plugin.PluginStore.enabled(context).forEach { plugin ->
                    add(
                        SearchSourceOption(
                            "plugin:${plugin.id}",
                            "插件·${plugin.name.ifBlank { plugin.fileName }}"
                        )
                    )
                }
            }
        }
    } else null
    val bottomTabs = if (isGuest) listOf(
        BottomBarTab(11, Icons.Filled.Search, Icons.Outlined.Search, stringResource(R.string.title_search)),
        BottomBarTab(1, Icons.Filled.Explore, Icons.Outlined.Explore, stringResource(R.string.nav_discover)),
        BottomBarTab(2, Icons.Filled.Person, Icons.Outlined.Person, stringResource(R.string.nav_mine))
    ) else listOf(
        BottomBarTab(0, Icons.Filled.Home, Icons.Outlined.Home, stringResource(R.string.nav_home)),
        BottomBarTab(1, Icons.Filled.Search, Icons.Outlined.Search, stringResource(R.string.nav_discover)),
        BottomBarTab(2, Icons.Filled.Person, Icons.Outlined.Person, stringResource(R.string.nav_mine))
    )
    var settingsSubPage by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedTab) { if (selectedTab != 12) settingsSubPage = null }
    var isThemeRevealing by remember { mutableStateOf(false) }
    var themeRevealSnapshot by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var themeRevealOrigin by remember { mutableStateOf<Offset?>(null) }
    var themeRevealStartRadius by remember { mutableFloatStateOf(18f) }
    var themeRevealFallbackColor by remember { mutableStateOf<Color?>(null) }
    val currentBgColor = MaterialTheme.colorScheme.background
    var showPlayerPage by rememberSaveable { mutableStateOf(false) }
    // 播放器菜单 → 独立页面（声音设置 / 均衡器）：覆盖在播放器之上，返回键优先关闭
    var showSoundSettingsPage by rememberSaveable { mutableStateOf(false) }
    var showEqualizerPage by rememberSaveable { mutableStateOf(false) }
    // 歌词页状态：由播放器翻页器驱动，此处仅作进程重建时的持久化镜像
    var showLyricsPage by rememberSaveable { mutableStateOf(false) }
    // 播放器 ↔ 歌词页翻页器（0=播放页 1=歌词页）。hoist 到 MainActivity 层：
    // 收起再展开、进程重建（rememberPagerState 自带 Saver）都恢复上次所在页
    val playerPagerState = rememberPagerState(initialPage = if (showLyricsPage) 1 else 0) { 2 }
    LaunchedEffect(playerPagerState.currentPage) {
        showLyricsPage = playerPagerState.currentPage == 1
    }
    // 播放器展开/收起共用的屏幕高度
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }
    // 播放器展开/收起统一偏移（0=完全展开，screenHeightPx=完全收起）：
    // mini 播放条上拉跟手展开 / 播放器内下拉跟手收起共用同一状态。
    // 初始为收起态（打开软件不展开播放器）
    val playerRevealY = remember { Animatable(screenHeightPx) }
    var isMiniBarDragging by remember { mutableStateOf(false) }
    // 页面展开/收起动画进行中（组合条件用布尔驱动，避免每帧读取动画值导致整树重组）
    var isRevealAnimating by remember { mutableStateOf(false) }

    // 旋转（横竖屏切换）时校正收起态偏移：
    // playerRevealY 是 remember 缓存的 Animatable，旋转后仍保留旧方向的屏幕高度
    // （竖屏 2670 ↔ 横屏 900），使收起态偏移 ≠ 新屏高 → 底栏的 translationY 计算出
    // 非 0 值被推出屏幕（表现为旋转后底栏消失）。这里在屏幕高度变化、且处于静止
    // 收起态时立即 snap 到新屏高，从数据层面根治。
    LaunchedEffect(screenHeightPx) {
        if (!showPlayerPage && !isMiniBarDragging && !isRevealAnimating) {
            playerRevealY.snapTo(screenHeightPx)
        }
    }
    var previousTab by rememberSaveable { mutableIntStateOf(0) }

    // 进程重建后，依赖非持久化数据的子页面（4-7 每日/历史/风格列表、8-10 歌单详情）
    // 数据已丢失，回退到父级页面，避免显示空白列表。
    // 未激活模式没有首页（0）及其子页，统一落到搜索（11）。
    LaunchedEffect(Unit) {
        if (isGuest) {
            when (selectedTab) {
                0, 13, 18, in 4..7 -> selectedTab = 11
                in 8..10 -> selectedTab = 2
            }
        } else {
            when (selectedTab) {
                in 4..7 -> selectedTab = 0
                13 -> selectedTab = 0
                18 -> selectedTab = 0
                in 8..10 -> selectedTab = 2
            }
        }
    }

    // 激活成功后立即切到完整版首页；解绑（Activity.recreate 重建）时由上面的恢复逻辑落到搜索
    var wasGuest by remember { mutableStateOf(isGuest) }
    LaunchedEffect(isGuest) {
        if (wasGuest && !isGuest) selectedTab = 0
        wasGuest = isGuest
    }

    val currentSong = uiState.currentSong
    val currentSongList = uiState.queue
    val currentSongIndex = uiState.currentIndex
    val isPlaying = uiState.isPlaying
    val currentPlayingPath = uiState.currentPlayingPath
    val playMode = uiState.playMode

    var showPlaylistSheet by remember { mutableStateOf(false) }
    // 在线歌词（播放页与全屏歌词页共享）
    // 关键优化：使用 remember(currentSong?.filePath) 同步命中内存与磁盘缓存（0ms 响应，首帧即有歌词，彻底告别“正在加载”卡顿）
    val initialCachedLyric = remember(currentSong?.filePath) {
        currentSong?.let { song ->
            com.example.lxmusic.data.LyricRepository.getCachedLyric(context, song)
                ?: song.lyrics?.takeIf { it.isNotBlank() }
        }
    }
    var onlineLyrics by remember(currentSong?.filePath) { mutableStateOf(initialCachedLyric) }

    // 翻译（LRC 文本，来自网易云 tlyric / QQ trans）：独立于主歌词的内存通路
    var onlineTranslationText by remember(currentSong?.filePath) {
        mutableStateOf(currentSong?.let { com.example.lxmusic.data.LyricRepository.getTranslationMemory(it) })
    }

    LaunchedEffect(currentSong?.filePath) {
        val song = currentSong ?: return@LaunchedEffect

        // 1. 立即在后台静默预加载后续曲目与前一首曲目，确保连续切歌 0ms 秒开
        val next1 = currentSongList.getOrNull(currentSongIndex + 1)
        val next2 = currentSongList.getOrNull(currentSongIndex + 2)
        val prev1 = currentSongList.getOrNull(currentSongIndex - 1)
        launch(Dispatchers.IO) {
            com.example.lxmusic.data.LyricRepository.preload(context, next1)
            com.example.lxmusic.data.LyricRepository.preload(context, next2)
            com.example.lxmusic.data.LyricRepository.preload(context, prev1)
        }

        // 2. 若已有最高精度逐字歌词（TTML、YRC 或 QRC），无需重复发起网络拉取；
        //    但翻译仍要补拉（翻译不落盘，重启后内存为空）
        val currentHasVerbatim = !onlineLyrics.isNullOrBlank() && (
            com.example.lxmusic.ui.lyrics.isTtmlLyrics(onlineLyrics!!) ||
            com.example.lxmusic.ui.lyrics.isNeteaseYrc(onlineLyrics!!) ||
            com.example.lxmusic.ui.lyrics.isQrcLyrics(onlineLyrics!!) ||
            com.example.lxmusic.ui.lyrics.isKrcLyrics(onlineLyrics!!)
        )
        if (currentHasVerbatim) {
            if (onlineTranslationText == null) {
                launch(Dispatchers.IO) {
                    val t = runCatching {
                        com.example.lxmusic.data.LyricRepository.fetchTranslation(song)
                    }.getOrNull()
                    if (!t.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            if (onlineTranslationText == null) onlineTranslationText = t
                        }
                    }
                }
            }
            return@LaunchedEffect
        }

        // 3. 双通路并发拉取（Native 极速通道 ~150ms 先顶上 + 高画质逐字通道平滑升级）
        kotlinx.coroutines.coroutineScope {
            // 通路 A：原生平台歌词（网易云或酷狗），通常 150ms 内极速返回
            launch(Dispatchers.IO) {
                try {
                    val nativeContent = com.example.lxmusic.data.LyricRepository.fetchNativeLyric(song)
                    if (!nativeContent.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            // 仅当当前还没有获取到更高规格的逐字时先展示原生歌词
                            if (onlineLyrics == null || (!com.example.lxmusic.ui.lyrics.isTtmlLyrics(onlineLyrics!!) && !com.example.lxmusic.ui.lyrics.isNeteaseYrc(onlineLyrics!!) && !com.example.lxmusic.ui.lyrics.isQrcLyrics(onlineLyrics!!) && !com.example.lxmusic.ui.lyrics.isKrcLyrics(onlineLyrics!!))) {
                                onlineLyrics = nativeContent
                            }
                            // fetchNativeLyric 顺带缓存了 tlyric，回填翻译
                            if (onlineTranslationText == null) {
                                onlineTranslationText =
                                    com.example.lxmusic.data.LyricRepository.getTranslationMemory(song)
                            }
                        }
                        com.example.lxmusic.data.LyricRepository.putCachedLyric(context, song, nativeContent)
                    }
                } catch (_: Exception) {}
            }

            // 通路 B：最高品质逐字通道（AMLL TTML -> 网易云 YRC -> QQ音乐 QRC）
            launch(Dispatchers.IO) {
                try {
                    val bestVerbatim = com.example.lxmusic.data.LyricRepository.fetchAmllLyric(song)
                    if (!bestVerbatim.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            onlineLyrics = bestVerbatim
                            // QQ 通路在 fetchAmllLyric 内经 onTranslation 回填了翻译
                            if (onlineTranslationText == null) {
                                onlineTranslationText =
                                    com.example.lxmusic.data.LyricRepository.getTranslationMemory(song)
                            }
                        }
                        com.example.lxmusic.data.LyricRepository.putCachedLyric(context, song, bestVerbatim)
                    }
                } catch (_: Exception) {}
            }
        }
    }
    val currentSongLyrics = currentSong?.lyrics
    val currentLyricsText = when {
        !onlineLyrics.isNullOrBlank() && (com.example.lxmusic.ui.lyrics.isTtmlLyrics(onlineLyrics!!) || com.example.lxmusic.ui.lyrics.isNeteaseYrc(onlineLyrics!!) || com.example.lxmusic.ui.lyrics.isQrcLyrics(onlineLyrics!!) || com.example.lxmusic.ui.lyrics.isKrcLyrics(onlineLyrics!!)) -> onlineLyrics
        !currentSongLyrics.isNullOrBlank() && (com.example.lxmusic.ui.lyrics.isTtmlLyrics(currentSongLyrics) || com.example.lxmusic.ui.lyrics.isNeteaseYrc(currentSongLyrics) || com.example.lxmusic.ui.lyrics.isQrcLyrics(currentSongLyrics) || com.example.lxmusic.ui.lyrics.isKrcLyrics(currentSongLyrics)) -> currentSongLyrics
        !onlineLyrics.isNullOrBlank() -> onlineLyrics
        else -> currentSongLyrics
    }
    // 翻译条目（±450ms 容差与主歌词行对齐，attachTranslations 消费）
    val currentTranslationEntries = remember(onlineTranslationText) {
        onlineTranslationText?.let { text ->
            com.example.lxmusic.ui.lyrics.parseNeteaseLrc(text).takeIf { it.isNotEmpty() }
        }
    }
    var showRankDetail by remember { mutableStateOf<RankItem?>(null) }
    var showPublicPlaylist by remember { mutableStateOf<PublicPlaylistProviders.PublicPlaylist?>(null) }
    var showPublicAlbum by remember { mutableStateOf<SearchAlbumItem?>(null) }
    var showLoginPage by rememberSaveable { mutableStateOf(false) }
    var loginVersion by rememberSaveable { mutableIntStateOf(0) }
    // 激活成功但未登录时，弹窗里点「去登录」→ 打开登录页
    LaunchedEffect(com.example.lxmusic.ui.components.AppUiEvents.requestLogin) {
        if (com.example.lxmusic.ui.components.AppUiEvents.requestLogin) {
            com.example.lxmusic.ui.components.AppUiEvents.requestLogin = false
            showLoginPage = true
        }
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchSelectedType by rememberSaveable { mutableStateOf("song") }
    // 搜索音源：kugou / netease（持久化 prefs "music_source"，启动时通过 settingsPrefs 恢复）
    var searchSource by rememberSaveable { mutableStateOf("kugou") }
    var searchPlaylistId by rememberSaveable { mutableLongStateOf(0L) }
    var searchPlaylistName by rememberSaveable { mutableStateOf("") }
    var searchPlaylistCover by rememberSaveable { mutableStateOf("") }
    var searchPlaylistAuthor by rememberSaveable { mutableStateOf("") }
    var searchPlaylistGid by rememberSaveable { mutableStateOf("") }
    var dailySongsForList by remember { mutableStateOf<List<DailyRecommendSong>>(emptyList()) }
    var vipSongsForList by remember { mutableStateOf<List<SongInfo>>(emptyList()) }
    var millionSongsForList by remember { mutableStateOf<List<SongInfo>>(emptyList()) }
    var networkSongsForList by remember { mutableStateOf<List<SongInfo>>(emptyList()) }
    var historySongsForList by remember { mutableStateOf<List<DailyRecommendSong>>(emptyList()) }
    var styleSongsForList by remember { mutableStateOf<List<DailyRecommendSong>>(emptyList()) }
    var selectedPlaylist by remember { mutableStateOf<UserPlaylistItem?>(null) }
    var selectedCollectionType by rememberSaveable { mutableStateOf("") }  // "favorites" 或 "playlist"
    var homeAllSongs by remember { mutableStateOf<List<SongInfo>>(emptyList()) }
    var homeScrollIndex by rememberSaveable { mutableIntStateOf(0) }
    var homeScrollOffset by rememberSaveable { mutableIntStateOf(0) }
    val homeListState = rememberLazyListState(initialFirstVisibleItemIndex = homeScrollIndex, initialFirstVisibleItemScrollOffset = homeScrollOffset)
    // 首页顶部推荐栏横滑进度（与首页外层列表一致：状态对象只建一次，切 tab 复用同一对象）
    var homeRecommendIndex by rememberSaveable { mutableIntStateOf(0) }
    var homeRecommendOffset by rememberSaveable { mutableIntStateOf(0) }
    val homeRecommendListState = rememberLazyListState(homeRecommendIndex, homeRecommendOffset)
    var discoverScrollIndex by rememberSaveable { mutableIntStateOf(0) }
    var discoverScrollOffset by rememberSaveable { mutableIntStateOf(0) }
    val discoverListState = rememberLazyListState(initialFirstVisibleItemIndex = discoverScrollIndex, initialFirstVisibleItemScrollOffset = discoverScrollOffset)
    // 发现页推荐子页滚动进度（与排行榜列表一致：状态对象只建一次，切底栏 tab 复用同一对象）
    var discoverRecommendScrollIndex by rememberSaveable { mutableIntStateOf(0) }
    var discoverRecommendScrollOffset by rememberSaveable { mutableIntStateOf(0) }
    val discoverRecommendListState = rememberLazyListState(initialFirstVisibleItemIndex = discoverRecommendScrollIndex, initialFirstVisibleItemScrollOffset = discoverRecommendScrollOffset)
    // 我的页滚动进度（与发现页一致：LazyListState 提升到外层只建一次，切 tab 复用同一对象）
    var mineScrollY by rememberSaveable { mutableIntStateOf(0) }
    val mineListState = rememberLazyListState(
        initialFirstVisibleItemIndex = 0,
        initialFirstVisibleItemScrollOffset = mineScrollY
    )
    LaunchedEffect(mineListState.firstVisibleItemScrollOffset) {
        mineScrollY = mineListState.firstVisibleItemScrollOffset
    }
    // 歌单详情列表滚动保留（返回重进同歌单不丢位置；换歌单回到顶部）
    var playlistDetailScrollIndex by rememberSaveable { mutableIntStateOf(0) }
    var playlistDetailScrollOffset by rememberSaveable { mutableIntStateOf(0) }
    val playlistDetailListState = rememberLazyListState(
        initialFirstVisibleItemIndex = playlistDetailScrollIndex,
        initialFirstVisibleItemScrollOffset = playlistDetailScrollOffset
    )
    LaunchedEffect(
        playlistDetailListState.firstVisibleItemIndex,
        playlistDetailListState.firstVisibleItemScrollOffset
    ) {
        playlistDetailScrollIndex = playlistDetailListState.firstVisibleItemIndex
        playlistDetailScrollOffset = playlistDetailListState.firstVisibleItemScrollOffset
    }
    val playlistDetailId = selectedPlaylist?.listid
    LaunchedEffect(playlistDetailId) {
        if (playlistDetailId != null) playlistDetailListState.scrollToItem(0)
    }
    var homeClickRefresh by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isHomeRefreshing by remember { mutableStateOf(false) }
    var isSelectionModeActive by remember { mutableStateOf(false) }
    LaunchedEffect(selectedTab) {
        isSelectionModeActive = false
    }
    var selectedCollectionPlaylistId by rememberSaveable { mutableLongStateOf(0L) }
    var onlineSongList by remember { mutableStateOf<List<SongInfo>>(emptyList()) }
    val settingsRepository = remember { SettingsRepository(context) }
    val settingsPrefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    val authPrefs = remember { context.getSharedPreferences("auth", android.content.Context.MODE_PRIVATE) }
    var blurNavBar by remember { mutableStateOf(settingsRepository.blurNavBar) }
    // 默认关闭悬浮底栏（原生主题下使用传统导航栏）
    var floatingBottomBar by remember { mutableStateOf(settingsRepository.floatingBottomBar) }
    var navBarOpacity by remember { mutableFloatStateOf(settingsRepository.navBarOpacity) }
    var playerBarOpacity by remember { mutableFloatStateOf(settingsRepository.playerBarOpacity) }
    var followThemeColor by remember { mutableStateOf(settingsRepository.followThemeColor) }
    var playerBarWhiteBlend by remember { mutableFloatStateOf(settingsRepository.playerBarWhiteBlend) }
    var floatingBarOpacity by remember { mutableFloatStateOf(settingsRepository.floatingBarOpacity) }
    val glassBackdrop = rememberLayerBackdrop()
    // 首页顶栏磨砂采样源：壁纸（zIndex 0）+ 首页列表（zIndex 1），
    // 顶栏用渐进模糊实时采样这两层，内容滑到顶栏下方时会一起被磨砂掉。
    val homeTopHazeState = rememberHazeState()
    
    // 本地音乐左侧面板状态（提升到 MainActivity，顶栏汉堡按钮与页面共享）
    var localShowSidePanel by remember { mutableStateOf(false) }
    val localPanelOffset = remember { Animatable(0f) }
    val localPanelWidthPx = with(LocalDensity.current) { 200.dp.toPx() }
    var localDrawerSection by remember { mutableIntStateOf(0) }
    var localScanTrigger by remember { mutableIntStateOf(0) }
    var locatePlayingSong by remember { mutableIntStateOf(0) }

    // 离开本地音乐页时收起侧边栏
    LaunchedEffect(selectedTab) {
        if (selectedTab != 3) {
            localShowSidePanel = false
        }
    }
    
    // 液态玻璃设置
    val liquidGlassSettings = remember { LiquidGlassSettings(context) }
    var liquidGlass by remember { mutableStateOf(liquidGlassSettings._enabled) }
    
    var playerBgOpacity by remember { mutableFloatStateOf(settingsRepository.playerBgOpacity) }
    var playerBlur by remember { mutableStateOf(settingsRepository.playerBlur) }
    var playerDynamicBg by remember { mutableStateOf(settingsRepository.playerDynamicBg) }
    var playerRoundAlbum by remember { mutableStateOf(settingsRepository.playerRoundAlbum) }
    var playerRotate by remember { mutableStateOf(settingsRepository.playerRotate) }
    var playerVinylStyle by remember { mutableStateOf(settingsRepository.playerVinylStyle) }
    var playerVinylPointer by remember { mutableStateOf(settingsRepository.playerVinylPointer) }
    var playerVinylBase by remember { mutableStateOf(settingsRepository.playerVinylBase) }
    var playerBgEnhance by remember { mutableStateOf(settingsRepository.playerBgEnhance) }
    var playerHyperBg by remember { mutableStateOf(settingsRepository.playerHyperBg) }
    var playerFluidAmbientBg by remember { mutableStateOf(settingsRepository.playerFluidAmbientBg) }
    var playerMeshBg by remember { mutableStateOf(settingsRepository.playerMeshBg) }
    // 频谱律动（Mesh）高级参数
    var meshFlowSpeed by remember { mutableFloatStateOf(settingsRepository.meshFlowSpeed) }
    var meshRenderScale by remember { mutableFloatStateOf(settingsRepository.meshRenderScale) }
    var meshSensitivity by remember { mutableFloatStateOf(settingsRepository.meshSensitivity) }
    var meshSubdivision by remember { mutableIntStateOf(settingsRepository.meshSubdivision) }
    var meshStaticMode by remember { mutableStateOf(settingsRepository.meshStaticMode) }
    var meshAnimation by remember { mutableStateOf(settingsRepository.meshAnimation) }
    var playerWaveformSlider by remember { mutableStateOf(settingsRepository.playerWaveformSlider) }
    var playerLyricsWordEffect by remember { mutableStateOf(settingsRepository.playerLyricsWordEffect) }
    var playerMeiloxLyrics by remember { mutableStateOf(settingsRepository.playerMeiloxLyrics) }
    var playerLyricsSeekPreview by remember { mutableStateOf(settingsRepository.playerLyricsSeekPreview) }
    var playerCoverBlurBg by remember { mutableStateOf(settingsRepository.playerCoverBlurBg) }
    var playerCoverBlurAmount by remember { mutableFloatStateOf(settingsRepository.playerCoverBlurAmount) }
    var playerCoverBlurDarken by remember { mutableFloatStateOf(settingsRepository.playerCoverBlurDarken) }
    var playerAudioReactive by remember { mutableStateOf(settingsRepository.playerAudioReactive) }
    var playerLyricBlur by remember { mutableStateOf(settingsRepository.playerLyricBlur) }
    var playerLyricBlurAmount by remember { mutableFloatStateOf(settingsRepository.playerLyricBlurAmount) }
    var playerTapCoverToLyrics by remember { mutableStateOf(settingsRepository.playerTapCoverToLyrics) }
    var playerCompactControls by remember { mutableStateOf(settingsRepository.playerCompactControls) }
    var playerMinimalistControls by remember { mutableStateOf(settingsRepository.playerMinimalistControls) }
    var playerShowTopFavorite by remember { mutableStateOf(settingsRepository.playerShowTopFavorite) }
    var playerLyricFontSize by remember { mutableFloatStateOf(settingsRepository.playerLyricFontSize) }
    var playerLyricFontWeight by remember { mutableFloatStateOf(settingsRepository.playerLyricFontWeight) }
    var playerLyricAlignment by remember { mutableStateOf(settingsRepository.playerLyricAlignment) }
    var playerCoverLyricFontSize by remember { mutableFloatStateOf(settingsRepository.playerCoverLyricFontSize) }
    var playerCoverLyricFontWeight by remember { mutableFloatStateOf(settingsRepository.playerCoverLyricFontWeight) }
    var playerCoverLyricAlignment by remember { mutableStateOf(settingsRepository.playerCoverLyricAlignment) }
    var playbackKeepProgress by remember { mutableStateOf(settingsRepository.playbackKeepProgress) }
    var playbackKeepMode by remember { mutableStateOf(settingsRepository.playbackKeepMode) }
    var playbackBluetoothStop by remember { mutableStateOf(settingsRepository.playbackBluetoothStop) }
    var playbackFadeIn by remember { mutableStateOf(settingsRepository.playbackFadeIn) }
    var playbackFadeInMs by remember { mutableIntStateOf(settingsRepository.playbackFadeInMs) }
    var playbackCrossfadeNext by remember { mutableStateOf(settingsRepository.playbackCrossfadeNext) }
    var playbackCrossfadeInMs by remember { mutableIntStateOf(settingsRepository.playbackCrossfadeInMs) }
    var playbackCrossfadeOutMs by remember { mutableIntStateOf(settingsRepository.playbackCrossfadeOutMs) }
    var playbackVolumeNormalization by remember { mutableStateOf(settingsRepository.playbackVolumeNormalization) }
    var playbackVolumeBalance by remember { mutableFloatStateOf(settingsRepository.playbackVolumeBalance) }
    var playbackHighRes by remember { mutableStateOf(settingsRepository.playbackHighRes) }
    var playbackPreemptFocus by remember { mutableStateOf(settingsRepository.playbackPreemptFocus) }
    var playbackPauseOnOtherApp by remember { mutableStateOf(settingsRepository.playbackPauseOnOtherApp) }
    var backgroundImageUri by remember {
        val saved = settingsRepository.bgImagePath
        mutableStateOf(saved?.let { Uri.fromFile(File(it)).toString() })
    }
    var bgOpacity by remember { mutableFloatStateOf(settingsRepository.bgOpacity) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val destFile = File(context.filesDir, "bg_image.jpg")
            try {
                // 删除旧文件再写入新文件
                destFile.delete()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                backgroundImageUri = Uri.fromFile(destFile).toString() + "?t=${System.currentTimeMillis()}"
                settingsRepository.bgImagePath = destFile.absolutePath
            } catch (_: Exception) {}
        }
    }

    // 收藏状态
    val db = remember { MusicDatabase.getDatabase(context) }
    val collectionDao = remember { db.collectionDao() }
    var isCurrentSongFavorite by remember { mutableStateOf(false) }
    // 开启「收藏到酷狗」时每次增删后 +1，强制重查一次红心状态
    var kugouLikeTick by remember { mutableStateOf(0) }
    LaunchedEffect(currentSong?.filePath, favoriteToKugou, kugouLikeTick) {
        val fp = currentSong?.filePath ?: return@LaunchedEffect
        val isNeteaseSong = NeteaseApi.isNeteasePath(fp)
        val parts = fp.split("|")
        val hash = parts.getOrElse(0) { "" }
        val audioId = parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L
        if (!isNeteaseSong && hash.isBlank()) {
            isCurrentSongFavorite = false
            return@LaunchedEffect
        }
        val key = if (isNeteaseSong) fp else "${hash}|${audioId}"
        if (favoriteToKugou && !isNeteaseSong) {
            // 官方模式：本地「喜欢镜像」先写即亮；酷狗官方喜欢的也亮；本地“我的收藏”忽略
            if (KuGouApi.token.isNotBlank() && KuGouApi.userid.isNotBlank()) {
                val liked = withContext(Dispatchers.IO) { KuGouApi.isSongLikedKugou(key) }
                isCurrentSongFavorite = liked || collectionDao.isLikedSong(key)
            } else {
                isCurrentSongFavorite = collectionDao.isLikedSong(key)
            }
        } else {
            isCurrentSongFavorite = collectionDao.isSongCollected(key)
        }
    }

    var launchUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    // 初始化 API 认证信息和设备注册
    LaunchedEffect(Unit) {
        // 加载自定义服务器地址
        // 历史迁移：曾把指向旧默认服务器的自定义 server_url 清掉，让用户回落 DEFAULT_BASE_URL。
        // 旧服务器地址已不在开源代码中，迁移列表留空保持逻辑兼容。
        val MIGRATION_KEY = "server_url_migrated_v4"
        val OLD_DEAD_URLS = emptyList<String>()
        if (!settingsPrefs.getBoolean(MIGRATION_KEY, false)) {
            val saved = settingsRepository.serverUrl
            if (saved != null && OLD_DEAD_URLS.any { saved.trimEnd('/') == it.trimEnd('/') }) {
                settingsRepository.serverUrl = null
                android.util.Log.i("LxMusic", "已迁移旧服务器地址 $saved -> ${KuGouApi.DEFAULT_BASE_URL}")
            }
            settingsPrefs.edit().putBoolean(MIGRATION_KEY, true).apply()
        }
        val savedServerUrl = settingsRepository.serverUrl
        if (!savedServerUrl.isNullOrBlank()) {
            KuGouApi.baseUrl = savedServerUrl
        }
        // 在线服务：优先用户自定义后端，其次使用编译内置后端；都为空 = 无在线服务
        KuGouApi.bffUrl = settingsPrefs.getString("bff_url", null)
            ?: BuildConfig.LX_BFF_URL.trim()
        if (KuGouApi.bffUrl.isNotBlank()) {
            android.util.Log.d(
                "LxMusic",
                "在线服务已启用（${if (settingsPrefs.getString("bff_url", null).isNullOrBlank()) "内置" else "自定义"}）"
            )
        }
        // 网易云 API：自定义地址优先，否则自动跟随酷狗（同一主机 + 3001 端口）
        val savedNeteaseUrl = settingsPrefs.getString("netease_server_url", null) ?: ""
        NeteaseApi.setCustomServerUrl(savedNeteaseUrl.ifBlank { null })
        NeteaseApi.baseUrl = NeteaseApi.effectiveBaseUrl()
        android.util.Log.d("LxMusic", "网易云 API 地址: ${NeteaseApi.baseUrl}")
        // 恢复搜索音源（酷狗 / 网易云 / 本地 / 插件）；未激活默认「本地」
        searchSource = settingsPrefs.getString("music_source", null)
            ?: if (isGuest) "local" else "kugou"
        // VIP userid 由 ownerToken/ownerUserid 统一处理，不再单独加载
        KuGouApi.vipUserid = ""

        val savedToken = authPrefs.getString("token", "") ?: ""
        val savedUserid = authPrefs.getLong("userid", 0)
        if (savedToken.isNotBlank()) {
            KuGouApi.token = savedToken
            KuGouApi.userid = savedUserid.toString()
        }
        // 加载音质设置
        KuGouApi.audioQuality = settingsRepository.audioQuality

        // 清除旧的 owner_token 系统（已改用手动设置）
        if (!authPrefs.getString("owner_token", "").isNullOrBlank()) {
            authPrefs.edit().remove("owner_token").remove("owner_userid").apply()
        }
        // 加载 VIP 配置（新版：优先使用 VipConfigManager）
        VipConfigManager.applySavedVipToApi(context)
        val vipStatus = VipConfigManager.getStatus(context)
        android.util.Log.d("LxMusic", "VIP 状态: activated=${vipStatus.isActivated}, bound=${vipStatus.isDeviceBound}, canUse=${vipStatus.canUseVip}")

        // 如果已激活且需要自动刷新，在后台刷新 token
        if (VipConfigManager.shouldAutoRefresh(context)) {
            scope.launch(Dispatchers.IO) {
                try {
                    val result = VipConfigManager.refreshToken(context)
                    android.util.Log.d("LxMusic", "自动刷新 VIP token: ${result.getOrElse { it.message ?: "失败" }}")
                } catch (e: Exception) {
                    android.util.Log.e("LxMusic", "自动刷新 VIP token 失败", e)
                }
            }
        }
        // 服务端会话 Cookie 持久化：登录/设备注册时服务器下发的 t1、vip_token 等凭证
        // 存进 prefs，重启后恢复——概念版 /login/token 刷新登录需要登录时的 t1，
        // 只放内存 cookieStore 会随进程重启丢失，导致刷新永远失败（实测 error_code=20018）
        val cookieGson = com.google.gson.Gson()
        var lastCookieJson: String? = null
        KuGouApi.onServerCookiesChanged = { cookies ->
            runCatching {
                val json = cookieGson.toJson(cookies)
                if (json != lastCookieJson) {
                    lastCookieJson = json
                    settingsPrefs.edit().putString("kugou_server_cookies", json).apply()
                }
            }
        }
        // 获取或注册 dfid
        KuGouApi.clearServerCookies()
        // 恢复上次持久化的服务端 Cookie（核心是登录下发的 t1）
        runCatching {
            val saved = settingsPrefs.getString("kugou_server_cookies", null)
            if (!saved.isNullOrBlank()) {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                KuGouApi.restoreServerCookies(cookieGson.fromJson(saved, type))
            }
        }
        try {
            val resp = KuGouApi.service.registerDev()
            val newDfid = resp.data?.dfid
            // 服务器 /register/dev 偶发返回空或 "undefined"（其调酷狗失败时的容错缺陷）：
            // 无效时回退到本地保存的 dfid，避免 Cookie/请求缺失设备标识
            if (!newDfid.isNullOrBlank() && newDfid != "undefined") {
                KuGouApi.dfid = newDfid
                settingsRepository.dfid = newDfid
            } else {
                val savedDfid = settingsRepository.dfid
                if (!savedDfid.isNullOrBlank()) KuGouApi.dfid = savedDfid
            }
        } catch (_: Exception) {
            val savedDfid = settingsRepository.dfid
            if (!savedDfid.isNullOrBlank()) KuGouApi.dfid = savedDfid
        }
        // 登录 token 保活：距上次续期超过 7 天则（后台）调酷狗 /login/token 延长过期时间，
        // 避免用户 token 过期后必须重新验证码登录（酷狗 token 约 1 个月过期）。
        // 必须放在 registerDev 之后：概念版(lite)刷新要用 dfid + 服务器下发的设备指纹
        // cookie（KUGOU_API_GUID/DEV/MAC），提前发会因缺设备参数被拒（实测 502/20018）
        if (LoginTokenKeeper.shouldRefresh(context)) {
            scope.launch(Dispatchers.IO) {
                try {
                    val result = LoginTokenKeeper.refresh(context)
                    android.util.Log.d("LxMusic", "登录 token 保活: ${result.getOrElse { it.message ?: "失败" }}")
                } catch (e: Exception) {
                    android.util.Log.e("LxMusic", "登录 token 保活失败", e)
                }
            }
        }
        playerViewModel.onApiReady()
        android.util.Log.d("LxMusic", "API就绪: dfid=${KuGouApi.dfid}, owner=${KuGouApi.ownerUserid}")

        // 启动时自动检查 GitHub 更新（默认开启）
        if (settingsRepository.autoCheckUpdateDialog) {
            scope.launch(Dispatchers.IO) {
                try {
                    val info = UpdateChecker.checkForUpdate()
                    if (info != null && !UpdateChecker.isVersionIgnored(context, info.versionName)) {
                        withContext(Dispatchers.Main) {
                            launchUpdateInfo = info
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("LxMusic", "启动自动检查更新失败: ${e.message}")
                }
            }
        }
    }

    // 本地音乐排序/定位回调
    // 排行榜 / 歌单详情页的"定位播放"回调（替代全局单例，避免数据竞争）
    var rankLocateAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var playlistLocateAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var playlistMenuAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // 手机返回键：统一的返回处理，替代原先散落的多个 BackHandler
    // 未激活时搜索是顶层 tab：不拦截系统返回，直接退出应用
    val backEnabled = showLoginPage || showRankDetail != null || showPublicPlaylist != null ||
        showPublicAlbum != null || showPlayerPage ||
        showSoundSettingsPage || showEqualizerPage ||
        selectedTab == 3 || selectedTab in 4..7 || selectedTab in 8..10 ||
        (selectedTab == 11 && !isGuest) ||
        selectedTab == 12 || selectedTab == 13 || selectedTab == 14 ||
        selectedTab == 15 || selectedTab == 16 || selectedTab == 17 || selectedTab == 18
    // 展开/收起播放器：页面状态在清动画标记之前提交（同一帧），
    // 避免动画结束到 showPlayerPage 生效之间闪出底栏/迷你播放条
    suspend fun expandPlayer(fromY: Float? = null) {
        isRevealAnimating = true
        try {
            if (fromY != null) playerRevealY.snapTo(fromY)
            playerRevealY.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
            showPlayerPage = true
        } finally {
            isRevealAnimating = false
        }
    }

    suspend fun collapsePlayer() {
        isRevealAnimating = true
        try {
            playerRevealY.animateTo(screenHeightPx, tween(350, easing = FastOutSlowInEasing))
            showPlayerPage = false
        } finally {
            isRevealAnimating = false
        }
    }

    androidx.activity.compose.BackHandler(enabled = backEnabled) {
        when {
            showLoginPage -> showLoginPage = false
            // 播放器菜单弹出的独立页面在最上层：先关页面，再处理播放器
            showEqualizerPage -> showEqualizerPage = false
            showSoundSettingsPage -> showSoundSettingsPage = false
            // 播放器在最上层：先处理播放器，避免一次返回先把下层榜单/歌单关掉
            showPlayerPage -> {
                // 返回键两级：歌词页先回播放页，播放页再收起（带滑出动画）
                if (playerPagerState.currentPage == 1) {
                    scope.launch { playerPagerState.animateScrollToPage(0) }
                } else {
                    scope.launch { collapsePlayer() }
                }
            }
            showPublicAlbum != null -> showPublicAlbum = null
            showPublicPlaylist != null -> showPublicPlaylist = null
            showRankDetail != null -> {
                rankLocateAction = null
                showRankDetail = null
            }
            selectedTab == 3 -> selectedTab = 2
            selectedTab in 4..7 -> selectedTab = 0
            selectedTab == 13 -> selectedTab = 0
            selectedTab == 18 -> selectedTab = 0
            selectedTab in 8..10 -> selectedTab = 2
            selectedTab == 11 -> {
                selectedTab = previousTab
                searchQuery = ""
                searchSelectedType = "song"
            }
            selectedTab == 12 -> selectedTab = 2
            selectedTab == 14 -> selectedTab = 2
            selectedTab == 15 -> selectedTab = 2
            selectedTab == 16 -> selectedTab = 11
            selectedTab == 17 -> selectedTab = previousTab
        }
    }

    // 下一首
    fun playNext() = playerViewModel.playNext()

    // 上一首
    fun playPrevious() = playerViewModel.playPrevious()

    // 实测底栏高度（px），用于迷你播放条贴住底栏（对齐 NeriPlayer 的 bottomBarLayoutInsets）
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navBarInsetPx = WindowInsets.navigationBars.getBottom(density)

    // 迷你播放条底部间距：原生主题下用实测底栏高度（含导航栏 inset 的差值），
    // 保证与底栏严丝合缝；悬浮主题保持胶囊悬浮间距
    val showNavBar = selectedTab in topLevelTabs && showRankDetail == null &&
        showPublicPlaylist == null && showPublicAlbum == null && !showLoginPage
    val miniPlayerBottomPadding by animateDpAsState(
        targetValue = when {
            !showNavBar -> 0.dp
            floatingBottomBar -> 66.dp
            else -> with(density) {
                // 底栏实测总高度减去底部系统导航条 inset，
                // 得到与 MiniPlayerBar 内部 navigationBarsPadding 叠加后正好贴住底栏的偏移
                (bottomBarHeightPx - navBarInsetPx).coerceAtLeast(0).toDp()
            }
        },
        animationSpec = tween(durationMillis = 300),
        label = "miniPlayerBottom"
    )

    // 播放歌曲的回调
    fun playSong(songs: List<SongInfo>, index: Int) = playerViewModel.play(songs, index)

    // 播放在线歌曲 - 设置完整播放列表
    fun playOnlineSong(songs: List<SongInfo>, index: Int) = playerViewModel.playOnline(songs, index)

    // 添加到下一首播放
    fun addToQueueNext(song: SongInfo) = playerViewModel.addToQueueNext(song)

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 主框架底色跟随主题（种子色 / 莫奈取色），背景图存在时由上层图片覆盖
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 背景图片（铺满整个屏幕，包括标题栏区域，始终显示）。
        // 现代主题 + 壁纸时，这一层同时作为顶栏磨砂的采样源：
        // 必须自带不透明基座，否则顶栏渐隐段会透出下层同一张壁纸，出现"重影"。
        val captureTopBarHaze = settingsRepository.themeMode == "modern" && backgroundImageUri != null
        Box(
            modifier = Modifier
                .fillMaxSize()
                // hazeSource 必须在 background 外层：采样层要包含不透明基座
                .then(
                    if (captureTopBarHaze) Modifier.hazeSource(homeTopHazeState, zIndex = 0f)
                    else Modifier
                )
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (backgroundImageUri != null) {
                val painter = rememberAsyncImagePainter(model = Uri.parse(backgroundImageUri))
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().alpha(bgOpacity),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        // 顶层 tab（首页/发现/我的）统一用同向横移 + 交叉淡化，
                        // 并且三段顶栏高度一致（现代 = 状态栏 + 54dp，原生 = 状态栏 + 64dp），
                        // 切换时不会出现高度压缩/内容跳动。
                        val topLevelOrder = if (isGuest) listOf(11, 1, 2) else listOf(0, 1, 2)
                        val fromPos = topLevelOrder.indexOf(initialState)
                        val toPos = topLevelOrder.indexOf(targetState)
                        val isTopLevelSwitch = fromPos >= 0 && toPos >= 0 && fromPos != toPos
                        val isSearch = targetState == 11 || initialState == 11
                        when {
                            isTopLevelSwitch -> {
                                val dir = if (toPos > fromPos) 1 else -1
                                (
                                    fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                                        slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { dir * it / 14 }
                                    ) togetherWith (
                                    fadeOut(tween(150, easing = FastOutSlowInEasing)) +
                                        slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -dir * it / 18 }
                                    )
                            }
                            isSearch -> {
                                (fadeIn(tween(260)) + slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { if (targetState == 11) it / 6 else -it / 6 }) togetherWith
                                    (fadeOut(tween(180)) + slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { if (targetState == 11) -it / 6 else it / 6 })
                            }
                            else -> fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                        }
                    },
                    label = "topBarTransition"
                ) { currentTab ->
                    if (showLoginPage) {
                        // 登录页面不显示标题栏
                    } else if (showRankDetail != null) {
                        val rank = showRankDetail
                        TopAppBar(
                            title = { Text(rank?.rankname ?: stringResource(R.string.title_rank)) },
                            navigationIcon = {
                                IconButton(onClick = { rankLocateAction = null; showRankDetail = null }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                            },
                            actions = {
                                IconButton(onClick = { rankLocateAction?.invoke() }) {
                                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.action_locate))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    } else if (currentTab == 1 || showPublicPlaylist != null || showPublicAlbum != null) {
                        // 发现页 / 公开歌单详情：页面自带顶栏，不再显示额外标题栏。
                        // "发现"顶层 tab 补一个等高的透明占位：三个顶层页面的顶栏槽位高度恒定，
                        // 切换时 AnimatedContent 不会压缩/拉伸顶栏区域。
                        if (currentTab == 1 && showPublicPlaylist == null && showPublicAlbum == null) {
                            val statusTop = androidx.compose.foundation.layout.WindowInsets.statusBars
                                .asPaddingValues()
                                .calculateTopPadding()
                            // 胶囊顶栏在两个主题下都是 54dp，占位高度统一
                            val contentHeight = 54.dp
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(statusTop + contentHeight)
                            )
                        }
                    } else if (currentTab == 3) {
                        // 本地音乐页顶栏（汉堡菜单 + 搜索），与页面面板联动；面板展开时随内容右移
                        Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                            LocalMusicTopBar(
                                onMenuClick = { localShowSidePanel = !localShowSidePanel },
                                onSearchClick = {
                                    previousTab = selectedTab
                                    searchQuery = ""
                                    searchSelectedType = "song"
                                    selectedTab = 11
                                },
                                onLocateClick = { locatePlayingSong++ },
                                isMenuOpen = localShowSidePanel,
                                modifier = Modifier.offset { IntOffset(localPanelOffset.value.roundToInt(), 0) }
                            )
                        }
                    } else if (currentTab == 4) {
                        TopAppBar(
                            title = { Text(stringResource(R.string.title_daily_recommend)) },
                            navigationIcon = {
                                IconButton(onClick = { selectedTab = 0 }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                            },
                            actions = {
                                IconButton(onClick = { playlistLocateAction?.invoke() }) {
                                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.action_locate))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    } else if (currentTab == 5) {
                        TopAppBar(
                            title = { Text(stringResource(R.string.title_vip_recommend)) },
                            navigationIcon = {
                                IconButton(onClick = { selectedTab = 0 }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                            },
                            actions = {
                                IconButton(onClick = { playlistLocateAction?.invoke() }) {
                                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.action_locate))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    } else if (currentTab == 6) {
                        TopAppBar(
                            title = { Text(stringResource(R.string.title_history_recommend)) },
                            navigationIcon = {
                                IconButton(onClick = { selectedTab = 0 }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                            },
                            actions = {
                                IconButton(onClick = { playlistLocateAction?.invoke() }) {
                                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.action_locate))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    } else if (currentTab == 7) {
                        TopAppBar(
                            title = { Text(stringResource(R.string.title_style_recommend)) },
                            navigationIcon = {
                                IconButton(onClick = { selectedTab = 0 }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                            },
                            actions = {
                                IconButton(onClick = { playlistLocateAction?.invoke() }) {
                                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.action_locate))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    } else if (currentTab == 13) {
                        TopAppBar(
                            title = { Text("百万收藏") },
                            navigationIcon = {
                                IconButton(onClick = { selectedTab = 0 }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                            },
                            actions = {
                                IconButton(onClick = { playlistLocateAction?.invoke() }) {
                                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.action_locate))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    } else if (currentTab == 18) {
                        TopAppBar(
                            title = { Text("热歌推荐") },
                            navigationIcon = {
                                IconButton(onClick = { selectedTab = 0 }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                            },
                            actions = {
                                IconButton(onClick = { playlistLocateAction?.invoke() }) {
                                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.action_locate))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    } else if (currentTab in 8..10) {
                        val playlistName = when (currentTab) {
                            8 -> stringResource(R.string.title_default_favorites)
                            9 -> stringResource(R.string.title_liked)
                            else -> selectedPlaylist?.listname ?: stringResource(R.string.title_playlist_detail)
                        }
                        TopAppBar(
                            title = { Text(playlistName) },
                            navigationIcon = {
                                IconButton(onClick = { selectedTab = 2 }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                            },
                            actions = {
                                IconButton(onClick = { playlistLocateAction?.invoke() }) {
                                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.action_locate))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    } else if (currentTab == 14) {
                        TopAppBar(
                            title = {
                                Text(
                                    if (selectedCollectionType == "favorites") stringResource(R.string.title_my_favorites)
                                    else if (selectedCollectionType == "liked") "我喜欢的"
                                    else stringResource(R.string.title_playlist_detail)
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { selectedTab = 2 }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                            },
                            actions = {
                                IconButton(onClick = { playlistLocateAction?.invoke() }) {
                                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.action_locate))
                                }
                                if (selectedCollectionType == "playlist") {
                                    IconButton(onClick = { playlistMenuAction?.invoke() }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    } else if (currentTab == 11) {
                        SearchTopBar(
                            onBack = {
                                selectedTab = previousTab
                                searchQuery = ""
                                searchSelectedType = "song"
                            },
                            onSearch = { query -> searchQuery = query },
                            externalQuery = searchQuery,
                            searchSource = searchSource,
                            onSourceChange = { source ->
                                searchSource = source
                                settingsPrefs.edit().putString("music_source", source).apply()
                            },
                            sources = guestSearchSources,
                            // 未激活时搜索是顶层 tab：无返回箭头、不自动弹键盘、不请求在线搜索建议
                            showBack = !isGuest,
                            autoFocus = !isGuest,
                            suggestionsEnabled = !isGuest
                        )
                    } else if (currentTab == 12) {
                        TopAppBar(
                            title = {
                                if (settingsSubPage == null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.title_settings),
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        val isDark = when (themeDarkMode) {
                                            "light" -> false
                                            "dark" -> true
                                            else -> isSystemInDarkTheme()
                                        }
                                        ThemeModeToggleButton(
                                            isDark = isDark,
                                            enabled = !isThemeRevealing,
                                            onClick = { origin, startRadius ->
                                                if (isThemeRevealing) return@ThemeModeToggleButton
                                                isThemeRevealing = true
                                                val activity = context as? android.app.Activity
                                                val nextMode = when (themeDarkMode) {
                                                    "auto" -> if (isDark) "light" else "dark"
                                                    "light" -> "dark"
                                                    "dark" -> "auto"
                                                    else -> "auto"
                                                }

                                                scope.launch {
                                                    val snapshot = activity?.let { captureActivitySnapshot(it) }
                                                    themeRevealSnapshot = snapshot
                                                    themeRevealOrigin = origin
                                                    themeRevealStartRadius = startRadius
                                                    themeRevealFallbackColor = currentBgColor
                                                    onThemeDarkModeChange(nextMode)
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    Text(
                                        text = when (settingsSubPage) {
                                            "appearance", "display", "customize" -> "外观与个性化"
                                            "player_stage", "player", "motion" -> "播放界面与动效"
                                            "playback", "common" -> "声音与播放"
                                            "usb" -> "USB 独占模式"
                                            "general" -> "通用与交互"
                                            "storage" -> "存储与缓存"
                                            "proxy" -> "激活与在线服务"
                                            "plugins" -> "音乐源插件"
                                            "about" -> "关于应用"
                                            "home_order" -> "首页推荐排序"
                                            else -> stringResource(R.string.title_settings)
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            navigationIcon = {
                                if (settingsSubPage != null) {
                                    IconButton(onClick = {
                                        settingsSubPage = null
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    } else if (currentTab == 15) {
                        TopAppBar(
                            title = { Text(stringResource(R.string.title_manage_playlists)) },
                            navigationIcon = {
                                IconButton(onClick = { selectedTab = 2 }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    } else if (currentTab == 16) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = searchPlaylistName.ifBlank { stringResource(R.string.title_playlist_detail) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { selectedTab = 11 }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                            },
                            actions = {
                                IconButton(onClick = { playlistLocateAction?.invoke() }) {
                                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.action_locate))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    } else if (currentTab == 17) {
                        // 管理歌单页顶栏（与歌单详情页一致：标题 + 返回）
                        TopAppBar(
                            title = { Text("管理歌单") },
                            navigationIcon = {
                                IconButton(onClick = { selectedTab = previousTab }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    } else if (currentTab == 0) {
                        // 首页悬浮顶栏（灵动双胶囊）：现代 / 原生两个主题都用，
                        // 不再只在现代化主题下生效
                        var homePlayMode by remember { mutableIntStateOf(settingsRepository.homePlayMode) }
                        val isThisPlaying = isPlaying && currentSong != null
                        val hasSongs = homeAllSongs.isNotEmpty()
                        val homeScrollProgress by remember {
                            derivedStateOf<Float> {
                                if (homeListState.firstVisibleItemIndex > 0) 1f
                                else (homeListState.firstVisibleItemScrollOffset / 180f).coerceIn(0f, 1f)
                            }
                        }
                        val animatedHomeScrollProgress by animateFloatAsState(
                            targetValue = homeScrollProgress,
                            animationSpec = tween(180),
                            label = "home_scroll_progress"
                        )
                        ModernHomeTopBar(
                            homePlayMode = homePlayMode,
                            onModeChange = { mode ->
                                homePlayMode = mode
                                settingsRepository.homePlayMode = mode
                                if (hasSongs) {
                                    if (mode == 0) playOnlineSong(homeAllSongs, 0)
                                    else playOnlineSong(homeAllSongs.shuffled(), 0)
                                }
                            },
                            isThisPlaying = isThisPlaying,
                            hasSongs = hasSongs,
                            onPlayAll = {
                                if (hasSongs) {
                                    if (homePlayMode == 0) {
                                        playOnlineSong(homeAllSongs, 0)
                                    } else {
                                        playOnlineSong(homeAllSongs.shuffled(), 0)
                                    }
                                }
                            },
                            onRefresh = { homeClickRefresh?.invoke() },
                            isRefreshing = isHomeRefreshing,
                            onSearch = {
                                previousTab = selectedTab
                                searchQuery = ""
                                searchSelectedType = "song"
                                selectedTab = 11
                            },
                            backdrop = glassBackdrop,
                            liquidGlass = liquidGlass,
                            hasCustomWallpaper = backgroundImageUri != null,
                            hazeState = homeTopHazeState,
                            scrollProgress = animatedHomeScrollProgress
                        )
                    } else if (currentTab == 2) {
                        // 「我的」页悬浮顶栏（左侧固定标题 + 右侧圆形设置键）：两个主题通用
                        // 现代主题「我的」页：左侧固定「我的」标题 + 右侧圆形设置按键
                        ModernMineTopBar(
                            onSettings = { selectedTab = 12 },
                            backdrop = glassBackdrop,
                            liquidGlass = liquidGlass,
                            hasCustomWallpaper = backgroundImageUri != null,
                            hazeState = homeTopHazeState
                        )
                    } else {
                        TopAppBar(
                            title = {
                                if (currentTab == 0) {
                                    var homePlayMode by remember { mutableIntStateOf(settingsRepository.homePlayMode) }
                                    var showModeSheet by remember { mutableStateOf(false) }
                                    val modeLabel = when (homePlayMode) { 1 -> "随机"; 2 -> "心动"; else -> "顺序" }
                                    val isThisPlaying = isPlaying && currentSong != null
                                    // 胶囊常驻：首帧即显示，不再依赖 homeAllSongs 是否就绪，
                                    // 避免冷启动时先闪一下“Lx Music”再跳成胶囊。数据未就绪时置灰不可点。
                                    val hasSongs = homeAllSongs.isNotEmpty()
                                    // 一体式液态胶囊：模式名 + 播放键 + 切换键（播放行为与原来完全一致）
                                    // 点模式名/图标都是按当前模式从头开播，图标只反映播放中/暂停状态
                                    val doPlayAll = {
                                        if (hasSongs) {
                                            if (homePlayMode == 0) {
                                                playOnlineSong(homeAllSongs, 0)
                                            } else {
                                                playOnlineSong(homeAllSongs.shuffled(), 0)
                                            }
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f))
                                            .alpha(if (hasSongs) 1f else 0.45f)
                                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                    ) {
                                        // 左侧固定槽：律动条/音符都在 20dp 里居中，切换不改变胶囊尺寸
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            HomePlayEqIcon(playing = isThisPlaying)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = modeLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { doPlayAll() }
                                                .padding(vertical = 6.dp)
                                        )
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { doPlayAll() }
                                        ) {
                                            Icon(
                                                if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isThisPlaying) "暂停" else stringResource(R.string.action_play_all),
                                                modifier = Modifier.size(22.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(18.dp)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                        )
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { showModeSheet = true }
                                        ) {
                                            Icon(
                                                Icons.Default.ExpandMore,
                                                contentDescription = "切换播放模式",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    // 3 种模式切换页：点选即保存（settingsRepository 持久化）
                                    if (showModeSheet) {
                                        ModalBottomSheet(
                                            onDismissRequest = { showModeSheet = false },
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                                        ) {
                                            Text(
                                                text = "一键播放模式",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                            )
                                            val modes = listOf(
                                                Triple(Icons.Default.QueueMusic, "顺序", "按当前列表顺序播放"),
                                                Triple(Icons.Default.Shuffle, "随机", "打乱列表顺序播放"),
                                                Triple(Icons.Default.Favorite, "心动", "随机播放，遇见心动的歌")
                                            )
                                            modes.forEachIndexed { mode, (icon, label, desc) ->
                                                val isSelected = homePlayMode == mode
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                            else Color.Transparent
                                                        )
                                                            .clickable {
                                                                homePlayMode = mode
                                                                settingsRepository.homePlayMode = mode
                                                                showModeSheet = false
                                                                // 切完即按新模式开播（无数据时只保存模式，不开播）
                                                                if (!hasSongs) return@clickable
                                                                if (mode == 0) playOnlineSong(homeAllSongs, 0)
                                                                else playOnlineSong(homeAllSongs.shuffled(), 0)
                                                            }
                                                        .padding(12.dp)
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier.fillMaxSize()
                                                        ) {
                                                            Icon(
                                                                icon,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(22.dp),
                                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            fontWeight = FontWeight.Medium,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = desc,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    if (isSelected) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(28.dp))
                                        }
                                    }
                                } else if (currentTab == 2) {
                                    // 「我的」标题固定，不随状态变化
                                    Text(
                                        text = "我的",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            actions = {
                                if (currentTab == 0) {
                                    IconButton(
                                        onClick = { homeClickRefresh?.invoke() },
                                        enabled = !isHomeRefreshing
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                                    }
                                }
                                // 未激活时搜索已是最左侧顶层 tab，不再在顶栏重复放搜索入口
                                // （「我的」页只保留设置齿轮，标题固定为「我的」）
                                if (!isGuest && currentTab != 2) {
                                    IconButton(onClick = {
                                        previousTab = selectedTab
                                        searchQuery = ""
                                        searchSelectedType = "song"
                                        selectedTab = 11
                                    }) {
                                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.title_search))
                                    }
                                }
                                if (currentTab == 2) {
                                    IconButton(onClick = { selectedTab = 12 }) {
                                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.title_settings))
                                    }
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {

            // 液态玻璃开启时，在捕获层内垫一层不透明背景，让玻璃条下方始终有可模糊的内容
            // （对齐库示例 BackdropDemoScaffold 把壁纸放进 layerBackdrop）。
            // 关闭时不绘制，保持原有透明逻辑完全不变。
            if (liquidGlass) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
                if (backgroundImageUri != null) {
                    val glassBgPainter = rememberAsyncImagePainter(model = Uri.parse(backgroundImageUri))
                    Image(
                        painter = glassBgPainter,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().alpha(bgOpacity),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            val isModernTheme = settingsRepository.themeMode == "modern"
            val layoutDirection = LocalLayoutDirection.current
            // 有背景图时：顶栏不做任何底色遮罩，直接对内容层做「歌词同款」DstIn 渐隐
            // （顶部+底部），渐隐后露出壁纸，与播放器歌词边缘渐隐露背景一致，不会发白。
            val density = LocalDensity.current
            val fadeBarPx = with(density) { innerPadding.calculateTopPadding().toPx() }
            val fadeTailPx = with(density) { TopBarFadeTailHeight.toPx() }
            val fadeBottomPx = with(density) { 150.dp.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        end = innerPadding.calculateEndPadding(layoutDirection),
                        bottom = innerPadding.calculateBottomPadding()
                    ),
                contentAlignment = Alignment.Center
            ) {

                // 始终显示标签页内容（保持 Composition 不销毁，避免搜索返回后图片闪烁）
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        // 顶层 tab（首页/发现/我的）用轻量横移 + 交叉淡化，方向与顶栏一致、
                        // 时长对齐，避免"内容整屏滑、顶栏只渐隐"的割裂感；其余页面保持整页滑动。
                        val topLevelOrder = if (isGuest) listOf(11, 1, 2) else listOf(0, 1, 2)
                        val fromPos = topLevelOrder.indexOf(initialState)
                        val toPos = topLevelOrder.indexOf(targetState)
                        val isTopLevelSwitch = fromPos >= 0 && toPos >= 0 && fromPos != toPos
                        if (isTopLevelSwitch) {
                            val forward = toPos > fromPos
                            if (forward) {
                                slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 5 } +
                                    fadeIn(tween(240)) togetherWith
                                    slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 12 } +
                                    fadeOut(tween(180))
                            } else {
                                slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 5 } +
                                    fadeIn(tween(240)) togetherWith
                                    slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 12 } +
                                    fadeOut(tween(180))
                            }
                        } else {
                            val forward = if (isGuest && fromPos >= 0 && toPos >= 0) toPos > fromPos
                                else targetState > initialState
                            if (forward) {
                                slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) togetherWith
                                    slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(200))
                            } else {
                                slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300)) togetherWith
                                    slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(200))
                            }
                        }
                    },
                    label = "tabTransition"
                ) { tab ->
                    // 横屏：页面内容限宽居中（列表行不被拉宽变形）；顶栏/底栏/播放条仍在 Scaffold 层保持全宽
                    val isThisModernHome = tab == 0
                    // 「我的」页：顶栏只是悬浮胶囊（内容从下方穿过，与首页同一套观感），
                    // 不再把内容整体压到顶栏下面。胶囊顶栏两个主题通用。
                    val isModernMine = tab == 2
                    // 发现页列表自带顶栏，Scaffold 顶栏槽位只是等高占位 → 内容仍从屏幕顶部开始；
                    // 榜单详情打开时顶栏是 Scaffold 里的真实 TopAppBar，仍需按 innerPadding 让位。
                    val isDiscoverList = tab == 1 && showRankDetail == null &&
                        showPublicPlaylist == null && showPublicAlbum == null
                    val tabTopPadding = if (isThisModernHome || isModernMine || isDiscoverList) 0.dp
                        else innerPadding.calculateTopPadding()
                    // 悬浮顶栏时首屏内容的下移量（滚动后内容从顶栏下方穿过）——胶囊顶栏两主题通用
                    val floatingTopContentPadding = innerPadding.calculateTopPadding()
                    // 内容层顶部（首页/我的）与底部（显示底栏时）做歌词同款渐隐。
                    // **不再区分有没有壁纸**：无壁纸时渐隐到背景色，有壁纸时露出壁纸，
                    // 保证两种状态下顶栏/底栏的渐变观感一致。
                    val wallpaperMask = run {
                        val topFade = if (tab == 0 || tab == 2) fadeBarPx else 0f
                        val bottomFade = if (showNavBar && !showPlayerPage) fadeBottomPx else 0f
                        if (topFade > 0f || bottomFade > 0f) {
                            Modifier.lyricEdgeFadeMask(
                                topBarPx = topFade,
                                topTailPx = if (topFade > 0f) fadeTailPx else 0f,
                                bottomFadePx = bottomFade
                            )
                        } else Modifier
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = if (isLandscape) 640.dp else Dp.Infinity)
                            .align(Alignment.TopCenter)
                            .then(wallpaperMask)
                            .padding(top = tabTopPadding)
                    ) {
                    when (tab) {
                        0 -> {
                            // 保存滚动位置
                            LaunchedEffect(homeListState.firstVisibleItemIndex, homeListState.firstVisibleItemScrollOffset) {
                                homeScrollIndex = homeListState.firstVisibleItemIndex
                                homeScrollOffset = homeListState.firstVisibleItemScrollOffset
                            }
                            LaunchedEffect(homeRecommendListState.firstVisibleItemIndex, homeRecommendListState.firstVisibleItemScrollOffset) {
                                homeRecommendIndex = homeRecommendListState.firstVisibleItemIndex
                                homeRecommendOffset = homeRecommendListState.firstVisibleItemScrollOffset
                            }
                            HomePage(
                                topContentPadding = innerPadding.calculateTopPadding(),
                                onPlaySong = { songs, index -> playOnlineSong(songs, index) },
                                onAddToQueueNext = { song -> addToQueueNext(song) },
                                onRankClick = { showRankDetail = it },
                                onDailyClick = { songs ->
                                    dailySongsForList = songs
                                    selectedTab = 4
                                },
                                onVipClick = { songs ->
                                    vipSongsForList = songs
                                    selectedTab = 5
                                },
                                onMillionClick = { songs ->
                                    millionSongsForList = songs
                                    selectedTab = 13
                                },
                                onNetworkClick = { songs ->
                                    networkSongsForList = songs
                                    selectedTab = 18
                                },
                                onHistoryClick = { songs ->
                                    historySongsForList = songs
                                    selectedTab = 6
                                },
                                onStyleClick = { songs ->
                                    styleSongsForList = songs
                                    selectedTab = 7
                                },
                                currentPlayingPath = currentSong?.filePath,
                                isPlaying = isPlaying,
                                onAllSongsReady = { homeAllSongs = it },
                                listState = homeListState,
                                recommendListState = homeRecommendListState,
                                onClickRefresh = { homeClickRefresh = it },
                                onRefreshStateChange = { isHomeRefreshing = it },
                                hazeState = if (isModernTheme && backgroundImageUri != null) homeTopHazeState else null,
                                loginVersion = loginVersion
                            )
                        }
                        1 -> {
                            LaunchedEffect(discoverListState.firstVisibleItemIndex, discoverListState.firstVisibleItemScrollOffset) {
                                discoverScrollIndex = discoverListState.firstVisibleItemIndex
                                discoverScrollOffset = discoverListState.firstVisibleItemScrollOffset
                            }
                            LaunchedEffect(discoverRecommendListState.firstVisibleItemIndex, discoverRecommendListState.firstVisibleItemScrollOffset) {
                                discoverRecommendScrollIndex = discoverRecommendListState.firstVisibleItemIndex
                                discoverRecommendScrollOffset = discoverRecommendListState.firstVisibleItemScrollOffset
                            }
                            AnimatedContent(
                                targetState = showRankDetail,
                                transitionSpec = {
                                    slideInHorizontally(tween(350)) { it } + fadeIn(tween(300)) togetherWith
                                        slideOutHorizontally(tween(350)) { it / 3 } + fadeOut(tween(200))
                                },
                                label = "rankDetailTransition"
                            ) { rank ->
                                if (rank != null) {
                                    RankDetailPage(
                                        rank = rank,
                                        onBack = {
                                            rankLocateAction = null
                                            showRankDetail = null
                                        },
                                        onPlaySong = { songs, index -> playOnlineSong(songs, index) },
                                        currentPlayingPath = currentSong?.filePath,
                                        isPlaying = isPlaying,
                                        onLocateReady = { rankLocateAction = it },
                                        onAddToQueueNext = { song -> addToQueueNext(song) }
                                    )
                                } else {
                                    DiscoverPage(
                                        onRankClick = { showRankDetail = it },
                                        onPlaySong = { songs, index -> playOnlineSong(songs, index) },
                                        onPlaylistClick = { playlist ->
                                            searchPlaylistId = playlist.specialid
                                            searchPlaylistName = playlist.specialname ?: "未知歌单"
                                            searchPlaylistCover = playlist.coverUrl
                                            searchPlaylistAuthor = playlist.nickname ?: ""
                                            searchPlaylistGid = playlist.gid ?: ""
                                            previousTab = selectedTab
                                            selectedTab = 16
                                        },
                                        onPublicPlaylistClick = { showPublicPlaylist = it },
                                        currentPlayingPath = currentSong?.filePath,
                                        isPlaying = isPlaying,
                                        onAddToQueueNext = { song -> addToQueueNext(song) },
                                        listState = discoverListState,
                                        recommendListState = discoverRecommendListState,
                                        // 乐库需要在线服务，未激活隐藏（排行榜/歌单/专区为公开接口）
                                        showYueku = !isGuest,
                                        // 顶栏高度与首页/我的统一：胶囊顶栏两主题都是 54dp
                                        topBarContentHeight = 54.dp,
                                        modernTopBar = true
                                    )
                                }
                            }
                        }
                        2 -> if (isGuest) {
                            GuestMinePage(
                                onFavoritesClick = {
                                    selectedCollectionType = "favorites"
                                    selectedCollectionPlaylistId = 0L
                                    selectedTab = 14
                                },
                                onLocalClick = { selectedTab = 3 },
                                topContentPadding = floatingTopContentPadding
                            )
                        } else MinePage(
                            loginVersion = loginVersion,
                            onLocalClick = { selectedTab = 3 },
                            onAvatarClick = { showLoginPage = true },
                            onManagePlaylists = { selectedTab = 15 },
                            onManageKugouPlaylists = {
                                previousTab = selectedTab
                                selectedTab = 17
                            },
                            onPlaylistDetailClick = { playlist ->
                                selectedPlaylist = playlist
                                selectedTab = when {
                                    playlist.listname == "默认收藏" -> 8
                                    playlist.listname == "我喜欢" ->
                                        if (favoriteToKugou) {
                                            // 官方模式：我喜欢的列表走本地「喜欢镜像」（读 liked_songs）
                                            selectedCollectionType = "liked"
                                            14
                                        } else 9
                                    else -> 10
                                }
                            },
                            onCollectionDetailClick = { type, id ->
                                selectedCollectionType = type
                                selectedCollectionPlaylistId = id
                                selectedTab = 14
                            },
                            listState = mineListState,
                            initialScrollOffset = mineScrollY,
                            topContentPadding = floatingTopContentPadding
                        )
                        3 -> LocalMusicPage(
                            currentPlayingPath = currentPlayingPath,
                            isPlaying = isPlaying,
                            onPlaySong = { songs, index -> playSong(songs, index) },
                            onAddToQueueNext = { song -> addToQueueNext(song) },
                            showSidePanel = localShowSidePanel,
                            onShowSidePanelChange = { localShowSidePanel = it },
                            panelOffset = localPanelOffset,
                            panelWidthPx = localPanelWidthPx,
                            scanTrigger = localScanTrigger,
                            locatePlayingSong = locatePlayingSong
                        )
                        4 -> {
                            val songInfos = remember(dailySongsForList) {
                                dailySongsForList.map { song ->
                                    SongInfo(
                                        title = song.title,
                                        artist = song.artist,
                                        filePath = "${song.hash}|${song.album_audio_id}",
                                        albumArtUri = song.coverUrl,
                                        duration = song.durationMs
                                    )
                                }
                            }
                            SongListPage(
                                title = stringResource(R.string.title_daily_recommend),
                                songs = songInfos,
                                onBack = { selectedTab = 0 },
                                onPlaySong = { songs, index -> playOnlineSong(songs, index) },
                                currentPlayingPath = currentSong?.filePath,
                                isPlaying = isPlaying,
                                onLocateReady = { playlistLocateAction = it },
                                onAddToQueueNext = { song -> addToQueueNext(song) }
                            )
                        }
                        5 -> SongListPage(
                            title = stringResource(R.string.title_vip_recommend),
                            songs = vipSongsForList,
                            onBack = { selectedTab = 0 },
                            onPlaySong = { songs, index -> playOnlineSong(songs, index) },
                            currentPlayingPath = currentSong?.filePath,
                            isPlaying = isPlaying,
                            onLocateReady = { playlistLocateAction = it },
                            onAddToQueueNext = { song -> addToQueueNext(song) }
                        )
                        6 -> {
                            val songInfos = remember(historySongsForList) {
                                historySongsForList.map { song ->
                                    SongInfo(
                                        title = song.title,
                                        artist = song.artist,
                                        filePath = "${song.hash}|${song.album_audio_id}",
                                        albumArtUri = song.coverUrl,
                                        duration = song.durationMs
                                    )
                                }
                            }
                            SongListPage(
                                title = stringResource(R.string.title_history_recommend),
                                songs = songInfos,
                                onBack = { selectedTab = 0 },
                                onPlaySong = { songs, index -> playOnlineSong(songs, index) },
                                currentPlayingPath = currentSong?.filePath,
                                isPlaying = isPlaying,
                                onLocateReady = { playlistLocateAction = it },
                                onAddToQueueNext = { song -> addToQueueNext(song) }
                            )
                        }
                        7 -> {
                            val songInfos = remember(styleSongsForList) {
                                styleSongsForList.map { song ->
                                    SongInfo(
                                        title = song.title,
                                        artist = song.artist,
                                        filePath = "${song.hash}|${song.album_audio_id}",
                                        albumArtUri = song.coverUrl,
                                        duration = song.durationMs
                                    )
                                }
                            }
                            SongListPage(
                                title = stringResource(R.string.title_style_recommend),
                                songs = songInfos,
                                onBack = { selectedTab = 0 },
                                onPlaySong = { songs, index -> playOnlineSong(songs, index) },
                                currentPlayingPath = currentSong?.filePath,
                                isPlaying = isPlaying,
                                onLocateReady = { playlistLocateAction = it },
                                onAddToQueueNext = { song -> addToQueueNext(song) }
                            )
                        }
                        13 -> SongListPage(
                            title = "百万收藏",
                            songs = millionSongsForList,
                            onBack = { selectedTab = 0 },
                            onPlaySong = { songs, index -> playOnlineSong(songs, index) },
                            currentPlayingPath = currentSong?.filePath,
                            isPlaying = isPlaying,
                            onLocateReady = { playlistLocateAction = it },
                            onAddToQueueNext = { song -> addToQueueNext(song) }
                        )
                        18 -> SongListPage(
                            title = "热歌推荐",
                            songs = networkSongsForList,
                            onBack = { selectedTab = 0 },
                            onPlaySong = { songs, index -> playOnlineSong(songs, index) },
                            currentPlayingPath = currentSong?.filePath,
                            isPlaying = isPlaying,
                            onLocateReady = { playlistLocateAction = it },
                            onAddToQueueNext = { song -> addToQueueNext(song) }
                        )
                        8, 9, 10 -> {                            val playlist = when (tab) {
                                8 -> selectedPlaylist
                                9 -> selectedPlaylist
                                else -> selectedPlaylist
                            }
                            if (playlist != null) {
                                PlaylistDetailPage(
                                    playlist = playlist,
                                    onBack = { selectedTab = 2 },
                                    onPlaySong = { songs, index -> playOnlineSong(songs, index) },
                                    currentPlayingPath = currentSong?.filePath,
                                    isPlaying = isPlaying,
                                    onLocateReady = { playlistLocateAction = it },
                                    onAddToQueueNext = { song -> addToQueueNext(song) },
                                    onSelectionModeChange = { isSelectionModeActive = it },
                                    listState = playlistDetailListState
                                )
                            }
                        }
                        14 -> CollectionDetailPage(
                            type = selectedCollectionType,
                            playlistId = selectedCollectionPlaylistId,
                            onBack = { selectedTab = 2 },
                            onPlaySong = { songs, index -> playOnlineSong(songs, index) },
                            currentPlayingPath = currentSong?.filePath,
                            isPlaying = isPlaying,
                            onLocateReady = { playlistLocateAction = it },
                            onMenuReady = { playlistMenuAction = it },
                            onAddToQueueNext = { song -> addToQueueNext(song) },
                            onSelectionModeChange = { isSelectionModeActive = it }
                        )
                        15 -> PlaylistManagerPage(
                            onBack = { selectedTab = 2 }
                        )
                        11 -> SearchPage(
                            initialQuery = searchQuery,
                            initialSelectedType = searchSelectedType,
                            onBack = {
                                selectedTab = previousTab
                                searchQuery = ""
                                searchSelectedType = "song"
                            },
                            onPlaySong = { songs, index -> playOnlineSong(songs, index) },
                            onPlaylistClick = { playlist ->
                                searchPlaylistId = playlist.specialid
                                searchPlaylistName = playlist.specialname ?: "未知歌单"
                                searchPlaylistCover = playlist.coverUrl
                                searchPlaylistAuthor = playlist.nickname ?: ""
                                searchPlaylistGid = playlist.gid ?: ""
                                selectedTab = 16
                            },
                            onPublicPlaylistClick = { showPublicPlaylist = it },
                            onAlbumClick = { showPublicAlbum = it },
                            onQueryChange = { query -> searchQuery = query },
                            onSelectedTypeChange = { type -> searchSelectedType = type },
                            currentPlayingPath = currentSong?.filePath,
                            isPlaying = isPlaying,
                            onAddToQueueNext = { song -> addToQueueNext(song) },
                            initialSource = searchSource,
                            onSourceChange = { source ->
                                searchSource = source
                                settingsPrefs.edit().putString("music_source", source).apply()
                            },
                            sourceOptions = guestSearchSources,
                            guestMode = isGuest
                        )
                        16 -> SearchPlaylistDetailPage(
                            playlistId = searchPlaylistId,
                            playlistName = searchPlaylistName,
                            coverUrl = searchPlaylistCover,
                            authorName = searchPlaylistAuthor,
                            gid = searchPlaylistGid,
                            onBack = { selectedTab = 11 },
                            onPlaySong = { songs, index -> playOnlineSong(songs, index) },
                            currentPlayingPath = currentSong?.filePath,
                            isPlaying = isPlaying,
                            onLocateReady = { playlistLocateAction = it },
                            onAddToQueueNext = { song -> addToQueueNext(song) },
                            onSelectionModeChange = { isSelectionModeActive = it }
                        )
                        17 -> PlaylistManagePage(
                            onBack = { selectedTab = previousTab }
                        )
                        12 -> SettingsPage(
                            currentUri = backgroundImageUri,
                            bgOpacity = bgOpacity,
                            onPickImage = { imagePickerLauncher.launch("image/*") },
                            onOpacityChange = { bgOpacity = it; settingsRepository.bgOpacity = it },
                            onReset = {
                                File(context.filesDir, "bg_image.jpg").delete()
                                backgroundImageUri = null
                                settingsRepository.bgImagePath = null
                                settingsRepository.bgOpacity = 0.5f
                                bgOpacity = 0.5f
                            },
                            onLogout = { selectedTab = 2 },
                            onDynamicColorChange = onDynamicColorChange,
                            onThemeColorChange = onThemeColorChange,
                            onBackgroundImageChange = { presetImageUri ->
                                if (presetImageUri != null) {
                                    // 应用预设的背景图片
                                    try {
                                        val destFile = File(context.filesDir, "bg_image.jpg")
                                        
                                        // 判断是内部存储绝对路径还是content:// URI
                                        if (presetImageUri.startsWith("/")) {
                                            // 内部存储绝对路径，直接复制文件
                                            File(presetImageUri).inputStream().use { input ->
                                                destFile.outputStream().use { output -> input.copyTo(output) }
                                            }
                                        } else {
                                            // content:// URI，通过contentResolver读取
                                            val sourceUri = Uri.parse(presetImageUri)
                                            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                                                destFile.outputStream().use { output -> input.copyTo(output) }
                                            }
                                        }
                                        backgroundImageUri = Uri.fromFile(destFile).toString() + "?t=${System.currentTimeMillis()}"
                                        settingsRepository.bgImagePath = destFile.absolutePath
                                    } catch (e: Exception) {
                                        android.util.Log.e("LxMusic", "apply background image failed", e)
                                    }
                                } else {
                                    // 清除背景图片
                                    File(context.filesDir, "bg_image.jpg").delete()
                                    backgroundImageUri = null
                                    settingsRepository.bgImagePath = null
                                }
                            },
                            floatingBottomBar = floatingBottomBar,
                            onFloatingBottomBarChange = { enabled ->
                                floatingBottomBar = enabled
                                settingsRepository.floatingBottomBar = enabled
                            },
                            liquidGlass = liquidGlass,
                            onLiquidGlassChange = { enabled ->
                                liquidGlass = enabled
                                liquidGlassSettings.saveEnabled(enabled)
                                settingsRepository.liquidGlass = enabled
                            },
                            navBarOpacity = navBarOpacity,
                            onNavBarOpacityChange = { value ->
                                navBarOpacity = value
                                settingsRepository.navBarOpacity = value
                            },
                            playerBarOpacity = playerBarOpacity,
                            onPlayerBarOpacityChange = { value ->
                                playerBarOpacity = value
                                settingsRepository.playerBarOpacity = value
                            },
                            followThemeColor = followThemeColor,
                            onFollowThemeColorChange = { enabled ->
                                followThemeColor = enabled
                                settingsRepository.followThemeColor = enabled
                            },
                            playerBarWhiteBlend = playerBarWhiteBlend,
                            onPlayerBarWhiteBlendChange = { value ->
                                playerBarWhiteBlend = value
                                settingsRepository.playerBarWhiteBlend = value
                            },
                            floatingBarOpacity = floatingBarOpacity,
                            onFloatingBarOpacityChange = { value ->
                                floatingBarOpacity = value
                                settingsRepository.floatingBarOpacity = value
                            },
                            playerBgOpacity = playerBgOpacity,
                            onPlayerBgOpacityChange = { value ->
                                playerBgOpacity = value
                                settingsRepository.playerBgOpacity = value
                            },
                            playerBlur = playerBlur,
                            onPlayerBlurChange = { enabled ->
                                playerBlur = enabled
                                settingsRepository.playerBlur = enabled
                            },
                            playerDynamicBg = playerDynamicBg,
                            onPlayerDynamicBgChange = { enabled ->
                                playerDynamicBg = enabled
                                settingsRepository.playerDynamicBg = enabled
                            },
                            playerRoundAlbum = playerRoundAlbum,
                            onPlayerRoundAlbumChange = { enabled ->
                                playerRoundAlbum = enabled
                                settingsRepository.playerRoundAlbum = enabled
                                if (enabled) {
                                    // 开启圆形封面时默认连同开启播放旋转（用户仍可手动关闭旋转）
                                    playerRotate = true
                                    settingsRepository.playerRotate = true
                                } else {
                                    playerRotate = false
                                    settingsRepository.playerRotate = false
                                    playerVinylStyle = false
                                    settingsRepository.playerVinylStyle = false
                                    playerVinylPointer = false
                                    settingsRepository.playerVinylPointer = false
                                    playerVinylBase = false
                                    settingsRepository.playerVinylBase = false
                                }
                            },
                            playerRotate = playerRotate,
                            onPlayerRotateChange = { enabled ->
                                playerRotate = enabled
                                settingsRepository.playerRotate = enabled
                            },
                            playerVinylStyle = playerVinylStyle,
                            onPlayerVinylStyleChange = { enabled ->
                                playerVinylStyle = enabled
                                settingsRepository.playerVinylStyle = enabled
                            },
                            playerVinylPointer = playerVinylPointer,
                            onPlayerVinylPointerChange = { enabled ->
                                playerVinylPointer = enabled
                                settingsRepository.playerVinylPointer = enabled
                            },
                            playerVinylBase = playerVinylBase,
                            onPlayerVinylBaseChange = { enabled ->
                                playerVinylBase = enabled
                                settingsRepository.playerVinylBase = enabled
                            },
                            playerBgEnhance = playerBgEnhance,
                            onPlayerBgEnhanceChange = { enabled ->
                                playerBgEnhance = enabled
                                settingsRepository.playerBgEnhance = enabled
                            },
                            playerHyperBg = playerHyperBg,
                            onPlayerHyperBgChange = { enabled ->
                                playerHyperBg = enabled
                                settingsRepository.playerHyperBg = enabled
                            },
                            playerFluidAmbientBg = playerFluidAmbientBg,
                            onPlayerFluidAmbientBgChange = { enabled ->
                                playerFluidAmbientBg = enabled
                                settingsRepository.playerFluidAmbientBg = enabled
                            },
                            playerMeshBg = playerMeshBg,
                            onPlayerMeshBgChange = { enabled ->
                                playerMeshBg = enabled
                                settingsRepository.playerMeshBg = enabled
                            },
                            meshFlowSpeed = meshFlowSpeed,
                            onMeshFlowSpeedChange = { value ->
                                meshFlowSpeed = value
                                settingsRepository.meshFlowSpeed = value
                            },
                            meshRenderScale = meshRenderScale,
                            onMeshRenderScaleChange = { value ->
                                meshRenderScale = value
                                settingsRepository.meshRenderScale = value
                            },
                            meshSensitivity = meshSensitivity,
                            onMeshSensitivityChange = { value ->
                                meshSensitivity = value
                                settingsRepository.meshSensitivity = value
                            },
                            meshSubdivision = meshSubdivision,
                            onMeshSubdivisionChange = { value ->
                                meshSubdivision = value
                                settingsRepository.meshSubdivision = value
                            },
                            meshStaticMode = meshStaticMode,
                            onMeshStaticModeChange = { enabled ->
                                meshStaticMode = enabled
                                settingsRepository.meshStaticMode = enabled
                            },
                            meshAnimation = meshAnimation,
                            onMeshAnimationChange = { enabled ->
                                meshAnimation = enabled
                                settingsRepository.meshAnimation = enabled
                            },
                            playerWaveformSlider = playerWaveformSlider,
                            onPlayerWaveformSliderChange = { enabled ->
                                playerWaveformSlider = enabled
                                settingsRepository.playerWaveformSlider = enabled
                            },
                            playerLyricsWordEffect = playerLyricsWordEffect,
                            onPlayerLyricsWordEffectChange = { enabled ->
                                playerLyricsWordEffect = enabled
                                settingsRepository.playerLyricsWordEffect = enabled
                            },
                            playerMeiloxLyrics = playerMeiloxLyrics,
                            onPlayerMeiloxLyricsChange = { enabled ->
                                playerMeiloxLyrics = enabled
                                settingsRepository.playerMeiloxLyrics = enabled
                            },
                            playerLyricsSeekPreview = playerLyricsSeekPreview,
                            onPlayerLyricsSeekPreviewChange = { enabled ->
                                playerLyricsSeekPreview = enabled
                                settingsRepository.playerLyricsSeekPreview = enabled
                            },
                            playerCoverBlurBg = playerCoverBlurBg,
                            onPlayerCoverBlurBgChange = { enabled ->
                                playerCoverBlurBg = enabled
                                settingsRepository.playerCoverBlurBg = enabled
                            },
                            playerCoverBlurAmount = playerCoverBlurAmount,
                            onPlayerCoverBlurAmountChange = { value ->
                                playerCoverBlurAmount = value
                                settingsRepository.playerCoverBlurAmount = value
                            },
                            playerCoverBlurDarken = playerCoverBlurDarken,
                            onPlayerCoverBlurDarkenChange = { value ->
                                playerCoverBlurDarken = value
                                settingsRepository.playerCoverBlurDarken = value
                            },
                            playerAudioReactive = playerAudioReactive,
                            onPlayerAudioReactiveChange = { enabled ->
                                playerAudioReactive = enabled
                                settingsRepository.playerAudioReactive = enabled
                            },
                            playerLyricBlur = playerLyricBlur,
                            onPlayerLyricBlurChange = { enabled ->
                                playerLyricBlur = enabled
                                settingsRepository.playerLyricBlur = enabled
                            },
                            playerLyricBlurAmount = playerLyricBlurAmount,
                            onPlayerLyricBlurAmountChange = { value ->
                                playerLyricBlurAmount = value
                                settingsRepository.playerLyricBlurAmount = value
                            },
                            playerTapCoverToLyrics = playerTapCoverToLyrics,
                            onPlayerTapCoverToLyricsChange = { enabled ->
                                playerTapCoverToLyrics = enabled
                                settingsRepository.playerTapCoverToLyrics = enabled
                            },
                            playerCompactControls = playerCompactControls,
                            onPlayerCompactControlsChange = { enabled ->
                                playerCompactControls = enabled
                                settingsRepository.playerCompactControls = enabled
                            },
                            playerMinimalistControls = playerMinimalistControls,
                            onPlayerMinimalistControlsChange = { enabled ->
                                playerMinimalistControls = enabled
                                settingsRepository.playerMinimalistControls = enabled
                            },
                            playerShowTopFavorite = playerShowTopFavorite,
                            onPlayerShowTopFavoriteChange = { enabled ->
                                playerShowTopFavorite = enabled
                                settingsRepository.playerShowTopFavorite = enabled
                            },
                            playbackKeepProgress = playbackKeepProgress,
                            onPlaybackKeepProgressChange = { enabled ->
                                playbackKeepProgress = enabled
                                settingsRepository.playbackKeepProgress = enabled
                            },
                            playbackKeepMode = playbackKeepMode,
                            onPlaybackKeepModeChange = { enabled ->
                                playbackKeepMode = enabled
                                settingsRepository.playbackKeepMode = enabled
                            },
                            playbackBluetoothStop = playbackBluetoothStop,
                            onPlaybackBluetoothStopChange = { enabled ->
                                playbackBluetoothStop = enabled
                                settingsRepository.playbackBluetoothStop = enabled
                            },
                            playbackFadeIn = playbackFadeIn,
                            onPlaybackFadeInChange = { enabled ->
                                playbackFadeIn = enabled
                                settingsRepository.playbackFadeIn = enabled
                            },
                            playbackFadeInMs = playbackFadeInMs,
                            onPlaybackFadeInMsChange = { value ->
                                playbackFadeInMs = value
                                settingsRepository.playbackFadeInMs = value
                            },
                            playbackCrossfadeNext = playbackCrossfadeNext,
                            onPlaybackCrossfadeNextChange = { enabled ->
                                playbackCrossfadeNext = enabled
                                settingsRepository.playbackCrossfadeNext = enabled
                            },
                            playbackCrossfadeInMs = playbackCrossfadeInMs,
                            onPlaybackCrossfadeInMsChange = { value ->
                                playbackCrossfadeInMs = value
                                settingsRepository.playbackCrossfadeInMs = value
                            },
                            playbackCrossfadeOutMs = playbackCrossfadeOutMs,
                            onPlaybackCrossfadeOutMsChange = { value ->
                                playbackCrossfadeOutMs = value
                                settingsRepository.playbackCrossfadeOutMs = value
                            },
                            playbackVolumeNormalization = playbackVolumeNormalization,
                            onPlaybackVolumeNormalizationChange = { enabled ->
                                playbackVolumeNormalization = enabled
                                settingsRepository.playbackVolumeNormalization = enabled
                                // AudioProcessor 全局状态实时生效
                                com.example.lxmusic.util.PlaybackVolumeNormalizationState
                                    .updateEnabled(enabled)
                            },
                            playbackVolumeBalance = playbackVolumeBalance,
                            onPlaybackVolumeBalanceChange = { value ->
                                playbackVolumeBalance = value
                                settingsRepository.playbackVolumeBalance = value
                                com.example.lxmusic.util.PlaybackVolumeBalanceState.update(value)
                            },
                            playbackHighRes = playbackHighRes,
                            onPlaybackHighResChange = { enabled ->
                                playbackHighRes = enabled
                                settingsRepository.playbackHighRes = enabled
                            },
                            playbackPreemptFocus = playbackPreemptFocus,
                            onPlaybackPreemptFocusChange = { enabled ->
                                playbackPreemptFocus = enabled
                                settingsRepository.playbackPreemptFocus = enabled
                            },
                            playbackPauseOnOtherApp = playbackPauseOnOtherApp,
                            onPlaybackPauseOnOtherAppChange = { enabled ->
                                playbackPauseOnOtherApp = enabled
                                settingsRepository.playbackPauseOnOtherApp = enabled
                                // 立即刷新服务的音频焦点策略（播放中切换也生效）
                                context.sendBroadcast(
                                    android.content.Intent("com.example.lxmusic.SETTINGS_CHANGED")
                                )
                            },
                            themeDarkMode = themeDarkMode,
                            onThemeDarkModeChange = { value ->
                                onThemeDarkModeChange(value)
                                settingsRepository.themeDarkMode = value
                            },
                            themeSeedColor = themeSeedColor,
                            onThemeSeedColorChange = { value ->
                                onThemeSeedColorChange(value)
                                settingsRepository.themeSeedColor = value
                            },
                            themePaletteStyle = themePaletteStyle,
                            onThemePaletteStyleChange = { value ->
                                onThemePaletteStyleChange(value)
                                settingsRepository.themePaletteStyle = value
                            },
                            themeColorAnimation = themeColorAnimation,
                            onThemeColorAnimationChange = { enabled ->
                                onThemeColorAnimationChange(enabled)
                                settingsRepository.themeColorAnimation = enabled
                            },
                            uiDensityScale = uiDensityScale,
                            onUiDensityScaleChange = onUiDensityScaleChange,
                            bottomBarHeightScale = bottomBarHeightScale,
                            onBottomBarHeightScaleChange = onBottomBarHeightScaleChange,
                            hapticEnabled = hapticEnabled,
                            onHapticEnabledChange = onHapticEnabledChange,
                            preferHighRefreshRate = preferHighRefreshRate,
                            onPreferHighRefreshRateChange = onPreferHighRefreshRateChange,
                            favoriteToKugou = favoriteToKugou,
                            onFavoriteToKugouChange = onFavoriteToKugouChange,
                            favoriteSyncLocal = favoriteSyncLocal,
                            onFavoriteSyncLocalChange = onFavoriteSyncLocalChange,
                            settingsSubPage = settingsSubPage,
                            onSettingsSubPageChange = { settingsSubPage = it }
                        )
                    }
                    } // 关闭横屏限宽容器
                }

                // 公开歌单详情覆盖层：和首页一样铺 App 背景（壁纸/纯色），
                // 只透背景、不透下层 tab 内容
                if (showPublicPlaylist != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        )
                        if (backgroundImageUri != null) {
                            val painter = rememberAsyncImagePainter(model = Uri.parse(backgroundImageUri))
                            Image(
                                painter = painter,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().alpha(bgOpacity),
                                contentScale = ContentScale.Crop
                            )
                        }
                        PublicPlaylistDetailPage(
                            playlist = showPublicPlaylist!!,
                            onBack = { showPublicPlaylist = null },
                            onPlaySong = { songs, index -> playOnlineSong(songs, index) },
                            currentPlayingPath = currentSong?.filePath,
                            isPlaying = isPlaying
                        )
                    }
                }

                // 专辑详情覆盖层（搜索「专辑」点进来）：铺 App 背景，底栏隐藏、胶囊播放条保留
                if (showPublicAlbum != null) {
                    val album = showPublicAlbum!!
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        )
                        if (backgroundImageUri != null) {
                            val painter = rememberAsyncImagePainter(model = Uri.parse(backgroundImageUri))
                            Image(
                                painter = painter,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().alpha(bgOpacity),
                                contentScale = ContentScale.Crop
                            )
                        }
                        PublicAlbumDetailPage(
                            albumId = album.albumid,
                            albumName = album.albumname ?: "未知专辑",
                            artist = album.authorname.orEmpty(),
                            coverUrl = album.coverUrl,
                            songCount = album.songcount,
                            onBack = { showPublicAlbum = null },
                            onPlaySong = { songs, index -> playOnlineSong(songs, index) },
                            currentPlayingPath = currentSong?.filePath,
                            isPlaying = isPlaying
                        )
                    }
                }

                // 覆盖层页面（登录页保留覆盖层方式）
                if (showLoginPage) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        if (backgroundImageUri != null) {
                            val painter = rememberAsyncImagePainter(model = Uri.parse(backgroundImageUri))
                            Image(
                                painter = painter,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().alpha(bgOpacity),
                                contentScale = ContentScale.Crop
                            )
                        }
                        LoginPage(
                            onBack = { showLoginPage = false },
                            onLoginSuccess = { nickname ->
                                showLoginPage = false
                                loginVersion++
                                // 登录成功后请求一次首页自动刷新：
                                // 首页若正挂在组合里由 loginVersion 变化当场消费；
                                // 若在其他 tab，则下次进首页（HomePage 重建）时消费标记
                                settingsRepository.requestHomeRefresh()
                            }
                        )
                    }
                }
            }
            // 顶栏渐变统一交给内容层的 DstIn 渐隐（有/无壁纸都一样），
            // 这里不再额外汇一条色带——否则无壁纸时会在顶栏下方出现一条明显的色条。
            } // 关闭外层 Box
        }

    // 本地音乐左侧分类面板（叠加在整个 Scaffold 之上，含顶栏区域；跟随手势平移）
    if (selectedTab == 3) {
        LocalMusicSidePanel(
            currentSection = localDrawerSection,
            onSectionClick = { localDrawerSection = it },
            onScanClick = { localScanTrigger++ },
            onSettingsClick = { selectedTab = 2 },
            modifier = Modifier
                .width(200.dp)
                .offset { IntOffset((localPanelOffset.value - localPanelWidthPx).roundToInt(), 0) }
        )
    }

    // 播放条实际高度（测量）与其顶部位置：跟手展开的起点 = 播放条顶部，
    // 页面顶从播放条位置升起、显示页面顶部内容（播放条即页面的收起形态，
    // 向上拉时播放条淡出变为页面被拉出的部分）
    var miniBarHeightPx by remember { mutableIntStateOf(0) }
    val miniPlayerBottomPaddingPx = with(density) { miniPlayerBottomPadding.toPx() }
    val miniBarTopY = (screenHeightPx - miniPlayerBottomPaddingPx - miniBarHeightPx).coerceAtLeast(0f)
    // 展开/收起手势速度判定（快速滑动提前判定）
    val revealVelocityTracker = remember { VelocityTracker() }
    var miniBarDragAccumulated by remember { mutableFloatStateOf(0f) }
    var playerDragAccumulated by remember { mutableFloatStateOf(0f) }
    // 跟手协程（UNDISPATCHED 立即执行；取消旧的避免堆积导致判定滞后）
    var revealSnapJob by remember { mutableStateOf<Job?>(null) }
    // 统一动画入口：动画期间维持组合（isRevealAnimating），结束后复位
    suspend fun animateRevealTo(target: Float, spec: androidx.compose.animation.core.AnimationSpec<Float>) {
        isRevealAnimating = true
        try {
            playerRevealY.animateTo(target, spec)
        } finally {
            isRevealAnimating = false
        }
    }


    // 迷你播放条（带滑入动画 + 上滑手势跟随展开）
    val lastValidSong = remember { mutableStateOf<SongInfo?>(null) }
    if (currentSong != null) lastValidSong.value = currentSong
    val displaySong = lastValidSong.value
    AnimatedVisibility(
        visible = currentSong != null && !showPlayerPage &&
            selectedTab !in listOf(12, 15, 17) && !isSelectionModeActive &&
            (!floatingBottomBar || !showNavBar),
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(280)) + fadeIn(tween(220)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(tween(150))
    ) {
        MiniPlayerBar(
            song = displaySong ?: return@AnimatedVisibility,
            isPlaying = isPlaying,
            progress = playerViewModel.progress,
            onPlayPause = { playerViewModel.playPause() },
            onNext = { playNext() },
            onPrevious = { playPrevious() },
            // 点击 = 从播放条位置生长展开（动画，BoomingMusic BOOMING_ANIM_TIME=350ms）
            onClick = {
                scope.launch { expandPlayer(miniBarTopY) }
            },
            onMenuClick = { showPlaylistSheet = true },
            // 上拉跟手展开：页面顶从播放条顶部位置实时跟随手指升起
            // （播放条淡出，变为页面被拉出的部分）
            onVerticalDragStart = {
                isMiniBarDragging = true
                miniBarDragAccumulated = 0f
                revealVelocityTracker.resetTracking()
                revealSnapJob?.cancel()
                revealSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    playerRevealY.snapTo(miniBarTopY)
                }
            },
            onVerticalDrag = { delta ->
                miniBarDragAccumulated += delta
                revealVelocityTracker.addPosition(
                    SystemClock.uptimeMillis(),
                    Offset(0f, miniBarDragAccumulated)
                )
                revealSnapJob?.cancel()
                revealSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    playerRevealY.snapTo(
                        (playerRevealY.value + delta).coerceIn(0f, screenHeightPx)
                    )
                }
            },
            onVerticalDragEnd = {
                val velocityY = revealVelocityTracker.calculateVelocity().y
                // 判定基于手指实际累计位移（事件回调同步更新，不受动画值滞后影响）：
                // 上拉累计超过 10%（miniBarTopY 的 10%，一点点距离即可拉起）或速度 > 80px/s → 展开
                val threshold = miniBarTopY * 0.1f
                val shouldExpand = miniBarDragAccumulated < -threshold || velocityY < -80f
                scope.launch {
                    // 先关拖动标记、紧接着置动画标记，保证页面组合条件不中断（不销毁重建）
                    isMiniBarDragging = false
                    if (shouldExpand) {
                        expandPlayer()
                    } else {
                        animateRevealTo(screenHeightPx, tween(350, easing = FastOutSlowInEasing))
                    }
                }
            },
            onVerticalDragCancel = {
                scope.launch {
                    isMiniBarDragging = false
                    animateRevealTo(screenHeightPx, tween(350, easing = FastOutSlowInEasing))
                }
            },
            bottomPadding = miniPlayerBottomPadding,
            isFloatingBottomBar = floatingBottomBar,
            followThemeColor = followThemeColor,
            playerBarOpacity = playerBarOpacity,
            playerBarWhiteBlend = playerBarWhiteBlend,
            // 原生主题下液态玻璃播放条有显示 bug：降级成毛玻璃（只模糊、不折射）；
            // 现代主题维持液态玻璃。只影响播放条本身，底栏/顶栏/主题布局都不动。
            liquidGlass = liquidGlass && settingsRepository.themeMode == "modern",
            frostedGlass = liquidGlass && settingsRepository.themeMode != "modern",
            hasCustomWallpaper = backgroundImageUri != null,
            backdrop = glassBackdrop,
            // BoomingMusic 同款：迷你条固定原位（不随拖动上移），
            // 展开进度 0-20% 内快速淡出，之后完全由播放器页面接管。
            // progress 在 draw 阶段读取动画值（不触发组合重组）
            modifier = Modifier
                .onSizeChanged { miniBarHeightPx = it.height }
                .graphicsLayer {
                    val progress = if (miniBarTopY > 0f) {
                        ((miniBarTopY - playerRevealY.value) / miniBarTopY).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    alpha = if (isMiniBarDragging) {
                        1f - (progress / 0.2f).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                }
        )
    }

    // ===== 全屏播放器：舞台层共享背景 + 单页面卡片翻页（中间封面卡↔歌词卡，四周固定） =====
    // 背景只渲染一份：取色一致、旋转相位连续，卡片翻页时背景与四周 UI 完全静止
    // 展开/收起统一由 playerRevealY 驱动（0=完全展开，screenHeightPx=完全收起）：
    // - mini 播放条上拉 → 页面底部边缘实时跟随手指从屏幕底部升起（跟手展开）
    // - 播放器内下拉 → 页面跟手下移（跟手收起），松手按阈值回弹/收起
    val playerShown = showPlayerPage && currentSong != null
    val playerSong = displaySong
    // 页面顶偏移（px）共享状态：布局阶段写入、流体背景 View 每帧读取做绘制裁剪
    // （引用传递，组合中不读取值 → 不触发重组）
    val hyperClipTopPx = remember { mutableFloatStateOf(0f) }
    // 组合条件用布尔驱动（不读取动画值，避免每帧重组整棵页面树）
    val playerInComposition =
        (playerShown || isMiniBarDragging || isRevealAnimating) && playerSong != null
    if (playerInComposition) {
        val song = playerSong
        // 背景旋转相位：舞台层统一驱动（原在 PlayerPage 内，翻页后连续不跳变）
        val rotationAngle = remember { Animatable(0f) }
        LaunchedEffect(isPlaying, playerRotate, playerRoundAlbum, playerVinylStyle, playerVinylPointer, playerVinylBase, song.filePath) {
            rotationAngle.snapTo(0f)
            if (isPlaying && playerRoundAlbum && (playerRotate || playerVinylStyle || playerVinylPointer || playerVinylBase)) {
                // 等待切歌进场放大（scaleIn 520ms + delay 180ms = 700ms）动效完全就绪后，再从 0 开始平滑转动
                kotlinx.coroutines.delay(700)
                while (true) {
                    rotationAngle.animateTo(
                        targetValue = rotationAngle.value + 360f,
                        animationSpec = tween(20000, easing = LinearEasing)
                    )
                    rotationAngle.snapTo(0f)
                }
            }
        }
        // 舞台层共享背景 painter（单实例；页面内的封面 painter 各自持有）
        val albumModel: Any? = song.let { s ->
            when {
                s.albumArtUri != null && (s.albumArtUri.startsWith("/") || s.albumArtUri.startsWith("file://")) ->
                    File(s.albumArtUri.removePrefix("file://"))
                s.albumArtUri != null -> Uri.parse(s.albumArtUri)
                else -> File(s.filePath)
            }
        }
        val albumPainter = rememberAsyncImagePainter(model = albumModel)
        val bgPainter = rememberAsyncImagePainter(model = backgroundImageUri, placeholder = null)

        Box(
            modifier = Modifier
                .fillMaxSize()
                // 跟手偏移：0=全展开；正=页面下移（展开中从播放条位置升起 / 收起时滑出屏底）。
                // 用真实布局偏移（自定义 layout + place）而不是 Modifier.offset：
                // offset 会被优化成 RenderNode 平移，只移动 Compose 渲染层，
                // 流体背景（AndroidView 的 View 独立合成）不会跟随 → 拖动时占满屏幕。
                // 布局阶段读取动画值（不触发组合重组），子内容只重新 place 不重新 measure
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    // 同步页面顶偏移（布局阶段写入，流体背景 View 裁剪用）
                    hyperClipTopPx.floatValue = playerRevealY.value
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, playerRevealY.value.roundToInt())
                    }
                }
                // 拖动/收起动画中顶部圆角（卡片感）：中间最大、完全展开/收起时归 0。
                // 绘制阶段读取动画值（drawWithCache 只重绘不重组）
                // 注意：必须**始终**裁剪到页面矩形。流体背景是 Compose Canvas + graphicsLayer
                // （1.3x 放大 + RenderEffect 模糊），不裁剪时收起过程中放大出来的那圈会画到
                // 页面顶部之外，首页上会闪出一条动效背景色（SurfaceView 的 Mesh 不受影响）。
                .drawWithCache {
                    onDrawWithContent {
                        val progress =
                            ((miniBarTopY - playerRevealY.value) / miniBarTopY).coerceIn(0f, 1f)
                        // 4 * 28dp * p * (1-p)：p=0 或 1 时为 0，p=0.5 时最大 28dp
                        val corner = 4f * 28.dp.toPx() * progress * (1f - progress)
                        val path = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(0f, 0f, size.width, size.height),
                                    topLeft = CornerRadius(corner, corner),
                                    topRight = CornerRadius(corner, corner)
                                )
                            )
                        }
                        clipPath(path) { this@onDrawWithContent.drawContent() }
                    }
                }
                // 整页下拉收起（纵向；横向翻页在卡片区，歌词滚动优先、滚动到顶后下拉才收起）
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            playerDragAccumulated = 0f
                            revealVelocityTracker.resetTracking()
                            scope.launch { playerRevealY.stop() }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            playerDragAccumulated += dragAmount
                            revealVelocityTracker.addPosition(change.uptimeMillis, change.position)
                            // 下拉时增加偏移（页面下移），上拉时限制最小为0
                            revealSnapJob?.cancel()
                            revealSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                playerRevealY.snapTo(
                                    (playerRevealY.value + dragAmount).coerceIn(0f, screenHeightPx)
                                )
                            }
                        },
                        onDragEnd = {
                            val velocityY = revealVelocityTracker.calculateVelocity().y
                            // 判定基于手指实际累计下拉位移：超过 15% 屏高或速度 > 150px/s → 收起
                            val shouldCollapse =
                                playerDragAccumulated > screenHeightPx * 0.15f || velocityY > 150f
                            scope.launch {
                                if (shouldCollapse) {
                                    collapsePlayer()
                                } else {
                                    // 回弹到完全展开
                                    expandPlayer()
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { expandPlayer() }
                        }
                    )
                }
        ) {
                    // ===== 共享背景（单实例：一次取色、一次加载，两页观感完全一致） =====
                    PlayerBackdrop(
                        backgroundColor = MaterialTheme.colorScheme.background,
                        primaryColor = MaterialTheme.colorScheme.primary,
                        secondaryColor = MaterialTheme.colorScheme.secondary,
                        tertiaryColor = MaterialTheme.colorScheme.tertiary,
                        coverModel = albumModel,
                        dynamicBackground = playerDynamicBg,
                        backgroundEnhance = playerBgEnhance,
                        playerHyperBg = playerHyperBg,
                        playerFluidAmbientBg = playerFluidAmbientBg,
                        playerMeshBg = playerMeshBg,
                        meshFlowSpeed = meshFlowSpeed,
                        meshRenderScale = meshRenderScale,
                        meshSensitivity = meshSensitivity,
                        meshSubdivision = meshSubdivision,
                        meshStaticMode = meshStaticMode,
                        meshAnimation = meshAnimation,
                        playerAudioReactive = playerAudioReactive,
                        hyperClipTopProvider = { hyperClipTopPx.floatValue },
                        playerCoverBlurBg = playerCoverBlurBg,
                        playerCoverBlurAmount = playerCoverBlurAmount,
                        playerCoverBlurDarken = playerCoverBlurDarken,
                        playerBgOpacity = playerBgOpacity,
                        playerBlur = playerBlur,
                        rotationAngle = rotationAngle.value,
                        playing = isPlaying,
                        bgPainter = bgPainter,
                        albumPainter = albumPainter,
                        modifier = Modifier.fillMaxSize()
                    )

                    // ===== 播放器单页面（顶栏/进度/控制固定，中间卡片翻页） =====
                    PlayerStage(
                        song = song,
                        isPlaying = isPlaying,
                        progress = playerViewModel.progress,
                        audioFormat = audioFormat,
                        pagerState = playerPagerState,
                        onPlayPause = { playerViewModel.playPause() },
                        onNext = { playNext() },
                        onPrevious = { playPrevious() },
                        onSeek = { position -> playerViewModel.seekTo(position) },
                        onPlaylistClick = { showPlaylistSheet = true },
                        onStartSleepTimer = { minutes, extend ->
                            playerViewModel.startSleepTimer(minutes, extend)
                        },
                        onStopSleepTimer = { playerViewModel.cancelSleepTimer() },
                        onOpenSoundSettings = { showSoundSettingsPage = true },
                        onOpenEqualizer = { showEqualizerPage = true },
                        playMode = playMode,
                        onPlayModeChange = { newMode -> playerViewModel.setPlayMode(newMode) },
                        isFavorite = isCurrentSongFavorite,
                        onFavoriteClick = {
                            val song = currentSong ?: return@PlayerStage
                            scope.launch(Dispatchers.IO) {
                                val isNeteaseSong = NeteaseApi.isNeteasePath(song.filePath)
                                val parts = song.filePath.split("|")
                                val hash = if (isNeteaseSong) "" else parts.getOrElse(0) { "" }
                                val audioId = if (isNeteaseSong) 0L else parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L
                                if (!isNeteaseSong && hash.isBlank()) return@launch
                                val key = if (isNeteaseSong) song.filePath else "${hash}|${audioId}"
                                // 开启「收藏到酷狗」：本地先写「喜欢镜像」（立即可见、不消失），酷狗后台慢慢同步；
                                // “同步到本地收藏”开启时再额外复制一份到本地“我的收藏”
                                // 网易云歌曲暂无官方同步，一律走本地收藏
                                if (favoriteToKugou && !isNeteaseSong) {
                                    val target = !isCurrentSongFavorite
                                    isCurrentSongFavorite = target
                                    if (target) {
                                        collectionDao.insertLikedSong(
                                            com.example.lxmusic.LikedSongEntity(
                                                filePath = key,
                                                title = song.title,
                                                artist = song.artist,
                                                albumArtUri = song.albumArtUri,
                                                duration = song.duration,
                                                hash = hash,
                                                audioId = audioId,
                                                albumId = song.albumId,
                                                mixsongid = song.mixsongid
                                            )
                                        )
                                        if (favoriteSyncLocal) {
                                            collectionDao.insertCollectedSong(
                                                com.example.lxmusic.CollectedSongEntity(
                                                    filePath = key,
                                                    title = song.title,
                                                    artist = song.artist,
                                                    albumArtUri = song.albumArtUri,
                                                    duration = song.duration,
                                                    hash = hash,
                                                    audioId = audioId,
                                                    albumId = song.albumId,
                                                    mixsongid = song.mixsongid
                                                )
                                            )
                                        }
                                    } else {
                                        collectionDao.deleteLikedSong(key)
                                        if (favoriteSyncLocal) collectionDao.deleteCollectedSong(key)
                                    }
                                    kugouLikeTick++
                                    // 维护「我喜欢的」统一数量（官方∪本地）
                                    if (target) KuGouApi.bumpLikedCount(1) else KuGouApi.bumpLikedCount(-1)
                                    // 后台同步酷狗（失败不影响本地）
                                    val kugouOk = if (KuGouApi.token.isNotBlank() && KuGouApi.userid.isNotBlank()) {
                                        if (target) KuGouApi.addToKugouLike(song) else KuGouApi.removeFromKugouLike(song)
                                    } else false
                                    val msg = when {
                                        target && kugouOk -> "已喜欢"
                                        target && KuGouApi.token.isBlank() -> "已喜欢（未登录，暂未同步官方）"
                                        target -> "已喜欢（官方同步失败）"
                                        kugouOk -> "已取消喜欢"
                                        else -> "已取消喜欢（官方同步失败）"
                                    }
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    return@launch
                                }
                                if (isCurrentSongFavorite) {
                                    collectionDao.deleteCollectedSong(key)
                                    withContext(Dispatchers.Main) { isCurrentSongFavorite = false }
                                } else {
                                    val entity = com.example.lxmusic.CollectedSongEntity(
                                        filePath = key,
                                        title = song.title,
                                        artist = song.artist,
                                        albumArtUri = song.albumArtUri,
                                        duration = song.duration,
                                        hash = hash,
                                        audioId = audioId,
                                        albumId = song.albumId,
                                        mixsongid = song.mixsongid
                                    )
                                    collectionDao.insertCollectedSong(entity)
                                    withContext(Dispatchers.Main) { isCurrentSongFavorite = true }
                                }
                            }
                        },
                        rotationAngle = rotationAngle.value,
                        isRoundAlbum = playerRoundAlbum,
                        isRotating = playerRotate,
                        isVinylStyle = playerVinylStyle && playerRoundAlbum,
                        isVinylPointer = playerVinylPointer && playerRoundAlbum,
                        isVinylBase = playerVinylBase && playerRoundAlbum,
                        songIndex = currentSongIndex,
                        queueSize = currentSongList.size.coerceAtLeast(1),
                        playerDynamicBg = playerDynamicBg,
                        playerBgEnhance = playerBgEnhance,
                        playerHyperBg = playerHyperBg,
                        playerFluidAmbientBg = playerFluidAmbientBg,
                        playerMeshBg = playerMeshBg,
                        playerCoverBlurBg = playerCoverBlurBg,
                        playerLyricsWordEffect = playerLyricsWordEffect,
                        playerMeiloxLyrics = playerMeiloxLyrics,
                        translatedLyrics = currentTranslationEntries,
                        playerWaveformSlider = playerWaveformSlider,
                        playerLyricsSeekPreview = playerLyricsSeekPreview,
                        playerLyricBlur = playerLyricBlur,
                        playerLyricBlurAmount = playerLyricBlurAmount,
                        playerTapCoverToLyrics = playerTapCoverToLyrics,
                        playerCompactControls = playerCompactControls,
                        playerMinimalistControls = playerMinimalistControls,
                        playerShowTopFavorite = playerShowTopFavorite,
                        lyricFontSize = playerLyricFontSize,
                        onLyricFontSizeChange = { value ->
                            playerLyricFontSize = value
                            settingsRepository.playerLyricFontSize = value
                        },
                        lyricFontWeight = playerLyricFontWeight,
                        onLyricFontWeightChange = { value ->
                            playerLyricFontWeight = value
                            settingsRepository.playerLyricFontWeight = value
                        },
                        lyricAlignment = playerLyricAlignment,
                        onLyricAlignmentChange = { value ->
                            playerLyricAlignment = value
                            settingsRepository.playerLyricAlignment = value
                        },
                        coverLyricFontSize = playerCoverLyricFontSize,
                        onCoverLyricFontSizeChange = { value ->
                            playerCoverLyricFontSize = value
                            settingsRepository.playerCoverLyricFontSize = value
                        },
                        coverLyricFontWeight = playerCoverLyricFontWeight,
                        onCoverLyricFontWeightChange = { value ->
                            playerCoverLyricFontWeight = value
                            settingsRepository.playerCoverLyricFontWeight = value
                        },
                        coverLyricAlignment = playerCoverLyricAlignment,
                        onCoverLyricAlignmentChange = { value ->
                            playerCoverLyricAlignment = value
                            settingsRepository.playerCoverLyricAlignment = value
                        },
                        lyricsText = currentLyricsText
                )
            }
        }

        // ===== 播放器菜单弹出的独立页面（声音设置 / 均衡器）：覆盖播放器，返回键先关页面 =====
        if (showEqualizerPage) {
            EqualizerPage(onBack = { showEqualizerPage = false })
        }
        if (showSoundSettingsPage) {
            SoundSettingsPage(
                playerViewModel = playerViewModel,
                audioFormat = audioFormat,
                balance = playbackVolumeBalance,
                onBalanceChange = { value ->
                    playbackVolumeBalance = value
                    settingsRepository.playbackVolumeBalance = value
                    com.example.lxmusic.util.PlaybackVolumeBalanceState.update(value)
                },
                normalization = playbackVolumeNormalization,
                onNormalizationChange = { enabled ->
                    playbackVolumeNormalization = enabled
                    settingsRepository.playbackVolumeNormalization = enabled
                    com.example.lxmusic.util.PlaybackVolumeNormalizationState.updateEnabled(enabled)
                },
                highRes = playbackHighRes,
                onHighResChange = { enabled ->
                    playbackHighRes = enabled
                    settingsRepository.playbackHighRes = enabled
                },
                onBack = { showSoundSettingsPage = false }
            )
        }

        // 播放列表面板
        if (showPlaylistSheet && currentSong != null) {
            android.util.Log.d("LxMusic", "打开播放列表: currentSongList.size=${currentSongList.size}, currentSongIndex=$currentSongIndex")
            PlaylistSheet(
                songs = currentSongList,
                currentIndex = currentSongIndex,
                onSongClick = { index ->
                    android.util.Log.d("LxMusic", "PlaylistSheet onSongClick: index=$index, currentSongIndex=$currentSongIndex, listSize=${currentSongList.size}")
                    if (index != currentSongIndex) {
                        playerViewModel.seekToIndex(index)
                    }
                },
                onDismiss = { showPlaylistSheet = false },
                onReorder = { from, to ->
                    playerViewModel.moveItem(from, to)
                },
                onRemoveSong = { filePath ->
                    playerViewModel.removeItem(filePath)
                }
            )
        }

        // 纯色主题（无壁纸）：底栏下方铺一层背景色渐变，滚动内容从底栏下柔和淡出。
        // 有壁纸时保持原来的玻璃观感，不加这层遮罩（两种状态互不影响）
        if (backgroundImageUri == null && floatingBottomBar && showNavBar && !showPlayerPage) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                        )
                    )
            )
        }

        // 导航栏叠加在底部
        AnimatedVisibility(
            visible = selectedTab in topLevelTabs && !showPlayerPage && showRankDetail == null &&
                showPublicPlaylist == null && showPublicAlbum == null && !showLoginPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomBarHeightPx = it.height },
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(280)) + fadeIn(tween(220)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(tween(150))
        ) {
            // BoomingMusic 同款：跟手展开时底栏随进度向下滚出屏幕（translationY）+ 渐隐。
            // progress 在 draw 阶段读取动画值（不触发组合重组）
            Box(
                modifier = Modifier.graphicsLayer {
                    // 静止态（非跟手拖动、非展开/收起动画）progress 强制 0：
                    // 仅手势/动画进行中底栏才跟随滚出；避免任何状态残留
                    // （如旋转后 playerRevealY 旧值）把底栏推出屏幕导致"底栏消失"
                    val progress = when {
                        // 播放器已打开：底栏保持"已滚出"状态，避免收起动画开始前
                        // 进度被重置为 0 导致底栏跳回屏幕内再滑出（打开播放器时的闪烁）
                        showPlayerPage -> 1f
                        !isMiniBarDragging && !isRevealAnimating -> 0f
                        miniBarTopY > 0f -> ((miniBarTopY - playerRevealY.value) / miniBarTopY).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    // BoomingMusic 原值：底栏滚出 500px（非 dp）
                    translationY = progress * 500f
                    alpha = if (isMiniBarDragging) 1f - progress else 1f
                }
            ) {
                FloatingBottomBar(
                    selectedTabIndex = selectedTab,
                    onTabSelected = { index -> selectedTab = index },
                    tabs = bottomTabs,
                    blurEnabled = blurNavBar,
                    isFloating = floatingBottomBar,
                    liquidGlass = liquidGlass,
                    hasCustomWallpaper = backgroundImageUri != null,
                    backdrop = glassBackdrop,
                    navBarOpacity = if (floatingBottomBar) floatingBarOpacity else navBarOpacity,
                    followThemeColor = followThemeColor,
                    playerBarWhiteBlend = playerBarWhiteBlend,
                    bottomBarHeightScale = bottomBarHeightScale,
                    currentSong = if (selectedTab !in listOf(12, 15, 17) && !isSelectionModeActive) currentSong else null,
                    isPlaying = isPlaying,
                    progress = playerViewModel.progress,
                    onPlayPause = { playerViewModel.playPause() },
                    onNext = { playNext() },
                    onPlayerClick = {
                        scope.launch {
                            // 从底栏顶部位置起升（而非固定的屏高比例）：底栏随页面升起
                            // 同步滚出，避免起手瞬间底栏被直接推出屏幕的"闪烁"观感
                            expandPlayer(miniBarTopY)
                        }
                    }
                )
            }
        }

        // 全屏圆形波纹主题揭示过渡（对齐 NeriPlayer）
        val revealOrigin = themeRevealOrigin
        val revealFallback = themeRevealFallbackColor
        if (revealOrigin != null && revealFallback != null) {
            ThemeRevealOverlay(
                snapshot = themeRevealSnapshot,
                fallbackColor = revealFallback,
                originInWindow = revealOrigin,
                startRadiusPx = themeRevealStartRadius,
                durationMillis = 680,
                onFinished = {
                    themeRevealSnapshot = null
                    themeRevealOrigin = null
                    themeRevealFallbackColor = null
                    isThemeRevealing = false
                }
            )
        }

        // 应用启动自动检测到的新版本弹窗
        launchUpdateInfo?.let { info ->
            AppUpdateDialog(
                updateInfo = info,
                onDismiss = { launchUpdateInfo = null },
                onIgnoreVersion = {
                    UpdateChecker.setVersionIgnored(context, info.versionName)
                    Toast.makeText(context, "已忽略 v${info.versionName} 版本提示", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

