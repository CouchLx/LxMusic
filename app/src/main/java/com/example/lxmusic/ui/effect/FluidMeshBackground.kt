package com.example.lxmusic.ui.effect

/*
 * MeiloX 旗舰 Mesh 网格渐变动态背景（移植自 https://github.com/NEORUAA/MeiloX，GPL-3.0）。
 *
 * 渲染：GLSurfaceView(GLES30) 内对封面取色生成的 BHP 网格做流体形变，
 *       音频三频段 + 鼓点 pulse 驱动网格缩放/对比度/饱和度律动；
 *       暂停自动转静态省电（RENDERMODE_WHEN_DIRTY），切封面双缓冲交叉淡入。
 * 频谱：MeshSpectrumProvider（PCM tee 三频段分析，USB 独占同样有效）。
 *
 * 与 MeiloX 原版 FluidBackground 的差异：
 * - 不移植 PixelCopy/LayerBackdrop（那是它液态玻璃浮层的专用机制）；
 * - 封面加载用 Coil 2（项目版本），网格参数用 MeiloX 默认值。
 */

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.lxmusic.ui.effect.mesh.MeshBackgroundView
import com.example.lxmusic.util.MeshSpectrumProvider
import com.example.lxmusic.util.PlaybackSpectrum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FluidMeshBackground(
    coverModel: Any?,
    playing: Boolean,
    modifier: Modifier = Modifier,
    // 必须用带 AudioArtFetcher 的应用级 loader（LocalImageLoader），
    // 全局 Coil.imageLoader 解不了本地音频文件的内嵌封面 → 会黑屏
    imageLoader: ImageLoader = LocalImageLoader.current,
    // 封面缺失/加载失败时的兜底取色（动态渐变同源的专辑主色或主题色）
    fallbackColors: List<Color> = emptyList(),
    // MeiloX 默认灵敏度 0.1 × 2 = 0.2；0 = 关闭音频响应
    audioStrength: Float = 0.2f,
    flowSpeed: Float = 0.25f,
    // 渲染精度（Surface 缓冲缩放）与网格细分，默认对齐 MeiloX
    renderScale: Float = 0.75f,
    subdivision: Int = 50,
    // 静态模式：暂停且交叉淡入完成后停止渲染（省电）
    staticMode: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDark = isSystemInDarkTheme()
    val meshView = remember { MeshBackgroundView(context) }

    // GL 生命周期：跟随页面宿主（ON_RESUME 渲染 / ON_PAUSE 暂停 GL 线程省电）
    DisposableEffect(lifecycleOwner, meshView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> meshView.setHostStarted(true)
                Lifecycle.Event.ON_PAUSE -> meshView.setHostStarted(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            meshView.setHostStarted(false)
        }
    }

    // 封面 → 纹理（Coil 2 加载软件位图并 copy 出池，GL 渲染器持有并负责 recycle）
    LaunchedEffect(coverModel, imageLoader, fallbackColors) {
        val bitmap = if (coverModel == null) {
            createFallbackMeshBitmap(fallbackColors, isDark)
        } else {
            withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(coverModel)
                        .size(256)
                        .allowHardware(false)
                        .build()
                    val result = imageLoader.execute(request)
                    (result as? SuccessResult)?.drawable?.toBitmap()
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                } catch (_: Exception) {
                    null
                }
            } ?: createFallbackMeshBitmap(fallbackColors, isDark)
        }
        meshView.setAlbum(bitmap)
    }

    // 音频响应参数与播放状态
    LaunchedEffect(meshView, flowSpeed) {
        meshView.setFlowSpeed(flowSpeed)
    }
    LaunchedEffect(meshView, renderScale) {
        meshView.setRenderScale(renderScale)
    }
    LaunchedEffect(meshView, subdivision) {
        meshView.setSubdivision(subdivision)
    }
    LaunchedEffect(meshView, playing) {
        meshView.setPlaying(playing)
    }
    LaunchedEffect(meshView, staticMode) {
        meshView.setStaticMode(staticMode)
    }

    // 频谱收集：仅在播放中且有音频响应时开启（冷流取消即停止分析，省电）
    LaunchedEffect(meshView, playing, staticMode, audioStrength) {
        val reactive = playing && !staticMode && audioStrength > 0f
        if (reactive) {
            meshView.setAudioStrength(audioStrength)
            try {
                MeshSpectrumProvider.spectrum.collect { meshView.updateSpectrum(it) }
            } finally {
                meshView.updateSpectrum(PlaybackSpectrum.Zero)
                meshView.setAudioStrength(0f)
            }
        } else {
            meshView.updateSpectrum(PlaybackSpectrum.Zero)
            meshView.setAudioStrength(0f)
        }
    }

    // GL 首帧就绪状态：表面在封面纹理/网格准备好之前是「不透明黑」（renderer 清屏色），
    // 直接用 AndroidView 会看到打开播放器瞬间黑屏闪一下。
    var surfaceReady by remember(meshView) { mutableStateOf(meshView.hasRenderedAlbum) }
    DisposableEffect(meshView) {
        meshView.onSurfaceReadyChanged = { ready -> surfaceReady = ready }
        // GL 线程可能在我们注册监听之前就已经渲染完首帧（回调丢失），这里补读一次
        if (meshView.hasRenderedAlbum) surfaceReady = true
        onDispose { meshView.onSurfaceReadyChanged = null }
    }
    // 再兜一层轮询：注册竞态没覆盖到的场景（如表面重建）也能及时揭开占位
    LaunchedEffect(meshView) {
        repeat(30) {
            if (meshView.hasRenderedAlbum) {
                surfaceReady = true
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(100)
        }
        // 3 秒仍未就绪：放行，避免占位色永久挡住背景（宁可显示 GL 现状）
        surfaceReady = true
    }

    // 占位色与 renderer 的兜底位图同源，保证「占位 → 网格」切换时颜色不跳。
    // 颜色变化用 400ms 动画过渡：首次打开时「主题色 → 封面取色」不会硬切。
    val targetPlaceholder = remember(fallbackColors, isDark) {
        val colors = fallbackColors.filter { it != Color.Unspecified }
        when {
            colors.size >= 3 -> colors.take(3)
            colors.size == 2 -> listOf(colors[0], colors[1], colors[1])
            isDark -> listOf(Color(0xFF2C1E4A), Color(0xFF52154E), Color(0xFF111135))
            else -> listOf(Color(0xFF8EC5FC), Color(0xFFE0C3FC), Color(0xFFB8C0EC))
        }
    }
    val ph0 by androidx.compose.animation.animateColorAsState(
        targetValue = targetPlaceholder[0],
        animationSpec = tween(400),
        label = "meshPlaceholder0"
    )
    val ph1 by androidx.compose.animation.animateColorAsState(
        targetValue = targetPlaceholder[1],
        animationSpec = tween(400),
        label = "meshPlaceholder1"
    )
    val ph2 by androidx.compose.animation.animateColorAsState(
        targetValue = targetPlaceholder[2],
        animationSpec = tween(400),
        label = "meshPlaceholder2"
    )

    Box(modifier) {
        AndroidView(
            factory = { meshView },
            modifier = Modifier.fillMaxSize()
        )
        // 首帧未就绪时盖住 GL 的黑面，就绪后淡出（切歌/重建表面同样生效）
        AnimatedVisibility(
            visible = !surfaceReady,
            enter = fadeIn(tween(0)),
            exit = fadeOut(tween(360))
        ) {
            MeshLikePlaceholder(
                colors = listOf(ph0, ph1, ph2),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 仿网格占位：底色线性渐变 + 两个大的径向光斑，
 * 比纯渐变更接近 Mesh 的有机色块，打开播放器时「占位 → 网格」的切换不突兀。
 */
@Composable
private fun MeshLikePlaceholder(colors: List<Color>, modifier: Modifier = Modifier) {
    val c0 = colors.getOrElse(0) { Color.Black }
    val c1 = colors.getOrElse(1) { c0 }
    val c2 = colors.getOrElse(2) { c1 }
    // 缓慢漂移：占位阶段也有轻微流动感，切到真网格时不会显得"突然活了"
    val drift by rememberInfiniteTransition(label = "meshPlaceholderDrift").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "meshPlaceholderDriftValue"
    )
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        if (w <= 0f || h <= 0f) return@BoxWithConstraints
        val radius = maxOf(w, h)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(c0, c1, c2)))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(c1.copy(alpha = 0.9f), Color.Transparent),
                        center = Offset(w * (0.18f + 0.08f * drift), h * (0.16f + 0.06f * drift)),
                        radius = radius * 0.62f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(c2.copy(alpha = 0.9f), Color.Transparent),
                        center = Offset(w * (0.88f - 0.08f * drift), h * (0.80f - 0.06f * drift)),
                        radius = radius * 0.58f
                    )
                )
        )
    }
}

