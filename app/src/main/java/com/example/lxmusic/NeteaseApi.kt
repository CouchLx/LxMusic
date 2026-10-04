package com.example.lxmusic

import com.example.lxmusic.model.SongInfo
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ==================== 网易云 DTO ====================
// 接口按 D:\Porject\Android\网易云api.txt（NeteaseCloudMusicAPI Enhanced）文档实现，
// 字段以 2026-09-12 对 39.96.75.20:3001 实测 JSON 为准。

/** /cloudsearch 单曲搜索响应 */
data class NeteaseCloudSearchResponse(
    val result: NeteaseSearchResult? = null,
    val code: Int = 0
)

data class NeteaseSearchResult(
    val songs: List<NeteaseSong>? = null,
    val songCount: Int = 0
)

/** 搜索结果中的单曲 */
data class NeteaseSong(
    val id: Long = 0,
    val name: String? = null,
    val ar: List<NeteaseArtist>? = null,
    val al: NeteaseAlbum? = null,
    val dt: Long = 0,               // 时长（毫秒）
    val fee: Int = 0                // 0=免费 1=单曲收费 8=会员
) {
    val artist: String
        get() = ar?.joinToString("/") { it.name ?: "" }.orEmpty().ifBlank { "未知艺术家" }
    val coverUrl: String
        get() = al?.picUrl?.let { if (it.contains("?")) it else "$it?param=300y300" } ?: ""
}

data class NeteaseArtist(val name: String? = null)

data class NeteaseAlbum(
    val picUrl: String? = null,
    val name: String? = null
)

/** /song/url/v1 播放地址响应 */
data class NeteaseSongUrlResponse(
    val code: Int = 0,
    val data: List<NeteaseSongUrlData>? = null
)

data class NeteaseSongUrlData(
    val id: Long = 0,
    val url: String? = null,
    val type: String? = null,       // flac / mp3 / ...
    val level: String? = null,
    val fee: Int = 0,
    // 实测服务端把 null 序列化为字符串 "null"，有试听时是对象；
    // 用 JsonElement 接收两种形态，避免 Gson 抛类型不匹配
    val freeTrialInfo: com.google.gson.JsonElement? = null
) {
    /** 是否为试听片段（30 秒） */
    val isTrial: Boolean
        get() = freeTrialInfo != null && freeTrialInfo.isJsonObject
}

/** /lyric 歌词响应（yrc 为逐字歌词，不一定存在；lrc 为标准 LRC） */
data class NeteaseLyricResponse(
    val lrc: NeteaseLyricPart? = null,
    val yrc: NeteaseLyricPart? = null,
    val ytlrc: NeteaseLyricPart? = null,
    val tlyric: NeteaseLyricPart? = null,
    val code: Int = 0
)

data class NeteaseLyricPart(
    val version: Int = 0,
    val lyric: String? = null
)

// ==================== Retrofit 接口 ====================
// 本轮只实现单曲搜索闭环；登录/歌单/榜单等留待后续按文档逐个接入

interface NeteaseService {
    /** 单曲搜索（type=1），文档：keywords 必选，limit 默认 30，offset 分页 */
    @GET("cloudsearch")
    suspend fun search(
        @Query("keywords") keywords: String,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
        @Query("type") type: Int = 1
    ): NeteaseCloudSearchResponse

    /** 获取播放地址，level: standard/higher/exhigh/lossless/hires；unblock 解灰，randomCNIP 防 460 */
    @GET("song/url/v1")
    suspend fun getSongUrl(
        @Query("id") id: Long,
        @Query("level") level: String,
        @Query("unblock") unblock: Boolean = true,
        @Query("randomCNIP") randomCNIP: Boolean = true
    ): NeteaseSongUrlResponse

    /** 歌词（lrc 标准歌词 + yrc 逐字歌词） */
    @GET("lyric")
    suspend fun getLyric(
        @Query("id") id: Long
    ): NeteaseLyricResponse

    // ========== 后续扩展（按网易云api.txt 文档） ==========
    // @GET("playlist/track/all") 歌单全部歌曲
    // @GET("toplist/detail") 所有榜单
    // @GET("top/list") 榜单歌曲
    // @GET("login/qr/key|create|check") 二维码登录
    // @GET("like/v1") / @GET("likelist") 红心
    // @GET("recommend/songs") 每日推荐（需登录）
}

// ==================== 单例 ====================

