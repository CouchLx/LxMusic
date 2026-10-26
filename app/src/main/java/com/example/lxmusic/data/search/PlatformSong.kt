package com.example.lxmusic.data.search

import com.example.lxmusic.model.SongInfo

/**
 * 统一的多平台搜索结果（只含元数据，不含播放地址）。
 * 播放地址由落雪音源插件（或内置链路）按 platform + id 解析。
 */
data class PlatformSong(
    val platform: String,          // kg / wy / tx / kw
    val id: String,                // hash / netease songId / qq songmid / kuwo rid
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0L,
    val albumId: String = "",
    val albumAudioId: String = "",
    val albumMid: String = "",
    val extra: Map<String, String> = emptyMap()
) {
    /** 转成 App 内部 SongInfo；各平台用独立路径前缀，播放时按平台解析 */
    fun toSongInfo(): SongInfo = when (platform) {
        "kg" -> SongInfo(
            title = title,
            artist = artist.ifBlank { "未知艺术家" },
            filePath = if (albumAudioId.isNotBlank()) "${id}|${albumAudioId}" else "${id}|0",
            albumArtUri = coverUrl,
            duration = durationMs
        )
        "wy" -> SongInfo(
            title = title,
            artist = artist.ifBlank { "未知艺术家" },
            filePath = "netease://$id",
            albumArtUri = coverUrl,
            duration = durationMs
        )
        "tx" -> SongInfo(
            title = title,
            artist = artist.ifBlank { "未知艺术家" },
            filePath = "tx://$id" + if (albumMid.isNotBlank()) "?albummid=$albumMid" else "",
            albumArtUri = coverUrl,
            duration = durationMs
        )
        "kw" -> SongInfo(
            title = title,
            artist = artist.ifBlank { "未知艺术家" },
            filePath = "kw://$id",
            albumArtUri = coverUrl,
            duration = durationMs
        )
        else -> SongInfo(
            title = title,
            artist = artist.ifBlank { "未知艺术家" },
            filePath = "pubsrc://$platform/$id",
            albumArtUri = coverUrl,
            duration = durationMs
        )
    }

    /** 给落雪插件用的 musicInfo（不同平台字段名不同） */
    fun musicInfoJson(): String {
        val intervalSec = (durationMs / 1000).toInt()
        val sb = StringBuilder("{")
        fun put(key: String, value: String, quoted: Boolean = true) {
            if (sb.length > 1) sb.append(',')
            sb.append('"').append(key).append("\":")
            if (quoted) sb.append('"').append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"')
            else sb.append(value)
        }
        when (platform) {
            "kg" -> {
                put("hash", id)
                put("songmid", id)
                put("albumId", albumId.ifBlank { albumAudioId })
                put("albumAudioId", albumAudioId)
                put("name", title)
                put("singer", artist)
                put("albumName", album)
                put("interval", intervalSec.toString(), false)
            }
            "wy" -> {
                put("id", id, false)
                put("songmid", id)
                put("name", title)
                put("singer", artist)
                put("albumName", album)
                put("interval", intervalSec.toString(), false)
            }
            "tx" -> {
                put("songmid", id)
                put("id", id)
                put("albumMid", albumMid)
                put("albumId", albumId)
                put("name", title)
                put("singer", artist)
                put("albumName", album)
                put("interval", intervalSec.toString(), false)
            }
            "kw" -> {
                put("id", id)
                put("songmid", id)
                put("hash", id)
                put("name", title)
                put("singer", artist)
                put("albumName", album)
                put("interval", intervalSec.toString(), false)
            }
        }
        sb.append('}')
        return sb.toString()
    }
}

/** 平台展示名 */
fun platformDisplayName(platform: String): String = when (platform) {
    "kg" -> "酷狗音乐"
    "wy" -> "网易云音乐"
    "tx" -> "QQ音乐"
    "kw" -> "酷我音乐"
    "mg" -> "咪咕音乐"
    else -> platform
}
