package com.example.lxmusic.ui.components.bottombar

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.lxmusic.PlayerProgress
import com.example.lxmusic.model.SongInfo
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

@Composable
fun FloatingGlassPlayerCapsule(
    song: SongInfo,
    isPlaying: Boolean,
    progress: StateFlow<PlayerProgress> = MutableStateFlow(PlayerProgress()),
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    isBlurEnabled: Boolean = true,
    navBarOpacity: Float = 1f
) {
    val progressState by progress.collectAsStateWithLifecycle()
    val positionMs = progressState.positionMs
    val durationMs = progressState.durationMs
    val progressFraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val isDarkTheme = isSystemInDarkTheme()
    val containerColor = if (isDarkTheme) {
        Color(0xFF1E1E1E).copy(alpha = 0.40f * navBarOpacity.coerceIn(0.1f, 1f))
    } else {
        Color.White.copy(alpha = 0.35f * navBarOpacity.coerceIn(0.1f, 1f))
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val fallbackColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = (0.94f * navBarOpacity).coerceIn(0.1f, 1f))

    // 封面旋转动画
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, song.filePath) {
        if (isPlaying) {
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(18000, easing = LinearEasing)
                )
                rotation.snapTo(0f)
            }
        }
    }

    val albumModel: Any? = remember(song.albumArtUri, song.filePath) {
        when {
            song.albumArtUri != null && (song.albumArtUri.startsWith("/") || song.albumArtUri.startsWith("file://")) ->
                File(song.albumArtUri.removePrefix("file://"))
            song.albumArtUri != null -> Uri.parse(song.albumArtUri)
            else -> File(song.filePath)
        }
    }

    val glassModifier = if (isBlurEnabled && backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                vibrancy()
                blur(4.dp.toPx())
                lens(24.dp.toPx(), 24.dp.toPx())
            },
            highlight = {
                Highlight.Default.copy(alpha = 1f)
            },
            shadow = {
                Shadow.Default.copy(
                    color = Color.Black.copy(if (isDarkTheme) 0.12f else 0.06f)
                )
            },
            onDrawSurface = {
                drawRect(containerColor)
                drawRect(primaryColor.copy(alpha = if (isDarkTheme) 0.10f else 0.08f))
            }
        )
    } else {
        Modifier.background(fallbackColor, CircleShape)
    }

    Box(
        modifier = modifier
            .height(64.dp)
            .clip(CircleShape)
            .then(glassModifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 专辑封面 + 环形进度圈
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                // 环形播放进度条
                CircularProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    strokeWidth = 2.dp,
                    strokeCap = StrokeCap.Round
                )

                // 旋转圆形封面
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .graphicsLayer { rotationZ = rotation.value }
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (albumModel != null) {
                        AsyncImage(
                            model = albumModel,
                            contentDescription = song.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 播放/暂停 快捷按钮
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPlayPause
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
