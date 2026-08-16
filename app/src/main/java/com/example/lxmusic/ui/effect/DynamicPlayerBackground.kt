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
 * 使用专辑封面提取的主色构建一个圆锥渐变（sweep gradient），
 * 渐变随 rotationAngle 整体旋转，产生整屏色彩流动的效果。
 * 暂停时 rotationAngle 不变，背景即静止。
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

        val center = Offset(size.width / 2f, size.height / 2f)
        // 超出屏幕绘制，避免旋转时四角露出空白
        val overscan = size.maxDimension * 1.5f
        val gradientColors = safeColors + safeColors.first()

        rotate(rotationAngle, pivot = center) {
            drawRect(
                brush = Brush.sweepGradient(
                    colors = gradientColors,
                    center = center
                ),
                topLeft = Offset(center.x - overscan / 2f, center.y - overscan / 2f),
                size = androidx.compose.ui.geometry.Size(overscan, overscan)
            )
        }

        // 轻度遮罩，保证前台文字可读
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.08f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.12f)
                )
            )
        )
    }
}

/**
 * 从专辑封面提取主色，生成 4 个**同色系柔和变体**用于渐变背景。
 *
 * 策略：取封面的主导色（vibrant > dominant > muted），
 * 保持其色相，生成饱和度 0.15~0.4、明度 0.78~0.92 的浅色变体，
 * 色相微调 ±8°~15° 形成渐变过渡。
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
 * 从 bitmap 提取主导色相，生成 4 个同色系柔和渐变色。
 */
internal fun extractDominantHueVariations(bitmap: Bitmap): List<Color> {
    val palette = Palette.from(bitmap)
        .maximumColorCount(16)
        .clearFilters()
        .generate()

    // 优先取鲜艳色，其次取主导色，最后取柔和色
    val heroSwatch = palette.vibrantSwatch
        ?: palette.dominantSwatch
        ?: palette.mutedSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: return emptyList()

    val hero = Color(heroSwatch.rgb)
    val h = hero.hue()
    val s = hero.saturation()
    val l = hero.lightness()

    // 如果色相无意义（极低饱和度的灰），用封面最大 population 的色
    if (s < 0.05f) {
        val fallback = palette.swatches.maxByOrNull { it.population }
            ?: return emptyList()
        val fb = Color(fallback.rgb)
        return generateHarmoniousVariations(fb.hue(), fb.saturation())
    }

    return generateHarmoniousVariations(h, s)
}

/**
 * 根据一个色相和饱和度，生成 4 个**浅色柔和变体**用于背景渐变。
 * 明度固定在 0.80~0.92 之间（浅色系），饱和度限定在 0.12~0.45。
 */
private fun generateHarmoniousVariations(hue: Float, saturation: Float): List<Color> {
    val baseS = saturation.coerceIn(0.12f, 0.45f)

    // 四个变体：明度略有差异，色相微调
    data class Variant(val hOffset: Float, val sFactor: Float, val l: Float)

    val variants = listOf(
        Variant(hOffset = -8f, sFactor = 0.9f, l = 0.88f),
        Variant(hOffset = 0f, sFactor = 1.0f, l = 0.92f),
        Variant(hOffset = 10f, sFactor = 0.85f, l = 0.84f),
        Variant(hOffset = 5f, sFactor = 0.95f, l = 0.80f)
    )

    return variants.map { v ->
        val adjustedH = (hue + v.hOffset + 360f) % 360f
        val adjustedS = (baseS * v.sFactor).coerceIn(0.10f, 0.50f)
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
