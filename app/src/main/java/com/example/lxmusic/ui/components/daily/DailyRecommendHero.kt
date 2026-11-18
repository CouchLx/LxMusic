package com.example.lxmusic.ui.components.daily

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * 首页「每日推荐」封面流里的一首歌。
 *
 * 与播放队列解耦：浏览只影响 [DailyRecommendHero] 的选中项，只有显式点播放才进队列。
 */
@Immutable
data class DailyHeroSong(
    val id: Long,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val durationMs: Long = 0
)

/**
 * 每日推荐 Hero：标题行 + 5 槽位伪 3D 封面流 + 歌名/歌手。
 *
 * 视觉与交互移植自 CurrentMusicX 的 DailyRecommendationHero（GPL-3.0），
 * 数值按原实现逐项对齐；组件与配色改为本项目的 Material3 体系。
 */
@Composable
fun DailyRecommendHero(
    songs: List<DailyHeroSong>,
    loading: Boolean,
    isLoggedIn: Boolean,
    isPlaying: Boolean,
    currentPlayingPath: String?,
    onPlay: (Int) -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    dateLabel: String = "",
    showRecordWhenPlaying: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val selectedIndex = songs.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0
    val selected = songs.getOrNull(selectedIndex)
    // 刷新或推荐顺序变化后，仍把同一首歌保持选中
    LaunchedEffect(selected?.id) { selectedId = selected?.id }
    val sameSong = selected != null && currentPlayingPath == "netease://${selected.id}"
    val canPlay = selected != null
    val primaryLabel = if (sameSong && isPlaying) "暂停推荐歌曲" else "播放推荐歌曲"

    Column(modifier.fillMaxWidth()) {
        // 标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "每日推荐",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            if (songs.isNotEmpty()) {
                TextButton(
                    onClick = onOpen,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("全部", fontSize = 13.sp)
                }
            }
        }
        // 副标题：日期 · 数量
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = listOfNotNull(
                    dateLabel.takeIf(String::isNotBlank),
                    when {
                        loading -> "更新中"
                        songs.isNotEmpty() -> "${songs.size} 首为你精选"
                        else -> null
                    }
                ).joinToString(" · "),
                fontSize = 11.sp,
                color = colors.onSurface.copy(alpha = .48f)
            )
        }

        when {
            songs.isNotEmpty() -> {
                val playSelected = { if (canPlay) onPlay(selectedIndex) }
                key(songs.map { it.id }) {
                    RecommendationCoverFlow(
                        songs = songs,
                        selected = selectedIndex,
                        onSelect = { selectedId = songs[it].id },
                        showRecord = showRecordWhenPlaying && sameSong && isPlaying,
                        onPlay = playSelected,
                        canPlay = canPlay,
                        playLabel = primaryLabel
                    )
                }
                Crossfade(
                    targetState = selected!!,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                    label = "recommendation caption"
                ) { song ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = song.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            modifier = Modifier.padding(top = 4.dp),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.onSurface.copy(alpha = .55f)
                        )
                    }
                }
            }

            loading -> LoadingRecommendationHero()

            else -> EmptyRecommendationHero(
                isLoggedIn = isLoggedIn,
                onLogin = onLogin,
                onRetry = onRetry
            )
        }
    }
}

/** 加载骨架：与真实封面流用同一组变换，避免加载完成时跳变 */
@Composable
private fun LoadingRecommendationHero() {
    val shimmer = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            shimmer.animateTo(1f, tween(900, easing = LinearEasing))
            shimmer.animateTo(0f, tween(900, easing = LinearEasing))
        }
    }
    val base = MaterialTheme.colorScheme.surfaceVariant
    val placeholderColor = base.copy(alpha = 0.35f + shimmer.value * 0.25f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val coverSize = (maxWidth / 2.38f).coerceIn(128.dp, 184.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(coverSize + 40.dp),
                contentAlignment = Alignment.Center
            ) {
                for (slot in listOf(-2, 2, -1, 1, 0)) {
                    Box(
                        Modifier
                            .size(coverSize)
                            .graphicsLayer {
                                val relative = slot.toFloat()
                                val scale = exp(-abs(relative) * .20f)
                                translationX = tanh(relative * .70f) * coverSize.toPx() * .98f
                                translationY = (1f - scale) * coverSize.toPx() * .42f
                                scaleX = scale
                                scaleY = scale
                                rotationZ = tanh(relative * .55f) * 12f
                            }
                            .clip(RoundedCornerShape(18.dp))
                            .background(placeholderColor)
                    )
                }
            }
        }
        Column(
            Modifier
                .width(180.dp)
                .heightIn(min = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .width(150.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(placeholderColor)
            )
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .width(100.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(placeholderColor)
            )
        }
    }
}

