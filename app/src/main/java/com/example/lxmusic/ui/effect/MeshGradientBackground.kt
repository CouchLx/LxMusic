package com.example.lxmusic.ui.effect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.om252345.composemeshgradient.MeshGradient
import io.github.om252345.composemeshgradient.rememberMeshGradientState
import io.github.om252345.composemeshgradient.utils.SimplexNoise
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

private const val MESH_WIDTH = 5
private const val MESH_HEIGHT = 5

/** 一个颜色“源”：在归一化画布上占据某个位置，周围的控制点按距离加权吸收它的颜色。 */
private data class ColorSource(
    val pos: Offset,
    val color: Color
)

/**
 * 动态背景2：Apple Music 风格 Mesh 渐变（基于 ComposeMeshGradient，OpenGL ES 渲染）。
 *
 * 复刻 AMLL 的效果思路：
 * - 4 个高饱和色“源”散布在画布四个象限，控制点按反距离加权混色 → 形成
 *   有明显**弧形分界**的色块（而非整屏平滑渐变），与专辑多色分布对应。
 * - 内部控制点大幅扰动 + SimplexNoise 驱动流动，颜色随时间缓慢呼吸，
 *   边框静止区也有流动感。
 * - 暂停时背景静止。
 */
@Composable
fun MeshGradientBackground(
    colors: List<Color>,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = remember(colors) { buildSourcePalette(colors) }
    val initialPoints = remember { buildInitialPoints() }

    // 切歌换色时整体重建 Mesh（库的 state 只按 points remember，颜色不随之刷新）
    key(palette) {
        val initialColors = remember(palette) {
            buildGridColors(palette.map { it.color }.toTypedArray(), initialPoints, palette)
        }
        val meshState = rememberMeshGradientState(points = initialPoints, colors = initialColors)
        val scope = rememberCoroutineScope()

        LaunchedEffect(playing, palette) {
            var time = 0f
            var lastFrameTime = 0L
            val basePoints = initialPoints.toList()
            val targetPoints = initialPoints.toMutableList()

            while (true) {
                if (!playing) {
                    lastFrameTime = 0L
                    delay(100)
                    continue
                }
                withFrameNanos { frameTime ->
                    if (lastFrameTime == 0L) lastFrameTime = frameTime
                    val deltaTime = ((frameTime - lastFrameTime) / 1_000_000_000.0f).coerceIn(0f, 0.1f)
                    lastFrameTime = frameTime
                    time += deltaTime * 0.55f

                    // 内部控制点按 SimplexNoise 缓慢流动
                    for (i in targetPoints.indices) {
                        val col = i % MESH_WIDTH
                        val row = i / MESH_WIDTH
                        val isBorder = row == 0 || row == MESH_HEIGHT - 1 || col == 0 || col == MESH_WIDTH - 1
                        if (!isBorder) {
                            val bp = basePoints[i]
                            val noiseX = SimplexNoise.noise(bp.x * 1.5f, time + i) * 0.18f
                            val noiseY = SimplexNoise.noise(bp.y * 1.5f, time + i + 100f) * 0.18f
                            targetPoints[i] = Offset(
                                (bp.x + noiseX).coerceIn(0f, 1f),
                                (bp.y + noiseY).coerceIn(0f, 1f)
                            )
                        }
                    }

                    // 颜色呼吸：色源整体在「基础色 ↔ 暗化变体」间缓慢波动
                    val breathe = (sin(time * 0.9f) * 0.5f + 0.5f)
                    val breathingColors = Array(palette.size) { idx ->
                        lerpColor(palette[idx].color, darken(palette[idx].color, 0.28f), breathe)
                    }
                    val animatedColors = buildGridColors(breathingColors, targetPoints.toTypedArray(), palette)

                    scope.launch {
                        meshState.snapAllPoints(targetPoints.toList())
                        animatedColors.forEachIndexed { idx, c -> meshState.setColor(idx, c) }
                    }
                }
            }
        }

        Box(modifier = modifier) {
            MeshGradient(
                modifier = Modifier.fillMaxSize(),
                width = MESH_WIDTH,
                height = MESH_HEIGHT,
                globalSubdivisions = 64,
                state = meshState
            )
            // 柔和遮罩，保证前景歌词可读
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.16f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.18f)
                            )
                        )
                    )
            )
        }
    }
}

