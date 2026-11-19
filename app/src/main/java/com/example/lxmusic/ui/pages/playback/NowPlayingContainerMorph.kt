package com.example.lxmusic.ui.pages.playback

import androidx.compose.ui.graphics.isSpecified
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.lxmusic.ui.animation.SharedCoverFlightState
import com.example.lxmusic.ui.animation.CoverFlightFrame
import com.example.lxmusic.ui.theme.addSmoothedRoundRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private const val MorphOpenDamping = 0.9f
private const val MorphOpenStiffness = 260f
private const val MorphCloseDurationMillis = 250
private const val MorphSettleFrames = 2
/** On close the card dissolves into the returning mini player instead of covering it. */
private const val CloseCardFadeSpan = 0.2f
/** On open the card fades in over the capsule so the mini player content cross-fades away. */
private const val OpenCardFadeSpan = 0.14f
/** The card keeps the capsule's own color at first and only takes the page tint as it grows. */
private const val CardColorSpan = 0.5f
private const val MorphScrimAlpha = 0.16f
/** Page content fades in over this slice of the morph; the card and artwork lead. */
private const val ContentFadeStart = 0.16f
private const val ContentFadeEnd = 0.76f
private val MorphCardColor = Color(0xFF19191C)
/** 圆角保持入口半径，直到最后 6% 才收成直角（参考项目同款） */
private const val CornerHoldUntil = 0.94f

/**
 * 展开/收起唯一的进度来源。
 *
 * 拖动期间读手指值（[dragged]），松手后交给 [progress] 动画；渲染层统一读 [value]，
 * MainActivity 的上拉/下拉都只调 [beginDrag]/[drag]/[finishDrag]，不再各自维护一份进度。
 */
@Stable
internal class NowPlayingMorphState(
    initiallyVisible: Boolean,
    private val scope: CoroutineScope
) {
    val progress = Animatable(if (initiallyVisible) 1f else 0f)
    var mounted by mutableStateOf(initiallyVisible)
        internal set
    var closing by mutableStateOf(false)
        internal set

    private var dragged by mutableStateOf<Float?>(null)
    /**
     * 松手后的意向落脚（true=展开/弹回，false=收起）。
     * showPlayerPage 是异步提交的，同帧内可能还没落定；有它就不依赖提交先后。
     */
    var keepOpen by mutableStateOf(initiallyVisible)
        internal set
    val dragging: Boolean get() = dragged != null
    val value: Float get() = dragged ?: progress.value
    val valueState: State<Float> = derivedStateOf { value }

    fun beginDrag() {
        if (dragged != null) return
        closing = false
        mounted = true
        dragged = progress.value
        scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.stop() }
    }

    fun drag(delta: Float) {
        dragged?.let { dragged = (it + delta).coerceIn(0f, 1f) }
    }

    /**
     * 松手：把手指值写回 Animatable 再交回动画，目标（开/关）由 [keepOpen] 决定。
     * UNDISPATCHED 保证在重组触发 effect 之前就写完，动画从手指位置接着走。
     */
    fun finishDrag(open: Boolean) {
        val from = dragged ?: return
        keepOpen = open
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            progress.snapTo(from)
            dragged = null
        }
    }
}

/**
 * Mounts the page on open and keeps it until the close morph lands. The open spring waits two
 * frames so the page's first (heavy) composition is drawn before the clock starts. While the
 * morph runs the shared artwork is handed to [NowPlayingContainerMorph] via [flight].
 */
