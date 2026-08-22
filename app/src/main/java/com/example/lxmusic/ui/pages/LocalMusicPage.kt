@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.lxmusic.ui.pages

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.lxmusic.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import coil.compose.LocalImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.lxmusic.MusicDatabase
import com.example.lxmusic.SongDao
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.ui.components.IOLoadingIndicator
import com.example.lxmusic.ui.components.SongContextMenuActions
import com.example.lxmusic.ui.components.SortBottomSheet
import com.example.lxmusic.util.fullScanAndSave
import com.example.lxmusic.util.loadCachedSongs
import com.example.lxmusic.util.scanForNewSongs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ==================== 本地音乐页面 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicPage(
    currentPlayingPath: String? = null,
    isPlaying: Boolean = false,
    onPlaySong: (List<SongInfo>, Int) -> Unit = { _, _ -> },
    showSidePanel: Boolean = false,
    onShowSidePanelChange: (Boolean) -> Unit = {},
    panelOffset: Animatable<Float, AnimationVector1D>? = null,
    panelWidthPx: Float? = null,
    scanTrigger: Int = 0,
    locatePlayingSong: Int = 0,
    onAddToQueueNext: (SongInfo) -> Unit = {}
) {
    // 顶栏与面板状态由 MainActivity 提升；未传入时（如预览）回退到内部状态
    val actualPanelOffset = panelOffset ?: remember { Animatable(0f) }
    val actualPanelWidthPx = panelWidthPx ?: with(LocalDensity.current) { 200.dp.toPx() }
    val context = LocalContext.current
    val sysNavBarHeight = remember {
        val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }
    val sysNavBarDp = with(LocalDensity.current) { sysNavBarHeight.toDp() }
    var songs by remember { mutableStateOf<List<SongInfo>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isScanning by remember { mutableStateOf(false) }

    // 排序状态（从 SharedPreferences 恢复）
    val prefs = remember { context.getSharedPreferences("local_music_sort", android.content.Context.MODE_PRIVATE) }
    var sortType by remember { mutableIntStateOf(prefs.getInt("sort_type", 0)) }
    var reverseSort by remember { mutableStateOf(prefs.getBoolean("reverse_sort", false)) }
    var groupType by remember { mutableIntStateOf(prefs.getInt("group_type", 0)) }
    var showSortSheet by remember { mutableStateOf(false) }
    var contextSong by remember { mutableStateOf<SongInfo?>(null) }

    // 保存排序设置
    LaunchedEffect(sortType, reverseSort, groupType) {
        prefs.edit()
            .putInt("sort_type", sortType)
            .putBoolean("reverse_sort", reverseSort)
            .putInt("group_type", groupType)
            .apply()
    }

    // 排序：0=标题 1=艺术家 2=专辑（暂时用文件路径目录近似专辑）
    val sortedSongs = remember(songs, sortType, reverseSort) {
        val sorted = when (sortType) {
            1 -> songs.sortedBy { it.artist.lowercase() }
            2 -> songs.sortedBy { it.title.lowercase() }
            else -> songs.sortedBy { it.title.lowercase() } // 0=标题
        }
        if (reverseSort) sorted.reversed() else sorted
    }

    // 分组后的歌曲（保留扁平结构，分组用 key 前缀）
    val groupedSongs = remember(sortedSongs, groupType) {
        when (groupType) {
            1 -> sortedSongs.sortedBy { it.artist.lowercase() }  // 按艺术家
            2 -> sortedSongs.sortedBy { it.albumArtUri ?: "" }   // 按专辑（近似）
            3 -> sortedSongs.sortedBy { File(it.filePath).parent ?: "" } // 按文件夹
            else -> sortedSongs
        }
    }
    val displaySongs = groupedSongs

    val db = remember { MusicDatabase.getDatabase(context) }
    val songDao = remember { db.songDao() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val pullRefreshState = rememberPullToRefreshState()
    val imageLoader = LocalImageLoader.current

    // 内容可见性动画状态 - 刷新时隐藏，完成后显示（与首页一致）
    val contentAlpha by animateFloatAsState(
        targetValue = if (isScanning) 0f else 1f,
        animationSpec = tween(durationMillis = 250, easing = FastOutLinearInEasing),
        label = "contentAlpha"
    )

    // 侧边栏「扫描」按钮：MainActivity 通过 scanTrigger 递增触发
    LaunchedEffect(scanTrigger) {
        if (scanTrigger > 0) {
            isScanning = true
            scanForNewSongs(context, songDao) { updatedSongs ->
                songs = updatedSongs; isScanning = false
            }
        }
    }

    // 定位正在播放歌曲：MainActivity 通过 locatePlayingSong 递增触发
    LaunchedEffect(locatePlayingSong) {
        if (locatePlayingSong > 0 && currentPlayingPath != null) {
            val index = displaySongs.indexOfFirst { it.filePath == currentPlayingPath }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }
    }

    // 切换排序后自动滚到顶部
    LaunchedEffect(sortType, reverseSort) {
        if (songs.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // 是否显示回顶按钮
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 3 }
    }

    // 下拉刷新触发扫描
    fun onRefresh() {
        isScanning = true
        scanForNewSongs(context, songDao) { updatedSongs ->
            songs = updatedSongs
            isScanning = false
        }
    }

    // 权限请求
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: false
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        }
        hasPermission = audioPermission
        if (audioPermission) {
            loadContent(songDao, context) { loadedSongs, loading, scanning ->
                songs = loadedSongs
                isLoading = loading
                isScanning = scanning
            }
        } else {
            isLoading = false
        }
    }

    // 初始化加载：有缓存直接读，无缓存才扫描
    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            hasPermission = true
            loadContent(songDao, context) { loadedSongs, loading, scanning ->
                songs = loadedSongs
                isLoading = loading
                isScanning = scanning
            }
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    // 后台静默预加载封面，不阻塞列表显示
    LaunchedEffect(songs) {
        if (songs.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                songs.take(50).forEach { song ->
                    val model: Any = when {
                        song.albumArtUri != null && (song.albumArtUri.startsWith("/") || song.albumArtUri.startsWith("file://")) ->
                            File(song.albumArtUri.removePrefix("file://"))
                        song.albumArtUri != null -> Uri.parse(song.albumArtUri)
                        else -> File(song.filePath)
                    }
                    val request = ImageRequest.Builder(context)
                        .data(model)
                        .size(108, 108)
                        .build()
                    imageLoader.execute(request)
                }
            }
        }
    }

    // 侧边栏实时跟随手势：panelOffset 为 0（收起）~ 面板宽度（展开），拖拽时 snapTo 跟随手指，松手 animateTo 回弹
    val panelWidth = actualPanelWidthPx
    val panelOffset = actualPanelOffset
    var isDragging by remember { mutableStateOf(false) }
    var dragStartOffset by remember { mutableFloatStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }

    // 非拖拽（点菜单/遮罩）时动画到目标位置；isDragging 变化时重启以取消旧动画
    LaunchedEffect(showSidePanel, isDragging) {
        if (!isDragging) {
            panelOffset.animateTo(
                targetValue = if (showSidePanel) panelWidth else 0f,
                animationSpec = tween(300)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragStartOffset = panelOffset.value
                        totalDragX = 0f
                    },
                    onDragEnd = {
                        val shouldOpen = (dragStartOffset + totalDragX) > panelWidth / 2
                        onShowSidePanelChange(shouldOpen)
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                ) { _, dragAmount ->
                    totalDragX += dragAmount
                    coroutineScope.launch {
                        panelOffset.snapTo((dragStartOffset + totalDragX).coerceIn(0f, panelWidth))
                    }
                }
            }
    ) {
        // 右侧内容（全宽，正常布局，不被压缩；面板展开时整体右移，右侧被裁剪）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(panelOffset.value.roundToInt(), 0) }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    LocalMusicStatsBar(
                        songCount = songs.size,
                        onShufflePlay = { if (songs.isNotEmpty()) onPlaySong(songs.shuffled(), 0) },
                        onSortClick = { showSortSheet = true }
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            isLoading -> LoadingState()
                            !hasPermission -> PermissionState(
                                onRequestPermission = {
                                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                        arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                                    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                    requestPermissionLauncher.launch(perms)
                                }
                            )
                            songs.isEmpty() && !isScanning -> EmptyState()
                            else -> PullToRefreshBox(
                                isRefreshing = isScanning,
                                onRefresh = ::onRefresh,
                                state = pullRefreshState,
                                modifier = Modifier.fillMaxSize(),
                                indicator = {
                                    PullToRefreshDefaults.LoadingIndicator(
                                        state = pullRefreshState,
                                        isRefreshing = isScanning,
                                        modifier = Modifier.align(Alignment.TopCenter)
                                    )
                                }
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(contentAlpha),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(items = displaySongs, key = { it.filePath }) { song ->
                                        SongCard(
                                            song = song,
                                            isCurrentSong = song.filePath == currentPlayingPath,
                                            isPlaying = isPlaying && song.filePath == currentPlayingPath,
                                            onClick = { onPlaySong(displaySongs, displaySongs.indexOf(song)) },
                                            onMoreClick = { contextSong = song }
                                        )
                                    }
                                    item { Spacer(modifier = Modifier.height(80.dp)) }
                                }
                            }
                        }
                    }
                }
                // 面板展开或拖动中：右侧冻结（透明遮罩拦截所有点击）
                if (showSidePanel || isDragging) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onShowSidePanelChange(false) }
                    )
                }
            }
        }

        // 回到顶部按钮（纯色，背景图下更清晰）
        if (showScrollToTop && !showSidePanel) {
            SmallFloatingActionButton(
                onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 96.dp + sysNavBarDp).size(44.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "回到顶部", modifier = Modifier.size(22.dp))
            }
        }
    }

    // 排序面板
    if (showSortSheet) {
        SortBottomSheet(
            currentSortType = sortType,
            reverseSort = reverseSort,
            currentGroupType = groupType,
            onSortTypeChange = { sortType = it },
            onReverseChange = { reverseSort = it },
            onGroupTypeChange = { groupType = it },
            onDismiss = { showSortSheet = false }
        )
    }

    // 歌曲操作菜单（与其它页面一致：surface 背景 + 圆角 + 歌曲头部，仅播放下一首，无收藏/歌单）
    contextSong?.let { song ->
        ModalBottomSheet(
            onDismissRequest = { contextSong = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (song.albumArtUri != null && (song.albumArtUri.startsWith("/") || song.albumArtUri.startsWith("file://"))) {
                    Image(
                        painter = rememberAsyncImagePainter(model = File(song.albumArtUri.removePrefix("file://"))),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SongContextMenuActions(
                song = song,
                onDismiss = { contextSong = null },
                onAddToQueueNext = { onAddToQueueNext(song) },
                showCollectionActions = false,
                showOnlyPlaylistAdd = true
            )
        }
    }
}

// ==================== 统计栏（图三） ====================

@Composable
private fun LocalMusicStatsBar(
    songCount: Int,
    onShufflePlay: () -> Unit,
    onSortClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：随机播放
        Surface(
            onClick = onShufflePlay,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.height(36.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = stringResource(R.string.local_shuffle_play),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$songCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 右侧：排序按钮
        Surface(
            onClick = onSortClick,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.height(36.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = stringResource(R.string.local_sort),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.local_sort),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== 加载内容辅助函数 ====================

private fun loadContent(
    songDao: SongDao,
    context: android.content.Context,
    onResult: (songs: List<SongInfo>, isLoading: Boolean, isScanning: Boolean) -> Unit
) {
    loadCachedSongs(songDao) { cachedSongs ->
        if (cachedSongs.isNotEmpty()) {
            // 有缓存：直接显示，不做任何扫描
            onResult(cachedSongs, false, false)
        } else {
            // 无缓存：首次全量扫描
            onResult(emptyList(), true, false)
            fullScanAndSave(context, songDao) { allSongs ->
                onResult(allSongs, false, false)
            }
        }
    }
}

// ==================== 顶部栏 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicTopBar(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onLocateClick: () -> Unit = {},
    isMenuOpen: Boolean = false,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.local_songs_title))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        ),
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = if (isMenuOpen) Icons.Default.Close else Icons.Default.Menu,
                    contentDescription = stringResource(R.string.local_menu)
                )
            }
        },
        actions = {
            IconButton(onClick = onLocateClick) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "定位正在播放",
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.title_search)
                )
            }
        }
    )
}

