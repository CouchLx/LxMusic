package com.example.lxmusic.ui.shelf

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.lxmusic.SongDataSource
import com.example.lxmusic.model.SongInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 试听模式：参考项目同名枚举 */
enum class RecordShelfPreviewMode { ONCE, LOOP, NEXT, RANDOM }

/**
 * 唱片架试听：独立的 ExoPlayer，绝不占用主播放队列与进度。
 *
 * 参考 CurrentMusicX 的 RecordShelfPreview，但 Apple 侧依赖（副歌分析 / 音频输出模式）换成
 * 本项目的 [SongDataSource] 解析链；没有副歌数据时退化为「从 35% 处试听 45 秒」或整首试听。
 */
@OptIn(UnstableApi::class)
internal class RecordShelfPreview(context: Context) {
    // 不用 Main.immediate：onPreview 是从 View.onDraw 里回调出来的，
    // 若同步执行会在这个绘制帧内改播放器状态，改成投递到下一轮主线程
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var active = true
    private var blocked = false
    private var pausedMainForPreview = false
    private var currentPath: String? = null
    private var released = false

    var fullLength: Boolean = false
        private set

    var mode: RecordShelfPreviewMode = RecordShelfPreviewMode.ONCE
        private set

    private val player = ExoPlayer.Builder(context.applicationContext)
        // 和主播放器一致：song:// 这类自定义 scheme 用 ProgressiveMediaSource，
        // DefaultMediaSourceFactory 会按扩展名猜容器，猜不到时会抛 UnrecognizedInputFormat
        .setMediaSourceFactory(ProgressiveMediaSource.Factory(SongDataSource.Factory()))
        .build()
        .apply {
            volume = 1f
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("LxMusicShelf", "试听失败: ${error.errorCodeName}", error)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        when (mode) {
                            // 单次试听结束：停在静音，不要把外面那首重新放起来
                            RecordShelfPreviewMode.ONCE -> stopPreview()
                            RecordShelfPreviewMode.LOOP -> {
                                seekTo(0)
                                play()
                            }
                            else -> onFinished?.invoke()
                        }
                    }
                }
            })
        }

    /** 试听自然结束时通知宿主，让它按 NEXT/RANDOM 换下一张 */
    var onFinished: (() -> Unit)? = null

    /** 进架子就压住主播放器，不用等第一张唱片试听开始 */
    fun begin() {
        if (released || pausedMainForPreview) return
        pausedMainForPreview = ShelfPlaybackBridge.pauseMain()
        android.util.Log.d("LxMusicShelf", "进入唱片架，主播放器暂停=$pausedMainForPreview")
    }

    /** 当前试听片段在原曲里的起点（整首试听时为 0） */
    private var previewStartMs = 0L

    /** 本次试听是不是「片段」模式（从 35% 处起） */
    private var previewClipped = false

    fun setFullLength(value: Boolean) { fullLength = value }

    fun setMode(value: RecordShelfPreviewMode) { mode = value }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (!value) {
            // 页面不可见：停试听并把主播放器恢复到进架子前的状态
            stopPreview()
            restoreMain()
        }
    }

    fun select(song: SongInfo?) {
        if (released) return
        if (!active || blocked) return
        if (song == null) {
            // 拖动找歌时只停试听，主播放器保持暂停，不能把外面的歌放出来
            stopPreview()
            return
        }
        if (song.filePath == currentPath && job?.isActive == true) return
        currentPath = song.filePath
        job?.cancel()
        job = scope.launch {
            player.stop()
            player.clearMediaItems()
            // 快速划过很多张时不要重复暂停：只在第一次试听时问一次桥
            if (!pausedMainForPreview) {
                pausedMainForPreview = ShelfPlaybackBridge.pauseMain()
                android.util.Log.d("LxMusicShelf", "试听开始，主播放器暂停=$pausedMainForPreview")
            }
            val builder = MediaItem.Builder().setUri(song.previewUri())
            previewRange(song)?.let { (start, end) ->
                previewStartMs = start
                previewClipped = true
                builder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(start)
                        .setEndPositionMs(end)
                        .build()
                )
            } ?: run {
                previewStartMs = 0L
                previewClipped = false
            }
            player.setMediaItem(builder.build())
            player.prepare()
            player.play()
            android.util.Log.d("LxMusicShelf", "试听请求：${song.title} uri=${player.currentMediaItem?.localConfiguration?.uri}")
        }
    }

    /** 没有副歌数据：整首，或从 35% 开始试听 45 秒 */
    private fun previewRange(song: SongInfo): Pair<Long, Long>? {
        if (fullLength) return null
        val duration = song.duration
        if (duration <= 0L) return null
        val start = (duration * .35f).toLong()
        val end = (start + 45_000L).coerceAtMost(duration)
        return if (end - start < 10_000L) null else start to end
    }

    /**
     * 用户在唱片架上确认播放：停试听，返回试听到的绝对位置（毫秒），交给主播放器从这个位置继续。
     * 返回 0 表示没有试听过，从头播。
     */
    fun commit(): Long {
        blocked = true
        // 只有「整首试听」（从 0 开始）才续播到试听位置；
        // 片段模式是跳到 35% 处试听的，续播会让人一上来就落在歌曲中间，所以直接从头播。
        val playerPosition = player.currentPosition.coerceAtLeast(0L)
        val playerState = player.playbackState
        val position = if (currentPath != null && !previewClipped) {
            (previewStartMs + playerPosition).coerceAtLeast(0L)
        } else 0L
        stopPreview()
        // 主播放器交由新的播放命令接管，关闭架子时不要再"恢复"以免把它暂停掉
        pausedMainForPreview = false
        android.util.Log.d(
            "LxMusicShelf",
            "确认播放，从 ${position}ms 继续（片段模式=$previewClipped，试听器 state=$playerState pos=$playerPosition）"
        )
        return position
    }

    fun close() {
        active = false
        blocked = true
        stopPreview()
        restoreMain()
        released = true
        runCatching { player.release() }
        scope.cancel()
    }

    /** 只停试听，主播放器维持原状（仍在暂停），用于滑动找歌/试听自然结束 */
    private fun stopPreview() {
        job?.cancel(); job = null
        currentPath = null
        previewStartMs = 0L
        previewClipped = false
        runCatching {
            player.stop()
            player.clearMediaItems()
        }
    }

    /** 关闭架子时把主播放器恢复到进架子前的播放状态 */
    private fun restoreMain() {
        if (pausedMainForPreview) {
            pausedMainForPreview = false
            ShelfPlaybackBridge.resumeMain()
        }
    }
}