/**
 * 无封面兜底：用兜底取色画一张 64x64 渐变位图喂给 GL 网格（会再走 AlbumTextureProcessor
 * 的统一调色管线），保证本地无封面歌曲也有流动色彩而不是黑屏。
 */
private fun createFallbackMeshBitmap(colors: List<Color>, isDark: Boolean): Bitmap {
    val size = 64
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()

    // 压暗 20%，兜底背景不能亮过前景文字
    fun darken(c: Color): Int {
        val r = (c.red * 255f * 0.8f).toInt().coerceIn(0, 255)
        val g = (c.green * 255f * 0.8f).toInt().coerceIn(0, 255)
        val b = (c.blue * 255f * 0.8f).toInt().coerceIn(0, 255)
        return (255 shl 24) or (r shl 16) or (g shl 8) or b
    }

    val valid = colors.filter { it != Color.Unspecified }.map(::darken)
    val resolved = if (valid.size >= 2) {
        valid
    } else if (isDark) {
        listOf(0xFF2C1E4A.toInt(), 0xFF52154E.toInt(), 0xFF111135.toInt())
    } else {
        listOf(0xFF8EC5FC.toInt(), 0xFFE0C3FC.toInt(), 0xFFB8C0EC.toInt())
    }

    paint.shader = if (resolved.size >= 3) {
        SweepGradient(size / 2f, size / 2f, resolved.toIntArray(), null)
    } else {
        LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            resolved[0], resolved[1], Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    return bitmap
}
