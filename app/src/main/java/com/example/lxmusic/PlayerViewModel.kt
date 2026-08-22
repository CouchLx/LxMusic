@file:OptIn(androidx.media3.common.util.UnstableApi::class)

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
                // 同 playNext：单曲循环下 seekToPrevious 也会重播当前曲目，临时切换模式
                val prevRepeat = c.repeatMode
                if (prevRepeat == Player.REPEAT_MODE_ONE) {
                    c.repeatMode = Player.REPEAT_MODE_OFF
                }
                if (c.hasPreviousMediaItem()) c.seekToPrevious() else c.seekTo(0, 0)
                if (prevRepeat == Player.REPEAT_MODE_ONE) {
                    c.repeatMode = Player.REPEAT_MODE_ONE
                }
                c.prepare()
                c.play()
            }
        }
    }

    // 切歌交叉淡化（对齐 Neri：淡出当前曲 → 切歌 → 淡入下一首）
    private suspend fun crossfadeToNext(block: () -> Unit) {
        val c = controller ?: return
        val enabled = settingsPrefs.getBoolean("playback_crossfade_next", false)
        if (!enabled) {
            block()
            return
        }
        val outMs = settingsPrefs.getInt("playback_crossfade_out_ms", 500)
        val inMs = settingsPrefs.getInt("playback_crossfade_in_ms", 500)
        val steps = 10
        val outDelay = (outMs / steps).coerceAtLeast(1)
        repeat(steps) { i ->
            c.volume = (1f - (i + 1).toFloat() / steps).coerceAtLeast(0f)
            delay(outDelay.toLong())
        }
        block()
        c.volume = 0f
        val inDelay = (inMs / steps).coerceAtLeast(1)
        repeat(steps) { i ->
            c.volume = ((i + 1).toFloat() / steps).coerceAtMost(1f)
            delay(inDelay.toLong())
        }
        c.volume = 1f
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
                val uniqueList = restoredList.distinctBy { it.filePath }
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
