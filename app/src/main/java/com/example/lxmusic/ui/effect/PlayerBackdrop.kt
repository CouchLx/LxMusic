package com.example.lxmusic.ui.effect

/*
 * 播放器共享背景层：播放页与全屏歌词页复用同一背景渲染，
 * 6 种互斥模式：MeiloX 网格渐变 / 封面模糊背景 / 流体动态背景(Hyper) / 灵动流体光场 / 动态渐变 / 应用背景图片
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

/** 进程级封面提色缓存：key = 封面 model 字符串。打开播放器首帧直接命中，避免主题色闪烁。 */
private val albumColorCache =
    java.util.concurrent.ConcurrentHashMap<String, List<Color>>()

private fun albumColorKey(model: Any?): String? =
    model?.toString()?.takeIf { it.isNotBlank() }

/**
 * 预热封面提色缓存：换歌时调用。
 * 这样用户点开播放器时首帧就能用封面色，不会先闪一帧深色/主题色兜底。
 */
suspend fun warmAlbumColorCache(context: android.content.Context, imageLoader: coil.ImageLoader, model: Any) {
    val key = albumColorKey(model) ?: return
    if (albumColorCache.containsKey(key)) return
    val colors = extractAlbumColors(context, imageLoader, model)
    if (colors.size >= 2) albumColorCache[key] = colors
}

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
    playerFluidAmbientBg: Boolean = false,
    playerMeshBg: Boolean = false,
    // 频谱律动（Mesh）高级参数（对齐 MeiloX）
    meshFlowSpeed: Float = 0.25f,
    meshRenderScale: Float = 0.75f,
    meshSensitivity: Float = 0.1f,
    meshSubdivision: Int = 50,
    meshStaticMode: Boolean = false,
    meshAnimation: Boolean = true,
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
    // 进程级提色缓存（按封面 URL）：再次打开播放器时首帧就能用封面配色，
    // 不必先闪一下主题色再过渡到封面色
    var albumGradientColors by remember {
        mutableStateOf<List<Color>>(
            albumColorKey(coverModel)?.let { albumColorCache[it] } ?: emptyList()
        )
    }
    LaunchedEffect(coverModel) {
        if (coverModel != null) {
            val key = albumColorKey(coverModel)
            val cached = key?.let { albumColorCache[it] }
            if (cached != null) {
                albumGradientColors = cached
            } else {
                val colors = extractAlbumColors(context, imageLoader, coverModel)
                albumGradientColors = colors
                if (key != null && colors.size >= 2) albumColorCache[key] = colors
            }
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

    // 封面 / 壁纸是异步加载的：首次出图前保持透明，出图后淡入，
    // 避免打开播放器瞬间「先空白、再突然出现图片」闪一下。
    // 注意只在**首次**淡入：之后换歌/换壁纸保持不透明，否则整层背景会先淡出成底色再淡回来。
    // 非 AsyncImagePainter 的传入视为已就绪（保持旧行为）。
    val albumAsyncPainter = albumPainter as? coil.compose.AsyncImagePainter
    val bgAsyncPainter = bgPainter as? coil.compose.AsyncImagePainter
    val albumPainterReady = albumAsyncPainter == null ||
        albumAsyncPainter.state is coil.compose.AsyncImagePainter.State.Success
    val bgPainterReady = bgAsyncPainter == null ||
        bgAsyncPainter.state is coil.compose.AsyncImagePainter.State.Success
    var albumBgShown by remember { mutableStateOf(false) }
    var wallpaperBgShown by remember { mutableStateOf(false) }
    LaunchedEffect(albumPainterReady) { if (albumPainterReady) albumBgShown = true }
    LaunchedEffect(bgPainterReady) { if (bgPainterReady) wallpaperBgShown = true }
    val albumBgAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (albumPainterReady || albumBgShown) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "albumBgFade"
    )
    val wallpaperBgAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (bgPainterReady || wallpaperBgShown) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "wallpaperBgFade"
    )

    Box(modifier = modifier) {
        // 兜底色：所有效果都关闭时保证不透明，避免看穿到下层页面
        // （原先由各页面根 Box 的 .background 兜底，舞台层共享后统一在这里兜底）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        )
        // MeiloX 网格渐变背景（GL 网格 + 封面取色 + 频谱律动，最高优先级）
        if (playerMeshBg) {
            FluidMeshBackground(
                coverModel = coverModel,
                // 「背景动画」关闭后，暂停也持续流动（对齐 MeiloX shouldAnimate）
                playing = if (meshAnimation) playing else true,
                imageLoader = imageLoader,
                fallbackColors = dynamicBgColors,
                // 全局「音频律动响应」关闭时不做频谱驱动；灵敏度 ×2 对齐 MeiloX
                audioStrength = if (playerAudioReactive) {
                    (meshSensitivity * 2f).coerceIn(0f, 1f)
                } else 0f,
                flowSpeed = meshFlowSpeed,
                renderScale = meshRenderScale,
                subdivision = meshSubdivision,
                staticMode = meshStaticMode,
                modifier = Modifier.fillMaxSize()
            )
        }
        // 封面模糊背景（与流体/动态渐变/图片互斥）
        else if (playerCoverBlurBg) {
            Image(
                painter = albumPainter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(albumBgAlpha)
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
        // 灵动流体背景（仿 Apple Music 风格流体光场，多频有机流动与智能高饱和取色）
        else if (playerFluidAmbientBg) {
            FluidAmbientBackground(
                coverModel = coverModel,
                playing = playing,
                imageLoader = imageLoader,
                modifier = Modifier.fillMaxSize()
            )
        }
        // 动态渐变背景（与背景图片互斥，随专辑旋转流动）
        if (!playerMeshBg && !playerHyperBg && !playerCoverBlurBg && !playerFluidAmbientBg && dynamicBackground) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .dynamicPlayerBackground(
                        colors = dynamicBgColors,
                        rotationAngle = rotationAngle
                    )
            )
        }
        if (!playerMeshBg && !playerHyperBg && !playerCoverBlurBg && !playerFluidAmbientBg && !dynamicBackground &&
            backgroundEnhance
        ) {
            // 使用应用设置中的背景图片
            Image(
                painter = bgPainter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(playerBgOpacity * wallpaperBgAlpha)
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
