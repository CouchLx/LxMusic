package com.example.lxmusic.ui.pages

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.lxmusic.ui.components.IOLoadingIndicator
import com.example.lxmusic.R
import androidx.compose.ui.res.stringResource
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.MusicDatabase
import com.example.lxmusic.ui.components.PlaylistCard
import com.example.lxmusic.UserPlaylistEntity
import com.example.lxmusic.UserPlaylistItem
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.ui.components.PlaylistCard
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinePage(
    loginVersion: Int = 0,
    onLocalClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onPlaylistDetailClick: (UserPlaylistItem) -> Unit = {},
    onCollectionDetailClick: (String, Long) -> Unit = { _, _ -> },
    onManagePlaylists: () -> Unit = {},
    onManageKugouPlaylists: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    initialScrollOffset: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authPrefs = remember { context.getSharedPreferences("auth", Context.MODE_PRIVATE) }
    val isLoggedIn = remember(loginVersion) { authPrefs.getString("token", null) != null }
    val nickname = remember(loginVersion) { authPrefs.getString("nickname", "用户名") ?: "用户名" }
    val avatarUrl = remember(loginVersion) { authPrefs.getString("pic", null)?.replace("http://", "https://") }
    val db = remember { MusicDatabase.getDatabase(context) }
    val collectionDao = remember { db.collectionDao() }
    android.util.Log.d("LxMusic", "MinePage: isLoggedIn=$isLoggedIn, avatarUrl=$avatarUrl")

    val gson = remember { Gson() }
    val minePrefs = remember { context.getSharedPreferences("mine_state", Context.MODE_PRIVATE) }
    // 官方收藏模式：歌单区把“喜欢镜像”歌单（本地先写）持久置顶显示
    val mineSettingsPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val kugouFavOn = mineSettingsPrefs.getBoolean("favorite_to_kugou", false)
    // 歌单首屏直接从缓存同步初始化（带账号标识 + 本地顺序），保证首帧就有完整高度，
    // 避免 LazyListState 在“空内容”时恢复偏移被夹回顶部
    fun loadCachedPlaylists(): List<UserPlaylistItem> {
        val uid = authPrefs.getLong("userid", 0)
        val cached = minePrefs.getString("playlists_json", null)
        if (!cached.isNullOrBlank() && minePrefs.getLong("playlists_json_uid", 0L) == uid) {
            try {
                val type = TypeToken.getParameterized(List::class.java, UserPlaylistItem::class.java).type
                val list: List<UserPlaylistItem> = gson.fromJson(cached, type)
                return KuGouApi.applyLocalPlaylistOrder(list, minePrefs.getString("playlists_order_$uid", null))
            } catch (_: Exception) {}
        }
        return emptyList()
    }
    var playlists by remember { mutableStateOf(loadCachedPlaylists()) }
    // 本地「喜欢镜像」歌单的 listid（用于卡片显示"已收藏"标签）
    var likedMirrorIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var playlistLoading by remember { mutableStateOf(false) }
    var playlistError by remember { mutableStateOf<String?>(null) }
    var playlistExpanded by remember { mutableStateOf(minePrefs.getBoolean("playlist_expanded", false)) }

    // 收藏功能状态
    var collectionExpanded by remember { mutableStateOf(minePrefs.getBoolean("collection_expanded", false)) }
    var userPlaylists by remember { mutableStateOf<List<UserPlaylistEntity>>(emptyList()) }
    var collectedSongCount by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // 本地自建歌单排序
    fun applyLocalUserPlaylistsOrder(list: List<UserPlaylistEntity>): List<UserPlaylistEntity> {
        val orderStr = minePrefs.getString("local_playlists_order", null)
        if (orderStr.isNullOrBlank() || list.size <= 1) return list
        val idList = orderStr.split(",").mapNotNull { it.trim().toLongOrNull() }
        val orderMap = idList.withIndex().associate { it.value to it.index }
        return list.sortedWith(compareBy({ orderMap[it.id] ?: Int.MAX_VALUE }, { it.id }))
    }

    // 本账号的歌单本地显示顺序（仅本地，酷狗服务端顺序不变）
    fun applyLocalOrder(list: List<UserPlaylistItem>): List<UserPlaylistItem> {
        val uid = authPrefs.getLong("userid", 0)
        return KuGouApi.applyLocalPlaylistOrder(list, minePrefs.getString("playlists_order_$uid", null))
    }

    fun saveLocalOrder(ordered: List<UserPlaylistItem>) {
        val uid = authPrefs.getLong("userid", 0)
        minePrefs.edit().putString("playlists_order_$uid", KuGouApi.encodePlaylistOrder(ordered)).apply()
    }

    // 我的页滚动：LazyListState 由外层（AppScaffold）提升创建并传入，切 tab 复用同一对象；
    // 首屏渲染稳定后（已有缓存歌单、高度完整）再滚回保存位置，并后台刷新数据（列表保持不塌陷）
    val scrollState = listState
    var contentReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentReady = true }

    // 加载本地收藏数据
    LaunchedEffect(collectionExpanded) {
        if (collectionExpanded) {
            scope.launch(Dispatchers.IO) {
                collectedSongCount = collectionDao.getCollectedSongCount()
                val raw = collectionDao.getAllUserPlaylists()
                userPlaylists = applyLocalUserPlaylistsOrder(raw)
            }
        }
    }

    fun loadPlaylists() {
        val token = authPrefs.getString("token", "") ?: ""
        val userid = authPrefs.getLong("userid", 0)
        if (token.isBlank() || userid == 0L) return
        playlistLoading = true
        playlistError = null
        scope.launch(Dispatchers.IO) {
            try {
                val resp = KuGouApi.service.getUserPlaylist(token, userid)
                if (resp.status == 1 && resp.data?.list != null) {
                    if (kugouFavOn) {
                        // 官方模式：删除黑名单过滤（已删的不回弹）→ 稳定顺序（新歌单首次出现即固化，刷新不跳）→ 分组（我喜欢置顶/自建/收藏/镜像）
                        val orderRaw = minePrefs.getString("playlists_order_$userid", null)
                        val blacklist = KuGouApi.readDeletedPlaylistIds(minePrefs, userid)
                        val raw = resp.data.list
                        val available = raw.filter { it.listid !in blacklist }
                        // 只有服务器确认不再返回该歌单（官方已真正删除）才释放黑名单；还返回说明官方没删掉 → 黑名单保留、本地一直隐藏
                        blacklist.forEach { id ->
                            if (raw.none { it.listid == id }) KuGouApi.removeDeletedPlaylistId(minePrefs, userid, id)
                        }
                        val mirror = collectionDao.getAllLikedPlaylists().map {
                            com.example.lxmusic.UserPlaylistItem(
                                // 镜像用负 listid：官方歌单 listid 都是正数（1、2…），避免与本地自增 id 撞号导致列表 key 重复崩溃
                                listid = -it.id,
                                listname = it.name,
                                pic = it.coverUrl,
                                global_collection_id = it.gid,
                                songcount = it.songcount
                            )
                        }
                        val (stable, newOrder) = KuGouApi.stabilizePlaylistOrder(available, orderRaw)
                        if (newOrder != orderRaw) minePrefs.edit().putString("playlists_order_$userid", newOrder).apply()
                        val (grouped, mirrorIds) = KuGouApi.groupPlaylists(stable, mirror, userid, "我喜欢")
                        likedMirrorIds = mirrorIds
                        playlists = grouped
                    } else {
                        playlists = KuGouApi.mergeLocallyAddedPlaylists(applyLocalOrder(resp.data.list))
                    }
                    minePrefs.edit()
                        .putString("playlists_json", gson.toJson(playlists))
                        .putLong("playlists_json_uid", userid)
                        .apply()
                } else {
                    playlistError = "获取歌单失败"
                }
            } catch (e: Exception) {
                playlistError = "网络错误"
            } finally {
                playlistLoading = false
            }
        }
    }

    // 首屏渲染稳定后（已有缓存歌单、高度完整）再滚回保存位置，并后台刷新数据（列表保持不塌陷）
    LaunchedEffect(contentReady) {
        if (contentReady) {
            if (initialScrollOffset > 0) {
                runCatching { scrollState.scrollToItem(0, initialScrollOffset) }
            }
            if (isLoggedIn && playlists.isNotEmpty()) {
                loadPlaylists()
            }
        }
    }

    // 只在“账号/登录状态 真正变化”时清空重拉；重进同一账号不清空，
    // 避免歌单区内容高度塌陷把 LazyListState 的滚动位置夹回顶部
    val mineUserid = authPrefs.getLong("userid", 0)
    val authKey = "$mineUserid-$isLoggedIn"
    LaunchedEffect(mineUserid, isLoggedIn) {
        if (authKey != minePrefs.getString("last_load_auth", null)) {
            // 真正的账号切换 / 登录状态变化
            minePrefs.edit().putString("last_load_auth", authKey).apply()
            playlists = emptyList()
            playlistError = null
            if (isLoggedIn) {
                loadPlaylists()
            } else {
                minePrefs.edit().remove("playlists_json").remove("playlists_json_uid").apply()
            }
        } else if (isLoggedIn && playlists.isEmpty()) {
            // 同账号重进但没有数据：后台补拉一次
            loadPlaylists()
        }
    }


    val navBarHeight = remember {
        val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }
    val navBarDp = with(LocalDensity.current) { navBarHeight.toDp() }

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize()
    ) {
        item {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 120.dp + navBarDp)
        ) {
        // 用户信息卡片
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .then(if (!isLoggedIn) Modifier.clickable { onAvatarClick() } else Modifier),
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    if (isLoggedIn && !avatarUrl.isNullOrBlank()) {
                        val painter = rememberAsyncImagePainter(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(avatarUrl)
                                .memoryCacheKey(avatarUrl)
                                .crossfade(150)
                                .build()
                        )
                        Image(
                            painter = painter,
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "头像",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (isLoggedIn) nickname else "点击登录",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isLoggedIn) "已登录" else "登录后享受更多功能",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 功能按钮
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FunctionButton(
                    icon = Icons.Default.Favorite,
                    label = "收藏",
                    onClick = {
                        collectionExpanded = !collectionExpanded
                        minePrefs.edit().putBoolean("collection_expanded", collectionExpanded).apply()
                    },
                    modifier = Modifier.weight(1f)
                )
                FunctionButton(
                    icon = Icons.Default.LibraryMusic,
                    label = "歌单",
                    onClick = {
                        playlistExpanded = !playlistExpanded
                        minePrefs.edit().putBoolean("playlist_expanded", playlistExpanded).apply()
                        if (playlistExpanded && playlists.isEmpty() && !playlistLoading) {
                            loadPlaylists()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                FunctionButton(
                    icon = Icons.Default.MusicNote,
                    label = "本地",
                    onClick = onLocalClick,
                    modifier = Modifier.weight(1f)
                )
                FunctionButton(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    label = "听歌排行",
                    onClick = { },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 收藏列表（可折叠，显示在歌单上方）
        AnimatedVisibility(
            visible = collectionExpanded,
            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
            exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(16.dp))

                // 收藏标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "收藏",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    // 新建歌单按钮
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, "新建歌单", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // 多选管理按钮
                    IconButton(onClick = { onManagePlaylists() }) {
                        Icon(Icons.Default.Checklist, "管理歌单", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // 折叠按钮
                    IconButton(onClick = {
                        collectionExpanded = false
                        minePrefs.edit().putBoolean("collection_expanded", false).apply()
                    }) {
                        Icon(Icons.Default.ExpandLess, "收起", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // "我的收藏" 固定条目
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCollectionDetailClick("favorites", 0) },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Favorite, null, Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.title_my_favorites), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "$collectedSongCount 首", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))

                // 用户自建歌单列表
                if (userPlaylists.isEmpty()) {
                    Text(
                        "暂无歌单，点击 + 新建",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    userPlaylists.forEach { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCollectionDetailClick("playlist", pl.id) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.LibraryMusic, null, Modifier.size(26.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(pl.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // 新建歌单对话框
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false; newPlaylistName = "" },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text("新建歌单") },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("输入歌单名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                scope.launch(Dispatchers.IO) {
                                    val newId = collectionDao.createPlaylist(UserPlaylistEntity(name = newPlaylistName.trim()))
                                    val raw = collectionDao.getAllUserPlaylists()
                                    val ordered = applyLocalUserPlaylistsOrder(raw)
                                    val newOrderStr = ordered.joinToString(",") { it.id.toString() }
                                    minePrefs.edit().putString("local_playlists_order", newOrderStr).apply()
                                    withContext(Dispatchers.Main) {
                                        userPlaylists = ordered
                                        showCreateDialog = false
                                        newPlaylistName = ""
                                        Toast.makeText(context, "歌单已创建", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = newPlaylistName.isNotBlank()
                    ) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false; newPlaylistName = "" }) { Text("取消") }
                }
            )
        }

        // 歌单列表（可折叠）
        AnimatedVisibility(
            visible = playlistExpanded,
            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
            exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(16.dp))

                // 歌单标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "我的歌单",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onManageKugouPlaylists) {
                        Icon(
                            Icons.Default.Checklist,
                            contentDescription = "管理歌单",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        playlistExpanded = false
                        minePrefs.edit().putBoolean("playlist_expanded", false).apply()
                    }) {
                        Icon(
                            Icons.Default.ExpandLess,
                            contentDescription = "收起",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                when {
                    playlistLoading && playlists.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IOLoadingIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                    playlistError != null -> {
                        Text(
                            text = playlistError!!,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    playlists.isEmpty() -> {
                        Text(
                            text = "暂无歌单",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            playlists.forEach { playlist ->
                                PlaylistCard(
                                    playlist = playlist,
                                    onClick = { onPlaylistDetailClick(playlist) },
                                    countOverride = if (kugouFavOn && playlist.listname == "我喜欢") {
                                        // 「我喜欢的」：用官方∪本地的合并数量（进页面后记录），未记录时用官方缓存数
                                        KuGouApi.likedCount() ?: playlist.songcount
                                    } else {
                                        KuGouApi.playlistRealCount(playlist.listid) ?: playlist.songcount
                                    },
                                    countLabel = if (playlist.listid in likedMirrorIds && playlist.songcount <= 0) "已收藏" else null
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        }
        }
    }
}

@Composable
fun FunctionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
