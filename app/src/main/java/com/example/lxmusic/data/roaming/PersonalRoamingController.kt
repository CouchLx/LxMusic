package com.example.lxmusic.data.roaming

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.lxmusic.NeteaseApi
import com.example.lxmusic.PlayerViewModel
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.toSongInfo
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 私人漫游的一个可选模式/场景。
 *
 * 模式码与 api-enhanced 的 `/personal/fm/mode` 完全一致：
 * DEFAULT / FAMILIAR / EXPLORE / PUZZLE_MODE_RCMD，场景统一走 SCENE_RCMD + submode。
 */
data class RoamingChoice(
    val code: String,
    val title: String,
    val subtitle: String = "",
    val scene: Boolean = false
) {
    val key: String get() = if (scene) "scene:$code" else code
    val mode: String get() = if (scene) "SCENE_RCMD" else code
    val subMode: String get() = if (scene) code else ""
}

data class RoamingCatalog(
    val modes: List<RoamingChoice>,
    val scenes: List<RoamingChoice>
) {
    val choices: List<RoamingChoice> get() = modes + scenes

    companion object {
        val fallback = RoamingCatalog(
            modes = listOf(
                RoamingChoice("DEFAULT", "默认模式", "沿着目前的喜好继续聆听"),
                RoamingChoice("FAMILIAR", "熟悉模式", "喜欢过的歌曲与相似推荐"),
                RoamingChoice("EXPLORE", "探索模式", "偏好曲风与潜力好歌"),
                RoamingChoice("PUZZLE_MODE_RCMD", "拼图模式", "边听歌边拼封面")
            ),
            // 场景码全部来自网易云 /personal/fm/mode 文档（共 42 个），一个不少
            scenes = listOf(
                "EXERCISE" to "运动", "FOCUS" to "专注", "NIGHT_EMO" to "伤感", "SLEEP_HELP" to "助眠",
                "RELAX" to "放松", "COMMUTE" to "出行", "CHEERFUL" to "欢快", "LYRICAL" to "抒情",
                "CURE" to "治愈", "SWEET" to "情歌", "RHYTHM_BLUES" to "R&B", "RAINY" to "雨天",
                "GAMES" to "游戏", "RAP" to "说唱", "K_POP" to "K-Pop", "ORIGINAL_MUSICAL" to "宝藏原创",
                "ELECTRONIC" to "电音", "TAKE_SHOWER" to "洗澡", "COFFEE_SHOP" to "咖啡馆", "ROCK" to "摇滚",
                "INSPIRATIONAL" to "励志", "CHINESE" to "华语", "ENGLISH" to "欧美", "YUEYU" to "粤语",
                "MANYAO" to "慢摇DJ", "JINGDIAN" to "经典", "LIGHT" to "轻音乐", "GUOFENG" to "国风",
                "FOLK" to "民谣", "ACG" to "二次元", "GUDIAN" to "古典", "JAZZ" to "爵士",
                "JAPANESE" to "日语", "GLOBAL" to "全球", "FRANCH" to "法语", "BLUE" to "蓝调",
                "DANCE" to "舞蹈", "LATIN" to "拉丁", "PUNK" to "放克", "COUNTRY" to "乡村乐",
                "MUSICAL" to "音乐剧", "YINGSHI" to "影视"
            ).map { (code, title) -> RoamingChoice(code, title, scene = true) }
        )
    }
}

data class RoamingSettings(
    val preload: Boolean = true,
    val restoreQueue: Boolean = true,
    val selection: String = "DEFAULT"
)

data class RoamingState(
    val catalog: RoamingCatalog = RoamingCatalog.fallback,
    val choice: RoamingChoice = RoamingCatalog.fallback.modes.first(),
    val settings: RoamingSettings = RoamingSettings(),
    val active: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val currentCover: String = "",
    val currentSongId: Long = 0
)

/**
 * 私人漫游引擎（移植自 CurrentMusicX 的 PersonalRoamingController）。
 *
 * - 开始：拉一批 → 接管主队列；记录原队列以便结束恢复
 * - 预加载：队列剩 2 首时自动补货（去重，最多记 200 首），失败 4s 后重试一次
 * - 结束：按设置恢复原队列与进度，保持暂停
 * - 状态持久化：active + 模式 + 原队列落盘，进程重启后继续漫游、也能正确恢复原队列
 */
