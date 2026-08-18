package com.example.lxmusic.ui.effect

/*
 * 播放器共享背景层：播放页与全屏歌词页复用同一背景渲染，
 * 5 种互斥模式：封面模糊背景 / 流体动态背景(Hyper) / Mesh 渐变 / 动态渐变 / 应用背景图片
 */

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.LocalImageLoader
import com.example.lxmusic.ui.view.HyperBackground

@Composable
fun PlayerBackdrop(
    backgroundColor: Color,
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color,
    coverModel: Any?,
    dynamicBackground: Boolean,
    backgroundEnhance: Boolean,
    playerHyperBg: Boolean,
    playerAudioReactive: Boolean,
    // 页面顶偏移提供器（px）：流体 View 绘制裁剪到页面覆盖范围（防松手瞬间占满）
    hyperClipTopProvider: (() -> Float)? = null,
    playerCoverBlurBg: Boolean,
    playerCoverBlurAmount: Float,
    playerCoverBlurDarken: Float,
    playerBgOpacity: Float,
    playerBlur: Boolean,
    rotationAngle: Float,
    playing: Boolean,
    bgPainter: Painter,
    albumPainter: Painter,
    modifier: Modifier = Modifier
) {
    // 动态渐变背景：根据专辑封面提取主色（切歌时重新提取，结果缓存）
    val context = LocalContext.current
    val imageLoader = LocalImageLoader.current
    var albumGradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    LaunchedEffect(coverModel) {
        if (coverModel != null) {
            albumGradientColors = extractAlbumColors(context, imageLoader, coverModel)
        }
    }

    // 提取失败时用主题色兜底，保证开启动态背景必有视觉效果
    val dynamicBgColors = remember(albumGradientColors, primaryColor, secondaryColor, tertiaryColor) {
        if (albumGradientColors.size >= 2) {
            albumGradientColors
        } else {
            listOf(primaryColor, secondaryColor, tertiaryColor, primaryColor)
        }
    }

    Box(modifier = modifier) {
        // 兜底色：所有效果都关闭时保证不透明，避免看穿到下层页面
        // （原先由各页面根 Box 的 .background 兜底，舞台层共享后统一在这里兜底）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        )
        // 封面模糊背景（与流体/动态渐变/图片互斥）
        if (playerCoverBlurBg) {
            Image(
                painter = albumPainter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(playerCoverBlurAmount.dp),
                contentScale = ContentScale.Crop
            )
            // 调暗遮罩保证内容可读
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = playerCoverBlurDarken.coerceIn(0f, 0.8f)))
            )
        }
        // 流体动态背景（HyperBackground，API 33+ 生效，与其他背景互斥）
        else if (playerHyperBg) {
            HyperBackground(
                isDark = androidx.compose.foundation.isSystemInDarkTheme(),
                coverUrl = coverModel,
                audioReactiveEnabled = playerAudioReactive,
                clipTopProvider = hyperClipTopProvider ?: { 0f },
                modifier = Modifier.fillMaxSize()
            )
        }
        // 动态渐变背景（与背景图片互斥，随专辑旋转流动）
        if (!playerHyperBg && !playerCoverBlurBg && dynamicBackground) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .dynamicPlayerBackground(
                        colors = dynamicBgColors,
                        rotationAngle = rotationAngle
                    )
            )
        }
        if (!playerHyperBg && !playerCoverBlurBg && !dynamicBackground &&
            backgroundEnhance
        ) {
            // 使用应用设置中的背景图片
            Image(
                painter = bgPainter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(playerBgOpacity)
                    .then(if (playerBlur) Modifier.blur(40.dp) else Modifier),
                contentScale = ContentScale.Crop
            )
            // 遮罩层保证内容可读
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                backgroundColor.copy(alpha = 0.3f),
                                backgroundColor.copy(alpha = 0.6f),
                                backgroundColor.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
        }
    }
}
