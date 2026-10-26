@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.lxmusic

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.util.SongDurationCache
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class PlayerProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isWaiting: Boolean = false
)

data class PlayerUiState(
    val queue: List<SongInfo> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val playMode: Int = 0,
    val currentPlayingPath: String? = null,
    val restored: Boolean = false
) {
    val currentSong: SongInfo? get() = queue.getOrNull(currentIndex)
}

/**
 * 声音设置页状态：音量 / 变速变调 / 跳过静音。
 * fixedPitch=true 表示音调跟随速度（pitch 滑杆禁用），与 BoomingMusic 语义一致。
 */
data class SoundUiState(
    val volume: Float = 1f,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val fixedPitch: Boolean = true,
    val skipSilence: Boolean = false
) {
    /** 界面显示的音调值：锁定跟随速度时直接显示速度 */
    val displayPitch: Float get() = if (fixedPitch) speed else pitch
}

const val MIN_SOUND_SPEED = 0.5f
const val MAX_SOUND_SPEED = 2.0f
const val KEY_SOUND_VOLUME = "sound_volume"
const val KEY_SOUND_SPEED = "sound_speed"
const val KEY_SOUND_PITCH = "sound_pitch"
const val KEY_SOUND_FIXED_PITCH = "sound_fixed_pitch"
const val KEY_SOUND_SKIP_SILENCE = "sound_skip_silence"
const val ACTION_SET_SKIP_SILENCE = "com.example.lxmusic.action.SET_SKIP_SILENCE"

/**
 * 播放器状态管理：负责 MediaController 连接、播放队列、播放状态与进度。
 * 从 MainActivity 中抽出，使进度变化只重组订阅 progress 的组件，避免整页重组。
 */
