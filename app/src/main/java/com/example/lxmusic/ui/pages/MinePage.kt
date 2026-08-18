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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SelectAll
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
    onManagePlaylists: () -> Unit = {}
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
    var playlists by remember { mutableStateOf<List<UserPlaylistItem>>(emptyList()) }
    var playlistLoading by remember { mutableStateOf(false) }
    var playlistError by remember { mutableStateOf<String?>(null) }
    var playlistExpanded by remember { mutableStateOf(minePrefs.getBoolean("playlist_expanded", false)) }

    // 收藏功能状态
    var collectionExpanded by remember { mutableStateOf(minePrefs.getBoolean("collection_expanded", false)) }
    var userPlaylists by remember { mutableStateOf<List<UserPlaylistEntity>>(emptyList()) }
    var collectedSongCount by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // 加载本地收藏数据
    LaunchedEffect(collectionExpanded) {
        if (collectionExpanded) {
            scope.launch(Dispatchers.IO) {
                collectedSongCount = collectionDao.getCollectedSongCount()
                userPlaylists = collectionDao.getAllUserPlaylists()
            }
        }
    }

    // 从缓存加载歌单
    LaunchedEffect(Unit) {
        val cached = minePrefs.getString("playlists_json", null)
        if (!cached.isNullOrBlank()) {
            try {
                val type = TypeToken.getParameterized(List::class.java, UserPlaylistItem::class.java).type
                val list: List<UserPlaylistItem> = gson.fromJson(cached, type)
                if (list.isNotEmpty()) playlists = list
            } catch (_: Exception) {}
        }
        // 如果已展开且没有缓存数据，自动加载
        if (playlistExpanded && playlists.isEmpty() && isLoggedIn) {
            val token = authPrefs.getString("token", "") ?: ""
            val userid = authPrefs.getLong("userid", 0)
            if (token.isNotBlank() && userid != 0L) {
                playlistLoading = true
                scope.launch(Dispatchers.IO) {
                    try {
                        val resp = KuGouApi.service.getUserPlaylist(token, userid)
                        if (resp.status == 1 && resp.data?.list != null) {
                            playlists = resp.data.list
                            minePrefs.edit().putString("playlists_json", gson.toJson(playlists)).apply()
                        }
                    } catch (_: Exception) {}
                    finally { playlistLoading = false }
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
                    playlists = resp.data.list
                    minePrefs.edit().putString("playlists_json", gson.toJson(playlists)).apply()
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

    // 每次进入页面自动刷新歌单
    LaunchedEffect(Unit) {
        if (isLoggedIn) loadPlaylists()
    }


    val navBarHeight = remember {
        val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }
    val navBarDp = with(LocalDensity.current) { navBarHeight.toDp() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                        Icon(Icons.Default.SelectAll, "管理歌单", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    collectionDao.createPlaylist(UserPlaylistEntity(name = newPlaylistName.trim()))
                                    userPlaylists = collectionDao.getAllUserPlaylists()
                                    withContext(Dispatchers.Main) {
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
                    playlistLoading -> {
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
                                    onClick = { onPlaylistDetailClick(playlist) }
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
