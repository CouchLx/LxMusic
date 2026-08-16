package com.example.lxmusic.ui.components.bottombar

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================== 胶囊形状 ====================

internal fun resolveSharedBottomBarCapsuleShape(): Shape =
    RoundedCornerShape(percent = 50)

// ==================== Shell 颜色 ====================

internal fun resolveKernelSuBottomBarContainerColor(darkTheme: Boolean): Color {
    val surfaceContainer = if (darkTheme) {
        Color(36, 36, 36)
    } else {
        Color.White
    }
    return surfaceContainer.copy(alpha = 0.4f)
}

internal fun resolveKernelSuBottomBarShellColor(
    containerColor: Color,
    liquidGlassEnabled: Boolean,
    darkTheme: Boolean
): Color {
    return if (liquidGlassEnabled) {
        resolveKernelSuBottomBarContainerColor(darkTheme = darkTheme)
    } else {
        containerColor
    }
}

// ==================== 指示器颜色 ====================

internal fun resolveAndroidNativeIdleIndicatorSurfaceColor(darkTheme: Boolean): Color {
    return if (darkTheme) {
        Color.White.copy(alpha = 0.1f)
    } else {
        Color.Black.copy(alpha = 0.1f)
    }
}

internal fun resolveAndroidNativeIndicatorColor(
    themeColor: Color,
    darkTheme: Boolean
): Color {
    val fraction = if (darkTheme) 0.58f else 0.82f
    val softened = lerpColor(themeColor, Color.White, fraction)
    return softened.copy(alpha = if (darkTheme) 0.42f else 0.82f)
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    return Color(
        red = start.red + (stop.red - start.red) * fraction,
        green = start.green + (stop.green - start.green) * fraction,
        blue = start.blue + (stop.blue - start.blue) * fraction,
        alpha = start.alpha + (stop.alpha - start.alpha) * fraction
    )
}

// ==================== 指示器效果开关 ====================

internal fun resolveBottomBarIndicatorEffectsEnabled(
    liquidGlassEnabled: Boolean,
    blurEnabled: Boolean
): Boolean {
    return liquidGlassEnabled || blurEnabled
}

// ==================== 布局参数 ====================

internal data class AndroidNativeBottomBarTuning(
    val cornerRadiusDp: Dp,
    val shellShadowElevationDp: Dp,
    val shellBlurRadiusDp: Dp,
    val shellSurfaceAlpha: Float,
    val outerHorizontalPaddingDp: Dp,
    val innerHorizontalPaddingDp: Dp,
    val indicatorHeightDp: Dp,
    val indicatorLensRadiusDp: Dp
)

internal fun resolveAndroidNativeBottomBarTuning(): AndroidNativeBottomBarTuning {
    return AndroidNativeBottomBarTuning(
        cornerRadiusDp = 32.dp,
        shellShadowElevationDp = 0.6f.dp,
        shellBlurRadiusDp = 12.dp,
        shellSurfaceAlpha = 0.4f,
        outerHorizontalPaddingDp = 20.dp,
        innerHorizontalPaddingDp = 4.dp,
        indicatorHeightDp = 56.dp,
        indicatorLensRadiusDp = 24.dp
    )
}

// ==================== 底栏尺寸 ====================

internal fun resolveBottomBarFloatingHeightDp(labelMode: Int, isTablet: Boolean): Float {
    return when (labelMode) {
        0 -> if (isTablet) 72f else 66f
        2 -> if (isTablet) 54f else 52f
        else -> if (isTablet) 64f else 58f
    }
}

internal fun resolveBottomBarBottomPaddingDp(isFloating: Boolean, isTablet: Boolean): Float {
    if (!isFloating) return 0f
    return if (isTablet) 18f else 12f
}

// ==================== Dock 宽度 ====================

internal fun resolveKernelSuFloatingBottomBarWidth(
    containerWidthDp: Dp,
    itemCount: Int
): Dp {
    val preferredWidth = (76.dp * itemCount) + 8.dp
    val minWidth = (52.dp * itemCount) + 8.dp
    val maxBarWidth = 432.dp
    return preferredWidth.coerceIn(minWidth, maxBarWidth.coerceAtMost(containerWidthDp))
}

internal fun resolveKernelSuBottomBarItemSlotWidth(
    dockWidth: Dp,
    horizontalPadding: Dp,
    itemCount: Int
): Dp {
    return (dockWidth - horizontalPadding * 2) / itemCount
}

internal fun resolveKernelSuBottomBarItemCenterX(
    horizontalPadding: Dp,
    itemWidth: Dp,
    index: Int
): Dp {
    return horizontalPadding + itemWidth * index + itemWidth / 2
}

// ==================== Press 相关 resolver ====================

internal data class BottomBarBackdropPresetProgress(
    val shellProgress: Float,
    val captureProgress: Float,
    val indicatorProgress: Float
)

internal fun resolveBottomBarBackdropPresetProgress(
    motionProgress: Float,
    pressProgress: Float
): BottomBarBackdropPresetProgress {
    val clampedMotion = motionProgress.coerceIn(0f, 1f)
    val clampedPress = pressProgress.coerceIn(0f, 1f)
    return BottomBarBackdropPresetProgress(
        shellProgress = clampedPress,
        captureProgress = maxOf(clampedMotion, clampedPress * 0.72f),
        indicatorProgress = maxOf(clampedMotion, clampedPress)
    )
}

internal fun resolveBottomBarShellHighlightAlpha(
    glassEnabled: Boolean,
    pressProgress: Float,
    motionProgress: Float = 0f,
    isDragging: Boolean = false
): Float {
    if (!glassEnabled) return 0f
    val dragFloor = if (isDragging) 0.6f else 0f
    return maxOf(pressProgress, motionProgress, dragFloor).coerceIn(0f, 1f)
}

internal fun resolveBottomBarIndicatorGlowAlpha(
    glassEnabled: Boolean,
    pressProgress: Float,
    motionProgress: Float = 0f
): Float {
    if (!glassEnabled) return 0f
    return maxOf(pressProgress, motionProgress).coerceIn(0f, 1f)
}

// ==================== Shell lens 参数 ====================

internal data class BottomBarBackdropPresetLensSpec(
    val refractionHeightDp: Float,
    val refractionAmountDp: Float
)

internal fun resolveBottomBarBackdropPresetShellLens(
    preset: BottomBarLiquidGlassPreset,
    glassEnabled: Boolean
): BottomBarBackdropPresetLensSpec {
    if (!glassEnabled) return BottomBarBackdropPresetLensSpec(0f, 0f)
    return when (preset) {
        BottomBarLiquidGlassPreset.BILIPAI_TUNED -> BottomBarBackdropPresetLensSpec(24f, 24f)
        BottomBarLiquidGlassPreset.IOS26_REFINED -> BottomBarBackdropPresetLensSpec(24f, 24f)
    }
}

internal fun resolveBottomBarBackdropPresetIndicatorLens(
    preset: BottomBarLiquidGlassPreset,
    glassEnabled: Boolean
): BottomBarBackdropPresetLensSpec {
    if (!glassEnabled) return BottomBarBackdropPresetLensSpec(0f, 0f)
    return when (preset) {
        // 对齐官方 LiquidBottomTabs 指示器：lens(10dp, 14dp) + 色散，按压时更接近真实玻璃折射
        BottomBarLiquidGlassPreset.BILIPAI_TUNED -> BottomBarBackdropPresetLensSpec(10f, 14f)
        BottomBarLiquidGlassPreset.IOS26_REFINED -> BottomBarBackdropPresetLensSpec(10f, 14f)
    }
}
