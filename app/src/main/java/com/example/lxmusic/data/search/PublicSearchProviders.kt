package com.example.lxmusic.data.search

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 客户端直连的公开搜索接口（不依赖自建服务器）。
 *
 * 只负责元数据：歌名/歌手/专辑/封面/时长/平台歌曲ID。
 * 播放地址仍由落雪音源插件（或已激活的内置链路）解析。
 */
object PublicSearchProviders {

    private const val TAG = "PublicSearch"
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val kugouCoverCache = ConcurrentHashMap<String, String>()
    private val neteaseCoverCache = ConcurrentHashMap<String, String>()

    private val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun get(url: String, referer: String? = null): String? {
        return runCatching {
            val builder = Request.Builder().url(url).header("User-Agent", UA)
            if (referer != null) builder.header("Referer", referer)
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "HTTP ${resp.code} $url")
                    null
                } else resp.body?.string()
            }
        }.getOrElse {
            Log.w(TAG, "请求失败 ${it.message}: ${url.take(100)}")
            null
        }
    }

    suspend fun search(platform: String, keyword: String, page: Int = 1, pageSize: Int = 30): List<PlatformSong> {
        if (keyword.isBlank()) return emptyList()
        return when (platform) {
            "kg" -> kugou(keyword, page, pageSize)
            "wy" -> netease(keyword, page, pageSize)
            "tx" -> qq(keyword, page, pageSize)
            "kw" -> kuwo(keyword, page, pageSize)
            else -> emptyList()
        }
    }

    // ==================== 酷狗 ====================

    private suspend fun kugou(keyword: String, page: Int, pageSize: Int): List<PlatformSong> {
        // 主通道：mobilecdn v3（http；https 该接口常返回空）
        val v3url = "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=${encode(keyword)}" +
            "&page=$page&pagesize=$pageSize&showtype=1"
        val v3 = parseKugouV3(get(v3url))
        val songs = if (v3.isNotEmpty()) v3 else parseKugouV2(
            get("https://songsearch.kugou.com/song_search_v2?keyword=${encode(keyword)}&page=$page&pagesize=$pageSize")
        )
        // 封面：album/info（并行 + 内存缓存）
        return withContext(Dispatchers.IO) {
            coroutineScope {
                songs.map { song ->
                    async {
                        if (song.albumId.isBlank()) song
                        else song.copy(coverUrl = kugouCover(song.albumId))
                    }
                }.awaitAll()
            }
        }
    }

    private fun parseKugouV3(body: String?): List<PlatformSong> {
        if (body.isNullOrBlank()) return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val info = root.getAsJsonObject("data")?.getAsJsonArray("info") ?: return emptyList()
        return info.mapNotNull { el ->
            val o = el.asJsonObject
            val hash = o.get("hash")?.asString ?: return@mapNotNull null
            val title = o.get("songname")?.asString ?: o.get("filename")?.asString?.substringAfter(" - ") ?: return@mapNotNull null
            val artist = o.get("singername")?.asString ?: o.get("filename")?.asString?.substringBefore(" - ") ?: ""
            val albumId = o.get("album_id")?.asString ?: ""
            val albumAudioId = o.get("album_audio_id")?.asString ?: ""
            val durationSec = o.get("duration")?.asLong ?: 0L
            PlatformSong(
                platform = "kg",
                id = hash,
                title = title,
                artist = artist,
                album = o.get("albumname")?.asString ?: "",
                durationMs = durationSec * 1000,
                albumId = albumId,
                albumAudioId = albumAudioId
            )
        }
    }

    private fun parseKugouV2(body: String?): List<PlatformSong> {
        if (body.isNullOrBlank()) return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val lists = root.getAsJsonObject("data")?.getAsJsonArray("lists") ?: return emptyList()
        return lists.mapNotNull { el ->
            val o = el.asJsonObject
            val hash = o.get("FileHash")?.asString ?: return@mapNotNull null
            val title = o.get("SongName")?.asString ?: return@mapNotNull null
            val artist = o.get("SingerName")?.asString ?: ""
            val albumId = o.get("AlbumID")?.asString ?: ""
            val albumAudioId = o.get("Audioid")?.asString ?: ""
            val durationSec = o.get("Duration")?.asLong ?: o.get("HQDuration")?.asLong ?: 0L
            PlatformSong(
                platform = "kg",
                id = hash,
                title = title,
                artist = artist,
                album = o.get("AlbumName")?.asString ?: "",
                durationMs = durationSec * 1000,
                albumId = albumId,
                albumAudioId = albumAudioId
            )
        }
    }

    /** 酷狗专辑封面（供搜索/排行榜共用；带内存缓存） */
    fun kugouCover(albumId: String): String {
        kugouCoverCache[albumId]?.let { return it }
        val body = get("http://mobilecdn.kugou.com/api/v3/album/info?albumid=$albumId&plat=0&version=8000")
            ?: return ""
        val img = runCatching {
            gson.fromJson(body, JsonObject::class.java).getAsJsonObject("data")?.get("imgurl")?.asString
        }.getOrNull().orEmpty()
        if (img.isBlank()) return ""
        val cover = img.replace("{size}", "240").replace("http://", "https://")
        kugouCoverCache[albumId] = cover
        return cover
    }

    // ==================== 网易云 ====================

    private suspend fun netease(keyword: String, page: Int, pageSize: Int): List<PlatformSong> {
        val offset = (page - 1) * pageSize
        val url = "https://music.163.com/api/search/get/web?s=${encode(keyword)}&type=1" +
            "&limit=$pageSize&offset=$offset"
        val body = get(url, referer = "https://music.163.com/") ?: return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val songs = root.getAsJsonObject("result")?.getAsJsonArray("songs") ?: return emptyList()
        val items = songs.mapNotNull { el ->
            val o = el.asJsonObject
            val id = o.get("id")?.asString ?: return@mapNotNull null
            val title = o.get("name")?.asString ?: return@mapNotNull null
            val artists = o.getAsJsonArray("artists")?.joinToString("、") { a ->
                a.asJsonObject.get("name")?.asString ?: ""
            } ?: ""
            val albumObj = o.getAsJsonObject("album")
            PlatformSong(
                platform = "wy",
                id = id,
                title = title,
                artist = artists,
                album = albumObj?.get("name")?.asString ?: "",
                durationMs = o.get("duration")?.asLong ?: 0L,
                albumId = albumObj?.get("id")?.asString ?: ""
            )
        }
        val covers = withContext(Dispatchers.IO) { neteaseCovers(items.map { it.id }) }
        return items.map { it.copy(coverUrl = covers[it.id].orEmpty()) }
    }

    /** 批量补封面：/api/song/detail?ids=[...]（公开接口） */
    private suspend fun neteaseCovers(ids: List<String>): Map<String, String> = coroutineScope {
        if (ids.isEmpty()) return@coroutineScope emptyMap()
        ids.chunked(50).map { chunk ->
            async(Dispatchers.IO) {
                val missing = chunk.filter { !neteaseCoverCache.containsKey(it) }
                if (missing.isNotEmpty()) {
                    val idsParam = missing.joinToString(",", "[", "]") { it }
                    val url = "https://music.163.com/api/song/detail?ids=${encode(idsParam)}"
                    val body = get(url, referer = "https://music.163.com/")
                    val root = body?.let { runCatching { gson.fromJson(it, JsonObject::class.java) }.getOrNull() }
                    root?.getAsJsonArray("songs")?.forEach { el ->
                        val o = el.asJsonObject
                        val id = o.get("id")?.asString ?: return@forEach
                        val pic = o.getAsJsonObject("album")?.get("picUrl")?.asString
                            ?.replace("http://", "https://") ?: ""
                        if (pic.isNotBlank()) neteaseCoverCache[id] = pic
                    }
                }
                chunk.associateWith { neteaseCoverCache[it].orEmpty() }
            }
        }.awaitAll().fold(mutableMapOf<String, String>()) { acc, map ->
            acc.putAll(map)
            acc
        }
    }

    // ==================== QQ 音乐 ====================

    private suspend fun qq(keyword: String, page: Int, pageSize: Int): List<PlatformSong> {
        val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=${encode(keyword)}" +
            "&p=$page&n=$pageSize&format=json"
        val body = get(url, referer = "https://y.qq.com/") ?: return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val list = root.getAsJsonObject("data")?.getAsJsonObject("song")?.getAsJsonArray("list")
            ?: return emptyList()
        return list.mapNotNull { el ->
            val o = el.asJsonObject
            val songmid = o.get("songmid")?.asString ?: o.get("songid")?.asString ?: return@mapNotNull null
            val title = o.get("songname")?.asString ?: return@mapNotNull null
            val singer = o.getAsJsonArray("singer")?.joinToString("、") { s ->
                s.asJsonObject.get("name")?.asString ?: ""
            } ?: ""
            val albumMid = o.get("albummid")?.asString ?: ""
            val cover = if (albumMid.isNotBlank()) {
                "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albumMid.jpg"
            } else ""
            PlatformSong(
                platform = "tx",
                id = songmid,
                title = title,
                artist = singer,
                album = o.get("albumname")?.asString ?: "",
                coverUrl = cover,
                durationMs = (o.get("interval")?.asLong ?: 0L) * 1000,
                albumId = o.get("albumid")?.asString ?: "",
                albumMid = albumMid
            )
        }
    }

    // ==================== 酷我音乐 ====================

    private suspend fun kuwo(keyword: String, page: Int, pageSize: Int): List<PlatformSong> {
        val pn = page - 1
        val url = "http://search.kuwo.cn/r.s?all=${encode(keyword)}&ft=music&itemset=web_2013" +
            "&client=kt&pn=$pn&rn=$pageSize&rformat=json&encoding=utf8&vipver=1"
        val body = get(url) ?: return emptyList()
        // 酷我返回的是单引号 JS 对象（非标准 JSON）：按 "},{" 切分后逐段提取
        return body.split("},{").mapNotNull { seg ->
            val rid = Regex("""MUSIC_(\d+)""").find(seg)?.groupValues?.get(1)
                ?: return@mapNotNull null
            val title = field(seg, "NAME") ?: return@mapNotNull null
            val artist = field(seg, "ARTIST").orEmpty()
            val album = field(seg, "ALBUM").orEmpty()
            val duration = field(seg, "DURATION")?.toLongOrNull() ?: 0L
            PlatformSong(
                platform = "kw",
                id = rid,
                title = unescapeHtml(title),
                artist = unescapeHtml(artist),
                album = unescapeHtml(album),
                durationMs = duration * 1000
            )
        }.toList()
    }

    private fun field(block: String, name: String): String? {
        return Regex("""'$name':'([^']*)'""").find(block)?.groupValues?.get(1)
    }

    private fun unescapeHtml(value: String): String =
        value.replace("&nbsp;", " ").replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")
}