@OptIn(UnstableApi::class)
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val playbackPrefs = app.getSharedPreferences("playback", Context.MODE_PRIVATE)
    private val settingsPrefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        PlayerUiState(playMode = settingsPrefs.getInt("play_mode", 0))
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _progress = MutableStateFlow(PlayerProgress())
    val progress: StateFlow<PlayerProgress> = _progress.asStateFlow()

    // 当前音频格式（采样率/位深/编码，来自 PlayerService 的 ExoPlayer；无信息时为 null）
    private val _audioFormat = MutableStateFlow<androidx.media3.common.Format?>(null)
    val audioFormat: StateFlow<androidx.media3.common.Format?> = _audioFormat.asStateFlow()

    // 声音设置（音量/变速变调/跳过静音）：PlayerService 启动时按同一份 prefs 恢复
    private val _soundState = MutableStateFlow(
        SoundUiState(
            volume = playbackPrefs.getFloat(KEY_SOUND_VOLUME, 1f).coerceIn(0f, 1f),
            speed = playbackPrefs.getFloat(KEY_SOUND_SPEED, 1f)
                .coerceIn(MIN_SOUND_SPEED, MAX_SOUND_SPEED),
            pitch = playbackPrefs.getFloat(KEY_SOUND_PITCH, 1f)
                .coerceIn(MIN_SOUND_SPEED, MAX_SOUND_SPEED),
            fixedPitch = playbackPrefs.getBoolean(KEY_SOUND_FIXED_PITCH, true),
            skipSilence = playbackPrefs.getBoolean(KEY_SOUND_SKIP_SILENCE, false)
        )
    )
    val soundState: StateFlow<SoundUiState> = _soundState.asStateFlow()

    private var controller: MediaController? = null
    private var restored = false
    private var apiReady = false
    private data class PendingPlay(val songs: List<SongInfo>, val index: Int)
    private var pendingPlay: PendingPlay? = null

    // 「下一首播放」排队偏移：当前播放歌曲后已连续插入的待播歌曲数（FIFO 排队用）
    private var nextInsertOffset = 0

    init {
        connectController()
        // 当前播放歌曲变化（切歌/播放完成/重新播放）时，重置排队偏移
        viewModelScope.launch {
            _uiState.map { it.currentIndex }
                .distinctUntilChanged()
                .drop(1)
                .collect { nextInsertOffset = 0 }
        }
    }

    fun onApiReady() {
        apiReady = true
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        super.onCleared()
    }

    // ==================== 连接 ====================

    private fun connectController() {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                onControllerReady(c)
                startProgressLoop()
            } catch (e: Exception) {
                android.util.Log.e("LxMusic", "MediaController 连接失败", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun onControllerReady(c: MediaController) {
        applyPlayMode(c, _uiState.value.playMode)
        c.addListener(playerListener)
        // 以控制器实际状态为准（PlayerService 启动时已按 prefs 恢复）
        _soundState.update {
            it.copy(
                volume = c.volume,
                speed = c.playbackParameters.speed,
                pitch = if (it.fixedPitch) c.playbackParameters.speed else c.playbackParameters.pitch
            )
        }
        restoreIfNeeded(c)
        pendingPlay?.let { p ->
            pendingPlay = null
            play(p.songs, p.index)
        }
    }

    private fun startProgressLoop() {
        viewModelScope.launch {
            var lastPositionSave = 0L
            while (isActive) {
                val c = controller
                if (c != null) {
                    // 顺带读取解码音频格式（PlayerService 的 ExoPlayer 更新）
                    _audioFormat.value = PlayerService.lastAudioFormat
                    val pos = c.currentPosition.coerceAtLeast(0L)
                    val dur = c.duration.coerceAtLeast(0L)
                    // 等待中：请求播放但尚未真正出声（缓冲/准备）
                    val waiting = c.playWhenReady && !c.isPlaying &&
                        c.playbackState == Player.STATE_BUFFERING
                    val prev = _progress.value
                    if (pos != prev.positionMs || dur != prev.durationMs || waiting != prev.isWaiting) {
                        _progress.value = PlayerProgress(pos, dur, waiting)
                    }
                    if (c.isPlaying) {
                        val now = System.currentTimeMillis()
                        if (now - lastPositionSave > 15_000) {
                            savePosition(pos)
                            lastPositionSave = now
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val c = controller ?: return
            val idx = c.currentMediaItemIndex
            _uiState.update {
                it.copy(
                    currentIndex = idx,
                    currentPlayingPath = it.queue.getOrNull(idx)?.filePath
                )
            }
            KuGouApi.lastBitRate = 0
            KuGouApi.lastExtName = null

            val q = _uiState.value.queue
            viewModelScope.launch(Dispatchers.IO) {
                val app = getApplication<Application>()
                com.example.lxmusic.data.LyricRepository.preload(app, q.getOrNull(idx))
                com.example.lxmusic.data.LyricRepository.preload(app, q.getOrNull(idx + 1))
                com.example.lxmusic.data.LyricRepository.preload(app, q.getOrNull(idx + 2))
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            _uiState.update { it.copy(isPlaying = playing) }
            saveFullState()
        }

        override fun onPlaybackStateChanged(state: Int) {
            val c = controller ?: return
            if (state == Player.STATE_READY) {
                val dur = c.duration.coerceAtLeast(0L)
                if (dur != _progress.value.durationMs) {
                    _progress.value = PlayerProgress(_progress.value.positionMs, dur)
                }
                val idx = _uiState.value.currentIndex
                val q = _uiState.value.queue
                if (idx in q.indices) {
                    val song = q[idx]
                    if (song.duration <= 0 && dur > 0) {
                        val updated = q.toMutableList()
                        updated[idx] = song.copy(duration = dur)
                        _uiState.update { it.copy(queue = updated) }
                        val hash = song.filePath.split("|").getOrElse(0) { "" }
                        if (hash.isNotBlank()) SongDurationCache.put(hash, dur)
                    }
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            android.util.Log.e("LxMusic", "播放错误: ${error.message}")
            val c = controller ?: return
            if (c.hasNextMediaItem()) {
                c.seekToNext()
                c.prepare()
                c.play()
            } else {
                Toast.makeText(getApplication(), "播放列表已结束", Toast.LENGTH_SHORT).show()
            }
        }

        // ===== 声音设置状态同步（系统音量键/其他控制器改动时界面跟随） =====
        override fun onVolumeChanged(volume: Float) {
            _soundState.update { it.copy(volume = volume) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            _soundState.update {
                it.copy(
                    speed = playbackParameters.speed,
                    pitch = if (it.fixedPitch) playbackParameters.speed else playbackParameters.pitch
                )
            }
        }

        override fun onSkipSilenceEnabledChanged(enabled: Boolean) {
            _soundState.update { it.copy(skipSilence = enabled) }
        }
    }

    // ==================== 播放操作 ====================

    fun play(songs: List<SongInfo>, index: Int) {
        val c = controller ?: run {
            pendingPlay = PendingPlay(songs, index)
            return
        }
        val safeIndex = index.coerceIn(0, songs.lastIndex.coerceAtLeast(0))
        _uiState.update {
            it.copy(
                queue = songs,
                currentIndex = safeIndex,
                currentPlayingPath = songs.getOrNull(safeIndex)?.filePath
            )
        }
        c.setMediaItems(songs.map { it.toMediaItem() }, safeIndex, 0)
        c.prepare()
        c.play()
        saveFullState()

        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            com.example.lxmusic.data.LyricRepository.preload(app, songs.getOrNull(safeIndex))
            com.example.lxmusic.data.LyricRepository.preload(app, songs.getOrNull(safeIndex + 1))
            com.example.lxmusic.data.LyricRepository.preload(app, songs.getOrNull(safeIndex + 2))
        }
    }

    fun playOnline(songs: List<SongInfo>, index: Int) {
        if (apiReady) {
            play(songs, index)
            return
        }
        val safeIndex = index.coerceIn(0, songs.lastIndex.coerceAtLeast(0))
        _uiState.update {
            it.copy(
                queue = songs,
                currentIndex = safeIndex,
                currentPlayingPath = songs.getOrNull(safeIndex)?.filePath
            )
        }
        viewModelScope.launch {
            val ready = withTimeoutOrNull(15_000) {
                while (!apiReady) delay(100)
                true
            } ?: false
            if (ready) play(songs, index)
        }
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
            saveFullState()
        } else {
            c.play()
        }
    }

    // ==================== 睡眠定时 ====================

    /**
     * 启动睡眠定时：直调 PlayerService 静态入口（定时逻辑在服务内，
     * 播放中场景退出 App / Activity 销毁后仍随播放继续生效）。
     */
    fun startSleepTimer(minutes: Int, extendOnSongEnd: Boolean) {
        val armed = PlayerService.startSleepTimerStatic(minutes, extendOnSongEnd)
        if (!armed) {
            Toast.makeText(getApplication(), "播放服务未就绪，请稍后重试", Toast.LENGTH_SHORT).show()
        }
    }

    /** 手动停止定时：只清定时不动播放 */
    fun cancelSleepTimer() {
        PlayerService.cancelSleepTimerStatic()
    }

    fun playNext() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        viewModelScope.launch {
            crossfadeToNext {
                // 单曲循环（REPEAT_MODE_ONE）下 seekToNext 会重播当前曲目（Media3 行为）：
                // 临时切到顺序模式再切歌，随后恢复单曲循环
                val prevRepeat = c.repeatMode
                if (prevRepeat == Player.REPEAT_MODE_ONE) {
                    c.repeatMode = Player.REPEAT_MODE_OFF
                }
                if (c.hasNextMediaItem()) c.seekToNext() else c.seekTo(0, 0)
                if (prevRepeat == Player.REPEAT_MODE_ONE) {
                    c.repeatMode = Player.REPEAT_MODE_ONE
                }
                c.prepare()
                c.play()
            }
        }
    }

    fun playPrevious() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        viewModelScope.launch {
            crossfadeToNext {
                // 单曲循环下临时切顺序模式再切歌，随后恢复（同 playNext）
                val prevRepeat = c.repeatMode
                if (prevRepeat == Player.REPEAT_MODE_ONE) {
                    c.repeatMode = Player.REPEAT_MODE_OFF
                }
                // 必须用 seekToPreviousMediaItem：seekToPrevious 是 Media3 的"3 秒内才切上一首，
                // 否则重播当前曲"语义，歌播到中段时点上一首会变成从头播放
                if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem() else c.seekTo(0, 0)
                if (prevRepeat == Player.REPEAT_MODE_ONE) {
                    c.repeatMode = Player.REPEAT_MODE_ONE
                }
                c.prepare()
                c.play()
            }
        }
    }

    // 切歌交叉淡化统一由 PlayerService 的服务侧播放器处理：
    // UI、通知栏、锁屏、蓝牙按键走同一条淡出→切歌→淡入链路，这里只执行切歌动作，
    // 避免 ViewModel 和 Service 两层各淡出一次。
    private fun crossfadeToNext(block: () -> Unit) {
        block()
    }

    // ==================== 声音设置 ====================

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _soundState.update { it.copy(volume = clamped) }
        playbackPrefs.edit().putFloat(KEY_SOUND_VOLUME, clamped).apply()
        controller?.volume = clamped
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(MIN_SOUND_SPEED, MAX_SOUND_SPEED)
        _soundState.update { it.copy(speed = clamped) }
        playbackPrefs.edit().putFloat(KEY_SOUND_SPEED, clamped).apply()
        applyPlaybackParameters()
    }

    fun setPitch(pitch: Float) {
        if (_soundState.value.fixedPitch) return
        val clamped = pitch.coerceIn(MIN_SOUND_SPEED, MAX_SOUND_SPEED)
        _soundState.update { it.copy(pitch = clamped) }
        playbackPrefs.edit().putFloat(KEY_SOUND_PITCH, clamped).apply()
        applyPlaybackParameters()
    }

    /** 锁定音调跟随速度（true）/ 解锁独立变调（false） */
    fun setFixedPitch(fixed: Boolean) {
        _soundState.update {
            it.copy(fixedPitch = fixed, pitch = if (fixed) it.speed else it.pitch)
        }
        playbackPrefs.edit().putBoolean(KEY_SOUND_FIXED_PITCH, fixed).apply()
        applyPlaybackParameters()
    }

    fun resetSpeedAndPitch() {
        _soundState.update { it.copy(speed = 1f, pitch = 1f) }
        playbackPrefs.edit()
            .putFloat(KEY_SOUND_SPEED, 1f)
            .putFloat(KEY_SOUND_PITCH, 1f)
            .apply()
        applyPlaybackParameters()
    }

    fun setSkipSilence(enabled: Boolean) {
        _soundState.update { it.copy(skipSilence = enabled) }
        playbackPrefs.edit().putBoolean(KEY_SOUND_SKIP_SILENCE, enabled).apply()
        // Player/MediaController 没有跳过静音的公开接口，走会话自定义命令交给 PlayerService
        val c = controller ?: return
        runCatching {
            c.sendCustomCommand(
                androidx.media3.session.SessionCommand(ACTION_SET_SKIP_SILENCE, android.os.Bundle.EMPTY),
                android.os.Bundle().apply { putBoolean("enabled", enabled) }
            )
        }
    }

    private fun applyPlaybackParameters() {
        val state = _soundState.value
        val targetPitch = if (state.fixedPitch) state.speed else state.pitch
        val params = androidx.media3.common.PlaybackParameters(state.speed, targetPitch)
        runCatching { controller?.playbackParameters = params }
    }

    fun seekTo(positionMs: Long) {
        val c = controller ?: return
        c.seekTo(positionMs)
        _progress.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun seekToIndex(index: Int) {
        val c = controller ?: return
        val q = _uiState.value.queue
        if (index !in q.indices) return
        c.seekTo(index, 0)
        c.play()
        _uiState.update {
            it.copy(currentIndex = index, currentPlayingPath = q.getOrNull(index)?.filePath)
        }
    }

    fun setPlayMode(mode: Int) {
        if (mode == _uiState.value.playMode) return
        _uiState.update { it.copy(playMode = mode) }
        // 保留播放状态开关：关闭时不持久化
        if (settingsPrefs.getBoolean("playback_keep_mode", true)) {
            settingsPrefs.edit().putInt("play_mode", mode).apply()
        }
        controller?.let { applyPlayMode(it, mode) }
    }

    fun moveItem(from: Int, to: Int) {
        val q = _uiState.value.queue.toMutableList()
        if (from !in q.indices || to !in q.indices) return
        val item = q.removeAt(from)
        q.add(to, item)
        val currentPath = _uiState.value.currentPlayingPath
        val newIndex = q.indexOfFirst { it.filePath == currentPath }.coerceAtLeast(0)
        _uiState.update { it.copy(queue = q, currentIndex = newIndex) }
        controller?.moveMediaItem(from, to)
    }

    fun removeItem(filePath: String) {
        val c = controller ?: return
        val q = _uiState.value.queue
        val removeIndex = q.indexOfFirst { it.filePath == filePath }
        if (removeIndex < 0) return
        val newQ = q.filter { it.filePath != filePath }
        val oldIndex = _uiState.value.currentIndex
        val newIndex = when {
            removeIndex < oldIndex -> oldIndex - 1
            removeIndex == oldIndex -> oldIndex.coerceIn(0, newQ.lastIndex)
            else -> oldIndex
        }
        _uiState.update {
            it.copy(
                queue = newQ,
                currentIndex = newIndex,
                currentPlayingPath = newQ.getOrNull(newIndex)?.filePath
            )
        }
        c.removeMediaItem(removeIndex)
    }

    fun addToQueueNext(song: SongInfo) {
        val q = _uiState.value.queue
        val currentIdx = _uiState.value.currentIndex
        // FIFO 排队：连续添加时依次排在上一次插入的待播歌曲之后
        val targetIndex = if (q.isEmpty()) 0 else (currentIdx + 1 + nextInsertOffset).coerceIn(0, q.size)

        val existingIdx = q.indexOfFirst { it.filePath == song.filePath }
        val newQ: List<SongInfo>
        if (existingIdx >= 0) {
            // 已在队列中：移到目标位置（避免重复 filePath 导致列表 key 冲突）
            newQ = q.toMutableList().apply {
                removeAt(existingIdx)
                var t = targetIndex
                if (existingIdx < targetIndex) t -= 1
                add(t, song)
            }
        } else {
            newQ = q.toMutableList().apply { add(targetIndex, song) }
        }
        nextInsertOffset++

        // 更新 currentIndex（按当前播放路径重新定位；值不变时 StateFlow 去重，不会触发 offset 重置）
        val currentPath = _uiState.value.currentPlayingPath
        val newIndex = newQ.indexOfFirst { it.filePath == currentPath }.coerceAtLeast(0)
        _uiState.update { it.copy(queue = newQ, currentIndex = newIndex) }

        val c = controller
        if (c != null) {
            if (existingIdx >= 0) {
                val to = newQ.indexOfFirst { it.filePath == song.filePath }
                c.moveMediaItem(existingIdx, to)
            } else {
                c.addMediaItem(targetIndex, song.toMediaItem())
            }
        }
        saveFullState()
        Toast.makeText(getApplication(), "已添加到下一首播放", Toast.LENGTH_SHORT).show()
    }

    // ==================== 恢复与持久化 ====================

    private fun restoreIfNeeded(c: MediaController) {
        if (restored) return
        val savedListJson = playbackPrefs.getString("song_list_json", null)
        val savedIndex = playbackPrefs.getInt("song_index", -1)
        // 保留上次播放进度开关：关闭时从 0 开始
        val keepProgress = settingsPrefs.getBoolean("playback_keep_progress", true)
        val savedPosition = if (keepProgress) playbackPrefs.getLong("position", 0L) else 0L
        if (!savedListJson.isNullOrBlank() && savedIndex >= 0) {
            try {
                val restoredList = Gson().fromJson(savedListJson, Array<SongInfo>::class.java).toList()
                // 去重：避免历史数据重复 filePath 导致播放列表 key 冲突崩溃
                val uniqueListAll = restoredList.distinctBy { it.filePath }
                // 未激活（本地音乐模式）：不恢复在线歌曲，避免启动时反复请求被拒、报 Source error
                val uniqueList = if (BffApi.onlineAllowed) uniqueListAll
                    else uniqueListAll.filter { it.filePath.startsWith("/") }
                if (uniqueList.isEmpty()) {
                    restored = true
                    return
                }
                val currentPath = restoredList.getOrNull(savedIndex)?.filePath
                val newIndex = uniqueList.indexOfFirst { it.filePath == currentPath }.coerceAtLeast(0)
                if (newIndex in uniqueList.indices) {
                    _uiState.update {
                        it.copy(queue = uniqueList, currentIndex = newIndex, isPlaying = false)
                    }
                    _progress.value = PlayerProgress(savedPosition, 0L)
                    // 先暂停再重置队列：MediaController 命令经 binder 异步送达，
                    // 若继承的服务端 playWhenReady=true 且先 setMediaItems+prepare，
                    // prepare 完成瞬间可能已自动开始播放（渲染器喂数据、解码器丢弃输出
                    // → 断续）。先 pause 锁定 playWhenReady=false 再 prepare。
                    c.pause()
                    c.setMediaItems(uniqueList.map { it.toMediaItem() }, newIndex, savedPosition)
                    c.prepare()
                    _progress.value = PlayerProgress(savedPosition, c.duration.coerceAtLeast(0L))
                }
            } catch (_: Exception) {}
        }
        restored = true
    }

    private fun saveFullState() {
        if (!restored) return
        val s = _uiState.value
        val keepProgress = settingsPrefs.getBoolean("playback_keep_progress", true)
        playbackPrefs.edit()
            .putString("song_list_json", Gson().toJson(s.queue))
            .putInt("song_index", s.currentIndex)
            .putLong("position", if (keepProgress) _progress.value.positionMs else 0L)
            .putBoolean("is_playing", s.isPlaying)
            .apply()
    }

    private fun savePosition(pos: Long) {
        if (!restored) return
        if (!settingsPrefs.getBoolean("playback_keep_progress", true)) return
        playbackPrefs.edit().putLong("position", pos).apply()
    }

    // ==================== 工具 ====================

    private fun SongInfo.toMediaItem(): MediaItem {
        // 在线歌曲（netease:// / tx:// / kw:// / lxsrc://）直接作为播放 URI（SongDataSource 识别）
        if (filePath.contains("://")) {
            return MediaItem.Builder()
                .setUri(Uri.parse(filePath))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setArtworkUri(albumArtUri?.let { Uri.parse(it) })
                        .build()
                )
                .build()
        }
        val parts = filePath.split("|")
        val hash = parts.getOrElse(0) { "" }
        val albumAudioId = parts.getOrElse(1) { "0" }
        val isOnline = hash.isNotBlank() && !filePath.startsWith("/")
        return MediaItem.Builder()
            .setUri(
                if (isOnline) Uri.parse("song://$hash|$albumAudioId")
                else Uri.fromFile(File(filePath))
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(albumArtUri?.let { Uri.parse(it) })
                    .build()
            )
            .build()
    }

    private fun applyPlayMode(c: MediaController, mode: Int) {
        when (mode) {
            0 -> {
                c.shuffleModeEnabled = false
                c.repeatMode = Player.REPEAT_MODE_OFF
            }
            1 -> {
                c.shuffleModeEnabled = true
                c.repeatMode = Player.REPEAT_MODE_OFF
            }
            2 -> {
                c.shuffleModeEnabled = false
                c.repeatMode = Player.REPEAT_MODE_ONE
            }
        }
    }
}
