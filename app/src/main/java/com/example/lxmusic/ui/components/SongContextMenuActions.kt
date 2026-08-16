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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.lxmusic.MusicDatabase
import com.example.lxmusic.PlaylistSongCrossRef
import com.example.lxmusic.UserPlaylistEntity
import com.example.lxmusic.model.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SongContextMenuActions(
    song: SongInfo,
    onDismiss: () -> Unit,
    onAddToQueueNext: (() -> Unit)? = null,
    showCollectionActions: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MusicDatabase.getDatabase(context) }
    val collectionDao = remember { db.collectionDao() }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var userPlaylists by remember { mutableStateOf<List<UserPlaylistEntity>>(emptyList()) }

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

    // 当前歌曲是否已收藏
    var isCollected by remember { mutableStateOf(false) }
    LaunchedEffect(song.filePath) {
        val fp = getFilePath() ?: return@LaunchedEffect
        isCollected = collectionDao.isSongCollected(fp)
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

    // 收藏 / 添加歌单（本地音乐不需要时通过 showCollectionActions 关闭）
    if (showCollectionActions) {
    // 收藏 / 取消收藏
    Surface(
        onClick = {
            scope.launch(Dispatchers.IO) {
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

    // 添加到我的歌单
    Surface(
        onClick = {
            showPlaylistPicker = !showPlaylistPicker
            if (showPlaylistPicker) {
                scope.launch(Dispatchers.IO) {
                    userPlaylists = collectionDao.getAllUserPlaylists()
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
            Text("添加到我的歌单", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(
                if (showPlaylistPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 歌单选择器
    AnimatedVisibility(visible = showPlaylistPicker, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            if (userPlaylists.isEmpty()) {
                Text(
                    "暂无歌单，请在收藏页面创建",
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
                                val entity = saveSongEntity() ?: return@launch
                                if (!collectionDao.isSongInPlaylist(pl.id, entity.filePath)) {
                                    collectionDao.addSongToPlaylist(PlaylistSongCrossRef(
                                        playlistId = pl.id, songFilePath = entity.filePath,
                                        title = entity.title, artist = entity.artist,
                                        albumArtUri = entity.albumArtUri, duration = entity.duration,
                                        hash = entity.hash, audioId = entity.audioId,
                                        albumId = entity.albumId, mixsongid = entity.mixsongid
                                    ))
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
        }
    }
    }
    Spacer(modifier = Modifier.height(12.dp))
}
