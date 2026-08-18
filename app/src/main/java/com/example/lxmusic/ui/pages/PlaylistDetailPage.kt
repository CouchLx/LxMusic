package com.example.lxmusic.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.lxmusic.ui.components.IOLoadingIndicator
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.MusicDatabase
import com.example.lxmusic.PlaylistSongCrossRef
import com.example.lxmusic.PlaylistTrackSong
import com.example.lxmusic.UserPlaylistEntity
import com.example.lxmusic.UserPlaylistItem
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.ui.components.SongContextMenuActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==================== 歌单详情页 ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistDetailPage(
    playlist: UserPlaylistItem,
    onBack: () -> Unit,
    onPlaySong: (List<SongInfo>, Int) -> Unit,
    currentPlayingPath: String? = null,
    isPlaying: Boolean = false,
    onLocateReady: (() -> Unit) -> Unit = {},
    onAddToQueueNext: (SongInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val songs = remember { mutableStateListOf<SongInfo>() }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalSongs by remember { mutableIntStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var noMoreData by remember { mutableStateOf(false) }
    val pageSize = 30

    fun mapSong(song: PlaylistTrackSong): SongInfo {
        android.util.Log.d("LxMusic", "PlaylistTrack: name='${song.name}', hash=${song.hash}, audio_id=${song.audio_id}, mixsongid=${song.mixsongid}, title=${song.title}, artist=${song.artist}")
        return SongInfo(
            title = song.title,
            artist = song.artist,
            filePath = "${song.hash}|${song.mixsongid}",
            albumArtUri = song.coverUrl,
            duration = song.durationMs
        )
    }

    fun loadPage(page: Int, append: Boolean = false) {
        if (append) isLoadingMore = true else isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val resp = KuGouApi.service.getPlaylistTracksNew(playlist.listid, page, pageSize)
                val list = resp.data?.info
                if (list != null) {
                    val mapped = list.map { mapSong(it) }
                    if (append) songs.addAll(mapped) else {
                        songs.clear()
                        songs.addAll(mapped)
                    }
                    totalSongs = resp.data?.count ?: 0
                    if (list.size < pageSize) noMoreData = true
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
                        val resp = KuGouApi.service.getPlaylistTracksNew(playlist.listid, page, pageSize)
                        resp.data?.info?.map { mapSong(it) } ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                }
            }
            val results = jobs.awaitAll()
            results.forEach { list -> allSongs.addAll(list) }
        }
        allSongs
    }

    val listState = rememberLazyListState()

    // 定位到当前播放歌曲
    fun locateToCurrentSong() {
        val targetIndex = songs.indexOfFirst { it.filePath == currentPlayingPath }
        if (targetIndex >= 0) {
            scope.launch { listState.animateScrollToItem(targetIndex) }
        }
    }
    // 暴露定位函数
    LaunchedEffect(playlist.listid) { onLocateReady(::locateToCurrentSong) }

    LaunchedEffect(playlist.listid) {
        loadPage(1)
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
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
                            .padding(top = 64.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val imgUrl = playlist.coverUrl
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
                        Text(playlist.listname ?: "歌单详情", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                    }
                }
                itemsIndexed(songs, key = { _, song -> song.filePath }) { index, song ->
                    RankSongCard(
                        rank = index + 1,
                        song = song,
                        showRank = false,
                        isCurrentSong = song.filePath == currentPlayingPath,
                        isPlaying = isPlaying && song.filePath == currentPlayingPath,
                        onClick = { onPlaySong(songs.toList(), index) }
                    )
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

// ==================== 搜索歌单详情页 ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchPlaylistDetailPage(
    playlistId: Long,
    playlistName: String,
    coverUrl: String = "",
    authorName: String = "",
    gid: String = "",
    onBack: () -> Unit,
    onPlaySong: (List<SongInfo>, Int) -> Unit,
    currentPlayingPath: String? = null,
    isPlaying: Boolean = false,
    onLocateReady: (() -> Unit) -> Unit = {},
    onAddToQueueNext: (SongInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val songs = remember { mutableStateListOf<SongInfo>() }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalSongs by remember { mutableIntStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var noMoreData by remember { mutableStateOf(false) }
    val pageSize = 30
    val listState = rememberLazyListState()

    fun loadPage(page: Int, append: Boolean = false) {
        if (append) isLoadingMore = true else if (songs.isEmpty()) isLoading = true
        scope.launch(Dispatchers.IO) {
        try {
            val (list, count) = KuGouApi.fetchSpecialPlaylistSongs(playlistId, gid.ifBlank { null }, page, pageSize)
            android.util.Log.d("LxMusic", "SearchPlaylistDetail: page=$page, got=${list.size}, total=$count, gid=$gid, specialId=$playlistId")
            if (list.isNotEmpty()) {
                    if (append) {
                        songs.addAll(list)
                    } else {
                        songs.clear()
                        songs.addAll(list)
                    }
                    totalSongs = count
                    if (list.size < pageSize) noMoreData = true
                    loadError = false
                } else {
                    android.util.Log.w("LxMusic", "SearchPlaylistDetail: empty song list returned for gid=$gid, specialId=$playlistId")
                    if (!append && songs.isEmpty()) {
                        loadError = true
                    }
                    noMoreData = true
                }
            } catch (e: Exception) {
                android.util.Log.e("LxMusic", "SearchPlaylistDetail: error", e)
                if (songs.isEmpty()) loadError = true
            } finally {
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    // 后台分批并发加载全部歌曲（用于播放全部）
    suspend fun loadAllSongs(): List<SongInfo> = withContext(Dispatchers.IO) {
        val allSongs = mutableListOf<SongInfo>()
        allSongs.addAll(songs)
        if (totalSongs <= songs.size) return@withContext allSongs

        val totalPages = (totalSongs + pageSize - 1) / pageSize
        val remainingPages = (2..totalPages).toList()

        val batchSize = 5
        remainingPages.chunked(batchSize).forEach { batch ->
            val jobs = batch.map { page ->
                async {
                    try {
                        val (list, _) = KuGouApi.fetchSpecialPlaylistSongs(playlistId, gid.ifBlank { null }, page, pageSize)
                        list
                    } catch (_: Exception) { emptyList() }
                }
            }
            val results = jobs.awaitAll()
            results.forEach { list -> allSongs.addAll(list) }
        }
        allSongs
    }

    LaunchedEffect(playlistId, gid) {
        loadPage(1)
    }

    fun locateToCurrentSong() {
        val targetIndex = songs.indexOfFirst { it.filePath == currentPlayingPath }
        if (targetIndex >= 0) {
            scope.launch { listState.animateScrollToItem(targetIndex) }
        }
    }
    LaunchedEffect(playlistId) { onLocateReady(::locateToCurrentSong) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3 && !isLoadingMore && !noMoreData && songs.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            currentPage++
            loadPage(currentPage, true)
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

    Box(
        modifier = Modifier
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
                        val cardSize = 220.dp
                        if (coverUrl.isNotBlank()) {
                            val painter = rememberAsyncImagePainter(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(coverUrl)
                                    .memoryCacheKey(coverUrl)
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
                        Text(
                            text = playlistName.ifBlank { "歌单详情" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val subtitleText = buildString {
                            if (authorName.isNotBlank()) {
                                append(authorName)
                                append(" · ")
                            }
                            if (totalSongs > 0) {
                                append("共 $totalSongs 首")
                            } else if (songs.isNotEmpty()) {
                                append("已加载 ${songs.size} 首")
                            }
                        }
                        if (subtitleText.isNotBlank()) {
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                    }
                }
                itemsIndexed(songs, key = { _, song -> song.filePath }) { index, song ->
                    var showSheet by remember { mutableStateOf(false) }

                    RankSongCard(
                        rank = index + 1,
                        song = song,
                        showRank = true,
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
                                val itemCoverUrl = song.albumArtUri
                                if (!itemCoverUrl.isNullOrBlank()) {
                                    val painter = rememberAsyncImagePainter(
                                        model = ImageRequest.Builder(context).data(itemCoverUrl).memoryCacheKey(itemCoverUrl).crossfade(150).build()
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

// ==================== 收藏详情页 ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionDetailPage(
    type: String,          // "favorites" 或 "playlist"
    playlistId: Long = 0,
    onBack: () -> Unit,
    onPlaySong: (List<SongInfo>, Int) -> Unit,
    currentPlayingPath: String? = null,
    isPlaying: Boolean = false,
    onLocateReady: (() -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MusicDatabase.getDatabase(context) }
    val collectionDao = remember { db.collectionDao() }
    val songs = remember { mutableStateListOf<SongInfo>() }
    var isLoading by remember { mutableStateOf(true) }
    var totalSongs by remember { mutableIntStateOf(0) }

    LaunchedEffect(type, playlistId) {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            if (type == "favorites") {
                val collected = collectionDao.getAllCollectedSongs()
                val mapped = collected.map { entity ->
                    SongInfo(
                        title = entity.title, artist = entity.artist,
                        filePath = "${entity.hash}|${entity.audioId}",
                        albumArtUri = entity.albumArtUri, duration = entity.duration,
                        albumId = entity.albumId, mixsongid = entity.mixsongid
                    )
                }
                songs.clear(); songs.addAll(mapped); totalSongs = mapped.size
            } else {
                val playlistSongs = collectionDao.getPlaylistSongs(playlistId)
                val mapped = playlistSongs.map { it.toSongInfo() }
                songs.clear(); songs.addAll(mapped); totalSongs = mapped.size
            }
            isLoading = false
        }
    }

    val listState = rememberLazyListState()

    // 选中模式
    var selectMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var movePlaylists by remember { mutableStateOf<List<UserPlaylistEntity>>(emptyList()) }

    // 定位当前播放
    fun locateToCurrentSong() {
        val idx = songs.indexOfFirst { it.filePath == currentPlayingPath }
        if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
    }
    LaunchedEffect(type, playlistId) { onLocateReady(::locateToCurrentSong) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularWavyProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (songs.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Favorite, null, Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (type == "favorites") "收藏列表为空" else "歌单为空",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                            .padding(top = 64.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(220.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Favorite, null, Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (type == "favorites") "我的收藏" else "歌单",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (selectMode) "已选 ${selectedFiles.size} 首" else "共 $totalSongs 首",
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
                        if (selectMode) {
                            // 选中模式按钮
                            TextButton(
                                onClick = { showDeleteConfirm = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("删除")
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    movePlaylists = collectionDao.getAllUserPlaylists()
                                    withContext(Dispatchers.Main) { showMoveSheet = true }
                                }
                            }) {
                                Icon(Icons.Default.LibraryMusic, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("移动到")
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                selectMode = false
                                selectedFiles = emptySet()
                            }) {
                                Text("取消")
                            }
                        } else {
                            // 正常模式
                            Button(
                                onClick = { if (songs.isNotEmpty()) onPlaySong(songs.toList(), 0) },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("播放全部")
                            }
                            Spacer(Modifier.weight(1f))
                            // 选择按钮
                            IconButton(onClick = { selectMode = true }) {
                                Icon(
                                    Icons.Default.Menu, null, Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                itemsIndexed(songs, key = { _, song -> song.filePath }) { index, song ->
                    val isSelected = song.filePath in selectedFiles
                    RankSongCard(
                        rank = index + 1,
                        song = song,
                        showRank = false,
                        isCurrentSong = song.filePath == currentPlayingPath,
                        isPlaying = isPlaying && song.filePath == currentPlayingPath,
                        onClick = {
                            if (selectMode) {
                                selectedFiles = if (isSelected) selectedFiles - song.filePath
                                else selectedFiles + song.filePath
                            } else {
                                onPlaySong(songs.toList(), index)
                            }
                        }
                    )
                    if (selectMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 16.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("确认删除") },
            text = { Text("确定要删除选中的 ${selectedFiles.size} 首歌曲吗？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        if (type == "favorites") {
                            selectedFiles.forEach { collectionDao.deleteCollectedSong(it) }
                        } else {
                            selectedFiles.forEach { collectionDao.removeSongFromPlaylist(playlistId, it) }
                        }
                        // 重新加载
                        if (type == "favorites") {
                            val collected = collectionDao.getAllCollectedSongs()
                            val mapped = collected.map { entity ->
                                SongInfo(title = entity.title, artist = entity.artist,
                                    filePath = "${entity.hash}|${entity.audioId}",
                                    albumArtUri = entity.albumArtUri, duration = entity.duration,
                                    albumId = entity.albumId, mixsongid = entity.mixsongid)
                            }
                            withContext(Dispatchers.Main) {
                                songs.clear(); songs.addAll(mapped); totalSongs = mapped.size
                            }
                        } else {
                            val playlistSongs = collectionDao.getPlaylistSongs(playlistId)
                            val mapped = playlistSongs.map { it.toSongInfo() }
                            withContext(Dispatchers.Main) {
                                songs.clear(); songs.addAll(mapped); totalSongs = mapped.size
                            }
                        }
                        withContext(Dispatchers.Main) {
                            selectMode = false; selectedFiles = emptySet(); showDeleteConfirm = false
                            android.widget.Toast.makeText(context, "已删除", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }

    // 移动到歌单半屏
    if (showMoveSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoveSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("移动到", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                if (movePlaylists.isEmpty()) {
                    Text("暂无歌单", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    movePlaylists.forEachIndexed { idx, pl ->
                        if (idx > 0) Spacer(Modifier.height(2.dp))
                        Surface(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    selectedFiles.forEach { filePath ->
                                        if (!collectionDao.isSongInPlaylist(pl.id, filePath)) {
                                            // 获取完整歌曲数据以创建 PlaylistSongCrossRef
                                            val entity = collectionDao.getSongByFilePath(filePath)
                                            if (entity != null) {
                                                val parts = filePath.split("|")
                                                collectionDao.addSongToPlaylist(PlaylistSongCrossRef(
                                                    playlistId = pl.id, songFilePath = filePath,
                                                    title = entity.title, artist = entity.artist,
                                                    albumArtUri = entity.albumArtUri, duration = entity.duration,
                                                    hash = entity.hash, audioId = entity.audioId,
                                                    albumId = entity.albumId, mixsongid = entity.mixsongid
                                                ))
                                            }
                                        }
                                        if (type == "favorites") {
                                            collectionDao.deleteCollectedSong(filePath)
                                        }
                                    }
                                    // 重新加载
                                    if (type == "favorites") {
                                        val collected = collectionDao.getAllCollectedSongs()
                                        val mapped = collected.map { entity ->
                                            SongInfo(title = entity.title, artist = entity.artist,
                                                filePath = "${entity.hash}|${entity.audioId}",
                                                albumArtUri = entity.albumArtUri, duration = entity.duration,
                                                albumId = entity.albumId, mixsongid = entity.mixsongid)
                                        }
                                        withContext(Dispatchers.Main) {
                                            songs.clear(); songs.addAll(mapped); totalSongs = mapped.size
                                        }
                                    } else {
                                        val playlistSongs = collectionDao.getPlaylistSongs(playlistId)
                                        val mapped = playlistSongs.map { it.toSongInfo() }
                                        withContext(Dispatchers.Main) {
                                            songs.clear(); songs.addAll(mapped); totalSongs = mapped.size
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        selectMode = false; selectedFiles = emptySet(); showMoveSheet = false
                                        android.widget.Toast.makeText(context, "已移动到 ${pl.name}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LibraryMusic, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text(pl.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
