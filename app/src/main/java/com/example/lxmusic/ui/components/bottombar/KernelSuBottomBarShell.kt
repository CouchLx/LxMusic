package com.example.lxmusic.ui.components.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * Shell 组件（对齐 BiliPai KernelSuBottomBarShell）
 *
 * 渲染底栏外壳容器，支持：
 * 1. 液态玻璃效果（drawBackdrop + vibrancy + blur + lens）
 * 2. 交互高光（手指跟随的径向渐变）
 * 3. 按下缩放（layerBlock）
 */
@Composable
internal fun KernelSuBottomBarShell(
    width: Dp,
    height: Dp,
    shellShape: Shape,
    backdrop: Backdrop?,
    containerColor: Color,
    glassEnabled: Boolean,
    glassMaterialSpec: BottomBarGlassMaterialSpec,
    pressProgress: Float,
    interactiveHighlightEnabled: Boolean = false,
    interactiveHighlightAlpha: Float = 0f,
    interactiveHighlightCenterXPx: Float = 0f,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(modifier = Modifier.width(width).height(height)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    // 按下时微微放大（对齐 BiliPai layerBlock）
                    if (glassEnabled && size.width > 0f) {
                        val s = lerp(1f, 1f + 16.dp.toPx() / size.width, pressProgress)
                        scaleX = s
                        scaleY = s
                    }
                }
                .then(
                    if (backdrop != null && glassEnabled) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { shellShape },
                            effects = {
                                if (glassMaterialSpec.vibrancy) {
                                    vibrancy()
                                }
                                val blurRadius = glassMaterialSpec.blurRadiusDp
                                if (blurRadius != null) {
                                    blur(blurRadius.dp.toPx())
                                }
                                if (glassMaterialSpec.shellRefractionHeightDp > 0f &&
                                    glassMaterialSpec.shellRefractionAmountDp > 0f
                                ) {
                                    lens(
                                        refractionHeight = glassMaterialSpec.shellRefractionHeightDp.dp.toPx(),
                                        refractionAmount = glassMaterialSpec.shellRefractionAmountDp.dp.toPx()
                                    )
                                }
                            },
                            highlight = {
                                // 对齐官方：细的方向性高光（0.5dp），弱化过假的白边，只留一圈自然反光
                                Highlight.Default.copy(
                                    width = 0.5.dp * glassMaterialSpec.highlightWidthScale,
                                    alpha = 0.5f
                                )
                            },
                            shadow = {
                                Shadow.Default.copy(
                                    color = Color.Black.copy(
                                        alpha = 0.1f * glassMaterialSpec.shadowAlphaScale
                                    )
                                )
                            },
                            innerShadow = glassMaterialSpec.innerRimGlow?.let { glow ->
                                {
                                    com.kyant.backdrop.shadow.InnerShadow(
                                        radius = glow.radiusDp.dp,
                                        alpha = glow.alpha,
                                        color = Color.Black
                                    )
                                }
                            },
                            onDrawSurface = {
                                drawRect(containerColor)
                                if (glassMaterialSpec.foregroundTint.alpha > 0f) {
                                    drawRect(glassMaterialSpec.foregroundTint)
                                }
                            }
                        )
                    } else {
                        Modifier.background(containerColor, shellShape)
                    }
                )
                // 交互高光（对齐 BiliPai bottomBarInteractiveHighlight）
                .then(
                    if (interactiveHighlightEnabled && interactiveHighlightAlpha > 0f) {
                        Modifier.drawWithContent {
                            drawContent()
                            val clampedAlpha = interactiveHighlightAlpha.coerceIn(0f, 1f)
                            if (clampedAlpha <= 0f) return@drawWithContent
                            val center = Offset(
                                x = interactiveHighlightCenterXPx.coerceIn(0f, size.width),
                                y = size.height * 0.5f
                            )
                            // 整体白洗
                            drawRect(
                                color = Color.White.copy(alpha = 0.06f * clampedAlpha),
                                blendMode = BlendMode.Plus
                            )
                            // 径向渐变高光
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.17f * clampedAlpha),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = size.minDimension * 1.2f
                                ),
                                blendMode = BlendMode.Plus
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .clip(shellShape),
            content = content
        )
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
