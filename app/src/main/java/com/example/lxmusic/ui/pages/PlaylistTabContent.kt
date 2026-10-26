package com.example.lxmusic.ui.pages

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.lxmusic.data.search.PublicPlaylistProviders
import com.example.lxmusic.data.search.PublicZoneProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 歌单页分类/缓存/滚动状态（离开页面后保持）
private val playlistTabCategory = mutableStateMapOf<String, String>()
private val playlistTabCache = mutableMapOf<String, List<PublicPlaylistProviders.PublicPlaylist>>()
private val playlistTabTotals = mutableMapOf<String, Int>()
private val playlistTabPages = mutableMapOf<String, Int>()
private val playlistTabScroll = mutableMapOf<String, Int>()
private val playlistTabNoMore = mutableMapOf<String, Boolean>()

/**
 * 发现页「歌单」子页：各平台推荐歌单广场（公开接口，无需登录）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistTabContent(
    onOpenPlaylist: (PublicPlaylistProviders.PublicPlaylist) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val platform = discoverPlatform
    var showAllCategories by remember { mutableStateOf(false) }

    var categories by remember { mutableStateOf<List<PublicZoneProviders.ZoneCategory>>(emptyList()) }
    // 酷狗官方分类（激活后可用）：场景/主题/语种/风格/心情/年代
    var kgTagGroups by remember { mutableStateOf<List<PublicPlaylistProviders.TagGroup>>(emptyList()) }
    var sheetGroup by remember { mutableStateOf(0) }
    val category = playlistTabCategory[platform] ?: ""
    val cacheKey = "$platform|$category"

    var items by remember(cacheKey) {
        mutableStateOf(playlistTabCache[cacheKey] ?: emptyList())
    }
    var total by remember(cacheKey) { mutableIntStateOf(playlistTabTotals[cacheKey] ?: 0) }
    var page by remember(cacheKey) { mutableIntStateOf(playlistTabPages[cacheKey] ?: 0) }
    var isLoading by remember(cacheKey) { mutableStateOf(items.isEmpty()) }
    var isLoadingMore by remember(cacheKey) { mutableStateOf(false) }
    var loadError by remember(cacheKey) { mutableStateOf(false) }
    // 某平台翻到接口末尾（空页）后停止继续请求，避免滑到底无限重试
    var noMore by remember(cacheKey) { mutableStateOf(playlistTabNoMore[cacheKey] ?: false) }

    val gridState = remember(cacheKey) {
        LazyGridState(playlistTabScroll[cacheKey] ?: 0)
    }
    LaunchedEffect(gridState, cacheKey) {
        snapshotFlow { gridState.firstVisibleItemIndex }.collect { idx ->
            playlistTabScroll[cacheKey] = idx
        }
    }

    fun load(pageToLoad: Int, append: Boolean) {
        if (append) {
            if (isLoadingMore || isLoading || noMore) return
            isLoadingMore = true
        } else {
            isLoading = items.isEmpty()
        }
        loadError = false
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                PublicPlaylistProviders.playlists(
                    platform,
                    category.ifBlank { null },
                    pageToLoad,
                    30
                )
            }.getOrNull()
            val list = result?.items.orEmpty()
            val tot = result?.total ?: 0
            val more = result?.hasMore ?: false
            withContext(Dispatchers.Main) {
                if (list.isNotEmpty()) {
                    val merged = if (append) (items + list).distinctBy { it.platform + it.id } else list
                    items = merged
                    playlistTabCache[cacheKey] = merged
                    // 官方接口只有 has_next、没有 total：total 保持 0，由 noMore 控制是否继续
                    total = tot
                    playlistTabTotals[cacheKey] = tot
                    page = pageToLoad
                    playlistTabPages[cacheKey] = pageToLoad
                    noMore = !more
                    playlistTabNoMore[cacheKey] = !more
                    loadError = false
                } else if (append) {
                    // 翻到末尾：停止继续加载
                    noMore = true
                    playlistTabNoMore[cacheKey] = true
                    if (total <= 0) total = items.size
                } else if (items.isEmpty()) {
                    loadError = true
                }
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(platform) {
        categories = if (platform == "kg") emptyList() else runCatching {
            PublicPlaylistProviders.categories(platform)
        }.getOrElse { emptyList() }
        kgTagGroups = if (platform == "kg" && PublicPlaylistProviders.officialKugouAvailable()) {
            PublicPlaylistProviders.kugouTags()
        } else {
            emptyList()
        }
    }

    // 酷狗参考客户端的快捷分类：精选 / Hi-Res / 学习 / 下午茶
    val quickTags = remember(kgTagGroups) {
        if (kgTagGroups.isEmpty()) {
            emptyList()
        } else {
            val all = kgTagGroups.flatMap { it.tags }
            fun idOf(name: String) = all.firstOrNull { it.name == name }?.id
            buildList {
                idOf("精选")?.let {
                    add(PublicZoneProviders.ZoneCategory(platform = "kg", id = it, name = "精选"))
                }
                add(PublicZoneProviders.ZoneCategory(platform = "kg", id = "11292", name = "Hi-Res"))
                idOf("学习")?.let {
                    add(PublicZoneProviders.ZoneCategory(platform = "kg", id = it, name = "学习"))
                }
                idOf("下午茶")?.let {
                    add(PublicZoneProviders.ZoneCategory(platform = "kg", id = it, name = "下午茶"))
                }
            }.distinctBy { it.id }
        }
    }

    LaunchedEffect(cacheKey) {
        items = playlistTabCache[cacheKey] ?: emptyList()
        total = playlistTabTotals[cacheKey] ?: 0
        page = playlistTabPages[cacheKey] ?: 0
        isLoading = items.isEmpty()
        if (items.isEmpty()) load(1, append = false)
    }

    // 必须按 cacheKey/gridState 重建：remember 无 key 时闭包会一直引用
    // 第一次组合的 gridState 与状态对象，切换平台后读到的仍是旧数据 → 下滑不再加载
    val shouldLoadMore by remember(cacheKey, gridState) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            items.isNotEmpty() && !noMore && (total <= 0 || items.size < total) &&
                !isLoading && !isLoadingMore &&
                lastVisible >= items.size - 4
        }
    }
    LaunchedEffect(shouldLoadMore, cacheKey, items.size) {
        if (shouldLoadMore) load(page + 1, append = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 头部：标题 + 平台切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "歌单",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (total > 0) "${total}个歌单 · 热门推荐" else "热门推荐歌单",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 分类筛选：酷狗官方分类（激活后）优先，其次网易云/QQ 公开分类
        val chipTags: List<PublicZoneProviders.ZoneCategory> = if (platform == "kg" && kgTagGroups.isNotEmpty()) {
            val base = quickTags.toMutableList()
            if (category.isNotBlank() && base.none { it.id == category }) {
                kgTagGroups.flatMap { it.tags }.firstOrNull { it.id == category }?.let { base += it }
            }
            base
        } else {
            categories
        }
        if (chipTags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        CategoryChip(
                            label = "全部",
                            selected = category.isBlank(),
                            onClick = { playlistTabCategory[platform] = "" }
                        )
                    }
                    items(chipTags.size) { index ->
                        val c = chipTags[index]
                        CategoryChip(
                            label = c.name,
                            selected = category == c.id,
                            onClick = { playlistTabCategory[platform] = c.id }
                        )
                    }
                }
                IconButton(
                    onClick = { showAllCategories = true },
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Icon(
                        Icons.Default.GridView,
                        contentDescription = "全部分类",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // fillMaxWidth 不能省：Column 里 weight 只分配高度，宽度默认包裹内容，
        // 否则加载圈/错误提示会贴在屏幕左侧而不是居中
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                isLoading -> CircularWavyProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
                loadError && items.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("加载失败，请检查网络", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.Button(onClick = { load(1, append = false) }) {
                        Text("重试")
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { "${it.platform}_${it.id}" }) { playlist ->
                        PlaylistCard(playlist = playlist, onClick = { onOpenPlaylist(playlist) })
                    }
                    if (isLoadingMore) {
                        item(span = { GridItemSpan(3) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularWavyProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAllCategories) {
        ModalBottomSheet(
            onDismissRequest = { showAllCategories = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "全部分类",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                if (platform == "kg" && kgTagGroups.isNotEmpty()) {
                    // 酷狗官方分类：先切分组（场景/主题/语种/风格/心情/年代），再选标签
                    val activeIndex = sheetGroup.coerceIn(0, kgTagGroups.lastIndex)
                    val activeGroup = kgTagGroups[activeIndex]
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(kgTagGroups.size) { index ->
                            CategoryChip(
                                label = kgTagGroups[index].name,
                                selected = index == activeIndex,
                                onClick = { sheetGroup = index }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            CategoryChip(
                                label = "全部",
                                selected = category.isBlank(),
                                fill = true,
                                onClick = {
                                    playlistTabCategory[platform] = ""
                                    showAllCategories = false
                                }
                            )
                        }
                        items(activeGroup.tags.size) { index ->
                            val c = activeGroup.tags[index]
                            CategoryChip(
                                label = c.name,
                                selected = category == c.id,
                                fill = true,
                                onClick = {
                                    playlistTabCategory[platform] = c.id
                                    showAllCategories = false
                                }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            CategoryChip(
                                label = "全部",
                                selected = category.isBlank(),
                                fill = true,
                                onClick = {
                                    playlistTabCategory[platform] = ""
                                    showAllCategories = false
                                }
                            )
                        }
                        items(categories.size) { index ->
                            val c = categories[index]
                            CategoryChip(
                                label = c.name,
                                selected = category == c.id,
                                fill = true,
                                onClick = {
                                    playlistTabCategory[platform] = c.id
                                    showAllCategories = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }


}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    fill: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = if (fill) Modifier.fillMaxWidth() else Modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            textAlign = if (fill) TextAlign.Center else TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: PublicPlaylistProviders.PublicPlaylist,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (playlist.coverUrl.isNotBlank()) {
                    val painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(playlist.coverUrl)
                            .memoryCacheKey(playlist.coverUrl)
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
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
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatPlayCount(playlist.playCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (playlist.trackCount > 0) "${playlist.trackCount} 首" else "歌单",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatPlayCount(count: Long): String = when {
    count >= 100_000_000 -> String.format("%.1f亿", count / 100_000_000.0)
    count >= 10_000 -> String.format("%.1f万", count / 10_000.0)
    count > 0 -> count.toString()
    else -> ""
}
