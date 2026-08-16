package com.example.lxmusic.ui.pages

/*
 * 播放器单页面（合并原 PlayerPage + LyricsPage）：
 * 顶栏 / 进度条 / 控制栏固定不动，中间区域卡片式翻页（0=封面卡 1=歌词卡）。
 * 背景由 MainActivity 舞台层共享渲染，翻页时背景与四周 UI 完全静止。
 */

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import androidx.media3.common.C
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.PlayerProgress
import com.example.lxmusic.R
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.ui.components.WaveformSlider
import com.example.lxmusic.ui.lyrics.LyricEntry
import com.example.lxmusic.ui.lyrics.LyricSeekHapticFeedback
import com.example.lxmusic.ui.lyrics.NeriAdvancedLyricsView
import com.example.lxmusic.ui.lyrics.SyncedLyricsView
import com.example.lxmusic.ui.lyrics.parseNeteaseLyricsAuto
import com.example.lxmusic.ui.lyrics.rememberLyricSeekHapticFeedback
import com.example.lxmusic.ui.lyrics.resolveLyricPreviewTimeMs
import com.example.lxmusic.ui.lyrics.shouldReleaseLyricSeekPreview
import com.example.lxmusic.util.formatDuration
import com.example.lxmusic.util.parseLrcLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 歌词时间提前量（毫秒）：LRC 歌词时间戳普遍比实际演唱滞后，
 * 正值 = 歌词提前显示，弥补滞后感。改这一个值即可同时调整
 * 预览条与歌词卡（逐字与不逐字都生效）。
 */
private val LYRIC_TIME_OFFSET_MS = 300L

