package com.example.lxmusic.ui.components.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * 指示器液态玻璃层（对齐 BiliPai KernelSuBottomBarIndicatorLayer）
 *
 * 当 backdrop 可用时，使用 drawBackdrop 采样背景实现折射效果。
 * pressProgress 驱动 highlight、innerShadow、onDrawSurface 的交互反馈。
 * layerBlock 应用果冻形变。
 */
@Composable
internal fun BoxScope.KernelSuBottomBarIndicatorLayer(
    visible: Boolean,
    indicatorWidth: Dp,
    indicatorHeight: Dp,
    shellShape: Shape,
    backdrop: Backdrop?,
    indicatorLensSpec: BottomBarBackdropPresetLensSpec,
    effectivePressProgress: Float,
    indicatorIdleSurfaceColor: Color,
    glassEnabled: Boolean,
    indicatorEffectsEnabled: Boolean,
    indicatorScaleX: Float,
    indicatorScaleY: Float,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    Box(
        modifier = modifier
            .width(indicatorWidth)
            .height(indicatorHeight)
            .then(
                if (backdrop != null && indicatorEffectsEnabled) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shellShape },
                        effects = {
                            // 磨砂：与 Shell 保持一致，指示器静止时也是磨砂玻璃而非清晰透明窗口
                            blur(8f.dp.toPx())
                            // 折射仅在按下时出现（对齐库示例 LiquidBottomTabs 指示器：lens × pressProgress）
                            // 正常状态 effectivePressProgress = 0f，指示器完全无折射，仅显示磨砂玻璃
                            val lensProgress = effectivePressProgress
                            if (lensProgress > 0f &&
                                indicatorLensSpec.refractionHeightDp > 0f &&
                                indicatorLensSpec.refractionAmountDp > 0f
                            ) {
                                lens(
                                    refractionHeight = indicatorLensSpec.refractionHeightDp.dp.toPx() * lensProgress,
                                    refractionAmount = indicatorLensSpec.refractionAmountDp.dp.toPx() * lensProgress,
                                    depthEffect = true,
                                    chromaticAberration = true
                                )
                            }
                        },
                        highlight = {
                            // 对齐官方：细的方向性高光（0.5dp），仅按压时淡入，避免过假的白边
                            Highlight.Default.copy(alpha = effectivePressProgress)
                        },
                        shadow = {
                            // 按下时浮起投影（对齐库示例指示器 Shadow(alpha = progress)）
                            Shadow(alpha = effectivePressProgress)
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = 8.dp * effectivePressProgress,
                                alpha = effectivePressProgress * 0.15f,
                                color = Color.Black
                            )
                        },
                        onDrawSurface = {
                            // 静止色淡出 + 暗色叠加淡入（对齐 BiliPai onDrawSurface）
                            val surfaceFade = (1f - effectivePressProgress).coerceIn(0f, 1f)
                            if (surfaceFade > 0f) {
                                drawRect(
                                    color = indicatorIdleSurfaceColor,
                                    alpha = surfaceFade
                                )
                            }
                            if (effectivePressProgress > 0f) {
                                drawRect(
                                    Color.Black.copy(alpha = 0.03f * effectivePressProgress)
                                )
                            }
                        },
                        layerBlock = {
                            if (indicatorEffectsEnabled) {
                                scaleX = indicatorScaleX
                                scaleY = indicatorScaleY
                            }
                        }
                    )
                } else {
                    Modifier
                        .graphicsLayer {
                            scaleX = indicatorScaleX
                            scaleY = indicatorScaleY
                        }
                        .background(indicatorIdleSurfaceColor, shellShape)
                }
            )
            .clip(shellShape)
    )
}