// ==================== 歌曲卡片 ====================

@Composable
private fun SongCard(
    song: SongInfo,
    isCurrentSong: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit = {},
    onMoreClick: () -> Unit = {}
) {
    // 音阶动画
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar3"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrentSong)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        else
            Color.Transparent,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 专辑封面（带播放状态）
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                AlbumCover(filePath = song.filePath, albumArtUri = song.albumArtUri)
                // 当前歌曲：变灰 + 动画/图标
                if (isCurrentSong) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaying) {
                            Row(
                                modifier = Modifier.height(18.dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                listOf(bar1, bar2, bar3).forEach { h ->
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .fillMaxHeight(h)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(Color.White)
                                    )
                                }
                            }
                        } else {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 歌曲信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentSong)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentSong)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 更多操作按钮
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== 专辑封面（Coil 异步加载） ====================

@Composable
private fun AlbumCover(filePath: String, albumArtUri: String? = null) {
    // 优先用文件路径（最快），其次 content:// URI，最后回退到音频文件路径
    val model: Any = when {
        albumArtUri != null && (albumArtUri.startsWith("/") || albumArtUri.startsWith("file://")) ->
            File(albumArtUri.removePrefix("file://"))
        albumArtUri != null -> Uri.parse(albumArtUri)
        else -> File(filePath)
    }
    val painter = rememberAsyncImagePainter(model = model)

    Surface(
        modifier = Modifier.size(54.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 底层：音乐图标
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 上层：异步封面
            Image(
                painter = painter,
                contentDescription = "专辑封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// ==================== 状态页面 ====================

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IOLoadingIndicator(
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "正在扫描音乐...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionState(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "需要存储权限",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "授权后即可扫描本地音乐文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
            ) {
                Text("授权访问")
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicOff,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "未找到本地音乐",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请将音乐文件放入 Music 或 Downloads 文件夹",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
