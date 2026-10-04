package com.example.lxmusic.ui.effect

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 灵动流体环境光场（仿 Apple Music 风格流体背景，对齐 MeiloX AmbientBackground）。
 *
 * 核心特性：
 * 1. 智能 Palette 防灰算法：强行过滤暗淡低饱和灰度，确保 4 个明艳高对比环境基色。
 * 2. 互质三周期流动算法（15s, 23s, 18s 反向循环）：彻底打破机械感，呈现自然流体呼吸与游走。
 * 3. 硬件级高斯漫反射（Android 12+ RenderEffect MIRROR 镜像）+ 1.3 倍缩放抗裁剪黑边。
 * 4. 底部非线性暗化遮罩，兼顾流光溢彩与歌词文字极佳可读性。
 */
@Composable
fun FluidAmbientBackground(
    coverModel: Any?,
    playing: Boolean,
    modifier: Modifier = Modifier,
    // 应用级 loader（带 AudioArtFetcher）：全局 Coil 默认 loader 解不了本地音频内嵌封面
    imageLoader: coil.ImageLoader = coil.compose.LocalImageLoader.current
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current

    val defaultColors = remember(isDark) {
        if (isDark) {
            listOf(
                Color(0xFF2C1E4A),
                Color(0xFF52154E),
                Color(0xFF111135),
                Color(0xFF0F0F1A)
            )
        } else {
            listOf(
                Color(0xFFE0C3FC),
                Color(0xFF8EC5FC),
                Color(0xFFB8C0EC),
                Color(0xFFD8D0E8)
            )
        }
    }

    var fluidColors by remember { mutableStateOf(defaultColors) }

    LaunchedEffect(coverModel) {
        if (coverModel == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                // 复用组合级单例：每封面 new ImageLoader 会泄漏独立线程池与内存缓存
                val loader = imageLoader
                val request = ImageRequest.Builder(context)
                    .data(coverModel)
                    .size(200)
                    .allowHardware(false)
                    .build()

                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.drawable.toBitmap()
                    val newColors = extractVibrantColorsImproved(bitmap, isDark)
                    if (newColors.size >= 4) {
                        fluidColors = newColors
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val animSpec = tween<Color>(durationMillis = 1200, easing = FastOutSlowInEasing)
    val c1 by animateColorAsState(fluidColors[0], animSpec, label = "fluid_c1")
    val c2 by animateColorAsState(fluidColors[1], animSpec, label = "fluid_c2")
    val c3 by animateColorAsState(fluidColors[2], animSpec, label = "fluid_c3")
    val c4 by animateColorAsState(fluidColors[3], animSpec, label = "fluid_c4")

    val blurEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        remember(density) {
            val radius = with(density) { 100.dp.toPx() }
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.MIRROR)
                .asComposeRenderEffect()
        }
    } else null

    val baseColor = if (isDark) {
        Color(
            red = c2.red * 0.35f,
            green = c2.green * 0.35f,
            blue = c2.blue * 0.35f,
            alpha = 1f
        )
    } else {
        Color(0xFFF3F3F5)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. 基底底色，杜绝任何透光漏色
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(baseColor)
        )

        // 2. 硬件模糊流体层（1.3倍抗边缘裁剪）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurEffect != null) {
                        Modifier.graphicsLayer {
                            renderEffect = blurEffect
                            scaleX = 1.3f
                            scaleY = 1.3f
                        }
                    } else {
                        Modifier
                            .graphicsLayer {
                                scaleX = 1.3f
                                scaleY = 1.3f
                            }
                            .blur(80.dp)
                    }
                )
        ) {
            FluidCanvas(
                colors = listOf(c1, c2, c3, c4),
                playing = playing
            )
        }

        // 3. 底部非线性暗化遮罩（保护歌词与底栏控制可读性）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.40f to Color.Black.copy(alpha = 0.12f),
                        0.75f to Color.Black.copy(alpha = 0.45f),
                        1f to Color.Black.copy(alpha = 0.72f)
                    )
                )
        )
    }
}

