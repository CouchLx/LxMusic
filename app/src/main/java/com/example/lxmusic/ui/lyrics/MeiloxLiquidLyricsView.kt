package com.example.lxmusic.ui.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeBreathingDotsDefaults
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView

/**
 * MeiloX 风格液态逐字歌词视图
 * 基于 accompanist-lyrics-ui 的 KaraokeLyricsView 构建，
 * 具备完整的 Apple Music 风格液态高亮、间奏伴奏三点呼吸动画（KaraokeBreathingDots）、
 * 背景模糊与文字排版自适应。
 */
@Composable
fun MeiloxLiquidLyricsView(
    lyrics: List<LyricEntry>,
    currentTimeMs: Long,
    lyricOffsetMs: Long = 0L,
    isPlaying: Boolean,
    playbackSpeed: Float = 1f,
    onSeekTo: (Long) -> Unit,
    onLyricLongClick: ((LyricEntry) -> Unit)? = null,
    rawLyrics: String? = null,
    translatedLyrics: List<LyricEntry>? = null,
    showLyricTranslation: Boolean = true,
    textColor: Color = Color.White,
    fontSize: TextUnit = 32.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign = TextAlign.Center,
    translationFontSize: TextUnit = 16.sp,
    offset: Dp = 48.dp,
    keepAliveZone: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val effectiveTranslatedLyrics = translatedLyrics.orEmpty()
    val syncedLyrics = remember(rawLyrics, lyrics, effectiveTranslatedLyrics) {
        buildAdvancedSyncedLyrics(rawLyrics, lyrics, effectiveTranslatedLyrics)
    }
    if (syncedLyrics.lines.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "纯音乐",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            )
        }
        return
    }

    val normalTextStyle = remember(fontSize, fontWeight, textAlign) {
        TextStyle(
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            textMotion = TextMotion.Animated,
            lineHeight = (fontSize.value * 1.25f).sp
        )
    }
    val accompanimentTextStyle = remember(fontSize, textAlign) {
        TextStyle(
            fontSize = (fontSize.value * 0.65f).sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = textAlign,
            textMotion = TextMotion.Animated,
            lineHeight = (fontSize.value * 0.65f * 1.2f).sp
        )
    }
    val translationTextStyle = remember(translationFontSize, textAlign) {
        TextStyle(
            fontSize = translationFontSize,
            textAlign = textAlign,
            lineHeight = (translationFontSize.value * 1.2f).sp
        )
    }

    val listKey = remember(rawLyrics, lyrics.size) { rawLyrics?.hashCode() ?: lyrics.hashCode() }
    val listState = remember(listKey) { LazyListState() }
    val lyricAlpha = remember(listKey) { Animatable(0.2f) }
    LaunchedEffect(listKey) {
        lyricAlpha.animateTo(1f, tween(250))
    }

    val safeCurrentPosition = (currentTimeMs + lyricOffsetMs)
        .coerceAtLeast(0L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
    val renderPositionProvider = rememberInterpolatedPlaybackPositionProvider(
        currentTimeMs = safeCurrentPosition,
        isPlaying = isPlaying,
        playbackSpeed = playbackSpeed
    )

    CompositionLocalProvider(LocalTextStyle provides translationTextStyle) {
        KaraokeLyricsView(
            listState = listState,
            lyrics = syncedLyrics,
            currentPosition = { safeCurrentPosition.toInt() },
            renderCurrentPosition = renderPositionProvider,
            onLineClicked = { line -> onSeekTo(line.start.toLong()) },
            onLinePressed = { line ->
                val plain = when (line) {
                    is KaraokeLine -> line.syllables.joinToString("") { it.content }
                    is SyncedLine -> line.content
                    else -> ""
                }
                val entry = lyrics.find { it.startTimeMs == line.start.toLong() }
                    ?: LyricEntry(plain, line.start.toLong(), line.end.toLong())
                if (onLyricLongClick != null) onLyricLongClick(entry) else onSeekTo(line.start.toLong())
            },
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer { alpha = lyricAlpha.value },
            normalLineTextStyle = normalTextStyle,
            accompanimentLineTextStyle = accompanimentTextStyle,
            textColor = textColor,
            breathingDotsDefaults = KaraokeBreathingDotsDefaults(
                breathingDotsColor = textColor,
                size = 14.dp,
                margin = 10.dp
            ),
            blendMode = BlendMode.Plus,
            useBlurEffect = true,
            showTranslation = showLyricTranslation,
            showPhonetic = false,
            animateViewportScroll = true,
            focusedLineScale = 1.05f,
            unfocusedLineScale = 0.96f,
            activeLineAlpha = 1f,
            inactiveLineAlpha = 0.35f,
            offset = offset,
            keepAliveZone = keepAliveZone,
            karaokeEnabled = true
        )
    }
}
