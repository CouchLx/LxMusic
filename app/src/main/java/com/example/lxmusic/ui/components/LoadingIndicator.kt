package com.example.lxmusic.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Google I/O 风格的多圆点波浪加载动画
 *
 * 特点：
 * - 3-5 个圆点依次缩放/淡入淡出
 * - 波浪式动画效果
 * - 可自定义颜色、大小、圆点数量
 */

@Composable
fun IOLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    dotCount: Int = 3,
    dotSize: Dp = 12.dp,
    spacing: Dp = 8.dp,
    animationDuration: Int = 1200
) {
    val infiniteTransition = rememberInfiniteTransition(label = "io_loading")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(dotCount) { index ->
            // 每个圆点延迟启动，形成波浪效果
            val delay = index * (animationDuration / dotCount)

            val scale by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = animationDuration / 2,
                        delayMillis = delay,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale_$index"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = animationDuration / 2,
                        delayMillis = delay,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha_$index"
            )

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .scale(scale)
                    .alpha(alpha)
                    .background(color, CircleShape)
            )
        }
    }
}

/**
 * 带旋转效果的 I/O 风格加载动画
 * 圆点围绕中心旋转
 */
@Composable
fun IORotatingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    dotCount: Int = 3,
    dotSize: Dp = 10.dp,
    orbitRadius: Dp = 20.dp,
    animationDuration: Int = 2000
) {
    val infiniteTransition = rememberInfiniteTransition(label = "io_rotating")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = animationDuration,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier.size(orbitRadius * 2 + dotSize * 2),
        contentAlignment = Alignment.Center
    ) {
        repeat(dotCount) { index ->
            val angle = (index * 360f / dotCount + rotation) * (Math.PI / 180f)
            val x = (kotlin.math.cos(angle) * orbitRadius.value).dp
            val y = (kotlin.math.sin(angle) * orbitRadius.value).dp

            val delay = index * (animationDuration / dotCount)
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = animationDuration / 3,
                        delayMillis = delay,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rot_scale_$index"
            )

            Box(
                modifier = Modifier
                    .offset(x, y)
                    .size(dotSize)
                    .scale(scale)
                    .background(color, CircleShape)
            )
        }
    }
}

/**
 * 音乐主题的加载动画
 * 类似音频波形的效果
 */
@Composable
fun MusicWaveIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 5,
    barWidth: Dp = 4.dp,
    maxBarHeight: Dp = 24.dp,
    animationDuration: Int = 800
) {
    val infiniteTransition = rememberInfiniteTransition(label = "music_wave")

    Row(
        modifier = modifier.height(maxBarHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            // 每个柱子不同的动画参数，模拟音频波形
            val delay = index * (animationDuration / barCount / 2)
            val randomPhase = (index * 0.7f) // 让波形更自然

            val heightFraction by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = animationDuration / 2,
                        delayMillis = delay,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wave_$index"
            )

            val actualHeight = maxBarHeight * (heightFraction * (0.5f + randomPhase * 0.5f)).coerceIn(0.2f, 1f)

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(actualHeight)
                    .background(color, CircleShape)
            )
        }
    }
}