class PersonalRoamingController(
    context: Context,
    private val player: PlayerViewModel,
    private val scope: CoroutineScope
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("roaming", Context.MODE_PRIVATE)

    val state = MutableStateFlow(loadState())

    private var job: Job? = null
    private var refillJob: Job? = null
    private var refillRetryJob: Job? = null
    private val seen = LinkedHashSet<String>()
    private var activeChoice: RoamingChoice? = null

    private var originalQueue: List<SongInfo> = emptyList()
    private var originalIndex: Int = 0
    private var originalPosition: Long = 0L

    init {
        // 进程被杀 / 重启后队列本身会恢复（PlayerViewModel 还原 queue），但漫游状态是内存态的：
        // 之前会变成「有歌在放但不在漫游、也不补货」。这里把 active + 模式 + 原队列一起恢复。
        if (prefs.getBoolean(KEY_ACTIVE, false)) {
            val selection = prefs.getString("selection", "DEFAULT").orEmpty()
            val choice = RoamingCatalog.fallback.choices.find { it.key == selection }
            if (choice != null) {
                activeChoice = choice
                originalQueue = runCatching {
                    prefs.getString(KEY_ORIG_QUEUE, null)
                        ?.let { Gson().fromJson(it, Array<SongInfo>::class.java)?.toList() }
                }.getOrNull().orEmpty()
                originalIndex = prefs.getInt(KEY_ORIG_INDEX, 0)
                originalPosition = prefs.getLong(KEY_ORIG_POSITION, 0L)
                state.update { it.copy(active = true, choice = choice) }
                Log.d("LxMusicRoaming", "恢复漫游：${choice.title}，原队列 ${originalQueue.size} 首")
            }
        }
        scope.launch {
            player.uiState.collect { s ->
                if (!state.value.active) return@collect
                val song = s.currentSong
                // 恢复出来的漫游态校验：当前歌不是网易云歌了（用户手动切了别的）→ 退出漫游态
                if (song != null && !NeteaseApi.isNeteasePath(song.filePath)) {
                    Log.d("LxMusicRoaming", "当前歌不在漫游队列（${song.filePath}），退出漫游态")
                    stop(restore = false)
                    return@collect
                }
                if (song != null) {
                    state.update {
                        it.copy(
                            currentCover = song.albumArtUri.orEmpty(),
                            currentSongId = song.filePath.hashCode().toLong()
                        )
                    }
                }
                val remaining = s.queue.size - s.currentIndex - 1
                if (remaining <= 2 && state.value.settings.preload) refill()
            }
        }
    }

    private fun loadState(): RoamingState {
        val catalog = RoamingCatalog.fallback
        val selection = prefs.getString("selection", "DEFAULT").orEmpty()
        val choice = catalog.choices.find { it.key == selection } ?: catalog.modes.first()
        val settings = RoamingSettings(
            preload = prefs.getBoolean("preload", true),
            restoreQueue = prefs.getBoolean("restore_queue", true),
            selection = selection
        )
        return RoamingState(catalog = catalog, choice = choice, settings = settings)
    }

    fun updateSettings(value: RoamingSettings) {
        state.update { it.copy(settings = value) }
        prefs.edit()
            .putBoolean("preload", value.preload)
            .putBoolean("restore_queue", value.restoreQueue)
            .putString("selection", value.selection)
            .apply()
    }

    fun select(choice: RoamingChoice) {
        state.update { it.copy(choice = choice) }
    }

    fun start(choice: RoamingChoice = state.value.choice, onStarted: (() -> Unit)? = null) {
        // 已经是这个模式在漫游：不重复拉歌（点一下就是「继续听」）
        if (state.value.active && activeChoice?.key == choice.key && !state.value.loading) {
            onStarted?.invoke()
            return
        }
        if (state.value.loading) return
        job?.cancel()
        job = scope.launch {
            state.update { it.copy(loading = true, error = null) }
            try {
                val songs = fetch(choice)
                if (songs.isEmpty()) {
                    state.update {
                        it.copy(loading = false, error = "这个模式暂时没有可播放的推荐，换一个试试")
                    }
                    return@launch
                }
                if (!state.value.active) {
                    val s = player.uiState.value
                    originalQueue = s.queue
                    originalIndex = s.currentIndex.coerceAtLeast(0)
                    originalPosition = player.progress.value.positionMs
                }
                activeChoice = choice
                seen.clear()
                songs.forEach { seen.add(it.filePath) }
                player.playOnline(songs, 0)
                state.update {
                    it.copy(
                        loading = false,
                        active = true,
                        choice = choice,
                        error = null,
                        settings = it.settings.copy(selection = choice.key)
                    )
                }
                persistSelection(choice.key)
                persistActive(choice)
                val first = songs.first()
                Log.d(
                    "LxMusicRoaming",
                    "开始漫游：${choice.title}，${songs.size} 首，首曲=${first.title}/${first.artist} " +
                        "cover=${first.albumArtUri.orEmpty().take(48)}"
                )
                onStarted?.invoke()
            } catch (e: Exception) {
                Log.e("LxMusicRoaming", "开始漫游失败", e)
                state.update { it.copy(loading = false, error = e.message ?: "获取漫游推荐失败") }
            }
        }
    }

    fun retry() {
        start(state.value.choice)
    }

    /** 结束漫游：按设置恢复原队列（保持暂停），否则保留当前队列继续播 */
    fun stop(restore: Boolean = state.value.settings.restoreQueue) {
        job?.cancel(); job = null
        refillJob?.cancel(); refillJob = null
        refillRetryJob?.cancel(); refillRetryJob = null
        activeChoice = null
        seen.clear()
        state.update { it.copy(active = false, loading = false, error = null) }
        if (restore && originalQueue.isNotEmpty()) {
            player.restoreQueue(originalQueue, originalIndex, originalPosition)
            Log.d("LxMusicRoaming", "结束漫游，恢复原队列")
        }
        originalQueue = emptyList()
        prefs.edit().putBoolean(KEY_ACTIVE, false).remove(KEY_ORIG_QUEUE).apply()
    }

    private fun refill() {
        val choice = activeChoice ?: return
        if (refillJob?.isActive == true) return
        refillJob = scope.launch {
            try {
                val songs = fetch(choice)
                if (songs.isEmpty()) {
                    state.update { it.copy(error = "暂时没有新的推荐，可重试或切换模式") }
                    scheduleRefillRetry()
                    return@launch
                }
                seen.addAll(songs.map { it.filePath })
                while (seen.size > 200) seen.remove(seen.first())
                player.appendToQueue(songs)
                Log.d("LxMusicRoaming", "补充推荐 ${songs.size} 首")
            } catch (e: Exception) {
                Log.e("LxMusicRoaming", "补充推荐失败", e)
                state.update { it.copy(error = e.message ?: "补充推荐失败") }
                scheduleRefillRetry()
            }
        }
    }

    /** 补货失败后重试一次：否则队列放到最后一首就停住（外面看就是「异常暂停 / 切不了下一首」） */
    private fun scheduleRefillRetry() {
        if (!state.value.active) return
        if (refillRetryJob?.isActive == true) return
        refillRetryJob = scope.launch {
            delay(4000)
            if (!state.value.active) return@launch
            val s = player.uiState.value
            val remaining = s.queue.size - s.currentIndex - 1
            if (remaining <= 2) refill()
        }
    }

    private suspend fun fetch(choice: RoamingChoice): List<SongInfo> {
        val resp = NeteaseApi.service.personalFmMode(
            mode = choice.mode,
            subMode = choice.subMode.ifBlank { null }
        )
        if (resp.code != 200) throw IllegalStateException("接口返回 ${resp.code}")
        val parsed = resp.songs()
        val mapped = parsed.map { it.toSongInfo() }
        val fresh = mapped.filter { it.filePath !in seen }.distinctBy { it.filePath }
        Log.d(
            "LxMusicRoaming",
            "拉取 ${choice.title}：原始 ${parsed.size} 首，去重后 ${fresh.size} 首" +
                (fresh.firstOrNull()?.let { "，首曲=${it.title}/${it.artist} cover=${it.albumArtUri.orEmpty().take(48)}" } ?: "")
        )
        return fresh
    }

    private fun persistSelection(key: String) {
        prefs.edit().putString("selection", key).apply()
    }

    /** 把漫游态（模式 + 原队列）落盘，进程重启后能接着漫游、也能正确恢复原队列 */
    private fun persistActive(choice: RoamingChoice) {
        runCatching {
            prefs.edit()
                .putBoolean(KEY_ACTIVE, true)
                .putString("selection", choice.key)
                .putString(KEY_ORIG_QUEUE, Gson().toJson(originalQueue))
                .putInt(KEY_ORIG_INDEX, originalIndex)
                .putLong(KEY_ORIG_POSITION, originalPosition)
                .apply()
        }
    }

    private companion object {
        const val KEY_ACTIVE = "active"
        const val KEY_ORIG_QUEUE = "orig_queue"
        const val KEY_ORIG_INDEX = "orig_index"
        const val KEY_ORIG_POSITION = "orig_position"
    }
}
