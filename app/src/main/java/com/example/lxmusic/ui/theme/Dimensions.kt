package com.example.lxmusic.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * 专业屏幕适配方案
 * 
 * 核心思想：
 * 1. 以小米14（393dp宽）为基准设计
 * 2. 内容尺寸（图标、文字、图片）随屏幕放大
 * 3. 间距尺寸（边距、间距、高度）保持固定或轻微调整
 * 4. 所有适配逻辑集中在这里，其他地方只调用 scaleFactor
 * 
 * 使用方式：
 * - 内容尺寸：iconSize = 24.dp * scaleFactor
 * - 间距尺寸：padding = 16.dp（不加 scaleFactor）
 * - 混合尺寸：height = 56.dp + 4.dp * (scaleFactor - 1)  // 基础值 + 微调
 */
object ScreenAdapter {
    // 基准设计尺寸（小米14）
    const val BASE_SCREEN_WIDTH_DP = 393f

    /**
     * 获取内容缩放比例（用于图标、文字、图片等）
     * 大屏手机内容适当放大，小屏保持原样
     */
    @Composable
    fun getContentScaleFactor(): Float {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
        
        return when {
            // 小屏手机（<= 400dp）：不缩放
            screenWidthDp <= 400f -> 1.0f
            // 中屏手机（400-450dp）：内容放大到 1.0-1.08
            screenWidthDp <= 450f -> 1.0f + (screenWidthDp - 400f) / 50f * 0.08f
            // 大屏手机（> 450dp）：内容最大放大到 1.1
            else -> 1.08f + min((screenWidthDp - 450f) / 150f, 0.02f)
        }
    }

    /**
     * 获取间距缩放比例（用于边距、间距等）
     * 间距变化很小，保持视觉一致性
     */
    @Composable
    fun getSpacingScaleFactor(): Float {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
        
        return when {
            // 小屏：不缩放
            screenWidthDp <= 400f -> 1.0f
            // 中大屏：间距只微调 1.0-1.02（比之前更小）
            else -> 1.0f + min((screenWidthDp - 400f) / 250f, 0.02f)
        }
    }
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
