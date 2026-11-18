package com.example.lxmusic.ui.shelf

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 「帮我挑一首」按钮的按压反馈：提亮、110ms 时放大 7%、回弹、440ms 归位。
 * 移植自 CurrentMusicX 的 RecordShelfPickButton.kt（GPL-3.0-only）。
 */
@Composable
internal fun RecordShelfPickButton(
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressure by animateFloatAsState(if (pressed) 1f else 0f, tween(60), label = "shelf pick pressure")
    val expansion = remember { Animatable(0f) }
    val light = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var pulse by remember { mutableStateOf<Job?>(null) }
    val scale = 1f + expansion.value * .07f + pressure * .012f
    val illumination = maxOf(light.value, pressure * .7f)
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(lerp(Color(0xFFD9DFF1), Color(0xFFF0F2F5), illumination))
            .border(1.dp, Color.White.copy(alpha = .35f + illumination * .25f), shape)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button) {
                pulse?.cancel()
                pulse = scope.launch {
                    launch {
                        expansion.snapTo(0f)
                        expansion.animateTo(
                            0f,
                            keyframes {
                                durationMillis = 440
                                0f at 0 using FastOutSlowInEasing
                                1f at 110 using FastOutSlowInEasing
                                -.12f at 290 using FastOutSlowInEasing
                                0f at 440
                            }
                        )
                    }
                    launch {
                        light.snapTo(0f)
                        light.animateTo(
                            0f,
                            keyframes {
                                durationMillis = 440
                                0f at 0
                                1f at 45
                                1f at 300
                                0f at 440
                            }
                        )
                    }
                }
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            Modifier.graphicsLayer { alpha = 1f - illumination * .24f },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
            content = content
        )
    }
}
