package com.example.lxmusic.data.search

import android.util.Log
import com.example.lxmusic.RankItem
import com.example.lxmusic.RankSongBrief
import com.example.lxmusic.model.SongInfo
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 各大平台公开排行榜（不需要登录、不需要自建服务器）。
 * - 酷狗：rank/list + rank/song（服务端分页）
 * - 网易云：api/toplist + playlist/detail（一次取全量，本地分页）
 * - QQ：musicu GetAll + fcg_v8_toplist_cp（一次取 100 首，本地分页）
 *
 * 只提供元数据；播放仍由落雪插件或内置链路解析。
 */
object PublicRankingProviders {

    const val PLATFORM_KG = "kg"
    const val PLATFORM_WY = "wy"
    const val PLATFORM_TX = "tx"

    private const val TAG = "PublicRanking"
    private const val TTL = 5 * 60 * 1000L

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private data class CachedSongs(val at: Long, val songs: List<SongInfo>)

    private val wySongCache = ConcurrentHashMap<Long, CachedSongs>()
    private val txSongCache = ConcurrentHashMap<Long, CachedSongs>()

    private fun encode(v: String) = URLEncoder.encode(v, Charsets.UTF_8.name()).replace("+", "%20")

    private fun get(url: String, referer: String? = null, body: String? = null): String? = runCatching {
        val builder = Request.Builder().url(url).header("User-Agent", UA)
        if (referer != null) builder.header("Referer", referer)
        val req = if (body != null) {
            builder.header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
        } else builder.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "HTTP ${resp.code} ${url.take(100)}")
                null
            } else resp.body?.string()
        }
    }.getOrElse {
        Log.w(TAG, "请求失败 ${it.message}: ${url.take(100)}")
        null
    }

    // ==================== 排行榜列表 ====================

    suspend fun rankList(platform: String): List<RankItem> = withContext(Dispatchers.IO) {
        when (platform) {
            PLATFORM_KG -> kugouRankList()
            PLATFORM_WY -> neteaseRankList()
            PLATFORM_TX -> qqRankList()
            else -> emptyList()
        }
    }

    private fun kugouRankList(): List<RankItem> {
        val body = get("http://mobilecdn.kugou.com/api/v3/rank/list?page=1&pagesize=100&withsong=1")
            ?: return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val info = root.getAsJsonObject("data")?.getAsJsonArray("info") ?: return emptyList()
        return info.mapNotNull { el ->
            val o = el.asJsonObject
            val id = o.get("rankid")?.asLong ?: return@mapNotNull null
            val name = o.get("rankname")?.asString ?: return@mapNotNull null
            val previews = o.getAsJsonArray("songinfo")?.mapNotNull { s ->
                val so = s.asJsonObject
                val songName = so.get("name")?.asString
                    ?: so.get("songname")?.asString?.substringAfter(" - ")
                    ?: return@mapNotNull null
                val rawName = so.get("songname")?.asString ?: ""
                val author = if (rawName.contains(" - ")) rawName.substringBefore(" - ") else null
                RankSongBrief(name = songName, author = author, songname = rawName)
            }.orEmpty()
            RankItem(
                rankid = id,
                rankname = name,
                album_img_9 = o.get("album_img_9")?.asString ?: o.get("img_9")?.asString,
                img_9 = o.get("img_9")?.asString,
                play_times = o.get("play_times")?.asLong ?: 0L,
                update_frequency_type = o.get("update_frequency_type")?.asInt ?: 0,
                songinfo = previews,
                platform = PLATFORM_KG
            )
        }
    }

    private fun neteaseRankList(): List<RankItem> {
        val body = get("https://music.163.com/api/toplist", referer = "https://music.163.com/")
            ?: return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val list = root.getAsJsonArray("list") ?: return emptyList()
        return list.mapNotNull { el ->
            val o = el.asJsonObject
            val id = o.get("id")?.asLong ?: return@mapNotNull null
            val name = o.get("name")?.asString ?: return@mapNotNull null
            RankItem(
                rankid = id,
                rankname = name,
                album_img_9 = o.get("coverImgUrl")?.asString,
                play_times = o.get("playCount")?.asLong ?: 0L,
                songinfo = emptyList(),
                platform = PLATFORM_WY
            )
        }
    }

    private fun qqRankList(): List<RankItem> {
        val payload = """{"comm":{"g_tk":5381,"uin":"","format":"json","ct":6,"cv":8061},""" +
            """"topList":{"module":"musicToplist.ToplistInfoServer","method":"GetAll","param":{}}}"""
        val body = get(
            "https://u.y.qq.com/cgi-bin/musicu.fcg",
            referer = "https://y.qq.com/",
            body = payload
        ) ?: return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val groups = root.getAsJsonObject("topList")?.getAsJsonObject("data")?.getAsJsonArray("group")
            ?: return emptyList()
        val result = mutableListOf<RankItem>()
        groups.forEach { g ->
            val tops = g.asJsonObject.getAsJsonArray("toplist") ?: return@forEach
            tops.forEach { t ->
                val o = t.asJsonObject
                val id = o.get("topId")?.asLong ?: return@forEach
                val name = o.get("title")?.asString ?: return@forEach
                // 跳过 MV/视频类榜单（recType=10010 或标题含 MV），这类榜单没有可播放的歌曲
                val recType = o.get("recType")?.asInt ?: 0
                if (recType == 10010 || name.contains("MV", ignoreCase = true)) return@forEach
                result += RankItem(
                    rankid = id,
                    rankname = name,
                    album_img_9 = o.get("frontPicUrl")?.asString
                        ?: o.get("mbFrontPicUrl")?.asString
                        ?: o.get("headPicUrl")?.asString,
                    play_times = o.get("listenNum")?.asLong ?: 0L,
                    songinfo = emptyList(),
                    platform = PLATFORM_TX
                )
            }
        }
        return result
    }

    // ==================== 排行榜歌曲 ====================

    suspend fun rankSongs(
        platform: String,
        rankId: Long,
        page: Int,
        pageSize: Int
    ): Pair<List<SongInfo>, Int> = withContext(Dispatchers.IO) {
        when (platform) {
            PLATFORM_KG -> kugouRankSongs(rankId, page, pageSize)
            PLATFORM_WY -> slice(wySongs(rankId), page, pageSize)
            PLATFORM_TX -> slice(txSongs(rankId), page, pageSize)
            else -> emptyList<SongInfo>() to 0
        }
    }

    private fun slice(all: List<SongInfo>, page: Int, pageSize: Int): Pair<List<SongInfo>, Int> {
        if (all.isEmpty()) return emptyList<SongInfo>() to 0
        val from = (page - 1) * pageSize
        if (from >= all.size) return emptyList<SongInfo>() to all.size
        return all.subList(from, minOf(from + pageSize, all.size)) to all.size
    }

    private suspend fun kugouRankSongs(rankId: Long, page: Int, pageSize: Int): Pair<List<SongInfo>, Int> {
        val url = "http://mobilecdn.kugou.com/api/v3/rank/song?rankid=$rankId&page=$page&pagesize=$pageSize"
        val body = get(url) ?: return emptyList<SongInfo>() to 0
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
            ?: return emptyList<SongInfo>() to 0
        val data = root.getAsJsonObject("data") ?: return emptyList<SongInfo>() to 0
        val total = data.get("total")?.asInt ?: 0
        val info = data.getAsJsonArray("info") ?: return emptyList<SongInfo>() to total
        val songs = info.mapNotNull { el ->
            val o = el.asJsonObject
            val hash = o.get("hash")?.asString ?: return@mapNotNull null
            val audioId = o.get("album_audio_id")?.asString ?: "0"
            val title = o.get("songname")?.asString
                ?: o.get("filename")?.asString?.substringAfter(" - ")
                ?: return@mapNotNull null
            val artist = o.getAsJsonArray("authors")?.joinToString("、") { a ->
                a.asJsonObject.get("author_name")?.asString ?: ""
            } ?: o.get("filename")?.asString?.substringBefore(" - ") ?: ""
            val duration = (o.get("duration")?.asLong ?: 0L) * 1000
            val albumId = o.get("album_id")?.asString ?: ""
            SongInfo(
                title = title,
                artist = artist,
                filePath = "$hash|$audioId",
                albumArtUri = null,
                duration = duration
            ) to albumId
        }
        // 补封面（album/info，按专辑并行 + 缓存）
        val enriched = coroutineScope {
            songs.map { (song, albumId) ->
                async(Dispatchers.IO) {
                    if (albumId.isBlank()) song
                    else song.copy(albumArtUri = PublicSearchProviders.kugouCover(albumId))
                }
            }.awaitAll()
        }
        return enriched to total
    }

    private fun wySongs(rankId: Long): List<SongInfo> {
        wySongCache[rankId]?.let {
            if (System.currentTimeMillis() - it.at < TTL) return it.songs
        }
        val body = get(
            "https://music.163.com/api/playlist/detail?id=$rankId",
            referer = "https://music.163.com/"
        ) ?: return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val tracks = root.getAsJsonObject("result")?.getAsJsonArray("tracks") ?: return emptyList()
        val songs = tracks.mapNotNull { el ->
            val o = el.asJsonObject
            val id = o.get("id")?.asString ?: return@mapNotNull null
            val name = o.get("name")?.asString ?: return@mapNotNull null
            // 新版字段 ar/al/dt，旧版 playlist/detail 用 artists/album/duration
            val artist = o.getAsJsonArray("ar")?.joinToString("、") { a ->
                a.asJsonObject.get("name")?.asString ?: ""
            } ?: o.getAsJsonArray("artists")?.joinToString("、") { a ->
                a.asJsonObject.get("name")?.asString ?: ""
            } ?: ""
            val album = o.getAsJsonObject("al") ?: o.getAsJsonObject("album")
            val cover = album?.get("picUrl")?.asString
                ?: album?.get("blurPicUrl")?.asString
            SongInfo(
                title = name,
                artist = artist,
                filePath = "netease://$id",
                albumArtUri = cover?.replace("http://", "https://"),
                duration = o.get("dt")?.asLong ?: o.get("duration")?.asLong ?: 0L
            )
        }
        if (songs.isNotEmpty()) wySongCache[rankId] = CachedSongs(System.currentTimeMillis(), songs)
        return songs
    }

    private fun txSongs(rankId: Long): List<SongInfo> {
        txSongCache[rankId]?.let {
            if (System.currentTimeMillis() - it.at < TTL) return it.songs
        }
        val url = "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg?topid=$rankId" +
            "&format=json&page=detail&tpl=3&type=top"
        val body = get(url, referer = "https://y.qq.com/") ?: return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val songlist = root.getAsJsonArray("songlist") ?: return emptyList()
        val songs = songlist.mapNotNull { el ->
            val data = el.asJsonObject.getAsJsonObject("data") ?: return@mapNotNull null
            val songmid = data.get("songmid")?.asString ?: return@mapNotNull null
            val title = data.get("songname")?.asString ?: return@mapNotNull null
            val artist = data.getAsJsonArray("singer")?.joinToString("、") { s ->
                s.asJsonObject.get("name")?.asString ?: ""
            } ?: ""
            val albummid = data.get("albummid")?.asString ?: ""
            val cover = if (albummid.isNotBlank()) {
                "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albummid.jpg"
            } else null
            SongInfo(
                title = title,
                artist = artist,
                filePath = "tx://$songmid" + if (albummid.isNotBlank()) "?albummid=$albummid" else "",
                albumArtUri = cover,
                duration = (data.get("interval")?.asLong ?: 0L) * 1000
            )
        }
        if (songs.isNotEmpty()) txSongCache[rankId] = CachedSongs(System.currentTimeMillis(), songs)
        return songs
    }
}
