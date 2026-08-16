package com.example.lxmusic.model

import androidx.compose.runtime.Immutable
import com.example.lxmusic.SongEntity

@Immutable
data class SongInfo(
    val title: String,
    val artist: String,
    val filePath: String,
    val albumArtUri: String? = null,
    val duration: Long = 0,
    val lyrics: String? = null,
    val albumId: Long = 0,
    val playlistFileId: Long = 0,
    val mixsongid: Long = 0
)

// ==================== 实体转换扩展 ====================

fun SongEntity.toSongInfo() = SongInfo(
    title = title,
    artist = artist,
    filePath = filePath,
    albumArtUri = albumArtUri,
    duration = duration,
    lyrics = lyrics
)

fun SongInfo.toEntity() = SongEntity(
    filePath = filePath,
    title = title,
    artist = artist,
    duration = duration,
    albumArtUri = albumArtUri,
    lyrics = lyrics
)
