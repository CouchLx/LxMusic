package com.example.lxmusic.ui.pages

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.RankItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

private fun formatPlayTimes(times: Long): String {
    return when {
        times >= 100_000_000 -> String.format(Locale.US, "%.1f亿", times / 100_000_000.0)
        times >= 10_000 -> String.format(Locale.US, "%.1f万", times / 10_000.0)
        else -> times.toString()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DiscoverPage(onRankClick: (RankItem) -> Unit, listState: LazyListState = rememberLazyListState()) {
    val context = LocalContext.current
    var rankList by remember { mutableStateOf<List<RankItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val gson = remember { com.google.gson.Gson() }
    val prefs = remember { context.getSharedPreferences("rank_cache", android.content.Context.MODE_PRIVATE) }

    val allowedRankIds = setOf(
        8888L,   // TOP500
        6666L,   // 飙升榜
        74534L,  // 新歌榜
        82831L,  // 网络热歌榜
        52144L,  // 抖音热歌酷狗榜
        24971L,  // DJ热歌榜
        59895L,  // R&B榜
        33160L,  // 电音榜
        59900L,  // 纯音乐榜
        85432L,  // 百万收藏榜
        35811L,  // 会员热歌榜
        33162L,  // ACG新歌榜
        49223L,  // 90后热歌榜
        49224L   // 00后热歌榜
    )

    val imageLoader = coil.compose.LocalImageLoader.current

    LaunchedEffect(Unit) {
        val cached = prefs.getString("rank_list_json", null)
        if (!cached.isNullOrBlank()) {
            try {
                val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, RankItem::class.java).type
                val list: List<RankItem> = gson.fromJson(cached, type)
                if (list.isNotEmpty()) {
                    rankList = list
                    isLoading = false
                    list.forEach { rank ->
                        val url = rank.coverUrl
                        if (url.isNotBlank()) {
                            val request = ImageRequest.Builder(context)
                                .data(url)
                                .memoryCacheKey(url)
                                .build()
                            imageLoader.enqueue(request)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        scope.launch(Dispatchers.IO) {
            try {
                val resp = KuGouApi.service.getRankList()
                if (resp.data?.info != null) {
                    val filtered = resp.data.info.filter { it.rankid in allowedRankIds }
                    rankList = filtered
                    prefs.edit().putString("rank_list_json", gson.toJson(filtered)).apply()
                }
                loadError = false
            } catch (_: Exception) {
                if (rankList.isEmpty()) loadError = true
            } finally {
                isLoading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularWavyProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (loadError && rankList.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("加载失败，请检查网络", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    isLoading = true
                    loadError = false
                    scope.launch(Dispatchers.IO) {
                        try {
                            val resp = KuGouApi.service.getRankList()
                            if (resp.data?.info != null) {
                                rankList = resp.data.info.filter { it.rankid in allowedRankIds }
                                prefs.edit().putString("rank_list_json", gson.toJson(rankList)).apply()
                            }
                        } catch (_: Exception) {
                            loadError = true
                        } finally {
                            isLoading = false
                        }
                    }
                }) { Text("重试") }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    count = rankList.size,
                    key = { rankList[it].rankid }
                ) { index ->
                    RankCard(
                        rank = rankList[index],
                        onClick = { onRankClick(rankList[index]) }
                    )
                }
            }
        }
    }
}

@Composable
fun RankCard(rank: RankItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val rankColors = listOf(
        Color(0xFFFF4444),
        Color(0xFFFF8800),
        Color(0xFFFFCC00)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 标题行
            Text(
                text = rank.rankname ?: "排行榜",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 内容区：封面 + 歌曲列表
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 左侧封面图
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    val imgUrl = rank.coverUrl
                    if (imgUrl.isNotBlank()) {
                        val painter = rememberAsyncImagePainter(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imgUrl)
                                .memoryCacheKey(imgUrl)
                                .crossfade(150)
                                .build()
                        )
                        Image(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 底部播放量遮罩
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.6f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatPlayTimes(rank.play_times),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "播放",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // 右侧歌曲列表
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    val previewSongs = rank.songinfo?.take(3) ?: emptyList()
                    if (previewSongs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "暂无歌曲信息",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        previewSongs.forEachIndexed { i, song ->
                            val songName = song.songname ?: song.name ?: ""
                            val artist = song.author ?: ""
                            val display = if (artist.isNotBlank()) "$songName - $artist" else songName

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${i + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = rankColors.getOrElse(i) { MaterialTheme.colorScheme.onSurface },
                                    modifier = Modifier.width(16.dp)
                                )
                                Text(
                                    text = display,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
