package com.example.lxmusic.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.lxmusic.ui.components.SongContextMenuActions
import com.example.lxmusic.model.SongInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListPage(
    title: String,
    songs: List<SongInfo>,
    onBack: () -> Unit,
    onPlaySong: (List<SongInfo>, Int) -> Unit,
    currentPlayingPath: String? = null,
    isPlaying: Boolean = false,
    onLocateReady: (() -> Unit) -> Unit = {},
    onAddToQueueNext: (SongInfo) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()

    // 定位到当前播放歌曲
    fun locateToCurrentSong() {
        val targetIndex = songs.indexOfFirst { it.filePath == currentPlayingPath }
        if (targetIndex >= 0) {
            scope.launch { listState.animateScrollToItem(targetIndex) }
        }
    }
    LaunchedEffect(Unit) { onLocateReady(::locateToCurrentSong) }

    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }
    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 400),
        label = "contentAlpha"
    )
    val contentOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (contentVisible) 0f else with(LocalDensity.current) { 30.dp.toPx() },
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 400),
        label = "contentOffsetY"
    )

    Box(modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
            alpha = contentAlpha
            translationY = contentOffsetY
        }
    ) {
        if (songs.isEmpty()) {
            Text(
                text = "暂无歌曲",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val coverUrl = songs.firstOrNull()?.albumArtUri ?: ""
                        val cardSize = 220.dp
                        if (coverUrl.isNotBlank()) {
                            val painter = rememberAsyncImagePainter(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(coverUrl)
                                    .memoryCacheKey(coverUrl)
                                    .crossfade(150)
                                    .build()
                            )
                            Image(
                                painter = painter,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(cardSize)
                                    .clip(RoundedCornerShape(24.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(cardSize)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MusicNote, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "共 ${songs.size} 首",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { if (songs.isNotEmpty()) onPlaySong(songs, 0) },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("播放全部")
                        }
                    }
                }
                itemsIndexed(songs, key = { _, song -> song.filePath }) { index, song ->
                    var showSheet by remember { mutableStateOf(false) }

                    RankSongCard(
                        rank = index + 1,
                        song = song,
                        showRank = false,
                        isCurrentSong = song.filePath == currentPlayingPath,
                        isPlaying = isPlaying && song.filePath == currentPlayingPath,
                        onClick = { onPlaySong(songs, index) },
                        onMenuClick = { showSheet = true }
                    )

                    if (showSheet) {
                        ModalBottomSheet(
                            onDismissRequest = { showSheet = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val coverUrl = song.albumArtUri
                                if (!coverUrl.isNullOrBlank()) {
                                    val painter = rememberAsyncImagePainter(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(coverUrl).memoryCacheKey(coverUrl).crossfade(150).build()
                                    )
                                    Image(painter, null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                } else {
                                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.MusicNote, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                            SongContextMenuActions(song = song, onDismiss = { showSheet = false }, onAddToQueueNext = { onAddToQueueNext(song) })
                        }
                    }
                }
            }
        }
    }
}
