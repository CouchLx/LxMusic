package com.example.lxmusic.ui.components.bottombar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

internal data class BottomBarClickPulseTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
)

/**
 * 点击脉冲形变（对齐 BiliPai resolveBottomBarClickPulseTransform）
 *
 * Phase 1 (0..0.18): 压缩 — scaleX 降到 0.945
 * Phase 2 (0.18..1.0): 释放 — 阻尼振荡回弹到 1.0
 */
internal fun resolveBottomBarClickPulseTransform(
    progress: Float
): BottomBarClickPulseTransform {
    val clamped = progress.coerceIn(0f, 1f)
    val compressionEnd = 0.18f
    val compressionAmount = 0.055f
    val reboundAmount = 0.18f
    val scaleX = when {
        clamped >= 1f -> 1f
        clamped <= compressionEnd -> {
            val pressProgress = (clamped / compressionEnd).coerceIn(0f, 1f)
            1f - compressionAmount * easeOut(pressProgress)
        }
        else -> {
            val releaseProgress = ((clamped - compressionEnd) / (1f - compressionEnd)).coerceIn(0f, 1f)
            val damping = ((1f - releaseProgress) * exp(-3.0 * releaseProgress)).toFloat()
            val wave = (
                -compressionAmount * cos(PI * releaseProgress) +
                    reboundAmount * sin(PI * releaseProgress)
                ).toFloat()
            1f + damping * wave
        }
    }
    return BottomBarClickPulseTransform(scaleX = scaleX)
}

/**
 * 落位回弹形变（对齐 BiliPai resolveBottomBarSettleReboundTransform）
 *
 * Phase 1 (0..0.20): 水平压缩 (-3.5%) + 垂直挤压 (+2.8%)
 * Phase 2: 阻尼正弦半波回弹 — scaleX +8.5%, scaleY +7.5%
 */
internal fun resolveBottomBarSettleReboundTransform(
    progress: Float
): BottomBarClickPulseTransform {
    val clamped = progress.coerceIn(0f, 1f)
    val compressionEnd = 0.20f
    val compressionScaleXAmount = 0.035f
    val compressionScaleYAmount = 0.028f
    val reboundScaleXAmount = 0.085f
    val reboundScaleYAmount = 0.075f
    if (clamped >= 1f) {
        return BottomBarClickPulseTransform(scaleX = 1f, scaleY = 1f)
    }
    if (clamped <= compressionEnd) {
        val compressionProgress = (clamped / compressionEnd).coerceIn(0f, 1f)
        val easedProgress = easeOut(compressionProgress)
        return BottomBarClickPulseTransform(
            scaleX = 1f - compressionScaleXAmount * easedProgress,
            scaleY = 1f + compressionScaleYAmount * easedProgress
        )
    }
    val releaseProgress = ((clamped - compressionEnd) / (1f - compressionEnd)).coerceIn(0f, 1f)
    val damping = ((1f - releaseProgress) * exp(-3.2 * releaseProgress)).toFloat()
    val reboundWave = damping * sin(PI.toFloat() * releaseProgress)
    return BottomBarClickPulseTransform(
        scaleX = 1f + reboundScaleXAmount * reboundWave,
        scaleY = 1f + reboundScaleYAmount * reboundWave
    )
}

/**
 * 记住点击脉冲动画（对齐 BiliPai rememberBottomBarClickPulseTransform）
 *
 * pulseKey 变化时触发：snapTo(0) → animateTo(1) over 240ms
 */
@Composable
internal fun rememberBottomBarClickPulseTransform(
    pulseKey: Int
): BottomBarClickPulseTransform {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 240, easing = LinearEasing)
        )
    }
    return resolveBottomBarClickPulseTransform(progress.value)
}

/**
 * 记住落位回弹动画（对齐 BiliPai rememberBottomBarSettleReboundTransform）
 *
 * pulseKey 变化时触发：snapTo(0) → animateTo(1) over 260ms
 */
@Composable
internal fun rememberBottomBarSettleReboundTransform(
    pulseKey: Int
): BottomBarClickPulseTransform {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 260, easing = LinearEasing)
        )
    }
    return resolveBottomBarSettleReboundTransform(progress.value)
}

private fun easeOut(fraction: Float): Float {
    // Approximate EaseOut: 1 - (1-x)^2
    val inv = 1f - fraction
    return 1f - inv * inv
}
