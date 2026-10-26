@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.example.lxmusic.ui.pages

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.example.lxmusic.BffApi
import com.example.lxmusic.MusicDatabase
import com.example.lxmusic.toSongInfo
import com.example.lxmusic.model.toSongInfo
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.NeteaseApi
import com.example.lxmusic.SearchAlbumItem
import com.example.lxmusic.SearchAuthorItem
import com.example.lxmusic.SearchMvItem
import com.example.lxmusic.SearchPlaylistItem
import com.example.lxmusic.SearchSuggestItem
import com.example.lxmusic.ui.components.SongContextMenuActions
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.toSongInfo
import com.example.lxmusic.ui.components.IOLoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 搜索音源选项：key 为内部标识（kugou/netease/tx/kw/local/plugin:<id>），label 为展示名。
 * 未激活模式由 MainActivity 传入「本地 + 公开平台 + 已启用插件」。
 */
data class SearchSourceOption(val key: String, val label: String)

/** 激活模式默认音源（与改造前完全一致） */
private val DefaultSearchSources = listOf(
    SearchSourceOption("kugou", "酷狗"),
    SearchSourceOption("netease", "网易云"),
    SearchSourceOption("tx", "QQ音乐"),
    SearchSourceOption("kw", "酷我音乐")
)

/**
 * 音源切换按钮：pill 样式，点击弹出选择菜单
 * 搜索栏旁与搜索结果 Tab 行共用
 */
@Composable
fun MusicSourceSwitcher(
    searchSource: String,
    onSourceChange: (String) -> Unit,
    sources: List<SearchSourceOption> = DefaultSearchSources,
    modifier: Modifier = Modifier
) {
    val currentLabel = sources.firstOrNull { it.key == searchSource }?.label
        ?: sources.firstOrNull()?.label.orEmpty()
    val highlighted = searchSource != sources.firstOrNull()?.key
    var showMenu by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { showMenu = true },
            shape = RoundedCornerShape(14.dp),
            color = if (highlighted) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (highlighted) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "切换音源",
                    modifier = Modifier.size(16.dp),
                    tint = if (highlighted) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            sources.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontSize = 13.sp) },
                    trailingIcon = {
                        if (searchSource == key) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    onClick = { showMenu = false; onSourceChange(key) }
                )
            }
        }
    }
}

