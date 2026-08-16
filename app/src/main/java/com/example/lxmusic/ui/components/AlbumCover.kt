package com.example.lxmusic.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import java.io.File

@Composable
fun AlbumCover(
    filePath: String,
    albumArtUri: String? = null,
    size: Dp = 44.dp  // 默认尺寸，可根据悬浮底栏状态调整
) {
    // 优先用文件路径（最快），其次 content:// URI，最后回退到音频文件路径
    val model: Any = when {
        albumArtUri != null && (albumArtUri.startsWith("/") || albumArtUri.startsWith("file://")) ->
            File(albumArtUri.removePrefix("file://"))
        albumArtUri != null -> Uri.parse(albumArtUri)
        else -> File(filePath)
    }
    val painter = rememberAsyncImagePainter(model = model)

    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 底层：音乐图标
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 上层：异步封面
            Image(
                painter = painter,
                contentDescription = "专辑封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun AlbumCoverLarge(filePath: String, albumArtUri: String? = null) {
    val model: Any = when {
        albumArtUri != null && (albumArtUri.startsWith("/") || albumArtUri.startsWith("file://")) ->
            File(albumArtUri.removePrefix("file://"))
        albumArtUri != null -> Uri.parse(albumArtUri)
        else -> File(filePath)
    }
    val painter = rememberAsyncImagePainter(model = model)

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Image(
                painter = painter,
                contentDescription = "专辑封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
