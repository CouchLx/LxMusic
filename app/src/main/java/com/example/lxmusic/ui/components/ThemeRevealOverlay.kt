package com.example.lxmusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * 屏幕圆形波纹扩散转场遮罩（对齐 NeriPlayer 的 ThemeRevealOverlay 算法）
 * 从点击按钮的中心位置向屏幕四角呈圆形扩散揭开新主题，边缘带有柔和微光光环
 */
@Composable
fun ThemeRevealOverlay(
    snapshot: ImageBitmap?,
    fallbackColor: Color,
    originInWindow: Offset,
    modifier: Modifier = Modifier,
    startRadiusPx: Float = 18f,
    durationMillis: Int = 680,
    onFinished: () -> Unit
) {
    var containerOffsetInWindow by remember { mutableStateOf(Offset.Zero) }
    var hasFinished by remember { mutableStateOf(false) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    val progress = remember { Animatable(0f) }

    val finishReveal = {
        if (!hasFinished) {
            hasFinished = true
            currentOnFinished()
        }
    }

    DisposableEffect(Unit) {
        onDispose(finishReveal)
    }

    LaunchedEffect(Unit) {
        try {
            progress.stop()
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
            )
        } finally {
            finishReveal()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerOffsetInWindow = coordinates.positionInWindow()
            }
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                // 优先绘制截取的完整前台界面快照，若无快照则回退到背景色
                if (snapshot != null) {
                    drawImage(
                        image = snapshot,
                        dstSize = IntSize(
                            width = size.width.roundToInt().coerceAtLeast(1),
                            height = size.height.roundToInt().coerceAtLeast(1)
                        )
                    )
                } else {
                    drawRect(color = fallbackColor)
                }

                val origin = originInWindow - containerOffsetInWindow
                val maxRadius = maxRevealRadius(origin, size)
                val initialRadius = startRadiusPx.coerceIn(1f, maxRadius.coerceAtLeast(1f))
                val radius = initialRadius + (maxRadius - initialRadius) * progress.value

                // 扩散边缘微光光环
                val haloAlpha = (1f - progress.value) * 0.15f
                if (haloAlpha > 0.001f) {
                    val haloRadius = radius * 1.08f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = haloAlpha),
                                Color.Transparent
                            ),
                            center = origin,
                            radius = haloRadius
                        ),
                        radius = haloRadius,
                        center = origin
                    )
                }

                // 核心：使用 BlendMode.Clear 将圆形区域掏空，透出底下刚刚切换完成的新主题
                drawCircle(
                    color = Color.Transparent,
                    radius = radius,
                    center = origin,
                    blendMode = BlendMode.Clear
                )
            }
    )
}

private fun maxRevealRadius(origin: Offset, size: Size): Float {
    val corners = listOf(
        Offset.Zero,
        Offset(size.width, 0f),
        Offset(0f, size.height),
        Offset(size.width, size.height)
    )
    return corners.maxOf { corner -> (corner - origin).getDistance() }
}
