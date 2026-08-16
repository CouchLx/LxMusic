package com.example.lxmusic.ui.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import coil.compose.rememberAsyncImagePainter
import com.example.lxmusic.BuildConfig
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.UpdateChecker
import com.example.lxmusic.UpdateInfo
import com.example.lxmusic.VipConfigManager
import com.example.lxmusic.MusicDatabase
import com.example.lxmusic.ui.components.IOLoadingIndicator
import com.example.lxmusic.ui.components.ThemeCustomizationSection
import com.example.lxmusic.ui.components.ThemePreset
import com.example.lxmusic.ui.components.ThemeSeedColorDialog
import com.example.lxmusic.ui.components.NavBarCustomizationSection
import com.example.lxmusic.ui.theme.PALETTE_STYLES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

// ==================== 主题预设本地缓存 ====================

private fun loadThemePresets(prefs: android.content.SharedPreferences): List<ThemePreset> {
    val json = prefs.getString("theme_presets", null)
    android.util.Log.d("LxMusic", "loadThemePresets: json=$json")
    if (json == null) return emptyList()
    return try {
        val jsonArray = JSONArray(json)
        android.util.Log.d("LxMusic", "loadThemePresets: arrayLength=${jsonArray.length()}")
        (0 until jsonArray.length()).map { i ->
            val obj = jsonArray.getJSONObject(i)
            android.util.Log.d("LxMusic", "loadThemePresets: obj[$i]=$obj")
            ThemePreset(
                id = obj.getString("id"),
                name = obj.getString("name"),
                backgroundImageUri = if (obj.has("backgroundImageUri") && !obj.isNull("backgroundImageUri")) 
                    obj.getString("backgroundImageUri") else null
            )
        }
    } catch (e: Exception) {
        android.util.Log.e("LxMusic", "loadThemePresets: FATAL ERROR", e)
        emptyList()
    }
}

private fun saveThemePresets(prefs: android.content.SharedPreferences, presets: List<ThemePreset>) {
    val jsonArray = JSONArray()
    presets.forEach { preset ->
        val obj = JSONObject().apply {
            put("id", preset.id)
            put("name", preset.name)
            put("backgroundImageUri", preset.backgroundImageUri)
        }
        jsonArray.put(obj)
    }
    prefs.edit().putString("theme_presets", jsonArray.toString()).apply()
}

// ==================== 设置页面辅助组件 ====================

