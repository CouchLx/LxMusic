package com.example.lxmusic.ui.lyrics

import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.parser.ILyricsParser

/**
 * QQ 音乐 QRC 逐字/逐音节歌词解析器（移植自 MeiloX，GPLv3）
 * 支持 QRC 原生音节时值提取、对唱角色识别、伴奏背景音解析以及与 LRC 翻译合并。
 */
object QRCParser : ILyricsParser {

    private val QRC_LINE_REGEX = Regex("""^\[(\d+),(\d+)\](.*)$""")
    private val QRC_SYLLABLE_REGEX = Regex("""([^\(\)]*)\((\d+),(\d+)\)""")
    private val BG_LINE_REGEX = Regex("""^\[bg:(.*)\](.*)$""")
    private val translationLineRegex = """\[(\d{2}):(\d{2})[.:](\d{2,3})\].*""".toRegex()

    /**
     * 解析 QRC 主歌词并合并可选的 LRC 翻译
     */
    fun parse(qrcLyrics: String, translationLrc: String? = null): SyncedLyrics {
        val karaokeLines = parseInternal(qrcLyrics.lineSequence())
        val mergedLines = TranslationHelper.merge(karaokeLines, translationLrc)
        return SyncedLyrics(lines = mergedLines)
    }

    override fun canParse(content: String): Boolean {
        val lineTimeRegex = """^\[\d+,\d+\]""".toRegex()
        val wordTimeRegex = """[^\(\)]+\(\d+,\d+\)""".toRegex()

        return content.lineSequence()
            .map { it.trim() }
            .any { line ->
                lineTimeRegex.containsMatchIn(line) && wordTimeRegex.containsMatchIn(line)
            }
    }

    override fun parse(lines: List<String>): SyncedLyrics {
        val mainLyricsLines = lines.filter { line ->
            val trimmed = line.trim()
            QRC_LINE_REGEX.matches(trimmed) || trimmed.startsWith("[bg:")
        }
        val translationLines = lines.filter { translationLineRegex.matches(it.trim()) }

        return parse(
            qrcLyrics = mainLyricsLines.joinToString("\n"),
            translationLrc = translationLines.joinToString("\n").ifBlank { null }
        )
    }

    override fun parse(content: String): SyncedLyrics {
        return parse(content.lines())
    }

