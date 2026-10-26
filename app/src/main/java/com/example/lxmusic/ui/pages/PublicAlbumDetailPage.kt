package com.example.lxmusic.ui.pages

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.lxmusic.data.search.PublicAlbumProviders
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.ui.components.SongContextMenuActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 酷狗专辑详情页（搜索「专辑」点进来）。
 * 交互与公共歌单详情一致：分页、播放全部、序号开关、正在播放音阶、底栏隐藏但保留胶囊播放条。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PublicAlbumDetailPage(
    albumId: Long,
    albumName: String,
    artist: String,
    coverUrl: String,
    songCount: Int,
    onBack: () -> Unit,
    onPlaySong: (List<SongInfo>, Int) -> Unit,
    currentPlayingPath: String? = null,
    isPlaying: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageSize = 30
    var songs by remember(albumId) { mutableStateOf<List<SongInfo>>(emptyList()) }
    var total by remember(albumId) { mutableIntStateOf(if (songCount > 0) songCount else 0) }
    var page by remember(albumId) { mutableIntStateOf(0) }
    var isLoading by remember(albumId) { mutableStateOf(true) }
    var isLoadingMore by remember(albumId) { mutableStateOf(false) }
    var loadMoreFailed by remember(albumId) { mutableStateOf(false) }
    var loadError by remember(albumId) { mutableStateOf(false) }
    var noMore by remember(albumId) { mutableStateOf(false) }
    var preparingAll by remember(albumId) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val prefs = remember { context.getSharedPreferences("rank_cache", android.content.Context.MODE_PRIVATE) }
    var showRankNumbers by remember { mutableStateOf(prefs.getBoolean("show_rank_numbers", true)) }

    // 搜索项可能缺歌手/封面：补拉一次专辑信息
    var headerName by remember(albumId) { mutableStateOf(albumName) }
    var headerArtist by remember(albumId) { mutableStateOf(artist) }
    var headerCover by remember(albumId) { mutableStateOf(coverUrl) }
    LaunchedEffect(albumId) {
        if (headerArtist.isBlank() || headerCover.isBlank()) {
            val info = withContext(Dispatchers.IO) { PublicAlbumProviders.info(albumId) }
            if (info != null) {
                if (headerName.isBlank()) headerName = info.name
                if (headerArtist.isBlank()) headerArtist = info.artist
                if (headerCover.isBlank()) headerCover = info.coverUrl
            }
        }
    }

    BackHandler { onBack() }

    fun load(targetPage: Int, append: Boolean) {
        if (append) {
            if (isLoadingMore || noMore) return
            isLoadingMore = true
        } else {
            isLoading = songs.isEmpty()
        }
        loadError = false
        loadMoreFailed = false
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    PublicAlbumProviders.songs(albumId, coverUrl, targetPage, pageSize)
                }.getOrNull()
            }
            if (result == null) {
                isLoading = false
                isLoadingMore = false
                if (append) loadMoreFailed = true else loadError = songs.isEmpty()
                return@launch
            }
            val merged = if (append) {
                (songs + result.songs).distinctBy { it.filePath }
            } else {
                result.songs.distinctBy { it.filePath }
            }
            songs = merged
            total = if (result.total > 0) result.total else merged.size
            page = targetPage
            noMore = result.songs.isEmpty() || merged.size >= total
            isLoading = false
            isLoadingMore = false
        }
    }

    LaunchedEffect(albumId) { load(1, append = false) }

    val shouldLoadMore by remember(albumId, listState) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            songs.isNotEmpty() && !noMore && !isLoading && !isLoadingMore && !loadMoreFailed &&
                !preparingAll && (total <= 0 || songs.size < total) &&
                lastVisible >= songs.size - 5
        }
    }
    LaunchedEffect(shouldLoadMore, songs.size) {
        if (shouldLoadMore) load(page + 1, append = true)
    }

    fun playAll() {
        if (songs.isEmpty() || preparingAll) return
        if (noMore || (total > 0 && songs.size >= total)) {
            onPlaySong(songs, 0)
            return
        }
        preparingAll = true
        scope.launch {
            val all = withContext(Dispatchers.IO) {
                val out = mutableListOf<SongInfo>()
                var p = 1
                var t = total
                while (p <= 40) {
                    val res = runCatching {
                        PublicAlbumProviders.songs(albumId, coverUrl, p, pageSize)
                    }.getOrNull() ?: break
                    if (res.songs.isEmpty()) break
                    out += res.songs
                    if (res.total > 0) t = res.total
                    if (t > 0 && out.size >= t) break
                    p++
                }
                out.distinctBy { it.filePath }
            }
            preparingAll = false
            if (all.isNotEmpty()) {
                songs = all
                total = all.size
                noMore = true
                onPlaySong(all, 0)
            } else {
                onPlaySong(songs, 0)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = headerName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // 头部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                if (headerCover.isNotBlank()) {
                    val painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(context)
                            .data(headerCover.replace("{size}", "240"))
                            .memoryCacheKey(headerCover)
                            .crossfade(150)
                            .build()
                    )
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (headerArtist.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = headerArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    text = buildString {
                        val count = if (total > 0) total else songCount
                        if (count > 0) append("$count 首")
                        if (songs.isNotEmpty() && songs.size < count) append(" · 已加载 ${songs.size}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { playAll() },
                        enabled = songs.isNotEmpty() && !preparingAll,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (preparingAll) "加载全部…" else "播放全部", fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = {
                        showRankNumbers = !showRankNumbers
                        prefs.edit().putBoolean("show_rank_numbers", showRankNumbers).apply()
                    }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = if (showRankNumbers) "隐藏序号" else "显示序号",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                isLoading -> CircularWavyProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
                loadError || songs.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "该专辑暂时无法读取",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = { load(1, append = false) }, shape = RoundedCornerShape(20.dp)) {
                        Text("重试")
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 180.dp)
                ) {
                    itemsIndexed(songs, key = { _, song -> song.filePath }) { index, song ->
                        var showSheet by remember(song.filePath) { mutableStateOf(false) }
                        RankSongCard(
                            rank = index + 1,
                            song = song,
                            showRank = showRankNumbers,
                            isCurrentSong = song.filePath == currentPlayingPath,
                            isPlaying = isPlaying && song.filePath == currentPlayingPath,
                            onClick = { onPlaySong(songs, index) },
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
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val cover = song.albumArtUri
                                    if (!cover.isNullOrBlank()) {
                                        val painter = rememberAsyncImagePainter(
                                            model = ImageRequest.Builder(context)
                                                .data(cover)
                                                .memoryCacheKey(cover)
                                                .crossfade(120)
                                                .build()
                                        )
                                        Image(
                                            painter = painter,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = song.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                SongContextMenuActions(
                                    song = song,
                                    onDismiss = { showSheet = false }
                                )
                            }
                        }
                    }
                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularWavyProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    } else if (loadMoreFailed) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "加载失败，点击重试",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { load(page + 1, append = true) }
                                        .padding(8.dp)
                                )
                            }
                        }
                    } else if (noMore && songs.size > 12) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "已加载全部 ${songs.size} 首",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
