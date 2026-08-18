package com.example.lxmusic.ui.effect

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 播放器全屏动态渐变背景。
 *
 * 采用多重柔和径向环境光场与轨道流动算法（Organic Ambient Flow），
 * 彻底消除中心过曝亮点与扇形割裂感，呈现如高级音乐流媒体般的丝滑沉浸感。
 * 随 playback rotationAngle 平滑微动，暂停时保持静止。
 */
@Composable
fun Modifier.dynamicPlayerBackground(
    colors: List<Color>,
    rotationAngle: Float
): Modifier = composed {
    val safeColors = remember(colors) {
        if (colors.size < 2) emptyList() else colors
    }
    this.drawBehind {
        if (safeColors.isEmpty()) return@drawBehind

        val c1 = safeColors.getOrElse(0) { Color(0xFF1E1B2E) }
        val c2 = safeColors.getOrElse(1) { Color(0xFF2D1B36) }
        val c3 = safeColors.getOrElse(2) { Color(0xFF152238) }
        val c4 = safeColors.getOrElse(3) { Color(0xFF2A1C24) }

        val w = size.width
        val h = size.height
        val maxDim = max(w, h)

        // 1. 底层平滑全屏多阶深色基底渐变
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(c3, c2, c1),
                startY = 0f,
                endY = h
            )
        )

        // 弧度计算（随播放平滑旋转驱动多光晕轨道运动）
        val rad1 = Math.toRadians(rotationAngle.toDouble()).toFloat()
        val rad2 = Math.toRadians(rotationAngle * 0.72 + 120.0).toFloat()
        val rad3 = Math.toRadians(rotationAngle * 0.52 + 240.0).toFloat()

        // 2. 动态流动主光晕球（顶部/偏左上方主氛围光）
        val orb1Center = Offset(
            x = w * 0.38f + kotlin.math.cos(rad1) * (w * 0.22f),
            y = h * 0.32f + kotlin.math.sin(rad1) * (h * 0.16f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(c1.copy(alpha = 0.88f), c1.copy(alpha = 0.32f), Color.Transparent),
                center = orb1Center,
                radius = maxDim * 0.75f
            ),
            center = orb1Center,
            radius = maxDim * 0.75f
        )

        // 3. 动态流动次光晕球（右中/偏下方副氛围光）
        val orb2Center = Offset(
            x = w * 0.62f + kotlin.math.cos(rad2) * (w * 0.25f),
            y = h * 0.68f + kotlin.math.sin(rad2) * (h * 0.18f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(c2.copy(alpha = 0.82f), c2.copy(alpha = 0.28f), Color.Transparent),
                center = orb2Center,
                radius = maxDim * 0.80f
            ),
            center = orb2Center,
            radius = maxDim * 0.80f
        )

        // 4. 动态流动点缀光晕球（中下方对角流动光）
        val orb3Center = Offset(
            x = w * 0.48f + kotlin.math.sin(rad3) * (w * 0.26f),
            y = h * 0.82f + kotlin.math.cos(rad3) * (h * 0.14f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(c4.copy(alpha = 0.75f), Color.Transparent),
                center = orb3Center,
                radius = maxDim * 0.70f
            ),
            center = orb3Center,
            radius = maxDim * 0.70f
        )

        // 5. 顶底柔和微暗遮罩，确保歌名、状态栏与底栏控件极致清晰，同时抚平全屏过渡
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color.Black.copy(alpha = 0.32f),
                0.16f to Color.Black.copy(alpha = 0.08f),
                0.50f to Color.Transparent,
                0.84f to Color.Black.copy(alpha = 0.12f),
                1.0f to Color.Black.copy(alpha = 0.38f)
            )
        )
    }
}

/**
 * 从专辑封面提取主色，生成 4 个丰富且深邃的高级环境渐变色。
 */
suspend fun extractAlbumColors(context: Context, imageLoader: ImageLoader, model: Any): List<Color> =
    withContext(Dispatchers.Default) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(model)
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request)
            val bitmap = (result as? SuccessResult)?.drawable?.toBitmap()
                ?: return@runCatching emptyList<Color>()
            extractDominantHueVariations(bitmap)
        }.getOrDefault(emptyList())
    }

/**
 * 从专辑封面提取单个种子色（6 位 HEX，不含 #），供动态主题取色使用。
 *
 * 与 [extractAlbumColors] 同源：优先取鲜艳色（vibrant），
 * 其次主导色（dominant）、柔和色（muted）等，回退到封面最大面积色。
 * 提取失败返回 null，调用方应回退到用户种子色。
 */
