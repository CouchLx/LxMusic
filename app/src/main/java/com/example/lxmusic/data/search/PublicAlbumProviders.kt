package com.example.lxmusic.data.search

import android.util.Log
import com.example.lxmusic.model.SongInfo
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 酷狗专辑歌曲（公开 mobilecdn 接口，无需登录/激活）。
 * 专辑详情页与歌单详情页同一套交互：分页、播放全部、正在播放音阶。
 */
object PublicAlbumProviders {

    data class AlbumSongs(val songs: List<SongInfo>, val total: Int)

    data class AlbumInfo(val name: String, val artist: String, val coverUrl: String)

    private const val TAG = "PublicAlbum"
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val coverCache = mutableMapOf<Long, String>()

    private fun get(url: String): String? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "HTTP ${resp.code} ${url.take(100)}")
                null
            } else resp.body?.string()
        }
    }.getOrElse {
        Log.w(TAG, "请求失败 ${it.message}: ${url.take(100)}")
        null
    }

    /** 专辑基本信息（搜索项缺歌手/封面时兜底） */
    suspend fun info(albumId: Long): AlbumInfo? = withContext(Dispatchers.IO) {
        val body = get("http://mobilecdn.kugou.com/api/v3/album/info?albumid=$albumId") ?: return@withContext null
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
            ?: return@withContext null
        val data = root.getAsJsonObject("data") ?: return@withContext null
        val cover = data.get("imgurl")?.asString.orEmpty()
            .replace("{size}", "240").replace("http://", "https://")
        if (cover.isNotBlank()) coverCache[albumId] = cover
        AlbumInfo(
            name = data.get("albumname")?.asString.orEmpty(),
            artist = data.get("singername")?.asString.orEmpty(),
            coverUrl = cover
        )
    }

    /** 专辑封面（搜索结果没带图时兜底） */
    fun coverFor(albumId: Long): String {
        coverCache[albumId]?.let { return it }
        val body = get("http://mobilecdn.kugou.com/api/v3/album/info?albumid=$albumId") ?: return ""
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return ""
        val url = root.getAsJsonObject("data")?.get("imgurl")?.asString.orEmpty()
            .replace("{size}", "240").replace("http://", "https://")
        if (url.isNotBlank()) coverCache[albumId] = url
        return url
    }

    suspend fun songs(
        albumId: Long,
        coverUrl: String,
        page: Int,
        pageSize: Int = 30
    ): AlbumSongs = withContext(Dispatchers.IO) {
        val body = get("http://mobilecdn.kugou.com/api/v3/album/song?albumid=$albumId&page=$page&pagesize=$pageSize")
            ?: return@withContext AlbumSongs(emptyList(), 0)
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
            ?: return@withContext AlbumSongs(emptyList(), 0)
        val data = root.getAsJsonObject("data") ?: return@withContext AlbumSongs(emptyList(), 0)
        val total = data.get("total")?.asInt ?: 0
        val info = data.getAsJsonArray("info") ?: return@withContext AlbumSongs(emptyList(), total)
        val cover = coverUrl.ifBlank { coverFor(albumId) }
        val songs = info.mapNotNull { el ->
            val o = el.asJsonObject
            val hash = o.get("hash")?.asString ?: return@mapNotNull null
            val audioId = o.get("album_audio_id")?.asString ?: "0"
            val filename = o.get("filename")?.asString.orEmpty()
            val title = o.get("songname")?.asString?.takeIf { it.isNotBlank() && it != "null" }
                ?: filename.substringAfter(" - ", filename)
            val artist = o.get("singername")?.asString?.takeIf { it.isNotBlank() && it != "null" }
                ?: filename.substringBefore(" - ", "")
            SongInfo(
                title = title,
                artist = artist,
                filePath = "$hash|$audioId",
                albumArtUri = cover.takeIf { it.isNotBlank() },
                duration = (o.get("duration")?.asLong ?: 0L) * 1000
            )
        }
        AlbumSongs(songs, total)
    }
}
