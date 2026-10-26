package com.example.lxmusic.data.search

import android.util.Log
import com.example.lxmusic.BffApi
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.model.SongInfo
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
import java.util.concurrent.TimeUnit

/**
 * 公开歌单广场（不需要登录、不需要服务器）。
 * - 酷狗：m.kugou.com plist 推荐歌单（600 个，30/页），详情用 mobilecdn special/song
 * - 网易云：歌单分类 + playlist/list（按分类热门），详情 playlist/detail
 * - QQ：歌单分类 + fcg_get_diss_by_tag，详情 fcg_ucc_getcdinfo_byids_cp
 */
object PublicPlaylistProviders {

    data class PublicPlaylist(
        val platform: String,
        val id: String,
        val name: String,
        val coverUrl: String = "",
        val playCount: Long = 0L,
        val trackCount: Int = 0,
        val creator: String = ""
    )

    /** 一页歌曲 + 该歌单歌曲总数（total 为 0 表示接口未给出） */
    data class SongPage(val songs: List<SongInfo>, val total: Int)

    /** 一页歌单 + 总数 + 是否还有下一页（官方接口只有 has_next，没有 total） */
    data class PlaylistPage(
        val items: List<PublicPlaylist>,
        val total: Int,
        val hasMore: Boolean
    )

    /** 歌单分类分组（酷狗官方：场景/主题/语种/风格/心情/年代） */
    data class TagGroup(
        val name: String,
        val tags: List<PublicZoneProviders.ZoneCategory>
    )

    /** 乐库场景专区（助眠/车载/心情…），tags 为场景下的风格标签 */
    data class SceneGroup(
        val title: String,
        val label: String,
        val icon: String,
        val tags: List<PublicZoneProviders.ZoneCategory>
    )

    private const val TAG = "PublicPlaylist"
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private fun encode(v: String) = URLEncoder.encode(v, Charsets.UTF_8.name()).replace("+", "%20")

