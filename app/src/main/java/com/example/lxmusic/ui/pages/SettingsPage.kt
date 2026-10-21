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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.WaterDrop
import com.example.lxmusic.ui.components.AppUpdateDialog
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil.compose.rememberAsyncImagePainter
import com.example.lxmusic.BuildConfig
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.NeteaseApi
import com.example.lxmusic.UpdateChecker
import com.example.lxmusic.UpdateInfo
import com.example.lxmusic.VipConfigManager
import com.example.lxmusic.CollectionBackup
import com.example.lxmusic.CollectionBackupIO
import com.example.lxmusic.BackupStats
import com.example.lxmusic.ImportMode
import com.example.lxmusic.MusicDatabase
import com.example.lxmusic.backup.AutoBackupRunner
import com.example.lxmusic.backup.AutoBackupScheduler
import com.example.lxmusic.backup.BackupOutcome
import com.example.lxmusic.backup.BackupReason
import com.example.lxmusic.backup.BackupStorage
import com.example.lxmusic.ui.components.IOLoadingIndicator
import com.example.lxmusic.ui.components.ThemeCustomizationSection
import com.example.lxmusic.ui.components.ThemePreset
import com.example.lxmusic.ui.components.ThemeSeedColorDialog
import com.example.lxmusic.ui.components.NavBarCustomizationSection
import com.example.lxmusic.ui.theme.PALETTE_STYLES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/**
 * 设置分组卡片：将一组相关的设置项外层包裹在一个圆角卡片中（类似 iOS / Material 3 Inset Grouped）
 */
@Composable
internal fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

/**
 * 设置分组卡片内的单个条目行
 */
@Composable
internal fun SettingsGroupItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    statusBadge: String? = null,
    showDivider: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!statusBadge.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = statusBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 58.dp, end = 16.dp),
                thickness = 0.6.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}

/**
 * 现代化胶囊分段选择器（用于 2~4 个互斥选项切换，如歌词渲染模式、控制栏形态等）
 */