@Composable
internal fun rememberNowPlayingMorphState(
    visible: Boolean,
    flight: SharedCoverFlightState?,
    artwork: () -> ImageBitmap? = { null }
): NowPlayingMorphState {
    val scope = rememberCoroutineScope()
    val state = remember { NowPlayingMorphState(initiallyVisible = visible, scope = scope) }
    val latestArtwork by rememberUpdatedState(artwork)
    LaunchedEffect(visible, state.dragging) {
        val linked = flight
        if (state.dragging) {
            // 手指接管进度：挂载 + 交接封面，等松手再决定落脚
            state.closing = false
            state.mounted = true
            linked?.beginLinked(latestArtwork())
            return@LaunchedEffect
        }
        if (visible || state.keepOpen) {
            state.closing = false
            state.mounted = true
            state.keepOpen = true
            if (state.progress.value < 1f) {
                linked?.beginLinked(latestArtwork())
                if (state.progress.value <= 0f) repeat(MorphSettleFrames) { withFrameNanos { } }
                state.progress.animateTo(1f, spring(MorphOpenDamping, MorphOpenStiffness, 0.001f))
            }
            linked?.endLinked()
        } else if (state.mounted) {
            state.closing = true
            linked?.beginLinked(latestArtwork(), followMiniPlayerGeometry = true)
            state.progress.animateTo(0f, tween(MorphCloseDurationMillis, easing = FastOutSlowInEasing))
            state.mounted = false
            linked?.endLinked()
        }
    }
    DisposableEffect(flight) {
        onDispose { flight?.endLinked() }
    }
    return state
}

/**
 * Container transform from the mini player capsule to the full page, after the reference
 * motion: an opaque card grows out of the capsule, the page rides inside it scaled to the
 * card width and top-aligned, and the artwork flies on its own from the mini cover to the
 * page cover's live position so it stays crisp and continuous the whole way.
 */
