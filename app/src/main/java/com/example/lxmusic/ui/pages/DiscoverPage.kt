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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Radio
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
import com.example.lxmusic.fetchIpZoneBatch
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
            DiscoverSubTab("专区", Icons.Default.GridView),
            DiscoverSubTab("频道", Icons.Default.Radio),
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
                    // 专区子页面（48 专区列表，点播随机 50 首）
                    JingxuanTabContent(
                        onPlaySong = onPlaySong,
                        currentPlayingPath = currentPlayingPath,
                        onAddToQueueNext = onAddToQueueNext
                    )
                }
                3 -> {
                    // 频道子页面（zone/home 系专区完整页，v1 先做 Hi-Res）
                    PindaoTabContent(
                        zoneId = 21L,
                        channelName = "Hi-Res专区",
                        channelDesc = "无损音质 · 歌曲 + 相关歌单",
                        onPlaySong = onPlaySong,
                        onPlaylistClick = onPlaylistClick,
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

// ==================== 专区子页面（48 个编辑精选专区） ====================
// 每行一个专区（封面+名），点行或点播放键：用共用备货 helper 随机备 50 首即播。
// 游标与首页/乐库共用同一份（yueku_cache），全局轮转不重复。
// 无 ip_id 的 4 个专区（Hi-Res/青春/音乐人/热门自制）暂只支持详情页，行内提示。

private data class ZoneEntry(val name: String, val ipId: Long?, val iconUrl: String, val altZoneId: Long? = null)

private val ZONE_ENTRIES = listOf(
    ZoneEntry("DJ", 87641L, "https://imgessl.kugou.com/quickentry/20230704/20230704233204541061.jpg"),
    ZoneEntry("抖音", 88104L, "https://imgessl.kugou.com/quickentry/20230704/20230704233232980023.jpg"),
    ZoneEntry("经典老歌", 88226L, "https://imgessl.kugou.com/quickentry/20230704/20230704235426413521.jpg"),
    ZoneEntry("儿童", 87639L, "https://imgessl.kugou.com/quickentry/20230704/20230704232229905515.jpg"),
    ZoneEntry("车载", 87963L, "https://imgessl.kugou.com/quickentry/20230704/20230704232328671616.jpg"),
    ZoneEntry("纯音乐", 87747L, "https://imgessl.kugou.com/quickentry/20230704/20230704224558479280.jpg"),
    ZoneEntry("广场舞", 87992L, "https://imgessl.kugou.com/quickentry/20230704/20230704224319462864.jpg"),
    ZoneEntry("网络", 87634L, "https://imgessl.kugou.com/quickentry/20230704/20230704223003901012.jpg"),
    ZoneEntry("粤语", 87899L, "https://imgessl.kugou.com/quickentry/20230704/20230704222858744159.jpg"),
    ZoneEntry("80后", 88257L, "https://imgessl.kugou.com/quickentry/20230704/20230704222656156172.jpg"),
    ZoneEntry("90后", 88254L, "https://imgessl.kugou.com/quickentry/20230704/20230704222623938557.jpg"),
    ZoneEntry("欧美", 87780L, "https://imgessl.kugou.com/quickentry/20230704/20230704223719978571.jpg"),
    ZoneEntry("国风", 87715L, "https://imgessl.kugou.com/quickentry/20230704/20230704222315324865.jpg"),
    ZoneEntry("商铺", 87985L, "https://imgessl.kugou.com/quickentry/20230704/20230704222027689293.jpg"),
    ZoneEntry("伤感", 98934L, "https://imgessl.kugou.com/quickentry/20230704/20230704221932706154.jpg"),
    ZoneEntry("放松", 88103L, "https://imgessl.kugou.com/quickentry/20230704/20230704220928163940.jpg"),
    ZoneEntry("70后", 87977L, "https://imgessl.kugou.com/quickentry/20230704/20230704220631916317.jpg"),
    ZoneEntry("快乐", 88098L, "https://imgessl.kugou.com/quickentry/20230704/20230704220513691102.jpg"),
    ZoneEntry("睡前", 87990L, "https://imgessl.kugou.com/quickentry/20230704/20230704220413666683.jpg"),
    ZoneEntry("闽南语", 87900L, "https://imgessl.kugou.com/quickentry/20230704/20230704215606692913.jpg"),
    ZoneEntry("电音", 87746L, "https://imgessl.kugou.com/quickentry/20230704/20230704215446482578.jpg"),
    ZoneEntry("K歌必听", 87986L, "https://imgessl.kugou.com/quickentry/20230704/20230704215346999862.jpg"),
    ZoneEntry("健身", 87988L, "https://imgessl.kugou.com/quickentry/20230704/20230704221436255868.jpg"),
    ZoneEntry("影视", 88213L, "https://imgessl.kugou.com/quickentry/20230704/20230704215016764606.jpg"),
    ZoneEntry("游戏", 87952L, "https://imgessl.kugou.com/quickentry/20230704/20230704214615547779.jpg"),
    ZoneEntry("韩流", 87782L, "https://imgessl.kugou.com/quickentry/20230704/20230704213846185586.jpg"),
    ZoneEntry("咖啡馆", 87984L, "https://imgessl.kugou.com/quickentry/20230704/20230704213343318463.jpg"),
    ZoneEntry("00后", 88268L, "https://imgessl.kugou.com/quickentry/20230704/20230704212744775191.jpg"),
    ZoneEntry("解压", 88089L, "https://imgessl.kugou.com/quickentry/20230704/20230704212607857996.jpg"),
    ZoneEntry("甜蜜", 88093L, "https://imgessl.kugou.com/quickentry/20230704/20230704212112107046.jpg"),
    ZoneEntry("舞曲", 87749L, "https://imgessl.kugou.com/quickentry/20230704/20230704190657380671.jpg"),
    ZoneEntry("国语", 87898L, "https://imgessl.kugou.com/quickentry/20230704/20230704190506111298.jpg"),
    ZoneEntry("戏曲", 87964L, "https://imgessl.kugou.com/quickentry/20230704/20230704190205555372.jpg"),
    ZoneEntry("民谣", 87744L, "https://imgessl.kugou.com/quickentry/20230704/20230704175555940771.jpg"),
    ZoneEntry("流行", 87473L, "https://imgessl.kugou.com/quickentry/20230704/20230704185939164073.jpg"),
    ZoneEntry("青春专区", null, "https://imgessl.kugou.com/quickentry/20220507/20220507122307361033.jpg"),
    ZoneEntry("综艺专区", 89714L, "https://imgessl.kugou.com/quickentry/20220310/20220310175729970544.jpg"),
    ZoneEntry("首发专区", 94940L, "https://imgessl.kugou.com/quickentry/20220310/20220310175942769122.jpg"),
    ZoneEntry("音乐人专区", null, "https://imgessl.kugou.com/quickentry/20220310/20220310175926259503.jpg"),
    ZoneEntry("厂牌音乐", 91473L, "https://imgessl.kugou.com/quickentry/20221013/20221013152725767975.png"),
    ZoneEntry("热门自制节目专区", null, "https://imgessl.kugou.com/quickentry/20220424/20220424181308236196.jpg"),
    ZoneEntry("情歌", 88208L, "https://imgessl.kugou.com/quickentry/20230704/20230704224425990324.jpg")
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
    val prefs = remember { context.getSharedPreferences("yueku_cache", Context.MODE_PRIVATE) }

    var loadingId by remember { mutableStateOf<Long?>(null) }

    fun playZone(ipId: Long) {
        if (loadingId != null) return
        scope.launch {
            loadingId = ipId
            val (list, _) = fetchIpZoneBatch(
                ipId, 2, 30, 50,
                getCursor = { prefs.getInt("zone_page_$ipId", 1) },
                setCursor = { prefs.edit().putInt("zone_page_$ipId", it).apply() }
            )
            loadingId = null
            if (list.isNotEmpty()) {
                onPlaySong?.invoke(list, 0)
            } else {
                android.widget.Toast.makeText(context, "没备到歌，换一个专区试试", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 头部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "专区",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "42个专区 · 点播随机50首",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 180.dp)
        ) {
            items(ZONE_ENTRIES, key = { it.name }) { zone ->
                ZoneRow(
                    zone = zone,
                    loading = loadingId == zone.ipId,
                    onPlay = {
                        val id = zone.ipId
                        if (id != null) {
                            playZone(id)
                        } else {
                            android.widget.Toast.makeText(context, "该专区暂只支持详情页，开发中", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

/** 专区行（歌单选项样式：封面 + 名 + 右侧播放键） */
@Composable
private fun ZoneRow(
    zone: ZoneEntry,
    loading: Boolean,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onPlay() }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val url = zone.iconUrl
        if (url.isNotBlank()) {
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .memoryCacheKey(url)
                    .crossfade(150)
                    .size(160)
                    .build()
            )
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = zone.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (zone.ipId == null) "详情页开发中" else "随机50首 · 编辑精选",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp
            )
        } else if (zone.ipId != null) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ==================== 频道子页面（zone/home 系专区完整页） ====================
// 参数化 zoneId + 名字：青春(269)/音乐人(33)/热门自制(329)后续加进来就是调一次函数。
// 内容 = 模块里所有可播歌曲（歌单样式） + 相关歌单（横滑卡片，gid 直开详情）。

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PindaoTabContent(
    zoneId: Long,
    channelName: String,
    channelDesc: String,
    onPlaySong: ((List<SongInfo>, Int) -> Unit)?,
    onPlaylistClick: ((SearchPlaylistItem) -> Unit)?,
    currentPlayingPath: String?,
    onAddToQueueNext: ((SongInfo) -> Unit)?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }
    val prefs = remember { context.getSharedPreferences("channel_cache", Context.MODE_PRIVATE) }

    var songs by remember(zoneId) { mutableStateOf<List<SongInfo>>(emptyList()) }
    var playlists by remember(zoneId) { mutableStateOf<List<SearchPlaylistItem>>(emptyList()) }
    var isLoading by remember(zoneId) { mutableStateOf(true) }
    var loadError by remember(zoneId) { mutableStateOf<String?>(null) }

    fun loadData() {
        isLoading = true
        loadError = null
        scope.launch(Dispatchers.IO) {
            try {
                val ts = System.currentTimeMillis()
                val resp = KuGouApi.service.getIpZoneHome(zoneId, ts)
                val modules = resp.data?.module.orEmpty()
                modules.forEachIndexed { i, m ->
                    val items = m.items.orEmpty()
                    android.util.Log.d(
                        "LxMusic_Channel",
                        "[频道] id=$zoneId 模块$i module_id=${m.moduleId} 条目=${items.size} " +
                            "有hash=${items.count { !it.extend?.hash.isNullOrBlank() }} " +
                            "有gid=${items.count { !it.extend?.globalSpecialId.isNullOrBlank() }}"
                    )
                }
                val items = modules.flatMap { it.items.orEmpty() }
                var fetchedSongs = items.mapNotNull { it.toSongInfo() }
                val fetchedPlaylists = items.mapNotNull { it.toSearchPlaylistItem() }.distinctBy { it.gid }
                // 兜底：模块里没有直给的歌，就从头几个歌单里顺序各拿第一页凑 50 首（够即停）
                if (fetchedSongs.isEmpty() && fetchedPlaylists.isNotEmpty()) {
                    val gids = fetchedPlaylists.take(4).mapNotNull { it.gid?.takeIf { g -> g.isNotBlank() } }
                    val pooled = mutableListOf<SongInfo>()
                    for (gid in gids) {
                        if (pooled.size >= 50) break
                        val part = runCatching { KuGouApi.fetchSpecialPlaylistSongs(0, gid, 1, 30) }.getOrNull()?.first.orEmpty()
                        part.filter { s -> pooled.none { it.filePath == s.filePath } }.forEach { pooled.add(it) }
                    }
                    android.util.Log.d("LxMusic_Channel", "[频道] id=$zoneId 歌单凑歌: ${gids.size}个歌单 → ${pooled.size}首")
                    fetchedSongs = pooled.shuffled().take(50)
                }
                android.util.Log.d(
                    "LxMusic_Channel",
                    "=== [频道] id=$zoneId 模块=${resp.data?.module?.size ?: 0} 歌曲=${fetchedSongs.size} 歌单=${fetchedPlaylists.size} ==="
                )
                withContext(Dispatchers.Main) {
                    if (fetchedSongs.isNotEmpty() || fetchedPlaylists.isNotEmpty()) {
                        songs = fetchedSongs
                        playlists = fetchedPlaylists
                        prefs.edit()
                            .putString("songs_$zoneId", gson.toJson(songs))
                            .putString("playlists_$zoneId", gson.toJson(playlists))
                            .apply()
                    } else if (songs.isEmpty() && playlists.isEmpty()) {
                        loadError = "这个专区暂无可播内容"
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                android.util.Log.w("LxMusic_Channel", "[频道] id=$zoneId 失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (songs.isEmpty() && playlists.isEmpty()) loadError = e.message ?: "未知错误"
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(zoneId) {
        // 缓存秒开
        try {
            prefs.getString("songs_$zoneId", null)?.let { json ->
                gson.fromJson<List<SongInfo>>(json, object : TypeToken<List<SongInfo>>() {}.type)?.let { l ->
                    if (l.isNotEmpty()) songs = l
                }
            }
            prefs.getString("playlists_$zoneId", null)?.let { json ->
                gson.fromJson<List<SearchPlaylistItem>>(json, object : TypeToken<List<SearchPlaylistItem>>() {}.type)?.let { l ->
                    if (l.isNotEmpty()) playlists = l
                }
            }
        } catch (_: Exception) {}
        // 缓存命中也刷新一次，保证内容新鲜
        loadData()
    }

    fun hasData() = songs.isNotEmpty() || playlists.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && !hasData()) {
            CircularWavyProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (loadError != null && !hasData()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("频道加载失败：$loadError", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { loadData() }) {
                    Text("重试")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 头部：频道名 + 播放全部
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = channelName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when {
                                    songs.isNotEmpty() -> "$channelDesc · 共 ${songs.size} 首"
                                    playlists.isNotEmpty() -> "$channelDesc · ${playlists.size}个歌单"
                                    else -> channelDesc
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 有可播歌曲才显示播放全部（纯歌单型专区不显示）
                        if (songs.isNotEmpty()) {
                            IconButton(
                                onClick = { onPlaySong?.invoke(songs, 0) },
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
                    }
                }

                // 相关歌单（横滑卡片，gid 直开详情页）
                if (playlists.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "相关歌单",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(playlists, key = { i, p -> "ch_${p.gid}_$i" }) { _, item ->
                                    RecommendPlaylistCard(
                                        playlist = item,
                                        onClick = { onPlaylistClick?.invoke(item) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 歌曲列表
                if (songs.isNotEmpty()) {
                    item {
                        Text(
                            text = "歌曲",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    itemsIndexed(songs, key = { index, s -> s.filePath.ifEmpty { "ch_s_$index" } }) { index, song ->
                        YuekuSongRow(
                            song = song,
                            isCurrent = song.filePath == currentPlayingPath,
                            onClick = { onPlaySong?.invoke(songs, index) },
                            onAddToQueueNext = onAddToQueueNext
                        )
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
