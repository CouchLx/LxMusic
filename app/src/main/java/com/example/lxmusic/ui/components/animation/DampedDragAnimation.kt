package com.example.lxmusic.ui.components.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.util.fastCoerceIn
import com.example.lxmusic.ui.components.motion.BottomBarMotionSpec
import com.example.lxmusic.ui.components.motion.resolveBottomBarMotionSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal fun resolveDampedDragVelocityItemsPerSecond(
    velocityPxPerSecond: Float,
    itemWidthPx: Float
): Float {
    if (itemWidthPx <= 0f) return 0f
    return velocityPxPerSecond / itemWidthPx
}

internal fun resolveDampedDragReleaseTargetIndex(
    currentValue: Float,
    velocityPxPerSecond: Float,
    itemWidthPx: Float,
    itemCount: Int,
    motionSpec: BottomBarMotionSpec
): Int {
    if (itemCount <= 0) return 0
    val velocityItems = resolveDampedDragVelocityItemsPerSecond(velocityPxPerSecond, itemWidthPx)
    val projectedValue = currentValue + velocityItems * motionSpec.drag.flingProjectionTimeSeconds
    var nextIndex = projectedValue.roundToInt()
    val baseIndex = currentValue.roundToInt()
    val maxReleaseStep = motionSpec.drag.maxReleaseStepCount.coerceAtLeast(1)
    if (abs(nextIndex - baseIndex) > maxReleaseStep) {
        nextIndex = baseIndex + (nextIndex - baseIndex).sign * maxReleaseStep
    }
    return nextIndex.coerceIn(0, itemCount - 1)
}

