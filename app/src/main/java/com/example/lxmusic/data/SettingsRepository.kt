package com.example.lxmusic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 设置存储仓库：统一封装 settings SharedPreferences。
 * 每个属性读取时同步初始化，写入时自动持久化，供 Compose 直接订阅。
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    object Keys {
        const val THEME_MODE = "theme_mode"
        const val DYNAMIC_COLOR = "dynamic_color"
        const val THEME_COLOR = "theme_color"
        const val FLOATING_BOTTOM_BAR = "floating_bottom_bar"
        const val BLUR_NAV_BAR = "blur_nav_bar"
        const val NAV_BAR_OPACITY = "nav_bar_opacity"
        const val PLAYER_BAR_OPACITY = "player_bar_opacity"
        const val FOLLOW_THEME_COLOR = "follow_theme_color"
        const val PLAYER_BAR_WHITE_BLEND = "player_bar_white_blend"
        const val FLOATING_BAR_OPACITY = "floating_bar_opacity"
        const val PLAYER_BG_OPACITY = "player_bg_opacity"
        const val PLAYER_BLUR = "player_blur"
        const val PLAYER_DYNAMIC_BG = "player_dynamic_bg"
        const val PLAYER_MESH_BG = "player_mesh_bg"
        const val PLAYER_ROUND_ALBUM = "player_round_album"
        const val PLAYER_ROTATE = "player_rotate"
        const val PLAYER_BG_ENHANCE = "player_bg_enhance"
        const val PLAYER_HYPER_BG = "player_hyper_bg"
        const val PLAYER_WAVEFORM_SLIDER = "player_waveform_slider"
        // 逐字歌词动效：当前行逐字点亮（关闭=整行高亮，其余样式不变）
        const val PLAYER_LYRICS_WORD_EFFECT = "player_lyrics_word_effect"
        const val PLAYER_LYRICS_SEEK_PREVIEW = "player_lyrics_seek_preview"
        // 动效设置（对齐 Neri：封面模糊背景 / 音频律动 / 歌词模糊）
        const val PLAYER_COVER_BLUR_BG = "player_cover_blur_bg"
        const val PLAYER_COVER_BLUR_AMOUNT = "player_cover_blur_amount"
        const val PLAYER_COVER_BLUR_DARKEN = "player_cover_blur_darken"
        const val PLAYER_AUDIO_REACTIVE = "player_audio_reactive"
        const val PLAYER_LYRIC_BLUR = "player_lyric_blur"
        const val PLAYER_LYRIC_BLUR_AMOUNT = "player_lyric_blur_amount"
        // 播放器 UI 显示（点击封面切歌词 / 精简控制栏 / 顶栏收藏按钮）
        const val PLAYER_TAP_COVER_TO_LYRICS = "player_tap_cover_to_lyrics"
        const val PLAYER_COMPACT_CONTROLS = "player_compact_controls"
        const val PLAYER_SHOW_TOP_FAVORITE = "player_show_top_favorite"
        // 歌词显示（播放器菜单-歌词设置：字号/粗细/排版，持久化）
        // 歌词页（歌词卡）与封面卡（歌词预览条）两套独立，互不互通
        const val PLAYER_LYRIC_FONT_SIZE = "player_lyric_font_size"
        const val PLAYER_LYRIC_FONT_WEIGHT = "player_lyric_font_weight"
        const val PLAYER_LYRIC_ALIGNMENT = "player_lyric_alignment"
        const val PLAYER_COVER_LYRIC_FONT_SIZE = "player_cover_lyric_font_size"
        const val PLAYER_COVER_LYRIC_FONT_WEIGHT = "player_cover_lyric_font_weight"
        const val PLAYER_COVER_LYRIC_ALIGNMENT = "player_cover_lyric_alignment"
        // 播放设置（对齐 Neri：进度保留 / 播放状态 / 蓝牙断开 / 淡入淡出 / 交叉淡化 / 响度均衡 / 声道平衡 / 高解析输出 / 焦点）
        const val PLAYBACK_KEEP_PROGRESS = "playback_keep_progress"
        const val PLAYBACK_KEEP_MODE = "playback_keep_mode"
        const val PLAYBACK_BLUETOOTH_STOP = "playback_bluetooth_stop"
        const val PLAYBACK_FADE_IN = "playback_fade_in"
        const val PLAYBACK_FADE_IN_MS = "playback_fade_in_ms"
        const val PLAYBACK_CROSSFADE_NEXT = "playback_crossfade_next"
        const val PLAYBACK_CROSSFADE_IN_MS = "playback_crossfade_in_ms"
        const val PLAYBACK_CROSSFADE_OUT_MS = "playback_crossfade_out_ms"
        const val PLAYBACK_VOLUME_NORMALIZATION = "playback_volume_normalization"
        const val PLAYBACK_VOLUME_BALANCE = "playback_volume_balance"
        const val PLAYBACK_HIGH_RES = "playback_high_res"
        const val PLAYBACK_PREEMPT_FOCUS = "playback_preempt_focus"
        const val BG_IMAGE_PATH = "bg_image_path"
        const val BG_OPACITY = "bg_opacity"
        const val LIQUID_GLASS = "liquid_glass"
        const val HOME_PLAY_MODE = "home_play_mode"
        const val AUDIO_QUALITY = "audio_quality"
        const val SERVER_URL = "server_url"
        const val DFID = "dfid"
        // 主题设置（对齐 Neri：种子色 / 取色风格 / 明暗模式 / 颜色过渡动画）
        const val THEME_SEED_COLOR = "theme_seed_color"
        const val THEME_PALETTE_STYLE = "theme_palette_style"
        const val THEME_DARK_MODE = "theme_dark_mode"
        const val THEME_COLOR_ANIMATION = "theme_color_animation"
        // 通用设置（对齐 Neri：触感反馈 / 高刷新率 / UI 缩放 / 播放服务空闲退出 / USB DAC）
        const val HAPTIC_FEEDBACK = "haptic_feedback_enabled"
        const val PREFER_HIGH_REFRESH_RATE = "prefer_high_refresh_rate"
        const val UI_DENSITY_SCALE = "ui_density_scale"
        const val PLAYBACK_SERVICE_IDLE_SHUTDOWN_MINUTES = "playback_service_idle_shutdown_minutes"
        const val MAX_CACHE_SIZE_BYTES = "max_cache_size_bytes"
        // USB DAC
        const val USB_EXCLUSIVE_PLAYBACK = "usb_exclusive_playback"
        const val USB_EXCLUSIVE_SAMPLE_RATE = "usb_exclusive_sample_rate"
        const val USB_EXCLUSIVE_BIT_DEPTH = "usb_exclusive_bit_depth"
    }

    // ==================== 主题 ====================

    var themeMode: String
        get() = _themeMode
        set(value) {
            _themeMode = value
            prefs.edit().putString(Keys.THEME_MODE, value).apply()
        }
    private var _themeMode: String by mutableStateOf(prefs.getString(Keys.THEME_MODE, "dynamic") ?: "dynamic")

    var dynamicColor: Boolean
        get() = _dynamicColor
        set(value) {
            _dynamicColor = value
            prefs.edit().putBoolean(Keys.DYNAMIC_COLOR, value).apply()
        }
    private var _dynamicColor: Boolean by mutableStateOf(prefs.getBoolean(Keys.DYNAMIC_COLOR, true))

    var themeColorHex: String
        get() = _themeColorHex
        set(value) {
            _themeColorHex = value
            prefs.edit().putString(Keys.THEME_COLOR, value).apply()
        }
    private var _themeColorHex: String by mutableStateOf(prefs.getString(Keys.THEME_COLOR, "") ?: "")

    // ==================== 主题设置（Neri 风格） ====================

    var themeSeedColor: String
        get() = _themeSeedColor
        set(value) {
            _themeSeedColor = value
            prefs.edit().putString(Keys.THEME_SEED_COLOR, value).apply()
        }
    private var _themeSeedColor: String by mutableStateOf(
        prefs.getString(Keys.THEME_SEED_COLOR, "0061A4") ?: "0061A4"
    )

    var themePaletteStyle: String
        get() = _themePaletteStyle
        set(value) {
            _themePaletteStyle = value
            prefs.edit().putString(Keys.THEME_PALETTE_STYLE, value).apply()
        }
    private var _themePaletteStyle: String by mutableStateOf(
        prefs.getString(Keys.THEME_PALETTE_STYLE, "TonalSpot") ?: "TonalSpot"
    )

    var themeDarkMode: String
        get() = _themeDarkMode
        set(value) {
            _themeDarkMode = value
            prefs.edit().putString(Keys.THEME_DARK_MODE, value).apply()
        }
    private var _themeDarkMode: String by mutableStateOf(
        prefs.getString(Keys.THEME_DARK_MODE, "auto") ?: "auto"
    )

    var themeColorAnimation: Boolean
        get() = _themeColorAnimation
        set(value) {
            _themeColorAnimation = value
            prefs.edit().putBoolean(Keys.THEME_COLOR_ANIMATION, value).apply()
        }
    private var _themeColorAnimation: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.THEME_COLOR_ANIMATION, true)
    )

    // ==================== 导航栏 ====================

    var blurNavBar: Boolean
        get() = _blurNavBar
        set(value) {
            _blurNavBar = value
            prefs.edit().putBoolean(Keys.BLUR_NAV_BAR, value).apply()
        }
    private var _blurNavBar: Boolean by mutableStateOf(prefs.getBoolean(Keys.BLUR_NAV_BAR, false))

    var floatingBottomBar: Boolean
        get() = _floatingBottomBar
        set(value) {
            _floatingBottomBar = value
            prefs.edit().putBoolean(Keys.FLOATING_BOTTOM_BAR, value).apply()
        }
    private var _floatingBottomBar: Boolean by mutableStateOf(prefs.getBoolean(Keys.FLOATING_BOTTOM_BAR, false))

    var navBarOpacity: Float
        get() = _navBarOpacity
        set(value) {
            _navBarOpacity = value
            prefs.edit().putFloat(Keys.NAV_BAR_OPACITY, value).apply()
        }
    private var _navBarOpacity: Float by mutableFloatStateOf(prefs.getFloat(Keys.NAV_BAR_OPACITY, 1f))

    var playerBarOpacity: Float
        get() = _playerBarOpacity
        set(value) {
            _playerBarOpacity = value
            prefs.edit().putFloat(Keys.PLAYER_BAR_OPACITY, value).apply()
        }
    private var _playerBarOpacity: Float by mutableFloatStateOf(prefs.getFloat(Keys.PLAYER_BAR_OPACITY, 1f))

    var followThemeColor: Boolean
        get() = _followThemeColor
        set(value) {
            _followThemeColor = value
            prefs.edit().putBoolean(Keys.FOLLOW_THEME_COLOR, value).apply()
        }
    private var _followThemeColor: Boolean by mutableStateOf(prefs.getBoolean(Keys.FOLLOW_THEME_COLOR, false))

    var playerBarWhiteBlend: Float
        get() = _playerBarWhiteBlend
        set(value) {
            _playerBarWhiteBlend = value
            prefs.edit().putFloat(Keys.PLAYER_BAR_WHITE_BLEND, value).apply()
        }
    private var _playerBarWhiteBlend: Float by mutableFloatStateOf(prefs.getFloat(Keys.PLAYER_BAR_WHITE_BLEND, 0.8f))

    var floatingBarOpacity: Float
        get() = _floatingBarOpacity
        set(value) {
            _floatingBarOpacity = value
            prefs.edit().putFloat(Keys.FLOATING_BAR_OPACITY, value).apply()
        }
    private var _floatingBarOpacity: Float by mutableFloatStateOf(prefs.getFloat(Keys.FLOATING_BAR_OPACITY, 1f))

    // ==================== 播放器 / 背景 ====================

    var playerBgOpacity: Float
        get() = _playerBgOpacity
        set(value) {
            _playerBgOpacity = value
            prefs.edit().putFloat(Keys.PLAYER_BG_OPACITY, value).apply()
        }
    private var _playerBgOpacity: Float by mutableFloatStateOf(prefs.getFloat(Keys.PLAYER_BG_OPACITY, 0.5f))

    var playerBlur: Boolean
        get() = _playerBlur
        set(value) {
            _playerBlur = value
            prefs.edit().putBoolean(Keys.PLAYER_BLUR, value).apply()
        }
    private var _playerBlur: Boolean by mutableStateOf(prefs.getBoolean(Keys.PLAYER_BLUR, false))

    var playerDynamicBg: Boolean
        get() = _playerDynamicBg
        set(value) {
            _playerDynamicBg = value
            prefs.edit().putBoolean(Keys.PLAYER_DYNAMIC_BG, value).apply()
        }
    private var _playerDynamicBg: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYER_DYNAMIC_BG, true)
    )

    var playerMeshBg: Boolean
        get() = _playerMeshBg
        set(value) {
            _playerMeshBg = value
            prefs.edit().putBoolean(Keys.PLAYER_MESH_BG, value).apply()
        }
    private var _playerMeshBg: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYER_MESH_BG, false)
    )

    var playerRoundAlbum: Boolean
        get() = _playerRoundAlbum
        set(value) {
            _playerRoundAlbum = value
            prefs.edit().putBoolean(Keys.PLAYER_ROUND_ALBUM, value).apply()
        }
    private var _playerRoundAlbum: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYER_ROUND_ALBUM, false)
    )

    var playerRotate: Boolean
        get() = _playerRotate
        set(value) {
            _playerRotate = value
            prefs.edit().putBoolean(Keys.PLAYER_ROTATE, value).apply()
        }
    private var _playerRotate: Boolean by mutableStateOf(prefs.getBoolean(Keys.PLAYER_ROTATE, false))

    var playerBgEnhance: Boolean
        get() = _playerBgEnhance
        set(value) {
            _playerBgEnhance = value
            prefs.edit().putBoolean(Keys.PLAYER_BG_ENHANCE, value).apply()
        }
    private var _playerBgEnhance: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYER_BG_ENHANCE, false)
    )

    var playerHyperBg: Boolean
        get() = _playerHyperBg
        set(value) {
            _playerHyperBg = value
            prefs.edit().putBoolean(Keys.PLAYER_HYPER_BG, value).apply()
        }
    private var _playerHyperBg: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYER_HYPER_BG, false)
    )

    var playerWaveformSlider: Boolean
        get() = _playerWaveformSlider
        set(value) {
            _playerWaveformSlider = value
            prefs.edit().putBoolean(Keys.PLAYER_WAVEFORM_SLIDER, value).apply()
        }
    private var _playerWaveformSlider: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYER_WAVEFORM_SLIDER, true)
    )

    // ==================== 逐字歌词动效 ====================

    var playerLyricsWordEffect: Boolean
        get() = _playerLyricsWordEffect
        set(value) {
            _playerLyricsWordEffect = value
            prefs.edit().putBoolean(Keys.PLAYER_LYRICS_WORD_EFFECT, value).apply()
        }
    private var _playerLyricsWordEffect: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYER_LYRICS_WORD_EFFECT, true)
    )

    var playerLyricsSeekPreview: Boolean
        get() = _playerLyricsSeekPreview
        set(value) {
            _playerLyricsSeekPreview = value
            prefs.edit().putBoolean(Keys.PLAYER_LYRICS_SEEK_PREVIEW, value).apply()
        }
    private var _playerLyricsSeekPreview: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYER_LYRICS_SEEK_PREVIEW, true)
    )

    var playerCoverBlurBg: Boolean
        get() = _playerCoverBlurBg
        set(value) {
            _playerCoverBlurBg = value
            prefs.edit().putBoolean(Keys.PLAYER_COVER_BLUR_BG, value).apply()
        }
    private var _playerCoverBlurBg: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYER_COVER_BLUR_BG, false)
    )

    var playerCoverBlurAmount: Float
        get() = _playerCoverBlurAmount
        set(value) {
            _playerCoverBlurAmount = value
            prefs.edit().putFloat(Keys.PLAYER_COVER_BLUR_AMOUNT, value).apply()
        }
    private var _playerCoverBlurAmount: Float by mutableFloatStateOf(
        prefs.getFloat(Keys.PLAYER_COVER_BLUR_AMOUNT, 40f)
    )

    var playerCoverBlurDarken: Float
        get() = _playerCoverBlurDarken
        set(value) {
            _playerCoverBlurDarken = value
            prefs.edit().putFloat(Keys.PLAYER_COVER_BLUR_DARKEN, value).apply()
        }
    private var _playerCoverBlurDarken: Float by mutableFloatStateOf(
        prefs.getFloat(Keys.PLAYER_COVER_BLUR_DARKEN, 0.2f)
    )

    var playerAudioReactive: Boolean
        get() = _playerAudioReactive
        set(value) {
            _playerAudioReactive = value
            prefs.edit().putBoolean(Keys.PLAYER_AUDIO_REACTIVE, value).apply()
        }
    private var _playerAudioReactive: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYER_AUDIO_REACTIVE, true)
    )

    var playerLyricBlur: Boolean
        get() = _playerLyricBlur
        set(value) {
            _playerLyricBlur = value
            prefs.edit().putBoolean(Keys.PLAYER_LYRIC_BLUR, value).apply()
        }
    private var _playerLyricBlur: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYER_LYRIC_BLUR, true)
    )

    var playerLyricBlurAmount: Float
        get() = _playerLyricBlurAmount
        set(value) {
            _playerLyricBlurAmount = value
            prefs.edit().putFloat(Keys.PLAYER_LYRIC_BLUR_AMOUNT, value).apply()
        }
    private var _playerLyricBlurAmount: Float by mutableFloatStateOf(
        prefs.getFloat(Keys.PLAYER_LYRIC_BLUR_AMOUNT, 10f)
    )

    // ==================== 播放器 UI 显示 ====================

    var playerTapCoverToLyrics: Boolean
        get() = _playerTapCoverToLyrics
        set(value) {
            _playerTapCoverToLyrics = value
            prefs.edit().putBoolean(Keys.PLAYER_TAP_COVER_TO_LYRICS, value).apply()
        }
    private var _playerTapCoverToLyrics: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYER_TAP_COVER_TO_LYRICS, false)
    )

    var playerCompactControls: Boolean
        get() = _playerCompactControls
        set(value) {
            _playerCompactControls = value
            prefs.edit().putBoolean(Keys.PLAYER_COMPACT_CONTROLS, value).apply()
        }
    private var _playerCompactControls: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYER_COMPACT_CONTROLS, false)
    )

    var playerShowTopFavorite: Boolean
        get() = _playerShowTopFavorite
        set(value) {
            _playerShowTopFavorite = value
            prefs.edit().putBoolean(Keys.PLAYER_SHOW_TOP_FAVORITE, value).apply()
        }
    private var _playerShowTopFavorite: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYER_SHOW_TOP_FAVORITE, false)
    )

    // ==================== 歌词显示（播放器菜单-歌词设置） ====================

    var playerLyricFontSize: Float
        get() = _playerLyricFontSize
        set(value) {
            _playerLyricFontSize = value
            prefs.edit().putFloat(Keys.PLAYER_LYRIC_FONT_SIZE, value).apply()
        }
    private var _playerLyricFontSize: Float by mutableFloatStateOf(
        prefs.getFloat(Keys.PLAYER_LYRIC_FONT_SIZE, 20f)
    )

    var playerLyricFontWeight: Float
        get() = _playerLyricFontWeight
        set(value) {
            _playerLyricFontWeight = value
            prefs.edit().putFloat(Keys.PLAYER_LYRIC_FONT_WEIGHT, value).apply()
        }
    private var _playerLyricFontWeight: Float by mutableFloatStateOf(
        prefs.getFloat(Keys.PLAYER_LYRIC_FONT_WEIGHT, 500f)
    )

    var playerLyricAlignment: String
        get() = _playerLyricAlignment
        set(value) {
            _playerLyricAlignment = value
            prefs.edit().putString(Keys.PLAYER_LYRIC_ALIGNMENT, value).apply()
        }
    private var _playerLyricAlignment: String by mutableStateOf(
        prefs.getString(Keys.PLAYER_LYRIC_ALIGNMENT, "center") ?: "center"
    )

    // ==================== 封面卡歌词显示（主页菜单-歌词设置，与歌词页独立） ====================

    var playerCoverLyricFontSize: Float
        get() = _playerCoverLyricFontSize
        set(value) {
            _playerCoverLyricFontSize = value
            prefs.edit().putFloat(Keys.PLAYER_COVER_LYRIC_FONT_SIZE, value).apply()
        }
    private var _playerCoverLyricFontSize: Float by mutableFloatStateOf(
        prefs.getFloat(Keys.PLAYER_COVER_LYRIC_FONT_SIZE, 16f)
    )

    var playerCoverLyricFontWeight: Float
        get() = _playerCoverLyricFontWeight
        set(value) {
            _playerCoverLyricFontWeight = value
            prefs.edit().putFloat(Keys.PLAYER_COVER_LYRIC_FONT_WEIGHT, value).apply()
        }
    private var _playerCoverLyricFontWeight: Float by mutableFloatStateOf(
        prefs.getFloat(Keys.PLAYER_COVER_LYRIC_FONT_WEIGHT, 500f)
    )

    var playerCoverLyricAlignment: String
        get() = _playerCoverLyricAlignment
        set(value) {
            _playerCoverLyricAlignment = value
            prefs.edit().putString(Keys.PLAYER_COVER_LYRIC_ALIGNMENT, value).apply()
        }
    private var _playerCoverLyricAlignment: String by mutableStateOf(
        prefs.getString(Keys.PLAYER_COVER_LYRIC_ALIGNMENT, "center") ?: "center"
    )

    // ==================== 播放设置（对齐 Neri） ====================

    var playbackKeepProgress: Boolean
        get() = _playbackKeepProgress
        set(value) {
            _playbackKeepProgress = value
            prefs.edit().putBoolean(Keys.PLAYBACK_KEEP_PROGRESS, value).apply()
        }
    private var _playbackKeepProgress: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYBACK_KEEP_PROGRESS, true)
    )

    var playbackKeepMode: Boolean
        get() = _playbackKeepMode
        set(value) {
            _playbackKeepMode = value
            prefs.edit().putBoolean(Keys.PLAYBACK_KEEP_MODE, value).apply()
        }
    private var _playbackKeepMode: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYBACK_KEEP_MODE, true)
    )

    var playbackBluetoothStop: Boolean
        get() = _playbackBluetoothStop
        set(value) {
            _playbackBluetoothStop = value
            prefs.edit().putBoolean(Keys.PLAYBACK_BLUETOOTH_STOP, value).apply()
        }
    private var _playbackBluetoothStop: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYBACK_BLUETOOTH_STOP, true)
    )

    var playbackFadeIn: Boolean
        get() = _playbackFadeIn
        set(value) {
            _playbackFadeIn = value
            prefs.edit().putBoolean(Keys.PLAYBACK_FADE_IN, value).apply()
        }
    private var _playbackFadeIn: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYBACK_FADE_IN, false)
    )

    var playbackFadeInMs: Int
        get() = _playbackFadeInMs
        set(value) {
            _playbackFadeInMs = value
            prefs.edit().putInt(Keys.PLAYBACK_FADE_IN_MS, value).apply()
        }
    private var _playbackFadeInMs: Int by mutableIntStateOf(
        prefs.getInt(Keys.PLAYBACK_FADE_IN_MS, 500)
    )

    var playbackCrossfadeNext: Boolean
        get() = _playbackCrossfadeNext
        set(value) {
            _playbackCrossfadeNext = value
            prefs.edit().putBoolean(Keys.PLAYBACK_CROSSFADE_NEXT, value).apply()
        }
    private var _playbackCrossfadeNext: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYBACK_CROSSFADE_NEXT, false)
    )

    var playbackCrossfadeInMs: Int
        get() = _playbackCrossfadeInMs
        set(value) {
            _playbackCrossfadeInMs = value
            prefs.edit().putInt(Keys.PLAYBACK_CROSSFADE_IN_MS, value).apply()
        }
    private var _playbackCrossfadeInMs: Int by mutableIntStateOf(
        prefs.getInt(Keys.PLAYBACK_CROSSFADE_IN_MS, 500)
    )

    var playbackCrossfadeOutMs: Int
        get() = _playbackCrossfadeOutMs
        set(value) {
            _playbackCrossfadeOutMs = value
            prefs.edit().putInt(Keys.PLAYBACK_CROSSFADE_OUT_MS, value).apply()
        }
    private var _playbackCrossfadeOutMs: Int by mutableIntStateOf(
        prefs.getInt(Keys.PLAYBACK_CROSSFADE_OUT_MS, 500)
    )

    var playbackVolumeNormalization: Boolean
        get() = _playbackVolumeNormalization
        set(value) {
            _playbackVolumeNormalization = value
            prefs.edit().putBoolean(Keys.PLAYBACK_VOLUME_NORMALIZATION, value).apply()
        }
    private var _playbackVolumeNormalization: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYBACK_VOLUME_NORMALIZATION, false)
    )

    var playbackVolumeBalance: Float
        get() = _playbackVolumeBalance
        set(value) {
            _playbackVolumeBalance = value
            prefs.edit().putFloat(Keys.PLAYBACK_VOLUME_BALANCE, value).apply()
        }
    private var _playbackVolumeBalance: Float by mutableFloatStateOf(
        prefs.getFloat(Keys.PLAYBACK_VOLUME_BALANCE, 0f)
    )

    var playbackHighRes: Boolean
        get() = _playbackHighRes
        set(value) {
            _playbackHighRes = value
            prefs.edit().putBoolean(Keys.PLAYBACK_HIGH_RES, value).apply()
        }
    private var _playbackHighRes: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYBACK_HIGH_RES, false)
    )

    var playbackPreemptFocus: Boolean
        get() = _playbackPreemptFocus
        set(value) {
            _playbackPreemptFocus = value
            prefs.edit().putBoolean(Keys.PLAYBACK_PREEMPT_FOCUS, value).apply()
        }
    private var _playbackPreemptFocus: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PLAYBACK_PREEMPT_FOCUS, false)
    )

    init {
        // 迁移旧的 player_settings 值（旧版播放器菜单设置）
        val oldPrefs = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
        if (oldPrefs.contains("round_album") && !prefs.contains(Keys.PLAYER_ROUND_ALBUM)) {
            playerRoundAlbum = oldPrefs.getBoolean("round_album", false)
        }
        if (oldPrefs.contains("rotating") && !prefs.contains(Keys.PLAYER_ROTATE)) {
            playerRotate = oldPrefs.getBoolean("rotating", false)
        }
        if (oldPrefs.contains("background_enhance") && !prefs.contains(Keys.PLAYER_BG_ENHANCE)) {
            playerBgEnhance = oldPrefs.getBoolean("background_enhance", false)
        }
    }

    var bgOpacity: Float
        get() = _bgOpacity
        set(value) {
            _bgOpacity = value
            prefs.edit().putFloat(Keys.BG_OPACITY, value).apply()
        }
    private var _bgOpacity: Float by mutableFloatStateOf(prefs.getFloat(Keys.BG_OPACITY, 0.5f))

    var bgImagePath: String?
        get() = _bgImagePath
        set(value) {
            _bgImagePath = value
            prefs.edit().putString(Keys.BG_IMAGE_PATH, value).apply()
        }
    private var _bgImagePath: String? by mutableStateOf(prefs.getString(Keys.BG_IMAGE_PATH, null))

    var liquidGlass: Boolean
        get() = _liquidGlass
        set(value) {
            _liquidGlass = value
            prefs.edit().putBoolean(Keys.LIQUID_GLASS, value).apply()
        }
    private var _liquidGlass: Boolean by mutableStateOf(prefs.getBoolean(Keys.LIQUID_GLASS, false))

    var homePlayMode: Int
        get() = _homePlayMode
        set(value) {
            _homePlayMode = value
            prefs.edit().putInt(Keys.HOME_PLAY_MODE, value).apply()
        }
    private var _homePlayMode: Int by mutableIntStateOf(prefs.getInt(Keys.HOME_PLAY_MODE, 0))

    var audioQuality: String?
        get() = _audioQuality
        set(value) {
            _audioQuality = value
            prefs.edit().putString(Keys.AUDIO_QUALITY, value).apply()
        }
    private var _audioQuality: String? by mutableStateOf(prefs.getString(Keys.AUDIO_QUALITY, null))

    var serverUrl: String?
        get() = _serverUrl
        set(value) {
            _serverUrl = value
            prefs.edit().putString(Keys.SERVER_URL, value).apply()
        }
    private var _serverUrl: String? by mutableStateOf(prefs.getString(Keys.SERVER_URL, null))

    var dfid: String?
        get() = _dfid
        set(value) {
            _dfid = value
            prefs.edit().putString(Keys.DFID, value).apply()
        }
    private var _dfid: String? by mutableStateOf(prefs.getString(Keys.DFID, null))

    // ==================== 通用设置（对齐 Neri） ====================

    var hapticFeedback: Boolean
        get() = _hapticFeedback
        set(value) {
            _hapticFeedback = value
            prefs.edit().putBoolean(Keys.HAPTIC_FEEDBACK, value).apply()
        }
    private var _hapticFeedback: Boolean by mutableStateOf(prefs.getBoolean(Keys.HAPTIC_FEEDBACK, true))

    var preferHighRefreshRate: Boolean
        get() = _preferHighRefreshRate
        set(value) {
            _preferHighRefreshRate = value
            prefs.edit().putBoolean(Keys.PREFER_HIGH_REFRESH_RATE, value).apply()
        }
    private var _preferHighRefreshRate: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.PREFER_HIGH_REFRESH_RATE, false)
    )

    var uiDensityScale: Float
        get() = _uiDensityScale
        set(value) {
            _uiDensityScale = value
            prefs.edit().putFloat(Keys.UI_DENSITY_SCALE, value).apply()
        }
    private var _uiDensityScale: Float by mutableFloatStateOf(prefs.getFloat(Keys.UI_DENSITY_SCALE, 1.0f))

    var playbackServiceIdleShutdownMinutes: Int
        get() = _playbackServiceIdleShutdownMinutes
        set(value) {
            _playbackServiceIdleShutdownMinutes = value
            prefs.edit().putInt(Keys.PLAYBACK_SERVICE_IDLE_SHUTDOWN_MINUTES, value).apply()
        }
    private var _playbackServiceIdleShutdownMinutes: Int by mutableIntStateOf(
        prefs.getInt(Keys.PLAYBACK_SERVICE_IDLE_SHUTDOWN_MINUTES, 0)
    )

    var maxCacheSizeBytes: Long
        get() = _maxCacheSizeBytes
        set(value) {
            _maxCacheSizeBytes = value
            prefs.edit().putLong(Keys.MAX_CACHE_SIZE_BYTES, value).apply()
        }
    private var _maxCacheSizeBytes: Long by mutableStateOf(
        prefs.getLong(Keys.MAX_CACHE_SIZE_BYTES, 1024L * 1024 * 1024)
    )

    // ==================== USB DAC ====================

    var usbExclusivePlayback: Boolean
        get() = _usbExclusivePlayback
        set(value) {
            _usbExclusivePlayback = value
            prefs.edit().putBoolean(Keys.USB_EXCLUSIVE_PLAYBACK, value).apply()
        }
    private var _usbExclusivePlayback: Boolean by mutableStateOf(
        prefs.getBoolean(Keys.USB_EXCLUSIVE_PLAYBACK, false)
    )

    var usbExclusiveSampleRate: Int
        get() = _usbExclusiveSampleRate
        set(value) {
            _usbExclusiveSampleRate = value
            prefs.edit().putInt(Keys.USB_EXCLUSIVE_SAMPLE_RATE, value).apply()
        }
    private var _usbExclusiveSampleRate: Int by mutableIntStateOf(
        prefs.getInt(Keys.USB_EXCLUSIVE_SAMPLE_RATE, 48000)
    )

    var usbExclusiveBitDepth: Int
        get() = _usbExclusiveBitDepth
        set(value) {
            _usbExclusiveBitDepth = value
            prefs.edit().putInt(Keys.USB_EXCLUSIVE_BIT_DEPTH, value).apply()
        }
    private var _usbExclusiveBitDepth: Int by mutableIntStateOf(
        prefs.getInt(Keys.USB_EXCLUSIVE_BIT_DEPTH, 16)
    )

    // ==================== 通用 ====================

    fun contains(key: String): Boolean = prefs.contains(key)

    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    fun getString(key: String, default: String?): String? = prefs.getString(key, default)

    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    fun getFloat(key: String, default: Float): Float = prefs.getFloat(key, default)

    fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun putFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