@Composable
internal fun NowPlayingContainerMorph(
    morph: NowPlayingMorphState,
    flight: SharedCoverFlightState?,
    capsuleInWindow: () -> Rect?,
    cardColor: Color = MorphCardColor,
    // 播放条真实圆角（原生 20dp / 悬浮胶囊 32dp），由 MainActivity 传入
    entryCornerPx: Float? = null,
    /** 卡片底色要不要从播放条自身的填充色过渡（悬浮胶囊的玻璃色会让卡片先灰一下） */
    useEntryColor: Boolean = true,
    /**
     * 共享封面走直线插值而不是弧线。
     * 直线插值时「封面矩形」始终是「卡片矩形」的凸组合 → 永远完整地待在卡片内（用户要的「完整缩放」）；
     * 弧线的横向 ease-out 会比卡片长得快，封面会戳到卡片外面。
     */
    linearCover: Boolean = false,
    /** 内容显影起点（默认 ContentFadeStart）。黑胶/唱片机页面整页是圆盘，小卡片里裁切很难看，传更大的值让它晚点显影 */
    contentFadeStart: Float? = null,
    content: @Composable () -> Unit
) {
    val progressState = morph.valueState
    val startColor = { if (useEntryColor) flight?.miniPlayerContainerColor ?: Color.Unspecified else Color.Unspecified }
    LaunchedEffect(flight) {
        snapshotFlow { progressState.value }.collect { flight?.nowPlayingMorphProgress = it }
    }
    DisposableEffect(flight, progressState) {
        val owner = flight
        owner?.nowPlayingMorphProgressState = progressState
        onDispose {
            if (owner != null && owner.nowPlayingMorphProgressState === progressState) {
                owner.nowPlayingMorphProgressState = null
                owner.nowPlayingMorphProgress = 0f
            }
        }
    }
    val density = LocalDensity.current
    val cornerStartPx = entryCornerPx ?: with(density) { 22.dp.toPx() }
    val geometry = remember { MorphGeometry() }
    val coverPath = remember { Path() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { geometry.origin = it.positionInWindow() }
            .drawBehind {
                val p = morph.value.coerceIn(0f, 1f)
                if (p >= 0.999f) return@drawBehind
                val scrim = MorphScrimAlpha * p
                if (scrim > 0f) drawRect(Color.Black.copy(alpha = scrim))
                val card = geometry.card(p, size, capsuleInWindow())
                val corner = morphCorner(p, cornerStartPx)
                val fadeSpan = if (morph.closing) CloseCardFadeSpan else OpenCardFadeSpan
                val cardAlpha = smoothStep(p / fadeSpan)
                val color = startColor().let { start ->
                    if (start.isSpecified) {
                        androidx.compose.ui.graphics.lerp(start.copy(alpha = 1f), cardColor, smoothStep(p / CardColorSpan))
                    } else {
                        cardColor
                    }
                }
                drawRoundRect(
                    color = color.copy(alpha = cardAlpha),
                    topLeft = card.topLeft,
                    size = card.size,
                    cornerRadius = CornerRadius(corner)
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = morph.value.coerceIn(0f, 1f)
                    if (p >= 0.999f) {
                        geometry.translation = Offset.Zero
                        return@graphicsLayer
                    }
                    val card = geometry.card(p, size, capsuleInWindow())
                    // 参考项目做法：内容按整屏布局一次不缩放，只移动/裁剪窗口。
                    // 这样文字、歌词、AndroidView 背景都不会被压成缩略图。
                    translationX = card.left
                    translationY = card.top
                    val fadeStart = contentFadeStart ?: ContentFadeStart
                    alpha = ((p - fadeStart) / (ContentFadeEnd - fadeStart)).coerceIn(0f, 1f)
                    shape = MorphCardShape(
                        width = card.width,
                        height = card.height,
                        cornerPx = morphCorner(p, cornerStartPx)
                    )
                    clip = true
                    geometry.translation = Offset(card.left, card.top)
                }
                .onPlaced { geometry.content = it }
        ) {
            content()
        }
        Canvas(Modifier.fillMaxSize()) {
            if (flight?.linked != true || !flight.hidesCovers) return@Canvas
            val p = morph.value.coerceIn(0f, 1f)
            val bitmap = flight.linkedBitmap ?: return@Canvas
            val from = flight.miniCoverBounds?.translate(-geometry.origin) ?: return@Canvas
            // 目标 = 封面在页面里的“最终”矩形（页面本地坐标；p=1 时等于屏幕坐标）。
            // 不跟随页面当前的缩放/位移：否则小尺寸阶段整页被压进播放条时，位于页面中部的
            // 封面会被压到卡片外、再被钳制压扁 —— 观感就是“折叠”。用最终位置做线性插值，
            // 封面始终是完整比例，从播放条那个高度匀速长大，不往下掉。
            val host = geometry.content ?: return@Canvas
            val to = flight.targetFrameIn(host) ?: return@Canvas
            // 参考项目的共享元素轨迹：横向先快后慢（ease-out），纵向与尺寸先慢后快（ease-in），
            // 观感是「先往里收一点，再抬起来」，不是简单的直线插值。
            // 例外走直线：
            // - 悬浮胶囊：保证封面矩形始终在卡片矩形内（完整缩放、不被裁掉）；
            // - 圆形目标（圆盘盘芯 / 圆形专辑）：圆形不该「先甩个方向再落」，直线放大最自然，
            //   也避免和旋转的圆盘对不上。
            // 目标端是圆（圆盘盘芯 / 圆形专辑）：全程按圆画 + 直线放大，别让中间阶段是圆角方块
            val targetCircular = to.cornerPx >= minOf(to.bounds.width, to.bounds.height) / 2f - 1f
            val rect = if (linearCover || targetCircular) lerp(from, to.bounds, p)
            else sheetElementArc(from, to.bounds, p)
            if (rect.width <= 1f || rect.height <= 1f) return@Canvas
            val corner = if (targetCircular) minOf(rect.width, rect.height) / 2f
            else lerp(flight.miniCoverCornerPx, to.cornerPx, p)
            // ⚠️ 圆形目标必须 smoothing=0：continuous corner（平滑圆角）在 corner=短边一半时
            // 画出来是「超椭圆/方圆形」，不是正圆 —— 圆盘盘芯里会看到一块方圆形的封面。
            val smoothing = if (targetCircular) 0f
            else lerp(flight.miniCoverCornerSmoothing, to.cornerSmoothing, p)
            coverPath.reset()
            coverPath.addSmoothedRoundRect(rect.width, rect.height, corner, smoothing)
            translate(rect.left, rect.top) {
                clipPath(coverPath) {
                    val (srcOffset, srcSize) = centerCrop(bitmap, rect.size)
                    drawImage(
                        image = bitmap,
                        srcOffset = srcOffset,
                        srcSize = srcSize,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt())
                    )
                }
            }
        }
    }
}

private fun smoothStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** 保持入口圆角直到 [CornerHoldUntil]，最后收成直角，交还给页面时不跳变。 */
private fun morphCorner(p: Float, entryPx: Float): Float =
    if (p < CornerHoldUntil) entryPx
    else lerp(entryPx, 0f, (p - CornerHoldUntil) / (1f - CornerHoldUntil))

