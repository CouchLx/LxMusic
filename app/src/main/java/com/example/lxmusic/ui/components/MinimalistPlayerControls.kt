package com.example.lxmusic.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.lxmusic.R
import com.example.lxmusic.ui.lyrics.LyricSeekHapticFeedback

// ==================== 几何路径平滑圆角辅助函数 ====================

/**
 * 为任意多边形顶点构建平滑二次贝塞尔圆角路径
 */
private fun createRoundedPolygonPath(vertices: List<Offset>, radius: Float): Path {
    val path = Path()
    val n = vertices.size
    if (n < 3) return path

    for (i in 0 until n) {
        val curr = vertices[i]
        val prev = vertices[(i - 1 + n) % n]
        val next = vertices[(i + 1) % n]

        val vPrev = prev - curr
        val lenPrev = vPrev.getDistance()
        val dirPrev = if (lenPrev > 0f) vPrev / lenPrev else Offset.Zero

        val vNext = next - curr
        val lenNext = vNext.getDistance()
        val dirNext = if (lenNext > 0f) vNext / lenNext else Offset.Zero

        val r = radius.coerceAtMost(minOf(lenPrev, lenNext) * 0.45f)
        val pStart = curr + dirPrev * r
        val pEnd = curr + dirNext * r

        if (i == 0) {
            path.moveTo(pStart.x, pStart.y)
        } else {
            path.lineTo(pStart.x, pStart.y)
        }
        path.quadraticTo(curr.x, curr.y, pEnd.x, pEnd.y)
    }
    path.close()
    return path
}

// ==================== 简约风进度条组件 ====================

@Composable
fun MinimalistProgressSection(
    isSeekingState: MutableState<Boolean>,
    sliderPositionState: MutableState<Float>,
    pendingPreviewState: MutableState<Long?>,
    progressRatio: Float,
    totalDuration: Long,
    currentPosition: Long,
    durationState: State<Long>,
    lyricHaptic: LyricSeekHapticFeedback,
    playerLyricsSeekPreview: Boolean,
    deepBackdrop: Boolean,
    primaryColor: Color,
    uiTint: Color,
    uiTintVariant: Color,
    onSeek: (Long) -> Unit,
    formatDuration: (Long) -> String,
    audioInfo: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
) {
    val isSeeking = isSeekingState.value
    val sliderPosition = sliderPositionState.value

    val barHeight by animateDpAsState(
        targetValue = if (isSeeking) 10.dp else 6.5.dp,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "minBarHeight"
    )

    Column(
        modifier = modifier
    ) {
        // 扁平胶囊进度条
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isSeekingState.value = true
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        fun update(x: Float) {
                            val max = durationState.value.toFloat().coerceAtLeast(1f)
                            val pos = (x.coerceIn(0f, width) / width) * max
                            sliderPositionState.value = pos
                            if (playerLyricsSeekPreview) {
                                lyricHaptic.onSeekMove(pos.toLong())
                            }
                        }
                        if (playerLyricsSeekPreview) {
                            lyricHaptic.onSeekStart(sliderPositionState.value.toLong())
                        }
                        try {
                            update(down.position.x)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                update(change.position.x)
                            }
                        } finally {
                            isSeekingState.value = false
                            val finalPos = sliderPositionState.value.toLong()
                            pendingPreviewState.value = finalPos
                            onSeek(finalPos)
                            if (playerLyricsSeekPreview) lyricHaptic.onSeekEnd()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val progress = progressRatio.coerceIn(0f, 1f)

            // 背景轨道：深色舞台用半透明白，浅色舞台用柔和背景色
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (deepBackdrop) Color.White.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            )

            // 进度高亮：纯白或主题色胶囊
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(barHeight)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (deepBackdrop) Color.White else primaryColor)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 时间与格式信息行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 当前时间
            Text(
                text = formatDuration(if (isSeeking) sliderPosition.toLong() else currentPosition),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = uiTintVariant,
                modifier = Modifier.weight(1f)
            )

            // 音频格式信息（居中平滑淡入淡出过渡，如 44.1kHz 16bit FLAC）
            Box(
                modifier = Modifier.weight(1.5f),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = audioInfo,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                    label = "audioInfoCrossfade"
                ) { targetInfo ->
                    if (targetInfo != null) {
                        Text(
                            text = targetInfo,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = uiTintVariant.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 总时长
            Text(
                text = formatDuration(totalDuration),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = uiTintVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ==================== 简约风圆润实心控制按钮 ====================

/**
 * 上一首：双圆润实心三角（顶点朝左）
 */
@Composable
fun MinimalistPreviousButton(
    onClick: () -> Unit,
    tint: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "prevScale"
    )

    Box(
        modifier = modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(width = 30.dp, height = 22.dp)) {
            val w = size.width
            val h = size.height
            val gap = 3.dp.toPx()
            val tw = (w - gap) / 2f
            val r = 2.8.dp.toPx()

            // 左边三角形（顶点朝左）
            val verticesLeft = listOf(
                Offset(tw, 0f),
                Offset(0f, h / 2f),
                Offset(tw, h)
            )
            drawPath(createRoundedPolygonPath(verticesLeft, r), color = tint)

            // 右边三角形（顶点朝左）
            val verticesRight = listOf(
                Offset(w, 0f),
                Offset(tw + gap, h / 2f),
                Offset(w, h)
            )
            drawPath(createRoundedPolygonPath(verticesRight, r), color = tint)
        }
    }
}

/**
 * 下一首：双圆润实心三角（顶点朝右）
 */
@Composable
fun MinimalistNextButton(
    onClick: () -> Unit,
    tint: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "nextScale"
    )

    Box(
        modifier = modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(width = 30.dp, height = 22.dp)) {
            val w = size.width
            val h = size.height
            val gap = 3.dp.toPx()
            val tw = (w - gap) / 2f
            val r = 2.8.dp.toPx()

            // 左边三角形（顶点朝右）
            val verticesLeft = listOf(
                Offset(0f, 0f),
                Offset(tw, h / 2f),
                Offset(0f, h)
            )
            drawPath(createRoundedPolygonPath(verticesLeft, r), color = tint)

            // 右边三角形（顶点朝右）
            val verticesRight = listOf(
                Offset(tw + gap, 0f),
                Offset(w, h / 2f),
                Offset(tw + gap, h)
            )
            drawPath(createRoundedPolygonPath(verticesRight, r), color = tint)
        }
    }
}