/**
 * 把 SongInfo.filePath 转成 SongDataSource 认识的 URI。
 *
 * 和 PlayerViewModel.toMediaItem() 保持一致：在线歌曲自带 scheme 直接用；
 * 酷狗是 `hash|albumAudioId`，必须包成 `song://`，否则会被当成本地文件路径。
 */
private fun SongInfo.previewUri(): android.net.Uri {
    if (filePath.contains("://")) return android.net.Uri.parse(filePath)
    if (filePath.startsWith("/")) return android.net.Uri.fromFile(java.io.File(filePath))
    val parts = filePath.split("|")
    val hash = parts.getOrElse(0) { "" }
    val albumAudioId = parts.getOrElse(1) { "0" }
    return if (hash.isNotBlank()) {
        android.net.Uri.parse("song://$hash|$albumAudioId")
    } else {
        android.net.Uri.fromFile(java.io.File(filePath))
    }
}

/** 自动模式的下一张：跳过当前，随机时不重复当前 */internal fun nextRecordPreviewIndex(
    songs: List<SongInfo>,
    selection: Int,
    shuffle: Boolean,
    random: kotlin.random.Random = kotlin.random.Random.Default
): Int? {
    if (songs.isEmpty()) return null
    val candidates = (1..songs.size).map { Math.floorMod(selection + it, songs.size) }
    if (!shuffle) return candidates.firstOrNull()
    val alternatives = candidates.filter { it != selection }
    return (alternatives.ifEmpty { candidates }).randomOrNull(random)
}