/** Card tint from the cover seed, kept dark so white chrome and text read on it immediately. */
internal fun nowPlayingMorphCardColor(seedHex: String?): Color {
    val seed = seedHex?.removePrefix("#")?.toLongOrNull(16) ?: return MorphCardColor
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL((0xFF000000 or seed).toInt(), hsl)
    hsl[1] = hsl[1].coerceAtMost(0.42f)
    hsl[2] = 0.16f
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Shared per-frame geometry; plain fields because only draw and layer blocks read them. */
private class MorphGeometry {
    var origin: Offset = Offset.Zero
    var content: LayoutCoordinates? = null
    var scale: Float = 1f
    var translation: Offset = Offset.Zero

    fun card(p: Float, size: Size, capsuleInWindow: Rect?): Rect {
        val capsule = capsuleInWindow?.translate(-origin)
            // 重建场景没有播放条可参考：从底部一条小卡长出来，而不是整屏淡入
            ?: Rect(
                left = 26.dp.value * (size.width / 393f),
                top = size.height - 64.dp.value * (size.width / 393f),
                right = size.width - 26.dp.value * (size.width / 393f),
                bottom = size.height
            )
        return Rect(
            left = lerp(capsule.left, 0f, p),
            top = lerp(capsule.top, 0f, p),
            right = lerp(capsule.right, size.width, p),
            bottom = lerp(capsule.bottom, size.height, p)
        )
    }

    /** Page artwork bounds in this layer's space, following the content scale and translation. */
    fun pageCover(flight: SharedCoverFlightState): CoverFlightFrame? {
        val host = content ?: return null
        if (!host.isAttached) return null
        val frame = flight.targetFrameIn(host) ?: return null
        val local = frame.bounds
        val bounds = Rect(
            left = translation.x + local.left * scale,
            top = translation.y + local.top * scale,
            right = translation.x + local.right * scale,
            bottom = translation.y + local.bottom * scale
        )
        return frame.copy(bounds = bounds, cornerPx = frame.cornerPx * scale)
    }
}

private class MorphCardShape(
    private val width: Float,
    private val height: Float,
    private val cornerPx: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = width.coerceIn(1f, size.width)
        val h = height.coerceIn(1f, size.height)
        val rect = Rect(0f, 0f, w, h)
        val corner = cornerPx.coerceAtMost(minOf(w, h) / 2f)
        return Outline.Rounded(RoundRect(rect, CornerRadius(corner)))
    }
}

private fun centerCrop(bitmap: ImageBitmap, dst: Size): Pair<IntOffset, IntSize> {
    val scale = max(dst.width / bitmap.width, dst.height / bitmap.height)
    val srcWidth = (dst.width / scale).roundToInt().coerceIn(1, bitmap.width)
    val srcHeight = (dst.height / scale).roundToInt().coerceIn(1, bitmap.height)
    return IntOffset((bitmap.width - srcWidth) / 2, (bitmap.height - srcHeight) / 2) to IntSize(srcWidth, srcHeight)
}

private fun lerp(start: Rect, stop: Rect, fraction: Float): Rect = Rect(
    left = lerp(start.left, stop.left, fraction),
    top = lerp(start.top, stop.top, fraction),
    right = lerp(start.right, stop.right, fraction),
    bottom = lerp(start.bottom, stop.bottom, fraction)
)

/**
 * 共享元素弧线（移植参考项目 playerSheetElementArc）：
 * 横向 ease-out（先快后慢），纵向与尺寸 ease-in（先慢后快）。
 */
private fun sheetElementArc(start: Rect, end: Rect, progress: Float): Rect {
    val p = progress.coerceIn(0f, 1f)
    val horizontal = 1f - (1f - p) * (1f - p)
    val vertical = p * p
    val left = lerp(start.left, end.left, horizontal)
    val top = lerp(start.top, end.top, vertical)
    val width = lerp(start.width, end.width, vertical)
    val height = lerp(start.height, end.height, vertical)
    return Rect(left, top, left + width, top + height)
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction
