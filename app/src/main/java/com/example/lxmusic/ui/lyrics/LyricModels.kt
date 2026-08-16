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
 * File: moe.ouom.neriplayer.ui.component.lyrics/SyncedLyricsView (models)
 */

/** 单词/字的时间戳 */
data class WordTiming(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val charCount: Int = 0
)

/** 一行歌词 */
data class LyricEntry(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val words: List<WordTiming>? = null,
    val translation: String? = null
)
