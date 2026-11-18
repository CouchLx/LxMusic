package com.example.lxmusic.ui.roaming

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * 播放器「私人漫游」页：**普通翻页**。
 *
 * 手势逻辑沿用 CurrentMusicX 的 PlayerSidePage.kt（GPL-3.0-only）：
 * **跟手拖动、松手吸附由这里统一处理**，pager 只负责自己的横向翻页（嵌套滚动：pager 先消费，
 * 滚不动的剩余量才翻到漫游页），所以不会两边抢手势。
 * 视觉上不再缩小/压暗播放器：两页整屏并排，只做水平位移，背景层完全静止。
 */
/** 一页的行程 = 一整屏宽度（普通翻页，不缩短行程）。 */
internal const val PLAYER_SIDE_TRAVEL = 1f

class PlayerSidePageState(private val scope: CoroutineScope) {
    private val animation = Animatable(0f)
    private var dragged by mutableStateOf<Float?>(null)
    private var settling: Job? = null
    private var targetOpen = false

    var direction by mutableFloatStateOf(1f)
        private set
    var pagerDragging by mutableStateOf(false)
        private set

    val progress get() = dragged ?: animation.value
    val active by derivedStateOf { dragged != null || progress > .0001f }

    fun begin(openingDirection: Float = direction, fromPager: Boolean = false) {
        val current = progress
        if (!active) direction = openingDirection
        pagerDragging = fromPager
        settling?.cancel()
        dragged = current
        scope.launch(start = CoroutineStart.UNDISPATCHED) { animation.stop() }
    }

    fun drag(delta: Float, travel: Float) {
        dragged?.let { dragged = (it + delta * direction / travel.coerceAtLeast(1f)).coerceIn(0f, 1f) }
    }

    fun end(velocity: Float, flingThreshold: Float, cancelled: Boolean = false) {
        if (dragged == null) return
        pagerDragging = false
        settle(
            if (cancelled) targetOpen
            else if (abs(velocity) > flingThreshold) velocity * direction > 0
            // 对齐 Compose pager 的手感：位移约 56dp（≈14% 屏宽）或轻甩就翻页
            else progress >= .14f
        )
    }

    fun close() = settle(false)

    fun returnToPager() {
        settling?.cancel()
        targetOpen = false
        pagerDragging = false
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animation.snapTo(0f)
            dragged = null
        }
    }

    private fun settle(open: Boolean) {
        val current = progress
        settling?.cancel()
        pagerDragging = false
        targetOpen = open
        settling = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animation.snapTo(current)
            dragged = null
            animation.animateTo(
                if (open) 1f else 0f,
                tween(
                    (320 * abs((if (open) 1f else 0f) - current)).toInt().coerceAtLeast(120),
                    easing = CubicBezierEasing(.2f, 0f, .2f, 1f)
                )
            )
        }
    }
}

/**
 * 普通翻页（不缩放、不变形）：
 * 两页都按整屏测量并排摆放，拖动只改绘制层的 translationX ——
 * 播放器页整体右移一格，漫游页从左边一格外同步滑进来，背景不参与任何变换。
 */
