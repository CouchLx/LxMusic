package com.example.lxmusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lxmusic.CollectedSongEntity
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.MusicDatabase
import com.example.lxmusic.PlaylistSongCrossRef
import com.example.lxmusic.UserPlaylistEntity
import com.example.lxmusic.UserPlaylistItem
import com.example.lxmusic.model.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SongContextMenuActions(
    song: SongInfo,
    onDismiss: () -> Unit,
    onAddToQueueNext: (() -> Unit)? = null,
    showCollectionActions: Boolean = true,
    showOnlyPlaylistAdd: Boolean = false  // true 时隐藏收藏，仅显示「添加到我的歌单」（本地音乐用）
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MusicDatabase.getDatabase(context) }
    val collectionDao = remember { db.collectionDao() }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var userPlaylists by remember { mutableStateOf<List<UserPlaylistEntity>>(emptyList()) }
    // 本地新建歌单
    var showNewLocalDialog by remember { mutableStateOf(false) }
    var newLocalName by remember { mutableStateOf("") }
    // 收藏到酷狗开关（读设置，默认关闭；开启后收藏/添加到酷狗账号歌单）
    val settingsPrefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var favoriteToKugou by remember { mutableStateOf(settingsPrefs.getBoolean("favorite_to_kugou", false)) }
    // 收藏歌曲同步到本地收藏（读设置）
    var syncLocalFavorite by remember { mutableStateOf(settingsPrefs.getBoolean("favorite_sync_local", false)) }
    // 酷狗歌单多选面板状态
    var showKugouPicker by remember { mutableStateOf(false) }
    var kugouPlaylists by remember { mutableStateOf<List<UserPlaylistItem>>(emptyList()) }
    var selectedKugouIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var addingToKugou by remember { mutableStateOf(false) }
    var kugouPickerError by remember { mutableStateOf<String?>(null) }
    var showNewKugouDialog by remember { mutableStateOf(false) }
    var newKugouName by remember { mutableStateOf("") }
    var kugouLikeTick by remember { mutableStateOf(0) }

    fun getFilePath(): String? {
        val parts = song.filePath.split("|")
        val hash = parts.getOrElse(0) { "" }
        val audioId = parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L
        if (hash.isBlank()) return null
        return "${hash}|${audioId}"
    }

    fun saveSongEntity(): CollectedSongEntity? {
        val parts = song.filePath.split("|")
        val hash = parts.getOrElse(0) { "" }
        val audioId = parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L
        if (hash.isBlank()) return null
        return CollectedSongEntity(
            filePath = "${hash}|${audioId}",
            title = song.title,
            artist = song.artist,
            albumArtUri = song.albumArtUri,
            duration = song.duration,
            hash = hash,
            audioId = audioId,
            albumId = song.albumId,
            mixsongid = song.mixsongid
        )
    }

    // 生成歌单-歌曲关联数据：本地歌曲存绝对路径（离线可播+歌词），网络歌曲存 hash|audioId
    fun buildPlaylistCrossRef(playlistId: Long): PlaylistSongCrossRef? {
        if (song.filePath.startsWith("/")) {
            return PlaylistSongCrossRef(
                playlistId = playlistId,
                songFilePath = song.filePath,
                title = song.title,
                artist = song.artist,
                albumArtUri = song.albumArtUri,
                duration = song.duration,
                lyrics = song.lyrics
            )
        }
        val parts = song.filePath.split("|")
        val hash = parts.getOrElse(0) { "" }
        val audioId = parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L
        if (hash.isBlank()) return null
        return PlaylistSongCrossRef(
            playlistId = playlistId,
            songFilePath = "${hash}|${audioId}",
            title = song.title,
            artist = song.artist,
            albumArtUri = song.albumArtUri,
            duration = song.duration,
            hash = hash,
            audioId = audioId,
            albumId = song.albumId,
            mixsongid = song.mixsongid
        )
    }

    // 当前歌曲是否已收藏（开启收藏到酷狗时查酷狗「喜欢」歌单）
    var isCollected by remember { mutableStateOf(false) }
    LaunchedEffect(song.filePath, favoriteToKugou, syncLocalFavorite, kugouLikeTick) {
        val fp = getFilePath() ?: return@LaunchedEffect
        if (favoriteToKugou) {
            // 官方模式：本地「喜欢镜像」先写即亮；酷狗官方喜欢的也亮；本地“我的收藏”忽略
            isCollected = if (KuGouApi.token.isNotBlank() && KuGouApi.userid.isNotBlank()) {
                val liked = withContext(Dispatchers.IO) { KuGouApi.isSongLikedKugou(fp) }
                liked || collectionDao.isLikedSong(fp)
            } else {
                collectionDao.isLikedSong(fp)
            }
        } else {
            isCollected = collectionDao.isSongCollected(fp)
        }
    }

    // 加载酷狗歌单列表（含 喜欢/默认收藏/创建的，is_default==1 的标注「默认」）
    fun loadKugouPlaylists(preselect: Long? = null) {
        if (KuGouApi.token.isBlank() || KuGouApi.userid.isBlank()) {
            kugouPickerError = "请先登录酷狗账号"
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val uid = KuGouApi.userid.toLongOrNull() ?: 0L
                val resp = KuGouApi.service.getUserPlaylist(KuGouApi.token, uid)
                // 只显示自己创建的歌单 + 默认「喜欢/收藏」歌单，不显示收藏的别人的歌单
                val list = resp.data?.list.orEmpty().filter {
                    it.is_default == 1 || it.list_create_userid == uid
                }
                withContext(Dispatchers.Main) {
                    kugouPlaylists = list
                    if (preselect != null) selectedKugouIds = selectedKugouIds + preselect
                    kugouPickerError = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { kugouPickerError = "获取歌单失败: ${e.message}" }
            }
        }
    }

    // 确认添加：把当前歌曲加入所有选中的酷狗歌单，成功后关闭菜单
    fun confirmAddToKugou() {
        if (selectedKugouIds.isEmpty() || addingToKugou) return
        addingToKugou = true
        scope.launch(Dispatchers.IO) {
            var okCount = 0
            var failCount = 0
            selectedKugouIds.forEach { listId ->
                val item = kugouPlaylists.firstOrNull { it.listid == listId }
                val ok = KuGouApi.addSongToKugouPlaylist(listId, song, item?.list_ver ?: 0L)
                if (ok) okCount++ else failCount++
            }
            withContext(Dispatchers.Main) {
                addingToKugou = false
                if (failCount == 0) {
                    android.widget.Toast.makeText(context, "添加成功", android.widget.Toast.LENGTH_SHORT).show()
                    onDismiss()
                } else if (okCount == 0) {
                    android.widget.Toast.makeText(context, "添加失败，请检查网络/登录", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "部分失败（成功 $okCount，失败 $failCount）", android.widget.Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

    // 下一首播放
    onAddToQueueNext?.let { action ->
        Surface(
            onClick = {
                action()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Queue, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text("下一首播放", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    // 收藏 / 添加歌单（本地音乐不需要时通过 showCollectionActions 关闭；showOnlyPlaylistAdd 时只保留歌单）
    if (showCollectionActions && !showOnlyPlaylistAdd) {
    // 收藏 / 取消收藏（开启收藏到酷狗时走酷狗「喜欢」歌单增删）
    Surface(
        onClick = {
            scope.launch(Dispatchers.IO) {
                val fp = getFilePath() ?: return@launch
                if (favoriteToKugou) {
                    // 本地先写「喜欢镜像」（立即可见、不消失），酷狗后台慢慢同步；
                    // “同步到本地收藏”开启时再额外复制一份到本地“我的收藏”
                    val target = !isCollected
                    isCollected = target
                    if (target) {
                        val e = saveSongEntity()
                        if (e != null) {
                            collectionDao.insertLikedSong(
                                com.example.lxmusic.LikedSongEntity(
                                    filePath = e.filePath,
                                    title = e.title,
                                    artist = e.artist,
                                    albumArtUri = e.albumArtUri,
                                    duration = e.duration,
                                    hash = e.hash,
                                    audioId = e.audioId,
                                    albumId = e.albumId,
                                    mixsongid = e.mixsongid
                                )
                            )
                            if (syncLocalFavorite) collectionDao.insertCollectedSong(e)
                        }
                    } else {
                        collectionDao.deleteLikedSong(fp)
                        if (syncLocalFavorite) collectionDao.deleteCollectedSong(fp)
                    }
                    kugouLikeTick++
                    // 维护「我喜欢的」统一数量（官方∪本地）
                    if (target) KuGouApi.bumpLikedCount(1) else KuGouApi.bumpLikedCount(-1)
                    val kugouOk = if (KuGouApi.token.isNotBlank() && KuGouApi.userid.isNotBlank()) {
                        if (target) KuGouApi.addToKugouLike(song) else KuGouApi.removeFromKugouLike(song)
                    } else false
                    val msg = when {
                        target && kugouOk -> "已添加到收藏"
                        target && KuGouApi.token.isBlank() -> "已添加到收藏（未登录，暂未同步官方）"
                        target -> "已添加到收藏（官方同步失败）"
                        kugouOk -> "已取消收藏"
                        else -> "已取消收藏（官方同步失败）"
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val entity = saveSongEntity() ?: return@launch
                if (isCollected) {
                    collectionDao.deleteCollectedSong(entity.filePath)
                    withContext(Dispatchers.Main) {
                        isCollected = false
                        android.widget.Toast.makeText(context, "已取消收藏", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    collectionDao.insertCollectedSong(entity)
                    withContext(Dispatchers.Main) {
                        isCollected = true
                        android.widget.Toast.makeText(context, "已添加到收藏", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Favorite, null, Modifier.size(24.dp),
                tint = if (isCollected) Color(0xFFE57373) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Text(
                if (isCollected) "已收藏" else "添加到收藏",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
    }

    // 添加到歌单（酷狗模式时走酷狗歌单多选+新建；showOnlyPlaylistAdd 时固定显示收藏页面的本地歌单）
    val useKugouPlaylist = favoriteToKugou && !showOnlyPlaylistAdd
    Surface(
        onClick = {
            if (useKugouPlaylist) {
                showKugouPicker = !showKugouPicker
                if (showKugouPicker) loadKugouPlaylists()
            } else {
                showPlaylistPicker = !showPlaylistPicker
                if (showPlaylistPicker) {
                    scope.launch(Dispatchers.IO) {
                        userPlaylists = collectionDao.getAllUserPlaylists()
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LibraryMusic, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(
                if (useKugouPlaylist) "添加到" else "添加到歌单",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (if (useKugouPlaylist) showKugouPicker else showPlaylistPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 本地歌单选择器（酷狗模式关闭时使用；showOnlyPlaylistAdd 时强制使用收藏页本地歌单）
    if (!useKugouPlaylist) {
        AnimatedVisibility(visible = showPlaylistPicker, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                if (userPlaylists.isEmpty()) {
                    Text(
                        "暂无歌单",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    userPlaylists.forEachIndexed { idx, pl ->
                        if (idx > 0) Spacer(Modifier.height(2.dp))
                        Surface(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val crossRef = buildPlaylistCrossRef(pl.id) ?: return@launch
                                    if (!collectionDao.isSongInPlaylist(pl.id, crossRef.songFilePath)) {
                                        collectionDao.addSongToPlaylist(crossRef)
                                    }
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "已添加到 ${pl.name}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LibraryMusic, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text(pl.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Color.White)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                // 新建歌单
                Surface(
                    onClick = {
                        newLocalName = ""
                        showNewLocalDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("新建歌单", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    // 本地新建歌单对话框
    if (showNewLocalDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showNewLocalDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("新建歌单") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = newLocalName,
                    onValueChange = { newLocalName = it },
                    placeholder = { Text("歌单名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = newLocalName.isNotBlank(),
                    onClick = {
                        val name = newLocalName.trim()
                        if (name.isNotBlank()) scope.launch(Dispatchers.IO) {
                            val plId = collectionDao.createPlaylist(
                                UserPlaylistEntity(name = name)
                            )
                            val crossRef = buildPlaylistCrossRef(plId) ?: return@launch
                            if (!collectionDao.isSongInPlaylist(plId, crossRef.songFilePath)) {
                                collectionDao.addSongToPlaylist(crossRef)
                            }
                            withContext(Dispatchers.Main) {
                                showNewLocalDialog = false
                                showPlaylistPicker = true
                                userPlaylists = collectionDao.getAllUserPlaylists()
                                android.widget.Toast.makeText(context, "已创建歌单「$name」并添加歌曲", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) { Text("创建并添加") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showNewLocalDialog = false }) { Text("取消") } }
        )
    }

    // 酷狗歌单多选面板（酷狗模式开启且非 showOnlyPlaylistAdd 时使用）
    if (useKugouPlaylist) {
        AnimatedVisibility(visible = showKugouPicker, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (kugouPickerError != null) {
                    Text(
                        kugouPickerError!!,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Surface(
                        onClick = { showNewKugouDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("新建歌单", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Button(
                        onClick = { confirmAddToKugou() },
                        enabled = selectedKugouIds.isNotEmpty() && !addingToKugou,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (addingToKugou) "添加中…" else "确认添加（${selectedKugouIds.size}）")
                    }
                    Spacer(Modifier.height(8.dp))
                    if (kugouPlaylists.isEmpty()) {
                        Text(
                            "正在加载歌单…",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                kugouPlaylists.forEachIndexed { idx, pl ->
                                    if (idx > 0) Spacer(Modifier.height(2.dp))
                                    val selected = pl.listid in selectedKugouIds
                                    Surface(
                                        onClick = {
                                            selectedKugouIds = if (selected) selectedKugouIds - pl.listid else selectedKugouIds + pl.listid
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.LibraryMusic, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                (pl.listname ?: "") + if (pl.is_default == 1) "（默认）" else "",
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (selected) {
                                                    Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 新建酷狗歌单对话框
        if (showNewKugouDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showNewKugouDialog = false },
                title = { Text("新建酷狗歌单") },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = newKugouName,
                        onValueChange = { newKugouName = it },
                        placeholder = { Text("歌单名称") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        enabled = newKugouName.isNotBlank(),
                        onClick = {
                            val name = newKugouName.trim()
                            showNewKugouDialog = false
                            newKugouName = ""
                            scope.launch(Dispatchers.IO) {
                                val newId = KuGouApi.createKugouPlaylist(name)
                                withContext(Dispatchers.Main) {
                                    if (newId != null && newId > 0) {
                                        android.widget.Toast.makeText(context, "已创建歌单", android.widget.Toast.LENGTH_SHORT).show()
                                        loadKugouPlaylists(preselect = newId)
                                    } else {
                                        android.widget.Toast.makeText(context, "创建歌单失败", android.widget.Toast.LENGTH_SHORT).show()
                                        loadKugouPlaylists()
                                    }
                                }
                            }
                        }
                    ) { Text("创建") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showNewKugouDialog = false }) { Text("取消") }
                }
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}
