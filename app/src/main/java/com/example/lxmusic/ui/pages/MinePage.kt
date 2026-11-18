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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Cloud
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.lxmusic.ui.components.AccountPlatformSwitcher
import com.example.lxmusic.ui.components.IOLoadingIndicator
import com.example.lxmusic.R
import androidx.compose.ui.res.stringResource
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.MusicDatabase
import com.example.lxmusic.NeteaseApi
import com.example.lxmusic.NeteaseAuth
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
import kotlinx.coroutines.withTimeoutOrNull

/** 网易云品牌红（账号卡未登录占位与 VIP 标识；不参与主题配色） */
private val NeteaseBrand = Color(0xFFE60026)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinePage(
    loginVersion: Int = 0,
    neteaseVersion: Int = 0,
    // 账号卡当前平台（kugou / netease），状态提升到 MainActivity
    accountTab: String = "kugou",
    // 平台切换：右滑账号卡上的切换器触发（原来放在顶栏，会跟标题抢层级）
    onAccountPlatformChange: (String) -> Unit = {},
    onLocalClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onNeteaseLoginClick: () -> Unit = {},
    onNeteaseAuthChanged: () -> Unit = {},
    // 网易云歌单点击：id / 名称 / 封面 / 曲目数 / 创建者
    onNeteasePlaylistClick: (Long, String, String, Int, String) -> Unit = { _, _, _, _, _ -> },
    onPlaylistDetailClick: (UserPlaylistItem) -> Unit = {},
    onCollectionDetailClick: (String, Long) -> Unit = { _, _ -> },
    onManagePlaylists: () -> Unit = {},
    onManageKugouPlaylists: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    initialScrollOffset: Int = 0,
    // 顶栏悬浮时预留的顶部内容内边距（首屏内容从胶囊下方开始，向上滚动时从顶栏下方穿过）
    topContentPadding: Dp = 0.dp
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
    // 账号卡：平台由顶栏切换器控制（状态在 MainActivity），这里只读登录态
    val neteaseAccount = remember(neteaseVersion) { NeteaseAuth.load(context) }
    var showNeteaseAccountDialog by remember { mutableStateOf(false) }
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

    // ===== 网易云歌单（跟随顶栏平台切换，和账号卡同一套原理）=====
    fun loadCachedNeteasePlaylists(): List<UserPlaylistItem> {
        val uid = neteaseAccount.uid
        if (!neteaseAccount.loggedIn || uid == 0L) return emptyList()
        val cached = minePrefs.getString("netease_playlists_json", null)
        if (!cached.isNullOrBlank() && minePrefs.getLong("netease_playlists_json_uid", 0L) == uid) {
            try {
                val type = TypeToken.getParameterized(List::class.java, UserPlaylistItem::class.java).type
                return gson.fromJson(cached, type)
            } catch (_: Exception) {}
        }
        return emptyList()
    }
    var neteasePlaylists by remember(neteaseVersion) { mutableStateOf(loadCachedNeteasePlaylists()) }
    var neteasePlaylistLoading by remember { mutableStateOf(false) }
    var neteasePlaylistError by remember { mutableStateOf<String?>(null) }
    // 歌单创建者（详情页头部展示；不落盘，冷启动后首次刷新即补上）
    var neteasePlaylistCreators by remember(neteaseVersion) { mutableStateOf<Map<Long, String>>(emptyMap()) }

    fun loadNeteasePlaylists() {
        val uid = neteaseAccount.uid
        if (!neteaseAccount.loggedIn || uid == 0L || neteasePlaylistLoading) return
        neteasePlaylistLoading = true
        neteasePlaylistError = null
        scope.launch(Dispatchers.IO) {
            try {
                val resp = NeteaseApi.service.userPlaylists(
                    uid = uid,
                    limit = 100,
                    offset = 0,
                    timestamp = System.currentTimeMillis()
                )
                if (resp.code == 200) {
                    val items = resp.playlist.orEmpty()
                    val mapped = items.map { pl ->
                        UserPlaylistItem(
                            listid = pl.id,
                            listname = pl.name ?: "未命名歌单",
                            pic = pl.coverImgUrl,
                            songcount = pl.trackCount
                        )
                    }
                    neteasePlaylistCreators = items.associate { it.id to (it.creator?.nickname ?: "") }
                    neteasePlaylists = mapped
                    minePrefs.edit()
                        .putString("netease_playlists_json", gson.toJson(mapped))
                        .putLong("netease_playlists_json_uid", uid)
                        .apply()
                } else {
                    neteasePlaylistError = "获取歌单失败（${resp.code}）"
                }
            } catch (e: Exception) {
                neteasePlaylistError = e.message ?: "网络错误"
            } finally {
                neteasePlaylistLoading = false
            }
        }
    }

    // 切到网易云平台（或刚登录）时刷新一次；酷狗平台不触发
    LaunchedEffect(accountTab, neteaseAccount.loggedIn, neteaseAccount.uid) {
        if (accountTab == "netease" && neteaseAccount.loggedIn) {
            loadNeteasePlaylists()
        }
    }

    // 收藏功能状态
    var collectionExpanded by remember { mutableStateOf(minePrefs.getBoolean("collection_expanded", false)) }
    var userPlaylists by remember { mutableStateOf<List<UserPlaylistEntity>>(emptyList()) }
    var userPlaylistSongCounts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var userPlaylistCovers by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var firstCollectedCover by remember { mutableStateOf<String?>(null) }
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
                val collected = collectionDao.getAllCollectedSongs()
                val cCover = collected.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri
                val raw = collectionDao.getAllUserPlaylists()
                val ordered = applyLocalUserPlaylistsOrder(raw)
                val counts = mutableMapOf<Long, Int>()
                val covers = mutableMapOf<Long, String>()
                raw.forEach { pl ->
                    val songs = collectionDao.getPlaylistSongs(pl.id)
                    counts[pl.id] = songs.size
                    val cover = pl.coverUrl ?: songs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri
                    if (!cover.isNullOrBlank()) {
                        covers[pl.id] = cover
                    }
                }
                withContext(Dispatchers.Main) {
                    userPlaylists = ordered
                    userPlaylistSongCounts = counts
                    userPlaylistCovers = covers
                    firstCollectedCover = cCover
                }
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topContentPadding)
    ) {
        item {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 120.dp + navBarDp)
        ) {
        // 账号卡：右侧切换器切换平台（酷狗 ⇄ 网易云），卡片内容随之切换
        if (accountTab == "netease") {
            AccountCard(
                icon = Icons.Default.Cloud,
                loggedIn = neteaseAccount.loggedIn,
                avatarUrl = neteaseAccount.avatarUrl.replace("http://", "https://").ifBlank { null },
                title = if (neteaseAccount.loggedIn) {
                    neteaseAccount.nickname.ifBlank { "网易云用户" }
                } else "点击登录",
                subtitle = if (neteaseAccount.loggedIn) "已登录 · 网易云音乐" else "登录后同步歌单与每日推荐",
                containerColor = NeteaseBrand.copy(alpha = 0.16f),
                iconTint = NeteaseBrand,
                onClick = {
                    if (neteaseAccount.loggedIn) showNeteaseAccountDialog = true else onNeteaseLoginClick()
                },
                trailing = {
                    AccountPlatformSwitcher(
                        platform = accountTab,
                        onPlatformChange = onAccountPlatformChange
                    )
                }
            )
        } else {
            AccountCard(
                icon = Icons.Default.MusicNote,
                loggedIn = isLoggedIn,
                avatarUrl = avatarUrl,
                title = if (isLoggedIn) nickname else "点击登录",
                subtitle = if (isLoggedIn) "已登录 · 酷狗音乐" else "登录后享受更多功能",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = if (isLoggedIn) null else onAvatarClick,
                trailing = {
                    AccountPlatformSwitcher(
                        platform = accountTab,
                        onPlatformChange = onAccountPlatformChange
                    )
                }
            )
        }

        if (showNeteaseAccountDialog) {
            AlertDialog(
                onDismissRequest = { showNeteaseAccountDialog = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text("网易云音乐") },
                text = {
                    Column {
                        Text(
                            text = neteaseAccount.nickname.ifBlank { "网易云用户" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "UID: ${neteaseAccount.uid}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (neteaseAccount.vipType > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "黑胶 VIP",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeteaseBrand
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showNeteaseAccountDialog = false
                        scope.launch {
                            try {
                                // 尽力让服务端会话失效；本地清理放 finally，断网也能退出
                                withTimeoutOrNull(4000) {
                                    withContext(Dispatchers.IO) {
                                        runCatching { NeteaseApi.service.logout(System.currentTimeMillis()) }
                                    }
                                }
                            } finally {
                                NeteaseAuth.clear(context)
                                // 顺带清掉该账号的歌单缓存，避免换号后串数据
                                minePrefs.edit()
                                    .remove("netease_playlists_json")
                                    .remove("netease_playlists_json_uid")
                                    .apply()
                                onNeteaseAuthChanged()
                            }
                        }
                    }) {
                        Text("退出登录", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNeteaseAccountDialog = false }) { Text("取消") }
                }
            )
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
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            if (!firstCollectedCover.isNullOrBlank()) {
                                AsyncImage(
                                    model = firstCollectedCover,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.title_my_favorites),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "$collectedSongCount 首歌曲",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        userPlaylists.forEach { pl ->
                            val cover = pl.coverUrl ?: userPlaylistCovers[pl.id]
                            val count = userPlaylistSongCounts[pl.id] ?: 0
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCollectionDetailClick("playlist", pl.id) },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(64.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        if (!cover.isNullOrBlank()) {
                                            AsyncImage(
                                                model = cover,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.LibraryMusic,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(32.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = pl.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "$count 首歌曲",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
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

        // 歌单列表（可折叠）：内容跟随顶栏平台切换（酷狗 / 网易云）
        val neteaseMode = accountTab == "netease"
        val shownPlaylists = if (neteaseMode) neteasePlaylists else playlists
        val shownLoading = if (neteaseMode) neteasePlaylistLoading else playlistLoading
        val shownError = if (neteaseMode) neteasePlaylistError else playlistError
        val retryShown: () -> Unit = { if (neteaseMode) loadNeteasePlaylists() else loadPlaylists() }
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
                    // 刷新状态显示在标题行内（不额外占行高，列表不会上下跳动）
                    when {
                        shownLoading -> Text(
                            text = "刷新中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        shownError != null -> Text(
                            text = "刷新失败 · 重试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { retryShown() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    // 歌单管理只有酷狗有对应页面；网易云暂不提供
                    if (!neteaseMode) {
                        IconButton(onClick = onManageKugouPlaylists) {
                            Icon(
                                Icons.Default.Checklist,
                                contentDescription = "管理歌单",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                    // 网易云未登录：给登录入口，而不是一个空列表
                    neteaseMode && !neteaseAccount.loggedIn -> {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "登录网易云音乐后可同步歌单",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "去登录",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clickable { onNeteaseLoginClick() }
                            )
                        }
                    }
                    shownLoading && shownPlaylists.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IOLoadingIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                    shownPlaylists.isEmpty() && shownError != null -> {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = shownError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "点击重试",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clickable(enabled = !shownLoading) { retryShown() }
                            )
                        }
                    }
                    shownPlaylists.isEmpty() -> {
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
                            shownPlaylists.forEach { playlist ->
                                if (neteaseMode) {
                                    PlaylistCard(
                                        playlist = playlist,
                                        onClick = {
                                            onNeteasePlaylistClick(
                                                playlist.listid,
                                                playlist.listname ?: "",
                                                playlist.pic ?: "",
                                                playlist.songcount,
                                                neteasePlaylistCreators[playlist.listid] ?: ""
                                            )
                                        }
                                    )
                                } else {
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

/**
 * 账号卡：点「头像」去登录 / 看账号信息（整卡不再可点，也没有右侧箭头——
 * 箭头容易和右侧平台切换器抢注意力，点头像更符合「点头像 = 看这个账号」的直觉）。
 */
@Composable
private fun AccountCard(
    icon: ImageVector,
    loggedIn: Boolean,
    avatarUrl: String?,
    title: String,
    subtitle: String,
    containerColor: Color,
    iconTint: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    // 卡片右侧的附加控件（目前放平台切换：账号身份的一部分，比顶栏更自然）
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
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
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
                shape = RoundedCornerShape(12.dp),
                color = containerColor
            ) {
                if (loggedIn && !avatarUrl.isNullOrBlank()) {
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
                            imageVector = icon,
                            contentDescription = "头像",
                            modifier = Modifier.size(48.dp),
                            tint = iconTint
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                // 第一行：昵称 + 贴右的平台切换（和名字同一行，不再浮在卡片中间）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (trailing != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        trailing()
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
