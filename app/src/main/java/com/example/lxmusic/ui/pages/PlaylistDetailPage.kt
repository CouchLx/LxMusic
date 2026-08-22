package com.example.lxmusic.ui.pages

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.DisposableEffect
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
    onAddToQueueNext: (SongInfo) -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {},
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MusicDatabase.getDatabase(context) }
    val collectionDao = remember { db.collectionDao() }
    val songs = remember { mutableStateListOf<SongInfo>() }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalSongs by remember { mutableIntStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var noMoreData by remember { mutableStateOf(false) }
    val pageSize = 30

    // 选中模式状态
    var selectMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var movePlaylists by remember { mutableStateOf<List<UserPlaylistEntity>>(emptyList()) }

    // 通知外部多选状态变化（用于隐藏底部 MiniPlayer 播放条）
    LaunchedEffect(selectMode) {
        onSelectionModeChange(selectMode)
    }
    DisposableEffect(Unit) {
        onDispose {
            onSelectionModeChange(false)
        }
    }

    // 拦截返回键：多选模式下按返回键退出多选模式
    BackHandler(enabled = selectMode) {
        selectMode = false
        selectedFiles = emptySet()
    }

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
                var mapped: List<SongInfo>? = null
                var total = 0
                val gid = playlist.global_collection_id
                if (!gid.isNullOrBlank()) {
                    val (list, count) = KuGouApi.fetchSpecialPlaylistSongs(0L, gid, page, pageSize)
                    if (list.isNotEmpty()) {
                        mapped = list
                        total = count
                    }
                }
                if (mapped == null) {
                    val respNew = KuGouApi.service.getPlaylistTracksNew(playlist.listid, page, pageSize)
                    val rawList = respNew.data?.info?.map { mapSong(it) }.orEmpty()
                    mapped = KuGouApi.filterLocallyRemoved(playlist.listid, rawList)
                    total = respNew.data?.count ?: 0
                }
                withContext(Dispatchers.Main) {
                    if (append) {
                        songs.addAll(mapped ?: emptyList())
                    } else {
                        songs.clear()
                        songs.addAll(mapped ?: emptyList())
                    }
                    totalSongs = total
                    noMoreData = (mapped?.size ?: 0) < pageSize || songs.size >= total
                    isLoading = false
                    isLoadingMore = false
                    loadError = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    isLoadingMore = false
                    if (!append) loadError = true
                }
            }
        }
    }

    // 加载全部歌曲（并行分页）
    val loadAllSongs: suspend () -> List<SongInfo> = {
        withContext(Dispatchers.IO) {
            val allSongs = mutableListOf<SongInfo>()
            val gid = playlist.global_collection_id
            val total = if (totalSongs > 0) totalSongs else (KuGouApi.playlistRealCount(playlist.listid) ?: 300)
            val totalPages = (total + pageSize - 1) / pageSize
            val pages = (1..totalPages).toList()

            val batchSize = 5
            for (i in pages.indices step batchSize) {
                val batch = pages.subList(i, minOf(i + batchSize, pages.size))
                val jobs = batch.map { page ->
                    async {
                        try {
                            if (!gid.isNullOrBlank()) {
                                val (list, _) = KuGouApi.fetchSpecialPlaylistSongs(0L, gid, page, pageSize)
                                if (list.isNotEmpty()) return@async KuGouApi.filterLocallyRemoved(playlist.listid, list)
                            }
                            val respNew = KuGouApi.service.getPlaylistTracksNew(playlist.listid, page, pageSize)
                            KuGouApi.filterLocallyRemoved(
                                playlist.listid,
                                respNew.data?.info?.map { mapSong(it) }.orEmpty()
                            )
                        } catch (_: Exception) { emptyList<SongInfo>() }
                    }
                }
                val results = jobs.awaitAll()
                results.forEach { list -> allSongs.addAll(list) }
            }
            allSongs
        }
    }

    // 歌曲菜单（与首页/发现一致）
    var menuSong by remember { mutableStateOf<SongInfo?>(null) }

    // 定位到当前播放歌曲（支持 exact filePath 与 hash 双重匹配）
    fun locateToCurrentSong() {
        val targetPath = currentPlayingPath
        if (targetPath.isNullOrBlank()) {
            Toast.makeText(context, "当前没有正在播放的歌曲", Toast.LENGTH_SHORT).show()
            return
        }
        val targetHash = targetPath.substringBefore("|").uppercase()
        val targetIndex = songs.indexOfFirst {
            it.filePath == targetPath || (targetHash.isNotBlank() && it.filePath.substringBefore("|").uppercase() == targetHash)
        }
        if (targetIndex >= 0) {
            scope.launch { listState.animateScrollToItem((targetIndex + 2).coerceAtLeast(0)) }
            Toast.makeText(context, "已定位到第 ${targetIndex + 1} 首歌曲", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "当前播放歌曲不在本列表中", Toast.LENGTH_SHORT).show()
        }
    }
    // 暴露定位函数
    LaunchedEffect(playlist.listid, songs.size, currentPlayingPath) {
        onLocateReady { locateToCurrentSong() }
    }

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
                contentPadding = PaddingValues(bottom = if (selectMode) 140.dp else 100.dp)
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
                        AnimatedContent(
                            targetState = selectMode,
                            label = "playlistSubtitleAnim"
                        ) { isSelecting ->
                            if (isSelecting) {
                                Text(
                                    text = "已选 ${selectedFiles.size} / ${songs.size} 首歌曲",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = if (totalSongs > 0) "共 ${KuGouApi.playlistRealCount(playlist.listid) ?: totalSongs} 首" else " ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item {
                    AnimatedContent(
                        targetState = selectMode,
                        label = "playlistActionBarAnim"
                    ) { isSelecting ->
                        if (isSelecting) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val isAllSelected = songs.isNotEmpty() && selectedFiles.size == songs.size
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                selectedFiles = if (isAllSelected) emptySet()
                                                else songs.map { it.filePath }.toSet()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Checkbox(
                                            checked = isAllSelected,
                                            onCheckedChange = { checked ->
                                                selectedFiles = if (checked) songs.map { it.filePath }.toSet() else emptySet()
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (isAllSelected) "取消全选" else "全选",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "(${selectedFiles.size}/${songs.size})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            selectMode = false
                                            selectedFiles = emptySet()
                                        }
                                    ) {
                                        Text(
                                            text = "完成",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
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
                                    enabled = !isLoadingAll,
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isLoadingAll) "加载中..." else "播放全部", fontWeight = FontWeight.SemiBold)
                                }

                                FilledTonalIconButton(
                                    onClick = { selectMode = true },
                                    modifier = Modifier.size(42.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Checklist,
                                        contentDescription = "批量管理",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                itemsIndexed(songs, key = { _, song -> song.filePath }) { index, song ->
                    val isSelected = song.filePath in selectedFiles
                    RankSongCard(
                        rank = index + 1,
                        song = song,
                        showRank = !selectMode,
                        isCurrentSong = song.filePath == currentPlayingPath,
                        isPlaying = isPlaying && song.filePath == currentPlayingPath,
                        selectMode = selectMode,
                        isSelected = isSelected,
                        onClick = {
                            if (selectMode) {
                                selectedFiles = if (isSelected) selectedFiles - song.filePath else selectedFiles + song.filePath
                            } else {
                                onPlaySong(songs.toList(), index)
                            }
                        },
                        onLongClick = {
                            if (!selectMode) {
                                selectMode = true
                                selectedFiles = setOf(song.filePath)
                            }
                        },
                        onMenuClick = { menuSong = song }
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

        // 底部悬浮批量操作栏
        AnimatedVisibility(
            visible = selectMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            val hasSelection = selectedFiles.isNotEmpty()
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 下一首播放
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = hasSelection) {
                                val selectedSongs = songs.filter { it.filePath in selectedFiles }
                                selectedSongs.forEach { onAddToQueueNext(it) }
                                Toast.makeText(context, "已将 ${selectedFiles.size} 首歌曲添加到播放队列", Toast.LENGTH_SHORT).show()
                                selectMode = false
                                selectedFiles = emptySet()
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "下一首播放",
                            tint = if (hasSelection) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "下一首播放",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasSelection) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }

                    // 2. 收藏到歌单
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = hasSelection) {
                                scope.launch(Dispatchers.IO) {
                                    val allPls = collectionDao.getAllUserPlaylists()
                                    movePlaylists = allPls
                                    withContext(Dispatchers.Main) { showMoveSheet = true }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = "收藏到歌单",
                            tint = if (hasSelection) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "收藏到歌单",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasSelection) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }

        // 歌曲菜单（与首页/发现一致）
        menuSong?.let { menuSongItem ->
            ModalBottomSheet(
                onDismissRequest = { menuSong = null },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                SongContextMenuActions(
                    song = menuSongItem,
                    onDismiss = { menuSong = null },
                    onAddToQueueNext = { onAddToQueueNext(menuSongItem) }
                )
            }
        }

        // 批量收藏到自建歌单半屏 Sheet
        if (showMoveSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMoveSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "收藏 ${selectedFiles.size} 首歌曲至歌单",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))

                    if (movePlaylists.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无可用歌单，请先在「我的」中新建歌单", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(movePlaylists, key = { _, pl -> pl.id }) { _, pl ->
                                Surface(
                                    onClick = {
                                        val count = selectedFiles.size
                                        scope.launch(Dispatchers.IO) {
                                            val selectedSongsList = songs.filter { it.filePath in selectedFiles }
                                            selectedSongsList.forEach { songItem ->
                                                val filePath = songItem.filePath
                                                if (!collectionDao.isSongInPlaylist(pl.id, filePath)) {
                                                    val parts = filePath.split("|")
                                                    val hash = parts.getOrElse(0) { "" }
                                                    val audioId = parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L
                                                    collectionDao.addSongToPlaylist(
                                                        PlaylistSongCrossRef(
                                                            playlistId = pl.id,
                                                            songFilePath = filePath,
                                                            title = songItem.title,
                                                            artist = songItem.artist,
                                                            albumArtUri = songItem.albumArtUri,
                                                            duration = songItem.duration,
                                                            hash = hash,
                                                            audioId = audioId,
                                                            albumId = songItem.albumId,
                                                            mixsongid = songItem.mixsongid
                                                        )
                                                    )
                                                }
                                            }
                                            withContext(Dispatchers.Main) {
                                                selectMode = false
                                                selectedFiles = emptySet()
                                                showMoveSheet = false
                                                Toast.makeText(context, "已收藏 $count 首歌曲到「${pl.name}」", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LibraryMusic,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Text(
                                            text = pl.name,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
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
    onAddToQueueNext: (SongInfo) -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {}
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

    // 选中模式状态
    var selectMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var movePlaylists by remember { mutableStateOf<List<UserPlaylistEntity>>(emptyList()) }

    // 通知外部多选状态变化（用于隐藏底部 MiniPlayer 播放条）
    LaunchedEffect(selectMode) {
        onSelectionModeChange(selectMode)
    }
    DisposableEffect(Unit) {
        onDispose {
            onSelectionModeChange(false)
        }
    }

    // 拦截返回键：多选模式下按返回键退出多选模式
    BackHandler(enabled = selectMode) {
        selectMode = false
        selectedFiles = emptySet()
    }

    // 收藏歌单：官方模式=本地「喜欢镜像」表持久（先写不消失）；关闭官方=本地收藏区同名歌单
    val db = remember { MusicDatabase.getDatabase(context) }
    val collectionDao = remember { db.collectionDao() }
    var playlistCollected by remember { mutableStateOf(false) }
    var isCollecting by remember { mutableStateOf(false) }
    val favPrefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    val kugouFavOn = favPrefs.getBoolean("favorite_to_kugou", false)
    val syncLocalFavorite = favPrefs.getBoolean("favorite_sync_local", false)
    LaunchedEffect(playlistName, gid, kugouFavOn) {
        playlistCollected = if (kugouFavOn && gid.isNotBlank()) {
            collectionDao.getLikedPlaylistByGid(gid) != null
        } else {
            collectionDao.getAllUserPlaylists().any { it.name == playlistName }
        }
    }

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
                    if (list.size < pageSize || songs.size >= count) noMoreData = true
                    loadError = false
                } else {
                    noMoreData = true
                }
            } catch (e: Exception) {
                if (songs.isEmpty()) loadError = true
            } finally {
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    // 并行分页加载整张歌单全部歌曲（用于播放全部）
    val loadAllSongs: suspend () -> List<SongInfo> = {
        withContext(Dispatchers.IO) {
            val allSongs = mutableListOf<SongInfo>()
            val total = if (totalSongs > 0) totalSongs else 300
            val totalPages = (total + pageSize - 1) / pageSize
            val pages = (1..totalPages).toList()

            val batchSize = 5
            for (i in pages.indices step batchSize) {
                val batch = pages.subList(i, minOf(i + batchSize, pages.size))
                val jobs = batch.map { page ->
                    async {
                        try {
                            val (list, _) = KuGouApi.fetchSpecialPlaylistSongs(playlistId, gid.ifBlank { null }, page, pageSize)
                            list
                        } catch (_: Exception) { emptyList<SongInfo>() }
                    }
                }
                val results = jobs.awaitAll()
                results.forEach { list -> allSongs.addAll(list) }
            }
            allSongs
        }
    }

    suspend fun fetchAllSearchSongsDirect(): List<SongInfo> = withContext(Dispatchers.IO) {
        val all = mutableListOf<SongInfo>()
        var p = 1
        while (true) {
            val (list, count) = KuGouApi.fetchSpecialPlaylistSongs(playlistId, gid.ifBlank { null }, p, 100)
            if (list.isEmpty()) break
            all.addAll(list)
            if (all.size >= count || list.size < 100) break
            p++
        }
        all
    }

    suspend fun saveSongsToPlaylist(targetId: Long, list: List<SongInfo>) = withContext(Dispatchers.IO) {
        list.forEach { song ->
            val parts = song.filePath.split("|")
            val hash = parts.getOrElse(0) { "" }
            val audioId = parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L
            collectionDao.addSongToPlaylist(
                PlaylistSongCrossRef(
                    playlistId = targetId,
                    songFilePath = song.filePath,
                    title = song.title,
                    artist = song.artist,
                    albumArtUri = song.albumArtUri,
                    duration = song.duration,
                    hash = hash,
                    audioId = audioId,
                    albumId = song.albumId,
                    mixsongid = song.mixsongid
                )
            )
        }
    }

    fun toggleCollectPlaylist() {
        if (isCollecting) return
        isCollecting = true
        scope.launch(Dispatchers.IO) {
            if (playlistCollected) {
                if (kugouFavOn && gid.isNotBlank()) {
                    collectionDao.deleteLikedPlaylistByGid(gid)
                    if (syncLocalFavorite) {
                        val existing = collectionDao.getAllUserPlaylists().firstOrNull { it.name == playlistName }
                        if (existing != null) collectionDao.deletePlaylistWithSongs(existing.id)
                    }
                    val cid = KuGouApi.collectedPlaylistListid(gid)
                    if (cid > 0) {
                        val authPrefs = context.getSharedPreferences("auth", android.content.Context.MODE_PRIVATE)
                        val minePrefs = context.getSharedPreferences("mine_state", android.content.Context.MODE_PRIVATE)
                        val authUid = authPrefs.getLong("userid", 0)
                        if (authUid > 0) KuGouApi.addDeletedPlaylistId(minePrefs, authUid, cid)
                        KuGouApi.deleteKuGouPlaylist(cid)
                    }
                } else {
                    val existing = collectionDao.getAllUserPlaylists().firstOrNull { it.name == playlistName }
                    if (existing != null) collectionDao.deletePlaylistWithSongs(existing.id)
                }
                withContext(Dispatchers.Main) {
                    playlistCollected = false
                    Toast.makeText(context, "已取消收藏歌单", Toast.LENGTH_SHORT).show()
                }
            } else {
                var officialOk = false
                if (kugouFavOn && gid.isNotBlank()) {
                    collectionDao.insertLikedPlaylist(
                        com.example.lxmusic.LikedPlaylistEntity(
                            name = playlistName, gid = gid, coverUrl = coverUrl,
                            songcount = if (totalSongs > 0) totalSongs else if (songs.isNotEmpty()) songs.size else 0
                        )
                    )
                    if (syncLocalFavorite) {
                        val existingLocal = collectionDao.getAllUserPlaylists().firstOrNull { it.name == playlistName }
                        val allSongs = if (songs.isNotEmpty()) loadAllSongs() else fetchAllSearchSongsDirect()
                        val newId = existingLocal?.id ?: collectionDao.createPlaylist(com.example.lxmusic.UserPlaylistEntity(name = playlistName))
                        if (newId > 0) saveSongsToPlaylist(newId, allSongs)
                    }
                    val cid = KuGouApi.kuGouCollectPlaylist(playlistName, gid)
                    if (cid != null && cid > 0) {
                        officialOk = true
                        KuGouApi.rememberCollectedPlaylist(gid, cid)
                    }
                } else {
                    val existingLocal = collectionDao.getAllUserPlaylists().firstOrNull { it.name == playlistName }
                    val allSongs = if (songs.isNotEmpty()) loadAllSongs() else fetchAllSearchSongsDirect()
                    val newId = existingLocal?.id ?: collectionDao.createPlaylist(com.example.lxmusic.UserPlaylistEntity(name = playlistName))
                    if (newId > 0) saveSongsToPlaylist(newId, allSongs)
                }
                withContext(Dispatchers.Main) {
                    playlistCollected = true
                    val msg = when {
                        !kugouFavOn || gid.isBlank() -> "已收藏歌单"
                        officialOk -> "已收藏歌单（已同步酷狗）"
                        else -> "已收藏（酷狗同步失败）"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
            isCollecting = false
        }
    }

    LaunchedEffect(playlistId, gid) {
        loadPage(1)
    }

    // 定位到当前播放歌曲（支持 exact filePath 与 hash 双重匹配）
    fun locateToCurrentSong() {
        val targetPath = currentPlayingPath
        if (targetPath.isNullOrBlank()) {
            Toast.makeText(context, "当前没有正在播放的歌曲", Toast.LENGTH_SHORT).show()
            return
        }
        val targetHash = targetPath.substringBefore("|").uppercase()
        val targetIndex = songs.indexOfFirst {
            it.filePath == targetPath || (targetHash.isNotBlank() && it.filePath.substringBefore("|").uppercase() == targetHash)
        }
        if (targetIndex >= 0) {
            scope.launch { listState.animateScrollToItem((targetIndex + 2).coerceAtLeast(0)) }
            Toast.makeText(context, "已定位到第 ${targetIndex + 1} 首歌曲", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "当前播放歌曲不在本列表中", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(playlistId, songs.size, currentPlayingPath) {
        onLocateReady { locateToCurrentSong() }
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
                contentPadding = PaddingValues(bottom = if (selectMode) 140.dp else 100.dp)
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
                        AnimatedContent(
                            targetState = selectMode,
                            label = "searchPlaylistSubtitleAnim"
                        ) { isSelecting ->
                            if (isSelecting) {
                                Text(
                                    text = "已选 ${selectedFiles.size} / ${songs.size} 首歌曲",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
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
                    }
                }
                item {
                    AnimatedContent(
                        targetState = selectMode,
                        label = "searchPlaylistActionBarAnim"
                    ) { isSelecting ->
                        if (isSelecting) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val isAllSelected = songs.isNotEmpty() && selectedFiles.size == songs.size
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                selectedFiles = if (isAllSelected) emptySet()
                                                else songs.map { it.filePath }.toSet()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Checkbox(
                                            checked = isAllSelected,
                                            onCheckedChange = { checked ->
                                                selectedFiles = if (checked) songs.map { it.filePath }.toSet() else emptySet()
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (isAllSelected) "取消全选" else "全选",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "(${selectedFiles.size}/${songs.size})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            selectMode = false
                                            selectedFiles = emptySet()
                                        }
                                    ) {
                                        Text(
                                            text = "完成",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
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
                                    enabled = !isLoadingAll,
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isLoadingAll) "加载中..." else "播放全部", fontWeight = FontWeight.SemiBold)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 收藏歌单（与播放器同款 ❤）
                                    IconButton(
                                        onClick = { toggleCollectPlaylist() },
                                        enabled = !isCollecting
                                    ) {
                                        Icon(
                                            if (playlistCollected) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = if (playlistCollected) "取消收藏歌单" else "收藏歌单",
                                            tint = if (playlistCollected) Color(0xFFE57373) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(Modifier.width(4.dp))

                                    // 批量管理入口（纯图标按键）
                                    FilledTonalIconButton(
                                        onClick = { selectMode = true },
                                        modifier = Modifier.size(42.dp),
                                        shape = CircleShape
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Checklist,
                                            contentDescription = "批量管理",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                itemsIndexed(songs, key = { _, song -> song.filePath }) { index, song ->
                    val isSelected = song.filePath in selectedFiles
                    var showSheet by remember { mutableStateOf(false) }

                    RankSongCard(
                        rank = index + 1,
                        song = song,
                        showRank = !selectMode,
                        isCurrentSong = song.filePath == currentPlayingPath,
                        isPlaying = isPlaying && song.filePath == currentPlayingPath,
                        selectMode = selectMode,
                        isSelected = isSelected,
                        onClick = {
                            if (selectMode) {
                                selectedFiles = if (isSelected) selectedFiles - song.filePath else selectedFiles + song.filePath
                            } else {
                                onPlaySong(songs.toList(), index)
                            }
                        },
                        onLongClick = {
                            if (!selectMode) {
                                selectMode = true
                                selectedFiles = setOf(song.filePath)
                            }
                        },
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

        // 底部悬浮批量操作栏
        AnimatedVisibility(
            visible = selectMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            val hasSelection = selectedFiles.isNotEmpty()
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 下一首播放
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = hasSelection) {
                                val selectedSongs = songs.filter { it.filePath in selectedFiles }
                                selectedSongs.forEach { onAddToQueueNext(it) }
                                Toast.makeText(context, "已将 ${selectedFiles.size} 首歌曲添加到播放队列", Toast.LENGTH_SHORT).show()
                                selectMode = false
                                selectedFiles = emptySet()
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "下一首播放",
                            tint = if (hasSelection) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "下一首播放",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasSelection) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }

                    // 2. 收藏到歌单
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = hasSelection) {
                                scope.launch(Dispatchers.IO) {
                                    val allPls = collectionDao.getAllUserPlaylists()
                                    movePlaylists = allPls
                                    withContext(Dispatchers.Main) { showMoveSheet = true }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = "收藏到歌单",
                            tint = if (hasSelection) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "收藏到歌单",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasSelection) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }

        // 批量收藏到自建歌单半屏 Sheet
        if (showMoveSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMoveSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "收藏 ${selectedFiles.size} 首歌曲至歌单",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))

                    if (movePlaylists.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无可用歌单，请先在「我的」中新建歌单", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(movePlaylists, key = { _, pl -> pl.id }) { _, pl ->
                                Surface(
                                    onClick = {
                                        val count = selectedFiles.size
                                        scope.launch(Dispatchers.IO) {
                                            val selectedSongsList = songs.filter { it.filePath in selectedFiles }
                                            selectedSongsList.forEach { songItem ->
                                                val filePath = songItem.filePath
                                                if (!collectionDao.isSongInPlaylist(pl.id, filePath)) {
                                                    val parts = filePath.split("|")
                                                    val hash = parts.getOrElse(0) { "" }
                                                    val audioId = parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L
                                                    collectionDao.addSongToPlaylist(
                                                        PlaylistSongCrossRef(
                                                            playlistId = pl.id,
                                                            songFilePath = filePath,
                                                            title = songItem.title,
                                                            artist = songItem.artist,
                                                            albumArtUri = songItem.albumArtUri,
                                                            duration = songItem.duration,
                                                            hash = hash,
                                                            audioId = audioId,
                                                            albumId = songItem.albumId,
                                                            mixsongid = songItem.mixsongid
                                                        )
                                                    )
                                                }
                                            }
                                            withContext(Dispatchers.Main) {
                                                selectMode = false
                                                selectedFiles = emptySet()
                                                showMoveSheet = false
                                                Toast.makeText(context, "已收藏 $count 首歌曲到「${pl.name}」", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LibraryMusic,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Text(
                                            text = pl.name,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

// ==================== 收藏 / 自建歌单详情页 ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionDetailPage(
    type: String,          // "favorites" 或 "playlist" 或 "liked"
    playlistId: Long = 0,
    onBack: () -> Unit,
    onPlaySong: (List<SongInfo>, Int) -> Unit,
    currentPlayingPath: String? = null,
    isPlaying: Boolean = false,
    onLocateReady: (() -> Unit) -> Unit = {},
    onMenuReady: (() -> Unit) -> Unit = {},
    onAddToQueueNext: (SongInfo) -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MusicDatabase.getDatabase(context) }
    val collectionDao = remember { db.collectionDao() }
    val songs = remember { mutableStateListOf<SongInfo>() }
    var isLoading by remember { mutableStateOf(true) }
    var totalSongs by remember { mutableIntStateOf(0) }
    var playlistName by remember { mutableStateOf(if (type == "favorites") "我的收藏" else if (type == "liked") "我喜欢的" else "自建歌单") }

    // 选中模式状态
    var selectMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<SongInfo?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var movePlaylists by remember { mutableStateOf<List<UserPlaylistEntity>>(emptyList()) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }

    // 通知外部多选状态变化（用于隐藏底部 MiniPlayer 播放条，避免遮挡底部操作栏）
    LaunchedEffect(selectMode) {
        onSelectionModeChange(selectMode)
    }
    DisposableEffect(Unit) {
        onDispose {
            onSelectionModeChange(false)
        }
    }

    // 拦截返回键：多选模式下按返回键退出多选模式
    BackHandler(enabled = selectMode) {
        selectMode = false
        selectedFiles = emptySet()
    }

    suspend fun reloadSongs() {
        if (type == "favorites") {
            playlistName = "我的收藏"
            val collected = collectionDao.getAllCollectedSongs()
            val mapped = collected.map { entity ->
                SongInfo(
                    title = entity.title, artist = entity.artist,
                    filePath = "${entity.hash}|${entity.audioId}",
                    albumArtUri = entity.albumArtUri, duration = entity.duration,
                    albumId = entity.albumId, mixsongid = entity.mixsongid
                )
            }
            withContext(Dispatchers.Main) {
                songs.clear(); songs.addAll(mapped); totalSongs = mapped.size
            }
        } else if (type == "liked") {
            playlistName = "我喜欢的"
            val likedSongs = collectionDao.getAllLikedSongs()
            val localMapped = likedSongs.map { entity ->
                SongInfo(
                    title = entity.title, artist = entity.artist,
                    filePath = "${entity.hash}|${entity.audioId}",
                    albumArtUri = entity.albumArtUri, duration = entity.duration,
                    albumId = entity.albumId, mixsongid = entity.mixsongid
                )
            }
            val favPrefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            val officialFavOn = favPrefs.getBoolean("favorite_to_kugou", false)
            val merged = if (officialFavOn) {
                val official = KuGouApi.fetchKugouLikeSongs()
                val officialHashes = official.mapTo(HashSet()) { it.filePath.substringBefore("|").uppercase() }
                val localOnly = localMapped.filter { it.filePath.substringBefore("|").uppercase() !in officialHashes }
                localOnly + official
            } else localMapped
            withContext(Dispatchers.Main) {
                songs.clear(); songs.addAll(merged); totalSongs = merged.size
            }
            KuGouApi.recordLikedCount(merged.size)
        } else {
            val pl = collectionDao.getAllUserPlaylists().firstOrNull { it.id == playlistId }
            val resolvedName = pl?.name ?: "自建歌单"
            val playlistSongs = collectionDao.getPlaylistSongs(playlistId)
            val mapped = playlistSongs.map { it.toSongInfo() }
            withContext(Dispatchers.Main) {
                playlistName = resolvedName
                songs.clear(); songs.addAll(mapped); totalSongs = mapped.size
            }
        }
    }

    LaunchedEffect(type, playlistId) {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            reloadSongs()
            withContext(Dispatchers.Main) {
                isLoading = false
            }
        }
    }

    val listState = rememberLazyListState()

    // 定位当前播放（支持 exact filePath 与 hash 双重匹配，准确计算 LazyColumn 索引）
    fun locateToCurrentSong() {
        val targetPath = currentPlayingPath
        if (targetPath.isNullOrBlank()) {
            Toast.makeText(context, "当前没有正在播放的歌曲", Toast.LENGTH_SHORT).show()
            return
        }
        val targetHash = targetPath.substringBefore("|").uppercase()
        val targetIdx = songs.indexOfFirst {
            it.filePath == targetPath || (targetHash.isNotBlank() && it.filePath.substringBefore("|").uppercase() == targetHash)
        }
        if (targetIdx >= 0) {
            scope.launch {
                // index 0 为歌单头部信息，index 1 为控制操作栏，因此单曲 targetIdx 在 LazyColumn 中的对应项为 targetIdx + 2
                listState.animateScrollToItem((targetIdx + 2).coerceAtLeast(0))
            }
            Toast.makeText(context, "已定位到第 ${targetIdx + 1} 首歌曲", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "当前播放歌曲不在本列表中", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(songs.size, currentPlayingPath) {
        onLocateReady { locateToCurrentSong() }
    }

    LaunchedEffect(type, playlistId, playlistName) {
        if (type == "playlist") {
            onMenuReady {
                renameInput = playlistName
                showRenameDialog = true
            }
        }
    }

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
                    imageVector = if (type == "liked" || type == "favorites") Icons.Default.Favorite else Icons.Default.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (type == "favorites") "收藏列表为空" else if (type == "liked") "我喜欢的列表为空" else "歌单中暂无歌曲",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (selectMode) 140.dp else 100.dp)
            ) {
                // 歌单头部信息展示
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(180.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            tonalElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (type == "liked" || type == "favorites") Icons.Default.Favorite else Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = if (type == "liked") Color(0xFFE57373) else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = playlistName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        AnimatedContent(
                            targetState = selectMode,
                            label = "subtitleCountAnim"
                        ) { isSelecting ->
                            if (isSelecting) {
                                Text(
                                    text = "已选 ${selectedFiles.size} / ${songs.size} 首歌曲",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "共 $totalSongs 首歌曲",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 控制操作栏（播放全部 / 批量管理切换）
                item {
                    AnimatedContent(
                        targetState = selectMode,
                        label = "actionBarAnim"
                    ) { isSelecting ->
                        if (isSelecting) {
                            // 多选模式操作栏
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val isAllSelected = songs.isNotEmpty() && selectedFiles.size == songs.size
                                    // 全选 / 取消全选 Toggle
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                selectedFiles = if (isAllSelected) emptySet()
                                                else songs.map { it.filePath }.toSet()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Checkbox(
                                            checked = isAllSelected,
                                            onCheckedChange = { checked ->
                                                selectedFiles = if (checked) songs.map { it.filePath }.toSet() else emptySet()
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (isAllSelected) "取消全选" else "全选",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "(${selectedFiles.size}/${songs.size})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // 完成 / 退出多选按钮
                                    TextButton(
                                        onClick = {
                                            selectMode = false
                                            selectedFiles = emptySet()
                                        }
                                    ) {
                                        Text(
                                            text = "完成",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        } else {
                            // 正常模式操作栏
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // 播放全部主按钮
                                Button(
                                    onClick = { if (songs.isNotEmpty()) onPlaySong(songs.toList(), 0) },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("播放全部", fontWeight = FontWeight.SemiBold)
                                }

                                // 批量管理入口（纯图标按键）
                                FilledTonalIconButton(
                                    onClick = { selectMode = true },
                                    modifier = Modifier.size(42.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Checklist,
                                        contentDescription = "批量管理",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 歌曲列表项
                itemsIndexed(songs, key = { _, song -> song.filePath }) { index, song ->
                    val isSelected = song.filePath in selectedFiles
                    RankSongCard(
                        rank = index + 1,
                        song = song,
                        showRank = !selectMode,
                        isCurrentSong = song.filePath == currentPlayingPath,
                        isPlaying = isPlaying && song.filePath == currentPlayingPath,
                        selectMode = selectMode,
                        isSelected = isSelected,
                        onClick = {
                            if (selectMode) {
                                selectedFiles = if (isSelected) selectedFiles - song.filePath
                                else selectedFiles + song.filePath
                            } else {
                                onPlaySong(songs.toList(), index)
                            }
                        },
                        onLongClick = {
                            if (!selectMode) {
                                selectMode = true
                                selectedFiles = setOf(song.filePath)
                            }
                        },
                        onMenuClick = if (!selectMode) { { menuSong = song } } else null
                    )
                }
            }
        }

        // 底部悬浮批量操作栏（多选模式下显示）
        AnimatedVisibility(
            visible = selectMode,
            enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            val hasSelection = selectedFiles.isNotEmpty()
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 下一首播放
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = hasSelection) {
                                val selectedSongs = songs.filter { it.filePath in selectedFiles }
                                selectedSongs.forEach { onAddToQueueNext(it) }
                                Toast.makeText(context, "已将 ${selectedFiles.size} 首歌曲添加到播放队列", Toast.LENGTH_SHORT).show()
                                selectMode = false
                                selectedFiles = emptySet()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "下一首播放",
                            tint = if (hasSelection) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "下一首播放",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasSelection) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }

                    // 2. 移动到 / 添加到歌单
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = hasSelection) {
                                scope.launch(Dispatchers.IO) {
                                    val allPls = collectionDao.getAllUserPlaylists()
                                    movePlaylists = if (type == "playlist") allPls.filter { it.id != playlistId } else allPls
                                    withContext(Dispatchers.Main) { showMoveSheet = true }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = "移动到歌单",
                            tint = if (hasSelection) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (type == "playlist") "移动到歌单" else "添加到歌单",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasSelection) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }

                    // 3. 删除 / 移除
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = hasSelection) { showDeleteConfirm = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = if (hasSelection) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (hasSelection) "删除 (${selectedFiles.size})" else "删除",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasSelection) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    }

    // 单曲更多菜单
    menuSong?.let { menuSongItem ->
        ModalBottomSheet(
            onDismissRequest = { menuSong = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            SongContextMenuActions(
                song = menuSongItem,
                onDismiss = { menuSong = null },
                onAddToQueueNext = { onAddToQueueNext(menuSongItem) }
            )
        }
    }

    // 批量删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text("确认移除歌曲", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("确定要将选中的 ${selectedFiles.size} 首歌曲从「$playlistName」中移除吗？")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = selectedFiles.size
                        scope.launch(Dispatchers.IO) {
                            if (type == "favorites") {
                                selectedFiles.forEach { collectionDao.deleteCollectedSong(it) }
                            } else if (type == "liked") {
                                selectedFiles.forEach { fp ->
                                    collectionDao.deleteLikedSong(fp)
                                    val s = songs.firstOrNull { it.filePath == fp }
                                    if (s != null && KuGouApi.token.isNotBlank() && KuGouApi.userid.isNotBlank()) {
                                        runCatching { KuGouApi.removeFromKugouLike(s) }
                                    }
                                }
                            } else {
                                selectedFiles.forEach { collectionDao.removeSongFromPlaylist(playlistId, it) }
                            }
                            reloadSongs()
                            withContext(Dispatchers.Main) {
                                selectMode = false
                                selectedFiles = emptySet()
                                showDeleteConfirm = false
                                Toast.makeText(context, "已成功移除 $count 首歌曲", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 移动 / 添加到歌单半屏 Sheet
    if (showMoveSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoveSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (type == "playlist") "移动 ${selectedFiles.size} 首歌曲至歌单" else "添加 ${selectedFiles.size} 首歌曲至歌单",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))

                if (movePlaylists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无其他可用歌单", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(movePlaylists, key = { _, pl -> pl.id }) { _, pl ->
                            Surface(
                                onClick = {
                                    val count = selectedFiles.size
                                    scope.launch(Dispatchers.IO) {
                                        val selectedSongsList = songs.filter { it.filePath in selectedFiles }
                                        selectedSongsList.forEach { songItem ->
                                            val filePath = songItem.filePath
                                            if (!collectionDao.isSongInPlaylist(pl.id, filePath)) {
                                                val parts = filePath.split("|")
                                                val hash = parts.getOrElse(0) { "" }
                                                val audioId = parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L
                                                collectionDao.addSongToPlaylist(
                                                    PlaylistSongCrossRef(
                                                        playlistId = pl.id,
                                                        songFilePath = filePath,
                                                        title = songItem.title,
                                                        artist = songItem.artist,
                                                        albumArtUri = songItem.albumArtUri,
                                                        duration = songItem.duration,
                                                        hash = hash,
                                                        audioId = audioId,
                                                        albumId = songItem.albumId,
                                                        mixsongid = songItem.mixsongid
                                                    )
                                                )
                                            }
                                            // 如果是从自建歌单中"移动"，从当前歌单移除
                                            if (type == "playlist") {
                                                collectionDao.removeSongFromPlaylist(playlistId, filePath)
                                            }
                                        }
                                        if (type == "playlist") {
                                            reloadSongs()
                                        }
                                        withContext(Dispatchers.Main) {
                                            selectMode = false
                                            selectedFiles = emptySet()
                                            showMoveSheet = false
                                            val actionName = if (type == "playlist") "已移动" else "已添加"
                                            Toast.makeText(context, "$actionName $count 首歌曲到「${pl.name}」", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LibraryMusic,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Text(
                                        text = pl.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // 重命名歌单弹窗
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text("重命名歌单", fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("歌单名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameInput.trim()
                        if (trimmed.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                collectionDao.renamePlaylist(playlistId, trimmed)
                                withContext(Dispatchers.Main) {
                                    playlistName = trimmed
                                    showRenameDialog = false
                                    Toast.makeText(context, "歌单已重命名为「$trimmed」", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = renameInput.trim().isNotBlank()
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
