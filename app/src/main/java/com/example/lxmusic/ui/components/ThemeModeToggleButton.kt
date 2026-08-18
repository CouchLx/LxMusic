package com.example.lxmusic.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 明暗主题切换胶囊按钮（100% 对齐 NeriPlayer 的 ThemeModeActionButton 动画设计）
 * 包含双层图标平滑反向旋转（±56°）、双向渐变缩放（0.56 ~ 1.0）、自适应容器背景色过渡、触感振动反馈与防连点
 *
 * 逻辑对齐：
 * - 当前处于浅色模式（isDark = false）：展示月亮图标（Moon），点击切换为深色模式；
 * - 当前处于深色模式（isDark = true）：展示太阳图标（Sun），点击切换为浅色模式。
 */
@Composable
fun ThemeModeToggleButton(
    isDark: Boolean,
    onClick: (Offset, Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 36.dp
) {
    val haptic = LocalHapticFeedback.current
    var centerInWindow by remember { mutableStateOf<Offset?>(null) }
    var revealStartRadiusPx by remember { mutableFloatStateOf(18f) }

    // 0f = 浅色模式（展示月亮），1f = 深色模式（展示太阳）
    val iconProgress by animateFloatAsState(
        targetValue = if (isDark) 1f else 0f,
        animationSpec = tween(durationMillis = 580, easing = FastOutSlowInEasing),
        label = "theme_toggle_icon_progress"
    )

    // 容器背景色自适应平滑过渡（对齐 NeriPlayer 的 420ms 容器补间）
    val containerColor by animateColorAsState(
        targetValue = if (isDark) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        },
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "theme_toggle_container_color"
    )

    Surface(
        onClick = {
            if (!enabled) return@Surface
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick(centerInWindow ?: Offset.Zero, revealStartRadiusPx)
        },
        enabled = enabled,
        shape = CircleShape,
        color = containerColor,
        tonalElevation = 1.dp,
        modifier = modifier
            .size(size)
            .onGloballyPositioned { coordinates ->
                revealStartRadiusPx = maxOf(coordinates.size.width, coordinates.size.height) / 2f
                centerInWindow = coordinates.positionInWindow() + Offset(
                    x = coordinates.size.width / 2f,
                    y = coordinates.size.height / 2f
                )
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // 月亮图标：浅色模式下完全展示（alpha=1, scale=1.0），深色模式下缩小并逆时针旋转淡出
            Icon(
                imageVector = Icons.Outlined.DarkMode,
                contentDescription = if (isDark) "切换为浅色模式" else "切换为深色模式",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        alpha = 1f - iconProgress
                        val scale = 0.56f + (1f - iconProgress) * 0.44f
                        scaleX = scale
                        scaleY = scale
                        rotationZ = -56f * iconProgress
                    }
            )

            // 太阳图标：深色模式下完全展示（alpha=1, scale=1.0），浅色模式下缩小并顺时针旋转淡出
            Icon(
                imageVector = Icons.Outlined.LightMode,
                contentDescription = if (isDark) "切换为浅色模式" else "切换为深色模式",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        alpha = iconProgress
                        val scale = 0.56f + iconProgress * 0.44f
                        scaleX = scale
                        scaleY = scale
                        rotationZ = 56f * (1f - iconProgress)
                    }
            )
        }
    }
}