    /**
     * 将 QRC 转换为通用的网易云 YRC 格式字符串
     * 格式：[lineStart,lineDuration](wordStart,wordDuration,0)word...
     * 转换后可被任意已有逐字播放器和缓存直接透明读取
     */
    fun qrcToYrc(qrcText: String): String {
        val sb = java.lang.StringBuilder()
        for (raw in qrcText.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("[ti:") || line.startsWith("[ar:") ||
                line.startsWith("[al:") || line.startsWith("[by:") || line.startsWith("[offset:")
            ) {
                continue
            }
            val match = QRC_LINE_REGEX.find(line) ?: continue
            val lineStart = match.groupValues[1].toIntOrNull() ?: continue
            val lineDur = match.groupValues[2].toIntOrNull() ?: continue
            val contentPart = match.groupValues[3]

            val syllables = parseSyllablesAndMergeColons(contentPart, lineStart)
            if (syllables.isEmpty()) continue

            sb.append("[$lineStart,$lineDur]")
            for (syl in syllables) {
                val dur = syl.end - syl.start
                sb.append("(${syl.start},$dur,0)${syl.content}")
            }
            sb.append("\n")
        }
        return sb.toString().trim()
    }

    /**
     * 将 QRC 解析为播放器 UI 通用的 List<LyricEntry>
     */
    fun parseToLyricEntries(qrcLyrics: String, translationLrc: String? = null): List<LyricEntry> {
        val synced = parse(qrcLyrics, translationLrc)
        return synced.lines.mapNotNull { line ->
            when (line) {
                is KaraokeLine -> {
                    LyricEntry(
                        text = line.syllables.joinToString("") { it.content },
                        startTimeMs = line.start.toLong(),
                        endTimeMs = line.end.toLong(),
                        translation = line.translation,
                        words = line.syllables.map { syl ->
                            WordTiming(
                                startTimeMs = syl.start.toLong(),
                                endTimeMs = syl.end.toLong(),
                                charCount = syl.content.length
                            )
                        }
                    )
                }
                else -> null
            }
        }
    }

    private fun parseInternal(rawLinesSequence: Sequence<String>): List<KaraokeLine> {
        val resultLines = mutableListOf<KaraokeLine>()

        var currentRoleState = KaraokeAlignment.Start
        var lastLineStartTime = -1

        for (raw in rawLinesSequence) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("[bg:")) {
                parseBackgroundLine(line)?.let { bgLine ->
                    if (resultLines.isNotEmpty()) {
                        val last = resultLines.last()
                        if (last is KaraokeLine.MainKaraokeLine) {
                            resultLines[resultLines.size - 1] = last.copy(
                                accompanimentLines = (last.accompanimentLines ?: emptyList()) + bgLine
                            )
                        } else {
                            resultLines.add(bgLine)
                        }
                    } else {
                        resultLines.add(bgLine)
                    }
                }
                continue
            }

            val match = QRC_LINE_REGEX.find(line) ?: continue

            var lineStart = match.groupValues[1].toIntOrNull() ?: continue
            if (lastLineStartTime != -1 && lineStart <= lastLineStartTime) {
                lineStart = lastLineStartTime + 3
            }
            lastLineStartTime = lineStart

            val contentPart = match.groupValues[3]
            val rawSyllables = parseSyllablesAndMergeColons(contentPart, lineStart)

            val (alignment, finalSyllables, nextState) = determineRole(
                rawSyllables,
                currentRoleState
            )
            currentRoleState = nextState

            if (finalSyllables.isNotEmpty()) {
                resultLines.add(
                    KaraokeLine.MainKaraokeLine(
                        syllables = finalSyllables,
                        translation = null,
                        alignment = alignment,
                        start = finalSyllables.first().start,
                        end = finalSyllables.last().end
                    )
                )
            }
        }

        return resultLines
    }

    private fun parseBackgroundLine(line: String): KaraokeLine.AccompanimentKaraokeLine? {
        val m = BG_LINE_REGEX.find(line) ?: return null
        val content = m.groupValues[1] + m.groupValues[2]
        val syllables = parseSyllablesAndMergeColons(content, 0)
        if (syllables.isEmpty()) return null

        return KaraokeLine.AccompanimentKaraokeLine(
            syllables = syllables,
            translation = null,
            alignment = KaraokeAlignment.Unspecified,
            start = syllables.first().start,
            end = syllables.last().end
        )
    }

    private fun parseSyllablesAndMergeColons(
        content: String,
        baseStartTime: Int
    ): List<KaraokeSyllable> {
        data class TempToken(val offset: Int, val duration: Int, val text: String)

        val tokens = mutableListOf<TempToken>()

        for (m in QRC_SYLLABLE_REGEX.findAll(content)) {
            val text = m.groupValues[1]
            val offset = m.groupValues[2].toIntOrNull() ?: 0
            val duration = m.groupValues[3].toIntOrNull() ?: 0

            if (text.isNotEmpty()) {
                tokens.add(TempToken(offset, duration, text))
            }
        }

        if (tokens.isEmpty()) return emptyList()

        val useAbsoluteTime = tokens.isNotEmpty() && tokens[0].offset >= baseStartTime

        val mergedSyllables = mutableListOf<KaraokeSyllable>()
        var i = 0
        while (i < tokens.size) {
            val current = tokens[i]
            val next = tokens.getOrNull(i + 1)

            val startTime = if (useAbsoluteTime) current.offset else baseStartTime + current.offset

            if (next != null && (next.text == "：" || next.text == ":")) {
                val nextStartTime = if (useAbsoluteTime) next.offset else baseStartTime + next.offset
                val e = nextStartTime + next.duration
                mergedSyllables.add(KaraokeSyllable(current.text + next.text, startTime, e))
                i += 2
            } else {
                val e = startTime + current.duration
                mergedSyllables.add(KaraokeSyllable(current.text, startTime, e))
                i++
            }
        }
        return mergedSyllables
    }

    private fun determineRole(
        syllables: List<KaraokeSyllable>,
        currentState: KaraokeAlignment
    ): Triple<KaraokeAlignment, List<KaraokeSyllable>, KaraokeAlignment> {
        if (syllables.isEmpty()) return Triple(KaraokeAlignment.Unspecified, syllables, currentState)

        val rawText = syllables.joinToString("") { it.content }
        val hasMarker = rawText.startsWith("：") || rawText.startsWith(":") ||
                rawText.endsWith("：") || rawText.endsWith(":")

        if (hasMarker) {
            val newState =
                if (currentState == KaraokeAlignment.Start) KaraokeAlignment.End else KaraokeAlignment.Start
            return Triple(newState, syllables, newState)
        }

        return Triple(currentState, syllables, currentState)
    }
}