/**
 * 播放器单页面：顶栏 / 进度条 / 控制栏固定，中间卡片区 HorizontalPager（0=封面卡 1=歌词卡）。
 * 拖动进度条时封面卡与歌词卡的歌词预览实时联动（共享 isSeeking/sliderPosition）。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerStage(
    song: SongInfo,
    isPlaying: Boolean,
    progress: StateFlow<PlayerProgress>,
    // 当前解码音频格式（采样率/位深，来自 PlayerService 的 ExoPlayer；仅专辑页显示）
    audioFormat: androidx.media3.common.Format? = null,
    pagerState: PagerState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlaylistClick: () -> Unit = {},
    playMode: Int = 0,
    onPlayModeChange: (Int) -> Unit = {},
    isFavorite: Boolean = false,
    onFavoriteClick: () -> Unit = {},
    // 封面/背景旋转相位（由 MainActivity 舞台层统一驱动）
    rotationAngle: Float = 0f,
    isRoundAlbum: Boolean = false,
    isRotating: Boolean = false,
    // 仅用于深色背景下的前景取色（背景本体在舞台层）
    playerHyperBg: Boolean = false,
    playerCoverBlurBg: Boolean = false,
    playerLyricBlur: Boolean = true,
    playerLyricBlurAmount: Float = 10f,
    // 逐字歌词动效（设置-播放器设置，默认开启；关闭=整行高亮）
    playerLyricsWordEffect: Boolean = true,
    playerWaveformSlider: Boolean = true,
    playerLyricsSeekPreview: Boolean = true,
    // 播放器 UI 显示设置（设置-播放器设置）
    playerTapCoverToLyrics: Boolean = false,
    playerCompactControls: Boolean = false,
    playerShowTopFavorite: Boolean = false,
    // 歌词显示（播放器菜单-歌词设置，实时生效并持久化）
    // 两套独立：lyric* = 歌词页（歌词卡）；coverLyric* = 主页（封面卡歌词预览条），互不互通
    lyricFontSize: Float = 20f,
    onLyricFontSizeChange: (Float) -> Unit = {},
    lyricFontWeight: Float = 500f,
    onLyricFontWeightChange: (Float) -> Unit = {},
    lyricAlignment: String = "center",
    onLyricAlignmentChange: (String) -> Unit = {},
    coverLyricFontSize: Float = 16f,
    onCoverLyricFontSizeChange: (Float) -> Unit = {},
    coverLyricFontWeight: Float = 500f,
    onCoverLyricFontWeightChange: (Float) -> Unit = {},
    coverLyricAlignment: String = "center",
    onCoverLyricAlignmentChange: (String) -> Unit = {},
    lyricsText: String? = null
) {
    val progressState by progress.collectAsState()
    val currentPosition = progressState.positionMs
    val totalDuration = progressState.durationMs
    val scope = rememberCoroutineScope()

    // ===== 共享状态（进度条 ↔ 歌词预览联动；MutableState 引用供底部组件读写） =====
    val isSeekingState = remember { mutableStateOf(false) }
    var isSeeking by isSeekingState
    val sliderPositionState = remember { mutableFloatStateOf(currentPosition.toFloat()) }
    var sliderPosition by sliderPositionState
    val pendingPreviewState = remember { mutableStateOf<Long?>(null) }
    var pendingSeekPreviewPositionMs by pendingPreviewState
    var displayBitRate by remember(song.filePath) { mutableIntStateOf(0) }
    var displayExtName by remember(song.filePath) { mutableStateOf<String?>(null) }
    var showPlayerMenu by remember { mutableStateOf(false) }

    // 轮询音质信息
    LaunchedEffect(song.filePath) {
        displayBitRate = 0
        displayExtName = null
        repeat(30) {
            val br = KuGouApi.lastBitRate
            if (br > 0) {6
                displayBitRate = br
                displayExtName = KuGouApi.lastExtName
                return@LaunchedEffect
            }
            delay(500)
        }
    }

    // 定时更新播放进度
    LaunchedEffect(progressState.positionMs, isSeeking) {
        if (!isSeeking) {
            sliderPosition = progressState.positionMs.toFloat()
        }
    }

    // 歌词解析（两卡共享；拖动进度条时跟随预览）
    val lyricText = lyricsText?.takeIf { it.isNotBlank() }
        ?: song.lyrics?.takeIf { it.isNotBlank() }
    val lyricEntries = remember(lyricText) {
        lyricText?.let { parseNeteaseLyricsAuto(it) }?.takeIf { it.isNotEmpty() }
            ?: emptyList()
    }
    val lyricPreviewTimeMs = resolveLyricPreviewTimeMs(
        isDraggingSlider = isSeeking,
        sliderPreviewPositionMs = sliderPosition.toLong(),
        pendingSeekPreviewPositionMs = pendingSeekPreviewPositionMs,
        playbackPositionMs = currentPosition
    )
    LaunchedEffect(currentPosition, pendingSeekPreviewPositionMs) {
        val pending = pendingSeekPreviewPositionMs ?: return@LaunchedEffect
        if (shouldReleaseLyricSeekPreview(currentPosition, pending)) {
            pendingSeekPreviewPositionMs = null
        }
    }

    val durationState = rememberUpdatedState(totalDuration)
    val lyricHaptic = rememberLyricSeekHapticFeedback(lyricEntries)

    // 深色动态背景（流体/封面模糊）下强制白色前景，保证文字与图标可读
    val deepBackdrop = playerHyperBg || playerCoverBlurBg
    val uiTint = if (deepBackdrop) Color.White else MaterialTheme.colorScheme.onSurface
    val uiTintVariant = if (deepBackdrop) {
        Color.White.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val progressRatio = if (totalDuration > 0) {
        sliderPosition / totalDuration.toFloat()
    } else 0f

    // 歌词显示配置（菜单实时调节）：粗细 100-900 取整，排版映射到 TextAlign
    val lyricFontWeightValue = FontWeight(lyricFontWeight.roundToInt().coerceIn(100, 900))
    val lyricTextAlign = when (lyricAlignment) {
        "left" -> TextAlign.Start
        "right" -> TextAlign.End
        else -> TextAlign.Center
    }
    // 封面卡歌词预览条配置（独立一套，与歌词页不互通）
    val coverLyricFontWeightValue = FontWeight(coverLyricFontWeight.roundToInt().coerceIn(100, 900))
    val coverLyricTextAlign = when (coverLyricAlignment) {
        "left" -> TextAlign.Start
        "right" -> TextAlign.End
        else -> TextAlign.Center
    }
    // 音频参数文本（仅专辑页显示）：采样率 + 位深 + 格式（如 "44.1kHz 16bit FLAC"）；
    // 解码格式缺失时回退码率 + 扩展名（如 "FLAC 320kbps"）
    val audioInfoText = if (pagerState.currentPage == 0) {
        buildAudioInfoText(audioFormat, displayExtName, displayBitRate)
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        // ===== 顶栏（固定）：歌名 + 歌手两行（无返回箭头，主页/歌词页共用） =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    color = uiTint
                )
                Text(
                    text = song.artist ?: "未知歌手",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    color = uiTintVariant
                )
            }
            // 顶栏右侧：主页 = 收藏按钮常驻（音质标签移除，收藏移至菜单旁）；
            // 歌词页 = 设置决定收藏/音质标签（淡入淡出过渡）
            Box(
                modifier = Modifier.padding(end = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (pagerState.currentPage == 0) {
                    IconButton(
                        onClick = onFavoriteClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "取消喜欢" else "喜欢",
                            modifier = Modifier.size(24.dp),
                            tint = if (isFavorite) Color(0xFFE57373) else uiTintVariant
                        )
                    }
                } else {
                    val showTopFavorite = pagerState.currentPage == 1 && playerShowTopFavorite
                    // 用全限定名：Row 作用域内 AnimatedVisibility 会解析到 RowScope 实验版扩展
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showTopFavorite,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(150))
                    ) {
                        IconButton(
                            onClick = onFavoriteClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFavorite) "取消喜欢" else "喜欢",
                                modifier = Modifier.size(24.dp),
                                tint = if (isFavorite) Color(0xFFE57373) else uiTintVariant
                            )
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !showTopFavorite && displayBitRate > 0,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(150))
                    ) {
                        val bitRateKbps = displayBitRate / 1000
                        val qualityLabel = when {
                            displayExtName == "flac" -> "FLAC"
                            displayExtName == "ape" -> "APE"
                            bitRateKbps >= 900 -> "Hi-Res"
                            bitRateKbps >= 320 -> "HQ 320"
                            bitRateKbps >= 128 -> "SQ 128"
                            else -> "${bitRateKbps}k"
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = primaryColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = qualityLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = primaryColor
                            )
                        }
                    }
                }
            }
            // 菜单按钮（主页/歌词页分别弹各自的菜单；歌词设置两套独立）
            IconButton(
                onClick = { showPlayerMenu = true }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                    tint = uiTint
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 中间卡片翻页区（唯一可变区域） =====
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            beyondViewportPageCount = 1
        ) { page ->
            val cardPage = page
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 跟手拖动时：离屏卡片轻微缩放 + 淡出 + 圆角（QQ音乐式卡片感）。
                    // 用 layoutInfo.visiblePagesInfo 的 px 偏移归一化（稳定 API，
                    // 不依赖各版本 currentPageOffsetFraction 语义差异）
                    .graphicsLayer {
                        val info = pagerState.layoutInfo.visiblePagesInfo
                            .firstOrNull { it.index == cardPage } ?: return@graphicsLayer
                        val viewport = pagerState.layoutInfo.viewportSize.width.toFloat().coerceAtLeast(1f)
                        val p = abs(info.offset / viewport).coerceIn(0f, 1f)
                        scaleX = (1f - 0.08f * p).coerceAtLeast(0.85f)
                        scaleY = (1f - 0.08f * p).coerceAtLeast(0.85f)
                        alpha = (1f - 0.12f * p).coerceAtLeast(0.8f)
                    }
                    .clip(RoundedCornerShape(28.dp))
            ) {
                when (cardPage) {
                    0 -> PlayerCoverCard(
                        song = song,
                        isRoundAlbum = isRoundAlbum,
                        isRotating = isRotating,
                        rotationAngle = rotationAngle,
                        uiTint = uiTint,
                        uiTintVariant = uiTintVariant,
                        lyricEntries = lyricEntries,
                        lyricPreviewTimeMs = lyricPreviewTimeMs,
                        lyricFontSize = coverLyricFontSize,
                        lyricFontWeight = coverLyricFontWeightValue,
                        lyricTextAlign = coverLyricTextAlign,
                        karaokeEnabled = playerLyricsWordEffect,
                        lyricBlurEnabled = playerLyricBlur,
                        lyricBlurAmount = playerLyricBlurAmount,
                        isPlaying = isPlaying,
                        isSeeking = isSeeking,
                        tapCoverToLyrics = playerTapCoverToLyrics,
                        onSeekTo = onSeek,
                        onOpenLyrics = { scope.launch { pagerState.animateScrollToPage(1) } }
                    )
                    else -> {
                        // 歌词卡：NeriPlayer 高级歌词渲染（滚动动效 + 点击跳转）；
                        // 逐字歌词动效开关只控制逐字点亮，其余样式不变
                        NeriAdvancedLyricsView(
                            lyrics = lyricEntries,
                            currentTimeMs = lyricPreviewTimeMs,
                            lyricOffsetMs = LYRIC_TIME_OFFSET_MS,
                            modifier = Modifier.fillMaxSize(),
                            textColor = uiTint,
                            fontSize = lyricFontSize.sp,
                            fontWeight = lyricFontWeightValue,
                            textAlign = lyricTextAlign,
                            translationFontSize = (lyricFontSize * 0.7f).sp,
                            rawLyrics = lyricText,
                            karaokeEnabled = playerLyricsWordEffect,
                            lyricBlurEnabled = playerLyricBlur,
                            lyricBlurAmount = playerLyricBlurAmount,
                            isPlaying = isPlaying,
                            animateViewportScroll = !isSeeking,
                            onSeekTo = onSeek
                        )
                    }
                }
            }
        }

        // ===== 底部区域：主页进度条独立在控制栏上方；歌词卡时进度条滑入控制栏顶部紧贴播放键 =====
        val isLyricsCompact = pagerState.currentPage == 1 && playerCompactControls
        // 歌词卡控制栏顶部的进度条（捕获共享状态，仅在歌词卡组合）
        val compactProgressSection: @Composable () -> Unit = {
            PlayerProgressSection(
                isSeekingState = isSeekingState,
                sliderPositionState = sliderPositionState,
                pendingPreviewState = pendingPreviewState,
                progressRatio = progressRatio,
                totalDuration = totalDuration,
                currentPosition = currentPosition,
                durationState = durationState,
                lyricHaptic = lyricHaptic,
                playerLyricsSeekPreview = playerLyricsSeekPreview,
                playerWaveformSlider = playerWaveformSlider,
                deepBackdrop = deepBackdrop,
                primaryColor = primaryColor,
                uiTint = uiTint,
                uiTintVariant = uiTintVariant,
                isPlaying = isPlaying,
                isWaiting = progressState.isWaiting,
                onSeek = onSeek,
                audioInfo = null
            )
        }
        // 主页：进度条独立在控制栏上方（歌词卡时淡出让位）
        androidx.compose.animation.AnimatedVisibility(
            visible = !isLyricsCompact,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150))
        ) {
            PlayerProgressSection(
                isSeekingState = isSeekingState,
                sliderPositionState = sliderPositionState,
                pendingPreviewState = pendingPreviewState,
                progressRatio = progressRatio,
                totalDuration = totalDuration,
                currentPosition = currentPosition,
                durationState = durationState,
                lyricHaptic = lyricHaptic,
                playerLyricsSeekPreview = playerLyricsSeekPreview,
                playerWaveformSlider = playerWaveformSlider,
                deepBackdrop = deepBackdrop,
                primaryColor = primaryColor,
                uiTint = uiTint,
                uiTintVariant = uiTintVariant,
                isPlaying = isPlaying,
                isWaiting = progressState.isWaiting,
                onSeek = onSeek,
                audioInfo = audioInfoText
            )
        }
        // 间隙：歌词卡时收缩，让进度条更贴控制栏
        val bottomGap by animateDpAsState(
            targetValue = if (isLyricsCompact) 4.dp else 12.dp,
            animationSpec = spring(dampingRatio = 0.8f),
            label = "bottomGap"
        )
        Spacer(modifier = Modifier.height(bottomGap))
        PlayerFullControls(
            compactProgressSection = compactProgressSection,
            isLyricsCompact = isLyricsCompact,
            isPlaying = isPlaying,
            playMode = playMode,
            onPlayModeChange = onPlayModeChange,
            onPrevious = onPrevious,
            onNext = onNext,
            onPlayPause = onPlayPause,
            onPlaylistClick = onPlaylistClick,
            deepBackdrop = deepBackdrop,
            primaryColor = primaryColor,
            uiTint = uiTint,
            uiTintVariant = uiTintVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    // ===== 播放器菜单（主页菜单 → 封面卡歌词设置；歌词页菜单 → 歌词页歌词设置，两套独立） =====
    if (showPlayerMenu) {
        val onCoverPage = pagerState.currentPage == 0
        PlayerMenuSheet(
            onDismiss = { showPlayerMenu = false },
            targetLabel = if (onCoverPage) "封面卡歌词" else "歌词页歌词",
            lyricFontSize = if (onCoverPage) coverLyricFontSize else lyricFontSize,
            onLyricFontSizeChange = if (onCoverPage) onCoverLyricFontSizeChange else onLyricFontSizeChange,
            lyricFontWeight = if (onCoverPage) coverLyricFontWeight else lyricFontWeight,
            onLyricFontWeightChange = if (onCoverPage) onCoverLyricFontWeightChange else onLyricFontWeightChange,
            lyricAlignment = if (onCoverPage) coverLyricAlignment else lyricAlignment,
            onLyricAlignmentChange = if (onCoverPage) onCoverLyricAlignmentChange else onLyricAlignmentChange
        )
    }
}

// ==================== 底部区域组件 ====================

/** 进度条 + 时间行（主页与歌词卡共用；拖动状态经 MutableState 引用读写，供歌词预览联动） */
@Composable
private fun PlayerProgressSection(
    isSeekingState: MutableState<Boolean>,
    sliderPositionState: MutableState<Float>,
    pendingPreviewState: MutableState<Long?>,
    progressRatio: Float,
    totalDuration: Long,
    currentPosition: Long,
    durationState: State<Long>,
    lyricHaptic: LyricSeekHapticFeedback,
    playerLyricsSeekPreview: Boolean,
    playerWaveformSlider: Boolean,
    deepBackdrop: Boolean,
    primaryColor: Color,
    uiTint: Color,
    uiTintVariant: Color,
    isPlaying: Boolean,
    isWaiting: Boolean,
    onSeek: (Long) -> Unit,
    // 时间行中间显示的音频参数（如 "44.1kHz 16bit FLAC"）；null = 不显示
    audioInfo: String? = null
) {
    val isSeeking = isSeekingState.value
    val sliderPosition = sliderPositionState.value
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (playerWaveformSlider) {
            // 波浪进度条（NeriPlayer 移植）
            WaveformSlider(
                value = progressRatio,
                onValueChange = { newValue ->
                    sliderPositionState.value = newValue * durationState.value.toFloat()
                    if (playerLyricsSeekPreview) {
                        lyricHaptic.onSeekMove(sliderPositionState.value.toLong())
                    }
                },
                onValueChangeFinished = {
                    isSeekingState.value = false
                    pendingPreviewState.value = sliderPositionState.value.toLong()
                    onSeek(sliderPositionState.value.toLong())
                    if (playerLyricsSeekPreview) lyricHaptic.onSeekEnd()
                },
                isPlaying = isPlaying,
                onValueChangeStarted = { _ ->
                    isSeekingState.value = true
                    if (playerLyricsSeekPreview) {
                        lyricHaptic.onSeekStart(sliderPositionState.value.toLong())
                    }
                },
                onValueChangeCanceled = {
                    isSeekingState.value = false
                    pendingPreviewState.value = null
                    if (playerLyricsSeekPreview) lyricHaptic.onSeekEnd()
                },
                enabled = totalDuration > 0,
                isPlaybackWaiting = isWaiting,
                activeTint = if (deepBackdrop) Color.White else primaryColor,
                inactiveTint = uiTint
            )
        } else {
            // 自定义进度条
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            isSeekingState.value = true
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            fun update(x: Float) {
                                val max = durationState.value.toFloat().coerceAtLeast(1f)
                                sliderPositionState.value = (x.coerceIn(0f, width) / width) * max
                            }
                            try {
                                update(down.position.x)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    update(change.position.x)
                                }
                            } finally {
                                isSeekingState.value = false
                                onSeek(sliderPositionState.value.toLong())
                            }
                        }
                    }
            ) {
                val boxWidthDp = maxWidth
                val sliderOffset = progressRatio.coerceIn(0f, 1f)
                val sliderStart = (boxWidthDp * sliderOffset - 6.dp).coerceIn(0.dp, boxWidthDp - 12.dp)

                // 背景轨道
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
                // 进度
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressRatio.coerceIn(0f, 1f))
                        .height(4.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(2.dp))
                        .background(primaryColor)
                )
                // 滑块
                Box(
                    modifier = Modifier
                        .padding(start = sliderStart)
                        .size(12.dp)
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(primaryColor)
                )
            }
        }

        // 时间行：左侧当前时间 / 中间音频参数（仅专辑页） / 右侧总时长
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(if (isSeeking) sliderPosition.toLong() else currentPosition),
                style = MaterialTheme.typography.bodySmall,
                color = uiTintVariant,
                modifier = Modifier.weight(1f)
            )
            if (audioInfo != null) {
                Text(
                    text = audioInfo,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = uiTintVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(
                text = formatDuration(totalDuration),
                style = MaterialTheme.typography.bodySmall,
                color = uiTintVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 控制栏（主页与歌词卡共用同一套按钮参数：尺寸/间距/居中布局完全一致）：
 * - 主页：进度条独立在上方，模式/列表显示，控制键垂直居中
 * - 歌词卡（isLyricsCompact）：进度条滑入容器顶部紧贴播放键，模式/列表淡出，控制键平滑下沉贴底
 */
@Composable
private fun PlayerFullControls(
    compactProgressSection: @Composable () -> Unit,
    isLyricsCompact: Boolean,
    isPlaying: Boolean,
    playMode: Int,
    onPlayModeChange: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onPlaylistClick: () -> Unit,
    deepBackdrop: Boolean,
    primaryColor: Color,
    uiTint: Color,
    uiTintVariant: Color
) {
    // 控制栏高度：主页 128dp；歌词卡 140dp（容纳顶部进度条 64dp + 4dp 间隙 + 贴底播放键 72dp）
    val ctrlHeight by animateDpAsState(
        targetValue = if (isLyricsCompact) 140.dp else 128.dp,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "ctrlHeight"
    )
    // 控制键从垂直居中平滑下沉到贴底（播放键与上一首/下一首保持同一水平线，同主页的相对位置）
    val playDrop by animateDpAsState(
        targetValue = if (isLyricsCompact) 34.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "playDrop"
    )
    val sideDrop by animateDpAsState(
        targetValue = if (isLyricsCompact) 34.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "sideDrop"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ctrlHeight),
        contentAlignment = Alignment.Center
    ) {
        // 歌词卡：进度条从上方滑入容器顶部，紧贴播放键（高 40dp，与贴底的播放键间留 16dp）
        AnimatedVisibility(
            visible = isLyricsCompact,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -it },
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            compactProgressSection()
        }

        // 播放模式 - 左下（歌词卡淡出）
        AnimatedVisibility(
            visible = !isLyricsCompact,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            IconButton(
                onClick = {
                    val newMode = (playMode + 1) % 3
                    onPlayModeChange(newMode)
                }
            ) {
                Icon(
                    imageVector = when (playMode) {
                        1 -> Icons.Default.Shuffle
                        2 -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = stringResource(R.string.action_play_mode),
                    modifier = Modifier.size(24.dp),
                    tint = if (playMode != 0) {
                        if (deepBackdrop) Color.White else primaryColor
                    } else {
                        uiTintVariant
                    }
                )
            }
        }

        // 上一首 - 左中（歌词卡平滑下沉贴底）
        IconButton(
            onClick = onPrevious,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(0, sideDrop.roundToPx()) }
                .padding(start = 60.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.action_previous),
                modifier = Modifier.size(32.dp),
                tint = uiTint
            )
        }

        // 下一首 - 右中（歌词卡平滑下沉贴底）
        IconButton(
            onClick = onNext,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset { IntOffset(0, sideDrop.roundToPx()) }
                .padding(end = 60.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.action_next),
                modifier = Modifier.size(32.dp),
                tint = uiTint
            )
        }

        // 播放列表 - 右下（歌词卡淡出）
        AnimatedVisibility(
            visible = !isLyricsCompact,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            IconButton(
                onClick = onPlaylistClick
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "播放列表",
                    modifier = Modifier.size(24.dp),
                    tint = uiTintVariant
                )
            }
        }

        // 播放/暂停（大按钮，居中；歌词卡平滑下沉贴底）
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(0, playDrop.roundToPx()) }
                .size(72.dp)
                .clip(CircleShape)
                .background(primaryColor)
                .clickable { onPlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

// ==================== 卡片 0：封面卡 ====================

@Composable
private fun PlayerCoverCard(
    song: SongInfo,
    isRoundAlbum: Boolean,
    isRotating: Boolean,
    rotationAngle: Float,
    uiTint: Color,
    uiTintVariant: Color,
    lyricEntries: List<LyricEntry>,
    lyricPreviewTimeMs: Long,
    lyricFontSize: Float,
    lyricFontWeight: FontWeight,
    lyricTextAlign: TextAlign,
    karaokeEnabled: Boolean,
    lyricBlurEnabled: Boolean,
    lyricBlurAmount: Float,
    isPlaying: Boolean,
    isSeeking: Boolean,
    tapCoverToLyrics: Boolean,
    onSeekTo: (Long) -> Unit,
    onOpenLyrics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 封面（自动适配卡片高度，窄屏不溢出）；开启「点击封面切换歌词」时整块可点
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(
                    enabled = tapCoverToLyrics,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenLyrics
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = song,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
                },
                contentKey = { it.filePath },
                label = "albumAnim"
            ) { targetSong ->
                val targetModel: Any = when {
                    targetSong.albumArtUri != null && (targetSong.albumArtUri.startsWith("/") || targetSong.albumArtUri.startsWith("file://")) ->
                        File(targetSong.albumArtUri.removePrefix("file://"))
                    targetSong.albumArtUri != null -> Uri.parse(targetSong.albumArtUri)
                    else -> File(targetSong.filePath)
                }
                val targetPainter = rememberAsyncImagePainter(model = targetModel)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(1f)
                        .clip(
                            if (isRoundAlbum) CircleShape else RoundedCornerShape(16.dp)
                        )
                        .graphicsLayer {
                            if (isRoundAlbum && isRotating) {
                                rotationZ = rotationAngle
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 阴影
                    if (!isRoundAlbum) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .blur(20.dp)
                                .alpha(0.3f)
                                .background(Color.Black)
                        )
                    }

                    Image(
                        painter = targetPainter,
                        contentDescription = "专辑封面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // 默认图标
                    if (targetPainter.state !is coil.compose.AsyncImagePainter.State.Success) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = uiTintVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 歌词预览条（铺满屏幕宽度：突破卡片 24dp 与外层 20dp 的左右 padding，
        // 居左/居右时歌词紧靠屏幕左右边缘；点击翻到歌词卡；无歌词时占位防跳动）
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            // 内层突破父约束铺满全屏宽（44dp = 卡片 24dp + 外层 20dp）：
            // contentAlignment=Center 会让加宽后的 Box 自动居中布局，
            // 起点正好落在屏幕左缘，不能再加 offset（否则整体偏左）
            val overflowDp = 44.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredWidth(maxWidth + overflowDp * 2)
                    .fillMaxHeight()
                    .clickable(
                        enabled = lyricEntries.isNotEmpty(),
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenLyrics
                    ),
                contentAlignment = Alignment.Center
            ) {
            if (lyricEntries.isNotEmpty()) {
                // NeriPlayer 播放页嵌入式歌词预览同款：stableEmbeddedViewport 稳定视口，
                // 当前行垂直居中（centerPad=(视口高-行高)/2 → 行中心=正中，上下对称留白），
                // 逐字点亮 + 边缘渐隐 + 歌词模糊；点击歌词行 = 跳转；
                // 整条空白处点击 = 翻到歌词卡（外层 clickable）
                SyncedLyricsView(
                    lyrics = lyricEntries,
                    currentTimeMs = lyricPreviewTimeMs,
                    lyricOffsetMs = LYRIC_TIME_OFFSET_MS,
                    modifier = Modifier.fillMaxSize(),
                    textColor = uiTint,
                    fontSize = lyricFontSize.sp,
                    fontWeight = lyricFontWeight,
                    textAlign = lyricTextAlign,
                    centerPadding = 8.dp,
                    // 当前行微上移：上面显示比下面多，负值=上移（上方空间减小、下方增大）
                    centerPaddingOffset = (-4).dp,
                    karaokeEnabled = karaokeEnabled,
                    visualEffectsEnabled = false,
                    scaleActiveLine = false,
                    smoothActiveLineProgress = false,
                    edgeFadeHeight = 28.dp,
                    lyricBlurEnabled = lyricBlurEnabled,
                    lyricBlurAmount = lyricBlurAmount,
                    isPlaying = isPlaying,
                    interpolatePlaybackPosition = isPlaying && !isSeeking,
                    playbackSessionKey = song.filePath,
                    stableEmbeddedViewport = true,
                    onLyricClick = { entry -> onSeekTo(entry.startTimeMs) }
                )
            } else {
                Text(
                    text = "歌词加载中...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = uiTintVariant.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
            }
        }
    }
}

// ==================== 辅助组件 ====================

/**
 * 音频参数文本：
 * - 有解码格式（Media3 Format）：采样率 + 位深 + 编码，如 "44.1kHz 16bit FLAC"
 * - 格式三级兜底：解码器名(codecs) > MIME 类型 > 酷狗扩展名(extName)，保证格式必出
 * - 位深仅显示真实 PCM 位深；Float 输出/无效编码时隐藏（Float32 不代表源位深，避免误导）
 * - 无解码格式：回退扩展名 + 码率，如 "FLAC 320kbps"
 */
private fun buildAudioInfoText(
    audioFormat: androidx.media3.common.Format?,
    extName: String?,
    bitRate: Int
): String? {
    if (audioFormat != null && audioFormat.sampleRate > 0) {
        val sampleRate = when (audioFormat.sampleRate) {
            44100 -> "44.1kHz"
            48000 -> "48kHz"
            88200 -> "88.2kHz"
            96000 -> "96kHz"
            192000 -> "192kHz"
            else -> "${audioFormat.sampleRate / 1000}kHz"
        }
        // 真实 PCM 位深；FLOAT/INVALID/DEFAULT 等不显示
        val bitDepth = when (audioFormat.pcmEncoding) {
            C.ENCODING_PCM_8BIT -> "8bit"
            C.ENCODING_PCM_16BIT -> "16bit"
            C.ENCODING_PCM_24BIT -> "24bit"
            C.ENCODING_PCM_32BIT -> "32bit"
            else -> null
        }
        // 格式三级兜底：解码器名 > MIME（raw 跳过） > 酷狗扩展名
        val mime = audioFormat.sampleMimeType
            ?.substringAfterLast('/')
            ?.uppercase()
            ?.takeIf { it.isNotBlank() && it != "RAW" }
        val format = audioFormat.codecs?.substringAfterLast('/')?.uppercase()?.takeIf { it.isNotBlank() }
            ?: mime
            ?: extName?.uppercase()?.takeIf { it.isNotBlank() }
        return listOfNotNull(sampleRate, bitDepth, format).joinToString(" ").ifBlank { null }
    }
    // 无解码格式信息：回退扩展名 + 码率
    val fallbackFormat = extName?.uppercase()?.takeIf { it.isNotBlank() }
    return listOfNotNull(
        fallbackFormat,
        if (bitRate > 0) "${bitRate / 1000}kbps" else null
    ).joinToString(" ").ifBlank { null }
}

/** 将 LyricEntry 列表还原为 LRC 文本（用于回退样式显示） */
private fun lyricsToText(lyrics: List<LyricEntry>): String {
    return lyrics.joinToString("\n") { line ->
        val m = line.startTimeMs
        val mm = m / 60000
        val ss = (m % 60000) / 1000
        val ms = m % 1000
        "[%02d:%02d.%03d]%s".format(mm, ss, ms, line.text)
    }
}


/**
 * 歌词显示组件 - 高亮当前行（关闭逐字卡拉OK时的回退样式）
 */
@Composable
internal fun LyricsDisplay(
    lyrics: String,
    currentPosition: Long,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Center
) {
    val lines = remember(lyrics) {
        lyrics.lines().mapNotNull { line ->
            parseLrcLine(line)
        }.sortedBy { it.first }
    }

    var currentLineIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentPosition, lines) {
        val idx = lines.indexOfLast { it.first <= currentPosition }
        if (idx >= 0) currentLineIndex = idx
    }

    val listState = rememberLazyListState()

    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            listState.animateScrollToItem(currentLineIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 32.dp)
    ) {
        items(lines.size) { index ->
            val (_, text) = lines[index]
            val isCurrent = index == currentLineIndex
            Text(
                text = text,
                // 注意：textAlign 只放在 style 里（Text 参数会覆盖 style 的同名属性）
                style = (if (isCurrent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium)
                    .copy(fontSize = fontSize, fontWeight = fontWeight, textAlign = textAlign),
                color = if (isCurrent) activeColor else inactiveColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .alpha(if (isCurrent) 1f else 0.6f)
            )
        }
    }
}

