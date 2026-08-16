package com.example.lxmusic.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import androidx.core.graphics.toColorInt
import java.util.Locale

/**
 * 默认种子色（Neri 风格）：蓝色
 */
internal const val DEFAULT_SEED_COLOR_HEX = "0061A4"

/**
 * 可用的取色风格（对齐 Neri 的 ThemeDefaults.PALETTE_STYLES）
 */
internal val PALETTE_STYLES = listOf(
    "TonalSpot",
    "Neutral",
    "Vibrant",
    "Expressive",
    "Rainbow",
    "FruitSalad",
    "Monochrome",
    "Fidelity",
    "Content"
)

/**
 * 预设种子色板（对齐 Neri 的 PRESET_COLORS）
 */
internal val PRESET_SEED_COLORS = listOf(
    "0061A4",
    "6750A4",
    "B3261E",
    "C425A8",
    "00897B",
    "388E3C",
    "FBC02D",
    "E65100"
)

/**
 * 解析种子色 hex：去 # 前缀、转大写，仅接受合法 6 位 hex，否则回退默认
 */
internal fun sanitizeSeedColorHex(value: String?): String {
    val normalized = value?.trim()?.removePrefix("#")?.uppercase(Locale.ROOT)
    return normalized?.takeIf { it.length == 6 && it.all { c -> c.isDigit() || c in 'A'..'F' } }
        ?: DEFAULT_SEED_COLOR_HEX
}

