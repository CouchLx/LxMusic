/*
 * 新歌词页面渲染器（移植自 NeriPlayer AdvancedLyricsView，GPLv3）：
 * 包装 accompanist-lyrics（Apache 2.0）的 ModernKaraokeLyricsView：
 * 逐音节卡拉OK、视口滚动动效、歌词居中定位、点击/长按跳转、翻译显示。
 * 适配 LxMusic：字号/粗细/对齐沿用播放器歌词设置；视口参数按歌词卡区域调小。
 */

package com.example.lxmusic.ui.lyrics

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.ModernKaraokeLyricsView
import kotlin.math.abs
import kotlin.math.max

private const val TranslationAlignmentToleranceMs = 450L
private const val FocusedLyricVisualCompensationRatio = 0.42f
private val FocusedLyricMaskSafePadding = 24.dp

/** 歌词列表是否含逐字（word）时间戳 */
private fun List<LyricEntry>.hasWordTimedEntries(): Boolean = any { !it.words.isNullOrEmpty() }

/** TTML 特征检测（简单版）：XML 声明或 <tt 根标签 */
private fun isTtmlLyrics(raw: String): Boolean {
    val trimmed = raw.trimStart()
    return trimmed.startsWith("<?xml") || trimmed.startsWith("<tt")
}

/**
 * 新歌词页面渲染器（NeriPlayer 高级歌词移植）：
 * 逐音节卡拉OK + 视口滚动动效 + 歌词居中 + 点击跳转 + 翻译（有翻译数据时）。
 */
@Composable
fun NeriAdvancedLyricsView(
    lyrics: List<LyricEntry>,
    currentTimeMs: Long,
    modifier: Modifier = Modifier,
    textColor: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign = TextAlign.Center,
    translationFontSize: TextUnit = fontSize,
    lyricOffsetMs: Long = 0L,
    rawLyrics: String? = null,
    translatedLyrics: List<LyricEntry>? = null,
    showLyricTranslation: Boolean = true,
    // 逐字歌词动效：true=当前行逐字点亮；false=整行高亮（其余样式/动效不变）
    karaokeEnabled: Boolean = true,
    lyricBlurEnabled: Boolean = true,
    lyricBlurAmount: Float = 2.5f,
    isPlaying: Boolean = false,
    animateViewportScroll: Boolean = false,
    playbackSpeed: Float = 1f,
    offset: Dp = 24.dp,
    keepAliveZone: Dp = 56.dp,
    playedLyricViewportFraction: Float = 0.30f,
    topFadeLength: Dp = 48.dp,
    bottomFadeLength: Dp = 72.dp,
    bottomContentInset: Dp = 0.dp,
    onLyricLongClick: ((LyricEntry) -> Unit)? = null,
    onSeekTo: (Long) -> Unit = {}
) {
    val effectiveTranslatedLyrics = translatedLyrics.orEmpty()
    val syncedLyrics = remember(
        rawLyrics,
        lyrics,
        effectiveTranslatedLyrics
    ) {
        buildAdvancedSyncedLyrics(
            rawLyrics = rawLyrics,
            lyrics = lyrics,
            translatedLyrics = effectiveTranslatedLyrics
        )
    }
    if (syncedLyrics.lines.isEmpty()) {
        return
    }

    val normalTextStyle = remember(fontSize, fontWeight) {
        TextStyle(
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            textMotion = TextMotion.Animated,
            lineHeight = (fontSize.value * 1.18f).sp
        )
    }
    val accompanimentTextStyle = remember(fontSize) {
        TextStyle(
            fontSize = (fontSize.value * 0.62f).sp,
            fontWeight = FontWeight.SemiBold,
            textMotion = TextMotion.Animated,
            lineHeight = (fontSize.value * 0.62f * 1.12f).sp
        )
    }
    val baseTranslationTextStyle = LocalTextStyle.current
    val translationTextStyle = remember(baseTranslationTextStyle, translationFontSize) {
        baseTranslationTextStyle.copy(
            fontSize = translationFontSize,
            lineHeight = (translationFontSize.value * 1.12f).sp
        )
    }
    val listState = rememberLazyListState()
    val blurDelta = (lyricBlurAmount * 0.45f).coerceIn(0f, 4f)
    val safeCurrentPosition = (currentTimeMs + lyricOffsetMs)
        .coerceAtLeast(0L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
    val renderPositionProvider = rememberInterpolatedPlaybackPositionProvider(
        currentTimeMs = safeCurrentPosition,
        isPlaying = isPlaying,
        playbackSpeed = playbackSpeed
    )

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val focusedLineVisualCompensation = with(density) {
            normalTextStyle.lineHeight.toDp() * FocusedLyricVisualCompensationRatio
        }
        val effectiveOffset = resolvePlayedLyricViewportOffset(
            viewportHeight = maxHeight,
            keepAliveZone = keepAliveZone,
            minimumOffset = offset,
            playedLyricViewportFraction = playedLyricViewportFraction,
            focusedLineVisualCompensation = focusedLineVisualCompensation,
            topFadeLength = topFadeLength
        )

        CompositionLocalProvider(LocalTextStyle provides translationTextStyle) {
            ModernKaraokeLyricsView(
                listState = listState,
                lyrics = syncedLyrics,
                currentPosition = { safeCurrentPosition.toInt() },
                renderCurrentPosition = renderPositionProvider,
                onLineClicked = { line -> onSeekTo(line.start.toLong()) },
                onLinePressed = { line ->
                    val entry = resolvePressedLyricEntry(line, lyrics)
                    if (onLyricLongClick != null) {
                        onLyricLongClick(entry)
                    } else {
                        onSeekTo(line.start.toLong())
                    }
                },
                modifier = Modifier.fillMaxSize(),
                normalLineTextStyle = normalTextStyle,
                accompanimentLineTextStyle = accompanimentTextStyle,
                textColor = textColor,
                showTranslation = showLyricTranslation,
                showPhonetic = false,
                useBlurEffect = lyricBlurEnabled,
                animateViewportScroll = animateViewportScroll,
                offset = effectiveOffset,
                keepAliveZone = keepAliveZone,
                bottomContentInset = bottomContentInset,
                blurDelta = blurDelta,
                topFadeLength = topFadeLength,
                bottomFadeLength = bottomFadeLength,
                karaokeEnabled = karaokeEnabled
            )
        }
    }
}

