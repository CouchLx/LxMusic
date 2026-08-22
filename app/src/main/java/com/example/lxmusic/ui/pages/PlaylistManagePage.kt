package com.example.lxmusic.ui.pages

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.MusicDatabase
import com.example.lxmusic.UserPlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 酷狗在线歌单管理页：
 * 1. 【长按拖动排序】：基于 sh.calvin.reorderable 实现平滑换位与阴影悬浮动效，实时保存本地自定义排序。
 * 2. 【现代卡片 UI】：精致圆角卡片、封面高清呈现、歌曲数与类型标签、丝滑选中动效。
 * 3. 【批量操作与保护】：底部悬浮批量删除栏、系统内置歌单（如「我喜欢」）标识保护、酷狗服务端异步同步与本地黑名单兜底。
 */
@Composable
fun PlaylistManagePage(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authPrefs = remember { context.getSharedPreferences("auth", android.content.Context.MODE_PRIVATE) }
    val minePrefs = remember { context.getSharedPreferences("mine_state", android.content.Context.MODE_PRIVATE) }
    val uid = authPrefs.getLong("userid", 0)

    var playlists by remember { mutableStateOf<List<UserPlaylistItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val db = remember { MusicDatabase.getDatabase(context) }
    val collectionDao = remember { db.collectionDao() }
    var likedMirrorIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var officialList by remember { mutableStateOf<List<UserPlaylistItem>>(emptyList()) }

    fun load() {
        scope.launch(Dispatchers.IO) {
            try {
                val resp = KuGouApi.service.getUserPlaylist(KuGouApi.token, KuGouApi.userid.toLongOrNull() ?: 0L)
                val orderRaw = minePrefs.getString("playlists_order_$uid", null)
                val blacklist = KuGouApi.readDeletedPlaylistIds(minePrefs, uid)
                val raw = resp.data?.list.orEmpty()
                val list = raw.filter { it.listid !in blacklist }
                blacklist.forEach { id ->
                    if (raw.none { it.listid == id }) KuGouApi.removeDeletedPlaylistId(minePrefs, uid, id)
                }
                val mirror = collectionDao.getAllLikedPlaylists().map {
                    UserPlaylistItem(
                        listid = -it.id,
                        listname = it.name,
                        pic = it.coverUrl,
                        global_collection_id = it.gid,
                        songcount = it.songcount
                    )
                }
                val (stable, newOrder) = KuGouApi.stabilizePlaylistOrder(list, orderRaw)
                if (newOrder != orderRaw) minePrefs.edit().putString("playlists_order_$uid", newOrder).apply()
                val (grouped, mirrorIds) = KuGouApi.groupPlaylists(stable, mirror, uid, "我喜欢")
                withContext(Dispatchers.Main) {
                    officialList = list
                    likedMirrorIds = mirrorIds
                    playlists = grouped
                    loading = false
                    error = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loading = false
                    error = "获取歌单失败: ${e.message}"
                }
            }
        }
    }

    fun saveOrder(list: List<UserPlaylistItem>) {
        minePrefs.edit().putString("playlists_order_$uid", KuGouApi.encodePlaylistOrder(list)).apply()
    }

    fun toggle(id: Long) {
        selection = if (id in selection) selection - id else selection + id
    }

    fun deleteSelected() {
        if (selection.isEmpty()) return
        deleting = true
        scope.launch(Dispatchers.IO) {
            var okCount = 0
            var failCount = 0
            val removedIds = selection.toMutableSet()
            selection.forEach { id ->
                val item = playlists.firstOrNull { it.listid == id } ?: return@forEach
                if (id in likedMirrorIds) {
                    collectionDao.deleteLikedPlaylistById(-id)
                    val officialSame = officialList.firstOrNull { it.listname == item.listname && it.listid != id }
                    if (officialSame != null) {
                        removedIds.add(officialSame.listid)
                        KuGouApi.addDeletedPlaylistId(minePrefs, uid, officialSame.listid)
                        if (!KuGouApi.deleteKuGouPlaylist(officialSame.listid)) failCount++
                    }
                    okCount++
                } else if (item.is_default > 0) {
                    KuGouApi.addDeletedPlaylistId(minePrefs, uid, id)
                    okCount++
                } else {
                    KuGouApi.addDeletedPlaylistId(minePrefs, uid, id)
                    if (KuGouApi.deleteKuGouPlaylist(id)) okCount++ else {
                        failCount++
                        okCount++
                    }
                }
            }
            withContext(Dispatchers.Main) {
                playlists = playlists.filter { it.listid !in removedIds }
                saveOrder(playlists)
                minePrefs.edit()
                    .remove("playlists_json")
                    .remove("playlists_json_uid")
                    .apply()
                Toast.makeText(
                    context,
                    if (failCount == 0) "已删除 $okCount 个歌单" else "已删除 $okCount 个歌单（酷狗后端同步失败 $failCount 个，本地已隐藏）",
                    Toast.LENGTH_SHORT
                ).show()
                deleting = false
                selection = emptySet()
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val manageListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = manageListState,
        onMove = { from, to ->
            val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
            val toId = to.key as? Long ?: return@rememberReorderableLazyListState
            val fromIdx = playlists.indexOfFirst { it.listid == fromId }
            val toIdx = playlists.indexOfFirst { it.listid == toId }
            if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                val newList = playlists.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
                playlists = newList
                saveOrder(newList)
            }
        }
    )

    val allSelected = playlists.isNotEmpty() && selection.size == playlists.size

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部操作栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            selection = if (allSelected) emptySet()
                            else playlists.map { it.listid }.toSet()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (allSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (allSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (selection.isEmpty()) "全选" else "已选 ${selection.size} 项",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    TextButton(onClick = onBack) {
                        Text("完成", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 提示副标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "共 ${playlists.size} 个歌单 · 长按右侧把手拖拽排序",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularWavyProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = error!!,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                playlists.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "暂无歌单",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = manageListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlists.size, key = { playlists[it].listid }) { index ->
                            val pl = playlists[index]
                            val selected = pl.listid in selection
                            val isMirror = pl.listid in likedMirrorIds
                            val isDefault = pl.is_default > 0 || pl.listname == "我喜欢" || pl.listname == "默认收藏"
                            val realCount = if (isMirror) pl.songcount else (KuGouApi.playlistRealCount(pl.listid) ?: pl.songcount)

                            ReorderableItem(
                                state = reorderableState,
                                key = pl.listid
                            ) { isDragging ->
                                val elevation by animateDpAsState(
                                    targetValue = if (isDragging) 8.dp else 0.dp,
                                    label = "cardElevation"
                                )
                                val scale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.02f else 1.0f,
                                    label = "cardScale"
                                )

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .scale(scale)
                                        .shadow(elevation, RoundedCornerShape(16.dp))
                                        .longPressDraggableHandle(onDragStarted = {}, onDragStopped = {}),
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    onClick = { toggle(pl.listid) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 勾选框
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (selected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (selected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        // 封面图
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val coverUrl = pl.pic?.replace("{size}", "400")?.replace("http://", "https://")
                                            if (!coverUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = coverUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    if (pl.listname == "我喜欢") Icons.Default.Favorite else Icons.Default.LibraryMusic,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        // 歌单名与副标题标签
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = pl.listname ?: "歌单",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (isDefault) {
                                                    Spacer(Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                                    ) {
                                                        Text(
                                                            text = "系统",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                } else if (isMirror) {
                                                    Spacer(Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                                    ) {
                                                        Text(
                                                            text = "已收藏",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(2.dp))

                                            Text(
                                                text = if (isMirror && realCount <= 0) "已收藏歌单" else "$realCount 首歌曲",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(Modifier.width(8.dp))

                                        // 拖动排序把手指示
                                        Box(
                                            modifier = Modifier.padding(6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = "拖动排序",
                                                modifier = Modifier.size(22.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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

        // 底部悬浮批量删除操作栏（选中时弹出）
        AnimatedVisibility(
            visible = selection.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选中 ${selection.size} 个歌单",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(onClick = { selection = emptySet() }) {
                        Text("取消选择")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = { showDeleteDialog = true },
                        enabled = !deleting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (deleting) "删除中…" else "删除 (${selection.size})")
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("删除歌单") },
            text = {
                Text(
                    "确定删除选中的 ${selection.size} 个歌单吗？\n" +
                    "• 自建歌单将被彻底删除；\n" +
                    "• 收藏的歌单将取消收藏；\n" +
                    "• 若服务端同步受阻，本地将进行黑名单隐藏。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    deleteSelected()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