/**
 * 播放器菜单 BottomSheet（功能已迁移至 设置-播放器设置，暂时留空）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerMenuSheet(
    onDismiss: () -> Unit,
    // 当前菜单对应的歌词对象（封面卡歌词 / 歌词页歌词），两套设置独立
    targetLabel: String,
    lyricFontSize: Float,
    onLyricFontSizeChange: (Float) -> Unit,
    lyricFontWeight: Float,
    onLyricFontWeightChange: (Float) -> Unit,
    lyricAlignment: String,
    onLyricAlignmentChange: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    // null = 主菜单；"lyrics" = 歌词设置子页（子页退出直接关闭菜单）
    var subPage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {}
    ) {
        if (subPage == null) {
            // ===== 主菜单（主页） =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "播放设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                // 歌词设置入口
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { subPage = "lyrics" }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "歌词设置",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "$targetLabel：字体大小、粗细与排版",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            // ===== 歌词设置子页（退出 = 关闭菜单，直接回播放器主页） =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "退出",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${targetLabel}设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // 字体大小（实时生效）
                Text(
                    text = "字体大小",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = lyricFontSize,
                        onValueChange = onLyricFontSizeChange,
                        valueRange = 14f..50f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${lyricFontSize.roundToInt()} sp",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                // 字体粗细（实时生效，100-900 步进 100）
                Text(
                    text = "字体粗细",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = lyricFontWeight,
                        onValueChange = onLyricFontWeightChange,
                        valueRange = 100f..900f,
                        steps = 7,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${lyricFontWeight.roundToInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                // 歌词排版（居左/居中/居右）
                Text(
                    text = "歌词排版",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("left" to "居左", "center" to "居中", "right" to "居右")
                        .forEach { (value, label) ->
                            val selected = lyricAlignment == value
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier.clickable { onLyricAlignmentChange(value) }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                                )
                            }
                        }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