@Composable
fun SearchTopBar(
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    externalQuery: String = "",
    searchSource: String = "kugou",
    onSourceChange: (String) -> Unit = {},
    // 音源列表；null = 激活默认四平台
    sources: List<SearchSourceOption>? = null,
    // 未激活时搜索是顶层 tab：不显示返回箭头、不自动聚焦弹键盘、不请求在线搜索建议
    showBack: Boolean = true,
    autoFocus: Boolean = true,
    suggestionsEnabled: Boolean = true
) {
    val resolvedSources = sources ?: DefaultSearchSources
    var textFieldValue by remember { mutableStateOf(TextFieldValue(externalQuery, selection = TextRange(externalQuery.length))) }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 搜索建议
    var suggestList by remember { mutableStateOf<List<SearchSuggestItem>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var isUserInput by remember { mutableStateOf(false) }

    // 提交搜索：安全收起键盘、清空并隐藏推荐词
    fun submitSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            isUserInput = false
            showSuggestions = false
            suggestList = emptyList()
            textFieldValue = TextFieldValue(trimmed, selection = TextRange(trimmed.length))
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            onSearch(trimmed)
        }
    }

    // 外部传入搜索词变化时同步
    LaunchedEffect(externalQuery) {
        if (externalQuery != textFieldValue.text) {
            isUserInput = false
            textFieldValue = TextFieldValue(externalQuery, selection = TextRange(externalQuery.length))
            showSuggestions = false
            suggestList = emptyList()
            if (externalQuery.isNotBlank()) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        }
    }

    // 首次进入且无搜索词时自动弹起软键盘聚焦（未激活顶层搜索不打扰用户）
    LaunchedEffect(Unit) {
        if (autoFocus && externalQuery.isBlank()) {
            delay(150)
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // 搜索建议防抖请求：仅在获得焦点且用户正在主动打字时触发
    LaunchedEffect(textFieldValue.text, isFocused) {
        if (!suggestionsEnabled || !isFocused || !isUserInput) {
            showSuggestions = false
            suggestList = emptyList()
            return@LaunchedEffect
        }
        val query = textFieldValue.text.trim()
        if (query.isBlank()) {
            suggestList = emptyList()
            showSuggestions = false
            return@LaunchedEffect
        }
        delay(250)
        if (!isFocused || !isUserInput) return@LaunchedEffect
        try {
            val resp = KuGouApi.service.getSearchSuggest(query)
            if (isFocused && isUserInput) {
                val list = resp.data?.flatMap { it.RecordDatas ?: emptyList() } ?: emptyList()
                suggestList = list
                showSuggestions = isFocused && list.isNotEmpty()
            }
        } catch (_: Exception) {
            suggestList = emptyList()
            showSuggestions = false
        }
    }

    Column {
        TopAppBar(
            title = {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
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
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = {
                                isUserInput = true
                                textFieldValue = it
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    isFocused = focusState.isFocused
                                    if (!focusState.isFocused) {
                                        showSuggestions = false
                                        suggestList = emptyList()
                                    }
                                },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                submitSearch(textFieldValue.text)
                            }),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (textFieldValue.text.isEmpty()) {
                                        Text(
                                            "搜索歌曲、歌手、歌单",
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
                            IconButton(
                                onClick = {
                                    isUserInput = false
                                    textFieldValue = TextFieldValue("")
                                    showSuggestions = false
                                    suggestList = emptyList()
                                    try { focusRequester.requestFocus() } catch (_: Exception) {}
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "清空",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            navigationIcon = {
                if (showBack) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(20.dp))
                    }
                }
            },
            actions = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MusicSourceSwitcher(
                        searchSource = searchSource,
                        onSourceChange = onSourceChange,
                        sources = resolvedSources
                    )
                    TextButton(onClick = {
                        submitSearch(textFieldValue.text)
                    }) {
                        Text("搜索", fontSize = 14.sp)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        // 搜索建议下拉浮层
        AnimatedVisibility(
            visible = showSuggestions && suggestList.isNotEmpty() && isFocused,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(150))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    suggestList.take(8).forEach { suggest ->
                        val hint = suggest.HintInfo
                        if (!hint.isNullOrBlank()) {
                            Surface(
                                onClick = {
                                    submitSearch(hint)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = hint,
                                        style = MaterialTheme.typography.bodyMedium,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPage(
    initialQuery: String = "",
    initialSelectedType: String = "song",
    onBack: () -> Unit,
    onPlaySong: (List<SongInfo>, Int) -> Unit,
    onPlaylistClick: (SearchPlaylistItem) -> Unit = {},
    onPublicPlaylistClick: (com.example.lxmusic.data.search.PublicPlaylistProviders.PublicPlaylist) -> Unit = {},
    onAlbumClick: (SearchAlbumItem) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onSelectedTypeChange: (String) -> Unit = {},
    currentPlayingPath: String? = null,
    isPlaying: Boolean = false,
    onAddToQueueNext: (SongInfo) -> Unit = {},
    initialSource: String = "kugou",
    onSourceChange: (String) -> Unit = {},
    // 未激活模式由 MainActivity 传入「本地 + 公开平台 + 插件」音源
    sourceOptions: List<SearchSourceOption>? = null,
    // 未激活：只保留单曲、不请求热搜/默认词/搜索建议
    guestMode: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sources = sourceOptions ?: DefaultSearchSources
    var searchResults by remember { mutableStateOf<List<SongInfo>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(initialQuery.isNotBlank()) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalResults by remember { mutableIntStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var noMoreData by remember { mutableStateOf(false) }
    var currentQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(initialSelectedType) }
    var inputText by remember { mutableStateOf(initialQuery) }

    // 音源：kugou / netease / local / plugin:<id>（持久化到 settings 的 music_source，启动时由 MainActivity 传入）
    var searchSource by remember { mutableStateOf(initialSource) }
    LaunchedEffect(initialSource) {
        searchSource = initialSource
    }
    // 音源合法性：切模式后旧保存值可能不在当前列表（如激活后用过的 plugin/local），回落到默认源
    LaunchedEffect(sources) {
        if (sources.none { it.key == searchSource }) {
            val fallback = if (guestMode) "local" else "kugou"
            if (sources.any { it.key == fallback }) {
                searchSource = fallback
                onSourceChange(fallback)
            }
        }
    }

    // 热搜
    var hotSearchList by remember { mutableStateOf<List<HotSearchItem>>(emptyList()) }
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

    // 加载热搜和历史（未激活不请求在线热搜/默认词）
    LaunchedEffect(Unit) {
        loadHistory()
        if (guestMode) return@LaunchedEffect
        try {
            val hotResp = KuGouApi.service.getHotSearch()
            hotSearchList = hotResp.data?.list?.flatMap { it.keywords ?: emptyList() } ?: emptyList()
        } catch (_: Exception) {}
        try {
            val defaultResp = KuGouApi.service.getDefaultSearchKeyword()
            defaultKeyword = defaultResp.data?.keyword ?: ""
        } catch (_: Exception) {}
    }

    // 歌单搜索结果（统一用公共歌单模型，点进去复用公共歌单详情）
    var playlistResults by remember {
        mutableStateOf<List<com.example.lxmusic.data.search.PublicPlaylistProviders.PublicPlaylist>>(emptyList())
    }

    // 歌手/专辑/MV 搜索结果
    var authorResults by remember { mutableStateOf<List<SearchAuthorItem>>(emptyList()) }
    var albumResults by remember { mutableStateOf<List<SearchAlbumItem>>(emptyList()) }
    var mvResults by remember { mutableStateOf<List<SearchMvItem>>(emptyList()) }

    // 搜索函数
    fun doSearch(keywords: String, type: String = selectedType) {
        if (keywords.isBlank()) return
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        isSearching = true
        hasSearched = true
        currentQuery = keywords
        inputText = keywords
        currentPage = 1
        noMoreData = false
        onQueryChange(keywords)
        scope.launch(Dispatchers.IO) {
            try {
                // 清空所有结果
                searchResults = emptyList()
                playlistResults = emptyList()
                authorResults = emptyList()
                albumResults = emptyList()
                mvResults = emptyList()

                when (type) {
                    "special" -> {
                        // 多平台歌单搜索：酷狗 / 网易云 / QQ
                        val result = com.example.lxmusic.data.search.PublicSearchPlaylistProviders.search(
                            searchSource, keywords, page = 1, pageSize = 30
                        )
                        playlistResults = result.playlists
                        totalResults = result.total
                        android.util.Log.d("LxMusic", "歌单搜索($searchSource): total=${result.total}, size=${result.playlists.size}")
                        if (result.playlists.size < 30) noMoreData = true
                        if (result.playlists.isNotEmpty()) saveHistory(keywords)
                    }
                    "author" -> {
                        val resp = KuGouApi.service.searchAuthor(keywords, page = 1, pageSize = 30)
                        authorResults = resp.data?.lists ?: emptyList()
                        totalResults = resp.data?.total ?: 0
                        android.util.Log.d("LxMusic", "歌手搜索: total=${resp.data?.total}, listSize=${authorResults.size}")
                        if (authorResults.size < 30) noMoreData = true
                        if (authorResults.isNotEmpty()) saveHistory(keywords)
                    }
                    "album" -> {
                        val resp = KuGouApi.service.searchAlbum(keywords, page = 1, pageSize = 30)
                        albumResults = resp.data?.lists ?: emptyList()
                        totalResults = resp.data?.total ?: 0
                        android.util.Log.d("LxMusic", "专辑搜索: total=${resp.data?.total}, listSize=${albumResults.size}")
                        if (albumResults.size < 30) noMoreData = true
                        if (albumResults.isNotEmpty()) saveHistory(keywords)
                    }
                    "mv" -> {
                        val resp = KuGouApi.service.searchMv(keywords, page = 1, pageSize = 30)
                        mvResults = resp.data?.lists ?: emptyList()
                        totalResults = resp.data?.total ?: 0
                        android.util.Log.d("LxMusic", "MV搜索: total=${resp.data?.total}, listSize=${mvResults.size}")
                        if (mvResults.size < 30) noMoreData = true
                        if (mvResults.isNotEmpty()) saveHistory(keywords)
                    }
                    else -> {
                        // 多平台搜索：酷狗/网易云（已激活优先内置，否则公开接口）+ QQ/酷我（公开接口）+ 本地/插件
                        val (songs, total) = searchSongsBySource(
                            context, searchSource, keywords, page = 1, pageSize = 30
                        )
                        searchResults = songs
                        totalResults = total
                        if (songs.size < 30) noMoreData = true
                        if (songs.isNotEmpty()) saveHistory(keywords)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LxMusic", "搜索失败: type=$type", e)
                searchResults = emptyList()
                playlistResults = emptyList()
                authorResults = emptyList()
                albumResults = emptyList()
                mvResults = emptyList()
            } finally {
                isSearching = false
                withContext(Dispatchers.Main) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
            }
        }
    }

    // 加载更多
    fun loadMore() {
        if (isLoadingMore || noMoreData || currentQuery.isBlank()) return
        isLoadingMore = true
        val nextPage = currentPage + 1
        scope.launch(Dispatchers.IO) {
            try {
                when (selectedType) {
                    "special" -> {
                        val result = com.example.lxmusic.data.search.PublicSearchPlaylistProviders.search(
                            searchSource, currentQuery, page = nextPage, pageSize = 30
                        )
                        val more = result.playlists
                        if (more.isNotEmpty()) {
                            playlistResults = (playlistResults + more).distinctBy { it.platform + it.id }
                            currentPage = nextPage
                        }
                        if (more.size < 30) noMoreData = true
                    }
                    "author" -> {
                        val resp = KuGouApi.service.searchAuthor(currentQuery, page = nextPage, pageSize = 30)
                        val more = resp.data?.lists ?: emptyList()
                        if (more.isNotEmpty()) { authorResults = authorResults + more; currentPage = nextPage }
                        if (more.size < 30) noMoreData = true
                    }
                    "album" -> {
                        val resp = KuGouApi.service.searchAlbum(currentQuery, page = nextPage, pageSize = 30)
                        val more = resp.data?.lists ?: emptyList()
                        if (more.isNotEmpty()) { albumResults = albumResults + more; currentPage = nextPage }
                        if (more.size < 30) noMoreData = true
                    }
                    "mv" -> {
                        val resp = KuGouApi.service.searchMv(currentQuery, page = nextPage, pageSize = 30)
                        val more = resp.data?.lists ?: emptyList()
                        if (more.isNotEmpty()) { mvResults = mvResults + more; currentPage = nextPage }
                        if (more.size < 30) noMoreData = true
                    }
                    else -> {
                        val (songs, _) = searchSongsBySource(
                            context, searchSource, currentQuery, page = nextPage, pageSize = 30
                        )
                        if (songs.isNotEmpty()) {
                            searchResults = searchResults + songs
                            currentPage = nextPage
                        }
                        if (songs.size < 30) noMoreData = true
                    }
                }
            } catch (_: Exception) {}
            isLoadingMore = false
        }
    }

    LaunchedEffect(initialSelectedType) {
        selectedType = initialSelectedType
    }

    // 歌手/MV tab 已下线、非酷狗不支持专辑：历史选中类型回落到单曲
    LaunchedEffect(selectedType, searchSource) {
        val unsupported = selectedType == "author" || selectedType == "mv" ||
            (selectedType == "album" && searchSource != "kugou") ||
            (guestMode && selectedType != "song")
        if (unsupported) {
            selectedType = "song"
            onSelectedTypeChange("song")
        }
    }

    // 接收标题栏传来的搜索词
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            doSearch(initialQuery, selectedType)
        } else {
            hasSearched = false
            currentQuery = ""
            selectedType = "song"
            onSelectedTypeChange("song")
            searchResults = emptyList()
            playlistResults = emptyList()
            authorResults = emptyList()
            albumResults = emptyList()
            mvResults = emptyList()
        }
    }

    // 点击歌手/专辑/MV 时：用名称搜歌并直接播放
    fun playByName(name: String) {
        if (name.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val resp = KuGouApi.service.search(name, page = 1, pageSize = 30, type = "song")
                val songs = resp.data?.lists?.map { song ->
                    SongInfo(
                        title = song.title, artist = song.artist,
                        filePath = "${song.hash}|${song.album_audio_id}",
                        albumArtUri = song.coverUrl,
                        duration = song.Duration.toLong() * 1000,
                        albumId = song.AlbumID?.toLongOrNull() ?: 0
                    )
                } ?: emptyList()
                if (songs.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onPlaySong(songs, 0)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LxMusic", "播放失败: name=$name", e)
            }
        }
    }

    // 切换音源：保存状态，非酷狗模式只支持单曲，已有搜索词则立即用新音源重搜
    fun switchSource(source: String) {
        if (source == searchSource) return
        searchSource = source
        onSourceChange(source)
        if (source != "kugou" && selectedType != "song") {
            selectedType = "song"
            onSelectedTypeChange("song")
        }
        if (currentQuery.isNotBlank()) {
            doSearch(currentQuery, "song")
        } else {
            searchResults = emptyList()
            playlistResults = emptyList()
            authorResults = emptyList()
            albumResults = emptyList()
            mvResults = emptyList()
            noMoreData = false
            hasSearched = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索类型 Tab（搜索后显示，平滑展开/收起）
        AnimatedVisibility(
            visible = hasSearched,
            enter = expandVertically(tween(250, easing = FastOutSlowInEasing)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(180))
        ) {
            // 歌手/MV 暂时下线；歌单搜索支持 酷狗/网易云/QQ，专辑目前只做酷狗详情；
            // 未激活（本地/插件）只有单曲
            val types = when {
                guestMode || searchSource == "local" || searchSource.startsWith("plugin:") ->
                    listOf("song" to "单曲")
                searchSource == "kugou" -> listOf(
                    "song" to "单曲", "special" to "歌单", "album" to "专辑"
                )
                searchSource == "netease" || searchSource == "tx" -> listOf(
                    "song" to "单曲", "special" to "歌单"
                )
                else -> listOf("song" to "单曲")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScrollableTabRow(
                    selectedTabIndex = types.indexOfFirst { it.first == selectedType }.coerceAtLeast(0),
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    edgePadding = 8.dp
                ) {
                    types.forEach { (type, label) ->
                        Tab(
                            selected = selectedType == type,
                            onClick = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                selectedType = type
                                onSelectedTypeChange(type)
                                if (currentQuery.isNotBlank()) doSearch(currentQuery, type)
                            },
                            text = { Text(label, fontSize = 13.sp) }
                        )
                    }
                }
                Box(modifier = Modifier.padding(end = 12.dp)) {
                    MusicSourceSwitcher(
                        searchSource = searchSource,
                        onSourceChange = { switchSource(it) },
                        sources = sources
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
            ) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
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
                hasSearched && searchResults.isEmpty() && playlistResults.isEmpty() && authorResults.isEmpty() && albumResults.isEmpty() && mvResults.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("未找到相关结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                hasSearched && playlistResults.isNotEmpty() -> {
                    // 歌单搜索结果
                    val playlistListState = rememberLazyListState()
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val last = playlistListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= playlistListState.layoutInfo.totalItemsCount - 3 && !isLoadingMore && !noMoreData
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) loadMore()
                    }
                    // 滚动时收起键盘
                    LaunchedEffect(playlistListState.isScrollInProgress) {
                        if (playlistListState.isScrollInProgress) {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        }
                    }

                    LazyColumn(
                        state = playlistListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        item {
                            Text(
                                text = if (totalResults > 0) "共 $totalResults 个歌单" else "歌单",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp)
                            )
                        }
                        items(playlistResults, key = { "${it.platform}_${it.id}" }) { playlist ->
                            // 透明紧凑行：不铺卡片底色，壁纸下也不糊
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPublicPlaylistClick(playlist) }
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val coverUrl = playlist.coverUrl
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (coverUrl.isNotBlank()) {
                                        val painter = rememberAsyncImagePainter(
                                            model = ImageRequest.Builder(context)
                                                .data(coverUrl)
                                                .memoryCacheKey(coverUrl)
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
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val metaText = buildString {
                                        if (playlist.creator.isNotBlank()) append(playlist.creator)
                                        if (playlist.trackCount > 0) {
                                            if (isNotEmpty()) append(" · ")
                                            append("${playlist.trackCount}首")
                                        }
                                        val pc = playlist.playCount
                                        if (pc > 0) {
                                            if (isNotEmpty()) append(" · ")
                                            append(
                                                when {
                                                    pc >= 100_000_000 -> String.format("%.1f亿", pc / 100_000_000.0)
                                                    pc >= 10_000 -> String.format("%.1f万", pc / 10_000.0)
                                                    else -> "$pc"
                                                }
                                            )
                                        }
                                    }
                                    if (metaText.isNotBlank()) {
                                        Text(
                                            text = metaText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
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
                hasSearched && authorResults.isNotEmpty() -> {
                    // 歌手搜索结果
                    val authorListState = rememberLazyListState()
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val last = authorListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= authorListState.layoutInfo.totalItemsCount - 3 && !isLoadingMore && !noMoreData
                        }
                    }
                    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) loadMore() }
                    LaunchedEffect(authorListState.isScrollInProgress) {
                        if (authorListState.isScrollInProgress) {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        }
                    }
                    LazyColumn(state = authorListState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                        item {
                            Text(
                                text = if (totalResults > 0) "共 $totalResults 位歌手，已加载 ${authorResults.size} 位" else "已加载 ${authorResults.size} 位歌手",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(authorResults) { author ->
                            Surface(
                                onClick = { playByName(author.authorname ?: "") },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val coverUrl = author.coverUrl
                                    if (coverUrl.isNotBlank()) {
                                        val painter = rememberAsyncImagePainter(model = ImageRequest.Builder(context).data(coverUrl).memoryCacheKey(coverUrl).crossfade(150).build())
                                        Image(painter, null, Modifier.size(56.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                    } else {
                                        Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.MusicNote, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(author.authorname ?: "未知歌手", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val meta = buildString {
                                            if (author.songcount > 0) append("${author.songcount}首歌曲")
                                            if (author.albumcount > 0) {
                                                if (isNotEmpty()) append(" · ")
                                                append("${author.albumcount}张专辑")
                                            }
                                        }
                                        if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (isLoadingMore) {
                            item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { IOLoadingIndicator(Modifier.size(24.dp)) } }
                        }
                    }
                }
                hasSearched && albumResults.isNotEmpty() -> {
                    // 专辑搜索结果
                    val albumListState = rememberLazyListState()
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val last = albumListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= albumListState.layoutInfo.totalItemsCount - 3 && !isLoadingMore && !noMoreData
                        }
                    }
                    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) loadMore() }
                    LaunchedEffect(albumListState.isScrollInProgress) {
                        if (albumListState.isScrollInProgress) {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        }
                    }
                    LazyColumn(state = albumListState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                        item {
                            Text(
                                text = if (totalResults > 0) "共 $totalResults 张专辑" else "专辑",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp)
                            )
                        }
                        items(albumResults, key = { it.albumid }) { album ->
                            // 透明紧凑行，点进专辑详情
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAlbumClick(album) }
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val coverUrl = album.coverUrl
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Album,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (coverUrl.isNotBlank()) {
                                        val painter = rememberAsyncImagePainter(
                                            model = ImageRequest.Builder(context)
                                                .data(coverUrl)
                                                .memoryCacheKey(coverUrl)
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
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = album.albumname ?: "未知专辑",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val meta = buildString {
                                        if (!album.authorname.isNullOrBlank()) append(album.authorname)
                                        if (album.songcount > 0) {
                                            if (isNotEmpty()) append(" · ")
                                            append("${album.songcount}首")
                                        }
                                    }
                                    if (meta.isNotBlank()) {
                                        Text(
                                            meta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        if (isLoadingMore) {
                            item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { IOLoadingIndicator(Modifier.size(24.dp)) } }
                        }
                    }
                }
                hasSearched && mvResults.isNotEmpty() -> {
                    // MV 搜索结果
                    val mvListState = rememberLazyListState()
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val last = mvListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= mvListState.layoutInfo.totalItemsCount - 3 && !isLoadingMore && !noMoreData
                        }
                    }
                    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) loadMore() }
                    LaunchedEffect(mvListState.isScrollInProgress) {
                        if (mvListState.isScrollInProgress) {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        }
                    }
                    LazyColumn(state = mvListState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                        item {
                            Text(
                                text = if (totalResults > 0) "共 $totalResults 个MV，已加载 ${mvResults.size} 个" else "已加载 ${mvResults.size} 个MV",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(mvResults) { mv ->
                            Surface(
                                onClick = { playByName(mv.mvname ?: "") },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val coverUrl = mv.coverUrl
                                    if (coverUrl.isNotBlank()) {
                                        val painter = rememberAsyncImagePainter(model = ImageRequest.Builder(context).data(coverUrl).memoryCacheKey(coverUrl).crossfade(150).build())
                                        Image(painter, null, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                    } else {
                                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.MusicNote, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mv.mvname ?: "未知MV", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val meta = buildString {
                                            if (!mv.authorname.isNullOrBlank()) append(mv.authorname)
                                            if (mv.durationText.isNotBlank()) {
                                                if (isNotEmpty()) append(" · ")
                                                append(mv.durationText)
                                            }
                                            mv.playcount?.let { raw ->
                                                val num = raw.replace("[^0-9]".toRegex(), "").toLongOrNull() ?: 0L
                                                val formatted = when {
                                                    num >= 100_000_000 -> String.format("%.1f亿", num / 100_000_000.0)
                                                    num >= 10_000 -> String.format("%.1f万", num / 10_000.0)
                                                    num > 0 -> "${num}次"
                                                    else -> null
                                                }
                                                if (!formatted.isNullOrBlank()) {
                                                    if (isNotEmpty()) append(" · ")
                                                    append(formatted)
                                                }
                                            }
                                        }
                                        if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (isLoadingMore) {
                            item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { IOLoadingIndicator(Modifier.size(24.dp)) } }
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
                        if (listState.isScrollInProgress) {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        }
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
                    val defaultListState = rememberLazyListState()
                    LaunchedEffect(defaultListState.isScrollInProgress) {
                        if (defaultListState.isScrollInProgress) {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        }
                    }
                    LazyColumn(
                        state = defaultListState,
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
                                                    focusManager.clearFocus(force = true)
                                                    keyboardController?.hide()
                                                    selectedType = "song"
                                                    onSelectedTypeChange("song")
                                                    doSearch(keyword, "song")
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
                                        val rotation by animateFloatAsState(
                                            targetValue = if (hotSearchExpanded) 180f else 0f,
                                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                                            label = "hotSearchRotation"
                                        )
                                        Icon(
                                            Icons.Default.ExpandMore,
                                            null,
                                            Modifier.size(20.dp).graphicsLayer(rotationZ = rotation)
                                        )
                                    }
                                }
                            }
                            if (hotSearchExpanded) {
                                itemsIndexed(hotSearchList.take(20), key = { i, hot -> hot.keyword ?: "hot_$i" }) { index, hot ->
                                    hot.keyword?.let { keyword ->
                                        Surface(
                                            onClick = {
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()
                                                selectedType = "song"
                                                onSelectedTypeChange("song")
                                                doSearch(keyword, "song")
                                            },
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
                                    Text(
                                        text = when {
                                            defaultKeyword.isNotBlank() -> "试试搜索: $defaultKeyword"
                                            guestMode && searchSource == "local" -> "搜索本地歌曲（歌名或歌手）"
                                            else -> "输入关键词搜索"
                                        },
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
}

/**
 * 按音源搜索单曲（新架构）：
 * - 酷狗/网易云：已激活优先走内置（BFF/原有接口），否则走公开搜索接口（无需服务器）
 * - QQ/酷我：公开搜索接口（无需服务器）
 * 返回 (歌曲列表, 总数)
 */
private suspend fun searchSongsBySource(
    context: Context,
    source: String,
    query: String,
    page: Int,
    pageSize: Int
): Pair<List<SongInfo>, Int> = withContext(Dispatchers.IO) {
    when {
        // 未激活：本地曲库内存过滤分页
        source == "local" -> searchLocalSongs(context, query, page, pageSize)
        source == "tx" || source == "kw" -> {
            val list = com.example.lxmusic.data.search.PublicSearchProviders.search(source, query, page, pageSize)
            list.map { it.toSongInfo() } to list.size
        }
        source == "netease" -> {
            if (BffApi.isEnabled && BffApi.onlineAllowed) {
                val inner = runCatching {
                    val resp = NeteaseApi.service.search(
                        query, limit = pageSize, offset = (page - 1) * pageSize, type = 1
                    )
                    val songs = resp.result?.songs?.map { it.toSongInfo() } ?: emptyList()
                    songs to (resp.result?.songCount ?: songs.size)
                }.getOrNull()
                if (inner != null && inner.first.isNotEmpty()) return@withContext inner
            }
            val list = com.example.lxmusic.data.search.PublicSearchProviders.search("wy", query, page, pageSize)
            com.example.lxmusic.data.plugin.PluginContext.get()?.let { ctx ->
                list.forEach { song ->
                    com.example.lxmusic.data.plugin.PluginSongStore.put(
                        ctx, "wymeta|${song.id}", song.musicInfoJson()
                    )
                }
            }
            list.map { it.toSongInfo() } to list.size
        }
        source.startsWith("plugin:") -> {
            val ctx = com.example.lxmusic.data.plugin.PluginContext.get()
                ?: return@withContext emptyList<SongInfo>() to 0
            val pluginId = source.removePrefix("plugin:")
            val list = com.example.lxmusic.data.plugin.PluginSourceProvider.search(
                ctx, pluginId, query, page, pageSize
            )
            list to list.size
        }
        else -> {
            if (BffApi.isEnabled && BffApi.onlineAllowed) {
                val bff = BffApi.search(query, page, pageSize)
                val songs = bff?.items?.map { it.toSongInfo() } ?: emptyList()
                if (songs.isNotEmpty()) return@withContext songs to (bff?.total ?: songs.size)
            }
            val pub = com.example.lxmusic.data.search.PublicSearchProviders.search("kg", query, page, pageSize)
            if (pub.isNotEmpty()) {
                // 保存完整 musicInfo（含真实 albumId），供播放时插件解析使用
                com.example.lxmusic.data.plugin.PluginContext.get()?.let { ctx ->
                    pub.forEach { song ->
                        com.example.lxmusic.data.plugin.PluginSongStore.put(
                            ctx, "kgmeta|${song.id}|${song.albumAudioId}", song.musicInfoJson()
                        )
                    }
                }
                return@withContext pub.map { it.toSongInfo() } to pub.size
            }
            val legacy = runCatching {
                val resp = KuGouApi.service.search(query, page = page, pageSize = pageSize, type = "song")
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
                songs to (resp.data?.total ?: songs.size)
            }.getOrNull()
            legacy ?: (emptyList<SongInfo>() to 0)
        }
    }
}

/**
 * 未激活模式的本地搜索：读本地曲库内存过滤（歌名/歌手），按页切片。
 * 曲库规模通常几千首，一次性过滤足够快；走 IO 线程避免卡顿。
 */
private suspend fun searchLocalSongs(
    context: Context,
    query: String,
    page: Int,
    pageSize: Int
): Pair<List<SongInfo>, Int> = withContext(Dispatchers.IO) {
    val all = MusicDatabase.getDatabase(context).songDao().getAllSongs()
    val keyword = query.trim()
    val matched = if (keyword.isBlank()) all else all.filter { song ->
        song.title.contains(keyword, ignoreCase = true) ||
            song.artist.contains(keyword, ignoreCase = true)
    }
    val start = ((page - 1) * pageSize).coerceAtLeast(0)
    val slice = matched.drop(start).take(pageSize).map { it.toSongInfo() }
    slice to matched.size
}