/**
 * 播放/暂停：大圆润实心三角 / 双圆润竖条
 */
@Composable
fun MinimalistPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    tint: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "playScale"
    )

    Box(
        modifier = modifier
            .size(68.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            label = "minPlayPause"
        ) { playing ->
            Canvas(modifier = Modifier.size(36.dp)) {
                val w = size.width
                val h = size.height
                if (playing) {
                    // 暂停：两条圆润饱满的胶囊竖杠
                    val barWidth = w * 0.28f
                    val gap = w * 0.26f
                    val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(0f, 0f),
                        size = Size(barWidth, h),
                        cornerRadius = cornerRadius
                    )
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(barWidth + gap, 0f),
                        size = Size(barWidth, h),
                        cornerRadius = cornerRadius
                    )
                } else {
                    // 播放：圆润实心大三角（顶点朝右）
                    val vertices = listOf(
                        Offset(w * 0.12f, 0f),
                        Offset(w * 0.96f, h / 2f),
                        Offset(w * 0.12f, h)
                    )
                    val roundedPath = createRoundedPolygonPath(vertices, 4.5.dp.toPx())
                    drawPath(roundedPath, color = tint)
                }
            }
        }
    }
}

// ==================== 简约风完整控制栏（对齐错开层级布局） ====================

@Composable
fun MinimalistFullControls(
    compactProgressSection: @Composable () -> Unit,
    isLyricsCompact: Boolean,
    isPlaying: Boolean,
    playMode: Int,
    onPlayModeChange: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onPlaylistClick: () -> Unit,
    deepBackdrop: Boolean,
    primaryColor: Color,
    uiTint: Color,
    uiTintVariant: Color,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
) {
    // 高度与下沉动画参数（与原版保持完全一致的高低错开层级）
    val ctrlHeight by animateDpAsState(
        targetValue = if (isLyricsCompact) 140.dp else 128.dp,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "minCtrlHeight"
    )
    val playDrop by animateDpAsState(
        targetValue = if (isLyricsCompact) 34.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "minPlayDrop"
    )
    val sideDrop by animateDpAsState(
        targetValue = if (isLyricsCompact) 34.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "minSideDrop"
    )

    val controlTint = if (deepBackdrop) Color.White else uiTint

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ctrlHeight),
        contentAlignment = Alignment.Center
    ) {
        // 歌词卡精简模式：进度条从上方滑入
        AnimatedVisibility(
            visible = isLyricsCompact,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -it },
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            compactProgressSection()
        }

        // 播放模式 - 左下角对齐（与主播放键高低错开）
        AnimatedVisibility(
            visible = !isLyricsCompact,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            IconButton(
                onClick = {
                    val newMode = (playMode + 1) % 3
                    onPlayModeChange(newMode)
                }
            ) {
                Icon(
                    imageVector = when (playMode) {
                        1 -> Icons.Default.Shuffle
                        2 -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = stringResource(R.string.action_play_mode),
                    modifier = Modifier.size(24.dp),
                    tint = if (playMode != 0) {
                        if (deepBackdrop) Color.White else primaryColor
                    } else {
                        uiTintVariant
                    }
                )
            }
        }

        // 上一首 - 左中居中（圆润双三角）
        MinimalistPreviousButton(
            onClick = onPrevious,
            tint = controlTint,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(0, sideDrop.roundToPx()) }
                .padding(start = 60.dp)
        )

        // 播放 / 暂停 - 正中（圆润大实心按键）
        MinimalistPlayPauseButton(
            isPlaying = isPlaying,
            onClick = onPlayPause,
            tint = controlTint,
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(0, playDrop.roundToPx()) }
        )

        // 下一首 - 右中居中（圆润双三角）
        MinimalistNextButton(
            onClick = onNext,
            tint = controlTint,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset { IntOffset(0, sideDrop.roundToPx()) }
                .padding(end = 60.dp)
        )

        // 播放列表菜单 - 右下角对齐（与主播放键高低错开）
        AnimatedVisibility(
            visible = !isLyricsCompact,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            IconButton(
                onClick = onPlaylistClick
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "播放列表",
                    modifier = Modifier.size(24.dp),
                    tint = uiTintVariant
                )
            }
        }
    }
}