@Composable
internal fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
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
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
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
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsPage(
    currentUri: String? = null,
    bgOpacity: Float = 0.5f,
    onPickImage: () -> Unit = {},
    onOpacityChange: (Float) -> Unit = {},
    onReset: () -> Unit = {},
    onLogout: () -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    onThemeColorChange: (String) -> Unit = {},
    onBackgroundImageChange: (String?) -> Unit = {},
    floatingBottomBar: Boolean = true,
    onFloatingBottomBarChange: (Boolean) -> Unit = {},
    liquidGlass: Boolean = false,
    onLiquidGlassChange: (Boolean) -> Unit = {},
    navBarOpacity: Float = 1f,
    onNavBarOpacityChange: (Float) -> Unit = {},
    playerBarOpacity: Float = 1f,
    onPlayerBarOpacityChange: (Float) -> Unit = {},
    followThemeColor: Boolean = false,
    onFollowThemeColorChange: (Boolean) -> Unit = {},
    playerBarWhiteBlend: Float = 0.8f,
    onPlayerBarWhiteBlendChange: (Float) -> Unit = {},
    floatingBarOpacity: Float = 1f,
    onFloatingBarOpacityChange: (Float) -> Unit = {},
    playerBgOpacity: Float = 0.5f,
    onPlayerBgOpacityChange: (Float) -> Unit = {},
    playerBlur: Boolean = false,
    onPlayerBlurChange: (Boolean) -> Unit = {},
    playerDynamicBg: Boolean = true,
    onPlayerDynamicBgChange: (Boolean) -> Unit = {},
    playerMeshBg: Boolean = false,
    onPlayerMeshBgChange: (Boolean) -> Unit = {},
    playerRoundAlbum: Boolean = false,
    onPlayerRoundAlbumChange: (Boolean) -> Unit = {},
    playerRotate: Boolean = false,
    onPlayerRotateChange: (Boolean) -> Unit = {},
    playerBgEnhance: Boolean = false,
    onPlayerBgEnhanceChange: (Boolean) -> Unit = {},
    playerHyperBg: Boolean = false,
    onPlayerHyperBgChange: (Boolean) -> Unit = {},
    playerWaveformSlider: Boolean = true,
    onPlayerWaveformSliderChange: (Boolean) -> Unit = {},
    playerLyricsWordEffect: Boolean = true,
    onPlayerLyricsWordEffectChange: (Boolean) -> Unit = {},
    playerLyricsSeekPreview: Boolean = true,
    onPlayerLyricsSeekPreviewChange: (Boolean) -> Unit = {},
    playerCoverBlurBg: Boolean = false,
    onPlayerCoverBlurBgChange: (Boolean) -> Unit = {},
    playerCoverBlurAmount: Float = 40f,
    onPlayerCoverBlurAmountChange: (Float) -> Unit = {},
    playerCoverBlurDarken: Float = 0.2f,
    onPlayerCoverBlurDarkenChange: (Float) -> Unit = {},
    playerAudioReactive: Boolean = true,
    onPlayerAudioReactiveChange: (Boolean) -> Unit = {},
    playerLyricBlur: Boolean = true,
    onPlayerLyricBlurChange: (Boolean) -> Unit = {},
    playerLyricBlurAmount: Float = 10f,
    onPlayerLyricBlurAmountChange: (Float) -> Unit = {},
    playerTapCoverToLyrics: Boolean = false,
    onPlayerTapCoverToLyricsChange: (Boolean) -> Unit = {},
    playerCompactControls: Boolean = false,
    onPlayerCompactControlsChange: (Boolean) -> Unit = {},
    playerShowTopFavorite: Boolean = false,
    onPlayerShowTopFavoriteChange: (Boolean) -> Unit = {},
    playbackKeepProgress: Boolean = true,
    onPlaybackKeepProgressChange: (Boolean) -> Unit = {},
    playbackKeepMode: Boolean = true,
    onPlaybackKeepModeChange: (Boolean) -> Unit = {},
    playbackBluetoothStop: Boolean = true,
    onPlaybackBluetoothStopChange: (Boolean) -> Unit = {},
    playbackFadeIn: Boolean = false,
    onPlaybackFadeInChange: (Boolean) -> Unit = {},
    playbackFadeInMs: Int = 500,
    onPlaybackFadeInMsChange: (Int) -> Unit = {},
    playbackCrossfadeNext: Boolean = false,
    onPlaybackCrossfadeNextChange: (Boolean) -> Unit = {},
    playbackCrossfadeInMs: Int = 500,
    onPlaybackCrossfadeInMsChange: (Int) -> Unit = {},
    playbackCrossfadeOutMs: Int = 500,
    onPlaybackCrossfadeOutMsChange: (Int) -> Unit = {},
    playbackVolumeNormalization: Boolean = false,
    onPlaybackVolumeNormalizationChange: (Boolean) -> Unit = {},
    playbackVolumeBalance: Float = 0f,
    onPlaybackVolumeBalanceChange: (Float) -> Unit = {},
    playbackHighRes: Boolean = false,
    onPlaybackHighResChange: (Boolean) -> Unit = {},
    playbackPreemptFocus: Boolean = false,
    onPlaybackPreemptFocusChange: (Boolean) -> Unit = {},
    settingsSubPage: String? = null,
    onSettingsSubPageChange: (String?) -> Unit = {},
    themeDarkMode: String = "auto",
    onThemeDarkModeChange: (String) -> Unit = {},
    themeSeedColor: String = "0061A4",
    onThemeSeedColorChange: (String) -> Unit = {},
    themePaletteStyle: String = "TonalSpot",
    onThemePaletteStyleChange: (String) -> Unit = {},
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
    val authPrefs = remember { context.getSharedPreferences("auth", Context.MODE_PRIVATE) }
    val isLoggedIn = authPrefs.getString("token", null) != null
    val navBarHeight = remember {
        val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }
    val navBarDp = with(LocalDensity.current) { navBarHeight.toDp() }

    // 返回手势处理：子页面时返回到主菜单（USB 独占模式为独立顶级入口）
    BackHandler(enabled = settingsSubPage != null) {
        onSettingsSubPageChange(null)
    }
    val settingsPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    // 滚动状态跟随子页面重置
    val scrollState = rememberScrollState()
    LaunchedEffect(settingsSubPage) { scrollState.scrollTo(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* 阻止点击穿透到下方页面 */ }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
        AnimatedContent(
            targetState = settingsSubPage,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(tween(300)) { it } + fadeIn(tween(250)) togetherWith
                        slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(200)))
                        .using(SizeTransform(clip = false))
                } else {
                    (slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(250)) togetherWith
                        slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)))
                        .using(SizeTransform(clip = false))
                }
            },
            label = "settings_nav"
        ) { screen ->
            when (screen) {
                null -> {
                    // ==================== 主菜单 ====================
                    Column {
                        // —— 第一组：主题 / 个性化 / 动效 ——
                        SettingsMenuItem(
                            icon = Icons.Default.Palette,
                            title = "主题设置",
                            subtitle = "明暗模式、动态取色、液态玻璃、导航栏",
                            onClick = { onSettingsSubPageChange("display") }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsMenuItem(
                            icon = Icons.Default.AutoAwesome,
                            title = "自定义个性化",
                            subtitle = "主题模式、主题预设",
                            onClick = { onSettingsSubPageChange("customize") }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsMenuItem(
                            icon = Icons.Default.Bolt,
                            title = "动效设置",
                            subtitle = "流体背景、封面模糊、音频律动、歌词模糊",
                            onClick = { onSettingsSubPageChange("motion") }
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // —— 第二组：播放器 / 通用 ——
                        SettingsMenuItem(
                            icon = Icons.Default.MusicNote,
                            title = "播放器设置",
                            subtitle = "背景不透明度、高斯模糊",
                            onClick = { onSettingsSubPageChange("player") }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsMenuItem(
                            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                            title = "播放设置",
                            subtitle = "淡入淡出、响度均衡、下一首交叉淡化",
                            onClick = { onSettingsSubPageChange("playback") }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsMenuItem(
                            icon = Icons.Default.Settings,
                            title = "通用设置",
                            subtitle = "播放音质",
                            onClick = { onSettingsSubPageChange("general") }
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // —— USB 独占模式（独立顶级入口） ——
                        SettingsMenuItem(
                            icon = Icons.Default.Usb,
                            title = "USB 独占模式",
                            subtitle = if (settingsPrefs.getBoolean("usb_exclusive_playback", false)) {
                                "已开启 · 原生驱动直连 USB DAC"
                            } else {
                                "已关闭 · 绕过系统混音器输出"
                            },
                            onClick = { onSettingsSubPageChange("usb") }
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // —— 第三组：存储 / 代理 ——
                        SettingsMenuItem(
                            icon = Icons.Default.Storage,
                            title = "存储与缓存",
                            subtitle = "缓存占用、缓存上限、清除缓存",
                            onClick = { onSettingsSubPageChange("storage") }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsMenuItem(
                            icon = Icons.Default.Cloud,
                            title = "代理设置",
                            subtitle = "服务器地址、VIP 账号",
                            onClick = { onSettingsSubPageChange("proxy") }
                        )

                        // 退出登录按钮（仅登录后显示）
                        if (isLoggedIn) {
                            Spacer(modifier = Modifier.height(24.dp))
                            val scope = rememberCoroutineScope()
                            var showLogoutDialog by remember { mutableStateOf(false) }
                            
                            Surface(
                                onClick = { showLogoutDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "退出登录",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            
                            // 退出登录确认对话框
                            if (showLogoutDialog) {
                                AlertDialog(
                                    onDismissRequest = { showLogoutDialog = false },
                                    title = { Text("确认退出登录") },
                                    text = { Text("退出登录后将清除本地缓存数据，确定要退出吗？") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showLogoutDialog = false
                                            authPrefs.edit().clear().apply()
                                            KuGouApi.token = ""
                                            KuGouApi.userid = ""
                                            KuGouApi.ownerToken = ""
                                            KuGouApi.ownerUserid = ""
                                            val homePrefs = context.getSharedPreferences("home_cache", Context.MODE_PRIVATE)
                                            homePrefs.edit()
                                                .remove("daily_songs_json")
                                                .remove("vip_songs_json")
                                                .remove("history_songs_json")
                                                .remove("style_songs_json")
                                                .apply()
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val db = MusicDatabase.getDatabase(context)
                                                    db.collectionDao().clearAllData()
                                                } catch (_: Exception) {}
                                            }
                                            onLogout()
                                        }) {
                                            Text("退出", color = MaterialTheme.colorScheme.error)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showLogoutDialog = false }) {
                                            Text("取消")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                "customize" -> {
                    SettingsCustomizeContent(
                        currentUri = currentUri,
                        bgOpacity = bgOpacity,
                        onOpacityChange = onOpacityChange,
                        onReset = onReset,
                        onDynamicColorChange = onDynamicColorChange,
                        onThemeColorChange = onThemeColorChange,
                        onBackgroundImageChange = onBackgroundImageChange,
                        floatingBottomBar = floatingBottomBar,
                        onFloatingBottomBarChange = onFloatingBottomBarChange,
                        settingsPrefs = settingsPrefs
                    )
                }

                "display" -> {
                    SettingsDisplayContent(
                        floatingBottomBar = floatingBottomBar,
                        onFloatingBottomBarChange = onFloatingBottomBarChange,
                        liquidGlass = liquidGlass,
                        onLiquidGlassChange = onLiquidGlassChange,
                        navBarOpacity = navBarOpacity,
                        onNavBarOpacityChange = onNavBarOpacityChange,
                        playerBarOpacity = playerBarOpacity,
                        onPlayerBarOpacityChange = onPlayerBarOpacityChange,
                        followThemeColor = followThemeColor,
                        onFollowThemeColorChange = onFollowThemeColorChange,
                        playerBarWhiteBlend = playerBarWhiteBlend,
                        onPlayerBarWhiteBlendChange = onPlayerBarWhiteBlendChange,
                        floatingBarOpacity = floatingBarOpacity,
                        onFloatingBarOpacityChange = onFloatingBarOpacityChange,
                        themeDarkMode = themeDarkMode,
                        onThemeDarkModeChange = onThemeDarkModeChange,
                        themeSeedColor = themeSeedColor,
                        onThemeSeedColorChange = onThemeSeedColorChange,
                        themePaletteStyle = themePaletteStyle,
                        onThemePaletteStyleChange = onThemePaletteStyleChange,
                        themeColorAnimation = themeColorAnimation,
                        onThemeColorAnimationChange = onThemeColorAnimationChange,
                        onDynamicColorChange = onDynamicColorChange,
                        settingsPrefs = settingsPrefs
                    )
                }

                "player" -> {
                    SettingsPlayerContent(
                        playerBgOpacity = playerBgOpacity,
                        onPlayerBgOpacityChange = onPlayerBgOpacityChange,
                        playerBlur = playerBlur,
                        onPlayerBlurChange = onPlayerBlurChange,
                        playerDynamicBg = playerDynamicBg,
                        onPlayerDynamicBgChange = onPlayerDynamicBgChange,
                        playerMeshBg = playerMeshBg,
                        onPlayerMeshBgChange = onPlayerMeshBgChange,
                        playerRoundAlbum = playerRoundAlbum,
                        onPlayerRoundAlbumChange = onPlayerRoundAlbumChange,
                        playerRotate = playerRotate,
                        onPlayerRotateChange = onPlayerRotateChange,
                        playerBgEnhance = playerBgEnhance,
                        onPlayerBgEnhanceChange = onPlayerBgEnhanceChange,
                        playerWaveformSlider = playerWaveformSlider,
                        onPlayerWaveformSliderChange = onPlayerWaveformSliderChange,
                        playerLyricsWordEffect = playerLyricsWordEffect,
                        onPlayerLyricsWordEffectChange = onPlayerLyricsWordEffectChange,
                        playerLyricsSeekPreview = playerLyricsSeekPreview,
                        onPlayerLyricsSeekPreviewChange = onPlayerLyricsSeekPreviewChange,
                        playerCoverBlurBg = playerCoverBlurBg,
                        onPlayerCoverBlurBgChange = onPlayerCoverBlurBgChange,
                        playerTapCoverToLyrics = playerTapCoverToLyrics,
                        onPlayerTapCoverToLyricsChange = onPlayerTapCoverToLyricsChange,
                        playerCompactControls = playerCompactControls,
                        onPlayerCompactControlsChange = onPlayerCompactControlsChange,
                        playerShowTopFavorite = playerShowTopFavorite,
                        onPlayerShowTopFavoriteChange = onPlayerShowTopFavoriteChange
                    )
                }

                "motion" -> {
                    SettingsMotionContent(
                        playerHyperBg = playerHyperBg,
                        onPlayerHyperBgChange = onPlayerHyperBgChange,
                        playerCoverBlurBg = playerCoverBlurBg,
                        onPlayerCoverBlurBgChange = onPlayerCoverBlurBgChange,
                        playerCoverBlurAmount = playerCoverBlurAmount,
                        onPlayerCoverBlurAmountChange = onPlayerCoverBlurAmountChange,
                        playerCoverBlurDarken = playerCoverBlurDarken,
                        onPlayerCoverBlurDarkenChange = onPlayerCoverBlurDarkenChange,
                        playerAudioReactive = playerAudioReactive,
                        onPlayerAudioReactiveChange = onPlayerAudioReactiveChange,
                        playerLyricBlur = playerLyricBlur,
                        onPlayerLyricBlurChange = onPlayerLyricBlurChange,
                        playerLyricBlurAmount = playerLyricBlurAmount,
                        onPlayerLyricBlurAmountChange = onPlayerLyricBlurAmountChange,
                        playerDynamicBg = playerDynamicBg,
                        onPlayerDynamicBgChange = onPlayerDynamicBgChange,
                        playerMeshBg = playerMeshBg,
                        onPlayerMeshBgChange = onPlayerMeshBgChange,
                        playerBgEnhance = playerBgEnhance,
                        onPlayerBgEnhanceChange = onPlayerBgEnhanceChange
                    )
                }

                "playback" -> {
                    SettingsPlaybackContent(
                        playbackKeepProgress = playbackKeepProgress,
                        onPlaybackKeepProgressChange = onPlaybackKeepProgressChange,
                        playbackKeepMode = playbackKeepMode,
                        onPlaybackKeepModeChange = onPlaybackKeepModeChange,
                        playbackBluetoothStop = playbackBluetoothStop,
                        onPlaybackBluetoothStopChange = onPlaybackBluetoothStopChange,
                        playbackFadeIn = playbackFadeIn,
                        onPlaybackFadeInChange = onPlaybackFadeInChange,
                        playbackFadeInMs = playbackFadeInMs,
                        onPlaybackFadeInMsChange = onPlaybackFadeInMsChange,
                        playbackCrossfadeNext = playbackCrossfadeNext,
                        onPlaybackCrossfadeNextChange = onPlaybackCrossfadeNextChange,
                        playbackCrossfadeInMs = playbackCrossfadeInMs,
                        onPlaybackCrossfadeInMsChange = onPlaybackCrossfadeInMsChange,
                        playbackCrossfadeOutMs = playbackCrossfadeOutMs,
                        onPlaybackCrossfadeOutMsChange = onPlaybackCrossfadeOutMsChange,
                        playbackVolumeNormalization = playbackVolumeNormalization,
                        onPlaybackVolumeNormalizationChange = onPlaybackVolumeNormalizationChange,
                        playbackVolumeBalance = playbackVolumeBalance,
                        onPlaybackVolumeBalanceChange = onPlaybackVolumeBalanceChange,
                        playbackHighRes = playbackHighRes,
                        onPlaybackHighResChange = onPlaybackHighResChange,
                        playbackPreemptFocus = playbackPreemptFocus,
                        onPlaybackPreemptFocusChange = onPlaybackPreemptFocusChange,
                        settingsPrefs = settingsPrefs
                    )
                }

                "usb" -> {
                    SettingsUsbContent(
                        usbStore = remember {
                            com.example.lxmusic.usb.UsbExclusiveSettingsStore(context)
                        }
                    )
                }

                "general" -> {
                    SettingsGeneralContent(
                        settingsPrefs = settingsPrefs,
                        onClearBgImage = onReset,
                        uiDensityScale = uiDensityScale,
                        onUiDensityScaleChange = onUiDensityScaleChange,
                        hapticEnabled = hapticEnabled,
                        onHapticEnabledChange = onHapticEnabledChange,
                        preferHighRefreshRate = preferHighRefreshRate,
                        onPreferHighRefreshRateChange = onPreferHighRefreshRateChange
                    )
                }

                "storage" -> {
                    StorageCacheSection(settingsPrefs)
                }

                "proxy" -> {
                    SettingsProxyContent()
                }
            }
        }

            Spacer(modifier = Modifier.height(navBarDp + 60.dp))
        } // 内层滚动 Column
    }
}

@Composable
internal fun SettingsDisplayContent(
    floatingBottomBar: Boolean,
    onFloatingBottomBarChange: (Boolean) -> Unit,
    liquidGlass: Boolean,
    onLiquidGlassChange: (Boolean) -> Unit,
    navBarOpacity: Float,
    onNavBarOpacityChange: (Float) -> Unit,
    playerBarOpacity: Float,
    onPlayerBarOpacityChange: (Float) -> Unit,
    followThemeColor: Boolean,
    onFollowThemeColorChange: (Boolean) -> Unit,
    playerBarWhiteBlend: Float,
    onPlayerBarWhiteBlendChange: (Float) -> Unit,
    floatingBarOpacity: Float,
    onFloatingBarOpacityChange: (Float) -> Unit,
    themeDarkMode: String = "auto",
    onThemeDarkModeChange: (String) -> Unit = {},
    themeSeedColor: String = "0061A4",
    onThemeSeedColorChange: (String) -> Unit = {},
    themePaletteStyle: String = "TonalSpot",
    onThemePaletteStyleChange: (String) -> Unit = {},
    themeColorAnimation: Boolean = true,
    onThemeColorAnimationChange: (Boolean) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    settingsPrefs: android.content.SharedPreferences
) {
    // 主题模式（主题模式选择已移至"自定义个性化"页面，此处只读）
    val themeMode = settingsPrefs.getString("theme_mode", "dynamic") ?: "dynamic"

    Column {

        // ============ Neri 风格主题设置 ============

        // --- 明暗模式（自动/浅色/深色）---
        var darkMode by remember { mutableStateOf(themeDarkMode) }
        val darkModeOptions = listOf("auto" to "自动", "light" to "浅色", "dark" to "深色")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            darkModeOptions.forEach { (value, label) ->
                val selected = darkMode == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            darkMode = value
                            settingsPrefs.edit().putString("theme_dark_mode", value).apply()
                            onThemeDarkModeChange(value)
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 动态取色（按专辑取色）开关 ---
        var dynamicColorLocal by remember {
            mutableStateOf(settingsPrefs.getBoolean("dynamic_color", true))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "动态取色",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "专辑",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "开启时按当前歌曲专辑封面取色，整套界面随播放变化；关闭后使用种子色",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = dynamicColorLocal,
                onCheckedChange = { enabled ->
                    dynamicColorLocal = enabled
                    settingsPrefs.edit().putBoolean("dynamic_color", enabled).apply()
                    onDynamicColorChange(enabled)
                }
            )
        }

        // --- 主题颜色（种子色）仅原生主题且关闭动态取色时显示；取色风格始终显示 ---
        if (themeMode == "dynamic") {
            Spacer(modifier = Modifier.height(16.dp))

            // 自定义色板（存 prefs，逗号分隔）
            var customPalette by remember {
                mutableStateOf(
                    settingsPrefs.getString("theme_custom_palette", "")?.split(",")
                        ?.filter { it.isNotBlank() } ?: emptyList()
                )
            }
            val saveCustomPalette: (List<String>) -> Unit = { palette ->
                customPalette = palette
                settingsPrefs.edit().putString("theme_custom_palette", palette.joinToString(",")).apply()
            }
            var showSeedDialog by remember { mutableStateOf(false) }
            var showStyleDialog by remember { mutableStateOf(false) }
            val currentSeedHex = themeSeedColor.removePrefix("#").uppercase()

            // --- 种子色入口（动态取色开启时隐藏）---
            if (!dynamicColorLocal) {
                Surface(
                    onClick = {
                        showSeedDialog = true
                    },
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
                        Icon(Icons.Default.ColorLens, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "主题颜色（种子色）",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "基于种子色生成整套配色，背景也会跟随",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // 当前种子色色块
                        val seedColor = runCatching {
                            Color(("#$currentSeedHex").toColorInt())
                        }.getOrDefault(Color(0xFF0061A4))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(seedColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "#$currentSeedHex",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            null,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- 取色风格入口 ---
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                onClick = {
                    showStyleDialog = true
                },
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
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "取色风格",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = themePaletteStyle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 种子色对话框
            if (showSeedDialog) {
                ThemeSeedColorDialog(
                    currentHex = currentSeedHex,
                    palette = customPalette,
                    onDismiss = { showSeedDialog = false },
                    onColorSelected = { hex ->
                        showSeedDialog = false
                        settingsPrefs.edit().putString("theme_seed_color", hex).apply()
                        onThemeSeedColorChange(hex)
                    },
                    onAddColor = { hex ->
                        if (customPalette.none { it.equals(hex, ignoreCase = true) }) {
                            saveCustomPalette(customPalette + hex)
                        }
                    },
                    onRemoveColor = { hex ->
                        saveCustomPalette(customPalette.filter { !it.equals(hex, ignoreCase = true) })
                    }
                )
            }

            // 取色风格对话框
            if (showStyleDialog) {
                AlertDialog(
                    onDismissRequest = { showStyleDialog = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    title = { Text("选择取色风格") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            PALETTE_STYLES.forEach { style ->
                                Surface(
                                    onClick = {
                                        showStyleDialog = false
                                        settingsPrefs.edit().putString("theme_palette_style", style).apply()
                                        onThemePaletteStyleChange(style)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (themePaletteStyle == style) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = themePaletteStyle == style,
                                            onClick = {
                                                showStyleDialog = false
                                                settingsPrefs.edit().putString("theme_palette_style", style).apply()
                                                onThemePaletteStyleChange(style)
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = style,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (themePaletteStyle == style) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showStyleDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 颜色过渡动画开关 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "颜色过渡动画",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "切换主题时全界面颜色平滑过渡（420ms）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = themeColorAnimation,
                onCheckedChange = { enabled ->
                    settingsPrefs.edit().putBoolean("theme_color_animation", enabled).apply()
                    onThemeColorAnimationChange(enabled)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 液态玻璃（实验功能）---
        // 在原生主题和现代化主题下都显示，开关状态互通
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "液态玻璃",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "实验功能",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "底栏添加液态玻璃折射效果，谨慎开启",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = liquidGlass,
                onCheckedChange = { onLiquidGlassChange(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 悬浮底栏设置（仅在现代化主题模式下显示）---
        if (themeMode == "modern") {
            // --- 随主题颜色变化 ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "随主题颜色变化",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "指示器颜色跟随主题主色变化",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = followThemeColor,
                    onCheckedChange = { onFollowThemeColorChange(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

    // --- 导航栏自定义 ---
    var navBarSectionExpanded by remember { mutableStateOf(settingsPrefs.getBoolean("nav_bar_section_expanded", false)) }
    // 指示器点击切换动画速率（仅现代化主题生效）
    var clickAnimationSpeed by remember {
        mutableFloatStateOf(settingsPrefs.getFloat("click_animation_speed", 700f))
    }
    NavBarCustomizationSection(
        isExpanded = navBarSectionExpanded,
        onExpandedChange = { expanded ->
            navBarSectionExpanded = expanded
            settingsPrefs.edit().putBoolean("nav_bar_section_expanded", expanded).apply()
        },
        isModernTheme = themeMode == "modern",
        navBarOpacity = navBarOpacity,
        onNavBarOpacityChange = onNavBarOpacityChange,
        playerBarOpacity = playerBarOpacity,
        onPlayerBarOpacityChange = onPlayerBarOpacityChange,
        playerBarWhiteBlend = playerBarWhiteBlend,
        onPlayerBarWhiteBlendChange = onPlayerBarWhiteBlendChange,
        floatingBarOpacity = floatingBarOpacity,
        onFloatingBarOpacityChange = onFloatingBarOpacityChange,
        clickAnimationSpeed = clickAnimationSpeed,
        onClickAnimationSpeedChange = { value ->
            clickAnimationSpeed = value
            settingsPrefs.edit().putFloat("click_animation_speed", value).apply()
        },
        onResetDefaults = {
            // 还原默认设置
            onNavBarOpacityChange(1f)
            onPlayerBarOpacityChange(1f)
            onPlayerBarWhiteBlendChange(0.8f)
            onFloatingBarOpacityChange(1f)
            clickAnimationSpeed = 700f
            settingsPrefs.edit()
                .putFloat("nav_bar_opacity", 1f)
                .putFloat("player_bar_opacity", 1f)
                .putFloat("player_bar_white_blend", 0.8f)
                .putFloat("floating_bar_opacity", 1f)
                .putFloat("click_animation_speed", 700f)
                .apply()
        },
        onAutoBalance = {
            // 自动平衡颜色设置
            onNavBarOpacityChange(0.85f)
            onPlayerBarOpacityChange(0.85f)
            onPlayerBarWhiteBlendChange(0.5f)
            onFloatingBarOpacityChange(0.85f)
            settingsPrefs.edit()
                .putFloat("nav_bar_opacity", 0.85f)
                .putFloat("player_bar_opacity", 0.85f)
                .putFloat("player_bar_white_blend", 0.5f)
                .putFloat("floating_bar_opacity", 0.85f)
                .apply()
        }
    )

    Spacer(modifier = Modifier.height(16.dp))

    } // Column
}

@Composable
internal fun SettingsCustomizeContent(
    currentUri: String?,
    bgOpacity: Float,
    onOpacityChange: (Float) -> Unit,
    onReset: () -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeColorChange: (String) -> Unit,
    onBackgroundImageChange: (String?) -> Unit,
    floatingBottomBar: Boolean,
    onFloatingBottomBarChange: (Boolean) -> Unit,
    settingsPrefs: android.content.SharedPreferences
) {
    // 主题预设管理 - 使用本地缓存
    var themePresets by remember {
        mutableStateOf(loadThemePresets(settingsPrefs))
    }
    // 当前应用的预设ID
    var appliedPresetId by remember { mutableStateOf(settingsPrefs.getString("applied_preset_id", null)) }
    // 展开状态持久化
    var isExpanded by remember { mutableStateOf(settingsPrefs.getBoolean("theme_section_expanded", false)) }

    // 保存预设到本地缓存
    val savePresets: (List<ThemePreset>) -> Unit = { presets ->
        themePresets = presets
        saveThemePresets(settingsPrefs, presets)
    }

    // 应用预设：仅应用背景图片，不再干预主题颜色/种子色/动态取色
    val applyPreset: (ThemePreset) -> Unit = { preset ->
        onBackgroundImageChange(preset.backgroundImageUri)
        appliedPresetId = preset.id
        settingsPrefs.edit().putString("applied_preset_id", preset.id).apply()
    }

    Column {
        // --- 主题模式选择条 ---
        var themeMode by remember { mutableStateOf(settingsPrefs.getString("theme_mode", "dynamic") ?: "dynamic") }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 原生主题选项
            val isDynamic = themeMode == "dynamic"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDynamic) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        themeMode = "dynamic"
                        settingsPrefs.edit().putString("theme_mode", "dynamic").apply()
                        // 切换到原生主题时：关闭悬浮底栏；动态取色开关由主题设置页控制，不强行覆盖
                        onFloatingBottomBarChange(false)
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "原生主题",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isDynamic) FontWeight.Bold else FontWeight.Normal,
                    color = if (isDynamic) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }

            // 现代化主题选项
            val isModern = themeMode == "modern"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isModern) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        themeMode = "modern"
                        settingsPrefs.edit().putString("theme_mode", "modern").apply()
                        // 切换到现代化主题时：开启悬浮底栏，关闭动态颜色，应用默认黑色主题
                        onFloatingBottomBarChange(true)
                        onDynamicColorChange(false)
                        onThemeColorChange("#000000")
                        settingsPrefs.edit()
                            .putBoolean("dynamic_color", false)
                            .apply()
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "现代化主题",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isModern) FontWeight.Bold else FontWeight.Normal,
                    color = if (isModern) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        // --- 主题自定义 ---
        ThemeCustomizationSection(
            presets = themePresets,
            isExpanded = isExpanded,
            onExpandedChange = { expanded ->
                isExpanded = expanded
                settingsPrefs.edit().putBoolean("theme_section_expanded", expanded).apply()
            },
            onAddPreset = { preset, applyNow ->
                savePresets(themePresets + preset)
                if (applyNow) applyPreset(preset)
            },
            onDeletePreset = { presetId ->
                savePresets(themePresets.filter { it.id != presetId })
                // 如果删除的是当前应用的预设，还原默认主题
                if (presetId == appliedPresetId) {
                    appliedPresetId = null
                    settingsPrefs.edit().remove("applied_preset_id").apply()
                    onReset()
                }
            },
            onApplyPreset = { preset ->
                applyPreset(preset)
            },
            onEditPreset = { editedPreset, applyNow ->
                savePresets(themePresets.map { if (it.id == editedPreset.id) editedPreset else it })
                if (applyNow) applyPreset(editedPreset)
            },
            appliedPresetId = appliedPresetId,
            themeMode = themeMode,
            currentBgImageUri = currentUri,
            hasBackgroundImage = currentUri != null,
            bgOpacity = bgOpacity,
            onOpacityChange = onOpacityChange,
            onResetDefaults = {
                // 恢复默认预设：清除背景图片与当前预设标记，不干预主题颜色/动态取色
                onBackgroundImageChange(null)
                appliedPresetId = null
                settingsPrefs.edit().remove("applied_preset_id").apply()
                // 重置不透明度
                onOpacityChange(0.5f)
            },
            onClearBackgroundImage = {
                // 仅清除背景图片
                onBackgroundImageChange(null)
                appliedPresetId = null
                settingsPrefs.edit().remove("applied_preset_id").apply()
            }
        )
    }
}

@Composable
internal fun SettingsGeneralContent(
    settingsPrefs: android.content.SharedPreferences,
    onClearBgImage: () -> Unit = {},
    uiDensityScale: Float = 1f,
    onUiDensityScaleChange: (Float) -> Unit = {},
    hapticEnabled: Boolean = true,
    onHapticEnabledChange: (Boolean) -> Unit = {},
    preferHighRefreshRate: Boolean = false,
    onPreferHighRefreshRateChange: (Boolean) -> Unit = {}
) {
    val settingsContext = LocalContext.current
    Column {
    // --- 播放音质 ---
    var selectedQuality by remember {
        mutableStateOf(settingsPrefs.getString("audio_quality", null) ?: "default")
    }
    val qualityOptions = listOf(
        "default" to "默认",
        "128" to "标准 (128kbps)",
        "320" to "高品质 (320kbps)",
        "flac" to "无损 (FLAC)",
        "high" to "Hi-Res 无损"
    )
    var showQualityDialog by remember { mutableStateOf(false) }
    val currentQualityLabel = qualityOptions.find { it.first == selectedQuality }?.second ?: "默认"

    Text(
        text = "播放音质",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        onClick = { showQualityDialog = true },
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
            Icon(Icons.Default.MusicNote, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "当前音质",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = currentQualityLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("选择播放音质", color = Color(0xFF1A1A1A)) },
            text = {
                Column {
                    qualityOptions.forEach { (value, label) ->
                        Surface(
                            onClick = {
                                selectedQuality = value
                                val qualityValue = if (value == "default") null else value
                                KuGouApi.audioQuality = qualityValue
                                settingsPrefs.edit().putString("audio_quality", qualityValue).apply()
                                showQualityDialog = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedQuality == value)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                Color.Transparent,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedQuality == value,
                                    onClick = {
                                        selectedQuality = value
                                        val qualityValue = if (value == "default") null else value
                                        KuGouApi.audioQuality = qualityValue
                                        settingsPrefs.edit().putString("audio_quality", qualityValue).apply()
                                        showQualityDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (selectedQuality == value)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // --- 通用设置 ---
    Text(
        text = "通用",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    // 触感反馈
    var hapticEnabledLocal by remember { mutableStateOf(hapticEnabled) }
    SettingsSwitchItem(
        icon = Icons.Default.TouchApp,
        title = "触感反馈",
        description = "点击、拖动等操作时提供轻微震动反馈",
        checked = hapticEnabledLocal,
        onCheckedChange = { enabled ->
            hapticEnabledLocal = enabled
            settingsPrefs.edit().putBoolean("haptic_feedback_enabled", enabled).apply()
            onHapticEnabledChange(enabled)
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    // 高刷新率
    var highRefreshLocal by remember { mutableStateOf(preferHighRefreshRate) }
    SettingsSwitchItem(
        icon = Icons.Default.AutoAwesome,
        title = "高刷新率",
        description = "在支持的屏幕上优先使用高刷新率（120Hz 等），更流畅但更耗电",
        checked = highRefreshLocal,
        onCheckedChange = { enabled ->
            highRefreshLocal = enabled
            settingsPrefs.edit().putBoolean("prefer_high_refresh_rate", enabled).apply()
            onPreferHighRefreshRateChange(enabled)
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    // UI 缩放（点击打开对话框，应用后立即生效）
    var showUiScaleDialog by remember { mutableStateOf(false) }
    SettingsChoiceItem(
        title = "UI 缩放",
        subtitle = "调整界面整体大小（图标、文字、间距）",
        currentLabel = "${(uiDensityScale * 100).toInt()}%",
        onClick = { showUiScaleDialog = true }
    )
    if (showUiScaleDialog) {
        UiScaleDialog(
            currentScale = uiDensityScale,
            onDismiss = { showUiScaleDialog = false },
            onApply = { newScale ->
                showUiScaleDialog = false
                settingsPrefs.edit().putFloat("ui_density_scale", newScale).apply()
                onUiDensityScaleChange(newScale)
            }
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 暂停后退出播放服务
    var idleShutdownMinutes by remember {
        mutableIntStateOf(settingsPrefs.getInt("playback_service_idle_shutdown_minutes", 0))
    }
    var showIdleDialog by remember { mutableStateOf(false) }
    SettingsChoiceItem(
        title = "暂停后退出播放服务",
        subtitle = "暂停一段时间后自动停止后台播放服务，省电",
        currentLabel = idleShutdownLabel(idleShutdownMinutes),
        onClick = { showIdleDialog = true }
    )
    if (showIdleDialog) {
        IdleShutdownDialog(
            current = idleShutdownMinutes,
            onDismiss = { showIdleDialog = false },
            onSelect = { minutes ->
                idleShutdownMinutes = minutes
                settingsPrefs.edit()
                    .putInt("playback_service_idle_shutdown_minutes", minutes).apply()
                showIdleDialog = false
            }
        )
    }
    } // Column
}

@Composable
internal fun SettingsProxyContent() {
    val context = LocalContext.current
    val settingsPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    Column {
    // --- 服务器地址设置 ---
    var serverUrl by remember { mutableStateOf(settingsPrefs.getString("server_url", null) ?: KuGouApi.DEFAULT_BASE_URL) }
    var showServerDialog by remember { mutableStateOf(false) }
    var newServerInput by remember { mutableStateOf("") }
    var serverHidden by remember { mutableStateOf(settingsPrefs.getBoolean("server_hidden", false)) }
    var showDefaultAddr by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }

    Text(
        text = "服务器地址",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "当前服务器",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (serverHidden) "http://***"
                       else if (serverUrl == KuGouApi.DEFAULT_BASE_URL) "默认服务器"
                       else serverUrl,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "默认地址：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (showDefaultAddr) KuGouApi.DEFAULT_BASE_URL else "http://***",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (showDefaultAddr) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (showDefaultAddr) "隐藏" else "查看",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            if (showDefaultAddr) showDefaultAddr = false
                            else showPasswordDialog = true
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { newServerInput = ""; showServerDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("修改服务器") }
                OutlinedButton(
                    onClick = {
                        serverUrl = KuGouApi.DEFAULT_BASE_URL
                        settingsPrefs.edit().remove("server_url").apply()
                        KuGouApi.baseUrl = KuGouApi.DEFAULT_BASE_URL
                        KuGouApi.rebuildService()
                        Toast.makeText(context, "已恢复默认服务器", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("恢复默认") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 隐藏地址开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "隐藏服务器地址",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = serverHidden,
                    onCheckedChange = {
                        serverHidden = it
                        settingsPrefs.edit().putBoolean("server_hidden", it).apply()
                    },
                    modifier = Modifier.height(24.dp)
                )
            }
        }
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("修改服务器地址") },
            text = {
                Column {
                    Text("输入新的服务器地址（包含 http:// 和端口号）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("留空则恢复默认服务器", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newServerInput,
                        onValueChange = { newServerInput = it },
                        label = { Text("服务器地址") },
                        singleLine = true,
                        placeholder = { Text("http://example.com:3000/") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = newServerInput.trim()
                    if (url.isBlank()) {
                        // 留空恢复默认
                        serverUrl = KuGouApi.DEFAULT_BASE_URL
                        settingsPrefs.edit().remove("server_url").apply()
                        KuGouApi.baseUrl = KuGouApi.DEFAULT_BASE_URL
                        KuGouApi.rebuildService()
                        showServerDialog = false
                        Toast.makeText(context, "已恢复默认服务器", Toast.LENGTH_SHORT).show()
                    } else if (url.startsWith("http://") || url.startsWith("https://")) {
                        val finalUrl = if (url.endsWith("/")) url else "$url/"
                        serverUrl = finalUrl
                        settingsPrefs.edit().putString("server_url", finalUrl).apply()
                        KuGouApi.baseUrl = finalUrl
                        KuGouApi.rebuildService()
                        showServerDialog = false
                        Toast.makeText(context, "服务器已切换", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "请输入有效的 http/https 地址", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) { Text("取消") }
            }
        )
    }

    // 密码验证对话框（查看默认服务器地址）
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false; passwordInput = "" },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("管理员验证") },
            text = {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("请输入管理员密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (passwordInput == BuildConfig.LX_ADMIN_PASSWORD) showDefaultAddr = true
                    else Toast.makeText(context, "密码错误", Toast.LENGTH_SHORT).show()
                    showPasswordDialog = false; passwordInput = ""
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false; passwordInput = "" }) { Text("取消") }
            }
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // --- VIP 账号设置（新版：激活码 + 自动更新） ---
    var vipStatus by remember { mutableStateOf(VipConfigManager.getStatus(context)) }
    var showActivateDialog by remember { mutableStateOf(false) }
    var showUnbindDialog by remember { mutableStateOf(false) }
    var activateCodeInput by remember { mutableStateOf("") }
    var isActivating by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var activateError by remember { mutableStateOf<String?>(null) }
    var refreshResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 更新检查状态
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    Text(
        text = "VIP 服务",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 状态显示
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                vipStatus.canUseVip -> Color(0xFF4CAF50)
                                vipStatus.isActivated && !vipStatus.isDeviceBound -> Color(0xFFFF9800)
                                else -> Color(0xFF9E9E9E)
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = vipStatus.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        vipStatus.canUseVip -> Color(0xFF4CAF50)
                        vipStatus.isActivated && !vipStatus.isDeviceBound -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // 设备信息
            if (vipStatus.isActivated) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "设备ID: ${VipConfigManager.getDeviceId(context)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "激活码: ${vipStatus.activateCode ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按钮
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!vipStatus.canUseVip) {
                    // 未激活或未绑定：显示激活按钮
                    Button(
                        onClick = { showActivateDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("激活 VIP") }
                } else {
                    // 已激活：显示刷新和解绑按钮
                    Button(
                        onClick = {
                            isRefreshing = true
                            refreshResult = null
                            scope.launch {
                                val result = VipConfigManager.refreshToken(context)
                                isRefreshing = false
                                refreshResult = result.getOrElse { it.message ?: "刷新失败" }
                                vipStatus = VipConfigManager.getStatus(context)
                                Toast.makeText(
                                    context,
                                    refreshResult,
                                    if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = !isRefreshing,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isRefreshing) {
                            IOLoadingIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                dotSize = 6.dp,
                                spacing = 4.dp
                            )
                        } else {
                            Text("立即刷新")
                        }
                    }
                    OutlinedButton(
                        onClick = { showUnbindDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("解绑", color = MaterialTheme.colorScheme.error) }
                }
            }

            // 自动更新开关
            if (vipStatus.canUseVip) {
                Spacer(modifier = Modifier.height(8.dp))
                var autoUpdate by remember {
                    mutableStateOf(VipConfigManager.isAutoUpdateEnabled(context))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "自动更新 Token",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoUpdate,
                        onCheckedChange = {
                            autoUpdate = it
                            VipConfigManager.setAutoUpdateEnabled(context, it)
                        },
                        modifier = Modifier.height(24.dp)
                    )
                }
                Text(
                    text = "开启后，APP 会自动从服务器获取最新 Token",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 激活对话框
    if (showActivateDialog) {
        AlertDialog(
            onDismissRequest = { showActivateDialog = false; activateError = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("激活 VIP 服务") },
            text = {
                Column {
                    Text(
                        "请输入管理员提供的激活码，每个激活码只能绑定一台设备",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = activateCodeInput,
                        onValueChange = { activateCodeInput = it; activateError = null },
                        label = { Text("激活码") },
                        singleLine = true,
                        isError = activateError != null,
                        supportingText = if (activateError != null) {
                            { Text(activateError!!, color = MaterialTheme.colorScheme.error) }
                        } else null
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "设备ID: ${VipConfigManager.getDeviceId(context)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (activateCodeInput.isBlank()) {
                            activateError = "请输入激活码"
                            return@TextButton
                        }
                        isActivating = true
                        activateError = null
                        scope.launch {
                            val result = VipConfigManager.activate(context, activateCodeInput.trim())
                            isActivating = false
                            if (result.isSuccess) {
                                showActivateDialog = false
                                activateCodeInput = ""
                                vipStatus = VipConfigManager.getStatus(context)
                                Toast.makeText(context, result.getOrDefault("激活成功"), Toast.LENGTH_SHORT).show()
                            } else {
                                activateError = result.exceptionOrNull()?.message ?: "激活失败"
                            }
                        }
                    },
                    enabled = !isActivating
                ) {
                    if (isActivating) {
                        IOLoadingIndicator(
                            modifier = Modifier.size(20.dp),
                            dotSize = 5.dp,
                            spacing = 3.dp
                        )
                    } else {
                        Text("激活")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showActivateDialog = false; activateError = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 解绑确认对话框
    if (showUnbindDialog) {
        AlertDialog(
            onDismissRequest = { showUnbindDialog = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("确认解绑") },
            text = { Text("解绑后该设备将无法使用 VIP 功能，需要重新激活。确定要解绑吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        VipConfigManager.clearActivation(context)
                        vipStatus = VipConfigManager.getStatus(context)
                        showUnbindDialog = false
                        Toast.makeText(context, "已解绑", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("解绑", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showUnbindDialog = false }) { Text("取消") }
            }
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // --- 关于（版本号 + 检查更新） ---
    Text(
        text = "关于",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 版本信息
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "版本号",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // 检查更新按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "检查新版本",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isCheckingUpdate) {
                    IOLoadingIndicator(
                        modifier = Modifier.size(20.dp),
                        dotSize = 5.dp,
                        spacing = 3.dp
                    )
                } else {
                    TextButton(onClick = {
                        scope.launch {
                            isCheckingUpdate = true
                            val info = UpdateChecker.checkForUpdate()
                            isCheckingUpdate = false
                            if (info == null) {
                                Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                            } else {
                                updateInfo = info
                            }
                        }
                    }) { Text("检查更新") }
                }
            }
            Text(
                text = "更新说明以 GitHub Releases 发布页为准",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 发现新版本对话框
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null; downloadError = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("发现新版本 v${info.versionName}") },
            text = {
                Column {
                    Text(
                        text = info.desc.ifBlank { "有新版本可用，是否立即更新？" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "当前版本 v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "正在下载 ${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    downloadError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                if (isDownloading) {
                    TextButton(onClick = { updateInfo = null; downloadError = null }) { Text("取消") }
                } else {
                    TextButton(
                        onClick = {
                            scope.launch {
                                isDownloading = true
                                downloadError = null
                                val file = UpdateChecker.downloadApk(
                                    context,
                                    info.apkUrl,
                                    onProgress = { downloaded, total ->
                                        if (total > 0) {
                                            downloadProgress = downloaded.toFloat() / total
                                        }
                                    }
                                )
                                isDownloading = false
                                if (file != null) {
                                    updateInfo = null
                                    Toast.makeText(context, "下载完成，正在安装...", Toast.LENGTH_SHORT).show()
                                    UpdateChecker.installApk(context, file)
                                } else {
                                    downloadError = "下载失败，请检查网络后重试"
                                }
                            }
                        }
                    ) { Text("立即更新") }
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { updateInfo = null; downloadError = null }) { Text("稍后") }
                }
            }
        )
    }
    } // Column
}

// ==================== 播放器设置子页面 ====================

@Composable
fun SettingsPlayerContent(
    playerBgOpacity: Float,
    onPlayerBgOpacityChange: (Float) -> Unit,
    playerBlur: Boolean,
    onPlayerBlurChange: (Boolean) -> Unit,
    playerDynamicBg: Boolean,
    onPlayerDynamicBgChange: (Boolean) -> Unit,
    playerMeshBg: Boolean,
    onPlayerMeshBgChange: (Boolean) -> Unit,
    playerRoundAlbum: Boolean,
    onPlayerRoundAlbumChange: (Boolean) -> Unit,
    playerRotate: Boolean,
    onPlayerRotateChange: (Boolean) -> Unit,
    playerBgEnhance: Boolean,
    onPlayerBgEnhanceChange: (Boolean) -> Unit,
    playerWaveformSlider: Boolean,
    onPlayerWaveformSliderChange: (Boolean) -> Unit,
    playerLyricsWordEffect: Boolean,
    onPlayerLyricsWordEffectChange: (Boolean) -> Unit,
    playerLyricsSeekPreview: Boolean,
    onPlayerLyricsSeekPreviewChange: (Boolean) -> Unit,
    playerCoverBlurBg: Boolean,
    onPlayerCoverBlurBgChange: (Boolean) -> Unit,
    playerTapCoverToLyrics: Boolean,
    onPlayerTapCoverToLyricsChange: (Boolean) -> Unit,
    playerCompactControls: Boolean,
    onPlayerCompactControlsChange: (Boolean) -> Unit,
    playerShowTopFavorite: Boolean,
    onPlayerShowTopFavoriteChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    Column {
        Text(
            text = "背景效果",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 动态渐变背景
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "动态渐变背景",
                subtitle = "根据专辑封面颜色生成全屏渐变，随播放旋转流动",
                checked = playerDynamicBg,
                onCheckedChange = { enabled ->
                    onPlayerDynamicBgChange(enabled)
                    // 与「应用背景图片」「动态背景2」「封面模糊背景」互斥
                    if (enabled) {
                        if (playerBgEnhance) onPlayerBgEnhanceChange(false)
                        if (playerMeshBg) onPlayerMeshBgChange(false)
                        if (playerCoverBlurBg) onPlayerCoverBlurBgChange(false)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 动态背景2（Mesh 渐变）
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "动态背景2（Mesh 渐变）",
                subtitle = "Apple 风格网格渐变，随专辑颜色流动，播放时动效更明显",
                checked = playerMeshBg,
                onCheckedChange = { enabled ->
                    onPlayerMeshBgChange(enabled)
                    // 与「动态渐变背景」「应用背景图片」「封面模糊背景」互斥
                    if (enabled) {
                        if (playerDynamicBg) onPlayerDynamicBgChange(false)
                        if (playerBgEnhance) onPlayerBgEnhanceChange(false)
                        if (playerCoverBlurBg) onPlayerCoverBlurBgChange(false)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "背景不透明度",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "调节播放器背景图片的清晰度（仅对背景图片生效）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("透明", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = playerBgOpacity,
                        onValueChange = onPlayerBgOpacityChange,
                        valueRange = 0.1f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("清晰", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = String.format("当前: %.0f%%", playerBgOpacity * 100),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "高级效果",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 波浪进度条
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "波浪进度条",
                subtitle = "自绘正弦波进度条，播放时波浪流动，加载时脉冲动画",
                checked = playerWaveformSlider,
                onCheckedChange = onPlayerWaveformSliderChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 高斯模糊
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "高斯模糊",
                subtitle = "给背景图片添加模糊和高级质感",
                checked = playerBlur,
                onCheckedChange = onPlayerBlurChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 圆形专辑封面
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "圆形专辑封面",
                subtitle = "将专辑封面显示为圆形",
                checked = playerRoundAlbum,
                onCheckedChange = onPlayerRoundAlbumChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 播放旋转动画（依赖圆形封面）
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "播放旋转动画",
                subtitle = "播放时专辑封面旋转",
                checked = playerRotate,
                onCheckedChange = onPlayerRotateChange,
                enabled = playerRoundAlbum
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 应用背景图片（与动态渐变互斥）
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "应用背景图片",
                subtitle = "使用显示设置中选择的背景图片",
                checked = playerBgEnhance,
                onCheckedChange = { enabled ->
                    onPlayerBgEnhanceChange(enabled)
                    // 与「动态渐变背景」「动态背景2」「封面模糊背景」互斥
                    if (enabled) {
                        if (playerDynamicBg) onPlayerDynamicBgChange(false)
                        if (playerMeshBg) onPlayerMeshBgChange(false)
                        if (playerCoverBlurBg) onPlayerCoverBlurBgChange(false)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "歌词效果",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 逐字歌词动效
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "逐字歌词动效",
                subtitle = "当前歌词行逐字点亮推进；关闭后保持现有样式，仅整行高亮",
                checked = playerLyricsWordEffect,
                onCheckedChange = onPlayerLyricsWordEffectChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 拖动歌词预览
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "拖动歌词预览",
                subtitle = "拖动进度条时实时预览歌词位置，划过歌词行产生震动反馈",
                checked = playerLyricsSeekPreview,
                onCheckedChange = onPlayerLyricsSeekPreviewChange
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "界面显示",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 点击封面切换歌词
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "点击封面切换歌词",
                subtitle = "点击专辑封面卡直接滑到歌词卡（左滑/点歌词预览条始终可用）",
                checked = playerTapCoverToLyrics,
                onCheckedChange = onPlayerTapCoverToLyricsChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 精简控制栏
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "精简控制栏",
                subtitle = "仅歌词卡生效：隐藏播放模式与播放列表按钮，控制键贴底、进度条下沉到最底",
                checked = playerCompactControls,
                onCheckedChange = onPlayerCompactControlsChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 顶栏收藏按钮
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "顶栏收藏按钮",
                subtitle = "仅歌词卡生效：隐藏顶栏音质标签，替换为收藏（喜欢）按钮",
                checked = playerShowTopFavorite,
                onCheckedChange = onPlayerShowTopFavoriteChange
            )
        }
    }
}

/**
 * 播放器设置页的开关行
 */
@Composable
private fun PlayerSettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(alpha),
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
        Switch(
            checked = checked && enabled,
            onCheckedChange = { onCheckedChange(it) },
            enabled = enabled
        )
    }
}

// ==================== 动效设置子页面（对齐 Neri 动效设置） ====================

@Composable
fun SettingsMotionContent(
    playerHyperBg: Boolean,
    onPlayerHyperBgChange: (Boolean) -> Unit,
    playerCoverBlurBg: Boolean,
    onPlayerCoverBlurBgChange: (Boolean) -> Unit,
    playerCoverBlurAmount: Float,
    onPlayerCoverBlurAmountChange: (Float) -> Unit,
    playerCoverBlurDarken: Float,
    onPlayerCoverBlurDarkenChange: (Float) -> Unit,
    playerAudioReactive: Boolean,
    onPlayerAudioReactiveChange: (Boolean) -> Unit,
    playerLyricBlur: Boolean,
    onPlayerLyricBlurChange: (Boolean) -> Unit,
    playerLyricBlurAmount: Float,
    onPlayerLyricBlurAmountChange: (Float) -> Unit,
    playerDynamicBg: Boolean,
    onPlayerDynamicBgChange: (Boolean) -> Unit,
    playerMeshBg: Boolean,
    onPlayerMeshBgChange: (Boolean) -> Unit,
    playerBgEnhance: Boolean,
    onPlayerBgEnhanceChange: (Boolean) -> Unit
) {
    Column {
        // ==================== 播放页背景 ====================
        Text(
            text = "播放页背景",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 流体动态背景（HyperBackground，API 33+ 生效）
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "流体动态背景",
                subtitle = if (Build.VERSION.SDK_INT >= 33) {
                    "GLSL 流体着色器，随专辑封面颜色流动、随音乐律动（需 Android 13+）"
                } else {
                    "需要 Android 13 及以上版本"
                },
                checked = playerHyperBg,
                enabled = Build.VERSION.SDK_INT >= 33,
                onCheckedChange = { enabled ->
                    onPlayerHyperBgChange(enabled)
                    // 与「动态渐变背景」「动态背景2」「应用背景图片」「封面模糊背景」互斥
                    if (enabled) {
                        if (playerDynamicBg) onPlayerDynamicBgChange(false)
                        if (playerMeshBg) onPlayerMeshBgChange(false)
                        if (playerBgEnhance) onPlayerBgEnhanceChange(false)
                        if (playerCoverBlurBg) onPlayerCoverBlurBgChange(false)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 封面模糊背景（Android 12+，与动态背景/流体背景互斥）
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "封面模糊背景",
                subtitle = if (Build.VERSION.SDK_INT >= 31) {
                    "将当前专辑封面模糊铺满屏幕（与动态背景互斥）"
                } else {
                    "需要 Android 12 及以上版本"
                },
                checked = playerCoverBlurBg,
                enabled = Build.VERSION.SDK_INT >= 31,
                onCheckedChange = { enabled ->
                    onPlayerCoverBlurBgChange(enabled)
                    // 与「动态渐变背景」「动态背景2」「应用背景图片」「流体动态背景」互斥
                    if (enabled) {
                        if (playerDynamicBg) onPlayerDynamicBgChange(false)
                        if (playerMeshBg) onPlayerMeshBgChange(false)
                        if (playerBgEnhance) onPlayerBgEnhanceChange(false)
                        if (playerHyperBg) onPlayerHyperBgChange(false)
                    }
                }
            )
        }

        if (playerCoverBlurBg) {
            Spacer(modifier = Modifier.height(8.dp))

            // 封面模糊强度
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "封面模糊强度",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("柔和", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = playerCoverBlurAmount,
                            onValueChange = onPlayerCoverBlurAmountChange,
                            valueRange = 0f..100f,
                            steps = 19,
                            modifier = Modifier.weight(1f)
                        )
                        Text("强烈", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = String.format("当前: %.0f", playerCoverBlurAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 背景调暗
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "背景调暗",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("明亮", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = playerCoverBlurDarken,
                            onValueChange = onPlayerCoverBlurDarkenChange,
                            valueRange = 0f..0.8f,
                            steps = 15,
                            modifier = Modifier.weight(1f)
                        )
                        Text("暗", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = String.format("当前: %.0f%%", playerCoverBlurDarken * 100),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 音频律动（依赖流体动态背景）
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "音频律动",
                subtitle = if (Build.VERSION.SDK_INT >= 33) {
                    "流体动态背景随音乐音量律动（需开启流体动态背景）"
                } else {
                    "需要 Android 13 及以上版本"
                },
                checked = playerAudioReactive,
                enabled = Build.VERSION.SDK_INT >= 33 && playerHyperBg,
                onCheckedChange = onPlayerAudioReactiveChange
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==================== 歌词动效 ====================
        Text(
            text = "歌词动效",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 高级歌词动效（对齐 Neri：现代排版与动效；关闭=朴素排版）
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "歌词模糊",
                subtitle = "非当前歌词行添加模糊效果，突出当前行",
                checked = playerLyricBlur,
                onCheckedChange = onPlayerLyricBlurChange
            )
        }

        if (playerLyricBlur) {
            Spacer(modifier = Modifier.height(8.dp))

            // 歌词模糊强度
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "歌词模糊强度",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("轻", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = playerLyricBlurAmount,
                            onValueChange = onPlayerLyricBlurAmountChange,
                            valueRange = 0f..30f,
                            steps = 29,
                            modifier = Modifier.weight(1f)
                        )
                        Text("重", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = String.format("当前: %.0f", playerLyricBlurAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ==================== 播放设置子页面（对齐 Neri 播放设置） ====================

@Composable
fun SettingsPlaybackContent(
    playbackKeepProgress: Boolean,
    onPlaybackKeepProgressChange: (Boolean) -> Unit,
    playbackKeepMode: Boolean,
    onPlaybackKeepModeChange: (Boolean) -> Unit,
    playbackBluetoothStop: Boolean,
    onPlaybackBluetoothStopChange: (Boolean) -> Unit,
    playbackFadeIn: Boolean,
    onPlaybackFadeInChange: (Boolean) -> Unit,
    playbackFadeInMs: Int,
    onPlaybackFadeInMsChange: (Int) -> Unit,
    playbackCrossfadeNext: Boolean,
    onPlaybackCrossfadeNextChange: (Boolean) -> Unit,
    playbackCrossfadeInMs: Int,
    onPlaybackCrossfadeInMsChange: (Int) -> Unit,
    playbackCrossfadeOutMs: Int,
    onPlaybackCrossfadeOutMsChange: (Int) -> Unit,
    playbackVolumeNormalization: Boolean,
    onPlaybackVolumeNormalizationChange: (Boolean) -> Unit,
    playbackVolumeBalance: Float,
    onPlaybackVolumeBalanceChange: (Float) -> Unit,
    playbackHighRes: Boolean,
    onPlaybackHighResChange: (Boolean) -> Unit,
    playbackPreemptFocus: Boolean,
    onPlaybackPreemptFocusChange: (Boolean) -> Unit,
    settingsPrefs: android.content.SharedPreferences
) {
    val context = LocalContext.current

    Column {
        // ==================== 播放行为 ====================
        Text(
            text = "播放行为",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "保留上次播放进度",
                subtitle = "重启应用后恢复上次播放位置",
                checked = playbackKeepProgress,
                onCheckedChange = onPlaybackKeepProgressChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "保留播放状态",
                subtitle = "重启后保留列表循环/随机播放模式",
                checked = playbackKeepMode,
                onCheckedChange = onPlaybackKeepModeChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "蓝牙断开自动停止",
                subtitle = "蓝牙/耳机断开后自动暂停播放（采样确认防误判）",
                checked = playbackBluetoothStop,
                onCheckedChange = onPlaybackBluetoothStopChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 播放服务空闲退出（分钟选择）
        val idleMinutes = settingsPrefs.getInt("playback_service_idle_shutdown_minutes", 0)
        var showIdleDialog by remember { mutableStateOf(false) }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showIdleDialog = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "暂停后退出播放服务",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (idleMinutes > 0) {
                            "暂停 $idleMinutes 分钟后自动退出"
                        } else {
                            "不自动退出"
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
        }
        if (showIdleDialog) {
            AlertDialog(
                onDismissRequest = { showIdleDialog = false },
                title = { Text("暂停后退出播放服务") },
                text = {
                    Column {
                        listOf(
                            0 to "不自动退出",
                            5 to "5 分钟",
                            15 to "15 分钟",
                            30 to "30 分钟",
                            60 to "60 分钟",
                            120 to "120 分钟"
                        ).forEach { (minutes, label) ->
                            val selected = idleMinutes == minutes
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        settingsPrefs.edit()
                                            .putInt("playback_service_idle_shutdown_minutes", minutes)
                                            .apply()
                                        context.sendBroadcast(
                                            Intent("com.example.lxmusic.SETTINGS_CHANGED")
                                        )
                                        showIdleDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showIdleDialog = false }) { Text("取消") }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==================== 音频输出 ====================
        Text(
            text = "音频输出",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "32-bit 高解析输出",
                subtitle = "优先使用高精度浮点输出（重启生效）",
                checked = playbackHighRes,
                onCheckedChange = onPlaybackHighResChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "响度均衡",
                subtitle = "自动平衡不同歌曲的音量",
                checked = playbackVolumeNormalization,
                onCheckedChange = onPlaybackVolumeNormalizationChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 声道平衡滑块
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "声道平衡",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("偏左", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = playbackVolumeBalance,
                        onValueChange = onPlaybackVolumeBalanceChange,
                        valueRange = -1f..1f,
                        steps = 40,
                        modifier = Modifier.weight(1f)
                    )
                    Text("偏右", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = when {
                        playbackVolumeBalance == 0f -> "居中"
                        playbackVolumeBalance < 0f -> "偏左 ${(abs(playbackVolumeBalance) * 100).toInt()}%"
                        else -> "偏右 ${(playbackVolumeBalance * 100).toInt()}%"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "抢占音频焦点",
                subtitle = "启动时主动获取系统音频焦点",
                checked = playbackPreemptFocus,
                onCheckedChange = onPlaybackPreemptFocusChange
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==================== 播放淡入淡出 ====================
        Text(
            text = "播放淡入淡出",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "播放淡入淡出",
                subtitle = "开始播放时音量平滑渐入",
                checked = playbackFadeIn,
                onCheckedChange = onPlaybackFadeInChange
            )
        }

        if (playbackFadeIn) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "淡入时长",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = playbackFadeInMs.toFloat(),
                        onValueChange = { onPlaybackFadeInMsChange(it.toInt()) },
                        valueRange = 0f..3000f,
                        steps = 29,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${playbackFadeInMs} ms",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==================== 下一首淡入淡出 ====================
        Text(
            text = "下一首淡入淡出",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerSettingSwitchRow(
                title = "交叉淡化",
                subtitle = "切歌时淡出当前曲并淡入下一首",
                checked = playbackCrossfadeNext,
                onCheckedChange = onPlaybackCrossfadeNextChange
            )
        }

        if (playbackCrossfadeNext) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "下一首淡入时长",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = playbackCrossfadeInMs.toFloat(),
                        onValueChange = { onPlaybackCrossfadeInMsChange(it.toInt()) },
                        valueRange = 0f..3000f,
                        steps = 29,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${playbackCrossfadeInMs} ms",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "当前曲淡出时长",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = playbackCrossfadeOutMs.toFloat(),
                        onValueChange = { onPlaybackCrossfadeOutMsChange(it.toInt()) },
                        valueRange = 0f..3000f,
                        steps = 29,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${playbackCrossfadeOutMs} ms",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