@Composable
private fun FluidCanvas(
    colors: List<Color>,
    playing: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fluid_movement")

    // 三种互质周期（15s, 23s, 18s），打破规律感
    val t1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (playing) 1f else 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "t1"
    )
    val t2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (playing) 1f else 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(23000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "t2"
    )
    val t3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (playing) 1f else 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "t3"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. 主色球（左上方，宽幅运动）
        drawCircle(
            color = colors[0].copy(alpha = 0.82f),
            radius = w * 0.72f,
            center = Offset(
                x = w * 0.2f + (w * 0.32f * t1),
                y = h * 0.32f - (h * 0.12f * t2)
            )
        )

        // 2. 辅助基底色球（右下方，沉浸定调）
        drawCircle(
            color = colors[1].copy(alpha = 0.85f),
            radius = w * 0.82f,
            center = Offset(
                x = w * 0.88f - (w * 0.32f * t2),
                y = h * 0.78f - (h * 0.18f * t1)
            )
        )

        // 3. 提亮光斑（中偏左，呼吸起伏）
        val scale3 = 0.80f + (0.35f * t3)
        drawCircle(
            color = colors[2].copy(alpha = 0.65f),
            radius = w * 0.52f * scale3,
            center = Offset(
                x = w * 0.35f,
                y = h * 0.52f + (h * 0.22f * t2)
            )
        )

        // 4. 补色/对比色斑（右上方，游走交汇）
        drawCircle(
            color = colors[3].copy(alpha = 0.62f),
            radius = w * 0.62f,
            center = Offset(
                x = w * 0.82f,
                y = h * 0.22f + (h * 0.38f * t3)
            )
        )
    }
}

/**
 * 改进版高饱和 Palette 取色算法：拒绝灰色，制造高对比度色彩。
 */
private fun extractVibrantColorsImproved(bitmap: Bitmap, isDark: Boolean): List<Color> {
    val palette = Palette.from(bitmap)
        .maximumColorCount(24)
        .clearFilters()
        .generate()

    val swatches = palette.swatches
    if (swatches.isEmpty()) {
        return if (isDark) {
            listOf(Color(0xFF2C1E4A), Color(0xFF52154E), Color(0xFF111135), Color(0xFF0F0F1A))
        } else {
            listOf(Color(0xFFE0C3FC), Color(0xFF8EC5FC), Color(0xFFB8C0EC), Color(0xFFD8D0E8))
        }
    }

    // 过滤掉纯黑、纯白以及极低饱和度的死灰
    val validSwatches = swatches.filter { swatch ->
        val hsl = swatch.hsl
        val sat = hsl[1]
        val light = hsl[2]
        sat > 0.15f && light in 0.12f..0.88f
    }

    val candidates = if (validSwatches.size >= 4) validSwatches else swatches

    // 按饱和度与人群加权排序
    val sortedByVibrance = candidates.sortedByDescending { it.hsl[1] * 1.5f + (it.population.toFloat() / 1000f) }

    val c1 = sortedByVibrance.getOrNull(0)?.let { Color(it.rgb) }
        ?: Color(palette.getVibrantColor(0xFF52154E.toInt()))
    val c2 = sortedByVibrance.getOrNull(1)?.let { Color(it.rgb) }
        ?: Color(palette.getDominantColor(0xFF2C1E4A.toInt()))
    val c3 = sortedByVibrance.getOrNull(2)?.let { Color(it.rgb) }
        ?: Color(palette.getMutedColor(0xFF111135.toInt()))
    val c4 = sortedByVibrance.getOrNull(3)?.let { Color(it.rgb) }
        ?: Color(palette.getLightVibrantColor(0xFF8EC5FC.toInt()))

    return listOf(c1, c2, c3, c4)
}
