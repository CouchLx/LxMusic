package com.example.lxmusic.ui.shelf

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import coil.request.ImageRequest
import com.example.lxmusic.data.SettingsRepository
import com.example.lxmusic.model.SongInfo

/**
 * CD 唱片架的 Compose 外壳：顶部搜索/标题/返回、底部喜欢/帮我挑一首/选项。
 *
 * 结构对照 CurrentMusicX 的 PlaylistRecordShelf.kt（GPL-3.0-only），
 * 玻璃按钮与原生播放器换成 Material3 + 独立试听播放器。
 */
@Composable
internal fun PlaylistRecordShelf(
    songs: List<SongInfo>,
    canPlay: Boolean,
    onPlay: (Int) -> Unit,
    onClose: () -> Unit,
    onToggleLike: ((SongInfo) -> Unit)? = null,
    isLiked: (SongInfo) -> Boolean = { false },
    currentPlayingPath: String? = null,
    collectionKey: String = "",
    // 自定义「确认播放」：播放队列自己用，直接切队列下标 + 试听位置，不重建队列
    onCommit: ((index: Int, startPositionMs: Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val imageLoader = LocalImageLoader.current
    val owner = LocalLifecycleOwner.current
    val renderer = remember(context, collectionKey) { RecordShelfView(context) }
    // 试听要压住主播放器，统一走 ShelfPlaybackBridge（MainActivity 注册）
    val preview = remember(renderer) { RecordShelfPreview(context) }

    var selection by remember(renderer) { mutableIntStateOf(0) }
    var displayedSongs by remember(renderer) { mutableStateOf(songs) }
    var replacingResults by remember(renderer) { mutableStateOf(false) }
    var query by rememberSaveable(collectionKey) { mutableStateOf("") }
    var searchOpen by rememberSaveable(collectionKey) { mutableStateOf(false) }
    var optionsOpen by rememberSaveable(collectionKey) { mutableStateOf(false) }
    var picking by remember(renderer) { mutableStateOf(false) }
    var appeared by remember(renderer) { mutableStateOf(false) }
    // 一进架子就停掉外面的歌，等第一张唱片抽出来再试听
    LaunchedEffect(preview) { preview.begin() }

    // 试听选项跟设置仓库走，下次进架子保持上次选择
    val settings = remember(context) { SettingsRepository(context.applicationContext) }
    var fullPreview by remember { mutableStateOf(settings.recordShelfFullPreview) }
    var previewMode by remember { mutableStateOf(recordShelfPreviewModeOf(settings.recordShelfPreviewMode)) }
    val setFullPreview: (Boolean) -> Unit = { value ->
        fullPreview = value
        settings.recordShelfFullPreview = value
    }
    val setPreviewMode: (RecordShelfPreviewMode) -> Unit = { value ->
        previewMode = value
        settings.recordShelfPreviewMode = value.name.lowercase()
    }
    LaunchedEffect(renderer) { appeared = true }
    val controlsAlpha by animateFloatAsState(if (appeared) 1f else 0f, tween(500), label = "shelf controls")
    val titleAlpha by animateFloatAsState(if (picking) .4f else 1f, tween(200), label = "shelf title")

    val filtered = remember(songs, query) {
        songs.filter {
            query.isBlank() || listOf(it.title, it.artist).any { text -> text.contains(query.trim(), true) }
        }
    }
    val selected = displayedSongs.getOrNull(selection)
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density).toFloat()

    val activate by rememberUpdatedState<(Int) -> Unit>({ index ->
        if (canPlay && !replacingResults) displayedSongs.getOrNull(index)?.let { song ->
            val real = songs.indexOfFirst { it.filePath == song.filePath }
            if (real >= 0) {
                // 试听到哪就从哪继续，不要从头再放一遍
                val startPosition = preview.commit()
                val commit = onCommit
                val playAt = ShelfPlaybackBridge.playSongAt
                when {
                    commit != null -> commit(real, startPosition)
                    playAt != null -> playAt(songs, real, startPosition)
                    else -> onPlay(real)
                }
                // 选完就回播放页，不用再手动关架子
                onClose()
            }
        }
    })
    val currentSelection by rememberUpdatedState<(Int) -> Unit>({ index ->
        selection = index
    })
    val currentPreview by rememberUpdatedState<(Int?) -> Unit>({ index ->
        preview.select(if (canPlay) index?.let(displayedSongs::getOrNull) else null)
    })
    val currentSongsChanged by rememberUpdatedState<(List<SongInfo>) -> Unit>({ displayedSongs = it })
    val advancePreview by rememberUpdatedState<(SongInfo) -> Unit>({ finished ->
        if (!replacingResults && previewMode in setOf(RecordShelfPreviewMode.NEXT, RecordShelfPreviewMode.RANDOM) &&
            canPlay && selected?.filePath == finished.filePath
        ) {
            nextRecordPreviewIndex(displayedSongs, selection, previewMode == RecordShelfPreviewMode.RANDOM)
                ?.let { renderer.pick(it) }
        }
    })

    SideEffect { preview.onFinished = { selected?.let(advancePreview) } }

    DisposableEffect(renderer, owner) {
        renderer.onSelection = { currentSelection(it) }
        renderer.onSongsChanged = { currentSongsChanged(it) }
        renderer.onResultsChangingChanged = { replacingResults = it }
        renderer.onPreview = { currentPreview(it) }
        renderer.onActivate = { activate(it) }
        renderer.onPickingChanged = { picking = it }
        val observer = LifecycleEventObserver { _, _ ->
            renderer.setRunning(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            renderer.setRunning(false)
        }
    }

    DisposableEffect(renderer) {
        onDispose {
            renderer.dispose()
            preview.close()
        }
    }

    SideEffect {
        renderer.imageLoader = imageLoader
        renderer.setRunning(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        renderer.configure(filtered, searching = query.isNotBlank())
        displayedSongs = renderer.displayedSongs
        replacingResults = renderer.replacingResults
        preview.setFullLength(fullPreview)
        preview.setMode(previewMode)
    }

    LaunchedEffect(currentPlayingPath, displayedSongs.size) {
        val index = currentPlayingPath?.let { path -> displayedSongs.indexOfFirst { it.filePath == path } } ?: -1
        if (index >= 0) {
            selection = renderer.restoreSelection(displayedSongs[index].filePath)
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (searchOpen) searchOpen = false
        else if (optionsOpen) optionsOpen = false
        else onClose()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF102132))) {
        val screenWidth = maxWidth
        val safeInsets = WindowInsets.safeDrawing
        val footerWidth = screenWidth.coerceAtMost(420.dp)
        val footerSize = (footerWidth * .132f).coerceIn(48.dp, 56.dp)
        val footerMargin = 22.dp
        val footerInset = with(density) { (footerSize + footerMargin).toPx() } + safeInsets.getBottom(density)

        // 背景：当前唱片封面的模糊放大
        Crossfade(
            targetState = selected?.albumArtUri.orEmpty(),
            modifier = Modifier.matchParentSize(),
            animationSpec = tween(450),
            label = "shelf cover background"
        ) { cover ->
            Box(Modifier.fillMaxSize().background(Color(0xFF102132))) {
                if (cover.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(cover).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().blur(48.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x99102132),
                                    Color(0xCC0B1622),
                                    Color(0xF20B131C)
                                )
                            )
                        )
                )
            }
        }

        key(renderer) {
            AndroidView(
                factory = { renderer },
                update = {
                    it.topInset = top
                    it.bottomInset = footerInset
                    it.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 顶部：搜索 / 歌名 / 返回
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = controlsAlpha }
                .windowInsetsPadding(safeInsets.only(WindowInsetsSides.Horizontal))
                .statusBarsPadding()
                .padding(horizontal = screenWidth * .04f, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShelfCircle(label = "搜索歌曲", onClick = { searchOpen = true }) {
                Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = Color.White)
            }
            Column(
                Modifier.weight(1f).padding(top = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = selected?.title ?: "没有匹配的歌曲",
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .graphicsLayer { alpha = titleAlpha }
                        .blur(if (picking) 3.dp else 0.dp)
                )
                Text(
                    text = selected?.artist.orEmpty(),
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .graphicsLayer { alpha = titleAlpha },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = .6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            ShelfCircle(label = "返回歌曲列表", onClick = onClose) {
                Icon(Icons.Rounded.Close, null, Modifier.size(21.dp), tint = Color.White)
            }
        }

        // 底部：喜欢 / 帮我挑一首 / 选项
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = controlsAlpha }
                .windowInsetsPadding(safeInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(bottom = footerMargin)
                .width(footerWidth)
                .padding(horizontal = footerWidth * .05f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ShelfCircle(
                label = if (selected?.let(isLiked) == true) "取消喜欢" else "喜欢当前歌曲",
                enabled = selected != null && onToggleLike != null && !replacingResults,
                size = footerSize,
                onClick = { selected?.let { onToggleLike?.invoke(it) } }
            ) {
                Icon(
                    imageVector = if (selected?.let(isLiked) == true) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFFFF4D6D)
                )
            }
            RecordShelfPickButton(
                modifier = Modifier.width(footerWidth * .432f).height(footerSize),
                enabled = displayedSongs.isNotEmpty() && !picking && !replacingResults,
                onClick = {
                    val index = if (displayedSongs.size <= 1) 0 else {
                        val choice = (0 until displayedSongs.lastIndex).random()
                        if (choice >= selection) choice + 1 else choice
                    }
                    renderer.pick(index)
                }
            ) {
                Icon(Icons.Rounded.Shuffle, null, Modifier.size(21.dp), tint = Color(0xFF0E1420))
                Text("帮我挑一首", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0E1420))
            }
            ShelfCircle(label = "唱片架选项", onClick = { optionsOpen = true }, size = footerSize) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(24.dp), tint = Color.White)
            }
        }
    }

    if (optionsOpen) {
        AlertDialog(
            onDismissRequest = { optionsOpen = false },
            title = { Text("唱片架") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShelfSwitchRow(
                        title = "整首试听",
                        summary = "从头播放整首歌曲，关闭后从 35% 处试听片段",
                        checked = fullPreview,
                        onCheckedChange = setFullPreview
                    )
                    ShelfSwitchRow(
                        title = if (fullPreview) "循环试听整首歌" else "循环试听片段",
                        summary = "重复播放当前唱片",
                        checked = previewMode == RecordShelfPreviewMode.LOOP,
                        onCheckedChange = {
                            setPreviewMode(if (it) RecordShelfPreviewMode.LOOP else RecordShelfPreviewMode.ONCE)
                        }
                    )
                    ShelfSwitchRow(
                        title = "试听结束自动下一首",
                        summary = if (previewMode == RecordShelfPreviewMode.RANDOM) "结束后随机抽出另一张唱片"
                        else "按当前列表顺序试听，最后一首后回到开头",
                        checked = previewMode in setOf(RecordShelfPreviewMode.NEXT, RecordShelfPreviewMode.RANDOM),
                        onCheckedChange = {
                            setPreviewMode(if (it) RecordShelfPreviewMode.NEXT else RecordShelfPreviewMode.ONCE)
                        }
                    )
                    ShelfSwitchRow(
                        title = "自动随机切换",
                        summary = "随机抽出另一张唱片并试听",
                        checked = previewMode == RecordShelfPreviewMode.RANDOM,
                        enabled = previewMode in setOf(RecordShelfPreviewMode.NEXT, RecordShelfPreviewMode.RANDOM),
                        onCheckedChange = {
                            setPreviewMode(if (it) RecordShelfPreviewMode.RANDOM else RecordShelfPreviewMode.NEXT)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { optionsOpen = false; onClose() }) { Text("返回歌曲列表") }
            },
            dismissButton = {
                TextButton(onClick = { optionsOpen = false }) { Text("关闭") }
            }
        )
    }

    if (searchOpen) {
        AlertDialog(
            onDismissRequest = { searchOpen = false },
            title = { Text("搜索唱片") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("歌名或歌手") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${filtered.size} 首歌曲", fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { searchOpen = false }) { Text("查看唱片") }
            },
            dismissButton = {
                if (query.isNotBlank()) {
                    TextButton(onClick = { query = "" }) { Text("清空") }
                } else {
                    TextButton(onClick = { searchOpen = false }) { Text("取消") }
                }
            }
        )
    }
}

@Composable
private fun ShelfCircle(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) .16f else .07f))
            .clickable(enabled = enabled, onClickLabel = label) { onClick() },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun ShelfSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
            )
            Text(
                summary,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** 设置里存的是小写字符串，读不到时回到 ONCE */
private fun recordShelfPreviewModeOf(raw: String): RecordShelfPreviewMode =
    runCatching { RecordShelfPreviewMode.valueOf(raw.uppercase()) }
        .getOrDefault(RecordShelfPreviewMode.ONCE)
