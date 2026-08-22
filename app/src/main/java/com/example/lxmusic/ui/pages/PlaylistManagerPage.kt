package com.example.lxmusic.ui.pages

import android.content.Context
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.lxmusic.MusicDatabase
import com.example.lxmusic.R
import com.example.lxmusic.UserPlaylistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 本地自建歌单管理页面（收藏区管理入口）：
 * 1. 【长按拖拽排序】：基于 sh.calvin.reorderable 实现丝滑拖动换位，实时固化顺序并同步至我的页展示。
 * 2. 【精致卡片 UI】：圆角卡片、封面预览、歌曲统计副标题、悬浮拖动手感与立体投影。
 * 3. 【丰富管理功能】：多选批量删除、歌单重命名、新建歌单、全选/反选快捷操作。
 */
@Composable
fun PlaylistManagerPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MusicDatabase.getDatabase(context) }
    val collectionDao = remember { db.collectionDao() }
    val minePrefs = remember { context.getSharedPreferences("mine_state", Context.MODE_PRIVATE) }

    var userPlaylists by remember { mutableStateOf<List<UserPlaylistEntity>>(emptyList()) }
    val playlistSongCounts = remember { mutableStateMapOf<Long, Int>() }
    val playlistCovers = remember { mutableStateMapOf<Long, String?>() }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // 对话框状态
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var renameTargetPlaylist by remember { mutableStateOf<UserPlaylistEntity?>(null) }
    var renameInputText by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createInputText by remember { mutableStateOf("") }

    // 本地歌单顺序应用与保存
    fun applyLocalOrder(list: List<UserPlaylistEntity>): List<UserPlaylistEntity> {
        val orderStr = minePrefs.getString("local_playlists_order", null)
        if (orderStr.isNullOrBlank() || list.size <= 1) return list
        val idList = orderStr.split(",").mapNotNull { it.trim().toLongOrNull() }
        val orderMap = idList.withIndex().associate { it.value to it.index }
        return list.sortedWith(compareBy({ orderMap[it.id] ?: Int.MAX_VALUE }, { it.id }))
    }

    fun saveLocalOrder(ordered: List<UserPlaylistEntity>) {
        val orderStr = ordered.joinToString(",") { it.id.toString() }
        minePrefs.edit().putString("local_playlists_order", orderStr).apply()
    }

    fun loadData() {
        scope.launch(Dispatchers.IO) {
            val list = collectionDao.getAllUserPlaylists()
            val ordered = applyLocalOrder(list)
            // 预加载歌曲数与封面
            val counts = mutableMapOf<Long, Int>()
            val covers = mutableMapOf<Long, String?>()
            ordered.forEach { pl ->
                counts[pl.id] = collectionDao.getPlaylistSongCount(pl.id)
                covers[pl.id] = collectionDao.getPlaylistFirstCover(pl.id)
            }
            withContext(Dispatchers.Main) {
                userPlaylists = ordered
                playlistSongCounts.clear()
                playlistSongCounts.putAll(counts)
                playlistCovers.clear()
                playlistCovers.putAll(covers)
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
            val toId = to.key as? Long ?: return@rememberReorderableLazyListState
            val fromIdx = userPlaylists.indexOfFirst { it.id == fromId }
            val toIdx = userPlaylists.indexOfFirst { it.id == toId }
            if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                val newList = userPlaylists.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
                userPlaylists = newList
                saveLocalOrder(newList)
            }
        }
    )

    val allSelected = userPlaylists.isNotEmpty() && selectedIds.size == userPlaylists.size

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部操作与状态栏
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
                    // 全选复选框 + 文案
                    Surface(
                        onClick = {
                            selectedIds = if (allSelected) emptySet()
                            else userPlaylists.map { it.id }.toSet()
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
                                text = if (selectedIds.isEmpty()) "全选" else "已选 ${selectedIds.size} 项",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // 新建歌单快捷按钮
                    TextButton(
                        onClick = {
                            createInputText = ""
                            showCreateDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("新建歌单")
                    }

                    Spacer(Modifier.width(4.dp))

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
                    text = "共 ${userPlaylists.size} 个自建歌单 · 长按右侧把手拖拽排序",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (userPlaylists.isEmpty()) {
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
                            "暂无自建歌单",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "点击右上角「新建歌单」快速创建",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(userPlaylists.size, key = { userPlaylists[it].id }) { idx ->
                        val pl = userPlaylists[idx]
                        val isSelected = pl.id in selectedIds
                        val songCount = playlistSongCounts[pl.id] ?: 0
                        val coverUrl = playlistCovers[pl.id]

                        ReorderableItem(
                            state = reorderableState,
                            key = pl.id
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
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                onClick = {
                                    selectedIds = if (isSelected) selectedIds - pl.id
                                    else selectedIds + pl.id
                                }
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
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    // 封面图或图标
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!coverUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = coverUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.LibraryMusic,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    // 歌单名与统计
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pl.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "$songCount 首歌曲",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // 重命名按钮
                                    IconButton(
                                        onClick = {
                                            renameTargetPlaylist = pl
                                            renameInputText = pl.name
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DriveFileRenameOutline,
                                            contentDescription = "重命名歌单",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

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

        // 底部悬浮批量删除操作栏（选中时弹出）
        AnimatedVisibility(
            visible = selectedIds.isNotEmpty(),
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
                        text = "已选中 ${selectedIds.size} 个歌单",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(onClick = { selectedIds = emptySet() }) {
                        Text("取消选择")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("删除 (${selectedIds.size})")
                    }
                }
            }
        }
    }

    // 重命名歌单对话框
    if (renameTargetPlaylist != null) {
        val target = renameTargetPlaylist!!
        AlertDialog(
            onDismissRequest = { renameTargetPlaylist = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("重命名歌单") },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    placeholder = { Text("输入新的歌单名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameInputText.trim()
                        if (newName.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                collectionDao.renamePlaylist(target.id, newName)
                                val updated = userPlaylists.map {
                                    if (it.id == target.id) it.copy(name = newName) else it
                                }
                                withContext(Dispatchers.Main) {
                                    userPlaylists = updated
                                    renameTargetPlaylist = null
                                    Toast.makeText(context, "歌单名称已更新", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = renameInputText.isNotBlank() && renameInputText.trim() != target.name
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetPlaylist = null }) { Text("取消") }
            }
        )
    }

    // 新建歌单对话框
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = createInputText,
                    onValueChange = { createInputText = it },
                    placeholder = { Text("输入歌单名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = createInputText.trim()
                        if (name.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                val newId = collectionDao.createPlaylist(UserPlaylistEntity(name = name))
                                val newEntity = UserPlaylistEntity(id = newId, name = name)
                                val newList = userPlaylists + newEntity
                                saveLocalOrder(newList)
                                withContext(Dispatchers.Main) {
                                    userPlaylists = newList
                                    playlistSongCounts[newId] = 0
                                    playlistCovers[newId] = null
                                    showCreateDialog = false
                                    createInputText = ""
                                    Toast.makeText(context, "歌单已创建", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = createInputText.isNotBlank()
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            }
        )
    }

    // 批量删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("确认删除") },
            text = { Text("确定删除选中的 ${selectedIds.size} 个歌单？删除后歌单内的歌曲关联将一并移除，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = selectedIds.toList()
                    scope.launch(Dispatchers.IO) {
                        toDelete.forEach { id ->
                            collectionDao.deletePlaylistWithSongs(id)
                        }
                        val remaining = userPlaylists.filter { it.id !in selectedIds }
                        saveLocalOrder(remaining)
                        withContext(Dispatchers.Main) {
                            userPlaylists = remaining
                            selectedIds = emptySet()
                            showDeleteConfirm = false
                            Toast.makeText(context, "已删除 ${toDelete.size} 个歌单", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

