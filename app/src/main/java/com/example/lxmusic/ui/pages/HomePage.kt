@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.lxmusic.ui.pages

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.LocalImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.lxmusic.DailyRecommendSong
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.RankItem
import com.example.lxmusic.RankSong
import com.example.lxmusic.ui.components.SongContextMenuActions
import com.example.lxmusic.TopCardSong
import com.example.lxmusic.model.SongInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

// 首页各区块翻页状态（内存级，退出应用后重置）
private val homePagerStates = mutableMapOf<String, Int>()

fun clearHomePagerStates() {
    homePagerStates.clear()
}

/**
 * 首页全部区块数据的单一不可变状态：
 * 刷新时所有请求并行，完成后一次性替换整个对象（而非 13 个独立状态逐个更新），
 * 避免多次重组与"逐区块变新"的割裂感；也便于缓存一次性恢复。
 */
data class HomeFeedState(
    val dailySongs: List<DailyRecommendSong> = emptyList(),
    val vipSongs: List<SongInfo> = emptyList(),
    val historySongs: List<DailyRecommendSong> = emptyList(),
    val styleSongs: List<DailyRecommendSong> = emptyList(),
    val surgeSongs: List<SongInfo> = emptyList(),
    val hotPickSongs: List<SongInfo> = emptyList(),
    val newSongs: List<SongInfo> = emptyList(),
    val collectSongs: List<SongInfo> = emptyList(),
    val nicheSongs: List<SongInfo> = emptyList(),
    val personalSongs: List<SongInfo> = emptyList(),
    val conceptSongs: List<SongInfo> = emptyList(),
    val trendSongs: List<SongInfo> = emptyList(),
)

/**
 * 预算时间内持续重试的接口请求：网络抖动/超时自动重试直到成功或预算耗尽。
 * 正常网络下一次成功（刷新很快）；抖动时在 [budgetMs] 内反复尝试（每次
 * [attemptTimeoutMs] 超时），大幅降低"某区块加载失败"概率；预算耗尽仍失败
 * 返回 null（调用方保留旧数据，保证区块不空白）。
 * 预算/超时给足：单次超时 5s（服务器慢也能等到），预算 12s（约 2-3 次机会）。
 */