@Composable
internal fun ModernSegmentedPill(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (key, label) ->
                val isSelected = key == selectedKey
                Surface(
                    onClick = { onSelect(key) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * 播放器视觉背景预设卡片
 */
@Composable
internal fun BackgroundPresetCard(
    title: String,
    tag: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    previewBrush: Brush,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(previewBrush)
                    .border(0.8.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            RadioButton(
                selected = selected,
                onClick = onClick,
                enabled = enabled
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
    playerDynamicBg: Boolean = false,
    onPlayerDynamicBgChange: (Boolean) -> Unit = {},
    playerRoundAlbum: Boolean = true,
    onPlayerRoundAlbumChange: (Boolean) -> Unit = {},
    playerRotate: Boolean = true,
    onPlayerRotateChange: (Boolean) -> Unit = {},
    playerVinylStyle: Boolean = true,
    onPlayerVinylStyleChange: (Boolean) -> Unit = {},
    playerVinylPointer: Boolean = true,
    onPlayerVinylPointerChange: (Boolean) -> Unit = {},
    playerVinylBase: Boolean = false,
    onPlayerVinylBaseChange: (Boolean) -> Unit = {},
    playerBgEnhance: Boolean = false,
    onPlayerBgEnhanceChange: (Boolean) -> Unit = {},
    playerHyperBg: Boolean = true,
    onPlayerHyperBgChange: (Boolean) -> Unit = {},
    playerFluidAmbientBg: Boolean = false,
    onPlayerFluidAmbientBgChange: (Boolean) -> Unit = {},
    playerMeshBg: Boolean = false,
    onPlayerMeshBgChange: (Boolean) -> Unit = {},
    playerWaveformSlider: Boolean = false,
    onPlayerWaveformSliderChange: (Boolean) -> Unit = {},
    playerLyricsWordEffect: Boolean = true,
    onPlayerLyricsWordEffectChange: (Boolean) -> Unit = {},
    playerMeiloxLyrics: Boolean = false,
    onPlayerMeiloxLyricsChange: (Boolean) -> Unit = {},
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
    playerLyricBlur: Boolean = false,
    onPlayerLyricBlurChange: (Boolean) -> Unit = {},
    playerLyricBlurAmount: Float = 10f,
    onPlayerLyricBlurAmountChange: (Float) -> Unit = {},
    playerTapCoverToLyrics: Boolean = false,
    onPlayerTapCoverToLyricsChange: (Boolean) -> Unit = {},
    playerCompactControls: Boolean = false,
    onPlayerCompactControlsChange: (Boolean) -> Unit = {},
    playerMinimalistControls: Boolean = true,
    onPlayerMinimalistControlsChange: (Boolean) -> Unit = {},
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
    var onAddWallpaperAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    Box(
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
                        // 计算动态状态徽标（大厂级交互：主页一览即知当前关键配置）
                        val appearanceModeText = when (themeDarkMode) {
                            "dark" -> "深色"
                            "light" -> "浅色"
                            else -> "跟随系统"
                        }
                        val appearanceThemeText = if (settingsPrefs.getString("theme_mode", "dynamic") == "modern") "现代化" else "原生"
                        val appearanceBadge = "$appearanceModeText · $appearanceThemeText"

                        val playerStageBadge = when {
                            playerHyperBg -> "灵动流体"
                            playerMeshBg -> "Mesh频谱"
                            playerCoverBlurBg -> "封面模糊"
                            playerDynamicBg -> "封面渐变"
                            playerFluidAmbientBg -> "极简弥散"
                            else -> "极简黑调"
                        }

                        val audioQualityBadge = when (settingsPrefs.getString("audio_quality", null)) {
                            "128" -> "标准 128k"
                            "320" -> "高品质 320k"
                            "high" -> "Hi-Res"
                            else -> "无损 FLAC"
                        }

                        // —— 第 1 组：视听体验（外观与个性化 / 播放界面与动效 / 声音与播放） ——
                        SettingsGroupCard {
                            SettingsGroupItemRow(
                                icon = Icons.Default.Palette,
                                title = "外观与个性化",
                                subtitle = "主题色彩、深浅模式、动效与背景壁纸",
                                statusBadge = appearanceBadge,
                                onClick = { onSettingsSubPageChange("appearance") },
                                showDivider = true
                            )
                            SettingsGroupItemRow(
                                icon = Icons.Default.MusicNote,
                                title = "播放界面与动效",
                                subtitle = "黑胶唱机、动效氛围、歌词排版与播放控制",
                                statusBadge = playerStageBadge,
                                onClick = { onSettingsSubPageChange("player_stage") },
                                showDivider = true
                            )
                            SettingsGroupItemRow(
                                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                title = "声音与播放",
                                subtitle = "播放音质、淡入淡出、响度均衡与输出偏好",
                                statusBadge = audioQualityBadge,
                                onClick = { onSettingsSubPageChange("playback") },
                                showDivider = false
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // —— 第 2 组：系统与交互（通用与交互 / USB 独占模式） ——
                        SettingsGroupCard {
                            SettingsGroupItemRow(
                                icon = Icons.Default.Settings,
                                title = "通用与交互",
                                subtitle = "首页模块排序、触觉反馈、高刷新率",
                                onClick = { onSettingsSubPageChange("general") },
                                showDivider = true
                            )
                            SettingsGroupItemRow(
                                icon = Icons.Default.Usb,
                                title = "USB 独占模式",
                                subtitle = "原生驱动直连 USB DAC，绕过系统混音输出",
                                statusBadge = if (settingsPrefs.getBoolean("usb_exclusive_playback", false)) "已开启" else "已关闭",
                                onClick = { onSettingsSubPageChange("usb") },
                                showDivider = false
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // —— 第 3 组：数据与关于（存储与缓存 / 激活与在线服务 / 关于应用） ——
                        SettingsGroupCard {
                            SettingsGroupItemRow(
                                icon = Icons.Default.Storage,
                                title = "存储与缓存",
                                subtitle = "歌曲缓存占用、缓存上限与清理临时数据",
                                onClick = { onSettingsSubPageChange("storage") },
                                showDivider = true
                            )
                            SettingsGroupItemRow(
                                icon = Icons.Default.Cloud,
                                title = "激活与在线服务",
                                subtitle = "激活码、在线服务连接与自建后端",
                                onClick = { onSettingsSubPageChange("proxy") },
                                showDivider = true
                            )
                            SettingsGroupItemRow(
                                icon = Icons.Default.Info,
                                title = "关于应用",
                                subtitle = "版本信息、开源仓库与检查更新",
                                onClick = { onSettingsSubPageChange("about") },
                                showDivider = false
                            )
                        }

                        // 退出登录按钮（仅登录后显示）
                        if (isLoggedIn) {
                            Spacer(modifier = Modifier.height(16.dp))
                            val scope = rememberCoroutineScope()
                            var showLogoutDialog by remember { mutableStateOf(false) }
                            
                            SettingsGroupCard {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showLogoutDialog = true }
                                        .padding(horizontal = 18.dp, vertical = 15.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "退出登录",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            
                            // 退出登录确认对话框
                            if (showLogoutDialog) {
                                AlertDialog(
                                    onDismissRequest = { showLogoutDialog = false },
                                    title = { Text("确认退出登录") },
                                    text = { Text("退出后将清除酷狗账号的收藏歌单与喜欢记录；本地「我的收藏」和自建歌单会保留。确定要退出吗？") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showLogoutDialog = false
                                            authPrefs.edit().clear().apply()
                                            KuGouApi.token = ""
                                            KuGouApi.userid = ""
                                            // 清掉旧账号的会话 Cookie（t1/vip_type 等），防止与下一个
                                            // 登录账号的凭据混搭导致数据串号；设备类 Cookie 保留
                                            KuGouApi.clearAuthCookies()
                                            // 注意：不要清 ownerToken/ownerUserid（VIP 借号凭据）。
                                            // 激活是按设备绑定的，与登录哪个账号无关；清掉后取流会退化用
                                            // 当前账号 token，未激活账号将无法播放 VIP 歌，直到下次重启才恢复。
                                            val homePrefs = context.getSharedPreferences("home_cache", Context.MODE_PRIVATE)
                                            homePrefs.edit()
                                                .remove("daily_songs_json")
                                                .remove("vip_songs_json")
                                                .remove("history_songs_json")
                                                .remove("style_songs_json")
                                                .apply()
                                            // 清掉「我的」页缓存的在线歌单（带账号标识）
                                            val minePrefs = context.getSharedPreferences("mine_state", Context.MODE_PRIVATE)
                                            minePrefs.edit()
                                                .remove("playlists_json")
                                                .remove("playlists_json_uid")
                                                .remove("last_load_auth")
                                                .apply()
                                            // 清掉酷狗「喜欢」歌单的缓存，避免串账号
                                            KuGouApi.clearKugouLikeCache()
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val db = MusicDatabase.getDatabase(context)
                                                    // 只清酷狗账号镜像（喜欢/收藏歌单）；本地「我的收藏」与自建歌单保留
                                                    db.collectionDao().clearKugouAccountData()
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

                "appearance", "display", "customize" -> {
                    SettingsAppearanceContent(
                        themeDarkMode = themeDarkMode,
                        onThemeDarkModeChange = onThemeDarkModeChange,
                        themeSeedColor = themeSeedColor,
                        onThemeSeedColorChange = onThemeSeedColorChange,
                        themePaletteStyle = themePaletteStyle,
                        onThemePaletteStyleChange = onThemePaletteStyleChange,
                        themeColorAnimation = themeColorAnimation,
                        onThemeColorAnimationChange = onThemeColorAnimationChange,
                        onDynamicColorChange = onDynamicColorChange,
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
                        settingsPrefs = settingsPrefs,
                        currentUri = currentUri,
                        bgOpacity = bgOpacity,
                        onOpacityChange = onOpacityChange,
                        onBackgroundImageChange = onBackgroundImageChange,
                        onRegisterPickAction = { action ->
                            onAddWallpaperAction = action
                        }
                    )
                }

                "player_stage", "player", "motion" -> {
                    SettingsPlayerStageContent(
                        playerDynamicBg = playerDynamicBg,
                        onPlayerDynamicBgChange = onPlayerDynamicBgChange,
                        playerHyperBg = playerHyperBg,
                        onPlayerHyperBgChange = onPlayerHyperBgChange,
                        playerFluidAmbientBg = playerFluidAmbientBg,
                        onPlayerFluidAmbientBgChange = onPlayerFluidAmbientBgChange,
                        playerMeshBg = playerMeshBg,
                        onPlayerMeshBgChange = onPlayerMeshBgChange,
                        playerCoverBlurBg = playerCoverBlurBg,
                        onPlayerCoverBlurBgChange = onPlayerCoverBlurBgChange,
                        playerCoverBlurAmount = playerCoverBlurAmount,
                        onPlayerCoverBlurAmountChange = onPlayerCoverBlurAmountChange,
                        playerCoverBlurDarken = playerCoverBlurDarken,
                        onPlayerCoverBlurDarkenChange = onPlayerCoverBlurDarkenChange,
                        playerAudioReactive = playerAudioReactive,
                        onPlayerAudioReactiveChange = onPlayerAudioReactiveChange,
                        playerBgEnhance = playerBgEnhance,
                        onPlayerBgEnhanceChange = onPlayerBgEnhanceChange,
                        playerBgOpacity = playerBgOpacity,
                        onPlayerBgOpacityChange = onPlayerBgOpacityChange,
                        playerBlur = playerBlur,
                        onPlayerBlurChange = onPlayerBlurChange,
                        playerRoundAlbum = playerRoundAlbum,
                        onPlayerRoundAlbumChange = onPlayerRoundAlbumChange,
                        playerRotate = playerRotate,
                        onPlayerRotateChange = onPlayerRotateChange,
                        playerVinylStyle = playerVinylStyle,
                        onPlayerVinylStyleChange = onPlayerVinylStyleChange,
                        playerVinylPointer = playerVinylPointer,
                        onPlayerVinylPointerChange = onPlayerVinylPointerChange,
                        playerVinylBase = playerVinylBase,
                        onPlayerVinylBaseChange = onPlayerVinylBaseChange,
                        playerTapCoverToLyrics = playerTapCoverToLyrics,
                        onPlayerTapCoverToLyricsChange = onPlayerTapCoverToLyricsChange,
                        playerLyricsWordEffect = playerLyricsWordEffect,
                        onPlayerLyricsWordEffectChange = onPlayerLyricsWordEffectChange,
                        playerMeiloxLyrics = playerMeiloxLyrics,
                        onPlayerMeiloxLyricsChange = onPlayerMeiloxLyricsChange,
                        playerLyricBlur = playerLyricBlur,
                        onPlayerLyricBlurChange = onPlayerLyricBlurChange,
                        playerLyricBlurAmount = playerLyricBlurAmount,
                        onPlayerLyricBlurAmountChange = onPlayerLyricBlurAmountChange,
                        playerLyricsSeekPreview = playerLyricsSeekPreview,
                        onPlayerLyricsSeekPreviewChange = onPlayerLyricsSeekPreviewChange,
                        playerMinimalistControls = playerMinimalistControls,
                        onPlayerMinimalistControlsChange = onPlayerMinimalistControlsChange,
                        playerCompactControls = playerCompactControls,
                        onPlayerCompactControlsChange = onPlayerCompactControlsChange,
                        playerWaveformSlider = playerWaveformSlider,
                        onPlayerWaveformSliderChange = onPlayerWaveformSliderChange
                    )
                }

                "playback", "common" -> {
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
                        onOpenHomeOrder = { onSettingsSubPageChange("home_order") }
                    )
                }

                "home_order" -> {
                    HomeOrderSortPage(settingsPrefs = settingsPrefs)
                }

                "storage" -> {
                    StorageCacheSection(settingsPrefs)
                }

                "proxy" -> {
                    SettingsProxyContent()
                }

                "about" -> {
                    SettingsAboutContent()
                }
            }
        }

            Spacer(modifier = Modifier.height(navBarDp + 80.dp))
        } // 内层滚动 Column

        // 当处于「自定义个性化」或「外观」页面时，在屏幕右下角永远固定常驻大号悬浮加号按钮（不随滑动变化）
        if ((settingsSubPage == "customize" || settingsSubPage == "appearance") && onAddWallpaperAction != null) {
            FloatingActionButton(
                onClick = { onAddWallpaperAction?.invoke() },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 12.dp
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp, end = 20.dp)
                    .size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加壁纸",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * 新外观设置页面（包含主题风格、外观模式、色彩与风格等功能项）
 */
@Composable
internal fun SettingsAppearanceContent(
    themeDarkMode: String,
    onThemeDarkModeChange: (String) -> Unit,
    themeSeedColor: String,
    onThemeSeedColorChange: (String) -> Unit,
    themePaletteStyle: String,
    onThemePaletteStyleChange: (String) -> Unit,
    themeColorAnimation: Boolean = true,
    onThemeColorAnimationChange: (Boolean) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    floatingBottomBar: Boolean = false,
    onFloatingBottomBarChange: (Boolean) -> Unit = {},
    liquidGlass: Boolean = false,
    onLiquidGlassChange: (Boolean) -> Unit = {},
    navBarOpacity: Float = 1f,
    onNavBarOpacityChange: (Float) -> Unit = {},
    playerBarOpacity: Float = 1f,
    onPlayerBarOpacityChange: (Float) -> Unit = {},
    followThemeColor: Boolean = true,
    onFollowThemeColorChange: (Boolean) -> Unit = {},
    playerBarWhiteBlend: Float = 0.8f,
    onPlayerBarWhiteBlendChange: (Float) -> Unit = {},
    floatingBarOpacity: Float = 1f,
    onFloatingBarOpacityChange: (Float) -> Unit = {},
    settingsPrefs: android.content.SharedPreferences,
    currentUri: String? = null,
    bgOpacity: Float = 0.5f,
    onOpacityChange: (Float) -> Unit = {},
    onBackgroundImageChange: (String?) -> Unit = {},
    onRegisterPickAction: (((() -> Unit) -> Unit))? = null
) {
    var themeMode by remember { mutableStateOf(settingsPrefs.getString("theme_mode", "dynamic") ?: "dynamic") }

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

    // 动态取色本地状态
    var dynamicColorLocal by remember {
        mutableStateOf(settingsPrefs.getBoolean("dynamic_color", true))
    }

    val paletteStyleDescriptions = remember {
        mapOf(
            "TonalSpot" to "经典色调 · 平衡和谐（推荐）",
            "Neutral" to "中性内敛 · 低饱和，纯净素雅",
            "Vibrant" to "鲜艳活力 · 高饱和，色彩浓郁明快",
            "Expressive" to "表现力 · 跳跃丰富，个性鲜明",
            "Rainbow" to "彩虹光谱 · 多元色阶渐变",
            "FruitSalad" to "水果沙拉 · 活泼多色调组合",
            "Monochrome" to "单色极简 · 现代灰黑白单色",
            "Fidelity" to "色彩保真 · 高保真还原种子色",
            "Content" to "内容自适应 · 深度贴合专辑原色"
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==================== 1. 主题风格（原生 / 现代化） ====================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "主题风格",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SettingsGroupCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isDynamic = themeMode == "dynamic"
                    Surface(
                        onClick = {
                            themeMode = "dynamic"
                            settingsPrefs.edit().putString("theme_mode", "dynamic").apply()
                            onFloatingBottomBarChange(false)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDynamic) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(modifier = Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "原生主题",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isDynamic) FontWeight.Bold else FontWeight.Normal,
                                color = if (isDynamic) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    val isModern = themeMode == "modern"
                    Surface(
                        onClick = {
                            themeMode = "modern"
                            settingsPrefs.edit().putString("theme_mode", "modern").apply()
                            onFloatingBottomBarChange(true)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isModern) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(modifier = Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "现代化主题",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isModern) FontWeight.Bold else FontWeight.Normal,
                                color = if (isModern) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // ==================== 2. 外观模式（深色 / 浅色 / 跟随系统） ====================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "外观模式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SettingsGroupCard {
                val darkModeOptions = listOf(
                    Triple("light", "浅色", Icons.Default.LightMode),
                    Triple("dark", "深色", Icons.Default.DarkMode),
                    Triple("auto", "跟随系统", Icons.Default.SettingsBrightness)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    darkModeOptions.forEach { (value, label, icon) ->
                        val selected = themeDarkMode == value
                        Surface(
                            onClick = {
                                settingsPrefs.edit().putString("theme_dark_mode", value).apply()
                                onThemeDarkModeChange(value)
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                val isSystemDark = isSystemInDarkTheme()
                val modeDescription = when (themeDarkMode) {
                    "auto" -> "已开启跟随系统：当前系统为${if (isSystemDark) "深色" else "浅色"}模式，将随系统自动自适应"
                    "light" -> "已固定为浅色模式（不随系统深浅色变化）"
                    "dark" -> "已固定为深色模式（不随系统深浅色变化）"
                    else -> "跟随系统外观设置"
                }

                Text(
                    text = modeDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }
        }

        // ==================== 3. 色彩与风格 ====================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "色彩与风格",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SettingsGroupCard {
                // (1) 动态取色开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "动态取色",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "专辑封面",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "自动按当前歌曲专辑封面提取主色；关闭后可自定义种子色",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = dynamicColorLocal,
                        onCheckedChange = { enabled ->
                            dynamicColorLocal = enabled
                            settingsPrefs.edit().putBoolean("dynamic_color", enabled).apply()
                            onDynamicColorChange(enabled)
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // (2) 主题颜色（种子色）入口
                val isSeedColorEnabled = !dynamicColorLocal
                val seedColor = remember(currentSeedHex) {
                    runCatching { Color(("#$currentSeedHex").toColorInt()) }.getOrDefault(Color(0xFF0061A4))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isSeedColorEnabled) 1f else 0.45f)
                        .clickable(enabled = isSeedColorEnabled) {
                            showSeedDialog = true
                        }
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "主题颜色（种子色）",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!isSeedColorEnabled) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "已锁定",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isSeedColorEnabled) {
                                "基于种子色生成整套 Material 3 配色，背景与组件自适应"
                            } else {
                                "动态取色已开启（跟随专辑封面）；关闭动态取色后可自定义"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    // 当前种子色预览圆球
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(seedColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "#$currentSeedHex",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isSeedColorEnabled) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // (3) 取色风格选择
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStyleDialog = true }
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "取色风格算法",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = paletteStyleDescriptions[themePaletteStyle] ?: themePaletteStyle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // (4) 颜色平滑过渡动画
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Animation,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "颜色平滑过渡动画",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "切换主题或切歌色彩变化时，全界面颜色进行 420ms 柔和过渡",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = themeColorAnimation,
                        onCheckedChange = { enabled ->
                            settingsPrefs.edit().putBoolean("theme_color_animation", enabled).apply()
                            onThemeColorAnimationChange(enabled)
                        }
                    )
                }
            }
        }

        // ==================== 4. 悬浮底栏与质感折射 ====================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "悬浮底栏与质感折射",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SettingsGroupCard {
                // (1) 液态玻璃
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "液态玻璃",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "实验功能",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "底栏启用 Shader 流体折射与高级毛玻璃模糊，需 GPU 支持",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = liquidGlass,
                        onCheckedChange = { onLiquidGlassChange(it) }
                    )
                }

                if (themeMode == "modern") {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // (2) 随主题颜色变化
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "随主题颜色变化",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "悬浮指示器与选中高亮颜色跟随主题主色变化",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = followThemeColor,
                            onCheckedChange = { onFollowThemeColorChange(it) }
                        )
                    }
                }
            }

            // 导航栏高级自定义折叠面板
            var navBarSectionExpanded by remember { mutableStateOf(settingsPrefs.getBoolean("nav_bar_section_expanded", false)) }
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
        }

        // ==================== 5. 背景壁纸与相册 ====================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "背景壁纸与相册",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            ThemeCustomizationSection(
                onAddPreset = { filePath, _ ->
                    onBackgroundImageChange(filePath.toString())
                },
                currentBgImageUri = currentUri,
                hasBackgroundImage = currentUri != null,
                bgOpacity = bgOpacity,
                onOpacityChange = onOpacityChange,
                onResetDefaults = {
                    onBackgroundImageChange(null)
                    onOpacityChange(0.5f)
                },
                onClearBackgroundImage = {
                    onBackgroundImageChange(null)
                },
                onRegisterPickAction = { action ->
                    onRegisterPickAction?.invoke(action)
                }
            )
        }

        // 种子色选择与高级调色对话框 (BottomSheet)
        if (showSeedDialog) {
            ThemeSeedColorDialog(
                currentHex = currentSeedHex,
                palette = customPalette,
                paletteStyle = themePaletteStyle,
                onPaletteStyleChange = { style ->
                    settingsPrefs.edit().putString("theme_palette_style", style).apply()
                    onThemePaletteStyleChange(style)
                },
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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("选择取色风格算法", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PALETTE_STYLES.forEach { style ->
                            val isSelected = themePaletteStyle == style
                            Surface(
                                onClick = {
                                    showStyleDialog = false
                                    settingsPrefs.edit().putString("theme_palette_style", style).apply()
                                    onThemePaletteStyleChange(style)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            showStyleDialog = false
                                            settingsPrefs.edit().putString("theme_palette_style", style).apply()
                                            onThemePaletteStyleChange(style)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = style,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        paletteStyleDescriptions[style]?.let { desc ->
                                            Text(
                                                text = desc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showStyleDialog = false }) {
                        Text("关闭")
                    }
                }
            )
        }
    }
}

@Composable
internal fun SettingsGeneralContent(
    settingsPrefs: android.content.SharedPreferences,
    onClearBgImage: () -> Unit = {},
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
    onFavoriteSyncLocalChange: (Boolean) -> Unit = {},
    onOpenHomeOrder: () -> Unit = {}
) {
    val settingsContext = LocalContext.current
    Column {
    // --- 首页排序 ---
    Text(
        text = "首页排序",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        onClick = onOpenHomeOrder,
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
            Icon(Icons.Default.Tune, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "首页推荐排序",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "顶部推荐栏、下面歌曲推荐的显示顺序（长按拖动）",
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

    Spacer(modifier = Modifier.height(24.dp))

    // --- 通用设置 ---
    Text(
        text = "通用",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    // 预览化推页栏（开启后首页歌曲区块变成露边轮播推页栏样式）
    var previewPushPageBarLocal by remember {
        mutableStateOf(settingsPrefs.getBoolean("preview_push_page_bar", true))
    }
    SettingsSwitchItem(
        icon = Icons.Default.Animation,
        title = "预览化推页栏",
        description = "开启后主页歌曲推荐将采用滑动露边预览的推页栏卡片样式",
        checked = previewPushPageBarLocal,
        onCheckedChange = { enabled ->
            previewPushPageBarLocal = enabled
            settingsPrefs.edit().putBoolean("preview_push_page_bar", enabled).apply()
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

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

    // 收藏到酷狗官方列表（默认关闭；开启后收藏/喜欢写入酷狗账号的喜欢歌单）
    var favoriteToKugouLocal by remember { mutableStateOf(favoriteToKugou) }
    SettingsSwitchItem(
        icon = Icons.Default.Favorite,
        title = "收藏到酷狗官方列表",
        description = "开启后「喜欢/收藏」歌曲写入酷狗账号的喜欢歌单，关闭恢复本地收藏",
        checked = favoriteToKugouLocal,
        onCheckedChange = { enabled ->
            favoriteToKugouLocal = enabled
            settingsPrefs.edit().putBoolean("favorite_to_kugou", enabled).apply()
            onFavoriteToKugouChange(enabled)
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    // 收藏的歌曲同步到本地收藏（依赖上方官方收藏开启，关闭时置灰不可点）
    var syncLocalFavoriteLocal by remember { mutableStateOf(favoriteSyncLocal) }
    SettingsSwitchItem(
        icon = Icons.Default.FavoriteBorder,
        title = "收藏的歌曲同步到本地收藏",
        description = "开启官方收藏后，喜欢/收藏的歌曲同时加入本地“我的收藏”",
        checked = syncLocalFavoriteLocal && favoriteToKugouLocal,
        onCheckedChange = { enabled ->
            syncLocalFavoriteLocal = enabled
            settingsPrefs.edit().putBoolean("favorite_sync_local", enabled).apply()
            onFavoriteSyncLocalChange(enabled)
        },
        enabled = favoriteToKugouLocal
    )

    Spacer(modifier = Modifier.height(12.dp))

    // ===== 本地收藏数据导出 / 导入（防止卸载丢失本地收藏） =====
    val backupScope = rememberCoroutineScope()
    val backupTimeFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()) }
    val backupFileStampFormat = remember { java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US) }

    var exportConfirmStats by remember { mutableStateOf<BackupStats?>(null) }
    var exportBusy by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<CollectionBackup?>(null) }
    var importParsing by remember { mutableStateOf(false) }
    var importApplying by remember { mutableStateOf(false) }

    fun backupToast(text: String) {
        Toast.makeText(settingsContext, text, Toast.LENGTH_LONG).show()
    }

    // ---- 导出：先确认导出内容，再让用户选择保存位置 ----
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            exportBusy = true
            val result = runCatching {
                val dao = withContext(Dispatchers.IO) { MusicDatabase.getDatabase(settingsContext).collectionDao() }
                withContext(Dispatchers.IO) {
                    settingsContext.contentResolver.openOutputStream(uri)?.use { out ->
                        CollectionBackupIO.export(dao, out)
                    } ?: throw IllegalStateException("无法写入所选文件")
                }
            }
            exportBusy = false
            result
                .onSuccess { stats -> backupToast("导出成功：${stats.summary}") }
                .onFailure { e -> backupToast("导出失败：${e.message ?: "未知错误"}") }
        }
    }

    // ---- 导入：先解析预览，再由用户选择「合并共存 / 清空替换」 ----
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            importParsing = true
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    settingsContext.contentResolver.openInputStream(uri)?.use { input ->
                        CollectionBackupIO.read(input)
                    }
                }.getOrNull()
            }
            importParsing = false
            when {
                parsed == null -> backupToast("导入失败：文件格式不正确")
                parsed.isEmpty -> backupToast("导入失败：备份文件里没有任何数据")
                else -> importPreview = parsed
            }
        }
    }

    fun applyImport(mode: ImportMode) {
        val backup = importPreview ?: return
        importPreview = null
        backupScope.launch {
            importApplying = true
            val result = runCatching {
                val dao = withContext(Dispatchers.IO) { MusicDatabase.getDatabase(settingsContext).collectionDao() }
                withContext(Dispatchers.IO) { CollectionBackupIO.apply(dao, backup, mode) }
            }
            importApplying = false
            result
                .onSuccess { report ->
                    backupToast(if (mode == ImportMode.MERGE) "合并完成：${report.summary}" else "导入完成：${report.summary}")
                }
                .onFailure { e -> backupToast("导入失败：${e.message ?: "未知错误"}") }
        }
    }

    SettingsChoiceItem(
        title = "本地收藏数据导出",
        subtitle = "把本地收藏的歌曲、歌单导出为备份文件，防止卸载/更新丢失",
        currentLabel = if (exportBusy) "准备中…" else "",
        onClick = {
            if (exportBusy) return@SettingsChoiceItem
            backupScope.launch {
                exportBusy = true
                val stats = withContext(Dispatchers.IO) {
                    runCatching {
                        CollectionBackupIO.stats(MusicDatabase.getDatabase(settingsContext).collectionDao())
                    }.getOrNull()
                }
                exportBusy = false
                when {
                    stats == null -> backupToast("读取本地收藏失败")
                    stats.isEmpty -> backupToast("没有可导出的本地收藏数据")
                    else -> exportConfirmStats = stats
                }
            }
        }
    )
    Spacer(modifier = Modifier.height(8.dp))
    SettingsChoiceItem(
        title = "导入本地收藏数据",
        subtitle = "选择之前导出的备份文件，可选「合并共存」或「清空替换」",
        currentLabel = if (importParsing) "读取中…" else "",
        onClick = {
            if (!importParsing && !importApplying) {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }
    )

    // 导出确认弹窗：先让用户看清导出内容，再走系统文件选择器
    exportConfirmStats?.let { stats ->
        AlertDialog(
            onDismissRequest = { exportConfirmStats = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("导出本地收藏数据") },
            text = {
                Column {
                    Text("将导出以下内容：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stats.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "下一步选择保存位置，建议保存在「下载」目录，卸载重装后仍可导入恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    exportConfirmStats = null
                    val name = "lxmusic_collection_backup_${backupFileStampFormat.format(java.util.Date())}.json"
                    exportLauncher.launch(name)
                }) { Text("选择保存位置") }
            },
            dismissButton = {
                TextButton(onClick = { exportConfirmStats = null }) { Text("取消") }
            }
        )
    }

    // 导入预览弹窗：二选一（合并共存 / 清空替换）
    importPreview?.let { backup ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("导入本地收藏数据") },
            text = {
                Column {
                    Text(
                        "备份内容：收藏歌曲 ${backup.collectedSongs.size} 首 · 喜欢 ${backup.likedSongs.size} 首 · " +
                                "自建歌单 ${backup.playlists.size} 个（${backup.playlistSongs.size} 首） · " +
                                "收藏歌单 ${backup.likedPlaylists.size} 个",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (backup.exportedAt > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "备份时间：${backupTimeFormat.format(java.util.Date(backup.exportedAt))}" +
                                    if (backup.appVersion.isNotBlank()) "（版本 ${backup.appVersion}）" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("选择导入方式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        onClick = { applyImport(ImportMode.MERGE) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text(
                                "合并共存（推荐）",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "保留现有数据，只补齐缺失的歌曲和歌单，不会删除任何东西",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        onClick = { applyImport(ImportMode.REPLACE) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text(
                                "清空替换",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "先清空现有本地收藏和自建歌单，再完整还原为备份数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { importPreview = null }) { Text("取消") }
            }
        )
    }

    if (importApplying) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("正在导入") },
            text = { Text("正在写入本地收藏数据，请稍候…") },
            confirmButton = {}
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // ===== 定时自动备份（防丢）=====
    Text(
        text = "自动备份",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    var autoBackupEnabled by remember { mutableStateOf(AutoBackupScheduler.isEnabled(settingsContext)) }
    var autoBackupDays by remember { mutableIntStateOf(AutoBackupScheduler.intervalDays(settingsContext)) }
    var lastBackupTime by remember { mutableLongStateOf(AutoBackupScheduler.lastRunTime(settingsContext)) }
    var lastBackupOk by remember { mutableStateOf(AutoBackupScheduler.lastRunSuccess(settingsContext)) }
    var lastBackupMsg by remember { mutableStateOf(AutoBackupScheduler.lastRunMessage(settingsContext)) }
    var showBackupIntervalDialog by remember { mutableStateOf(false) }
    var manualBackupRunning by remember { mutableStateOf(false) }

    // 按需申请权限：通知权限只影响是否弹提示；存储权限仅 Android 9- 写公共下载目录需要
    var pendingBackupPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val backupPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        val action = pendingBackupPermissionAction
        pendingBackupPermissionAction = null
        action?.invoke()
    }
    fun runWithBackupPermissions(block: () -> Unit) {
        fun withStorage() {
            if (BackupStorage.hasWriteAccess(settingsContext)) {
                block()
            } else {
                pendingBackupPermissionAction = block
                backupPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        val needNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    settingsContext, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needNotification) {
            pendingBackupPermissionAction = { withStorage() }
            backupPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            withStorage()
        }
    }

    SettingsSwitchItem(
        icon = Icons.Default.Backup,
        title = "定时自动备份",
        description = "按设定周期自动把本地收藏导出到 下载/LxMusic 文件夹，防止数据丢失",
        checked = autoBackupEnabled,
        onCheckedChange = { enabled ->
            if (enabled) {
                runWithBackupPermissions {
                    AutoBackupScheduler.schedule(settingsContext, autoBackupDays)
                    autoBackupEnabled = true
                    Toast.makeText(
                        settingsContext,
                        "已开启：${AutoBackupScheduler.intervalLabel(autoBackupDays)}自动备份一次",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                AutoBackupScheduler.cancel(settingsContext)
                autoBackupEnabled = false
            }
        }
    )

    if (autoBackupEnabled) {
        Spacer(modifier = Modifier.height(12.dp))
        SettingsChoiceItem(
            title = "备份周期",
            subtitle = "自动导出本地收藏的时间间隔",
            currentLabel = AutoBackupScheduler.intervalLabel(autoBackupDays),
            onClick = { showBackupIntervalDialog = true }
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    SettingsChoiceItem(
        title = "立即备份一次",
        subtitle = if (lastBackupTime > 0) {
            "上次备份：${backupTimeFormat.format(java.util.Date(lastBackupTime))}" +
                    if (lastBackupOk) "" else "（失败：$lastBackupMsg）"
        } else {
            "还没有备份记录，建议先手动备份一次"
        },
        currentLabel = if (manualBackupRunning) "备份中…" else "",
        onClick = {
            if (manualBackupRunning) return@SettingsChoiceItem
            runWithBackupPermissions {
                backupScope.launch {
                    manualBackupRunning = true
                    val outcome = AutoBackupRunner.run(settingsContext, BackupReason.MANUAL, notify = false)
                    manualBackupRunning = false
                    lastBackupTime = AutoBackupScheduler.lastRunTime(settingsContext)
                    lastBackupOk = AutoBackupScheduler.lastRunSuccess(settingsContext)
                    lastBackupMsg = AutoBackupScheduler.lastRunMessage(settingsContext)
                    backupToast(
                        when (outcome) {
                            is BackupOutcome.Success -> "备份成功：${outcome.location}"
                            is BackupOutcome.Failure -> "备份失败：${outcome.reason}"
                        }
                    )
                }
            }
        }
    )

    if (showBackupIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showBackupIntervalDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("自动备份周期") },
            text = {
                Column {
                    AutoBackupScheduler.INTERVAL_OPTIONS.forEach { (days, label) ->
                        Surface(
                            onClick = {
                                autoBackupDays = days
                                AutoBackupScheduler.schedule(settingsContext, days)
                                showBackupIntervalDialog = false
                                Toast.makeText(
                                    settingsContext,
                                    "已设置为${label}自动备份一次",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (autoBackupDays == days) {
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
                                RadioButton(selected = autoBackupDays == days, onClick = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupIntervalDialog = false }) { Text("取消") }
            }
        )
    }

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

    // 底栏高度（仅原生主题生效；点击打开对话框，应用后立即生效）
    var showBottomBarHeightDialog by remember { mutableStateOf(false) }
    SettingsChoiceItem(
        title = "底栏高度",
        subtitle = "调整底部导航栏高度（原生主题），播放条自动贴紧",
        currentLabel = "${(bottomBarHeightScale * 100).toInt()}%",
        onClick = { showBottomBarHeightDialog = true }
    )
    if (showBottomBarHeightDialog) {
        BottomBarHeightDialog(
            currentScale = bottomBarHeightScale,
            onDismiss = { showBottomBarHeightDialog = false },
            onApply = { newScale ->
                showBottomBarHeightDialog = false
                settingsPrefs.edit().putFloat("bottom_bar_height_scale", newScale).apply()
                onBottomBarHeightScaleChange(newScale)
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
    // --- 在线服务（不展示任何服务器地址；内置后端对普通用户不可见） ---
    val builtInBff = BuildConfig.LX_BFF_URL.trim()
    var bffOverride by remember { mutableStateOf(settingsPrefs.getString("bff_url", "") ?: "") }
    // 内置后端时高级配置默认隐藏：长按「在线服务」标题展开（自建后端/调试用）
    var showBackendAdvanced by remember { mutableStateOf(builtInBff.isBlank()) }
    var showBackendDialog by remember { mutableStateOf(false) }
    var backendInput by remember { mutableStateOf("") }

    Text(
        text = "在线服务",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.pointerInput(builtInBff) {
            detectTapGestures(onLongPress = { showBackendAdvanced = !showBackendAdvanced })
        }
    )
    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "当前模式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    bffOverride.isNotBlank() -> "自定义后端"
                    builtInBff.isNotBlank() -> "内置在线服务"
                    else -> "未配置在线服务（仅本地播放）"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "激活成功后可自动连接在线音乐服务。未激活时在线功能不可用，只能播放本地音乐。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showBackendAdvanced) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        backendInput = ""
                        showBackendDialog = true
                    }) { Text(if (builtInBff.isNotBlank()) "自定义后端地址" else "设置后端地址") }
                    if (bffOverride.isNotBlank()) {
                        TextButton(onClick = {
                            bffOverride = ""
                            settingsPrefs.edit().remove("bff_url").apply()
                            KuGouApi.bffUrl = builtInBff
                            Toast.makeText(context, "已恢复内置后端", Toast.LENGTH_SHORT).show()
                        }) { Text("恢复内置", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (showBackendDialog) {
        AlertDialog(
            onDismissRequest = { showBackendDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("自定义后端地址") },
            text = {
                Column {
                    OutlinedTextField(
                        value = backendInput,
                        onValueChange = { backendInput = it },
                        singleLine = true,
                        placeholder = { Text("http://127.0.0.1:8080/") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "仅自建服务时填写；普通用户留空即可，激活后自动使用内置服务。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = backendInput.trim()
                    if (value.isNotBlank() && !value.startsWith("http://") && !value.startsWith("https://")) {
                        Toast.makeText(context, "请输入有效的 http/https 地址", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    val finalValue = when {
                        value.isBlank() -> ""
                        value.endsWith("/") -> value
                        else -> "$value/"
                    }
                    bffOverride = finalValue
                    if (finalValue.isBlank()) {
                        settingsPrefs.edit().remove("bff_url").apply()
                    } else {
                        settingsPrefs.edit().putString("bff_url", finalValue).apply()
                    }
                    KuGouApi.bffUrl = finalValue.ifBlank { builtInBff }
                    showBackendDialog = false
                    Toast.makeText(
                        context,
                        if (finalValue.isBlank()) "已恢复内置后端" else "后端地址已保存",
                        Toast.LENGTH_SHORT
                    ).show()
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showBackendDialog = false }) { Text("取消") }
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
    var showLoginHint by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "未激活：在线音乐、搜索与歌单不可用，仅能播放本地音乐。",
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
                                // 激活后 BFF 才开始放行数据接口：让首页下次进入时强制刷新一次
                                context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean(
                                        com.example.lxmusic.data.SettingsRepository.Keys.PENDING_HOME_REFRESH,
                                        true
                                    )
                                    .apply()
                                val authPrefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
                                val loggedIn = !authPrefs.getString("token", "").isNullOrBlank() &&
                                    authPrefs.getLong("userid", 0L) != 0L
                                if (loggedIn) {
                                    Toast.makeText(context, "激活成功，正在刷新首页", Toast.LENGTH_SHORT).show()
                                } else {
                                    // 未登录时首页拿不到账号相关数据，提示用户去登录
                                    showLoginHint = true
                                }
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

    // 激活成功后未登录：引导去登录
    if (showLoginHint) {
        AlertDialog(
            onDismissRequest = { showLoginHint = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("激活成功") },
            text = {
                Text(
                    "在线音乐需要登录酷狗账号后才能使用。现在去登录吗？",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLoginHint = false
                    com.example.lxmusic.ui.components.AppUiEvents.requestLogin = true
                }) { Text("去登录") }
            },
            dismissButton = {
                TextButton(onClick = { showLoginHint = false }) { Text("稍后") }
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

    } // Column
}

// ==================== 关于应用子页面 ====================

@Composable
internal fun SettingsAboutContent() {
    val context = LocalContext.current
    val settingsPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var autoCheckUpdate by remember {
        mutableStateOf(settingsPrefs.getBoolean("auto_check_update_dialog", true))
    }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    Column {
        // --- 应用信息 Header 徽章卡片 ---
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "LxMusic",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "高品质全功能优雅 Android 音乐播放器",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 版本与更新设置 ---
        Text(
            text = "版本与更新",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        SettingsGroupCard {
            // 开关：检测到新版本时显示弹窗更新
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newValue = !autoCheckUpdate
                        autoCheckUpdate = newValue
                        settingsPrefs.edit().putBoolean("auto_check_update_dialog", newValue).apply()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "检测到新版本时显示弹窗更新",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "应用启动时自动检测 GitHub 最新版本并弹窗提示更新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = autoCheckUpdate,
                    onCheckedChange = {
                        autoCheckUpdate = it
                        settingsPrefs.edit().putBoolean("auto_check_update_dialog", it).apply()
                    }
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )

            // 检查新版本操作
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isCheckingUpdate) {
                        scope.launch {
                            isCheckingUpdate = true
                            val info = UpdateChecker.checkForUpdate()
                            isCheckingUpdate = false
                            if (info == null) {
                                Toast.makeText(context, "已是最新版本 (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                            } else {
                                updateInfo = info
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "检查新版本",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "当前版本：v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isCheckingUpdate) {
                    IOLoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        dotSize = 5.dp,
                        spacing = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )

            // QQ 测试群
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            // 优先用 mqqapi 协议直接拉起 QQ 加群
                            val qqIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&uin=778350277&card_type=group&source=qrcode")
                            )
                            context.startActivity(qqIntent)
                        } catch (e: Exception) {
                            // 兜底：网页版加群链接
                            try {
                                val webIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://qm.qq.com/cgi-bin/qmqr?k=778350277")
                                )
                                context.startActivity(webIntent)
                            } catch (e2: Exception) {
                                Toast.makeText(context, "无法打开 QQ，请手动搜索群号 778350277 加入", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "加入 QQ 测试群",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "群号 778350277 · 分享测试安装包、反馈问题",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )

            // GitHub Releases 发布页
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.RELEASES_URL))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GitHub Releases 发布页",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "查看所有历史版本发布说明与更新日志",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 开源与项目信息 ---
        Text(
            text = "开源与信息",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        SettingsGroupCard {
            // 开源仓库
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.REPO_URL))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "开源项目主页",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = UpdateChecker.REPO_URL,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )

            // 作者信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "开发者",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = UpdateChecker.GITHUB_OWNER,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )

            // 开源协议
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "开源协议",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Apache-2.0 License",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 发现新版本弹窗
    updateInfo?.let { info ->
        AppUpdateDialog(
            updateInfo = info,
            onDismiss = { updateInfo = null },
            onIgnoreVersion = {
                UpdateChecker.setVersionIgnored(context, info.versionName)
                Toast.makeText(context, "已忽略 v${info.versionName} 版本提示", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/**
 * 新「播放界面」设置统一页面
 * 包含：
 * 1. 背景动效与氛围（灵动流体、MeiloX 网格、流体着色器、封面模糊、动态渐变、壁纸背景与音频律动）
 * 2. 黑胶唱片与封面（圆形专辑、旋转动画、黑胶质感、机械唱针、复古底座、触屏切歌词）
 * 3. 歌词特效与体验（逐字卡拉OK、MeiloX 液态歌词、歌词景深虚化、拖动预览）
 * 4. 播放控制与进度条（简约风控件、波浪动态进度条、紧凑控制栏）
 */
@Composable
internal fun SettingsPlayerStageContent(
    // 背景动效与氛围
    playerDynamicBg: Boolean,
    onPlayerDynamicBgChange: (Boolean) -> Unit,
    playerHyperBg: Boolean,
    onPlayerHyperBgChange: (Boolean) -> Unit,
    playerFluidAmbientBg: Boolean,
    onPlayerFluidAmbientBgChange: (Boolean) -> Unit,
    playerMeshBg: Boolean,
    onPlayerMeshBgChange: (Boolean) -> Unit,
    playerCoverBlurBg: Boolean,
    onPlayerCoverBlurBgChange: (Boolean) -> Unit,
    playerCoverBlurAmount: Float,
    onPlayerCoverBlurAmountChange: (Float) -> Unit,
    playerCoverBlurDarken: Float,
    onPlayerCoverBlurDarkenChange: (Float) -> Unit,
    playerAudioReactive: Boolean,
    onPlayerAudioReactiveChange: (Boolean) -> Unit,
    playerBgEnhance: Boolean,
    onPlayerBgEnhanceChange: (Boolean) -> Unit,
    playerBgOpacity: Float,
    onPlayerBgOpacityChange: (Float) -> Unit,
    playerBlur: Boolean,
    onPlayerBlurChange: (Boolean) -> Unit,
    // 黑胶唱片与封面
    playerRoundAlbum: Boolean,
    onPlayerRoundAlbumChange: (Boolean) -> Unit,
    playerRotate: Boolean,
    onPlayerRotateChange: (Boolean) -> Unit,
    playerVinylStyle: Boolean = true,
    onPlayerVinylStyleChange: (Boolean) -> Unit = {},
    playerVinylPointer: Boolean = true,
    onPlayerVinylPointerChange: (Boolean) -> Unit = {},
    playerVinylBase: Boolean = false,
    onPlayerVinylBaseChange: (Boolean) -> Unit = {},
    playerTapCoverToLyrics: Boolean,
    onPlayerTapCoverToLyricsChange: (Boolean) -> Unit,
    // 歌词特效与体验
    playerLyricsWordEffect: Boolean,
    onPlayerLyricsWordEffectChange: (Boolean) -> Unit,
    playerMeiloxLyrics: Boolean,
    onPlayerMeiloxLyricsChange: (Boolean) -> Unit,
    playerLyricBlur: Boolean,
    onPlayerLyricBlurChange: (Boolean) -> Unit,
    playerLyricBlurAmount: Float,
    onPlayerLyricBlurAmountChange: (Float) -> Unit,
    playerLyricsSeekPreview: Boolean,
    onPlayerLyricsSeekPreviewChange: (Boolean) -> Unit,
    // 播放控制与进度条
    playerMinimalistControls: Boolean = true,
    onPlayerMinimalistControlsChange: (Boolean) -> Unit = {},
    playerCompactControls: Boolean,
    onPlayerCompactControlsChange: (Boolean) -> Unit,
    playerWaveformSlider: Boolean,
    onPlayerWaveformSliderChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==================== 1. 背景动效与氛围 ====================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "背景动效与氛围",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            val currentBgPreset = when {
                playerFluidAmbientBg -> "fluid"
                playerMeshBg -> "mesh"
                playerHyperBg -> "hyper"
                playerCoverBlurBg -> "blur"
                playerDynamicBg -> "dynamic"
                playerBgEnhance -> "wallpaper"
                else -> "pure"
            }

            fun selectBgPreset(key: String) {
                onPlayerFluidAmbientBgChange(key == "fluid")
                onPlayerMeshBgChange(key == "mesh")
                onPlayerHyperBgChange(key == "hyper")
                onPlayerCoverBlurBgChange(key == "blur")
                onPlayerDynamicBgChange(key == "dynamic")
                onPlayerBgEnhanceChange(key == "wallpaper")
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BackgroundPresetCard(
                    title = "灵动流体背景",
                    tag = "推荐 · Apple 风格",
                    subtitle = "仿 Apple Music 流体光场，多频有机流动与智能高饱和取色",
                    selected = currentBgPreset == "fluid",
                    previewBrush = Brush.linearGradient(
                        listOf(Color(0xFFE94057), Color(0xFF8A2387), Color(0xFFF27121))
                    ),
                    onClick = { selectBgPreset("fluid") }
                )

                BackgroundPresetCard(
                    title = "Mesh 频谱背景",
                    tag = "自适应律动",
                    subtitle = "封面取色驱动 GL 网格流体形变，随音乐鼓点与频谱律动",
                    selected = currentBgPreset == "mesh",
                    previewBrush = Brush.sweepGradient(
                        listOf(Color(0xFF2193b0), Color(0xFF6dd5ed), Color(0xFF8E2DE2), Color(0xFF2193b0))
                    ),
                    onClick = { selectBgPreset("mesh") }
                )

                BackgroundPresetCard(
                    title = "极简弥散背景",
                    tag = if (Build.VERSION.SDK_INT >= 33) "Android 13+" else "不可用",
                    subtitle = "GLSL 弥散动态光晕，随封面色彩呼吸流动（需 Android 13+）",
                    selected = currentBgPreset == "hyper",
                    enabled = Build.VERSION.SDK_INT >= 33,
                    previewBrush = Brush.radialGradient(
                        listOf(Color(0xFFFF8008), Color(0xFFFFC837), Color(0xFF1F1C2C))
                    ),
                    onClick = { selectBgPreset("hyper") }
                )

                BackgroundPresetCard(
                    title = "封面高斯模糊",
                    tag = if (Build.VERSION.SDK_INT >= 31) "Android 12+" else "不可用",
                    subtitle = "全屏专辑封面高保真虚化铺底，深邃沉浸高级质感（需 Android 12+）",
                    selected = currentBgPreset == "blur",
                    enabled = Build.VERSION.SDK_INT >= 31,
                    previewBrush = Brush.linearGradient(
                        listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF))
                    ),
                    onClick = { selectBgPreset("blur") }
                )

                BackgroundPresetCard(
                    title = "封面动态渐变",
                    tag = "旋转流动",
                    subtitle = "根据专辑封面自适应全屏渐变，随播放旋转流动",
                    selected = currentBgPreset == "dynamic",
                    previewBrush = Brush.linearGradient(
                        listOf(Color(0xFF4776E6), Color(0xFF8E54E9))
                    ),
                    onClick = { selectBgPreset("dynamic") }
                )

                BackgroundPresetCard(
                    title = "应用壁纸背景",
                    tag = "相册壁纸",
                    subtitle = "使用在外观设置中选择的背景图片作为播放器底图",
                    selected = currentBgPreset == "wallpaper",
                    previewBrush = Brush.linearGradient(
                        listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                    ),
                    onClick = { selectBgPreset("wallpaper") }
                )

                BackgroundPresetCard(
                    title = "纯净极简黑调",
                    tag = "纯黑省电",
                    subtitle = "关闭所有动效背景，经典纯黑深邃沉浸，低功耗省电",
                    selected = currentBgPreset == "pure",
                    previewBrush = Brush.linearGradient(
                        listOf(Color(0xFF232526), Color(0xFF0F0C29))
                    ),
                    onClick = { selectBgPreset("pure") }
                )
            }

            // 封面模糊参数展开调节
            androidx.compose.animation.AnimatedVisibility(
                visible = currentBgPreset == "blur",
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SettingsGroupCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "封面模糊强度",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
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
                            text = String.format("当前模糊: %.0f", playerCoverBlurAmount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "背景调暗遮罩",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
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
                            Text("暗沉", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = String.format("遮罩深度: %.0f%%", playerCoverBlurDarken * 100),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 壁纸背景参数展开调节
            androidx.compose.animation.AnimatedVisibility(
                visible = currentBgPreset == "wallpaper",
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SettingsGroupCard {
                    PlayerSettingSwitchRow(
                        title = "壁纸高斯模糊",
                        subtitle = "给背景壁纸添加模糊与高级质感",
                        checked = playerBlur,
                        onCheckedChange = onPlayerBlurChange
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "背景壁纸透明度",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 动效细项调节卡片
            if (currentBgPreset in listOf("fluid", "mesh", "hyper")) {
                SettingsGroupCard {
                    PlayerSettingSwitchRow(
                        title = "音频律动响应",
                        subtitle = "背景流动与形变随当前播放的音量频谱自适应律动",
                        checked = playerAudioReactive,
                        onCheckedChange = onPlayerAudioReactiveChange
                    )
                }
            }
        }

        // ==================== 2. 黑胶唱片与封面 ====================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "黑胶唱片与封面",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SettingsGroupCard {
                // (1) 黑胶唱机模式主开关
                PlayerSettingSwitchRow(
                    title = "黑胶唱机模式",
                    subtitle = "启用复古黑胶唱机与旋转动效；关闭后显示矩形封面卡",
                    checked = playerRoundAlbum,
                    onCheckedChange = { enabled ->
                        onPlayerRoundAlbumChange(enabled)
                        if (enabled) {
                            onPlayerRotateChange(true)
                            onPlayerVinylStyleChange(true)
                        } else {
                            onPlayerRotateChange(false)
                            onPlayerVinylStyleChange(false)
                            onPlayerVinylPointerChange(false)
                            onPlayerVinylBaseChange(false)
                        }
                    }
                )

                // 唱机细节折叠卡片
                androidx.compose.animation.AnimatedVisibility(
                    visible = playerRoundAlbum,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        // 播放旋转动画
                        PlayerSettingSwitchRow(
                            title = "播放平滑旋转",
                            subtitle = "播放时唱片缓缓匀速旋转，暂停时轻柔停转",
                            checked = playerRotate,
                            onCheckedChange = onPlayerRotateChange
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        // 黑胶唱片质感
                        PlayerSettingSwitchRow(
                            title = "黑胶同心音轨质感",
                            subtitle = "在封面外圈展示拟真黑胶唱盘与精细同心音轨光泽",
                            checked = playerVinylStyle,
                            onCheckedChange = onPlayerVinylStyleChange
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        // 金属机械唱针
                        PlayerSettingSwitchRow(
                            title = "金属复古唱针",
                            subtitle = "显示金属唱臂与配重块，播放时落针，暂停时抬起复位",
                            checked = playerVinylPointer,
                            onCheckedChange = onPlayerVinylPointerChange
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        // 复古唱机底座
                        PlayerSettingSwitchRow(
                            title = "复古唱机底座",
                            subtitle = "显示白玉质感唱机底座卡片与精致徽标",
                            checked = playerVinylBase,
                            onCheckedChange = onPlayerVinylBaseChange
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // 轻触封面翻转歌词
                PlayerSettingSwitchRow(
                    title = "轻触封面翻转歌词",
                    subtitle = "点击专辑封面区域直接翻转或滑到歌词页面",
                    checked = playerTapCoverToLyrics,
                    onCheckedChange = onPlayerTapCoverToLyricsChange
                )
            }
        }

        // ==================== 3. 歌词特效与体验 ====================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "歌词特效与排版",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SettingsGroupCard {
                // (1) 歌词渲染引擎分段选择
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = "歌词高亮动效形态",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "选择当前正在演唱歌词行的动态渲染风格",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val currentLyricStyle = when {
                        playerMeiloxLyrics -> "meilox"
                        playerLyricsWordEffect -> "word"
                        else -> "classic"
                    }

                    ModernSegmentedPill(
                        options = listOf(
                            "classic" to "整行高亮",
                            "word" to "逐字微光",
                            "meilox" to "MeiloX 液态"
                        ),
                        selectedKey = currentLyricStyle,
                        onSelect = { key ->
                            when (key) {
                                "classic" -> {
                                    onPlayerLyricsWordEffectChange(false)
                                    onPlayerMeiloxLyricsChange(false)
                                }
                                "word" -> {
                                    onPlayerLyricsWordEffectChange(true)
                                    onPlayerMeiloxLyricsChange(false)
                                }
                                "meilox" -> {
                                    onPlayerLyricsWordEffectChange(false)
                                    onPlayerMeiloxLyricsChange(true)
                                }
                            }
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // (2) 歌词景深虚化
                PlayerSettingSwitchRow(
                    title = "歌词景深虚化",
                    subtitle = "非当前歌词行添加景深模糊效果，自然聚焦当前演唱行",
                    checked = playerLyricBlur,
                    onCheckedChange = onPlayerLyricBlurChange
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = playerLyricBlur,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            text = "虚化模糊强度",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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
                            text = String.format("当前虚化: %.0f", playerLyricBlurAmount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // (3) 拖动歌词预览
                PlayerSettingSwitchRow(
                    title = "拖动歌词进度预览",
                    subtitle = "拖动进度条时实时预览对应时间歌词，划过每行产生轻微震动反馈",
                    checked = playerLyricsSeekPreview,
                    onCheckedChange = onPlayerLyricsSeekPreviewChange
                )
            }
        }

        // ==================== 4. 播放控制与进度条 ====================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "播放控制与进度条",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SettingsGroupCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = "控件与进度条样式",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "选择底部播放控制栏与进度条的视觉与交互形态",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val currentControlStyle = when {
                        playerMinimalistControls -> "minimalist"
                        playerWaveformSlider -> "waveform"
                        else -> "default"
                    }

                    ModernSegmentedPill(
                        options = listOf(
                            "minimalist" to "简约胶囊",
                            "waveform" to "波浪进度",
                            "default" to "经典标准"
                        ),
                        selectedKey = currentControlStyle,
                        onSelect = { key ->
                            when (key) {
                                "minimalist" -> {
                                    onPlayerMinimalistControlsChange(true)
                                    onPlayerWaveformSliderChange(false)
                                }
                                "waveform" -> {
                                    onPlayerMinimalistControlsChange(false)
                                    onPlayerWaveformSliderChange(true)
                                }
                                "default" -> {
                                    onPlayerMinimalistControlsChange(false)
                                    onPlayerWaveformSliderChange(false)
                                }
                            }
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // 紧凑控制栏
                PlayerSettingSwitchRow(
                    title = "紧凑控制栏",
                    subtitle = "仅歌词页生效：隐藏播放模式与列表按钮，控制键与进度条紧凑下沉",
                    checked = playerCompactControls,
                    onCheckedChange = onPlayerCompactControlsChange
                )
            }
        }
    }
}

/**
 * 播放器设置页的开关行
 */
@Composable
internal fun PlayerSettingSwitchRow(
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

// ==================== 声音与播放设置子页面 ====================

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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==================== 1. 播放音质偏好 ====================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "播放音质偏好",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            var selectedQuality by remember {
                mutableStateOf(settingsPrefs.getString("audio_quality", null) ?: "flac")
            }
            val qualityOptions = listOf(
                "default" to "默认",
                "128" to "128k",
                "320" to "320k",
                "flac" to "FLAC",
                "high" to "Hi-Res"
            )

            SettingsGroupCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    qualityOptions.forEach { (value, label) ->
                        val isSelected = selectedQuality == value
                        Surface(
                            onClick = {
                                selectedQuality = value
                                val qualityValue = if (value == "default") null else value
                                KuGouApi.audioQuality = qualityValue
                                settingsPrefs.edit().putString("audio_quality", qualityValue).apply()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                val qualityDesc = when (selectedQuality) {
                    "default" -> "自适应流媒体音质，依据网络与源端智能切换"
                    "128" -> "标准 MP3 格式 (128kbps)，节省流量，极速加载"
                    "320" -> "高品质 MP3 格式 (320kbps)，听感通透饱满，均衡音质与速度"
                    "high" -> "Hi-Res 24-bit/96kHz+ 超高清采样率，母带级音频还原"
                    else -> "无损 FLAC 格式，CD 级 16-bit/44.1kHz 音质，还原丰富音乐细节"
                }
                Text(
                    text = qualityDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }
        }

        // ==================== 2. 音频引擎与输出 ====================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "音频引擎与输出",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SettingsGroupCard {
                // (1) 32-bit 高解析输出
                PlayerSettingSwitchRow(
                    title = "32-bit 高解析输出",
                    subtitle = "优先使用 32 位高精度浮点处理音频数据（重启应用生效）",
                    checked = playbackHighRes,
                    onCheckedChange = onPlaybackHighResChange
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // (2) 响度均衡
                PlayerSettingSwitchRow(
                    title = "响度均衡",
                    subtitle = "自动平衡不同歌曲与专辑的动态响度，避免音量骤变",
                    checked = playbackVolumeNormalization,
                    onCheckedChange = onPlaybackVolumeNormalizationChange
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // (3) 抢占音频焦点
                PlayerSettingSwitchRow(
                    title = "抢占音频焦点",
                    subtitle = "启动播放时主动请求系统独占焦点，暂停其他媒体声音",
                    checked = playbackPreemptFocus,
                    onCheckedChange = onPlaybackPreemptFocusChange
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // (4) 声道平衡
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "声道平衡",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val balanceText = when {
                                playbackVolumeBalance == 0f -> "居中 (L 100% · R 100%)"
                                playbackVolumeBalance < 0f -> "偏左 ${(kotlin.math.abs(playbackVolumeBalance) * 100).toInt()}%"
                                else -> "偏右 ${(playbackVolumeBalance * 100).toInt()}%"
                            }
                            Text(
                                text = balanceText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (playbackVolumeBalance != 0f) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "复位",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable { onPlaybackVolumeBalanceChange(0f) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("左", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = playbackVolumeBalance,
                            onValueChange = onPlaybackVolumeBalanceChange,
                            valueRange = -1f..1f,
                            steps = 39,
                            modifier = Modifier.weight(1f)
                        )
                        Text("右", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ==================== 3. 播放行为与后台策略 ====================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "播放行为与后台策略",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            val idleMinutes = settingsPrefs.getInt("playback_service_idle_shutdown_minutes", 0)
            var showIdleDialog by remember { mutableStateOf(false) }

            SettingsGroupCard {
                // (1) 保留上次播放进度
                PlayerSettingSwitchRow(
                    title = "保留上次播放进度",
                    subtitle = "重启应用后自动恢复上次中断的具体进度点",
                    checked = playbackKeepProgress,
                    onCheckedChange = onPlaybackKeepProgressChange
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // (2) 保留播放状态
                PlayerSettingSwitchRow(
                    title = "保留播放状态",
                    subtitle = "重启后保留列表循环、单曲循环或随机播放模式",
                    checked = playbackKeepMode,
                    onCheckedChange = onPlaybackKeepModeChange
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // (3) 蓝牙断开自动停止
                PlayerSettingSwitchRow(
                    title = "蓝牙断开自动暂停",
                    subtitle = "蓝牙耳机断开后平滑暂停播放，避免外放打扰",
                    checked = playbackBluetoothStop,
                    onCheckedChange = onPlaybackBluetoothStopChange
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // (4) 暂停后退出播放服务
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
                            text = if (idleMinutes > 0) "暂停 $idleMinutes 分钟后自动退出常驻服务" else "始终保持常驻服务（不自动退出）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (idleMinutes > 0) "${idleMinutes} 分钟" else "常驻",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (showIdleDialog) {
                AlertDialog(
                    onDismissRequest = { showIdleDialog = false },
                    title = { Text("暂停后退出播放服务", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            listOf(
                                0 to "始终保持常驻（推荐）",
                                5 to "5 分钟",
                                15 to "15 分钟",
                                30 to "30 分钟",
                                60 to "60 分钟",
                                120 to "120 分钟"
                            ).forEach { (minutes, label) ->
                                val selected = idleMinutes == minutes
                                Surface(
                                    onClick = {
                                        settingsPrefs.edit()
                                            .putInt("playback_service_idle_shutdown_minutes", minutes)
                                            .apply()
                                        context.sendBroadcast(
                                            Intent("com.example.lxmusic.SETTINGS_CHANGED")
                                        )
                                        showIdleDialog = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = {
                                                settingsPrefs.edit()
                                                    .putInt("playback_service_idle_shutdown_minutes", minutes)
                                                    .apply()
                                                context.sendBroadcast(
                                                    Intent("com.example.lxmusic.SETTINGS_CHANGED")
                                                )
                                                showIdleDialog = false
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showIdleDialog = false }) { Text("关闭") }
                    }
                )
            }
        }

        // ==================== 4. 无缝过渡与淡入淡出 ====================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "无缝过渡与淡入淡出",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SettingsGroupCard {
                // (1) 播放淡入淡出
                PlayerSettingSwitchRow(
                    title = "播放淡入淡出",
                    subtitle = "开始播放与暂停时音量平滑渐入渐出，告别生硬爆音",
                    checked = playbackFadeIn,
                    onCheckedChange = onPlaybackFadeInChange
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = playbackFadeIn,
                    enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "淡入时长",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${playbackFadeInMs} ms",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = playbackFadeInMs.toFloat(),
                            onValueChange = { onPlaybackFadeInMsChange(it.toInt()) },
                            valueRange = 0f..3000f,
                            steps = 29,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // (2) 切歌交叉淡化
                PlayerSettingSwitchRow(
                    title = "切歌交叉淡化 (Crossfade)",
                    subtitle = "切歌时当前歌曲渐出同时下一首平滑淡入，DJ 无缝衔接",
                    checked = playbackCrossfadeNext,
                    onCheckedChange = onPlaybackCrossfadeNextChange
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = playbackCrossfadeNext,
                    enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "下一首淡入时长",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${playbackCrossfadeInMs} ms",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = playbackCrossfadeInMs.toFloat(),
                            onValueChange = { onPlaybackCrossfadeInMsChange(it.toInt()) },
                            valueRange = 0f..3000f,
                            steps = 29,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "当前曲淡出时长",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${playbackCrossfadeOutMs} ms",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = playbackCrossfadeOutMs.toFloat(),
                            onValueChange = { onPlaybackCrossfadeOutMsChange(it.toInt()) },
                            valueRange = 0f..3000f,
                            steps = 29,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

