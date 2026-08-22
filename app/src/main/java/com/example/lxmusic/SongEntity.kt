package com.example.lxmusic

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    val filePath: String,        // 文件路径作为唯一标识
    val title: String,
    val artist: String,
    val duration: Long = 0,      // 时长（毫秒）
    val albumArtUri: String? = null,  // MediaStore 专辑封面 content:// URI
    val lyrics: String? = null        // 歌词（内嵌/外部 .lrc）
)

// ==================== 收藏功能实体 ====================

/** 本地收藏的歌曲（"我的收藏"），filePath 格式为 "hash|audioId" */
@Entity(tableName = "collected_songs")
data class CollectedSongEntity(
    @PrimaryKey
    val filePath: String,            // "hash|audioId" 作为唯一标识
    val title: String,
    val artist: String,
    val albumArtUri: String? = null, // 封面 URL
    val duration: Long = 0,
    val hash: String,                // 酷狗 hash（播放用）
    val audioId: Long = 0,          // 酷狗 audio_id（播放用）
    val albumId: Long = 0,
    val mixsongid: Long = 0,
    val collectedAt: Long = System.currentTimeMillis()
)

/** 用户自建歌单 */
@Entity(tableName = "user_playlists")
data class UserPlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** 歌单-歌曲关联表，直接存储完整歌曲数据，不依赖 collected_songs */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songFilePath"]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songFilePath: String,       // 本地歌曲为绝对路径，网络歌曲为 "hash|audioId"
    val title: String,
    val artist: String,
    val albumArtUri: String? = null,
    val duration: Long = 0,
    val hash: String = "",
    val audioId: Long = 0,
    val albumId: Long = 0,
    val mixsongid: Long = 0,
    val lyrics: String? = null      // 本地歌曲歌词（.lrc/内嵌），保证歌单离线播放时歌词可用
) {
    fun toSongInfo() = com.example.lxmusic.model.SongInfo(
        title = title,
        artist = artist,
        // 本地歌曲存的是绝对路径（以 / 开头），原样返回；网络歌曲为 "hash|audioId"
        filePath = if (songFilePath.startsWith("/")) songFilePath else "$hash|$audioId",
        albumArtUri = albumArtUri,
        duration = duration,
        albumId = albumId,
        mixsongid = mixsongid,
        lyrics = lyrics
    )
}

// ==================== 喜欢镜像（官方收藏模式的本地先写） ====================

/** 官方收藏模式下「喜欢」歌曲的本地镜像：先写本地、后台同步酷狗（filePath = "hash|audioId"） */
@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey
    val filePath: String,            // "hash|audioId" 作为唯一标识
    val title: String,
    val artist: String,
    val albumArtUri: String? = null, // 封面 URL
    val duration: Long = 0,
    val hash: String,                // 酷狗 hash（播放用）
    val audioId: Long = 0,           // 酷狗 audio_id（播放用）
    val albumId: Long = 0,
    val mixsongid: Long = 0,
    val likedAt: Long = System.currentTimeMillis()
)

/** 官方收藏模式下「收藏的歌单」本地镜像（点开按 gid 在线拉歌播放/删除） */
@Entity(tableName = "liked_playlists")
data class LikedPlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val gid: String = "",
    val coverUrl: String = "",
    val songcount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)