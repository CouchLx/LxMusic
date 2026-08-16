package com.example.lxmusic.util

import com.example.lxmusic.model.SongInfo

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

fun applyCachedDurations(songs: List<SongInfo>): List<SongInfo> {
    var hasChanges = false
    val result = songs.map { song ->
        if (song.duration > 0) return@map song
        if (!song.filePath.contains("|")) return@map song
        val hash = song.filePath.split("|")[0]
        val cached = SongDurationCache.get(hash) ?: return@map song
        hasChanges = true
        song.copy(duration = cached)
    }
    return if (hasChanges) result else songs
}
