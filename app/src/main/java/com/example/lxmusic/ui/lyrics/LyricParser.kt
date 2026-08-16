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
 * File: moe.ouom.neriplayer.ui.component.lyrics/SyncedLyricsView (parsers)
 */

private val NeteaseYrcLineRegex = Regex("""\[\d{1,19},\s*\d{1,19}]\(\d{1,19},""")
private val LrcCreditLineRegex = Regex(
    """^(?:作词|作曲|编曲|填词|演唱|歌手|混音|母带|制作|监制|录音|和声|配唱|吉他(?:solo)?|贝斯|鼓|键盘|弦乐|vo(?:/mix)?|mix|tune|inst|guitar|bass|drums?|vocal|lyrics|music|arrangement|produced)\s*[:：]""",
    RegexOption.IGNORE_CASE
)
private val LegacyLrcTimestampRegex = Regex("""\[(\d{1,2}):(\d{2}):(\d{2,3})]""")

private data class LrcTimelineEntry(
    val startTimeMs: Long,
    val text: String
)

fun isNeteaseYrc(content: String): Boolean = content.contains(NeteaseYrcLineRegex)

fun parseNeteaseLyricsAuto(content: String): List<LyricEntry> {
    return when {
        isNeteaseYrc(content) -> runCatching { parseNeteaseYrc(content) }.getOrDefault(emptyList())
        else -> parseNeteaseLrc(content)
    }
}

/**
 * 解析网易云 yrc (逐字/逐词)
 * 示例: [12580,3470](12580,250,0)难(12830,300,0)以
 * 会把每段文字的长度写入 WordTiming.charCount, 用于多行逐字揭示
 */
fun parseNeteaseYrc(yrc: String): List<LyricEntry> {
    val out = mutableListOf<LyricEntry>()
    val headerRegex = Regex("""\[(\d{1,19}),\s*(\d{1,19})]""")
    val segRegex = Regex("""\((\d{1,19}),\s*(\d{1,19}),\s*[-\d]{1,20}\)([^()\n\r]*)""")

    yrc.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@forEach
        if (!line.startsWith("[")) return@forEach

        val header = headerRegex.find(line) ?: return@forEach
        val start = header.groupValues[1].toLongOrNull() ?: return@forEach
        val dur = header.groupValues[2].toLongOrNull() ?: return@forEach
        val end = start.saturatingAdd(dur)

        val segs = segRegex.findAll(line).toList()
        if (segs.isEmpty()) {
            val text = line.substringAfter("]").trim()
            out.add(LyricEntry(text = text, startTimeMs = start, endTimeMs = end, words = null))
        } else {
            val words = mutableListOf<WordTiming>()
            val sb = StringBuilder()
            for (m in segs) {
                val ws = m.groupValues[1].toLongOrNull() ?: continue
                val wd = m.groupValues[2].toLongOrNull() ?: continue
                val we = ws.saturatingAdd(wd)
                val t = m.groupValues[3]
                sb.append(t)
                words.add(WordTiming(ws, we, charCount = t.length))
            }
            out.add(
                LyricEntry(
                    text = sb.toString(),
                    startTimeMs = start,
                    endTimeMs = end,
                    words = words
                )
            )
        }
    }
    return out.sortedBy { it.startTimeMs }
}

/**
 * 解析 LRC (逐句)
 * 支持 [mm:ss.SSS] 或 [mm:ss]
 * 没有逐字信息时, 逐字揭示会按整句线性推进
 */
fun parseNeteaseLrc(lrc: String): List<LyricEntry> {
    val normalizedLrc = normalizeLegacyLrcTimestamps(lrc)
    val tag = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?]""")
    val timeline = mutableListOf<LrcTimelineEntry>()

    normalizedLrc.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@forEach
        if (line.startsWith("{") || line.startsWith("}")) return@forEach // 过滤 JSON 段

        val m = tag.find(line) ?: return@forEach
        val mm = m.groupValues[1].toInt()
        val ss = m.groupValues[2].toInt()
        val msStr = m.groupValues.getOrNull(3).orEmpty()
        val ms = when (msStr.length) {
            0 -> 0
            2 -> msStr.toInt() * 10
            else -> msStr.toInt()
        }
        val time = mm * 60_000L + ss * 1_000L + ms
        val text = line.substring(m.range.last + 1).trim()
        timeline.add(LrcTimelineEntry(startTimeMs = time, text = text))
    }

    timeline.sortBy { it.startTimeMs }
    val suffixContainsOnlyCredits = BooleanArray(timeline.size + 1)
    suffixContainsOnlyCredits[timeline.size] = true
    for (index in timeline.lastIndex downTo 0) {
        val text = timeline[index].text
        suffixContainsOnlyCredits[index] = text.isNotBlank() &&
            LrcCreditLineRegex.containsMatchIn(text) &&
            suffixContainsOnlyCredits[index + 1]
    }
    var seenNonBlankLine = false
    var terminalMarkerIndex: Int? = null
    for (index in timeline.indices) {
        val entry = timeline[index]
        if (entry.text.isBlank() && seenNonBlankLine && suffixContainsOnlyCredits[index + 1]) {
            terminalMarkerIndex = index
            break
        }
        if (entry.text.isNotBlank()) {
            seenNonBlankLine = true
        }
    }
    val effectiveTimeline = terminalMarkerIndex?.let { markerIndex ->
        timeline.take(markerIndex + 1)
    } ?: timeline
    val out = mutableListOf<LyricEntry>()
    var nextTimestampMs: Long? = null
    for (index in effectiveTimeline.lastIndex downTo 0) {
        val entry = effectiveTimeline[index]
        if (entry.text.isNotBlank()) {
            out.add(
                LyricEntry(
                    text = entry.text,
                    startTimeMs = entry.startTimeMs,
                    endTimeMs = nextTimestampMs ?: (entry.startTimeMs + 5_000L),
                    words = null
                )
            )
        }
        nextTimestampMs = entry.startTimeMs
    }
    out.reverse()
    return out
}

private fun normalizeLegacyLrcTimestamps(content: String): String {
    if (content.isEmpty()) {
        return content
    }
    return LegacyLrcTimestampRegex.replace(content) { match ->
        val minutes = match.groupValues[1].padStart(2, '0')
        val seconds = match.groupValues[2]
        val fraction = match.groupValues[3]
        "[$minutes:$seconds.$fraction]"
    }
}

private fun Long.saturatingAdd(other: Long): Long {
    return if (other > 0L && this > Long.MAX_VALUE - other) {
        Long.MAX_VALUE
    } else {
        this + other
    }
}
