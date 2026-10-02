package com.example.lxmusic.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * 专业屏幕适配方案（宽度比例自适应）
 *
 * 核心思想：
 * 1. 以小米14（实测 1200px/480dpi = 400dp 宽）为基准设计，基准宽度设备缩放系数严格 = 1.0（视觉零变化）
 * 2. 自适应系数 = (屏宽 / 基准宽) 开方阻尼，双向生效：
 *    窄屏收缩（治"整体偏大"）、宽屏微放大（封顶防平板"老人机"化）
 * 3. 自适应统一收进 LxMusicTheme 的 Density 覆盖（见 Theme.kt），
 *    全局所有 dp 自动等比缩放，页面/组件代码不要再手动乘系数（手动乘 = 双重缩放）
 */
object ScreenAdapter {
    // 基准设计尺寸（小米14 实测：1200px 宽 / 480dpi = 400dp）
    const val BASE_SCREEN_WIDTH_DP = 400f

    // 自适应系数下限：极窄屏（≈290dp 及以下）封底 85%
    const val MIN_AUTO_SCALE = 0.85f
    // 自适应系数上限：宽屏/折叠屏最多放大到 106%
    const val MAX_AUTO_SCALE = 1.06f

    // 用户「UI 缩放」可调范围（设置对话框与 Theme 钳制共用，保持一致）
    const val MIN_UI_SCALE = 0.75f
    const val MAX_UI_SCALE = 1.15f

    // 原生底栏「高度调节」可调范围（设置对话框与底栏组件钳制共用，保持一致）
    // 下限 0.8：再小图标+文字会挤压（已按系统最大字体验算）；
    // 上限 1.2：避免底栏过高显得笨重
    const val MIN_BOTTOM_BAR_HEIGHT_SCALE = 0.8f
    const val MAX_BOTTOM_BAR_HEIGHT_SCALE = 1.2f

    /**
     * 宽度比例自适应系数（开方阻尼，双向生效）
     *
     * 400dp → 1.000（基准机零变化）  360dp → 0.949  340dp → 0.922
     * 411dp → 1.014  432dp → 1.039  460dp 及以上 → 1.06（封顶）
     */
    @Composable
    fun getAutoScale(): Float {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
        if (screenWidthDp <= 0f) return 1f
        return sqrt(screenWidthDp / BASE_SCREEN_WIDTH_DP)
            .coerceIn(MIN_AUTO_SCALE, MAX_AUTO_SCALE)
    }

    /**
     * 兼容旧调用点：自适应已收进 Theme 的 Density，这里恒返回 1.0，
     * 旧代码里的手动乘系数会变成无害的 no-op（请逐步删除）。
     */
    @Composable
    fun getContentScaleFactor(): Float = 1f

    /**
     * 兼容旧调用点：间距已随 density 全局缩放，这里恒返回 1.0。
     */
    @Composable
    fun getSpacingScaleFactor(): Float = 1f
}

/**
 * 应用统一的间距和尺寸规范
 * 所有值都基于小米14的设计稿
 */
object AppDimensions {
    // 导航栏高度（固定，不随屏幕变化）
    val NavigationBarHeight = 56.dp

    // 播放条高度（固定）
    val MiniPlayerHeight = 64.dp

    // 页面水平边距（固定）
    val PageHorizontalPadding = 16.dp

    // 卡片间距（固定）
    val CardSpacing = 12.dp

    // 小间距（固定）
    val SmallSpacing = 8.dp

    // 中间距（固定）
    val MediumSpacing = 16.dp

    // 大间距（固定）
    val LargeSpacing = 24.dp

    // 图标小尺寸（内容，需要缩放）
    val IconSmall = 20.dp

    // 图标中等尺寸（内容，需要缩放）
    val IconMedium = 24.dp

    // 图标大尺寸（内容，需要缩放）
    val IconLarge = 32.dp

    // 歌曲封面小尺寸（内容，需要缩放）
    val CoverSmall = 48.dp

    // 歌曲封面中等尺寸（内容，需要缩放）
    val CoverMedium = 52.dp

    // 顶部推荐卡片尺寸（内容，需要缩放）
    val TopCardWidth = 130.dp
    val TopCardHeight = 150.dp

    // 按钮高度（固定）
    val ButtonHeight = 48.dp

    // 圆角小（固定）
    val CornerSmall = 8.dp

    // 圆角中（固定）
    val CornerMedium = 12.dp

    // 圆角大（固定）
    val CornerLarge = 16.dp
}

/**
 * CompositionLocal 用于在 Composable 树中传递缩放比例
 */
val LocalScaleFactor = staticCompositionLocalOf { 1f }
