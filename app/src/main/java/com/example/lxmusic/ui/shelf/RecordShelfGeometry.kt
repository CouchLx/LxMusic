package com.example.lxmusic.ui.shelf

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * CD 唱片架的几何计算。
 *
 * 移植自 CurrentMusicX 的 RecordShelfGeometry.kt（GPL-3.0-only），数值逐项保留：
 * 以参考项目 724x1574 屏幕为基准，一张唱片盒宽 .435W、高 .45W、边缘抬升 .132W，
 * 抽出时前脸平移 (.367W, .095W)，不旋转。
 */

/** 有界的窗口：几千首歌里只渲染附近的一小段，每首一个唱片盒。 */
internal fun shelfWindow(position: Float, count: Int, nearSlots: Int = 10): IntRange {
    if (count <= 0) return IntRange.EMPTY
    val center = position.roundToInt()
    val first = center - minOf(nearSlots, (count - 1) / 2)
    return first until first + minOf(count, 29)
}

/** 竖屏时把唱片盒按高度限制住；宽屏时把架子往两边铺开。 */
internal class ShelfGeometry(
    val viewportWidth: Float,
    val top: Float,
    val height: Float,
    val viewportHeight: Float = top + height
) {
    val caseScale = minOf(viewportWidth, height * 1.12f)
    val wide = viewportWidth > caseScale
    private val originX = (viewportWidth - caseScale) * .5f
    private val spacing = if (wide) maxOf(caseScale * .069f, viewportWidth / 24f) else caseScale * .069f
    private val verticalRoom = (height - caseScale * .677f).coerceAtLeast(0f)
    private val rackX = originX + caseScale * .120f
    private val rackY = top + verticalRoom * .5f
    private val caseWidth = caseScale * .435f
    // 架子沿屏幕左下到右上的对角线排布，只有选中项走中央舞台。
    private val edgeReveal = caseScale * .1f
    private val edgeSkew = spacing * (.132f / .435f)
    private val rightSlope = (rackY + caseScale * .582f - edgeReveal - edgeSkew) /
        (viewportWidth - caseWidth - rackX + spacing).coerceAtLeast(1f)
    private val leftSlope = (viewportHeight - edgeReveal - rackY - edgeSkew) /
        (rackX + spacing).coerceAtLeast(1f)
    private val rise = if (wide) spacing * minOf(
        .5773503f, .92f * viewportHeight / viewportWidth,
        rightSlope, leftSlope
    ).coerceAtLeast(0f) else caseScale * .046f

    fun window(position: Float, count: Int) = shelfWindow(position, count, if (wide) 14 else 10)

    /** 全局坐标同时供绘制、命中测试和搜索结果动画使用。 */
    fun quad(distance: Float, extraction: Float, out: FloatArray) {
        if (!wide) {
            shelfQuad(distance, caseScale, top, height, extraction, out)
            return
        }
        val x = rackX + caseScale * extraction * .367f + distance * spacing
        val y = rackY - distance * rise + caseScale * extraction * .095f
        val w = caseWidth
        val h = caseScale * .45f
        val skew = caseScale * .132f
        out[0] = x; out[1] = y
        out[2] = x + w; out[3] = y + skew
        out[4] = x + w; out[5] = y + skew + h
        out[6] = x; out[7] = y + h
    }

    fun entryOffset(seconds: Float, distance: Float, restingX: Float) =
        shelfEntryOffsetX(seconds, distance, viewportWidth, restingX)

    fun exitOffset(seconds: Float, distance: Float, restingX: Float) =
        shelfResultsExitOffsetX(seconds, distance, viewportWidth, restingX, 0f)

    fun entryRise(offset: Float) = shelfEntryOffsetY(offset, viewportWidth)
}

internal fun shelfQuad(
    distance: Float, width: Float, top: Float, height: Float,
    extraction: Float, out: FloatArray
) {
    val x = width * (.120f + distance * .069f + extraction * .367f)
    val y = top + height * .404f + width * (-distance * .046f + extraction * .095f)
    val w = width * .435f
    val h = width * .45f
    val skew = width * .132f
    out[0] = x; out[1] = y
    out[2] = x + w; out[3] = y + skew
    out[4] = x + w; out[5] = y + skew + h
    out[6] = x; out[7] = y + h
}

/** 指数积分让回落与刷新率无关。 */
internal fun shelfApproach(value: Float, target: Float, seconds: Float, speed: Float = 12f): Float {
    val result = target + (value - target) * exp(-speed * seconds.coerceIn(0f, .05f))
    return if (abs(result - target) < .0005f) target else result
}

/** 入场：每个唱片盒都从右边缘外开始，后排先落位。 */
internal fun shelfEntryOffsetX(seconds: Float, distance: Float, width: Float, restingX: Float): Float {
    val front = (-distance).coerceIn(0f, 10f)
    val start = .412f + front * .0111f
    val duration = if (distance >= 0f) (.632f - distance * .030f).coerceAtLeast(.22f)
    else .632f + front * .0123f
    val progress = ((seconds - start) / duration).coerceIn(0f, 1f)
    return (width * 1.010f - restingX).coerceAtLeast(0f) * (1f - progress).pow(2.7f + front * .0158f)
}

internal fun shelfEntryOffsetY(offsetX: Float, width: Float): Float =
    1.1f * offsetX * offsetX / width - .48f * offsetX

/** 搜索结果沿入场路径离场。 */
internal fun shelfResultsExitOffsetX(
    seconds: Float, distance: Float, width: Float,
    restingX: Float, initialOffset: Float
): Float {
    val delay = .08f + (-distance).coerceIn(0f, 10f) * .004f
    val t = ((seconds - delay) / .26f).coerceIn(0f, 1f)
    val eased = t * t * (3f - 2f * t)
    return initialOffset + ((width * 1.010f - restingX).coerceAtLeast(0f) - initialOffset) * eased
}