private suspend fun <T> fetchWithinBudget(
    budgetMs: Long = 12_000,
    attemptTimeoutMs: Long = 5_000,
    request: suspend () -> T?
): T? {
    val deadline = System.currentTimeMillis() + budgetMs
    while (System.currentTimeMillis() < deadline) {
        try {
            val result = withTimeoutOrNull(attemptTimeoutMs) { request() }
            if (result != null) return result
        } catch (_: Exception) {
            // 网络异常：继续重试
        }
        delay(200)
    }
    return null
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomePage(
    onPlaySong: (List<SongInfo>, Int) -> Unit,
    onRankClick: (RankItem) -> Unit,
    onDailyClick: (List<DailyRecommendSong>) -> Unit,
    onVipClick: (List<SongInfo>) -> Unit = {},
    onHistoryClick: (List<DailyRecommendSong>) -> Unit = {},
    onStyleClick: (List<DailyRecommendSong>) -> Unit = {},
    currentPlayingPath: String? = null,
    isPlaying: Boolean = false,
    onAllSongsReady: ((List<SongInfo>) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    onClickRefresh: (((() -> Unit)) -> Unit)? = null,
    onRefreshStateChange: ((Boolean) -> Unit)? = null,
    onAddToQueueNext: (SongInfo) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }
    val prefs = remember { context.getSharedPreferences("rank_cache", Context.MODE_PRIVATE) }
    val homePrefs = remember { context.getSharedPreferences("home_cache", Context.MODE_PRIVATE) }
    val authPrefs = remember { context.getSharedPreferences("auth", Context.MODE_PRIVATE) }
    val imageLoader = LocalImageLoader.current

    // ===== 数据状态（收敛为单一不可变对象：刷新完成后一次性替换，
    // 避免 13 个独立状态逐个更新导致多次重组与"逐个变新"的割裂感） =====
    var feed by remember { mutableStateOf(HomeFeedState()) }

    // ===== UI 状态 =====
    var isRefreshing by remember { mutableStateOf(false) }
    var isRefreshEnabled by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    val isLoggedIn = authPrefs.getString("token", null) != null

    // 刷新世代号：刷新时 +1，作为歌曲区块分页器的 key —— 强制 Pager 重建，
    // 从（已被清空的）homePagerStates 读 initialPage=0，实现"刷新后翻页进度重置"。
    // 切到其他页面再回来时世代号不变，Pager 重建后从 map 恢复上次进度。
    var refreshGeneration by remember { mutableIntStateOf(0) }

    // 判断是否有任何数据
    val hasAnyData by remember {
        derivedStateOf {
            feed.surgeSongs.isNotEmpty() || feed.hotPickSongs.isNotEmpty() || feed.newSongs.isNotEmpty() ||
            feed.collectSongs.isNotEmpty() || feed.nicheSongs.isNotEmpty() || feed.personalSongs.isNotEmpty() ||
            feed.conceptSongs.isNotEmpty() || feed.trendSongs.isNotEmpty() ||
            feed.dailySongs.isNotEmpty() || feed.vipSongs.isNotEmpty()
        }
    }

    // 辅助函数：排行榜歌曲转 SongInfo
    fun mapRankSongs(list: List<RankSong>?) = list?.take(28)?.map { song ->
        SongInfo(title = song.title, artist = song.artist, filePath = "${song.hash}|${song.album_audio_id}", albumArtUri = song.coverUrl, duration = song.durationMs)
    } ?: emptyList()

    // 辅助函数：推荐歌曲转 SongInfo
    fun mapTopCardSongs(list: List<TopCardSong>?) = list?.take(28)?.map { song ->
        SongInfo(title = song.title, artist = song.artist, filePath = "${song.hash}|${song.album_audio_id}", albumArtUri = song.coverUrl, duration = song.durationMs)
    } ?: emptyList()

    // 骨架屏卡片
    @Composable
    fun SongSkeletonCard() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                )
            }
        }
    }

    // 歌曲区块组件
    @Composable
    fun SongSection(
        title: String,
        sectionKey: String,
        refreshGeneration: Int,
        songs: List<SongInfo>,
        onSongClick: (Int) -> Unit,
        currentPlayingPath: String? = null,
        isPlaying: Boolean = false
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
            )
            if (songs.isEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    repeat(4) { SongSkeletonCard() }
                }
            } else {
                val pageSize = 4
                val pages = songs.chunked(pageSize)
                // 翻页进度存全局 map（homePagerStates）：
                // - 切到其他页面再回来：从 map 恢复上次翻页进度（Compose 的
                //   rememberSaveable 依赖 SaveableStateHolder，切 Tab 回来时常失效；
                //   map 是进程级，HomePage 组合销毁重建后依然可读）
                // - 刷新时 clearHomePagerStates() 清空 map + key(refreshGeneration)
                //   强制重建 Pager → 自动重置回第 1 页
                // - 退出应用后内存 map 清空 → 重置（符合"每次打开默认第一页"）
                key(refreshGeneration) {
                    val savedPage = (homePagerStates[sectionKey] ?: 0)
                        .coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    val pagerState = rememberPagerState(initialPage = savedPage) { pages.size }
                    LaunchedEffect(pagerState.currentPage) {
                        homePagerStates[sectionKey] = pagerState.currentPage
                    }

                    // 原生 HorizontalPager 翻页（平滑跟手 + 自带吸附），外层方向锁定：
                    // 上下滑动浏览页面时容易误触横向翻页（手指带一点水平分量就会翻页）。
                    // 水平位移先超过 touchSlop 且大于垂直位移才放行给 Pager；
                    // 垂直占优则直接结束手势，交给外层 LazyColumn 垂直滚动。
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(pages.size) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var horizontalLocked = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (change.changedToUp()) {
                                            change.consume()
                                            break
                                        }
                                        if (!horizontalLocked) {
                                            val dx = change.position.x - down.position.x
                                            val dy = change.position.y - down.position.y
                                            val slop = viewConfiguration.touchSlop
                                            if (abs(dx) > slop || abs(dy) > slop) {
                                                if (abs(dx) > abs(dy)) {
                                                    horizontalLocked = true
                                                } else {
                                                    // 垂直滑动：放行给滚动容器，结束本手势
                                                    break
                                            }
                                        }
                                    }
                                    if (horizontalLocked) {
                                        change.consume()
                                    }
                                }
                            }
                        }
                ) { pageIndex ->
                    val pageSongs = pages[pageIndex]
                    Column {
                        pageSongs.forEachIndexed { indexInPage, song ->
                            val globalIndex = pageIndex * pageSize + indexInPage
                            key(song.filePath ?: "song_${pageIndex}_$indexInPage") {
                                var showSheet by remember { mutableStateOf(false) }

                                RankSongCard(
                                    rank = globalIndex + 1,
                                    song = song,
                                    showRank = false,
                                    isCurrentSong = song.filePath == currentPlayingPath,
                                    isPlaying = isPlaying && song.filePath == currentPlayingPath,
                                    onClick = { onSongClick(globalIndex) },
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
                                        .data(coverUrl)
                                        .memoryCacheKey(coverUrl)
                                        .crossfade(150)
                                        .size(200)
                                        .build()
                                )
                                                Image(painter, null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                            } else {
                                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.MusicNote, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

                if (pages.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(pages.size) { index ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(
                                        width = if (pagerState.currentPage == index) 14.dp else 5.dp,
                                        height = 5.dp
                                    )
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (pagerState.currentPage == index)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                    )
                            )
                        }
                    }
                }
                }
            }
        }
    }

    // 统一的刷新逻辑
    fun performRefresh(isAuto: Boolean = false, isClick: Boolean = false) {
        if (!isLoggedIn) {
            if (!isAuto) {
                Toast.makeText(context, "请先登录后刷新", Toast.LENGTH_SHORT).show()
            }
            isRefreshing = false
            isLoading = false
            return
        }

        // 防止连点
        if (!isRefreshEnabled) return
        isRefreshEnabled = false

        // 刷新不清空数据：全屏转圈期间内容被隐藏（contentAlpha=0），
        // 各请求成功才替换为新数据；失败区块保留刷新前的旧内容，
        // 保证"不丢失内容、不空白"且成功的区块都是全新的。
        // 翻页进度：清空全局 map + 世代号 +1（强制 Pager 重建）→ 全部回到第 1 页
        clearHomePagerStates()
        refreshGeneration++

        isRefreshing = true
        isLoading = true

        val ts = System.currentTimeMillis()
        val token = authPrefs.getString("token", "") ?: ""
        val userid = authPrefs.getLong("userid", 0)

        scope.launch(Dispatchers.IO) {
            supervisorScope {
                // ===== 首批：顶部推荐栏 + 前两个内容区块（5 个请求全并行，预算内持续重试）=====
                // 旧实现分两批串行 awaitAll（最多 6s 转圈）；合并并行后
                // 转圈时长 = 最慢单个请求（正常时 ~1-2s），更快显示新内容。
                val firstJobs = listOf(
                    async {
                        val list = fetchWithinBudget {
                            KuGouApi.service.getDailyRecommend(token, userid, timestamp = ts).data?.list
                        }
                        if (list != null) {
                            feed = feed.copy(
                                dailySongs = list,
                                historySongs = list
                            )
                        }
                    },
                    async {
                        val list = fetchWithinBudget {
                            KuGouApi.service.getTopCardYouth(3006, timestamp = ts).data?.list
                        }
                        if (list != null) {
                            feed = feed.copy(
                                vipSongs = list.map { song -> SongInfo(title = song.title, artist = song.artist, filePath = "${song.hash}|${song.album_audio_id}", albumArtUri = song.coverUrl, duration = song.durationMs) }
                            )
                        }
                    },
                    async {
                        val list = fetchWithinBudget {
                            KuGouApi.service.getStyleRecommend(token, userid, timestamp = ts).data?.list
                        }
                        if (list != null) feed = feed.copy(styleSongs = list)
                    },
                    async {
                        val list = fetchWithinBudget {
                            KuGouApi.service.getTopCardYouth(3001, timestamp = ts).data?.list
                        }
                        if (list != null) feed = feed.copy(personalSongs = mapTopCardSongs(list))
                    },
                    async {
                        val list = fetchWithinBudget {
                            KuGouApi.service.getTopCard(3, timestamp = ts).data?.list
                        }
                        if (list != null) feed = feed.copy(collectSongs = list.take(28).map { song -> SongInfo(title = song.title, artist = song.artist, filePath = "${song.hash}|${song.album_audio_id}", albumArtUri = song.coverUrl, duration = song.durationMs) })
                    }
                )
                firstJobs.awaitAll()

                // 首批完成后，结束刷新状态，让用户看到内容
                isRefreshing = false
                isLoading = false
                isRefreshEnabled = true

                // ===== 第二批：剩余区块（后台静默加载，成功后合并进 feed）=====
                launch {
                    supervisorScope {
                        val remainingJobs = listOf(
                            async {
                                val list = fetchWithinBudget {
                                    KuGouApi.service.getTopCard(1, timestamp = ts).data?.list
                                }
                                if (list != null) feed = feed.copy(surgeSongs = list.take(28).map { song -> SongInfo(title = song.title, artist = song.artist, filePath = "${song.hash}|${song.album_audio_id}", albumArtUri = song.coverUrl, duration = song.durationMs) })
                            },
                            async {
                                val list = fetchWithinBudget {
                                    KuGouApi.service.getTopCard(2, timestamp = ts).data?.list
                                }
                                if (list != null) feed = feed.copy(newSongs = list.take(28).map { song -> SongInfo(title = song.title, artist = song.artist, filePath = "${song.hash}|${song.album_audio_id}", albumArtUri = song.coverUrl, duration = song.durationMs) })
                            },
                            async {
                                val list = fetchWithinBudget {
                                    KuGouApi.service.getTopCard(4, timestamp = ts).data?.list
                                }
                                if (list != null) feed = feed.copy(nicheSongs = list.take(28).map { song -> SongInfo(title = song.title, artist = song.artist, filePath = "${song.hash}|${song.album_audio_id}", albumArtUri = song.coverUrl, duration = song.durationMs) })
                            },
                            async {
                                val list = fetchWithinBudget {
                                    KuGouApi.service.getTopCardYouth(3005, timestamp = ts).data?.list
                                }
                                if (list != null) {
                                    val mapped = mapTopCardSongs(list)
                                    feed = feed.copy(
                                        hotPickSongs = mapped,
                                        trendSongs = mapped
                                    )
                                }
                            },
                            async {
                                val list = fetchWithinBudget {
                                    KuGouApi.service.getTopCardYouth(3101, timestamp = ts).data?.list
                                }
                                if (list != null) feed = feed.copy(conceptSongs = mapTopCardSongs(list))
                            }
                        )
                        remainingJobs.awaitAll()

                        // 批量写入缓存（一次性提交）
                        homePrefs.edit().apply {
                            putString("daily_songs_json", gson.toJson(feed.dailySongs))
                            putString("history_songs_json", gson.toJson(feed.historySongs))
                            putString("vip_songs_json", gson.toJson(feed.vipSongs))
                            putString("style_songs_json", gson.toJson(feed.styleSongs))
                            putString("home_surge", gson.toJson(feed.surgeSongs))
                            putString("home_hot", gson.toJson(feed.hotPickSongs))
                            putString("home_new", gson.toJson(feed.newSongs))
                            putString("home_collect", gson.toJson(feed.collectSongs))
                            putString("home_niche", gson.toJson(feed.nicheSongs))
                            putString("home_personal", gson.toJson(feed.personalSongs))
                            putString("home_concept", gson.toJson(feed.conceptSongs))
                            putString("home_trend", gson.toJson(feed.trendSongs))
                            putLong("last_refresh_time", System.currentTimeMillis())
                            apply()
                        }
                    }
                }
            }
        }
    }

    // 通知父组件刷新状态变化
    LaunchedEffect(isRefreshing) {
        onRefreshStateChange?.invoke(isRefreshing)
    }

    // 向父组件报告所有歌曲（用于一键播放）
    LaunchedEffect(feed) {
        val all = (feed.personalSongs + feed.collectSongs + feed.surgeSongs + feed.hotPickSongs +
            feed.newSongs + feed.nicheSongs + feed.conceptSongs + feed.trendSongs)
            .distinctBy { it.filePath }
        if (all.isNotEmpty()) onAllSongsReady?.invoke(all)
    }

    // 首次加载：如果有缓存先显示缓存（一次性恢复 feed），不再自动刷新
    LaunchedEffect(Unit) {
        val cachedDaily = homePrefs.getString("daily_songs_json", null)
        val cachedHistory = homePrefs.getString("history_songs_json", null)
        val cachedVip = homePrefs.getString("vip_songs_json", null)
        val cachedStyle = homePrefs.getString("style_songs_json", null)
        val cachedSurge = homePrefs.getString("home_surge", null)
        val cachedHot = homePrefs.getString("home_hot", null)
        val cachedNew = homePrefs.getString("home_new", null)
        val cachedCollect = homePrefs.getString("home_collect", null)
        val cachedNiche = homePrefs.getString("home_niche", null)
        val cachedPersonal = homePrefs.getString("home_personal", null)
        val cachedConcept = homePrefs.getString("home_concept", null)
        val cachedTrend = homePrefs.getString("home_trend", null)

        val typeDaily = object : TypeToken<List<DailyRecommendSong>>() {}.type
        val typeSongInfo = object : TypeToken<List<SongInfo>>() {}.type

        feed = HomeFeedState(
            dailySongs = cachedDaily?.let { gson.fromJson(it, typeDaily) } ?: emptyList(),
            historySongs = cachedHistory?.let { gson.fromJson(it, typeDaily) } ?: emptyList(),
            vipSongs = cachedVip?.let { gson.fromJson(it, typeSongInfo) } ?: emptyList(),
            styleSongs = cachedStyle?.let { gson.fromJson(it, typeDaily) } ?: emptyList(),
            surgeSongs = cachedSurge?.let { gson.fromJson(it, typeSongInfo) } ?: emptyList(),
            hotPickSongs = cachedHot?.let { gson.fromJson(it, typeSongInfo) } ?: emptyList(),
            newSongs = cachedNew?.let { gson.fromJson(it, typeSongInfo) } ?: emptyList(),
            collectSongs = cachedCollect?.let { gson.fromJson(it, typeSongInfo) } ?: emptyList(),
            nicheSongs = cachedNiche?.let { gson.fromJson(it, typeSongInfo) } ?: emptyList(),
            personalSongs = cachedPersonal?.let { gson.fromJson(it, typeSongInfo) } ?: emptyList(),
            conceptSongs = cachedConcept?.let { gson.fromJson(it, typeSongInfo) } ?: emptyList(),
            trendSongs = cachedTrend?.let { gson.fromJson(it, typeSongInfo) } ?: emptyList()
        )
    }

    // 暴露刷新方法给父组件
    LaunchedEffect(onClickRefresh) {
        onClickRefresh?.invoke { performRefresh(isAuto = false, isClick = true) }
    }

    // 每日推荐等列表转 SongInfo 列表（供一键播放使用）
    val dailySongInfos = remember(feed.dailySongs) {
        feed.dailySongs.map { song ->
            SongInfo(
                title = song.title,
                artist = song.artist,
                filePath = "${song.hash}|${song.album_audio_id}",
                albumArtUri = song.coverUrl,
                duration = song.durationMs
            )
        }
    }
    val historySongInfos = remember(feed.historySongs) {
        feed.historySongs.map { song ->
            SongInfo(
                title = song.title,
                artist = song.artist,
                filePath = "${song.hash}|${song.album_audio_id}",
                albumArtUri = song.coverUrl,
                duration = song.durationMs
            )
        }
    }
    val styleSongInfos = remember(feed.styleSongs) {
        feed.styleSongs.map { song ->
            SongInfo(
                title = song.title,
                artist = song.artist,
                filePath = "${song.hash}|${song.album_audio_id}",
                albumArtUri = song.coverUrl,
                duration = song.durationMs
            )
        }
    }

    // 推荐卡片数据模型
    data class RecommendCardData(
        val id: String,
        val enTitle: String,
        val zhTag: String,
        val subtitle: String,
        val coverUrl: String,
        val gradientColors: List<Color>,
        val songs: List<SongInfo>,
        val onClick: () -> Unit,
        val onPlay: () -> Unit
    )

    val recommendCards = listOf(
        RecommendCardData(
            id = "daily",
            enTitle = "Daily\n30",
            zhTag = "每日30首",
            subtitle = if (feed.dailySongs.isNotEmpty()) {
                val first = feed.dailySongs.first()
                "${first.title} - ${first.artist}"
            } else if (isLoggedIn) {
                "加载中..."
            } else {
                "登录后查看"
            },
            coverUrl = feed.dailySongs.firstOrNull()?.coverUrl ?: "",
            gradientColors = listOf(Color(0xFFF28F9E), Color(0xFFEA7889)),
            songs = dailySongInfos,
            onClick = { if (feed.dailySongs.isNotEmpty()) onDailyClick(feed.dailySongs) },
            onPlay = { if (dailySongInfos.isNotEmpty()) onPlaySong(dailySongInfos, 0) }
        ),
        RecommendCardData(
            id = "vip",
            enTitle = "Fav\nRadar",
            zhTag = "雷达模式",
            subtitle = if (feed.vipSongs.isNotEmpty()) {
                val first = feed.vipSongs.first()
                "${first.title} - ${first.artist}"
            } else {
                "加载中..."
            },
            coverUrl = feed.vipSongs.firstOrNull()?.albumArtUri ?: "",
            gradientColors = listOf(Color(0xFFE59C63), Color(0xFFD6884A)),
            songs = feed.vipSongs,
            onClick = { if (feed.vipSongs.isNotEmpty()) onVipClick(feed.vipSongs) },
            onPlay = { if (feed.vipSongs.isNotEmpty()) onPlaySong(feed.vipSongs, 0) }
        ),
        RecommendCardData(
            id = "style",
            enTitle = "Style\nMix",
            zhTag = "风格推荐",
            subtitle = if (feed.styleSongs.isNotEmpty()) {
                val first = feed.styleSongs.first()
                "${first.title} - ${first.artist}"
            } else {
                "加载中..."
            },
            coverUrl = feed.styleSongs.firstOrNull()?.coverUrl ?: "",
            gradientColors = listOf(Color(0xFF7E97E8), Color(0xFF6B86E0)),
            songs = styleSongInfos,
            onClick = { if (feed.styleSongs.isNotEmpty()) onStyleClick(feed.styleSongs) },
            onPlay = { if (styleSongInfos.isNotEmpty()) onPlaySong(styleSongInfos, 0) }
        ),
        RecommendCardData(
            id = "history",
            enTitle = "Memory\nTracks",
            zhTag = "历史推荐",
            subtitle = if (feed.historySongs.isNotEmpty()) {
                val first = feed.historySongs.first()
                "${first.title} - ${first.artist}"
            } else {
                "加载中..."
            },
            coverUrl = feed.historySongs.firstOrNull()?.coverUrl ?: "",
            gradientColors = listOf(Color(0xFF5AB398), Color(0xFF48A185)),
            songs = historySongInfos,
            onClick = { if (feed.historySongs.isNotEmpty()) onHistoryClick(feed.historySongs) },
            onPlay = { if (historySongInfos.isNotEmpty()) onPlaySong(historySongInfos, 0) }
        )
    )

    // 内容可见性动画状态 - 刷新时隐藏，完成后显示
    val contentAlpha by animateFloatAsState(
        targetValue = if (isRefreshing) 0f else 1f,
        animationSpec = tween(durationMillis = 250, easing = FastOutLinearInEasing),
        label = "contentAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (isRefreshing) {
            CircularWavyProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .alpha(contentAlpha),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
            // ===== 顶部横向推荐栏（吸附对齐固定显示完整卡片） =====
            item {
                val recommendListState = rememberLazyListState()
                val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = recommendListState)

                LazyRow(
                    state = recommendListState,
                    flingBehavior = snapFlingBehavior,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
                ) {
                    items(
                        count = recommendCards.size,
                        key = { recommendCards[it].id }
                    ) { index ->
                        val card = recommendCards[index]
                        val isThisCardPlaying = isPlaying && card.songs.any { it.filePath == currentPlayingPath }

                        Box(
                            modifier = Modifier
                                .width(168.dp)
                                .height(168.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Brush.linearGradient(card.gradientColors))
                                .clickable { card.onClick() }
                                .padding(14.dp)
                        ) {
                            // 顶部：英文大标题与封面图
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopStart),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = card.enTitle,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    lineHeight = 24.sp,
                                    letterSpacing = (-0.3).sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (card.coverUrl.isNotBlank()) {
                                        val painter = rememberAsyncImagePainter(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(card.coverUrl)
                                                .memoryCacheKey(card.coverUrl)
                                                .crossfade(150)
                                                .size(240)
                                                .build()
                                        )
                                        Image(
                                            painter = painter,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }

                            // 底部：中文标签 + 首曲副标题 + 播放按钮
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomStart),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = card.zhTag,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        ),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = card.subtitle,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp
                                        ),
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // 播放按钮
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.25f))
                                        .clickable {
                                            if (card.songs.isNotEmpty()) {
                                                card.onPlay()
                                            } else {
                                                card.onClick()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isThisCardPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "播放",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ===== 私人专属好歌 =====
            item {
                SongSection(
                    title = "私人专属好歌", sectionKey = "section_personal", refreshGeneration = refreshGeneration, songs = feed.personalSongs,
                    onSongClick = { index -> onPlaySong(feed.personalSongs, index) },
                    currentPlayingPath = currentPlayingPath, isPlaying = isPlaying
                )
            }

            // ===== 热门好歌精选 =====
            item {
                SongSection(
                    title = "热门好歌精选", sectionKey = "section_collect", refreshGeneration = refreshGeneration, songs = feed.collectSongs,
                    onSongClick = { index -> onPlaySong(feed.collectSongs, index) },
                    currentPlayingPath = currentPlayingPath, isPlaying = isPlaying
                )
            }

            // ===== 精选好歌 =====
            item {
                SongSection(
                    title = "精选好歌", sectionKey = "section_surge", refreshGeneration = refreshGeneration, songs = feed.surgeSongs,
                    onSongClick = { index -> onPlaySong(feed.surgeSongs, index) },
                    currentPlayingPath = currentPlayingPath, isPlaying = isPlaying
                )
            }

            // ===== 热门精选 =====
            item {
                SongSection(
                    title = "热门精选", sectionKey = "section_hotpick", refreshGeneration = refreshGeneration, songs = feed.hotPickSongs,
                    onSongClick = { index -> onPlaySong(feed.hotPickSongs, index) },
                    currentPlayingPath = currentPlayingPath, isPlaying = isPlaying
                )
            }

            // ===== 经典怀旧金曲 =====
            item {
                SongSection(
                    title = "经典怀旧金曲", sectionKey = "section_new", refreshGeneration = refreshGeneration, songs = feed.newSongs,
                    onSongClick = { index -> onPlaySong(feed.newSongs, index) },
                    currentPlayingPath = currentPlayingPath, isPlaying = isPlaying
                )
            }

            // ===== 小众宝藏佳作 =====
            item {
                SongSection(
                    title = "小众宝藏佳作", sectionKey = "section_niche", refreshGeneration = refreshGeneration, songs = feed.nicheSongs,
                    onSongClick = { index -> onPlaySong(feed.nicheSongs, index) },
                    currentPlayingPath = currentPlayingPath, isPlaying = isPlaying
                )
            }

            // ===== 概念er新推 =====
            item {
                SongSection(
                    title = "概念er新推", sectionKey = "section_concept", refreshGeneration = refreshGeneration, songs = feed.conceptSongs,
                    onSongClick = { index -> onPlaySong(feed.conceptSongs, index) },
                    currentPlayingPath = currentPlayingPath, isPlaying = isPlaying
                )
            }

            // ===== 潮流尝鲜 =====
            item {
                SongSection(
                    title = "潮流尝鲜", sectionKey = "section_trend", refreshGeneration = refreshGeneration, songs = feed.trendSongs,
                    onSongClick = { index -> onPlaySong(feed.trendSongs, index) },
                    currentPlayingPath = currentPlayingPath, isPlaying = isPlaying
                )
            }
        }
    }
}