private fun resolvePressedLyricEntry(
    line: ISyncedLine,
    lyrics: List<LyricEntry>
): LyricEntry {
    val startTimeMs = line.start.toLong()
    lyrics.firstOrNull { it.startTimeMs == startTimeMs }?.let { return it }
    lyrics.minByOrNull { abs(it.startTimeMs - startTimeMs) }
        ?.takeIf { abs(it.startTimeMs - startTimeMs) <= TranslationAlignmentToleranceMs }
        ?.let { return it }

    return LyricEntry(
        text = line.plainText(),
        startTimeMs = line.start.toLong(),
        endTimeMs = line.end.toLong()
    )
}

private fun ISyncedLine.plainText(): String {
    return when (this) {
        is KaraokeLine -> syllables.joinToString(separator = "") { it.content }
        is SyncedLine -> content
        else -> ""
    }
}

internal fun resolvePlayedLyricViewportOffset(
    viewportHeight: Dp,
    keepAliveZone: Dp,
    minimumOffset: Dp,
    playedLyricViewportFraction: Float,
    focusedLineVisualCompensation: Dp,
    topFadeLength: Dp
): Dp {
    val effectivePlayedLyricViewportFraction = playedLyricViewportFraction.coerceIn(0.18f, 0.46f)
    val desiredPlayedLyricSpace = viewportHeight * effectivePlayedLyricViewportFraction
    val minimumVisiblePlayedLyricSpace = topFadeLength +
        FocusedLyricMaskSafePadding +
        keepAliveZone
    val resolvedPlayedLyricSpace = if (desiredPlayedLyricSpace > minimumVisiblePlayedLyricSpace) {
        desiredPlayedLyricSpace
    } else {
        minimumVisiblePlayedLyricSpace
    }
    return (
        resolvedPlayedLyricSpace + focusedLineVisualCompensation - keepAliveZone
        ).coerceAtLeast(minimumOffset)
}

internal fun buildAdvancedSyncedLyrics(
    rawLyrics: String?,
    lyrics: List<LyricEntry>,
    translatedLyrics: List<LyricEntry>,
): SyncedLyrics {
    val baseLyrics = resolveAdvancedBaseSyncedLyrics(
        rawLyrics = rawLyrics,
        lyrics = lyrics
    )
    return baseLyrics.attachTranslations(translatedLyrics)
}

private fun resolveAdvancedBaseSyncedLyrics(
    rawLyrics: String?,
    lyrics: List<LyricEntry>
): SyncedLyrics {
    val rawHasEmbeddedKaraoke = !rawLyrics.isNullOrBlank() &&
        (isTtmlLyrics(rawLyrics) || isNeteaseYrc(rawLyrics))
    return when {
        // 有逐字时间戳、或纯 LRC/无 raw（toSyncedLine 会自动合成逐字时间戳）：直接用我们的条目
        lyrics.isNotEmpty() && (!rawHasEmbeddedKaraoke || lyrics.hasWordTimedEntries()) ->
            lyrics.toSyncedLyrics()
        // TTML/YRC：优先 AutoParser（自带逐字）
        else -> parseRawLyrics(rawLyrics).takeIf { it.lines.isNotEmpty() }
            ?: lyrics.toSyncedLyrics()
    }
}

