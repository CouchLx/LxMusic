package com.example.lxmusic.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.lxmusic.data.search.KugouYuekuProvider
import com.example.lxmusic.data.search.PublicPlaylistProviders
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.ui.components.PlayingEqBars

// 乐库选中的语种/风格（离开页面后保持）
private var yuekuSelectedGenre = KugouYuekuProvider.DEFAULT_GENRE

/**
 * 发现页「乐库」子页：对齐酷狗客户端「乐库」顶部导航的语种/风格页。
 * - 顶部：国语/日语/英语/粤语/韩语/电子/流行/ACG/纯音乐/国风…
 * - 推荐歌单：官方分类歌单（tag）
 * - 单曲推荐：官方风格推荐组合成 50 首，换一批整批轮换
 * - 电子页：单曲推荐上方有 Phonk/House/EDM 等子风格 chips，切换后单曲不同
 */
@Composable
fun YuekuTabContent(
    onOpenPlaylist: (PublicPlaylistProviders.PublicPlaylist) -> Unit,
    onPlaySong: ((List<SongInfo>, Int) -> Unit)?,
    currentPlayingPath: String?,
    isPlaying: Boolean
) {
    val available = PublicPlaylistProviders.officialKugouAvailable()

    var genreKey by remember { mutableStateOf(yuekuSelectedGenre) }
    val genre = KugouYuekuProvider.genre(genreKey)
    var subGenre by remember(genreKey) { mutableStateOf("全部") }

    var playlists by remember(genreKey) {
        mutableStateOf<List<PublicPlaylistProviders.PublicPlaylist>>(emptyList())
    }
    var pool by remember(genreKey, subGenre) { mutableStateOf<List<SongInfo>>(emptyList()) }
    var offset by remember(genreKey, subGenre) { mutableIntStateOf(0) }
    var refreshSeed by remember(genreKey, subGenre) { mutableIntStateOf(0) }
    var isLoading by remember(genreKey, subGenre) { mutableStateOf(true) }
    var loadError by remember(genreKey, subGenre) { mutableStateOf(false) }

    LaunchedEffect(genreKey) {
        yuekuSelectedGenre = genreKey
        playlists = if (available) KugouYuekuProvider.playlists(genre) else emptyList()
    }

    LaunchedEffect(genreKey, subGenre, refreshSeed) {
        if (!available) {
            isLoading = false
            loadError = false
            return@LaunchedEffect
        }
        isLoading = pool.isEmpty()
        loadError = false
        val list = runCatching {
            if (subGenre == "全部") {
                KugouYuekuProvider.pool(genre)
            } else {
                KugouYuekuProvider.subGenreSongs(genre, subGenre, refreshSeed)
            }
        }.getOrElse { emptyList() }
        pool = list
        offset = 0
        isLoading = false
        loadError = list.isEmpty()
    }

    // 展示 50 首：池子大于 50 时按 offset 轮转切片，换一批等于整批换
    val shownSongs = remember(pool, offset) {
        when {
            pool.isEmpty() -> emptyList()
            pool.size <= 50 -> pool
            else -> List(50) { pool[(offset + it) % pool.size] }
        }
    }

    fun refreshBatch() {
        if (pool.isEmpty()) {
            refreshSeed++
            return
        }
        if (subGenre != "全部") {
            // 子风格：换搜索页 + 重新打散，保证列出来的是另一批
            refreshSeed++
        } else if (pool.size <= 50) {
            pool = pool.shuffled()
            offset = 0
        } else {
            offset = (offset + 50) % pool.size
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 语种/风格导航
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(KugouYuekuProvider.GENRES) { _, item ->
                val selected = item.key == genreKey
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { if (item.key != genreKey) genreKey = item.key }
                ) {
                    Text(
                        text = item.label,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                    )
                }
            }
        }

        if (!available) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "乐库推荐需要激活后使用",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
            // 推荐歌单
            if (playlists.isNotEmpty()) {
                item {
                    Text(
                        text = "推荐歌单",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(playlists) { _, playlist ->
                            YuekuPlaylistCard(
                                playlist = playlist,
                                onClick = { onOpenPlaylist(playlist) }
                            )
                        }
                    }
                }
            }

            // 单曲推荐标题行
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "单曲推荐",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        onClick = {
                            if (shownSongs.isNotEmpty()) onPlaySong?.invoke(shownSongs, 0)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "播放",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { refreshBatch() }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "换一批",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 电子子风格 chips
            if (genre.key == "dianzi") {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(KugouYuekuProvider.ELECTRONIC_SUB_GENRES) { _, sub ->
                            val selected = sub == subGenre
                            Surface(
                                onClick = { if (sub != subGenre) subGenre = sub },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = sub,
                                    fontSize = 12.sp,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            when {
                isLoading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularWavyProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                loadError -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "加载失败，点击重试",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .clickable { refreshSeed++ }
                                .padding(8.dp)
                        )
                    }
                }
                else -> itemsIndexed(shownSongs, key = { _, s -> s.filePath }) { index, song ->
                    YuekuSongRow(
                        song = song,
                        playing = song.filePath == currentPlayingPath,
                        isPlaying = isPlaying,
                        onClick = { onPlaySong?.invoke(shownSongs, index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun YuekuPlaylistCard(
    playlist: PublicPlaylistProviders.PublicPlaylist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            if (playlist.coverUrl.isNotBlank()) {
                val painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(playlist.coverUrl)
                        .memoryCacheKey(playlist.coverUrl)
                        .crossfade(150)
                        .build()
                )
                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                        )
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatYuekuPlayCount(playlist.playCount),
                        fontSize = 10.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = playlist.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun YuekuSongRow(
    song: SongInfo,
    playing: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            val cover = song.albumArtUri
            if (!cover.isNullOrBlank()) {
                val painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(cover)
                        .memoryCacheKey(cover)
                        .crossfade(120)
                        .build()
                )
                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (playing) 0.5f else 1f },
                    contentScale = ContentScale.Crop
                )
            }
            // 与首页 RankSongCard 一致：正在播放盖一层暗罩 + 音阶动画（暂停时显示播放图标）
            if (playing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        PlayingEqBars(barWidth = 2.5.dp, barHeight = 17.dp, spacing = 1.5.dp)
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "暂停",
                            modifier = Modifier.size(22.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (playing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = if (playing) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatYuekuPlayCount(count: Long): String = when {
    count >= 100_000_000 -> String.format("%.1f亿", count / 100_000_000.0)
    count >= 10_000 -> String.format("%.1f万", count / 10_000.0)
    count > 0 -> count.toString()
    else -> ""
}
