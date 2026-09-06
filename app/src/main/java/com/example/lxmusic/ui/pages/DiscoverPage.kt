package com.example.lxmusic.ui.pages

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.RankItem
import com.example.lxmusic.SearchPlaylistItem
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.ui.components.SongContextMenuActions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private fun formatPlayTimes(times: Long): String {
    return when {
        times >= 100_000_000 -> String.format(Locale.US, "%.1f亿", times / 100_000_000.0)
        times >= 10_000 -> String.format(Locale.US, "%.1f万", times / 10_000.0)
        else -> times.toString()
    }
}

// 发现页顶部子 Tab 选中位置（内存级：切到底部其他页面再回来保留，
// 只有进程销毁/退出软件重进才重置为 0，与首页翻页进度同一套路）
private var discoverSubTabIndex = 0

/**
 * 发现主页面：顶部支持子 Tab 切换（【推荐】与【排行榜】等）
 * 支持点击顶部 Tab 切换与左右滑动手势（HorizontalPager）平滑过渡
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DiscoverPage(
    onRankClick: (RankItem) -> Unit,
    onPlaySong: ((List<SongInfo>, Int) -> Unit)? = null,
    onPlaylistClick: ((SearchPlaylistItem) -> Unit)? = null,
    currentPlayingPath: String? = null,
    isPlaying: Boolean = false,
    onAddToQueueNext: ((SongInfo) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    recommendListState: LazyListState = rememberLazyListState()
) {
    val scope = rememberCoroutineScope()
    val tabs = remember {
        listOf(
            DiscoverSubTab("推荐", Icons.Default.AutoAwesome),
            DiscoverSubTab("排行榜", Icons.Default.Leaderboard),
            DiscoverSubTab("精选", Icons.Default.Star),
            DiscoverSubTab("探针", Icons.Default.Science)
        )
    }
    val pagerState = rememberPagerState(
        initialPage = discoverSubTabIndex.coerceIn(0, tabs.size - 1)
    ) { tabs.size }
    // 滑动/点击后实时存档：切到底部其他 tab 导致本组合销毁，下次重建直接恢复
    LaunchedEffect(pagerState.currentPage) {
        discoverSubTabIndex = pagerState.currentPage
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部 Tab 切换栏
        DiscoverTopTabBar(
            tabs = tabs,
            selectedIndex = pagerState.currentPage,
            onTabSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(index)
                }
            }
        )

        // 子页面内容 Pager（全部页面常驻组合，切 Tab 不重建、不重刷接口）
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = tabs.size - 1
        ) { page ->
            when (page) {
                0 -> {
                    // 推荐子页面
                    RecommendTabContent(
                        onPlaySong = onPlaySong,
                        onPlaylistClick = onPlaylistClick,
                        currentPlayingPath = currentPlayingPath,
                        isPlaying = isPlaying,
                        onAddToQueueNext = onAddToQueueNext,
                        listState = recommendListState
                    )
                }
                1 -> {
                    // 排行榜子页面（原封不动保留原逻辑）
                    RankTabContent(
                        onRankClick = onRankClick,
                        listState = listState
                    )
                }
                2 -> {
                    // 精选子页面（编辑精选 /ip 歌曲列表，默认百万收藏）
                    JingxuanTabContent(
                        onPlaySong = onPlaySong,
                        currentPlayingPath = currentPlayingPath,
                        onAddToQueueNext = onAddToQueueNext
                    )
                }
                else -> {
                    // 接口探针子页面（查看乐库/编辑精选系接口真实返回，结构确定后再做正式 UI）
                    ProbeTabContent()
                }
            }
        }
    }
}

private data class DiscoverSubTab(
    val title: String,
    val icon: ImageVector
)

/**
 * 发现页顶部胶囊风格 Tab 切换器
 */