private fun parseRawLyrics(rawLyrics: String?): SyncedLyrics {
    if (rawLyrics.isNullOrBlank()) {
        return SyncedLyrics(emptyList())
    }
    return runCatching {
        if (isTtmlLyrics(rawLyrics) || isNeteaseYrc(rawLyrics)) {
            AutoParser().parse(rawLyrics)
        } else {
            parseNeteaseLrc(rawLyrics).toSyncedLyrics()
        }
    }
        .getOrDefault(SyncedLyrics(emptyList()))
}

private fun List<LyricEntry>.toSyncedLyrics(): SyncedLyrics {
    if (isEmpty()) {
        return SyncedLyrics(emptyList())
    }
    return SyncedLyrics(lines = map { it.toSyncedLine() })
}

private fun LyricEntry.toSyncedLine(): ISyncedLine {
    // 无逐字时间戳时合成：把行文本按字符拆分，起止时间按行时长均匀插值，
    // 与旧版逐字（calculateLineProgress 按整行时长线性推进）节奏一致。
    val realWords = words.orEmpty()
    val syllables = if (realWords.isEmpty()) {
        synthesizeWordTimings()
    } else {
        realWords.mapIndexedNotNull { index, word ->
            val content = extractWordContent(index)
            if (content.isEmpty()) {
                null
            } else {
                KaraokeSyllable(
                    content = content,
                    start = word.startTimeMs.toIntSafely(),
                    end = max(word.endTimeMs.toIntSafely(), word.startTimeMs.toIntSafely())
                )
            }
        }
    }

    if (syllables.isEmpty()) {
        return SyncedLine(
            content = text,
            translation = translation,
            start = startTimeMs.toIntSafely(),
            end = endTimeMs.toIntSafely()
        )
    }

    return KaraokeLine.MainKaraokeLine(
        syllables = syllables,
        translation = translation,
        alignment = KaraokeAlignment.Unspecified,
        start = startTimeMs.toIntSafely(),
        end = max(endTimeMs.toIntSafely(), syllables.last().end)
    )
}

private fun LyricEntry.synthesizeWordTimings(): List<KaraokeSyllable> {
    if (text.isBlank()) {
        return emptyList()
    }
    val lineStart = startTimeMs
    val lineDuration = (endTimeMs - startTimeMs).coerceAtLeast(1L)
    val charCount = text.length
    return List(charCount) { index ->
        val charStart = lineStart + (lineDuration * index) / charCount
        val charEnd = lineStart + (lineDuration * (index + 1)) / charCount
        KaraokeSyllable(
            content = text.substring(index, index + 1),
            start = charStart.toIntSafely(),
            end = charEnd.toIntSafely()
        )
    }
}

private fun LyricEntry.extractWordContent(index: Int): String {
    val safeWords = words.orEmpty()
    if (safeWords.isEmpty()) {
        return text
    }

    var cursor = 0
    safeWords.forEachIndexed { currentIndex, word ->
        val requestedLength = word.charCount.coerceAtLeast(0)
        val isLast = currentIndex == safeWords.lastIndex
        val endExclusive = when {
            isLast -> text.length
            requestedLength == 0 -> cursor
            else -> (cursor + requestedLength).coerceAtMost(text.length)
        }
        if (currentIndex == index) {
            return text.substring(cursor.coerceAtMost(text.length), endExclusive)
        }
        cursor = endExclusive
    }
    return ""
}

private fun SyncedLyrics.attachTranslations(
    translations: List<LyricEntry>
): SyncedLyrics {
    if (lines.isEmpty() || translations.isEmpty()) {
        return this
    }

    val baseLyricEntries = lines.map { line ->
        LyricEntry(
            text = "",
            startTimeMs = line.start.toLong(),
            endTimeMs = line.end.toLong()
        )
    }
    val translationMatchesByIndex = matchTranslationsToLineIndices(
        lines = baseLyricEntries,
        translations = translations,
        toleranceMs = TranslationAlignmentToleranceMs
    )

    val updatedLines = lines.mapIndexed { index, line ->
        val matchedTranslation = translationMatchesByIndex[index]?.text

        when {
            matchedTranslation.isNullOrBlank() -> line
            line is KaraokeLine.MainKaraokeLine && line.translation.isNullOrBlank() ->
                line.copy(translation = matchedTranslation)
            line is SyncedLine && line.translation.isNullOrBlank() ->
                line.copy(translation = matchedTranslation)
            else -> line
        }
    }

    return copy(lines = updatedLines)
}

private fun Long.toIntSafely(): Int {
    return coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}

