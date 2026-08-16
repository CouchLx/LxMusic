@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.example.lxmusic.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.UserPlaylistItem
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.ui.components.PlaylistCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PlaylistPage(
    onBack: () -> Unit,
    onPlaySong: (List<SongInfo>, Int) -> Unit,
    onPlaylistClick: (UserPlaylistItem) -> Unit = {},
    selectedPlaylist: UserPlaylistItem? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authPrefs = remember { context.getSharedPreferences("auth", android.content.Context.MODE_PRIVATE) }
    val token = authPrefs.getString("token", "") ?: ""
    val userid = authPrefs.getLong("userid", 0)

    var playlists by remember { mutableStateOf<List<UserPlaylistItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("LxMusic", "获取歌单: token=$token, userid=$userid")
                val resp = KuGouApi.service.getUserPlaylist(token, userid)
                android.util.Log.d("LxMusic", "歌单响应: status=${resp.status}, data=${resp.data}, listSize=${resp.data?.list?.size}, total=${resp.data?.total}")
                if (resp.status == 1 && resp.data?.list != null) {
                    playlists = resp.data.list
                } else {
                    android.util.Log.e("LxMusic", "歌单获取失败: status=${resp.status}")
                    withContext(Dispatchers.Main) {
                        errorMessage = "获取歌单失败"
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LxMusic", "歌单请求异常", e)
                withContext(Dispatchers.Main) {
                    errorMessage = "网络错误: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    // 如果选中了歌单，显示歌单详情
    if (selectedPlaylist != null) {
        PlaylistDetailPage(
            playlist = selectedPlaylist,
            onBack = { onBack() },
            onPlaySong = onPlaySong,
            currentPlayingPath = null,
            isPlaying = false
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的歌单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                CircularWavyProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error
                )
            } else if (playlists.isEmpty()) {
                Text(
                    text = "暂无歌单",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        count = playlists.size,
                        key = { playlists[it].listid }
                    ) { index ->
                        val playlist = playlists[index]
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }
                }
            }
        }
    }
}
