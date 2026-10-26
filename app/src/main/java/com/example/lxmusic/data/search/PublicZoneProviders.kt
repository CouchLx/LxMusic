package com.example.lxmusic.data.search

import android.util.Log
import com.example.lxmusic.model.SongInfo
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 公开专区（分类）数据源：不需要登录、不需要自建服务器。
 * - 酷狗：沿用原有 39 个专区名称/封面，播放时用公开搜索按专区名取歌，随机 50 首
 * - 网易云：歌单分类（华语/流行/摇滚/电子…），取该分类热门歌单的歌曲，随机 50 首
 * - QQ：歌单分类（语种/风格/场景…），取该分类热门歌单的歌曲，随机 50 首
 *
 * 只提供元数据；播放地址仍由落雪插件或内置链路解析。
 */
object PublicZoneProviders {

    data class ZoneCategory(
        val platform: String,
        val id: String,
        val name: String,
        val coverUrl: String = "",
        /** 分组名（QQ 用于显示 语种/流派/主题/心情/场景 小标题） */
        val group: String = ""
    )

    private data class CacheEntry<T>(val at: Long, val value: T)
    private const val TTL = 5 * 60 * 1000L

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val categoriesCache = ConcurrentHashMap<String, CacheEntry<List<ZoneCategory>>>()
    private val coverCache = ConcurrentHashMap<String, String>()

    /** 酷狗原有专区（名称 + 封面 + ipId 标识，播放用搜索实现） */
    val KUGOU_ZONES: List<ZoneCategory> = listOf(
        ZoneCategory("kg", "DJ", "DJ", "https://imgessl.kugou.com/quickentry/20230704/20230704233204541061.jpg"),
        ZoneCategory("kg", "抖音", "抖音", "https://imgessl.kugou.com/quickentry/20230704/20230704233232980023.jpg"),
        ZoneCategory("kg", "经典老歌", "经典老歌", "https://imgessl.kugou.com/quickentry/20230704/20230704235426413521.jpg"),
        ZoneCategory("kg", "儿童", "儿童", "https://imgessl.kugou.com/quickentry/20230704/20230704232229905515.jpg"),
        ZoneCategory("kg", "车载", "车载", "https://imgessl.kugou.com/quickentry/20230704/20230704232328671616.jpg"),
        ZoneCategory("kg", "纯音乐", "纯音乐", "https://imgessl.kugou.com/quickentry/20230704/20230704224558479280.jpg"),
        ZoneCategory("kg", "广场舞", "广场舞", "https://imgessl.kugou.com/quickentry/20230704/20230704224319462864.jpg"),
        ZoneCategory("kg", "网络", "网络", "https://imgessl.kugou.com/quickentry/20230704/20230704223003901012.jpg"),
        ZoneCategory("kg", "粤语", "粤语", "https://imgessl.kugou.com/quickentry/20230704/20230704222858744159.jpg"),
        ZoneCategory("kg", "80后", "80后", "https://imgessl.kugou.com/quickentry/20230704/20230704222656156172.jpg"),
        ZoneCategory("kg", "90后", "90后", "https://imgessl.kugou.com/quickentry/20230704/20230704222623938557.jpg"),
        ZoneCategory("kg", "欧美", "欧美", "https://imgessl.kugou.com/quickentry/20230704/20230704223719978571.jpg"),
        ZoneCategory("kg", "国风", "国风", "https://imgessl.kugou.com/quickentry/20230704/20230704222315324865.jpg"),
        ZoneCategory("kg", "商铺", "商铺", "https://imgessl.kugou.com/quickentry/20230704/20230704222027689293.jpg"),
        ZoneCategory("kg", "伤感", "伤感", "https://imgessl.kugou.com/quickentry/20230704/20230704221932706154.jpg"),
        ZoneCategory("kg", "放松", "放松", "https://imgessl.kugou.com/quickentry/20230704/20230704220928163940.jpg"),
        ZoneCategory("kg", "70后", "70后", "https://imgessl.kugou.com/quickentry/20230704/20230704220631916317.jpg"),
        ZoneCategory("kg", "快乐", "快乐", "https://imgessl.kugou.com/quickentry/20230704/20230704220513691102.jpg"),
        ZoneCategory("kg", "睡前", "睡前", "https://imgessl.kugou.com/quickentry/20230704/20230704220413666681.jpg"),
        ZoneCategory("kg", "闽南语", "闽南语", "https://imgessl.kugou.com/quickentry/20230704/20230704215606692913.jpg"),
        ZoneCategory("kg", "电音", "电音", "https://imgessl.kugou.com/quickentry/20230704/20230704215446482578.jpg"),
        ZoneCategory("kg", "K歌必听", "K歌必听", "https://imgessl.kugou.com/quickentry/20230704/20230704215346999862.jpg"),
        ZoneCategory("kg", "健身", "健身", "https://imgessl.kugou.com/quickentry/20230704/20230704221436255868.jpg"),
        ZoneCategory("kg", "影视", "影视", "https://imgessl.kugou.com/quickentry/20230704/20230704215016764606.jpg"),
        ZoneCategory("kg", "游戏", "游戏", "https://imgessl.kugou.com/quickentry/20230704/20230704214615547769.jpg"),
        ZoneCategory("kg", "韩流", "韩流", "https://imgessl.kugou.com/quickentry/20230704/20230704213846185586.jpg"),
        ZoneCategory("kg", "咖啡馆", "咖啡馆", "https://imgessl.kugou.com/quickentry/20230704/20230704213343318463.jpg"),
        ZoneCategory("kg", "00后", "00后", "https://imgessl.kugou.com/quickentry/20230704/20230704212744775191.jpg"),
        ZoneCategory("kg", "解压", "解压", "https://imgessl.kugou.com/quickentry/20230704/20230704212607857996.jpg"),
        ZoneCategory("kg", "甜蜜", "甜蜜", "https://imgessl.kugou.com/quickentry/20230704/20230704212112107046.jpg"),
        ZoneCategory("kg", "舞曲", "舞曲", "https://imgessl.kugou.com/quickentry/20230704/20230704190657380671.jpg"),
        ZoneCategory("kg", "国语", "国语", "https://imgessl.kugou.com/quickentry/20230704/20230704190506111298.jpg"),
        ZoneCategory("kg", "戏曲", "戏曲", "https://imgessl.kugou.com/quickentry/20230704/20230704190205555372.jpg"),
        ZoneCategory("kg", "民谣", "民谣", "https://imgessl.kugou.com/quickentry/20230704/20230704175555940771.jpg"),
        ZoneCategory("kg", "流行", "流行", "https://imgessl.kugou.com/quickentry/20230704/20230704185939164073.jpg"),
        ZoneCategory("kg", "综艺专区", "综艺专区", "https://imgessl.kugou.com/quickentry/20220310/20220310175729970544.jpg"),
        ZoneCategory("kg", "首发专区", "首发专区", "https://imgessl.kugou.com/quickentry/20220310/20220310175942769122.jpg"),
        ZoneCategory("kg", "厂牌音乐", "厂牌音乐", "https://imgessl.kugou.com/quickentry/20221013/20221013152725767975.png"),
        ZoneCategory("kg", "情歌", "情歌", "https://imgessl.kugou.com/quickentry/20230704/20230704224425990324.jpg")
    )

