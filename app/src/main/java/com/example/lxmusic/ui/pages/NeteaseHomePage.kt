package com.example.lxmusic.ui.pages

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lxmusic.NeteaseApi
import com.example.lxmusic.NeteaseAuth
import com.example.lxmusic.NeteaseSong
import com.example.lxmusic.data.roaming.PersonalRoamingController
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.ui.components.daily.DailyHeroSong
import com.example.lxmusic.ui.components.daily.DailyRecommendHero
import com.example.lxmusic.ui.roaming.PersonalRoamingSheet
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 网易云首页。
 *
 * 目前只承载「每日推荐」封面流，其余区域留白；数据来自 `/recommend/songs`（需登录），
 * 按天缓存到 prefs，冷启动先出缓存再后台刷新。
 */
@Composable
fun NeteaseHomePage(
    topContentPadding: Dp = 0.dp,
    onPlaySong: (List<SongInfo>, Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenSongs: (List<SongInfo>) -> Unit,
    roamingController: PersonalRoamingController? = null,
    currentPlayingPath: String? = null,
    isPlaying: Boolean = false,
    loginVersion: Int = 0,
    hazeState: HazeState? = null,
    onLoginClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("netease_home", android.content.Context.MODE_PRIVATE) }
    val gson = remember { Gson() }

    // 登录态：loginVersion 变化（登录/退出）时重新读取
    val account = remember(loginVersion) { NeteaseAuth.load(context) }

    var songs by remember { mutableStateOf(loadCachedSongs(prefs, gson)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var showRoaming by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(account.loggedIn, reloadKey) {
        if (!account.loggedIn) {
            songs = emptyList()
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            val resp = NeteaseApi.service.recommendSongs()
            val list = resp.data?.dailySongs.orEmpty()
            if (list.isNotEmpty()) {
                songs = list.map { it.toHeroSong() }
                saveCachedSongs(prefs, gson, list)
            } else if (resp.code != 200) {
                error = "接口返回 ${resp.code}"
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    val songInfos = remember(songs) {
        songs.map { hero ->
            SongInfo(
                title = hero.title,
                artist = hero.artist,
                filePath = "netease://${hero.id}",
                albumArtUri = hero.coverUrl,
                duration = hero.durationMs
            )
        }
    }

    val listState = rememberLazyListState()
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(400),
        label = "neteaseHomeAlpha"
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .alphaCompat(contentAlpha)
                .then(
                    if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f)
                    else Modifier
                ),
            contentPadding = PaddingValues(top = topContentPadding, bottom = 180.dp)
        ) {
            item(key = "netease_daily_hero") {
                DailyRecommendHero(
                    songs = songs,
                    loading = loading,
                    isLoggedIn = account.loggedIn,
                    isPlaying = isPlaying,
                    currentPlayingPath = currentPlayingPath,
                    onPlay = { index ->
                        // 同一首歌：直接播/暂停切换；否则从该首起播
                        if (currentPlayingPath == "netease://${songs[index].id}") {
                            onTogglePlayPause()
                        } else {
                            onPlaySong(songInfos, index)
                        }
                    },
                    onOpen = { if (songInfos.isNotEmpty()) onOpenSongs(songInfos) },
                    onRetry = { reloadKey++ },
                    onLogin = onLoginClick,
                    dateLabel = remember { SimpleDateFormat("M月d日", Locale.CHINA).format(Date()) },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            // 私人漫游入口（参考项目里就放在每日推荐下面）
            if (roamingController != null) {
                item(key = "netease_roaming_entry") {
                    val roaming by roamingController.state.collectAsState()
                    RoamingEntry(
                        active = roaming.active,
                        isPlaying = roaming.active && isPlaying,
                        title = if (roaming.active) "漫游中 · ${roaming.choice.title}" else "私人漫游",
                        subtitle = if (roaming.active) "点开查看模式或拼图，结束漫游会恢复原队列"
                        else "按你的喜好，连续听点好音乐",
                        onClick = { showRoaming = true },
                        onPlayClick = {
                            when {
                                // 漫游中：这个键就变成播放/暂停开关
                                roaming.active -> onTogglePlayPause()
                                // 没登录就别直接开始，先引导登录
                                !account.loggedIn -> onLoginClick()
                                // 未开始：一键用默认模式起播，不打开漫游页
                                else -> roamingController.start(roaming.catalog.modes.first())
                            }
                        }
                    )
                }
            }
        }
    }

    if (showRoaming && roamingController != null) {
        PersonalRoamingSheet(
            controller = roamingController,
            isLoggedIn = account.loggedIn,
            onDismiss = { showRoaming = false },
            onLogin = {
                showRoaming = false
                onLoginClick()
            }
        )
    }
}

/** 首页「私人漫游」入口行：整行点开漫游页，右侧按钮直接起播/暂停 */
@Composable
private fun RoamingEntry(
    active: Boolean,
    isPlaying: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val accent = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Headphones,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = accent
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        // 独立按钮：未漫游时一键用默认模式起播；漫游中就是播放/暂停
        FilledTonalIconButton(onClick = onPlayClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = when {
                    isPlaying -> "暂停漫游"
                    active -> "继续漫游"
                    else -> "一键开始私人漫游"
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun Modifier.alphaCompat(alpha: Float) = graphicsLayer { this.alpha = alpha }

private fun NeteaseSong.toHeroSong() = DailyHeroSong(
    id = id,
    title = name ?: "未知歌曲",
    artist = artist,
    coverUrl = coverUrl,
    durationMs = dt
)

private fun loadCachedSongs(
    prefs: android.content.SharedPreferences,
    gson: Gson
): List<DailyHeroSong> {
    val raw = prefs.getString(KEY_DAILY_JSON, null) ?: return emptyList()
    val date = prefs.getString(KEY_DAILY_DATE, null)
    if (date != today()) return emptyList()
    return runCatching {
        val type = object : TypeToken<List<NeteaseSong>>() {}.type
        val list: List<NeteaseSong>? = gson.fromJson(raw, type)
        list.orEmpty().map { it.toHeroSong() }
    }.getOrDefault(emptyList())
}

private fun saveCachedSongs(
    prefs: android.content.SharedPreferences,
    gson: Gson,
    songs: List<NeteaseSong>
) {
    runCatching {
        prefs.edit()
            .putString(KEY_DAILY_JSON, gson.toJson(songs))
            .putString(KEY_DAILY_DATE, today())
            .apply()
    }
}

private fun today(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

private const val KEY_DAILY_JSON = "daily_songs_json"
private const val KEY_DAILY_DATE = "daily_songs_date"