internal class DampedDragAnimationState(
    initialIndex: Int,
    private val itemCount: Int,
    private val scope: CoroutineScope,
    private val onIndexChanged: (Int) -> Unit,
    private val motionSpec: BottomBarMotionSpec,
    private val notifyIndexChangedOnReleaseStart: Boolean = false
) {
    private val valueAnimationSpec = spring(1f, 1000f, 0.001f)
    // 点击切换 tab 时专用的「滑行」弹簧：临界阻尼不回弹，
    // 让指示器像手指拖动那样平滑滑到目标 tab（约 220ms），
    // 全程保持较高速度以持续触发果冻拉伸，过渡自然。拖动松手落位仍用上面的 valueAnimationSpec，互不影响。
    // 觉得还慢就把 stiffness 调到 1000f（~170ms）；觉得太快就调回 500f（~280ms）。
    // 可通过 setClickStiffness() 在运行时由设置页滑块动态调整。
    var clickStiffness: Float = 700f
        private set
    var clickTravelAnimationSpec: SpringSpec<Float> = spring(1f, clickStiffness, 0.001f)
        private set

    /** 设置点击切换 tab 的动画速率（stiffness，越大越快）。
     *  推荐范围：260f（最慢，约 400ms）~ 1000f（最快，约 170ms），默认 700f（约 220ms）。*/
    fun setClickStiffness(value: Float) {
        val clamped = value.coerceIn(260f, 1000f)
        if (clamped == clickStiffness) return
        clickStiffness = clamped
        clickTravelAnimationSpec = spring(1f, clamped, 0.001f)
    }
    private val velocityAnimationSpec = spring(0.5f, 300f, 0.01f)
    private val pressProgressAnimationSpec = spring(1f, 300f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)
    private val offsetSnapAnimationSpec = spring(1f, 300f, 0.5f)

    private val valueAnimation = Animatable(initialIndex.toFloat(), 0.001f)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(1f, 0.001f)
    private val scaleYAnimation = Animatable(1f, 0.001f)
    private val offsetAnimation = Animatable(0f)
    private val mutatorMutex = MutatorMutex()
    private val deformationVelocityTracker = VelocityTracker()

    private var motionGeneration = 0
    private var valueJob: Job? = null
    private var velocityJob: Job? = null
    private var releaseJob: Job? = null
    private var offsetJob: Job? = null
    private var desiredValue = initialIndex.toFloat()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val velocity: Float get() = velocityAnimation.value
    val deformationVelocityItemsPerSecond: Float get() = velocityAnimation.value
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val scale: Float get() = maxOf(scaleX, scaleY)
    val dragOffset: Float get() = offsetAnimation.value
    val isRunning: Boolean get() = valueAnimation.isRunning

    var velocityPxPerSecond by mutableFloatStateOf(0f)
        private set
    var isDragging by mutableStateOf(false)
        private set
    var targetIndex = initialIndex
        private set
    var settledReleaseCount by mutableIntStateOf(0)
        private set
    var settledSelectionCount by mutableIntStateOf(0)
        private set

    private fun startNewMotion(): Int {
        motionGeneration += 1
        return motionGeneration
    }

    fun press() {
        deformationVelocityTracker.resetTracking()
        releaseJob?.cancel()
        releaseJob = scope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(KERNEL_SU_PRESSED_SCALE, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(KERNEL_SU_PRESSED_SCALE, scaleYAnimationSpec) }
        }
    }

    fun release(onSettled: (() -> Unit)? = null) {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            kotlinx.coroutines.yield()
            if (value != targetValue) {
                val threshold = ((itemCount - 1).toFloat() * 0.025f).coerceAtLeast(0.001f)
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            onSettled?.invoke()
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(1f, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(1f, scaleYAnimationSpec) }
        }
    }

    private fun updateDeformationVelocity(value: Float) {
        val valueRange = (itemCount - 1).toFloat().coerceAtLeast(1f)
        deformationVelocityTracker.addPosition(System.currentTimeMillis(), Offset(value, 0f))
        val targetVelocity = deformationVelocityTracker.calculateVelocity().x / valueRange
        velocityJob = scope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }

    fun snapTo(targetValue: Float) {
        val generation = startNewMotion()
        valueJob?.cancel()
        desiredValue = targetValue
        targetIndex = targetValue.roundToInt().coerceIn(0, itemCount - 1)
        scope.launch {
            if (generation != motionGeneration) return@launch
            valueAnimation.stop()
            valueAnimation.snapTo(targetValue)
            velocityAnimation.snapTo(0f)
        }
    }

    fun animateToValue(
        value: Float,
        animationSpec: AnimationSpec<Float> = valueAnimationSpec,
        onSettled: (() -> Unit)? = null
    ) {
        scope.launch {
            mutatorMutex.mutate {
                press()
                val nextTarget = value.fastCoerceIn(0f, (itemCount - 1).toFloat())
                targetIndex = nextTarget.roundToInt().coerceIn(0, itemCount - 1)
                valueJob?.cancel()
                // 让「弹到目标 tab」的整个过程持续采样形变速度，
                // 使「点击切换」与「拖动」触发同一套 velocity 驱动的果冻拉伸动画。
                // 弹簧越接近目标速度越小，拉伸随之自然收敛；
                // mutatorMutex 会让新的一次切换取消上一次，配合 layerBlock 里的 ±0.2 夹紧，
                // 多个 tab 快速相互切换也不会卡顿或形变崩坏。
                valueJob = launch {
                    valueAnimation.animateTo(nextTarget, animationSpec) {
                        updateDeformationVelocity(valueAnimation.value)
                    }
                    // 落位后把形变速度收敛到 0，确保指示器恢复原形
                    velocityJob?.cancel()
                    velocityJob = launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release(onSettled = onSettled)
            }
        }
    }

    fun onDrag(dragAmountPx: Float, itemWidthPx: Float, gestureVelocityPxPerSecond: Float = 0f) {
        if (itemWidthPx <= 0f || itemCount <= 0) return
        if (!isDragging) {
            isDragging = true
            startNewMotion()
            valueJob?.cancel()
            offsetJob?.cancel()
            desiredValue = valueAnimation.value
            velocityPxPerSecond = 0f
            velocityJob?.cancel()
            velocityJob = scope.launch { velocityAnimation.snapTo(0f) }
            press()
        }
        velocityPxPerSecond = gestureVelocityPxPerSecond
        val isOverscrolling = desiredValue < 0f || desiredValue > (itemCount - 1).toFloat()
        val resistance = if (isOverscrolling) motionSpec.drag.overscrollResistance else motionSpec.drag.baseResistance
        desiredValue = (desiredValue + (dragAmountPx / itemWidthPx) * resistance)
            .fastCoerceIn(-motionSpec.drag.overscrollLimitItems, (itemCount - 1).toFloat() + motionSpec.drag.overscrollLimitItems)
        val clampedValue = desiredValue.fastCoerceIn(0f, (itemCount - 1).toFloat())
        valueJob?.cancel()
        valueJob = scope.launch {
            valueAnimation.snapTo(clampedValue)
            updateDeformationVelocity(clampedValue)
        }
        offsetJob?.cancel()
        offsetJob = scope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmountPx) }
    }

    fun setPressed(pressed: Boolean) {
        if (pressed) press()
        else if (!isDragging) release()
    }

    fun onDragEnd(velocityX: Float, itemWidthPx: Float, settleIndex: Int? = null, notifyIndexChanged: Boolean = true) {
        if (itemWidthPx <= 0f || itemCount <= 0) return
        isDragging = false
        val generation = motionGeneration
        velocityPxPerSecond = velocityX
        val releaseTargetIndex = settleIndex?.coerceIn(0, itemCount - 1)
            ?: resolveDampedDragReleaseTargetIndex(desiredValue, velocityX, itemWidthPx, itemCount, motionSpec)
        targetIndex = releaseTargetIndex
        desiredValue = releaseTargetIndex.toFloat()
        if (notifyIndexChanged && notifyIndexChangedOnReleaseStart) onIndexChanged(releaseTargetIndex)
        animateToValue(releaseTargetIndex.toFloat()) {
            if (generation == motionGeneration) {
                velocityPxPerSecond = 0f
                settledReleaseCount += 1
                if (notifyIndexChanged && !notifyIndexChangedOnReleaseStart) onIndexChanged(releaseTargetIndex)
            }
        }
        offsetJob?.cancel()
        offsetJob = scope.launch { offsetAnimation.animateTo(0f, offsetSnapAnimationSpec) }
    }

    fun updateIndex(index: Int) {
        if (isDragging || itemCount <= 0) return
        val safeIndex = index.coerceIn(0, itemCount - 1)
        if (safeIndex == targetIndex && (isRunning || abs(value - safeIndex.toFloat()) < 0.005f || abs(targetValue - safeIndex.toFloat()) < 0.005f)) return
        startNewMotion()
        targetIndex = safeIndex
        desiredValue = safeIndex.toFloat()
        velocityPxPerSecond = 0f
        // 点击切换用「滑行」弹簧，模拟手指从当前 tab 拖到目标 tab 的平滑过渡
        animateToValue(safeIndex.toFloat(), clickTravelAnimationSpec) { settledSelectionCount += 1 }
    }
}