@Composable
fun PlayerSidePageLayout(
    state: PlayerSidePageState,
    enabled: Boolean,
    openingDirection: () -> Float,
    excludedArea: () -> LayoutCoordinates?,
    side: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val gesturesEnabled by androidx.compose.runtime.rememberUpdatedState(enabled)
    val allowedDirection by androidx.compose.runtime.rememberUpdatedState(openingDirection)
    val excluded by androidx.compose.runtime.rememberUpdatedState(excludedArea)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // 这次手势是否从「排除区」（进度条）起手：嵌套滚动这条路径也不能接管，
    // 否则把进度条拖到头继续拖，剩余量会被当成「右滑开漫游」。
    val gestureFromExcluded = remember { mutableStateOf(false) }
    // 甩手阈值：对齐 pager（标准 pager 125dp/s 就翻），这里留一点防误触
    val fling = with(androidx.compose.ui.platform.LocalDensity.current) { 250.dp.toPx() }
    val flingThreshold by androidx.compose.runtime.rememberUpdatedState(fling)

    val edgeScroll = remember(state) {
        object : NestedScrollConnection {
            // 先让 pager 滚，滚不动的剩余量接着驱动侧页
            private fun drag(delta: Float): Offset {
                val travel = (coordinates?.size?.width ?: 1) * PLAYER_SIDE_TRAVEL
                val before = state.progress
                state.drag(delta, travel)
                val consumed = (state.progress - before) * travel * state.direction
                if (state.progress == 0f) state.returnToPager()
                return Offset(consumed, 0f)
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && state.pagerDragging && !gestureFromExcluded.value)
                    drag(available.x) else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || !gesturesEnabled ||
                    gestureFromExcluded.value ||
                    state.active && !state.pagerDragging
                ) return Offset.Zero
                val direction = allowedDirection()
                if (available.x * direction <= 0f) return Offset.Zero
                if (!state.pagerDragging) state.begin(direction, fromPager = true)
                return drag(available.x)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!state.pagerDragging) return Velocity.Zero
                state.end(available.x, flingThreshold)
                return Velocity(available.x, 0f)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (state.pagerDragging) state.end(available.x, flingThreshold)
                return Velocity.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates = it }
            .nestedScroll(edgeScroll)
            .pointerInput(state) {
                val velocity = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val gestureDirection = allowedDirection()
                    if (!gesturesEnabled || (!state.active && gestureDirection == 0f)) return@awaitEachGesture
                    val initiallyOpen = state.active
                    val root = coordinates ?: return@awaitEachGesture
                    // 记录起手点是否落在排除区（进度条等）：命中的话整个手势都不接管
                    val downInExcluded = excluded()?.takeIf { it.isAttached }?.let { area ->
                        val point = area.localPositionOf(root, down.position)
                        point.x in 0f..area.size.width.toFloat() &&
                            point.y in 0f..area.size.height.toFloat()
                    } == true
                    gestureFromExcluded.value = downInExcluded
                    if (!initiallyOpen && downInExcluded) return@awaitEachGesture
                    velocity.resetTracking()
                    velocity.addPosition(down.uptimeMillis, down.position)
                    var moved = Offset.Zero
                    var started = false
                    try {
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial)
                                .changes.firstOrNull { it.id == down.id } ?: break
                            velocity.addPosition(change.uptimeMillis, change.position)
                            if (change.isConsumed) break
                            if (!change.pressed) {
                                if (started) {
                                    state.end(velocity.calculateVelocity().x, flingThreshold)
                                    started = false
                                    change.consume()
                                }
                                break
                            }
                            val delta = change.positionChange()
                            if (!started) {
                                moved += delta
                                if (abs(moved.y) > viewConfiguration.touchSlop && abs(moved.y) >= abs(moved.x)) break
                                if (abs(moved.x) <= viewConfiguration.touchSlop) continue
                                // 只有「往外滑」才开侧页，往内滑归 pager
                                if (!initiallyOpen && (moved.x * gestureDirection <= 0f ||
                                        allowedDirection() != gestureDirection)
                                ) break
                                state.begin(gestureDirection)
                                started = true
                                state.drag(
                                    moved.x - sign(moved.x) * viewConfiguration.touchSlop,
                                    size.width * PLAYER_SIDE_TRAVEL
                                )
                            } else state.drag(delta.x, size.width * PLAYER_SIDE_TRAVEL)
                            change.consume()
                        }
                    } finally {
                        if (started) state.end(0f, flingThreshold, cancelled = true)
                    }
                }
            }
    ) {
        // 漫游页：从左边一格外同步滑进来（和普通 pager 的上一页一样，内容跟着手指走）。
        // **常驻组合**：这一页有 40+ 个场景图标，如果等 active 才挂进组合，
        // 手指开始拖的那一帧要付一次很重的首次组合，观感就是「拖完卡一下才展开」。
        // 关闭时整页被平移到屏幕左外，绘制被裁剪、也不参与命中测试。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = -state.direction * size.width * (1f - state.progress)
                }
        ) { side() }
        // 播放器页：整体右移一格。不缩放、不压暗、不投影，背景层完全不动。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = state.direction * size.width * state.progress
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (state.active) Modifier.clearAndSetSemantics {} else Modifier)
            ) { content() }
        }
    }
}