/**
 * 5x5 网格初始控制点。内部点做大幅随机扰动（±0.15），
 * 让色块边界在静止时就是有机的弧线（对应 AMLL 预设里控制点的随机偏移）。
 */
private fun buildInitialPoints(): Array<Offset> {
    val rand = Random(42)
    return Array(MESH_WIDTH * MESH_HEIGHT) { i ->
        val col = i % MESH_WIDTH
        val row = i / MESH_WIDTH
        val baseX = col / (MESH_WIDTH - 1f)
        val baseY = row / (MESH_HEIGHT - 1f)
        val isBorder = row == 0 || row == MESH_HEIGHT - 1 || col == 0 || col == MESH_WIDTH - 1
        if (isBorder) {
            Offset(baseX, baseY)
        } else {
            Offset(
                x = (baseX + (rand.nextFloat() - 0.5f) * 0.30f).coerceIn(0.05f, 0.95f),
                y = (baseY + (rand.nextFloat() - 0.5f) * 0.30f).coerceIn(0.05f, 0.95f)
            )
        }
    }
}

/**
 * 从专辑主色派生 4 个高饱和色源，分布在画布四个象限。
 * 明度/饱和度固定在高对比区间，避免专辑浅色变体导致整屏近色。
 * 专辑为灰白单色时（饱和度过低），退回一个靛蓝系默认色，仍呈现“单色动态”。
 */
private fun buildSourcePalette(colors: List<Color>): List<ColorSource> {
    val hero = colors.firstOrNull() ?: Color(0xFF6A5ACD)
    val h = if (hero.saturation() >= 0.05f) hero.hue() else 245f
    val s = 0.62f

    val c1 = fromHsl(h, s, 0.55f)                       // 亮：主色
    val c2 = fromHsl(h, s, 0.30f)                       // 暗：主色加深
    val c3 = fromHsl((h + 34f) % 360f, s, 0.50f)        // 色相偏移：暖调
    val c4 = fromHsl((h - 30f + 360f) % 360f, s, 0.34f) // 色相偏移：冷调

    return listOf(
        ColorSource(pos = Offset(0.14f, 0.20f), color = c1),
        ColorSource(pos = Offset(0.86f, 0.16f), color = c2),
        ColorSource(pos = Offset(0.20f, 0.84f), color = c3),
        ColorSource(pos = Offset(0.80f, 0.78f), color = c4)
    )
}

/**
 * 为每个控制点计算颜色：对所有色源按反距离加权求和。
 * 距离近的色源主导 → 自然形成色块，色块之间出现弧形分界。
 */
private fun buildGridColors(
    sourceColors: Array<Color>,
    points: Array<Offset>,
    palette: List<ColorSource>
): Array<Color> {
    return Array(points.size) { i ->
        var r = 0f; var g = 0f; var b = 0f; var a = 0f; var weightSum = 0f
        for (j in palette.indices) {
            val src = palette[j]
            val dx = points[i].x - src.pos.x
            val dy = points[i].y - src.pos.y
            val weight = 1f / (dx * dx + dy * dy + 0.06f)
            val c = sourceColors[j]
            r += c.red * weight
            g += c.green * weight
            b += c.blue * weight
            a += c.alpha * weight
            weightSum += weight
        }
        Color(r / weightSum, g / weightSum, b / weightSum, a / weightSum)
    }
}

private fun darken(color: Color, amount: Float): Color = lerpColor(color, Color.Black, amount)

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = a.alpha + (b.alpha - a.alpha) * tt
    )
}