suspend fun extractAlbumSeedHex(context: Context, imageLoader: ImageLoader, model: Any): String? =
    withContext(Dispatchers.Default) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(model)
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request)
            val bitmap = (result as? SuccessResult)?.drawable?.toBitmap()
                ?: return@runCatching null
            val palette = Palette.from(bitmap)
                .maximumColorCount(16)
                .clearFilters()
                .generate()

            val heroSwatch = palette.vibrantSwatch
                ?: palette.dominantSwatch
                ?: palette.mutedSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.swatches.maxByOrNull { it.population }
                ?: return@runCatching null

            val rgb = heroSwatch.rgb
            String.format(
                "%02X%02X%02X",
                (rgb shr 16) and 0xFF,
                (rgb shr 8) and 0xFF,
                rgb and 0xFF
            )
        }.getOrNull()
    }

/**
 * 从 bitmap 提取封面主色调与氛围色，生成 4 个丰富且深邃的高级渐变色。
 */
internal fun extractDominantHueVariations(bitmap: Bitmap): List<Color> {
    val palette = Palette.from(bitmap)
        .maximumColorCount(24)
        .clearFilters()
        .generate()

    val heroSwatch = palette.vibrantSwatch
        ?: palette.dominantSwatch
        ?: palette.mutedSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.swatches.maxByOrNull { it.population }
        ?: return emptyList()

    val hero = Color(heroSwatch.rgb)
    val h = hero.hue()
    val s = hero.saturation()

    // 如果色相无意义（极低饱和度的灰），用封面最大 population 的色
    if (s < 0.06f) {
        val fallback = palette.swatches.maxByOrNull { it.population }
            ?: return emptyList()
        val fb = Color(fallback.rgb)
        return generateHarmoniousVariations(fb.hue(), fb.saturation())
    }

    return generateHarmoniousVariations(h, s)
}

/**
 * 根据主色相和饱和度，生成 4 个**富有层次感的深邃高级环境色**用于背景流动。
 * 调整明度在 0.28~0.50 之间，饱和度在 0.35~0.75，避免过曝和泛白，营造沉浸式氛围。
 */
private fun generateHarmoniousVariations(hue: Float, saturation: Float): List<Color> {
    val baseS = saturation.coerceIn(0.35f, 0.75f)

    data class Variant(val hOffset: Float, val sFactor: Float, val l: Float)

    val variants = listOf(
        Variant(hOffset = 0f, sFactor = 1.0f, l = 0.42f),      // 主光晕色
        Variant(hOffset = 28f, sFactor = 0.90f, l = 0.35f),    // 邻近暖/冷调
        Variant(hOffset = -30f, sFactor = 0.85f, l = 0.28f),   // 深邃基底调
        Variant(hOffset = 18f, sFactor = 1.05f, l = 0.46f)     // 亮调氛围色
    )

    return variants.map { v ->
        val adjustedH = (hue + v.hOffset + 360f) % 360f
        val adjustedS = (baseS * v.sFactor).coerceIn(0.30f, 0.85f)
        fromHsl(adjustedH, adjustedS, v.l)
    }
}

// ==================== 颜色空间工具 ====================

internal fun Color.hue(): Float {
    val r = red; val g = green; val b = blue
    val maxC = max(r, max(g, b)); val minC = min(r, min(g, b))
    if (maxC == minC) return 0f
    val d = maxC - minC
    val h = when (maxC) {
        r -> (g - b) / d + if (g < b) 6f else 0f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    }
    return h * 60f
}

internal fun Color.saturation(): Float {
    val r = red; val g = green; val b = blue
    val maxC = max(r, max(g, b)); val minC = min(r, min(g, b))
    if (maxC == minC) return 0f
    val l = (maxC + minC) / 2f
    return if (l < 0.5f) (maxC - minC) / (maxC + minC) else (maxC - minC) / (2f - maxC - minC)
}

internal fun Color.lightness(): Float {
    return (max(red, max(green, blue)) + min(red, min(green, blue))) / 2f
}

internal fun fromHsl(h: Float, s: Float, l: Float): Color {
    if (s == 0f) return Color(l, l, l)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    val hn = h / 360f
    return Color(
        red = hueToRgb(p, q, hn + 1f / 3f),
        green = hueToRgb(p, q, hn),
        blue = hueToRgb(p, q, hn - 1f / 3f)
    )
}

private fun hueToRgb(p: Float, q: Float, t: Float): Float {
    var tt = t
    if (tt < 0f) tt += 1f
    if (tt > 1f) tt -= 1f
    return when {
        tt < 1f / 6f -> p + (q - p) * 6f * tt
        tt < 1f / 2f -> q
        tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
        else -> p
    }
}