/** 空态 / 未登录 / 加载失败 */
@Composable
private fun EmptyRecommendationHero(
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isLoggedIn) Icons.Rounded.Refresh else Icons.Rounded.Cloud,
            contentDescription = null,
            modifier = Modifier.size(34.dp),
            tint = colors.primary.copy(alpha = .55f)
        )
        Text(
            text = if (isLoggedIn) "推荐暂时没加载出来" else "登录网易云，获取你的每日推荐",
            fontSize = 16.sp
        )
        Text(
            text = if (isLoggedIn) "稍后刷新，再来听点新歌" else "每日 30 首，根据你的口味生成",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = colors.onSurface.copy(alpha = .55f)
        )
        TextButton(onClick = if (isLoggedIn) onRetry else onLogin) {
            Text(if (isLoggedIn) "重新加载" else "去登录", fontSize = 13.sp)
        }
    }
}

/**
 * 封面流本体。
 *
 * 逐帧变换全部走 graphicsLayer（不触发重新布局）；只有「跨过中点」才改变绘制顺序，
 * 因此拖动时不会重建子组件。
 */
@Composable
private fun RecommendationCoverFlow(
    songs: List<DailyHeroSong>,
    selected: Int,
    onSelect: (Int) -> Unit,
    showRecord: Boolean,
    onPlay: () -> Unit,
    canPlay: Boolean,
    playLabel: String
) {
    val select by rememberUpdatedState(onSelect)
    val scope = rememberCoroutineScope()
    var targetPosition by remember { mutableFloatStateOf(selected.toFloat()) }
    val position = remember { Animatable(selected.toFloat()) }
    var release by remember { mutableIntStateOf(0) }
    var releaseVelocity by remember { mutableStateOf<Float?>(null) }
    var dragPosition by remember { mutableFloatStateOf(selected.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val renderedPosition = remember {
        derivedStateOf { if (dragging) dragPosition else position.value }
    }
    // 只有跨过中点才切换绘制顺序，逐帧位移都在 graphicsLayer 里
    val foreground = remember { derivedStateOf { renderedPosition.value.roundToInt() } }

    LaunchedEffect(selected, release) {
        val from = Math.floorMod(targetPosition.roundToInt(), songs.size)
        val difference = selected - from
        val delta = when {
            difference > songs.size / 2 -> difference - songs.size
            difference < -songs.size / 2 -> difference + songs.size
            else -> difference
        }
        targetPosition += delta
        if (!dragging) {
            val velocity = releaseVelocity ?: position.velocity
            releaseVelocity = null
            position.animateTo(
                targetPosition,
                spring(dampingRatio = .94f, stiffness = 135f, visibilityThreshold = .001f),
                initialVelocity = velocity
            )
        }
    }

    // 唱片从封面右侧滑出 + 跟随播放旋转
    val recordReveal = animateFloatAsState(
        targetValue = if (showRecord) 1f else 0f,
        animationSpec = spring(dampingRatio = .94f, stiffness = 115f, visibilityThreshold = .001f),
        label = "recommendation record"
    )
    val recordRotation = rememberRecordRotation(active = showRecord)

    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = "第 ${selected + 1} 首，共 ${songs.size} 首"
                customActions = listOf(
                    CustomAccessibilityAction("上一首推荐") {
                        if (songs.size > 1) {
                            onSelect(Math.floorMod(selected - 1, songs.size)); true
                        } else false
                    },
                    CustomAccessibilityAction("下一首推荐") {
                        if (songs.size > 1) {
                            onSelect((selected + 1) % songs.size); true
                        } else false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val coverSize = (maxWidth / 2.38f).coerceIn(128.dp, 184.dp)
        val gestureDistance = with(density) { (coverSize * .65f).toPx() }
        val stage = Modifier
            .fillMaxWidth()
            .height(coverSize + 40.dp)
            .pointerInput(songs.map { it.id }, gestureDistance) {
                var start = 0f
                var pulled = 0f
                var nearest = selected
                val velocityTracker = VelocityTracker()
                fun finish(cancelled: Boolean) {
                    val released = dragPosition
                    val velocity = if (cancelled || songs.size <= 1) 0f
                    else -velocityTracker.calculateVelocity().x / gestureDistance
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        position.snapTo(released)
                        targetPosition = released.roundToInt().toFloat()
                        if (!cancelled) {
                            // 保留松手速度，最多再多走一张
                            targetPosition = (released + velocity * .12f)
                                .coerceIn(released - 1f, released + 1f)
                                .roundToInt().toFloat()
                            select(Math.floorMod(targetPosition.roundToInt(), songs.size))
                        }
                        releaseVelocity = velocity
                        dragging = false
                        release++
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = {
                        start = renderedPosition.value
                        pulled = 0f
                        dragPosition = start
                        dragging = true
                        nearest = start.roundToInt()
                        velocityTracker.resetTracking()
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { position.stop() }
                    },
                    onDragCancel = { finish(true) },
                    onDragEnd = { finish(false) }
                ) { change, amount ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    pulled += amount
                    if (songs.size > 1) {
                        dragPosition = start - pulled / gestureDistance
                        val next = dragPosition.roundToInt()
                        if (next != nearest) {
                            nearest = next
                            select(Math.floorMod(next, songs.size))
                        }
                    }
                }
            }

        Box(stage, contentAlignment = Alignment.Center) {
            val visible = minOf(songs.size, 7)
            val centre = foreground.value
            for (slot in centre - (visible - 1) / 2..centre + visible / 2) key(slot) {
                val index = Math.floorMod(slot, songs.size)
                Box(
                    Modifier
                        .size(coverSize)
                        .zIndex(10f - abs(slot - centre))
                        .graphicsLayer {
                            val relative = slot - renderedPosition.value
                            val depth = abs(relative)
                            val scale = exp(-depth * .20f)
                            translationX = tanh(relative * .70f) * coverSize.toPx() * .98f
                            translationY = (1f - scale) * coverSize.toPx() * .42f
                            scaleX = scale
                            scaleY = scale
                            rotationZ = tanh(relative * .55f) * 12f
                            alpha = (1f - (depth - 1f).coerceAtLeast(0f) * .14f).coerceIn(.35f, 1f)
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                ) {
                    // 唱片压在封面下面，只露出一部分
                    if (index == selected) {
                        VinylRecord(
                            accent = accent,
                            cover = songs[index].coverUrl,
                            modifier = Modifier
                                .size(coverSize * .9f)
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    translationX = coverSize.toPx() * .52f * recordReveal.value
                                    rotationZ = recordRotation.value
                                }
                        )
                    }
                    val artwork = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .semantics { contentDescription = songs[index].title }
                        .clickable(
                            enabled = index != selected || canPlay,
                            role = Role.Button,
                            onClickLabel = if (index == selected) playLabel else "浏览${songs[index].title}"
                        ) {
                            if (index == selected) onPlay() else onSelect(index)
                        }
                    DeckArtwork(songs[index].coverUrl, artwork)
                }
            }
        }
        PrefetchCovers((-4..4).map { songs[Math.floorMod(selected + it, songs.size)].coverUrl })
    }
}

/** 单张封面：占位图标 + Coil 网络图 */
@Composable
private fun DeckArtwork(cover: String, modifier: Modifier) {
    val context = LocalContext.current
    val model = remember(context, cover) {
        ImageRequest.Builder(context)
            .data(cover)
            .size(480)
            .crossfade(true)
            .build()
    }
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)
        )
        if (cover.isNotBlank()) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * 黑胶唱片：径向渐变盘面 + 12 圈同心音轨 + 扫掠高光 + 主色标签 + 中心封面。
 * 绘制参数与参考实现一致（盘面 3 色、ring 0..11、0.44~0.902 半径、210°/48° 高光弧）。
 */
@Composable
private fun VinylRecord(accent: Color, cover: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFF34363A), Color(0xFF121315), Color(0xFF292B2F)),
                    radius = radius
                )
            )
            for (ring in 0..11) {
                drawCircle(
                    color = Color.White.copy(alpha = if (ring % 3 == 0) .12f else .045f),
                    radius = radius * (.44f + ring * .042f),
                    style = Stroke(.7.dp.toPx())
                )
            }
            drawArc(
                color = Color.White.copy(alpha = .13f),
                startAngle = 210f,
                sweepAngle = 48f,
                useCenter = false,
                topLeft = Offset(radius * .16f, radius * .16f),
                size = size * .84f,
                style = Stroke(2.dp.toPx())
            )
            drawCircle(accent, radius * .26f)
            drawCircle(Color(0xFF101113), radius * .055f)
        }
        DeckArtwork(
            cover = cover,
            modifier = Modifier
                .fillMaxSize(.26f)
                .clip(CircleShape)
        )
        Canvas(Modifier.fillMaxSize(.05f)) { drawCircle(Color(0xFF101113)) }
    }
}

/** 唱片旋转：播放中 20s/圈线性，暂停即停在当前角度 */
@Composable
private fun rememberRecordRotation(active: Boolean, periodMs: Int = 20_000): State<Float> {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            angle.animateTo(angle.value + 360f, tween(periodMs, easing = LinearEasing))
            angle.snapTo(0f)
        }
    }
    return angle.asState()
}

/** 预取前后各 4 张封面，滑动时不出现白块 */
@Composable
private fun PrefetchCovers(covers: List<String>) {
    val imageLoader = LocalImageLoader.current
    val context = LocalContext.current
    LaunchedEffect(covers) {
        covers.filter { it.isNotBlank() }.forEach { url ->
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(480)
                    .build()
            )
        }
    }
}
