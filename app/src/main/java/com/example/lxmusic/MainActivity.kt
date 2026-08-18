@file:OptIn(androidx.media3.common.util.UnstableApi::class)

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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.graphics.Color
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
import com.example.lxmusic.ui.theme.LocalScaleFactor
import com.example.lxmusic.ui.theme.ScreenAdapter
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.ui.components.FloatingBottomBar
import com.example.lxmusic.data.LiquidGlassSettings
import com.example.lxmusic.data.SettingsRepository
import com.example.lxmusic.ui.components.LocalMusicSidePanel
import com.example.lxmusic.ui.components.MiniPlayerBar
import com.example.lxmusic.ui.components.HapticsProvider
import com.example.lxmusic.ui.components.PlaylistSheet
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.example.lxmusic.ui.pages.HomePage
import com.example.lxmusic.ui.pages.DiscoverPage
import com.example.lxmusic.ui.pages.RankDetailPage
import com.example.lxmusic.ui.pages.SearchPage
import com.example.lxmusic.ui.pages.LoginPage
import com.example.lxmusic.ui.pages.PlayerStage
import com.example.lxmusic.ui.pages.SongListPage
import com.example.lxmusic.ui.pages.PlaylistPage
import com.example.lxmusic.ui.pages.PlaylistDetailPage
import com.example.lxmusic.ui.pages.CollectionDetailPage
import com.example.lxmusic.ui.pages.SearchPlaylistDetailPage
import com.example.lxmusic.ui.pages.MinePage
import com.example.lxmusic.ui.pages.PlaylistManagerPage
import com.example.lxmusic.ui.effect.PlayerBackdrop
import com.example.lxmusic.ui.pages.LocalMusicPage
import com.example.lxmusic.ui.pages.LocalMusicTopBar
import com.example.lxmusic.ui.pages.SearchTopBar
import com.example.lxmusic.ui.pages.SettingsPage
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
                var hapticEnabled by remember { mutableStateOf(settingsRepository.hapticFeedback) }
                var preferHighRefreshRate by remember { mutableStateOf(settingsRepository.preferHighRefreshRate) }
                // 高刷新率实时生效
                LaunchedEffect(preferHighRefreshRate) {
                    applyPreferredHighRefreshRate(preferHighRefreshRate)
                }
                // 动态取色 = 根据当前歌曲专辑封面取色（对齐 NeriPlayer）。
                // 监听当前播放歌曲，从其专辑封面提取种子色，随切歌实时变化。
                val playerViewModel: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                val playerUiState by playerViewModel.uiState.collectAsState()
                val currentSong = playerUiState.currentSong
                var albumSeedColorHex by remember { mutableStateOf<String?>(null) }
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
                }
                // 自定义主色仅现代化主题生效；原生主题一律走种子色/专辑取色路径，
                // 避免 themeColorHex 残留值劫持种子色导致背景不跟随变化
                val customColor = if (
                    settingsRepository.themeMode == "modern" &&
                    themeColorHex.isNotBlank() &&
                    !dynamicColor
                ) {
                    try { Color(android.graphics.Color.parseColor(themeColorHex)) } catch (_: Exception) { null }
                } else null

                // 明暗模式：自动 / 浅色 / 深色
                val resolvedDarkTheme = when (themeDarkMode) {
                    "light" -> false
                    "dark" -> true
                    else -> isSystemInDarkTheme()
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
                    customPrimaryColor = customColor,
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
                                hapticEnabled = hapticEnabled,
                                onHapticEnabledChange = { enabled ->
                                    hapticEnabled = enabled
                                    settingsRepository.hapticFeedback = enabled
                                },
                                preferHighRefreshRate = preferHighRefreshRate,
                                onPreferHighRefreshRateChange = { enabled ->
                                    preferHighRefreshRate = enabled
                                    settingsRepository.preferHighRefreshRate = enabled
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
    hapticEnabled: Boolean = true,
    onHapticEnabledChange: (Boolean) -> Unit = {},
    preferHighRefreshRate: Boolean = false,
    onPreferHighRefreshRateChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerViewModel: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val uiState by playerViewModel.uiState.collectAsState()
    val audioFormat by playerViewModel.audioFormat.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var settingsSubPage by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedTab) { if (selectedTab != 12) settingsSubPage = null }
    var isThemeRevealing by remember { mutableStateOf(false) }
    var themeRevealSnapshot by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var themeRevealOrigin by remember { mutableStateOf<Offset?>(null) }
    var themeRevealStartRadius by remember { mutableFloatStateOf(18f) }
    var themeRevealFallbackColor by remember { mutableStateOf<Color?>(null) }
    val currentBgColor = MaterialTheme.colorScheme.background
    var showPlayerPage by rememberSaveable { mutableStateOf(false) }
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
    var previousTab by rememberSaveable { mutableIntStateOf(0) }

    // 进程重建后，依赖非持久化数据的子页面（4-7 每日/历史/风格列表、8-10 歌单详情）
    // 数据已丢失，回退到父级页面，避免显示空白列表
    LaunchedEffect(Unit) {
        when (selectedTab) {
            in 4..7 -> selectedTab = 0
            in 8..10 -> selectedTab = 2
        }
    }

    val currentSong = uiState.currentSong
    val currentSongList = uiState.queue
    val currentSongIndex = uiState.currentIndex
    val isPlaying = uiState.isPlaying
    val currentPlayingPath = uiState.currentPlayingPath
    val playMode = uiState.playMode

    var showPlaylistSheet by remember { mutableStateOf(false) }
    // 在线歌词（播放页与全屏歌词页共享）
    var onlineLyrics by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentSong?.filePath) {
        onlineLyrics = null
        val song = currentSong ?: return@LaunchedEffect
        if (!song.lyrics.isNullOrBlank()) return@LaunchedEffect
        val hash = song.filePath.split("|").firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return@LaunchedEffect
        try {
            val searchResp = KuGouApi.service.searchLyric(hash)
            val candidate = searchResp.candidates?.firstOrNull()
            if (candidate?.id != null && candidate.accesskey != null) {
                val lyricResp = KuGouApi.service.getLyric(candidate.id, candidate.accesskey)
                val content = lyricResp.content
                if (!content.isNullOrBlank()) {
                    val decoded = try {
                        String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
                    } catch (_: Exception) { content }
                    onlineLyrics = decoded
                }
            }
        } catch (_: Exception) {}
    }
    val currentLyricsText = currentSong?.lyrics?.takeIf { it.isNotBlank() } ?: onlineLyrics
    var showRankDetail by remember { mutableStateOf<RankItem?>(null) }
    var showLoginPage by rememberSaveable { mutableStateOf(false) }
    var loginVersion by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchSelectedType by rememberSaveable { mutableStateOf("song") }
    var searchPlaylistId by rememberSaveable { mutableLongStateOf(0L) }
    var searchPlaylistName by rememberSaveable { mutableStateOf("") }
    var searchPlaylistCover by rememberSaveable { mutableStateOf("") }
    var searchPlaylistAuthor by rememberSaveable { mutableStateOf("") }
    var searchPlaylistGid by rememberSaveable { mutableStateOf("") }
    var dailySongsForList by remember { mutableStateOf<List<DailyRecommendSong>>(emptyList()) }
    var vipSongsForList by remember { mutableStateOf<List<SongInfo>>(emptyList()) }
    var historySongsForList by remember { mutableStateOf<List<DailyRecommendSong>>(emptyList()) }
    var styleSongsForList by remember { mutableStateOf<List<DailyRecommendSong>>(emptyList()) }
    var selectedPlaylist by remember { mutableStateOf<UserPlaylistItem?>(null) }
    var selectedCollectionType by rememberSaveable { mutableStateOf("") }  // "favorites" 或 "playlist"
    var homeAllSongs by remember { mutableStateOf<List<SongInfo>>(emptyList()) }
    var showPlayModePopup by remember { mutableStateOf(false) }
    var homeScrollIndex by rememberSaveable { mutableIntStateOf(0) }
    var homeScrollOffset by rememberSaveable { mutableIntStateOf(0) }
    val homeListState = rememberLazyListState(initialFirstVisibleItemIndex = homeScrollIndex, initialFirstVisibleItemScrollOffset = homeScrollOffset)
    var discoverScrollIndex by rememberSaveable { mutableIntStateOf(0) }
    var discoverScrollOffset by rememberSaveable { mutableIntStateOf(0) }
    val discoverListState = rememberLazyListState(initialFirstVisibleItemIndex = discoverScrollIndex, initialFirstVisibleItemScrollOffset = discoverScrollOffset)
    var homeClickRefresh by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isHomeRefreshing by remember { mutableStateOf(false) }
    // 滚动或切页时自动收回播放模式弹窗
    LaunchedEffect(homeListState.isScrollInProgress) {
        if (homeListState.isScrollInProgress && showPlayModePopup) showPlayModePopup = false
    }
    LaunchedEffect(selectedTab) {
        if (showPlayModePopup) showPlayModePopup = false
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
    var playerWaveformSlider by remember { mutableStateOf(settingsRepository.playerWaveformSlider) }
    var playerLyricsWordEffect by remember { mutableStateOf(settingsRepository.playerLyricsWordEffect) }
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
    LaunchedEffect(currentSong?.filePath) {
        val fp = currentSong?.filePath ?: return@LaunchedEffect
        val parts = fp.split("|")
        val hash = parts.getOrElse(0) { "" }
        val audioId = parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L
        if (hash.isBlank()) {
            isCurrentSongFavorite = false
            return@LaunchedEffect
        }
        val key = "${hash}|${audioId}"
        isCurrentSongFavorite = collectionDao.isSongCollected(key)
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
        // 获取或注册 dfid
        KuGouApi.clearServerCookies()
        try {
            val resp = KuGouApi.service.registerDev()
            if (!resp.data?.dfid.isNullOrBlank()) {
                KuGouApi.dfid = resp.data!!.dfid!!
                settingsRepository.dfid = KuGouApi.dfid
            }
        } catch (_: Exception) {
            val savedDfid = settingsRepository.dfid
            if (!savedDfid.isNullOrBlank()) KuGouApi.dfid = savedDfid
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

    // 手机返回键：统一的返回处理，替代原先散落的多个 BackHandler
    val backEnabled = showLoginPage || showRankDetail != null || showPlayerPage ||
        selectedTab == 3 || selectedTab in 4..7 || selectedTab in 8..10 ||
        selectedTab == 11 || selectedTab == 12 || selectedTab == 14 ||
        selectedTab == 15 || selectedTab == 16
    androidx.activity.compose.BackHandler(enabled = backEnabled) {
        when {
            showLoginPage -> showLoginPage = false
            showRankDetail != null -> {
                rankLocateAction = null
                showRankDetail = null
            }
            showPlayerPage -> {
                // 返回键两级：歌词页先回播放页，播放页再收起（带滑出动画）
                if (playerPagerState.currentPage == 1) {
                    scope.launch { playerPagerState.animateScrollToPage(0) }
                } else {
                    scope.launch {
                        isRevealAnimating = true
                        try {
                            playerRevealY.animateTo(screenHeightPx, tween(350, easing = FastOutSlowInEasing))
                        } finally {
                            isRevealAnimating = false
                        }
                        showPlayerPage = false
                    }
                }
            }
            selectedTab == 3 -> selectedTab = 2
            selectedTab in 4..7 -> selectedTab = 0
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
        }
    }

    // 下一首
    fun playNext() = playerViewModel.playNext()

    // 上一首
    fun playPrevious() = playerViewModel.playPrevious()

    // 获取屏幕缩放比例
    val contentScaleFactor = LocalScaleFactor.current
    val spacingScaleFactor = ScreenAdapter.getSpacingScaleFactor()

    // 实测底栏高度（px），用于迷你播放条贴住底栏（对齐 NeriPlayer 的 bottomBarLayoutInsets）
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navBarInsetPx = WindowInsets.navigationBars.getBottom(density)

    // 迷你播放条底部间距：原生主题下用实测底栏高度（含导航栏 inset 的差值），
    // 保证与底栏严丝合缝；悬浮主题保持胶囊悬浮间距
    val showNavBar = selectedTab !in 3..16 && showRankDetail == null && !showLoginPage
    val miniPlayerBottomPadding by animateDpAsState(
        targetValue = when {
            !showNavBar -> 0.dp
            floatingBottomBar -> 66.dp * spacingScaleFactor
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
        // 背景图片（铺满整个屏幕，包括标题栏区域，始终显示）
        if (backgroundImageUri != null) {
            val painter = rememberAsyncImagePainter(model = Uri.parse(backgroundImageUri))
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(bgOpacity),
                contentScale = ContentScale.Crop
            )
        }
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val isSearch = targetState == 11 || initialState == 11
                        if (isSearch) {
                            (fadeIn(tween(260)) + slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { if (targetState == 11) it / 6 else -it / 6 }) togetherWith
                                (fadeOut(tween(180)) + slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { if (targetState == 11) -it / 6 else it / 6 })
                        } else {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(150))
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
                            externalQuery = searchQuery
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
                                                val nextMode = if (isDark) "light" else "dark"

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
                                            "display" -> "主题设置"
                                            "customize" -> "自定义个性化"
                                            "motion" -> "动效设置"
                                            "player" -> "播放器设置"
                                            "playback" -> "播放设置"
                                            "usb" -> "USB 独占模式"
                                            "general" -> "通用设置"
                                            "storage" -> "存储与缓存"
                                            "proxy" -> "代理设置"
                                            "about" -> "关于应用"
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
                    } else {
                        TopAppBar(
                            title = {
                                if (currentTab == 0 && homeAllSongs.isNotEmpty()) {
                                    var homePlayMode by remember { mutableIntStateOf(settingsRepository.homePlayMode) }
                                    // "Lx Music" + 音符 整体可点击/长按
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onTap = {
                                                        when (homePlayMode) {
                                                            0 -> playOnlineSong(homeAllSongs, 0)
                                                            1 -> playOnlineSong(homeAllSongs.shuffled(), 0)
                                                            2 -> playOnlineSong(homeAllSongs.shuffled(), 0)
                                                        }
                                                    },
                                                    onLongPress = { showPlayModePopup = !showPlayModePopup }
                                                )
                                            }
                                            .background(
                                                if (showPlayModePopup) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                                else Color.Transparent,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(stringResource(R.string.app_name))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = stringResource(R.string.action_play_all),
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        // 展开的模式选择条
                                        AnimatedVisibility(
                                            visible = showPlayModePopup,
                                            enter = expandHorizontally(tween(200)) + fadeIn(tween(200)),
                                            exit = shrinkHorizontally(tween(150)) + fadeOut(tween(150))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(start = 6.dp),
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                val modes = listOf(
                                                    Triple(Icons.Default.QueueMusic, "顺序", 0),
                                                    Triple(Icons.Default.Shuffle, "随机", 1),
                                                    Triple(Icons.Default.Favorite, "心动", 2)
                                                )
                                                modes.forEach { (icon, label, mode) ->
                                                    val isSelected = homePlayMode == mode
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(
                                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                                else Color.Transparent
                                                            )
                                                            .clickable {
                                                                homePlayMode = mode
                                                                settingsRepository.homePlayMode = mode
                                                                showPlayModePopup = false
                                                            }
                                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                                    ) {
                                                        Icon(
                                                            icon,
                                                            contentDescription = label,
                                                            modifier = Modifier.size(20.dp),
                                                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                                                                   else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            label,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontSize = 10.sp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                                                    else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text("Lx Music")
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
                                IconButton(onClick = {
                                    previousTab = selectedTab
                                    searchQuery = ""
                                    searchSelectedType = "song"
                                    selectedTab = 11
                                }) {
                                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.title_search))
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

            Box(
                modifier = Modifier

                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {

                // 始终显示标签页内容（保持 Composition 不销毁，避免搜索返回后图片闪烁）
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) togetherWith
                                slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(200))
                        } else {
                            slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300)) togetherWith
                                slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(200))
                        }
                    },
                    label = "tabTransition"
                ) { tab ->
                    when (tab) {
                        0 -> {
                            // 保存滚动位置
                            LaunchedEffect(homeListState.firstVisibleItemIndex, homeListState.firstVisibleItemScrollOffset) {
                                homeScrollIndex = homeListState.firstVisibleItemIndex
                                homeScrollOffset = homeListState.firstVisibleItemScrollOffset
                            }
                            HomePage(
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
                                onClickRefresh = { homeClickRefresh = it },
                                onRefreshStateChange = { isHomeRefreshing = it }
                            )
                        }
                        1 -> {
                            LaunchedEffect(discoverListState.firstVisibleItemIndex, discoverListState.firstVisibleItemScrollOffset) {
                                discoverScrollIndex = discoverListState.firstVisibleItemIndex
                                discoverScrollOffset = discoverListState.firstVisibleItemScrollOffset
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
                                        listState = discoverListState
                                    )
                                }
                            }
                        }
                        2 -> MinePage(
                            loginVersion = loginVersion,
                            onLocalClick = { selectedTab = 3 },
                            onAvatarClick = { showLoginPage = true },
                            onManagePlaylists = { selectedTab = 15 },
                            onPlaylistDetailClick = { playlist ->
                                selectedPlaylist = playlist
                                selectedTab = when {
                                    playlist.listname == "默认收藏" -> 8
                                    playlist.listname == "我喜欢" -> 9
                                    else -> 10
                                }
                            },
                            onCollectionDetailClick = { type, id ->
                                selectedCollectionType = type
                                selectedCollectionPlaylistId = id
                                selectedTab = 14
                            }
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
                            onLocateReady = { playlistLocateAction = it }
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
                        8, 9, 10 -> {
                            val playlist = when (tab) {
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
                                    onAddToQueueNext = { song -> addToQueueNext(song) }
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
                            onLocateReady = { playlistLocateAction = it }
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
                            onQueryChange = { query -> searchQuery = query },
                            onSelectedTypeChange = { type -> searchSelectedType = type },
                            currentPlayingPath = currentSong?.filePath,
                            isPlaying = isPlaying,
                            onAddToQueueNext = { song -> addToQueueNext(song) }
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
                            onAddToQueueNext = { song -> addToQueueNext(song) }
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
                                if (!enabled) {
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
                            hapticEnabled = hapticEnabled,
                            onHapticEnabledChange = onHapticEnabledChange,
                            preferHighRefreshRate = preferHighRefreshRate,
                            onPreferHighRefreshRateChange = onPreferHighRefreshRateChange,
                            settingsSubPage = settingsSubPage,
                            onSettingsSubPageChange = { settingsSubPage = it }
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
                            }
                        )
                    }
                }
            }
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
        visible = currentSong != null && !showPlayerPage && selectedTab != 12,
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
                scope.launch {
                    isRevealAnimating = true
                    playerRevealY.snapTo(miniBarTopY)
                    animateRevealTo(0f, tween(350, easing = FastOutSlowInEasing))
                    showPlayerPage = true
                }
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
                        animateRevealTo(0f, tween(350, easing = FastOutSlowInEasing))
                        showPlayerPage = true
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
            liquidGlass = liquidGlass,
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
                .drawWithCache {
                    onDrawWithContent {
                        val progress =
                            ((miniBarTopY - playerRevealY.value) / miniBarTopY).coerceIn(0f, 1f)
                        // 4 * 28dp * p * (1-p)：p=0 或 1 时为 0，p=0.5 时最大 28dp
                        val corner = 4f * 28.dp.toPx() * progress * (1f - progress)
                        if (corner > 0.5f) {
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
                        } else {
                            this@onDrawWithContent.drawContent()
                        }
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
                                    animateRevealTo(screenHeightPx, tween(350, easing = FastOutSlowInEasing))
                                    showPlayerPage = false
                                } else {
                                    // 回弹到完全展开
                                    animateRevealTo(0f, tween(350, easing = FastOutSlowInEasing))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { animateRevealTo(0f, tween(350, easing = FastOutSlowInEasing)) }
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
                        playMode = playMode,
                        onPlayModeChange = { newMode -> playerViewModel.setPlayMode(newMode) },
                        isFavorite = isCurrentSongFavorite,
                        onFavoriteClick = {
                            val song = currentSong ?: return@PlayerStage
                            scope.launch(Dispatchers.IO) {
                                val parts = song.filePath.split("|")
                                val hash = parts.getOrElse(0) { "" }
                                val audioId = parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L
                                if (hash.isBlank()) return@launch
                                val key = "${hash}|${audioId}"
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
                        playerCoverBlurBg = playerCoverBlurBg,
                        playerLyricsWordEffect = playerLyricsWordEffect,
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

        // 导航栏叠加在底部
        AnimatedVisibility(
            visible = selectedTab !in 3..16 && !showPlayerPage && showRankDetail == null && !showLoginPage,
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
                    val progress = if (miniBarTopY > 0f) {
                        ((miniBarTopY - playerRevealY.value) / miniBarTopY).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    // BoomingMusic 原值：底栏滚出 500px（非 dp）
                    translationY = progress * 500f
                    alpha = if (isMiniBarDragging) 1f - progress else 1f
                }
            ) {
                FloatingBottomBar(selectedTab, { index ->
                    selectedTab = index
                }, blurNavBar, floatingBottomBar, liquidGlass, glassBackdrop,
                if (floatingBottomBar) floatingBarOpacity else navBarOpacity,
                followThemeColor, playerBarWhiteBlend)
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