object NeteaseApi {
    /** 网易云 API 默认端口（酷狗 3000 → 网易云 3001） */
    const val DEFAULT_PORT = 3001

    // 服务器地址由应用启动时设置：优先用户自定义（setting prefs "netease_server_url"），
    // 否则自动跟随酷狗服务器（同一主机 + 3001 端口）
    @Volatile
    var baseUrl: String = "http://your-server:3001/"

    private var _cookieStore: MutableMap<String, String>? = null

    /** 从酷狗服务器地址推导网易云地址（同一主机换端口 3001） */
    fun deriveDefaultBaseUrl(): String {
        return try {
            val kugou = KuGouApi.baseUrl.ifBlank { return "http://your-server:3001/" }
            val url = java.net.URI(kugou)
            val host = url.host
            if (host.isNullOrBlank()) "http://your-server:3001/"
            else "${if (url.scheme == "https") "https" else "http"}://$host:$DEFAULT_PORT/"
        } catch (_: Exception) {
            "http://your-server:3001/"
        }
    }

    /** 当前生效的网易云地址：自定义优先，否则自动跟随酷狗 */
    fun effectiveBaseUrl(): String {
        val saved = neteaseServerUrlPref
        return if (!saved.isNullOrBlank()) saved else deriveDefaultBaseUrl()
    }

    // 自定义地址仅由设置页写入，这里缓存一份避免每次读取 SharedPreferences
    @Volatile
    private var neteaseServerUrlPref: String? = null
    fun setCustomServerUrl(url: String?) {
        neteaseServerUrlPref = url
    }

    /** 重建 API 服务（服务器地址变更后调用） */
    fun rebuildService() {
        _service = null
        _cookieStore?.clear()
        android.util.Log.d("LxMusic", "网易云 API 服务已重建，baseUrl=$baseUrl")
    }

    private var _service: NeteaseService? = null
    val service: NeteaseService
        get() = _service ?: createService().also { _service = it }

    // ==================== 工具 ====================

    /** 是否为网易云歌曲路径（netease://<songId>） */
    fun isNeteasePath(path: String): Boolean = path.startsWith("netease://")

    /** 从 netease://<songId> 提取歌曲 id */
    fun neteaseSongIdOf(path: String): Long? = path.removePrefix("netease://").toLongOrNull()

    /** 酷狗音质设置 → 网易云 level 降级链（flac→lossless, 320→exhigh, 128→higher, high→lossless） */
    fun qualityLevels(): List<String> {
        return when (KuGouApi.audioQuality) {
            "flac" -> listOf("lossless", "exhigh", "higher", "standard")
            "320" -> listOf("exhigh", "higher", "standard")
            "128" -> listOf("higher", "standard")
            "high" -> listOf("lossless", "exhigh", "higher", "standard")
            else -> listOf("lossless", "exhigh", "higher", "standard")
        }
    }

    /** level → 码率（服务端不返回 br，按 level 估算，仅用于 UI 徽章显示） */
    fun bitrateOf(level: String?): Int = when (level) {
        "hires" -> 999000
        "lossless" -> 1411200
        "exhigh" -> 320000
        "higher" -> 192000
        "standard" -> 128000
        else -> 0
    }

    private fun createService(): NeteaseService {
        val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor { message ->
            android.util.Log.d("LxMusic_Netease", message)
        }.apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        }

        // 简易 CookieJar：网易云登录态 cookie（MUSIC_U 等）由登录接口回写，为后续登录留位
        val cookieStore = mutableMapOf<String, String>()
        this._cookieStore = cookieStore
        val cookieJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookies.forEach { cookie ->
                    if (cookie.value.isNotBlank()) {
                        cookieStore[cookie.name] = cookie.value
                    } else {
                        cookieStore.remove(cookie.name)
                    }
                }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore.filter { it.value.isNotBlank() }.map { (name, value) ->
                    Cookie.Builder()
                        .domain(url.host)
                        .path("/")
                        .name(name)
                        .value(value)
                        .build()
                }
            }
        }

        val client = okhttp3.OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NeteaseService::class.java)
    }
}

/** 网易云搜索结果 → 播放队列 SongInfo（filePath = "netease://<id>"） */
fun NeteaseSong.toSongInfo() = SongInfo(
    title = name ?: "未知歌曲",
    artist = artist,
    filePath = "netease://$id",
    albumArtUri = coverUrl,
    duration = dt
)