    private fun get(url: String, referer: String? = null): String? = runCatching {
        val builder = Request.Builder().url(url).header("User-Agent", UA)
        if (referer != null) builder.header("Referer", referer)
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "HTTP ${resp.code} ${url.take(100)}")
                null
            } else resp.body?.string()
        }
    }.getOrElse {
        Log.w(TAG, "请求失败 ${it.message}: ${url.take(100)}")
        null
    }

    /** 分类（酷狗公开接口没有分类；官方分类在激活后走 playlistTags） */
    suspend fun categories(platform: String): List<PublicZoneProviders.ZoneCategory> =
        if (platform == "kg") emptyList() else PublicZoneProviders.categories(platform)

    /** 官方酷狗接口是否可用（需要 BFF + 已激活） */
    fun officialKugouAvailable(): Boolean =
        BffApi.onlineAllowed && KuGouApi.bffUrl.isNotBlank()

    private var kgTagGroups: List<TagGroup>? = null
    private var kgScenes: List<SceneGroup>? = null

    /** 酷狗官方歌单分类：场景/主题/语种/风格/心情/年代 */
    suspend fun kugouTags(): List<TagGroup> = withContext(Dispatchers.IO) {
        kgTagGroups?.let { return@withContext it }
        val groups = runCatching {
            KuGouApi.service.getPlaylistTags().data.orEmpty().mapNotNull { g ->
                val tags = g.children.orEmpty()
                    .mapNotNull { t ->
                        if (t.tagName.isBlank() || t.tagId.isBlank()) null
                        else PublicZoneProviders.ZoneCategory(
                            platform = "kg",
                            id = t.tagId,
                            name = t.tagName
                        )
                    }
                if (tags.isEmpty()) null else TagGroup(g.tagName, tags)
            }
        }.getOrElse { emptyList() }
        if (groups.isNotEmpty()) kgTagGroups = groups
        groups
    }

    /** 酷狗乐库场景专区（助眠/车载/心情/工作学习/运动/店铺/游戏/广场舞/疗愈/婚礼/母婴） */
    suspend fun kugouScenes(): List<SceneGroup> = withContext(Dispatchers.IO) {
        kgScenes?.let { return@withContext it }
        val groups = runCatching {
            KuGouApi.service.getSceneLists().data?.list.orEmpty().mapNotNull { s ->
                val sceneIcon = (s.icon ?: s.pic).orEmpty()
                    .replace("{size}", "240").replace("http://", "https://")
                val tags = s.musicTag?.tags.orEmpty()
                    .mapNotNull { t ->
                        val name = t.name ?: return@mapNotNull null
                        if (name.isBlank()) null
                        else PublicZoneProviders.ZoneCategory(
                            platform = "kg",
                            id = t.tagId.toString(),
                            name = name,
                            coverUrl = t.pic.orEmpty()
                                .replace("{size}", "240").replace("http://", "https://")
                                .ifBlank { sceneIcon },
                            group = s.title.orEmpty()
                        )
                    }
                    .distinctBy { it.id }
                if (tags.isEmpty()) null
                else SceneGroup(
                    title = s.title.orEmpty(),
                    label = s.label.orEmpty(),
                    icon = (s.icon ?: s.pic).orEmpty()
                        .replace("{size}", "240").replace("http://", "https://"),
                    tags = tags
                )
            }
        }.getOrElse { emptyList() }
        if (groups.isNotEmpty()) kgScenes = groups
        groups
    }

    /** 专区页可直接使用的扁平分类（group = 场景名，coverUrl = 标签/场景封面） */
    suspend fun kugouSceneCategories(): List<PublicZoneProviders.ZoneCategory> =
        kugouScenes().flatMap { it.tags }

    // ==================== 歌单列表 ====================

    suspend fun playlists(
        platform: String,
        categoryId: String?,
        page: Int,
        pageSize: Int = 30
    ): PlaylistPage = withContext(Dispatchers.IO) {
        if (platform == "kg" && officialKugouAvailable()) {
            // 优先官方分类歌单（激活设备）；失败时回退公开推荐列表
            runCatching { kugouOfficialPlaylists(categoryId, page, pageSize) }
                .getOrNull()
                ?.let { return@withContext it }
        }
        when (platform) {
            "kg" -> kugouPlaylists(page, pageSize)
            "wy" -> neteasePlaylists(categoryId ?: "全部", page, pageSize)
            "tx" -> qqPlaylists(categoryId ?: "10000000", page, pageSize)
            else -> PlaylistPage(emptyList(), 0, false)
        }
    }

    private suspend fun kugouOfficialPlaylists(categoryId: String?, page: Int, pageSize: Int): PlaylistPage {
        val resp = KuGouApi.service.getTopPlaylists(
            categoryId = categoryId?.ifBlank { "0" } ?: "0",
            page = page,
            pageSize = pageSize
        )
        if (resp.status != 1) return PlaylistPage(emptyList(), 0, false)
        val data = resp.data
        val items = data?.list.orEmpty().mapNotNull { it ->
            val name = it.name ?: return@mapNotNull null
            if (name.isBlank() || it.specialId <= 0L) return@mapNotNull null
            PublicPlaylist(
                platform = "kg",
                id = it.specialId.toString(),
                name = name,
                coverUrl = it.imgurl.orEmpty()
                    .replace("{size}", "240").replace("http://", "https://"),
                playCount = it.playCount,
                trackCount = it.songcount,
                creator = it.nickname.orEmpty()
            )
        }
        val total = data?.total ?: 0
        val hasMore = (data?.hasNext ?: 0) == 1
        return PlaylistPage(items, total, hasMore)
    }

    /** 乐库分类页的推荐歌单（官方 top/playlist，按 tag 取） */
    suspend fun kugouGenrePlaylists(
        tagId: String,
        page: Int = 1,
        pageSize: Int = 12
    ): List<PublicPlaylist> = withContext(Dispatchers.IO) {
        runCatching { kugouOfficialPlaylists(tagId, page, pageSize) }
            .getOrNull()?.items.orEmpty()
    }

    private fun kugouPlaylists(page: Int, pageSize: Int): PlaylistPage {
        val body = get("https://m.kugou.com/plist/index?json=true&page=$page")
            ?: return PlaylistPage(emptyList(), 0, false)
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
            ?: return PlaylistPage(emptyList(), 0, false)
        val list = root.getAsJsonObject("plist")?.getAsJsonObject("list")
            ?: return PlaylistPage(emptyList(), 0, false)
        val total = list.get("total")?.asInt ?: 0
        val info = list.getAsJsonArray("info") ?: return PlaylistPage(emptyList(), total, false)
        val items = info.mapNotNull { el ->
            val o = el.asJsonObject
            val id = o.get("specialid")?.asString ?: return@mapNotNull null
            val name = o.get("specialname")?.asString ?: return@mapNotNull null
            PublicPlaylist(
                platform = "kg",
                id = id,
                name = name,
                coverUrl = o.get("imgurl")?.asString.orEmpty()
                    .replace("{size}", "240").replace("http://", "https://"),
                playCount = o.get("playcount")?.asString?.toLongOrNull() ?: 0L,
                trackCount = o.get("songcount")?.asString?.toIntOrNull() ?: 0
            )
        }
        return PlaylistPage(items, total, page * pageSize < total)
    }

    private fun neteasePlaylists(cat: String, page: Int, pageSize: Int): PlaylistPage {
        val offset = (page - 1) * pageSize
        val url = "https://music.163.com/api/playlist/list?cat=${encode(cat)}&offset=$offset&limit=$pageSize&order=hot"
        val body = get(url, referer = "https://music.163.com/")
            ?: return PlaylistPage(emptyList(), 0, false)
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
            ?: return PlaylistPage(emptyList(), 0, false)
        val total = root.get("total")?.asInt ?: 0
        val arr = root.getAsJsonArray("playlists") ?: return PlaylistPage(emptyList(), total, false)
        val items = arr.mapNotNull { el ->
            val o = el.asJsonObject
            val id = o.get("id")?.asString ?: return@mapNotNull null
            val name = o.get("name")?.asString ?: return@mapNotNull null
            PublicPlaylist(
                platform = "wy",
                id = id,
                name = name,
                coverUrl = o.get("coverImgUrl")?.asString.orEmpty().replace("http://", "https://"),
                playCount = o.get("playCount")?.asLong ?: 0L,
                trackCount = o.get("trackCount")?.asInt ?: 0,
                creator = o.getAsJsonObject("creator")?.get("nickname")?.asString.orEmpty()
            )
        }
        return PlaylistPage(items, total, offset + items.size < total)
    }

    private fun qqPlaylists(categoryId: String, page: Int, pageSize: Int): PlaylistPage {
        val sin = (page - 1) * pageSize
        val url = "https://c.y.qq.com/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg" +
            "?categoryId=$categoryId&sortId=5&sin=$sin&ein=${sin + pageSize}&format=json&g_tk=5381" +
            "&loginUin=0&hostUin=0&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
        val body = get(url, referer = "https://y.qq.com/n/ryqq/playlist")
            ?: return PlaylistPage(emptyList(), 0, false)
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
            ?: return PlaylistPage(emptyList(), 0, false)
        val data = root.getAsJsonObject("data") ?: return PlaylistPage(emptyList(), 0, false)
        val total = data.get("sum")?.asInt ?: 0
        val arr = data.getAsJsonArray("list") ?: return PlaylistPage(emptyList(), total, false)
        val items = arr.mapNotNull { el ->
            val o = el.asJsonObject
            val id = o.get("dissid")?.asString ?: return@mapNotNull null
            val name = o.get("dissname")?.asString ?: return@mapNotNull null
            PublicPlaylist(
                platform = "tx",
                id = id,
                name = name,
                coverUrl = o.get("imgurl")?.asString.orEmpty().replace("http://", "https://"),
                playCount = o.get("listennum")?.asLong ?: 0L,
                trackCount = o.get("song_count")?.asInt ?: 0,
                creator = o.get("nickname")?.asString.orEmpty()
            )
        }
        return PlaylistPage(items, total, sin + items.size < total)
    }

    // ==================== 歌单歌曲 ====================

    /** 全量加载（用于「播放全部」）：酷狗/网易云按页拉完，QQ 一次返回 */
    suspend fun songs(platform: String, playlistId: String): List<SongInfo> = withContext(Dispatchers.IO) {
        when (platform) {
            "kg" -> kugouSongs(playlistId)
            "wy" -> neteaseSongs(playlistId)
            "tx" -> qqSongs(playlistId)
            else -> emptyList()
        }
    }

    /** 分页加载（详情页下滑加载更多）：kg/wy 走接口分页，tx 全量缓存后本地切片 */
    suspend fun songsPage(
        platform: String,
        playlistId: String,
        page: Int,
        pageSize: Int = 30
    ): SongPage = withContext(Dispatchers.IO) {
        when (platform) {
            "kg" -> kugouSongsPage(playlistId, page, pageSize)
            "wy" -> neteaseSongsPage(playlistId, page, pageSize)
            "tx" -> qqSongsPage(playlistId, page, pageSize)
            else -> SongPage(emptyList(), 0)
        }
    }

    private suspend fun kugouSongsPage(specialId: String, page: Int, pageSize: Int): SongPage {
        val body = get(
            "http://mobilecdn.kugou.com/api/v3/special/song?specialid=$specialId&page=$page&pagesize=$pageSize"
        ) ?: return SongPage(emptyList(), 0)
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
            ?: return SongPage(emptyList(), 0)
        val data = root.getAsJsonObject("data") ?: return SongPage(emptyList(), 0)
        val total = data.get("total")?.asInt ?: 0
        val info = data.getAsJsonArray("info") ?: return SongPage(emptyList(), total)
        val raw = mutableListOf<Pair<SongInfo, String>>()
        info.forEach { el ->
            parseKugouSong(el.asJsonObject)?.let { raw += it }
        }
        // 只给本页补封面（并行 + 缓存），避免一次歌单几百个专辑请求
        val withCovers = coroutineScope {
            raw.map { (song, albumId) ->
                async(Dispatchers.IO) {
                    if (albumId.isBlank()) song
                    else song.copy(albumArtUri = PublicSearchProviders.kugouCover(albumId))
                }
            }.awaitAll()
        }
        return SongPage(withCovers, total)
    }

    private fun parseKugouSong(o: JsonObject): Pair<SongInfo, String>? {
        val hash = o.get("hash")?.asString ?: return null
        val audioId = o.get("album_audio_id")?.asString ?: "0"
        val filename = o.get("filename")?.asString.orEmpty()
        val title = o.get("songname")?.asString?.takeIf { it.isNotBlank() && it != "null" }
            ?: filename.substringAfter(" - ", filename)
        val artist = o.get("singername")?.asString?.takeIf { it.isNotBlank() && it != "null" }
            ?: filename.substringBefore(" - ", "")
        val albumId = o.get("album_id")?.asString ?: ""
        return SongInfo(
            title = title,
            artist = artist,
            filePath = "$hash|$audioId",
            albumArtUri = null,
            duration = (o.get("duration")?.asLong ?: 0L) * 1000
        ) to albumId
    }

    /** 全量（「播放全部」用）：按页拉完，最多 40 页（1200 首）兜底 */
    private suspend fun kugouSongs(specialId: String): List<SongInfo> {
        val all = mutableListOf<SongInfo>()
        var page = 1
        var total = Int.MAX_VALUE
        while (all.size < total && page <= 40) {
            val p = kugouSongsPage(specialId, page, 30)
            if (p.songs.isEmpty()) break
            all += p.songs
            if (p.total > 0) total = p.total
            page++
        }
        return all.distinctBy { it.filePath }
    }

    private val neteaseTrackIdsCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    /** 歌单完整 trackIds（v6 detail 只回前 10 首，但带全部 id），按歌单缓存 */
    private fun neteaseTrackIds(id: String): List<String> {
        neteaseTrackIdsCache[id]?.let { return it }
        val ids = runCatching {
            val body = get(
                "https://music.163.com/api/v6/playlist/detail?id=$id&n=1000",
                referer = "https://music.163.com/"
            ) ?: return@runCatching emptyList<String>()
            val root = gson.fromJson(body, JsonObject::class.java)
            val trackIds = root.getAsJsonObject("playlist")?.getAsJsonArray("trackIds")
                ?: return@runCatching emptyList<String>()
            (0 until trackIds.size()).mapNotNull { i ->
                trackIds.get(i).asJsonObject.get("id")?.asString
            }
        }.getOrElse { emptyList() }
        if (ids.isNotEmpty()) neteaseTrackIdsCache[id] = ids
        return ids
    }

    private fun fetchNeteaseDetails(ids: List<String>): List<SongInfo> {
        val result = mutableListOf<SongInfo>()
        for (chunk in ids.chunked(50)) {
            val idsParam = chunk.joinToString(",", "[", "]")
            val body = get(
                "https://music.163.com/api/song/detail?ids=${encode(idsParam)}",
                referer = "https://music.163.com/"
            ) ?: continue
            val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
                ?: continue
            val songs = root.getAsJsonArray("songs") ?: continue
            songs.forEach { el ->
                val o = el.asJsonObject
                val sid = o.get("id")?.asString ?: return@forEach
                val title = o.get("name")?.asString ?: return@forEach
                val artist = o.getAsJsonArray("artists")?.joinToString("、") { a ->
                    a.asJsonObject.get("name")?.asString ?: ""
                } ?: o.getAsJsonArray("ar")?.joinToString("、") { a ->
                    a.asJsonObject.get("name")?.asString ?: ""
                } ?: ""
                val album = o.getAsJsonObject("album") ?: o.getAsJsonObject("al")
                result += SongInfo(
                    title = title,
                    artist = artist,
                    filePath = "netease://$sid",
                    albumArtUri = (album?.get("picUrl")?.asString ?: album?.get("blurPicUrl")?.asString)
                        ?.replace("http://", "https://"),
                    duration = o.get("duration")?.asLong ?: o.get("dt")?.asLong ?: 0L
                )
            }
        }
        // 接口返回顺序不保证，按请求顺序还原
        val byId = result.associateBy { it.filePath.removePrefix("netease://") }
        return ids.mapNotNull { byId[it] }
    }

    private fun neteaseSongsPage(id: String, page: Int, pageSize: Int): SongPage {
        val ids = neteaseTrackIds(id)
        if (ids.isEmpty()) return SongPage(emptyList(), 0)
        val from = ((page - 1).coerceAtLeast(0)) * pageSize
        if (from >= ids.size) return SongPage(emptyList(), ids.size)
        val slice = ids.subList(from, minOf(from + pageSize, ids.size))
        return SongPage(fetchNeteaseDetails(slice), ids.size)
    }

    private fun neteaseSongs(id: String): List<SongInfo> {
        val ids = neteaseTrackIds(id)
        if (ids.isNotEmpty()) {
            val result = fetchNeteaseDetails(ids)
            if (result.isNotEmpty()) return result.distinctBy { it.filePath }
        }
        // 兜底：playlist/detail
        val body = get(
            "https://music.163.com/api/playlist/detail?id=$id",
            referer = "https://music.163.com/"
        ) ?: return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val tracks = root.getAsJsonObject("result")?.getAsJsonArray("tracks") ?: return emptyList()
        return tracks.mapNotNull { el ->
            val o = el.asJsonObject
            val sid = o.get("id")?.asString ?: return@mapNotNull null
            val title = o.get("name")?.asString ?: return@mapNotNull null
            val artist = o.getAsJsonArray("artists")?.joinToString("、") { a ->
                a.asJsonObject.get("name")?.asString ?: ""
            } ?: ""
            val album = o.getAsJsonObject("album")
            SongInfo(
                title = title,
                artist = artist,
                filePath = "netease://$sid",
                albumArtUri = (album?.get("picUrl")?.asString ?: album?.get("blurPicUrl")?.asString)
                    ?.replace("http://", "https://"),
                duration = o.get("duration")?.asLong ?: 0L
            )
        }
    }

    private val qqSongsCache = java.util.concurrent.ConcurrentHashMap<String, List<SongInfo>>()

    private fun qqSongs(id: String): List<SongInfo> {
        qqSongsCache[id]?.let { return it }
        val url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg" +
            "?type=1&json=1&utf8=1&onlysong=0&disstid=$id&format=json&g_tk=5381"
        val body = get(url, referer = "https://y.qq.com/") ?: return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val cd = root.getAsJsonArray("cdlist")?.firstOrNull()?.asJsonObject ?: return emptyList()
        val songlist = cd.getAsJsonArray("songlist") ?: return emptyList()
        val list = songlist.mapNotNull { el ->
            val o = el.asJsonObject
            val songmid = o.get("songmid")?.asString ?: return@mapNotNull null
            val title = o.get("songname")?.asString ?: return@mapNotNull null
            val artist = o.getAsJsonArray("singer")?.joinToString("、") { s ->
                s.asJsonObject.get("name")?.asString ?: ""
            } ?: ""
            val albummid = o.get("albummid")?.asString ?: ""
            SongInfo(
                title = title,
                artist = artist,
                filePath = "tx://$songmid" + if (albummid.isNotBlank()) "?albummid=$albummid" else "",
                albumArtUri = if (albummid.isNotBlank())
                    "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albummid.jpg" else null,
                duration = (o.get("interval")?.asLong ?: 0L) * 1000
            )
        }
        if (list.isNotEmpty()) qqSongsCache[id] = list
        return list
    }

    /** QQ 一次返回全量，缓存后本地切片，避免每次翻页重复请求 */
    private fun qqSongsPage(id: String, page: Int, pageSize: Int): SongPage {
        val all = qqSongs(id)
        if (all.isEmpty()) return SongPage(emptyList(), 0)
        val from = ((page - 1).coerceAtLeast(0)) * pageSize
        if (from >= all.size) return SongPage(emptyList(), all.size)
        return SongPage(all.subList(from, minOf(from + pageSize, all.size)), all.size)
    }
}
