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
 * File: moe.ouom.neriplayer.ui.component.lyrics/LyricSeekHaptics (adapted for LxMusic)
 */

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.lxmusic.ui.components.HapticFeedbackEffect
import com.example.lxmusic.ui.components.performHapticFeedback

private const val NO_LYRIC_LINE_INDEX = -1
private const val MIN_LYRIC_SEEK_HAPTIC_INTERVAL_MS = 55L

@Composable
fun rememberLyricSeekHapticFeedback(
    lyrics: List<LyricEntry>,
    lyricOffsetMs: Long = 0L
): LyricSeekHapticFeedback {
    val context = LocalContext.current.applicationContext
    return remember(context, lyrics, lyricOffsetMs) {
        LyricSeekHapticFeedback(context = context, lyrics = lyrics, lyricOffsetMs = lyricOffsetMs)
    }
}

class LyricSeekHapticFeedback internal constructor(
    private val context: Context,
    private val lyrics: List<LyricEntry>,
    private val lyricOffsetMs: Long
) {
    private var hasBaseline = false
    private var lastLineIndex = NO_LYRIC_LINE_INDEX
    private var lastFeedbackUptimeMs = 0L

    fun onSeekStart(positionMs: Long) {
        hasBaseline = true
        lastLineIndex = resolveLineIndex(positionMs)
        lastFeedbackUptimeMs = 0L
    }

    fun onSeekMove(positionMs: Long) {
        val lineIndex = resolveLineIndex(positionMs)
        if (!hasBaseline) {
            hasBaseline = true
            lastLineIndex = lineIndex
            return
        }
        if (lineIndex == lastLineIndex) return       // 只有跨行才触发
        lastLineIndex = lineIndex
        if (lineIndex == NO_LYRIC_LINE_INDEX) return // 歌词开始前不触发
        val now = SystemClock.uptimeMillis()
        if (now - lastFeedbackUptimeMs < MIN_LYRIC_SEEK_HAPTIC_INTERVAL_MS) return  // 防抖
        lastFeedbackUptimeMs = now
        context.performHapticFeedback(HapticFeedbackEffect.Tick)
    }

    fun onSeekEnd() {
        hasBaseline = false
        lastLineIndex = NO_LYRIC_LINE_INDEX
        lastFeedbackUptimeMs = 0L
    }

    private fun resolveLineIndex(positionMs: Long): Int {
        if (lyrics.isEmpty()) return NO_LYRIC_LINE_INDEX
        val lyricTimeMs = (positionMs + lyricOffsetMs).coerceAtLeast(0L)
        if (lyricTimeMs < lyrics.first().startTimeMs) return NO_LYRIC_LINE_INDEX
        val lineIndex = findCurrentLineIndex(lyrics, lyricTimeMs)
        return if (lineIndex in lyrics.indices) lineIndex else NO_LYRIC_LINE_INDEX
    }
}
