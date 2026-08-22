package com.example.lxmusic.ui.pages

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.lxmusic.ui.components.IOLoadingIndicator
import com.example.lxmusic.ui.components.SongContextMenuActions
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.RankItem
import com.example.lxmusic.RankSong
import com.example.lxmusic.model.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun RankDetailPage(
    rank: RankItem,
    onBack: () -> Unit,
    onPlaySong: (List<SongInfo>, Int) -> Unit,
    currentPlayingPath: String? = null,
    isPlaying: Boolean = false,
    onLocateReady: (() -> Unit) -> Unit = {},
    onAddToQueueNext: (SongInfo) -> Unit = {},
) {
    val context = LocalContext.current
    val songs = remember { mutableStateListOf<SongInfo>() }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalSongs by remember { mutableIntStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var noMoreData by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val gson = remember { com.google.gson.Gson() }
    val prefs = remember { context.getSharedPreferences("rank_cache", android.content.Context.MODE_PRIVATE) }
    var showRankNumbers by remember { mutableStateOf(prefs.getBoolean("show_rank_numbers", true)) }
    val cacheKey = "rank_songs_${rank.rankid}"
    val pageSize = 30

    fun mapSong(song: RankSong) = SongInfo(
        title = song.title,
        artist = song.artist,
        filePath = "${song.hash}|${song.album_audio_id}",
        albumArtUri = song.coverUrl,
        duration = song.durationMs
    )

    fun loadPage(page: Int, append: Boolean = false) {
        if (append) isLoadingMore = true else if (songs.isEmpty()) isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val resp = KuGouApi.service.getRankAudio(rank.rankid, page, pageSize)
                val list = resp.data?.songlist
                if (list != null) {
                    val mapped = list.map { mapSong(it) }
                    if (append) songs.addAll(mapped) else {
                        songs.clear()
                        songs.addAll(mapped)
                    }
                    totalSongs = resp.data.total
                    if (list.size < pageSize) noMoreData = true
                    prefs.edit()
                        .putString(cacheKey, gson.toJson(songs.toList()))
                        .putInt("${cacheKey}_total", resp.data.total)
                        .apply()
                } else noMoreData = true
                loadError = false
            } catch (_: Exception) {
                if (songs.isEmpty()) loadError = true
            } finally {
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    // 后台静默加载全部歌曲（用于播放全部）
    suspend fun loadAllSongs(): List<SongInfo> = withContext(Dispatchers.IO) {
        val allSongs = mutableListOf<SongInfo>()
        allSongs.addAll(songs)
        if (totalSongs <= songs.size) return@withContext allSongs

        val totalPages = (totalSongs + pageSize - 1) / pageSize
        val remainingPages = (2..totalPages).toList()

        // 分批并行加载，每批5个请求
        val batchSize = 5
        remainingPages.chunked(batchSize).forEach { batch ->
            val jobs = batch.map { page ->
                async {
                    try {
                        val resp = KuGouApi.service.getRankAudio(rank.rankid, page, pageSize)
                        resp.data?.songlist?.map { mapSong(it) } ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                }
            }
            val results = jobs.awaitAll()
            results.forEach { list -> allSongs.addAll(list) }
        }
        allSongs
    }

    val imageLoader = coil.compose.LocalImageLoader.current

    LaunchedEffect(rank.rankid) {
        val cached = prefs.getString(cacheKey, null)
        if (!cached.isNullOrBlank()) {
            try {
                val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, SongInfo::class.java).type
                val list: List<SongInfo> = gson.fromJson(cached, type)
                if (list.isNotEmpty()) {
                    songs.clear()
                    songs.addAll(list)
                    totalSongs = prefs.getInt("${cacheKey}_total", 0)
                    isLoading = false
                    // 预加载封面到内存缓存
                    list.forEach { song ->
                        val url = song.albumArtUri
                        if (!url.isNullOrBlank()) {
                            val request = ImageRequest.Builder(context)
                                .data(url)
                                .memoryCacheKey(url)
                                .build()
                            imageLoader.enqueue(request)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        loadPage(1)
    }

    val listState = rememberLazyListState()

    // 定位到当前播放歌曲
    fun locateToCurrentSong() {
        val targetIndex = songs.indexOfFirst { it.filePath == currentPlayingPath }
        if (targetIndex >= 0) {
            scope.launch { listState.animateScrollToItem(targetIndex) }
        }
    }
    LaunchedEffect(rank.rankid) { onLocateReady(::locateToCurrentSong) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3 && !isLoadingMore && !noMoreData && songs.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            currentPage++
            loadPage(currentPage, append = true)
        }
    }

    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "contentAlpha"
    )
    val contentOffsetY by animateFloatAsState(
        targetValue = if (contentVisible) 0f else with(LocalDensity.current) { 30.dp.toPx() },
        animationSpec = tween(durationMillis = 400),
        label = "contentOffsetY"
    )

    Box(modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
            alpha = contentAlpha
            translationY = contentOffsetY
        }

    ) {
        if (isLoading && songs.isEmpty()) {
            CircularWavyProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (loadError && songs.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("加载失败", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { isLoading = true; loadPage(1) }) { Text("重试") }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val imgUrl = rank.coverUrl
                        val cardSize = 220.dp
                        if (imgUrl.isNotBlank()) {
                            val painter = rememberAsyncImagePainter(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imgUrl)
                                    .memoryCacheKey(imgUrl)
                                    .crossfade(150)
                                    .build()
                            )
                            Image(
                                painter = painter,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(cardSize)
                                    .clip(RoundedCornerShape(24.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(cardSize)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MusicNote, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(rank.rankname ?: "排行榜", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (totalSongs > 0) "共 $totalSongs 首" else " ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var isLoadingAll by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                if (songs.isNotEmpty() && !isLoadingAll) {
                                    scope.launch {
                                        isLoadingAll = true
                                        val allSongs = loadAllSongs()
                                        isLoadingAll = false
                                        if (allSongs.isNotEmpty()) {
                                            onPlaySong(allSongs, 0)
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            enabled = !isLoadingAll
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isLoadingAll) "加载中..." else "播放全部")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            showRankNumbers = !showRankNumbers
                            prefs.edit().putBoolean("show_rank_numbers", showRankNumbers).apply()
                        }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "隐藏排序",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                itemsIndexed(songs, key = { _, song -> song.filePath }) { index, song ->
                    var showSheet by remember { mutableStateOf(false) }

                    RankSongCard(
                        rank = index + 1,
                        song = song,
                        showRank = showRankNumbers,
                        isCurrentSong = song.filePath == currentPlayingPath,
                        isPlaying = isPlaying && song.filePath == currentPlayingPath,
                        onClick = { onPlaySong(songs.toList(), index) },
                        onMenuClick = { showSheet = true }
                    )

                    if (showSheet) {
                        ModalBottomSheet(
                            onDismissRequest = { showSheet = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val coverUrl = song.albumArtUri
                                if (!coverUrl.isNullOrBlank()) {
                                    val painter = rememberAsyncImagePainter(
                                        model = ImageRequest.Builder(context).data(coverUrl).memoryCacheKey(coverUrl).crossfade(150).build()
                                    )
                                    Image(painter, null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                } else {
                                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.MusicNote, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                            SongContextMenuActions(song = song, onDismiss = { showSheet = false }, onAddToQueueNext = { onAddToQueueNext(song) })
                        }
                    }
                }
                if (isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            IOLoadingIndicator(Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RankSongCard(
    rank: Int,
    song: SongInfo,
    showRank: Boolean = true,
    isCurrentSong: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onMenuClick: (() -> Unit)? = null,
    selectMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null
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

    val cardBg = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        isCurrentSong -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable { onClick() }
                }
            ),
        color = cardBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (showRank) 16.dp else 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 排名
            if (showRank) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isCurrentSong -> MaterialTheme.colorScheme.primary
                        rank == 1 -> Color(0xFFFF6B6B)
                        rank == 2 -> Color(0xFFFF9F43)
                        rank == 3 -> Color(0xFFFECA57)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.width(32.dp)
                )
            }
            // 封面
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                val coverUrl = song.albumArtUri
                // 本地歌曲无封面时回退到音频文件本身（Coil 自动提取内嵌封面），与本地音乐列表一致
                val coverModel: Any? = when {
                    !coverUrl.isNullOrBlank() -> coverUrl
                    song.filePath.startsWith("/") -> java.io.File(song.filePath)
                    else -> null
                }
                if (coverModel != null) {
                    val painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverModel)
                            .memoryCacheKey(coverModel.toString())
                            .crossfade(150)
                            .size(200)
                            .build()
                    )
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isCurrentSong) Modifier.graphicsLayer { alpha = 0.5f } else Modifier),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 正在播放：音阶动画 或 暂停图标
                if (isCurrentSong) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaying) {
                            // 音阶动画
                            Row(
                                modifier = Modifier.height(20.dp),
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
                                contentDescription = "暂停",
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            // 歌曲信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                           else if (isCurrentSong) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                           else if (isCurrentSong) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 选择模式：右侧显示圆润 Checkbox
            if (selectMode) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val scale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.9f,
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f),
                        label = "checkScale"
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .then(
                                if (!isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "已选择",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            } else if (onMenuClick != null) {
                // 正常模式：菜单按钮
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