@Composable
fun LxMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    albumSeedColorHex: String? = null,
    customPrimaryColor: androidx.compose.ui.graphics.Color? = null,
    seedColorHex: String = DEFAULT_SEED_COLOR_HEX,
    paletteStyle: String = "TonalSpot",
    colorAnimation: Boolean = true,
    uiDensityScale: Float = 1f,
    content: @Composable () -> Unit
) {
    // 动态取色 = 根据当前歌曲专辑封面取色；无封面/提取失败时回退到用户种子色。
    // 不再使用系统壁纸（Monet）取色。
    val effectiveSeedHex = if (dynamicColor) {
        albumSeedColorHex?.takeIf { it.isNotBlank() } ?: seedColorHex
    } else {
        seedColorHex
    }

    val seed = Color(("#${sanitizeSeedColorHex(effectiveSeedHex)}").toColorInt())
    val generated = rememberDynamicColorScheme(
        seedColor = seed,
        isDark = darkTheme,
        style = resolvePaletteStyle(paletteStyle)
    )
    // 背景/表面淡色 tint：柔和淡色（浅色 5% / 深色 3.5%），让"背景淡淡变色"可见但不抢内容
    val baseScheme = generated.copy(
        background = tintWith(generated.background, seed, if (darkTheme) 0.035f else 0.05f),
        surface = tintWith(generated.surface, seed, if (darkTheme) 0.03f else 0.04f)
    )

    // 现代化主题 / 主题预设：主色覆盖，背景与表面同步跟随主色 tint
    val finalScheme = if (customPrimaryColor != null) {
        val isDark = darkTheme
        val tint = customPrimaryColor
        baseScheme.copy(
            primary = tint,
            onPrimary = if (isDark) Color.Black else Color.White,
            primaryContainer = tint.copy(alpha = if (isDark) 0.25f else 0.12f),
            onPrimaryContainer = tint,
            secondary = tint.copy(alpha = 0.8f),
            secondaryContainer = tint.copy(alpha = if (isDark) 0.2f else 0.1f),
            onSecondaryContainer = tint,
            tertiary = tint.copy(alpha = 0.6f),
            tertiaryContainer = tint.copy(alpha = if (isDark) 0.15f else 0.08f),
            onTertiaryContainer = tint,
            // background / surface 系列加主色 tint（柔和淡色，不抢内容）
            background = tintWith(baseScheme.background, tint, if (isDark) 0.05f else 0.03f),
            onBackground = baseScheme.onBackground,
            surface = tintWith(baseScheme.surface, tint, if (isDark) 0.06f else 0.04f),
            onSurface = baseScheme.onSurface,
            surfaceContainerLowest = tintWith(baseScheme.surfaceContainerLowest, tint, if (isDark) 0.04f else 0.03f),
            surfaceContainerLow = tintWith(baseScheme.surfaceContainerLow, tint, if (isDark) 0.05f else 0.03f),
            surfaceContainer = tintWith(baseScheme.surfaceContainer, tint, if (isDark) 0.08f else 0.04f),
            surfaceContainerHigh = tintWith(baseScheme.surfaceContainerHigh, tint, if (isDark) 0.12f else 0.06f),
            surfaceContainerHighest = tintWith(baseScheme.surfaceContainerHighest, tint, if (isDark) 0.18f else 0.1f),
            surfaceVariant = tintWith(baseScheme.surfaceVariant, tint, if (isDark) 0.1f else 0.05f),
            onSurfaceVariant = baseScheme.onSurfaceVariant
        )
    } else {
        baseScheme
    }

    // 全色系颜色过渡动画（对齐 Neri 的 animateColorScheme，切换主题/种子色时平滑过渡）
    val colorScheme = if (colorAnimation) animateColorScheme(finalScheme) else finalScheme

    // 计算屏幕缩放比例（自动适配）
    val contentScaleFactor = ScreenAdapter.getContentScaleFactor()

    // UI 缩放：整体缩放 density（对齐 NeriPlayer 的 AppUiDensityRoot），
    // 作用于所有 dp，调节后立即全局生效
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, uiDensityScale) {
        Density(
            density = baseDensity.density * uiDensityScale.coerceIn(0.85f, 1.2f),
            fontScale = baseDensity.fontScale
        )
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalScaleFactor provides contentScaleFactor
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * 将 [base] 向 [tint] 方向混合 [ratio]，用于背景/表面跟随主题色
 */
private fun tintWith(base: Color, tint: Color, ratio: Float): Color {
    if (ratio <= 0f) return base
    return Color(
        red = base.red + (tint.red - base.red) * ratio,
        green = base.green + (tint.green - base.green) * ratio,
        blue = base.blue + (tint.blue - base.blue) * ratio,
        alpha = base.alpha
    )
}

/**
 * 字符串 → material-kolor PaletteStyle（对齐 Neri 的 normalizePaletteStyle）
 */
internal fun resolvePaletteStyle(style: String): PaletteStyle = when (style) {
    "Neutral" -> PaletteStyle.Neutral
    "Vibrant" -> PaletteStyle.Vibrant
    "Expressive" -> PaletteStyle.Expressive
    "Rainbow" -> PaletteStyle.Rainbow
    "FruitSalad" -> PaletteStyle.FruitSalad
    "Monochrome" -> PaletteStyle.Monochrome
    "Fidelity" -> PaletteStyle.Fidelity
    "Content" -> PaletteStyle.Content
    else -> PaletteStyle.TonalSpot
}

private const val ThemeColorTransitionDurationMs = 420

@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    return target.copy(
        primary = animateThemeColor(target.primary, "theme-primary"),
        onPrimary = animateThemeColor(target.onPrimary, "theme-on-primary"),
        primaryContainer = animateThemeColor(target.primaryContainer, "theme-primary-container"),
        onPrimaryContainer = animateThemeColor(target.onPrimaryContainer, "theme-on-primary-container"),
        inversePrimary = animateThemeColor(target.inversePrimary, "theme-inverse-primary"),
        secondary = animateThemeColor(target.secondary, "theme-secondary"),
        onSecondary = animateThemeColor(target.onSecondary, "theme-on-secondary"),
        secondaryContainer = animateThemeColor(target.secondaryContainer, "theme-secondary-container"),
        onSecondaryContainer = animateThemeColor(target.onSecondaryContainer, "theme-on-secondary-container"),
        tertiary = animateThemeColor(target.tertiary, "theme-tertiary"),
        onTertiary = animateThemeColor(target.onTertiary, "theme-on-tertiary"),
        tertiaryContainer = animateThemeColor(target.tertiaryContainer, "theme-tertiary-container"),
        onTertiaryContainer = animateThemeColor(target.onTertiaryContainer, "theme-on-tertiary-container"),
        background = animateThemeColor(target.background, "theme-background"),
        onBackground = animateThemeColor(target.onBackground, "theme-on-background"),
        surface = animateThemeColor(target.surface, "theme-surface"),
        onSurface = animateThemeColor(target.onSurface, "theme-on-surface"),
        surfaceVariant = animateThemeColor(target.surfaceVariant, "theme-surface-variant"),
        onSurfaceVariant = animateThemeColor(target.onSurfaceVariant, "theme-on-surface-variant"),
        surfaceTint = animateThemeColor(target.surfaceTint, "theme-surface-tint"),
        inverseSurface = animateThemeColor(target.inverseSurface, "theme-inverse-surface"),
        inverseOnSurface = animateThemeColor(target.inverseOnSurface, "theme-inverse-on-surface"),
        error = animateThemeColor(target.error, "theme-error"),
        onError = animateThemeColor(target.onError, "theme-on-error"),
        errorContainer = animateThemeColor(target.errorContainer, "theme-error-container"),
        onErrorContainer = animateThemeColor(target.onErrorContainer, "theme-on-error-container"),
        outline = animateThemeColor(target.outline, "theme-outline"),
        outlineVariant = animateThemeColor(target.outlineVariant, "theme-outline-variant"),
        scrim = animateThemeColor(target.scrim, "theme-scrim"),
        surfaceBright = animateThemeColor(target.surfaceBright, "theme-surface-bright"),
        surfaceDim = animateThemeColor(target.surfaceDim, "theme-surface-dim"),
        surfaceContainer = animateThemeColor(target.surfaceContainer, "theme-surface-container"),
        surfaceContainerHigh = animateThemeColor(target.surfaceContainerHigh, "theme-surface-container-high"),
        surfaceContainerHighest = animateThemeColor(target.surfaceContainerHighest, "theme-surface-container-highest"),
        surfaceContainerLow = animateThemeColor(target.surfaceContainerLow, "theme-surface-container-low"),
        surfaceContainerLowest = animateThemeColor(target.surfaceContainerLowest, "theme-surface-container-lowest"),
        primaryFixed = animateThemeColor(target.primaryFixed, "theme-primary-fixed"),
        primaryFixedDim = animateThemeColor(target.primaryFixedDim, "theme-primary-fixed-dim"),
        onPrimaryFixed = animateThemeColor(target.onPrimaryFixed, "theme-on-primary-fixed"),
        onPrimaryFixedVariant = animateThemeColor(target.onPrimaryFixedVariant, "theme-on-primary-fixed-variant"),
        secondaryFixed = animateThemeColor(target.secondaryFixed, "theme-secondary-fixed"),
        secondaryFixedDim = animateThemeColor(target.secondaryFixedDim, "theme-secondary-fixed-dim"),
        onSecondaryFixed = animateThemeColor(target.onSecondaryFixed, "theme-on-secondary-fixed"),
        onSecondaryFixedVariant = animateThemeColor(target.onSecondaryFixedVariant, "theme-on-secondary-fixed-variant"),
        tertiaryFixed = animateThemeColor(target.tertiaryFixed, "theme-tertiary-fixed"),
        tertiaryFixedDim = animateThemeColor(target.tertiaryFixedDim, "theme-tertiary-fixed-dim"),
        onTertiaryFixed = animateThemeColor(target.onTertiaryFixed, "theme-on-tertiary-fixed"),
        onTertiaryFixedVariant = animateThemeColor(target.onTertiaryFixedVariant, "theme-on-tertiary-fixed-variant")
    )
}

@Composable
private fun animateThemeColor(target: Color, label: String): Color {
    if (target == Color.Unspecified) return target
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = ThemeColorTransitionDurationMs,
            easing = FastOutSlowInEasing
        ),
        label = label
    )
    return color
}