@Composable
private fun DiscoverTopTabBar(
    tabs: List<DiscoverSubTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    // Tab 栏横滑（Tab 变多后小屏不溢出）
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(tabs.size) { index ->
            val tab = tabs[index]
            val isSelected = selectedIndex == index
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                animationSpec = tween(250),
                label = "tab_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(250),
                label = "tab_text"
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = bgColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onTabSelected(index) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = textColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = textColor
                    )
                }
            }
        }
    }
}

// ==================== 推荐子页面 ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecommendTabContent(
    onPlaySong: ((List<SongInfo>, Int) -> Unit)?,
    onPlaylistClick: ((SearchPlaylistItem) -> Unit)?,
    currentPlayingPath: String?,
    isPlaying: Boolean,
    onAddToQueueNext: ((SongInfo) -> Unit)?,
    listState: LazyListState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }
    val prefs = remember { context.getSharedPreferences("discover_recommend_cache", Context.MODE_PRIVATE) }

    var playlists by remember { mutableStateOf<List<SearchPlaylistItem>>(emptyList()) }
    var hotSongs by remember { mutableStateOf<List<SongInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }

    val styleTags = remember {
        listOf("精选", "热歌", "流行", "华语", "古风", "欧美", "民谣", "摇滚", "纯音乐", "车载DJ", "二次元")
    }
    var selectedStyle by remember { mutableStateOf("精选") }

    fun loadData(style: String = "精选") {
        isLoading = true
        loadError = false
        scope.launch(Dispatchers.IO) {
            try {
                // 1. 获取精选推荐歌单
                val playlistResp = KuGouApi.service.searchPlaylist(keywords = style, page = 1, pageSize = 10)
                val fetchedPlaylists = playlistResp.data?.lists ?: emptyList()

                // 2. 获取推荐歌曲（先根据风格搜索歌曲，为空则取飙升榜）
                var fetchedSongs: List<SongInfo> = emptyList()
                try {
                    val searchResp = KuGouApi.service.search(keywords = if (style == "精选") "热歌" else style, page = 1, pageSize = 20)
                    val rawSongs = searchResp.data?.lists ?: emptyList()
                    if (rawSongs.isNotEmpty()) {
                        fetchedSongs = rawSongs.map { s ->
                            SongInfo(
                                title = s.title,
                                artist = s.artist,
                                filePath = "${s.hash}|${s.album_audio_id}",
                                albumArtUri = s.coverUrl,
                                duration = s.Duration * 1000L
                            )
                        }
                    }
                } catch (_: Exception) {}

                if (fetchedSongs.isEmpty()) {
                    // 回退方案：获取飙升榜歌曲
                    try {
                        val rankAudio = KuGouApi.service.getRankAudio(rankId = 6666L, page = 1, pageSize = 20)
                        val rankSongs = rankAudio.data?.songlist ?: emptyList()
                        fetchedSongs = rankSongs.map { s ->
                            SongInfo(
                                title = s.title,
                                artist = s.artist,
                                filePath = "${s.hash}|${s.album_audio_id}",
                                albumArtUri = s.coverUrl,
                                duration = s.durationMs
                            )
                        }
                    } catch (_: Exception) {}
                }

                withContext(Dispatchers.Main) {
                    if (fetchedPlaylists.isNotEmpty() || fetchedSongs.isNotEmpty()) {
                        playlists = fetchedPlaylists
                        hotSongs = fetchedSongs
                        // 缓存数据
                        prefs.edit()
                            .putString("playlists_json", gson.toJson(fetchedPlaylists))
                            .putString("songs_json", gson.toJson(fetchedSongs))
                            .apply()
                    }
                    isLoading = false
                    loadError = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (playlists.isEmpty() && hotSongs.isEmpty()) {
                        loadError = true
                    }
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // 先读本地缓存秒开
        val cachedPlaylists = prefs.getString("playlists_json", null)
        val cachedSongs = prefs.getString("songs_json", null)
        if (!cachedPlaylists.isNullOrBlank() || !cachedSongs.isNullOrBlank()) {
            try {
                if (!cachedPlaylists.isNullOrBlank()) {
                    val type = object : TypeToken<List<SearchPlaylistItem>>() {}.type
                    playlists = gson.fromJson(cachedPlaylists, type)
                }
                if (!cachedSongs.isNullOrBlank()) {
                    val type = object : TypeToken<List<SongInfo>>() {}.type
                    hotSongs = gson.fromJson(cachedSongs, type)
                }
                isLoading = false
            } catch (_: Exception) {}
        }
        loadData(selectedStyle)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && playlists.isEmpty() && hotSongs.isEmpty()) {
            CircularWavyProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (loadError && playlists.isEmpty() && hotSongs.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("推荐内容加载失败，请检查网络", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { loadData(selectedStyle) }) {
                    Text("重新加载")
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 风格胶囊筛选条
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(styleTags) { tag ->
                            val isTagSelected = tag == selectedStyle
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isTagSelected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        selectedStyle = tag
                                        loadData(tag)
                                    }
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = if (isTagSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isTagSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // 2. 热门精选歌单推荐区块
                if (playlists.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Headphones,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "精选推荐歌单",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(playlists, key = { it.specialid }) { item ->
                                    RecommendPlaylistCard(
                                        playlist = item,
                                        onClick = { onPlaylistClick?.invoke(item) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. 今日推荐 / 热门歌曲列表
                if (hotSongs.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Whatshot,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "今日歌曲速递",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { loadData(selectedStyle) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "换一批",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    itemsIndexed(hotSongs, key = { index, s -> s.filePath.ifEmpty { "song_$index" } }) { index, song ->
                        val isCurrent = song.filePath == currentPlayingPath
                        var showSheet by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    else Color.Transparent
                                )
                                .clickable { onPlaySong?.invoke(hotSongs, index) }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 封面
                            val coverUrl = song.albumArtUri
                            if (!coverUrl.isNullOrBlank()) {
                                val painter = rememberAsyncImagePainter(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(coverUrl)
                                        .memoryCacheKey(coverUrl)
                                        .crossfade(150)
                                        .size(160)
                                        .build()
                                )
                                Image(
                                    painter = painter,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
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
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // 歌曲名与歌手
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 15.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // 更多操作按钮
                            IconButton(
                                onClick = { showSheet = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "更多",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

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
                                    val sheetCoverUrl = song.albumArtUri
                                    if (!sheetCoverUrl.isNullOrBlank()) {
                                        val painter = rememberAsyncImagePainter(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(sheetCoverUrl)
                                                .memoryCacheKey(sheetCoverUrl)
                                                .crossfade(150)
                                                .size(200)
                                                .build()
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

                                SongContextMenuActions(
                                    song = song,
                                    onDismiss = { showSheet = false },
                                    onAddToQueueNext = { onAddToQueueNext?.invoke(song) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 推荐歌单卡片
 */
@Composable
private fun RecommendPlaylistCard(
    playlist: SearchPlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            val imgUrl = playlist.coverUrl
            if (imgUrl.isNotBlank()) {
                val painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imgUrl)
                        .memoryCacheKey(imgUrl)
                        .crossfade(150)
                        .size(240)
                        .build()
                )
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
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
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 播放量角标
            if (!playlist.play_count.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "▶ ${playlist.play_count}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = playlist.specialname ?: "推荐歌单",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ==================== 乐库 Tab 已移除（内容由精选页覆盖） ====================

/** 歌曲行（封面+歌名歌手+更多菜单，推荐/精选共用） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YuekuSongRow(
    song: SongInfo,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onAddToQueueNext: ((SongInfo) -> Unit)?
) {
    var showSheet by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val coverUrl = song.albumArtUri
        if (!coverUrl.isNullOrBlank()) {
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .memoryCacheKey(coverUrl)
                    .crossfade(150)
                    .size(160)
                    .build()
            )
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
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
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = { showSheet = true },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "更多",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp)
            )
        }
    }

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
                val sheetCoverUrl = song.albumArtUri
                if (!sheetCoverUrl.isNullOrBlank()) {
                    val painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(sheetCoverUrl)
                            .memoryCacheKey(sheetCoverUrl)
                            .crossfade(150)
                            .size(200)
                            .build()
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
            SongContextMenuActions(
                song = song,
                onDismiss = { showSheet = false },
                onAddToQueueNext = { onAddToQueueNext?.invoke(song) }
            )
        }
    }
}

// ==================== 精选子页面（编辑精选 /ip 歌曲列表） ====================
// 默认百万收藏（99070）；预设+手填 ip_id 任意切（banner/歌曲 ips 里抠到的 id 都能直接听）。
// total 可达 9000+，分页追加加载。

private val JINGXUAN_PRESETS = listOf(
    "百万收藏" to 99070L,
    "抖音专区" to 88104L,
    "网络专区" to 87634L
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JingxuanTabContent(
    onPlaySong: ((List<SongInfo>, Int) -> Unit)?,
    currentPlayingPath: String?,
    onAddToQueueNext: ((SongInfo) -> Unit)?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }
    val prefs = remember { context.getSharedPreferences("jingxuan_cache", Context.MODE_PRIVATE) }
    val pageSize = 30

    var ipIdText by remember { mutableStateOf("99070") }
    var songs by remember { mutableStateOf<List<SongInfo>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    fun currentIpId(): Long = ipIdText.trim().toLongOrNull() ?: 0L
    fun noMore(): Boolean = total > 0 && songs.size >= total
    val presetName = JINGXUAN_PRESETS.firstOrNull { it.second == currentIpId() }?.first

    fun loadPage(targetPage: Int, append: Boolean) {
        val id = currentIpId()
        if (id <= 0) {
            loadError = "请先填写有效的 ip_id"
            isLoading = false
            return
        }
        if (append) isLoadingMore = true else {
            isLoading = true
            loadError = null
        }
        scope.launch(Dispatchers.IO) {
            try {
                val resp = KuGouApi.service.getIpSongs(id, "audios", targetPage, pageSize, System.currentTimeMillis())
                val list = resp.data.orEmpty().mapNotNull { it.toSongInfo() }
                withContext(Dispatchers.Main) {
                    songs = if (append) songs + list else list
                    total = resp.total
                    page = targetPage
                    if (!append && list.isNotEmpty()) {
                        prefs.edit()
                            .putString("songs_$id", gson.toJson(list))
                            .putInt("total_$id", total)
                            .apply()
                    }
                    isLoading = false
                    isLoadingMore = false
                }
            } catch (e: Exception) {
                android.util.Log.w("LxMusic_Jingxuan", "/ip?id=$id 请求失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (!append && songs.isEmpty()) loadError = e.message ?: "未知错误"
                    isLoading = false
                    isLoadingMore = false
                }
            }
        }
    }

    fun reload() {
        songs = emptyList()
        total = 0
        page = 1
        loadPage(1, false)
    }

    LaunchedEffect(Unit) {
        // 默认 id 的第一页缓存秒开
        try {
            prefs.getString("songs_99070", null)?.let { json ->
                gson.fromJson<List<SongInfo>>(json, object : TypeToken<List<SongInfo>>() {}.type)?.let { l ->
                    if (l.isNotEmpty()) {
                        songs = l
                        total = prefs.getInt("total_99070", 0)
                    }
                }
            }
        } catch (_: Exception) {}
        loadPage(1, false)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 头部：标题 + 播放全部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = presetName ?: "编辑精选",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (total > 0) "ip ${ipIdText.trim()} · 共 $total 首" else "ip ${ipIdText.trim()}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { if (songs.isNotEmpty()) onPlaySong?.invoke(songs, 0) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "播放全部",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // ip 切换：预设 chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(JINGXUAN_PRESETS) { preset ->
                val name = preset.first
                val id = preset.second
                val isSelected = currentIpId() == id
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            ipIdText = id.toString()
                            songs = emptyList()
                            total = 0
                            page = 1
                            loadPage(1, false)
                        }
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 手填任意 ip_id（banner 或歌曲 ips 里找）
        OutlinedTextField(
            value = ipIdText,
            onValueChange = { ipIdText = it.filter { c -> c.isDigit() } },
            label = { Text("ip_id（banner 或歌曲 ips 里找）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { reload() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            if (isLoading && songs.isEmpty()) {
                CircularWavyProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (loadError != null && songs.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("精选加载失败：$loadError", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { reload() }) {
                        Text("重试")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 180.dp)
                ) {
                    itemsIndexed(songs, key = { index, s -> s.filePath.ifEmpty { "jx_$index" } }) { index, song ->
                        YuekuSongRow(
                            song = song,
                            isCurrent = song.filePath == currentPlayingPath,
                            onClick = { onPlaySong?.invoke(songs, index) },
                            onAddToQueueNext = onAddToQueueNext
                        )
                    }
                    if (isLoadingMore) {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                    } else if (!noMore() && songs.isNotEmpty()) {
                        // 滑到底自动拉下一页（key 带 page，翻页后重新触发）
                        item(key = "more_$page") {
                            LaunchedEffect(Unit) { loadPage(page + 1, true) }
                        }
                    } else if (noMore() && songs.isNotEmpty()) {
                        item(key = "end") {
                            Text(
                                text = "— 到底了，共 $total 首 —",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 探针 JSON 工具 ====================
// 结构摘要：对象只列 key+类型，数组只给长度+首元素结构，深度/数量封顶，专治 373KB 巨包。
// 先看结构定骨架，再看原文抠字段，完整包一键存 Download。

private const val PROBE_SUMMARY_MAX_DEPTH = 4
private const val PROBE_SUMMARY_MAX_KEYS = 40

private fun summarizeJson(json: String): String {
    return try {
        // 用 fromJson 而不用 JsonParser.parseString：兼容手头这个老版本 Gson
        val el = com.google.gson.Gson().fromJson(json, com.google.gson.JsonElement::class.java)
        buildString { appendJsonSummary(el, "", 0, this) }.ifBlank { "(空)" }
    } catch (e: Exception) {
        "结构解析失败: ${e.message}"
    }
}

private fun appendJsonSummary(
    el: com.google.gson.JsonElement,
    indent: String,
    depth: Int,
    sb: StringBuilder
) {
    if (depth > PROBE_SUMMARY_MAX_DEPTH) {
        sb.append("…")
        return
    }
    when {
        el.isJsonObject -> {
            val obj = el.asJsonObject
            sb.append("object{${obj.size()}}")
            if (depth == PROBE_SUMMARY_MAX_DEPTH) return
            var count = 0
            for ((k, v) in obj.entrySet()) {
                if (count >= PROBE_SUMMARY_MAX_KEYS) {
                    sb.append("\n$indent  …(+${obj.size() - count}个key)")
                    break
                }
                sb.append("\n$indent- $k: ")
                appendJsonSummary(v, "$indent  ", depth + 1, sb)
                count++
            }
        }
        el.isJsonArray -> {
            val arr = el.asJsonArray
            sb.append("array[${arr.size()}]")
            if (arr.size() > 0 && depth < PROBE_SUMMARY_MAX_DEPTH) {
                sb.append(" 首元素: ")
                appendJsonSummary(arr[0], "$indent  ", depth + 1, sb)
            }
        }
        el.isJsonPrimitive -> {
            val p = el.asJsonPrimitive
            sb.append(
                when {
                    p.isString -> "string(\"${p.asString.take(30)}\")"
                    p.isNumber -> "number(${p.asString.take(20)})"
                    p.isBoolean -> "bool(${p.asBoolean})"
                    else -> "primitive"
                }
            )
        }
        else -> sb.append("null")
    }
}

/** 探针原文存到 Download（完整无截断，文件名带接口名+时间戳）。API 29 以下返回 null。 */
private fun saveProbeJsonToDownload(context: Context, endpoint: String, content: String): String? {
    return try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val safeName = endpoint.replace('/', '_')
        val name = "lxprobe_${safeName}_${System.currentTimeMillis()}.json"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
        ) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        "Download/$name"
    } catch (e: Exception) {
        Log.w(YUEKU_PROBE_TAG, "[探针] 保存文件失败: ${e.message}")
        null
    }
}

// ==================== 接口探针子页面（临时调试用） ====================
// 目标：把乐库系（/yueku、/yueku/banner、/yueku/fm）与编辑精选系
// （/top/ip、/ip、/ip/playlist、/ip/zone、/ip/zone/home）的真实返回抓出来看。
// 用法：进发现页 → 点“探针” → 切换接口（需 id 的接口先填 id，/ip 还可选 type）→
// 完整 JSON 打到 Logcat（tag=LxMusic_Probe），屏幕显示摘要 + 前 15000 字符预览（长按可复制）。
// 拿到结构后，第二步再做正式 UI。

private const val YUEKU_PROBE_TAG = "LxMusic_Probe"
private const val YUEKU_PROBE_PREVIEW_LIMIT = 15000

/** 需要填 id 才能调用的探针接口 */
private val PROBE_ID_REQUIRED = setOf("ip", "ip/playlist", "ip/zone/home")

/** 各需 id 接口的默认示例 id（来自官方文档调用例子） */
private fun probeDefaultId(endpoint: String): String = if (endpoint == "ip/zone/home") "329" else "87473"

private fun logChunked(tag: String, content: String) {
    var start = 0
    var index = 0
    while (start < content.length) {
        val end = minOf(start + 3500, content.length)
        Log.d(tag, "[$index] ${content.substring(start, end)}")
        start = end
        index++
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProbeTabContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val endpoints = remember {
        listOf("yueku", "yueku/banner", "yueku/fm", "top/ip", "ip", "ip/playlist", "ip/zone", "ip/zone/home")
    }
    var selectedEndpoint by remember { mutableStateOf("yueku") }
    var probeId by remember { mutableStateOf("87473") }
    val ipTypes = remember { listOf("audios", "albums", "videos", "author_list") }
    var selectedIpType by remember { mutableStateOf("audios") }

    var rawJson by remember { mutableStateOf<String?>(null) }
    var structureSummary by remember { mutableStateOf<String?>(null) }
    var showStructure by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var respLength by remember { mutableIntStateOf(0) }
    var costMs by remember { mutableStateOf(0L) }

    fun loadData() {
        val endpoint = selectedEndpoint
        val id = probeId.trim().toLongOrNull() ?: 0L
        val ipType = selectedIpType
        if (endpoint in PROBE_ID_REQUIRED && id <= 0) {
            errorMsg = "请先填写有效的 id（正整数）"
            return
        }
        isLoading = true
        errorMsg = null
        rawJson = null
        structureSummary = null
        respLength = 0
        costMs = 0
        scope.launch(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            try {
                // timestamp 打散后端 2 分钟 URL 缓存，保证每次都打到真实上游
                val ts = System.currentTimeMillis()
                val body = when (endpoint) {
                    "yueku/banner" -> KuGouApi.service.getYuekuBannerRaw(ts)
                    "yueku/fm" -> KuGouApi.service.getYuekuFmRaw(ts)
                    "top/ip" -> KuGouApi.service.getTopIpRaw(ts)
                    "ip" -> KuGouApi.service.getIpRaw(id, ipType, 1, 30, ts)
                    "ip/playlist" -> KuGouApi.service.getIpPlaylistRaw(id, 1, 30, ts)
                    "ip/zone" -> KuGouApi.service.getIpZoneRaw(ts)
                    "ip/zone/home" -> KuGouApi.service.getIpZoneHomeRaw(id, ts)
                    else -> KuGouApi.service.getYuekuRaw(ts)
                }
                val str = runCatching { body.string() }.getOrDefault("")
                runCatching { body.close() }
                val cost = System.currentTimeMillis() - t0
                // 结构摘要后台算好，UI 直接显示
                val summary = summarizeJson(str)
                val extra = when {
                    endpoint == "ip" -> "&id=$id&type=$ipType"
                    endpoint in PROBE_ID_REQUIRED -> "&id=$id"
                    else -> ""
                }
                Log.d(YUEKU_PROBE_TAG, "=== [接口探针] endpoint=$endpoint$extra, 长度=${str.length}, 耗时=${cost}ms ===")
                if (str.isNotBlank()) logChunked(YUEKU_PROBE_TAG, str)
                withContext(Dispatchers.Main) {
                    rawJson = str
                    structureSummary = summary
                    respLength = str.length
                    costMs = cost
                    isLoading = false
                }
            } catch (e: Exception) {
                Log.w(YUEKU_PROBE_TAG, "[接口探针] endpoint=$endpoint 请求失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    errorMsg = e.message ?: "未知错误"
                    costMs = System.currentTimeMillis() - t0
                    isLoading = false
                }
            }
        }
    }

    // 切换接口时：需 id 的接口填入默认示例 id，并自动抓取一次
    LaunchedEffect(selectedEndpoint) {
        if (selectedEndpoint in PROBE_ID_REQUIRED) probeId = probeDefaultId(selectedEndpoint)
        loadData()
    }
    // /ip 的 type 切换时自动重抓
    LaunchedEffect(selectedIpType) {
        if (selectedEndpoint == "ip") loadData()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && rawJson == null && errorMsg == null) {
            CircularWavyProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 接口切换（横滑）+ 重抓按钮
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(endpoints) { ep ->
                            val isSelected = ep == selectedEndpoint
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { selectedEndpoint = ep }
                            ) {
                                Text(
                                    text = ep,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { loadData() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重新抓取",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 需 id 的接口：id 输入框（数字键盘，回车直接抓取）
                if (selectedEndpoint in PROBE_ID_REQUIRED) {
                    OutlinedTextField(
                        value = probeId,
                        onValueChange = { probeId = it.filter { c -> c.isDigit() } },
                        label = { Text("id（/$selectedEndpoint 必选，例：${probeDefaultId(selectedEndpoint)}）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { loadData() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // /ip 的 type 选择
                if (selectedEndpoint == "ip") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ipTypes) { t ->
                            val isSelected = t == selectedIpType
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedIpType = t }
                            ) {
                                Text(
                                    text = t,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // 视图切换（原文/结构）+ 保存完整 JSON 到 Download
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("原文" to false, "结构" to true).forEach { (label, isStruct) ->
                        val isSelected = showStructure == isStruct
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showStructure = isStruct }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            val json = rawJson
                            val path = if (!json.isNullOrBlank()) {
                                saveProbeJsonToDownload(context, selectedEndpoint, json)
                            } else null
                            Toast.makeText(
                                context,
                                if (path != null) "已保存到 $path" else "保存失败（需 Android 10+ 且先抓到数据）",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "保存完整JSON到Download",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 摘要行
                if (errorMsg != null) {
                    Text(
                        text = "请求失败（${costMs}ms）：$errorMsg",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = { loadData() }) {
                        Text("重试")
                    }
                } else {
                    Text(
                        text = "endpoint=/$selectedEndpoint ｜ 长度=$respLength 字符 ｜ 耗时=${costMs}ms ｜ 完整内容见 Logcat($YUEKU_PROBE_TAG)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 原始 JSON 预览（长按可复制）
                val json = rawJson
                if (!json.isNullOrBlank()) {
                    val body = if (showStructure) {
                        structureSummary ?: "结构生成中…"
                    } else if (json.length > YUEKU_PROBE_PREVIEW_LIMIT) {
                        json.take(YUEKU_PROBE_PREVIEW_LIMIT) + "\n\n…（已截断，只显示前 $YUEKU_PROBE_PREVIEW_LIMIT 字符，完整包点右上保存按钮存到 Download）"
                    } else json
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = body,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(160.dp))
                }
            }
        }
    }
}

// ==================== 排行榜子页面（原封不动保留原逻辑） ====================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RankTabContent(
    onRankClick: (RankItem) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    var rankList by remember { mutableStateOf<List<RankItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }
    val prefs = remember { context.getSharedPreferences("rank_cache", Context.MODE_PRIVATE) }

    val allowedRankIds = setOf(
        8888L,   // TOP500
        6666L,   // 飙升榜
        74534L,  // 新歌榜
        82831L,  // 网络热歌榜
        52144L,  // 抖音热歌酷狗榜
        24971L,  // DJ热歌榜
        59895L,  // R&B榜
        33160L,  // 电音榜
        59900L,  // 纯音乐榜
        85432L,  // 百万收藏榜
        35811L,  // 会员热歌榜
        33162L,  // ACG新歌榜
        49223L,  // 90后热歌榜
        49224L   // 00后热歌榜
    )

    val imageLoader = coil.compose.LocalImageLoader.current

    LaunchedEffect(Unit) {
        val cached = prefs.getString("rank_list_json", null)
        if (!cached.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<RankItem>>() {}.type
                val list: List<RankItem> = gson.fromJson(cached, type)
                if (list.isNotEmpty()) {
                    rankList = list
                    isLoading = false
                    list.forEach { rank ->
                        val url = rank.coverUrl
                        if (url.isNotBlank()) {
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

        scope.launch(Dispatchers.IO) {
            try {
                val resp = KuGouApi.service.getRankList()
                if (resp.data?.info != null) {
                    val filtered = resp.data.info.filter { it.rankid in allowedRankIds }
                    rankList = filtered
                    prefs.edit().putString("rank_list_json", gson.toJson(filtered)).apply()
                }
                loadError = false
            } catch (_: Exception) {
                if (rankList.isEmpty()) loadError = true
            } finally {
                isLoading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularWavyProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (loadError && rankList.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("加载失败，请检查网络", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    isLoading = true
                    loadError = false
                    scope.launch(Dispatchers.IO) {
                        try {
                            val resp = KuGouApi.service.getRankList()
                            if (resp.data?.info != null) {
                                rankList = resp.data.info.filter { it.rankid in allowedRankIds }
                                prefs.edit().putString("rank_list_json", gson.toJson(rankList)).apply()
                            }
                        } catch (_: Exception) {
                            loadError = true
                        } finally {
                            isLoading = false
                        }
                    }
                }) { Text("重试") }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    count = rankList.size,
                    key = { rankList[it].rankid }
                ) { index ->
                    RankCard(
                        rank = rankList[index],
                        onClick = { onRankClick(rankList[index]) }
                    )
                }
            }
        }
    }
}

@Composable
fun RankCard(rank: RankItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val rankColors = listOf(
        Color(0xFFFF4444),
        Color(0xFFFF8800),
        Color(0xFFFFCC00)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 标题行
            Text(
                text = rank.rankname ?: "排行榜",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 内容区：封面 + 歌曲列表
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 左侧封面图
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    val imgUrl = rank.coverUrl
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
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 底部播放量遮罩
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.6f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatPlayTimes(rank.play_times),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "播放",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // 右侧歌曲列表
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    val previewSongs = rank.songinfo?.take(3) ?: emptyList()
                    if (previewSongs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "暂无歌曲信息",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        previewSongs.forEachIndexed { i, song ->
                            val songName = song.songname ?: song.name ?: ""
                            val artist = song.author ?: ""
                            val display = if (artist.isNotBlank()) "$songName - $artist" else songName

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${i + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = rankColors.getOrElse(i) { MaterialTheme.colorScheme.onSurface },
                                    modifier = Modifier.width(16.dp)
                                )
                                Text(
                                    text = display,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
