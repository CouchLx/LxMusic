@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.example.lxmusic.ui.pages

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.lxmusic.HotSearchItem
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.SearchPlaylistItem
import com.example.lxmusic.SearchSuggestItem
import com.example.lxmusic.ui.components.SongContextMenuActions
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.ui.components.IOLoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchTopBar(
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    externalQuery: String = ""
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var isEditing by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // 搜索建议
    var suggestList by remember { mutableStateOf<List<SearchSuggestItem>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var isSelectingSuggestion by remember { mutableStateOf(false) }
    var isExternalUpdate by remember { mutableStateOf(false) }

    // 同步外部搜索词
    LaunchedEffect(externalQuery) {
        if (externalQuery.isNotBlank() && externalQuery != textFieldValue.text) {
            isExternalUpdate = true
            textFieldValue = TextFieldValue(externalQuery)
            isEditing = false
            showSuggestions = false
            suggestList = emptyList()
        }
    }

    // 自动弹出键盘（首次进入）
    LaunchedEffect(Unit) {
        delay(200)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    // 重新进入编辑状态时聚焦并全选
    LaunchedEffect(isEditing) {
        if (isEditing) {
            delay(100)
            textFieldValue = TextFieldValue(
                text = textFieldValue.text,
                selection = TextRange(0, textFieldValue.text.length)
            )
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // 搜索建议（防抖）
    LaunchedEffect(textFieldValue.text) {
        if (isExternalUpdate) { isExternalUpdate = false; return@LaunchedEffect }
        if (isSelectingSuggestion) { isSelectingSuggestion = false; return@LaunchedEffect }
        if (textFieldValue.text.isBlank()) { suggestList = emptyList(); showSuggestions = false; return@LaunchedEffect }
        delay(300)
        try {
            val resp = KuGouApi.service.getSearchSuggest(textFieldValue.text)
            suggestList = resp.data?.flatMap { it.RecordDatas ?: emptyList() } ?: emptyList()
            showSuggestions = suggestList.isNotEmpty()
        } catch (_: Exception) { suggestList = emptyList() }
    }

    Column {
        TopAppBar(
            title = {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clickable {
                            if (!isEditing) {
                                isEditing = true
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (isEditing) {
                            BasicTextField(
                                value = textFieldValue,
                                onValueChange = { textFieldValue = it },
                                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    if (textFieldValue.text.isNotBlank()) {
                                        onSearch(textFieldValue.text)
                                        isEditing = false
                                        showSuggestions = false
                                        focusManager.clearFocus()
                                    }
                                }),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (textFieldValue.text.isEmpty()) {
                                            Text(
                                                "搜索歌曲、歌手",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                            if (textFieldValue.text.isNotEmpty()) {
                                IconButton(onClick = {
                                    textFieldValue = TextFieldValue("")
                                    showSuggestions = false
                                }, modifier = Modifier.size(18.dp).padding(0.dp)) {
                                    Icon(Icons.Default.Clear, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            // 锁定状态：显示纯文本
                            Text(
                                text = textFieldValue.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(20.dp))
                }
            },
            actions = {
                // 搜索按钮
                TextButton(onClick = {
                    if (textFieldValue.text.isNotBlank()) {
                        onSearch(textFieldValue.text)
                        isEditing = false
                        showSuggestions = false
                        focusManager.clearFocus()
                    }
                }) {
                    Text("搜索", fontSize = 14.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        // 搜索建议下拉列表
        if (showSuggestions && suggestList.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Column {
                    suggestList.take(8).forEach { suggest ->
                        suggest.HintInfo?.let { hint ->
                            Surface(
                                onClick = {
                                    isSelectingSuggestion = true
                                    textFieldValue = TextFieldValue(hint)
                                    onSearch(hint)
                                    showSuggestions = false
                                    isEditing = false
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(hint, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPage(
    initialQuery: String = "",
    onBack: () -> Unit,
    onPlaySong: (List<SongInfo>, Int) -> Unit,
    onPlaylistClick: (Long, String) -> Unit = { _, _ -> },
    onQueryChange: (String) -> Unit = {},
    currentPlayingPath: String? = null,
    isPlaying: Boolean = false,
    onAddToQueueNext: (SongInfo) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var searchResults by remember { mutableStateOf<List<SongInfo>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalResults by remember { mutableIntStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var noMoreData by remember { mutableStateOf(false) }
    var currentQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("song") }
    var inputText by remember { mutableStateOf(initialQuery) }

    // 热搜和建议
    var hotSearchList by remember { mutableStateOf<List<HotSearchItem>>(emptyList()) }
    var suggestList by remember { mutableStateOf<List<SearchSuggestItem>>(emptyList()) }
    var defaultKeyword by remember { mutableStateOf("") }

    // 搜索历史
    val historyPrefs = remember { context.getSharedPreferences("search_history", Context.MODE_PRIVATE) }
    var historyList by remember { mutableStateOf<List<String>>(emptyList()) }
    var showAllHistory by remember { mutableStateOf(false) }
    var isDeletingHistory by remember { mutableStateOf(false) }
    var selectedForDelete by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 热搜展开/收起（持久化）
    var hotSearchExpanded by remember { mutableStateOf(historyPrefs.getBoolean("hot_expanded", true)) }

    // 加载搜索历史
    fun loadHistory() {
        val historyStr = historyPrefs.getString("history", "") ?: ""
        historyList = if (historyStr.isNotBlank()) historyStr.split("|||").filter { it.isNotBlank() } else emptyList()
    }

    // 保存搜索历史
    fun saveHistory(keyword: String) {
        val current = historyPrefs.getString("history", "") ?: ""
        val list = current.split("|||").filter { it.isNotBlank() }.toMutableList()
        list.remove(keyword)
        list.add(0, keyword)
        val newStr = list.take(30).joinToString("|||")
        historyPrefs.edit().putString("history", newStr).apply()
        loadHistory()
    }

    // 删除历史记录
    fun deleteHistory(keywords: Set<String>) {
        val current = historyPrefs.getString("history", "") ?: ""
        val list = current.split("|||").filter { it.isNotBlank() && it !in keywords }.toMutableList()
        val newStr = list.joinToString("|||")
        historyPrefs.edit().putString("history", newStr).apply()
        loadHistory()
    }

    // 加载热搜和历史
    LaunchedEffect(Unit) {
        loadHistory()
        try {
            val hotResp = KuGouApi.service.getHotSearch()
            hotSearchList = hotResp.data?.list?.flatMap { it.keywords ?: emptyList() } ?: emptyList()
        } catch (_: Exception) {}
        try {
            val defaultResp = KuGouApi.service.getDefaultSearchKeyword()
            defaultKeyword = defaultResp.data?.keyword ?: ""
        } catch (_: Exception) {}
    }

    // 搜索建议（防抖）- 输入时显示
    LaunchedEffect(inputText) {
        if (inputText.isBlank()) { suggestList = emptyList(); return@LaunchedEffect }
        delay(300)
        try {
            val resp = KuGouApi.service.getSearchSuggest(inputText)
            suggestList = resp.data?.flatMap { it.RecordDatas ?: emptyList() } ?: emptyList()
        } catch (_: Exception) { suggestList = emptyList() }
    }

    // 歌单搜索结果
    var playlistResults by remember { mutableStateOf<List<SearchPlaylistItem>>(emptyList()) }

    // 搜索函数
    fun doSearch(keywords: String, type: String = selectedType) {
        if (keywords.isBlank()) return
        isSearching = true
        hasSearched = true
        currentQuery = keywords
        inputText = keywords
        currentPage = 1
        noMoreData = false
        onQueryChange(keywords) // 同步到标题栏搜索框
        scope.launch(Dispatchers.IO) {
            try {
                if (type == "special") {
                    // 歌单搜索
                    val resp = KuGouApi.service.searchPlaylist(keywords, page = 1, pageSize = 30)
                    playlistResults = resp.data?.lists ?: emptyList()
                    searchResults = emptyList()
                    totalResults = resp.data?.total ?: 0
                    android.util.Log.d("LxMusic", "歌单搜索: total=${resp.data?.total}, listSize=${playlistResults.size}")
                    if (playlistResults.size < 30) noMoreData = true
                    if (playlistResults.isNotEmpty()) saveHistory(keywords)
                } else {
                    // 单曲搜索
                    val resp = KuGouApi.service.search(keywords, page = 1, pageSize = 30, type = type)
                    android.util.Log.d("LxMusic", "搜索: type=$type, status=${resp.status}, total=${resp.data?.total}, listSize=${resp.data?.lists?.size}")
                    val songs = resp.data?.lists?.map { song ->
                        SongInfo(
                            title = song.title,
                            artist = song.artist,
                            filePath = "${song.hash}|${song.album_audio_id}",
                            albumArtUri = song.coverUrl,
                            duration = song.Duration.toLong() * 1000,
                            albumId = song.AlbumID?.toLongOrNull() ?: 0
                        )
                    } ?: emptyList()
                    searchResults = songs
                    playlistResults = emptyList()
                    totalResults = resp.data?.total ?: 0
                    if (songs.size < 30) noMoreData = true
                    if (songs.isNotEmpty()) saveHistory(keywords)
                }
            } catch (_: Exception) {
                searchResults = emptyList()
                playlistResults = emptyList()
            }
            isSearching = false
        }
    }

    // 加载更多
    fun loadMore() {
        if (isLoadingMore || noMoreData || currentQuery.isBlank()) return
        isLoadingMore = true
        val nextPage = currentPage + 1
        scope.launch(Dispatchers.IO) {
            try {
                val resp = KuGouApi.service.search(currentQuery, page = nextPage, pageSize = 30, type = selectedType)
                val songs = resp.data?.lists?.map { song ->
                    SongInfo(
                        title = song.title,
                        artist = song.artist,
                        filePath = "${song.hash}|${song.album_audio_id}",
                        albumArtUri = song.coverUrl,
                        duration = song.Duration.toLong(),
                        albumId = song.AlbumID?.toLongOrNull() ?: 0
                    )
                } ?: emptyList()
                if (songs.isNotEmpty()) {
                    searchResults = searchResults + songs
                    currentPage = nextPage
                }
                if (songs.size < 30) noMoreData = true
            } catch (_: Exception) {}
            isLoadingMore = false
        }
    }

    // 接收标题栏传来的搜索词
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) doSearch(initialQuery)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索类型 Tab（搜索后显示）
        if (hasSearched) {
            val types = listOf("song" to "单曲", "special" to "歌单", "author" to "歌手", "album" to "专辑", "mv" to "MV")
            ScrollableTabRow(
                selectedTabIndex = types.indexOfFirst { it.first == selectedType }.coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                edgePadding = 8.dp
            ) {
                types.forEach { (type, label) ->
                    Tab(
                        selected = selectedType == type,
                        onClick = {
                            selectedType = type
                            if (currentQuery.isNotBlank()) doSearch(currentQuery, type)
                        },
                        text = { Text(label, fontSize = 13.sp) }
                    )
                }
            }
        }

        // 内容区域（点击空白区域收起键盘）
        Box(modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
        ) {
            when {
                isSearching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                hasSearched && searchResults.isEmpty() && playlistResults.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("未找到相关结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                hasSearched && playlistResults.isNotEmpty() -> {
                    // 歌单搜索结果
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        item {
                            Text(
                                text = if (totalResults > 0) "共 $totalResults 个歌单，已加载 ${playlistResults.size} 个" else "已加载 ${playlistResults.size} 个歌单",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(playlistResults) { playlist ->
                            Surface(
                                onClick = { onPlaylistClick(playlist.specialid, playlist.specialname ?: "未知歌单") },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val coverUrl = playlist.coverUrl
                                    if (coverUrl.isNotBlank()) {
                                        val painter = rememberAsyncImagePainter(model = ImageRequest.Builder(context).data(coverUrl).memoryCacheKey(coverUrl).crossfade(150).build())
                                        Image(painter, null, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                    } else {
                                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.QueueMusic, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(playlist.specialname ?: "未知歌单", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("${playlist.nickname ?: "未知"} · ${playlist.song_count}首", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                hasSearched && searchResults.isNotEmpty() -> {
                    val listState = rememberLazyListState()
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= listState.layoutInfo.totalItemsCount - 3 && !isLoadingMore && !noMoreData
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) loadMore()
                    }
                    // 滚动时收起键盘
                    LaunchedEffect(listState.isScrollInProgress) {
                        if (listState.isScrollInProgress) focusManager.clearFocus()
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        item {
                            Text(
                                text = if (totalResults > 0) "共 $totalResults 首，已加载 ${searchResults.size} 首" else "已加载 ${searchResults.size} 首",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                        itemsIndexed(searchResults, key = { _, song -> song.filePath }) { index, song ->
                            var showSheet by remember { mutableStateOf(false) }
                            RankSongCard(
                                rank = index + 1,
                                song = song,
                                showRank = false,
                                isCurrentSong = song.filePath == currentPlayingPath,
                                isPlaying = isPlaying && song.filePath == currentPlayingPath,
                                onClick = { onPlaySong(searchResults, index) },
                                onMenuClick = { showSheet = true }
                            )
                            if (showSheet) {
                                ModalBottomSheet(
                                    onDismissRequest = { showSheet = false },
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val coverUrl = song.albumArtUri
                                        if (!coverUrl.isNullOrBlank()) {
                                            val painter = rememberAsyncImagePainter(model = ImageRequest.Builder(context).data(coverUrl).memoryCacheKey(coverUrl).crossfade(150).build())
                                            Image(painter, null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                        } else {
                                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.MusicNote, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
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
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        // 搜索历史
                        if (historyList.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("搜索历史", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                        if (isDeletingHistory) {
                                            Text(
                                                "完成",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable { isDeletingHistory = false }
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Delete, "删除",
                                                Modifier.size(16.dp).clickable { isDeletingHistory = true },
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                val displayHistory = if (showAllHistory) historyList.take(30) else historyList.take(15)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    displayHistory.forEach { keyword ->
                                        Surface(
                                            onClick = {
                                                if (isDeletingHistory) {
                                                    deleteHistory(setOf(keyword))
                                                    if (historyList.isEmpty()) isDeletingHistory = false
                                                } else {
                                                    doSearch(keyword)
                                                }
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(keyword, fontSize = 13.sp)
                                                if (isDeletingHistory) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(Icons.Default.Clear, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                                if (historyList.size > 15 && !showAllHistory) {
                                    TextButton(onClick = { showAllHistory = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
                                        Text("展开更多", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // 热搜列表（可收起）
                        if (hotSearchList.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("热搜榜", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    IconButton(onClick = {
                                        hotSearchExpanded = !hotSearchExpanded
                                        historyPrefs.edit().putBoolean("hot_expanded", hotSearchExpanded).apply()
                                    }, modifier = Modifier.size(24.dp)) {
                                        Icon(
                                            if (hotSearchExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            null, Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            if (hotSearchExpanded) {
                                itemsIndexed(hotSearchList.take(20), key = { i, hot -> hot.keyword ?: "hot_$i" }) { index, hot ->
                                    hot.keyword?.let { keyword ->
                                        Surface(
                                            onClick = { doSearch(keyword) },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = Color.Transparent
                                        ) {
                                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text("${index + 1}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                                                    color = if (index < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.width(28.dp))
                                                Text(keyword, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 默认提示
                        if (hotSearchList.isEmpty() && historyList.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                                    Text(if (defaultKeyword.isNotBlank()) "试试搜索: $defaultKeyword" else "输入关键词搜索",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
