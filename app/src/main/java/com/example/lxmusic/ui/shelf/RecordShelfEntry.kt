package com.example.lxmusic.ui.shelf

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.lxmusic.model.SongInfo

/**
 * 任意歌单/榜单/本地列表页共用的「CD 唱片架」入口。
 *
 * 按钮 + 全屏 Dialog 一起封装：Dialog 用 `usePlatformDefaultWidth = false`，
 * 才能盖住 App 的悬浮顶栏，唱片架自己的顶部搜索/标题/返回不会被压住。
 */
@Composable
fun RecordShelfEntry(
    songs: List<SongInfo>,
    onPlaySong: (List<SongInfo>, Int) -> Unit,
    currentPlayingPath: String?,
    // 当前是否在播（页面已有的状态；试听的暂停/恢复走 ShelfPlaybackBridge，这里仅语义标注）
    isPlaying: Boolean = false,
    onToggleLike: ((SongInfo) -> Unit)? = null,
    isLiked: (SongInfo) -> Boolean = { false },
    collectionKey: String = "",
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    var showShelf by rememberSaveable { mutableStateOf(false) }

    if (compact) {
        IconButton(
            onClick = { if (songs.isNotEmpty()) showShelf = true },
            modifier = modifier.size(40.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors()
        ) {
            Icon(Icons.Default.Album, contentDescription = "CD 唱片架", Modifier.size(20.dp))
        }
    } else {
        OutlinedButton(
            onClick = { if (songs.isNotEmpty()) showShelf = true },
            shape = RoundedCornerShape(24.dp),
            modifier = modifier
        ) {
            Icon(Icons.Default.Album, null, Modifier.size(18.dp))
            Text("CD 唱片架", fontSize = 14.sp)
        }
    }

    if (showShelf && songs.isNotEmpty()) {
        RecordShelfDialog(
            songs = songs,
            onPlaySong = onPlaySong,
            currentPlayingPath = currentPlayingPath,
            onDismiss = { showShelf = false },
            onToggleLike = onToggleLike,
            isLiked = isLiked,
            collectionKey = collectionKey
        )
    }
}

/**
 * 全屏唱片架对话框本体（不带入口按钮）。
 * 播放列表面板等自带头部按钮的地方直接复用这个。
 */
@Composable
fun RecordShelfDialog(
    songs: List<SongInfo>,
    onPlaySong: (List<SongInfo>, Int) -> Unit,
    currentPlayingPath: String?,
    onDismiss: () -> Unit,
    onToggleLike: ((SongInfo) -> Unit)? = null,
    isLiked: (SongInfo) -> Boolean = { false },
    collectionKey: String = "",
    onCommit: ((index: Int, startPositionMs: Long) -> Unit)? = null
) {
    if (songs.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        PlaylistRecordShelf(
            songs = songs,
            canPlay = true,
            onPlay = { index -> onPlaySong(songs, index) },
            onClose = onDismiss,
            onToggleLike = onToggleLike,
            isLiked = isLiked,
            currentPlayingPath = currentPlayingPath,
            collectionKey = collectionKey,
            onCommit = onCommit
        )
    }
}
