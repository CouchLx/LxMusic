package com.example.lxmusic.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.util.SongDurationCache
import com.example.lxmusic.util.formatDuration
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSheet(
    songs: List<SongInfo>,
    currentIndex: Int,
    onSongClick: (Int) -> Unit,
    onDismiss: () -> Unit,
    onReorder: (from: Int, to: Int) -> Unit = { _, _ -> },
    onRemoveSong: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val playingPath = songs.getOrNull(currentIndex)?.filePath
    val context = LocalContext.current
    android.util.Log.d("LxMusic", "PlaylistSheet: songs.size=${songs.size}, currentIndex=$currentIndex, playingPath=$playingPath")

    // 初始化时立即应用缓存（去重防止重复 filePath 导致 key 冲突崩溃）
    val list = remember(songs) {
        val items = songs.distinctBy { it.filePath }.map { song ->
            if (song.duration <= 0 && song.filePath.contains("|")) {
                val hash = song.filePath.split("|")[0]
                SongDurationCache.get(hash)?.let { song.copy(duration = it) } ?: song
            } else song
        }
        mutableStateListOf(*items.toTypedArray())
    }
    LaunchedEffect(songs) {
        val newPaths = songs.distinctBy { it.filePath }.map { it.filePath }
        val oldPaths = list.map { it.filePath }
        if (oldPaths != newPaths) {
            list.clear()
            list.addAll(songs.distinctBy { it.filePath }.map { song ->
                if (song.duration <= 0 && song.filePath.contains("|")) {
                    val hash = song.filePath.split("|")[0]
                    SongDurationCache.get(hash)?.let { song.copy(duration = it) } ?: song
                } else song
            })
        }
    }

    val listState = rememberLazyListState()
    var deleteMode by remember { mutableStateOf(false) }

    // 使用 reorderable 库
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromIdx = list.indexOfFirst { it.filePath == from.key }
            val toIdx = list.indexOfFirst { it.filePath == to.key }
            if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                val item = list.removeAt(fromIdx)
                list.add(toIdx, item)
                // 通知外部同步更新 ExoPlayer
                onReorder(fromIdx, toIdx)
            }
        }
    )
    val isReordering by remember { derivedStateOf { reorderableState.isAnyItemDragging } }

    // 定位到正在播放
    LaunchedEffect(Unit) {
        val idx = list.indexOfFirst { it.filePath == playingPath }
        if (idx >= 0) listState.scrollToItem(idx)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {}
    ) {
        BackHandler(onBack = {
            if (deleteMode) deleteMode = false else onDismiss()
        })

        Column(
            Modifier.fillMaxWidth()
        ) {
            // 顶栏
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("播放列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${list.size} 首", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    // 固定宽度容器，避免切换时布局跳动
                    Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                        if (deleteMode) {
                            Text(
                                "完成",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.clickable { deleteMode = false }
                            )
                        } else {
                            IconButton(onClick = { deleteMode = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, "删除", Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // 列表
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f),
                contentPadding = PaddingValues(bottom = 40.dp, top = 4.dp)
            ) {
                items(
                    count = list.size,
                    key = { idx ->
                        try { list[idx].filePath }
                        catch (e: Exception) {
                            android.util.Log.e("LxMusic", "PlaylistSheet key error: idx=$idx, list.size=${list.size}", e)
                            "error_$idx"
                        }
                    }
                ) { idx ->
                    val song = try { list[idx] }
                    catch (e: Exception) {
                        android.util.Log.e("LxMusic", "PlaylistSheet item error: idx=$idx, list.size=${list.size}", e)
                        return@items
                    }
                    val isPlaying = song.filePath == playingPath

                    ReorderableItem(
                        state = reorderableState,
                        key = song.filePath,
                        enabled = !deleteMode,
                        animateItemModifier = Modifier.animateItem(
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    ) { isDragging ->
                        val scale by animateFloatAsState(
                            targetValue = if (isDragging) 1.02f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "scaleAnimation"
                        )
                        val itemElevation by animateDpAsState(
                            targetValue = if (isDragging) 6.dp else 1.dp,
                            label = "elevationAnimation"
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .then(
                                    if (deleteMode) Modifier.clickable {
                                        onRemoveSong(song.filePath)
                                        list.remove(song)
                                    }
                                    else Modifier.longPressDraggableHandle(
                                        onDragStarted = {
                                            context.performHapticFeedback(HapticFeedbackEffect.Heavy)
                                        },
                                        onDragStopped = {
                                            context.performHapticFeedback(HapticFeedbackEffect.Heavy)
                                        }
                                    )
                                )
                                .then(
                                    if (!deleteMode && !isReordering) Modifier.clickable {
                                        val orig = songs.indexOfFirst { it.filePath == song.filePath }
                                        if (orig >= 0) onSongClick(orig)
                                    } else Modifier
                                ),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer
                                   else MaterialTheme.colorScheme.surfaceContainerLowest,
                            tonalElevation = itemElevation
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            // 封面
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))) {
                                AlbumCover(filePath = song.filePath, albumArtUri = song.albumArtUri)
                            }
                            Spacer(Modifier.width(10.dp))
                            // 歌曲信息
                            Column(Modifier.weight(1f)) {
                                Text(song.title, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (isPlaying) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface)
                                Text(song.artist, style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // 时长 / 删除模式显示 ❌
                            if (deleteMode) {
                                Icon(
                                    Icons.Default.Clear, "删除",
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(formatDuration(song.duration), style = MaterialTheme.typography.bodySmall,
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
