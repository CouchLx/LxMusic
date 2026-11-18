package com.example.lxmusic.ui.shelf

import com.example.lxmusic.model.SongInfo

/**
 * 主播放器的暂停/恢复/带位起播桥。
 *
 * 唱片架试听必须临时压住主播放器（否则两个 ExoPlayer 同时出声），但歌单页、榜单页、
 * 本地页数量多、层级深，逐页传回调会污染一堆签名。这里由 MainActivity 在启动时注册一次。
 *
 * - `pauseMain()` 返回「是否真的暂停了」：没在播时返回 false，关闭架子就不会去"恢复"一首
 *   本来没在放的歌。
 * - `playSongAt` 用于「试听了 30 秒后点确认播放」：从试听到的位置继续，而不是从头开始。
 */
object ShelfPlaybackBridge {
    var pauseMain: () -> Boolean = { false }
    var resumeMain: () -> Unit = {}
    var playSongAt: ((songs: List<SongInfo>, index: Int, startPositionMs: Long) -> Unit)? = null
}