    private fun encode(v: String) = URLEncoder.encode(v, Charsets.UTF_8.name()).replace("+", "%20")

    private fun get(url: String, referer: String? = null): String? = runCatching {
        val builder = Request.Builder().url(url).header("User-Agent", UA)
        if (referer != null) builder.header("Referer", referer)
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w("PublicZone", "HTTP ${resp.code} ${url.take(100)}")
                null
            } else resp.body?.string()
        }
    }.getOrElse {
        Log.w("PublicZone", "请求失败 ${it.message}: ${url.take(100)}")
        null
    }

    // ==================== 分类列表 ====================

    suspend fun categories(platform: String): List<ZoneCategory> = withContext(Dispatchers.IO) {
        val cachedEntry = categoriesCache[platform]
        if (cachedEntry != null && System.currentTimeMillis() - cachedEntry.at < TTL) {
            return@withContext cachedEntry.value
        }
        val list = when (platform) {
            "kg" -> KUGOU_ZONES
            "wy" -> neteaseCategories()
            "tx" -> qqCategories()
            else -> emptyList()
        }
        if (list.isNotEmpty()) categoriesCache[platform] = CacheEntry(System.currentTimeMillis(), list)
        list
    }

    private fun neteaseCategories(): List<ZoneCategory> {
        val body = get("https://music.163.com/api/playlist/catalogue", referer = "https://music.163.com/")
            ?: return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val sub = root.getAsJsonArray("sub") ?: return emptyList()
        return sub.mapNotNull { el ->
            val o = el.asJsonObject
            val name = o.get("name")?.asString ?: return@mapNotNull null
            ZoneCategory("wy", name, name)
        }
    }

    private fun qqCategories(): List<ZoneCategory> {
        val body = get(
            "https://c.y.qq.com/splcloud/fcgi-bin/fcg_get_diss_tag_conf.fcg?format=json&g_tk=5381",
            referer = "https://y.qq.com/"
        ) ?: return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val groups = root.getAsJsonObject("data")?.getAsJsonArray("categories") ?: return emptyList()
        val result = mutableListOf<ZoneCategory>()
        groups.forEach { g ->
            val items = g.asJsonObject.getAsJsonArray("items") ?: return@forEach
            val groupName = g.asJsonObject.get("categoryGroupName")?.asString.orEmpty()
            items.forEach { it2 ->
                val o = it2.asJsonObject
                val id = o.get("categoryId")?.asString ?: return@forEach
                val name = o.get("categoryName")?.asString ?: return@forEach
                if (name != "全部") result += ZoneCategory("tx", id, name, group = groupName)
            }
        }
        return result.distinctBy { it.id }
    }

    // ==================== 分类封面（懒加载） ====================

    suspend fun coverFor(platform: String, category: ZoneCategory): String = withContext(Dispatchers.IO) {
        val key = "${platform}_${category.id}"
        coverCache[key]?.let { return@withContext it }
        val cover = when (platform) {
            "wy" -> neteasePlaylists(category.name, 1).firstOrNull()?.get("coverImgUrl")?.asString.orEmpty()
            "tx" -> qqPlaylists(category.id, 1).firstOrNull()?.get("imgurl")?.asString.orEmpty()
            else -> category.coverUrl
        }.replace("http://", "https://")
        if (cover.isNotBlank()) coverCache[key] = cover
        cover
    }

    // ==================== 随机 50 首 ====================

    suspend fun randomSongs(platform: String, category: ZoneCategory, limit: Int = 50): List<SongInfo> =
        withContext(Dispatchers.IO) {
            val all = when (platform) {
                "kg" -> kugouZoneSongs(category.name)
                "wy" -> neteaseZoneSongs(category.name)
                "tx" -> qqZoneSongs(category.id)
                else -> emptyList()
            }
            if (all.isEmpty()) emptyList() else all.shuffled().take(limit)
        }

    /** 酷狗：用公开搜索按专区名取歌（官方 ip_zone 接口需要服务端签名，公开不可用） */
    private suspend fun kugouZoneSongs(name: String): List<SongInfo> {
        val first = PublicSearchProviders.search("kg", name, 1, 30).map { it.toSongInfo() }
        val second = runCatching { PublicSearchProviders.search("kg", name, 2, 30) }
            .getOrElse { emptyList() }.map { it.toSongInfo() }
        return (first + second).distinctBy { it.filePath }
    }

    private fun neteasePlaylists(cat: String, limit: Int): List<JsonObject> {
        val url = "https://music.163.com/api/playlist/list?cat=${encode(cat)}&offset=0&limit=$limit&order=hot"
        val body = get(url, referer = "https://music.163.com/") ?: return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val arr = root.getAsJsonArray("playlists") ?: return emptyList()
        return arr.mapNotNull { it.asJsonObject }
    }

    private fun neteaseZoneSongs(cat: String): List<SongInfo> {
        // 取该分类最热的几个歌单合并，保证能凑够 50 首（单歌单可能只有 10~30 首）
        val playlists = neteasePlaylists(cat, 10)
            .sortedByDescending { it.get("trackCount")?.asInt ?: 0 }
        val result = mutableListOf<SongInfo>()
        for (playlist in playlists) {
            if (result.size >= 60) break
            val id = playlist.get("id")?.asString ?: continue
            result += neteasePlaylistSongs(id)
        }
        return result.distinctBy { it.filePath }
    }

    private fun neteasePlaylistSongs(id: String): List<SongInfo> {
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
            val artist = o.getAsJsonArray("ar")?.joinToString("、") { a ->
                a.asJsonObject.get("name")?.asString ?: ""
            } ?: o.getAsJsonArray("artists")?.joinToString("、") { a ->
                a.asJsonObject.get("name")?.asString ?: ""
            } ?: ""
            val album = o.getAsJsonObject("al") ?: o.getAsJsonObject("album")
            SongInfo(
                title = title,
                artist = artist,
                filePath = "netease://$sid",
                albumArtUri = (album?.get("picUrl")?.asString ?: album?.get("blurPicUrl")?.asString)
                    ?.replace("http://", "https://"),
                duration = o.get("dt")?.asLong ?: o.get("duration")?.asLong ?: 0L
            )
        }
    }

    private fun qqPlaylists(categoryId: String, limit: Int): List<JsonObject> {
        val url = "https://c.y.qq.com/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg" +
            "?categoryId=$categoryId&sortId=5&sin=0&ein=$limit&format=json&g_tk=5381" +
            "&loginUin=0&hostUin=0&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
        val body = get(url, referer = "https://y.qq.com/n/ryqq/playlist") ?: return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val arr = root.getAsJsonObject("data")?.getAsJsonArray("list") ?: return emptyList()
        return arr.mapNotNull { it.asJsonObject }
    }

    private fun qqZoneSongs(categoryId: String): List<SongInfo> {
        val playlists = qqPlaylists(categoryId, 10)
            .sortedByDescending { it.get("song_count")?.asInt ?: 0 }
        val result = mutableListOf<SongInfo>()
        for (playlist in playlists) {
            if (result.size >= 60) break
            val disstid = playlist.get("dissid")?.asString ?: continue
            result += qqPlaylistSongs(disstid)
        }
        return result.distinctBy { it.filePath }
    }

    private fun qqPlaylistSongs(disstid: String): List<SongInfo> {
        val url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg" +
            "?type=1&json=1&utf8=1&onlysong=0&disstid=$disstid&format=json&g_tk=5381"
        val body = get(url, referer = "https://y.qq.com/") ?: return emptyList()
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val cd = root.getAsJsonArray("cdlist")?.firstOrNull()?.asJsonObject ?: return emptyList()
        val songlist = cd.getAsJsonArray("songlist") ?: return emptyList()
        return songlist.mapNotNull { el ->
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
    }
}
