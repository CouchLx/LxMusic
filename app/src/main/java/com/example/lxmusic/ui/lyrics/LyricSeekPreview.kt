package com.example.lxmusic.ui.lyrics

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.ui.screen/LyricSeekPreview (adapted for LxMusic)
 */

import kotlin.math.abs

internal const val LyricSeekPreviewSettleToleranceMs = 280L

/**
 * 解析歌词预览应显示的时间点：
 * 拖动中显示拖动位置，拖动结束但未同步到位时显示待落位位置，否则显示实际播放位置。
 */
internal fun resolveLyricPreviewTimeMs(
    isDraggingSlider: Boolean,
    sliderPreviewPositionMs: Long,
    pendingSeekPreviewPositionMs: Long?,
    playbackPositionMs: Long
): Long {
    return when {
        isDraggingSlider -> sliderPreviewPositionMs
        pendingSeekPreviewPositionMs != null -> pendingSeekPreviewPositionMs
        else -> playbackPositionMs
    }.coerceAtLeast(0L)
}

/** 播放位置已追平待预览位置（误差在容差内），可释放预览状态 */
internal fun shouldReleaseLyricSeekPreview(
    playbackPositionMs: Long,
    pendingSeekPreviewPositionMs: Long,
    toleranceMs: Long = LyricSeekPreviewSettleToleranceMs
): Boolean {
    return abs(playbackPositionMs - pendingSeekPreviewPositionMs) <= toleranceMs
}