internal const val KERNEL_SU_PRESSED_SCALE = 78f / 56f

@Composable
internal fun rememberDampedDragAnimationState(
    initialIndex: Int,
    itemCount: Int,
    onIndexChanged: (Int) -> Unit,
    motionSpec: BottomBarMotionSpec = resolveBottomBarMotionSpec(),
    notifyIndexChangedOnReleaseStart: Boolean = false
): DampedDragAnimationState {
    val scope = rememberCoroutineScope()
    val currentOnIndexChanged by rememberUpdatedState(onIndexChanged)
    return remember(itemCount, motionSpec, notifyIndexChangedOnReleaseStart) {
        DampedDragAnimationState(initialIndex, itemCount, scope, { currentOnIndexChanged(it) }, motionSpec, notifyIndexChangedOnReleaseStart)
    }
}

/**
 * 水平拖拽手势 Modifier（带点击检测）。
 *
 * 拖拽时完全跟手，松手后弹簧动画弹到目标tab。
 * 点击时通过 onTap(index) 回调通知调用方。
 */
internal fun Modifier.horizontalDragGesture(
    dragState: DampedDragAnimationState,
    itemWidthPx: Float,
    consumePointerChanges: Boolean = true,
    settleIndex: Int? = null,
    notifyIndexChanged: Boolean = true,
    onTap: ((Int) -> Unit)? = null
): Modifier = this.pointerInput(dragState, itemWidthPx, consumePointerChanges, settleIndex, notifyIndexChanged, onTap) {
    awaitEachGesture {
        val velocityTracker = VelocityTracker()
        val down = awaitFirstDown(requireUnconsumed = false)
        velocityTracker.resetTracking()
        velocityTracker.addPosition(down.uptimeMillis, down.position)
        val downX = down.position.x

        val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            if (consumePointerChanges) change.consume()
            dragState.onDrag(over, itemWidthPx)
        }

        if (dragStart != null) {
            // 拖拽模式
            velocityTracker.addPosition(dragStart.uptimeMillis, dragStart.position)
            var isCanceled = false
            try {
                horizontalDrag(dragStart.id) { change ->
                    if (consumePointerChanges) change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val dragAmount = change.position.x - change.previousPosition.x
                    val velocity = velocityTracker.calculateVelocity()
                    dragState.onDrag(dragAmount, itemWidthPx, velocity.x)
                }
            } catch (_: Exception) { isCanceled = true }
            val velocity = velocityTracker.calculateVelocity()
            dragState.onDragEnd(
                velocityX = if (isCanceled) 0f else velocity.x,
                itemWidthPx = itemWidthPx,
                settleIndex = settleIndex,
                notifyIndexChanged = notifyIndexChanged
            )
        } else {
            // 点击模式（没有超过触摸阈值 → 纯点击）
            // awaitHorizontalTouchSlopOrCancellation 返回 null = 手势取消（手指抬起/没有水平移动）
            if (onTap != null) {
                val index = (downX / itemWidthPx).toInt().coerceAtLeast(0)
                onTap(index)
            }
        }
    }
}
