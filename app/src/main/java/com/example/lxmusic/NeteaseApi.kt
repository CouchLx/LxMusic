package com.example.lxmusic

import com.example.lxmusic.model.SongInfo
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

/**
 * 单曲。
 *
 * 网易云有两套歌曲结构：新版是 `ar`/`al`/`dt`（搜索、每日推荐），
 * 私人漫游 `/personal/fm/mode` 返回的是旧版 `artists`/`album`/`duration`，
 * 所以两个字段名都收（alternate 是 Gson 的备用名）。
 */
data class NeteaseSong(
    val id: Long = 0,
    val name: String? = null,
    @com.google.gson.annotations.SerializedName(value = "ar", alternate = ["artists"])
    val ar: List<NeteaseArtist>? = null,
    @com.google.gson.annotations.SerializedName(value = "al", alternate = ["album", "albumInfo"])
    val al: NeteaseAlbum? = null,
    @com.google.gson.annotations.SerializedName(value = "dt", alternate = ["duration"])
    val dt: Long = 0,               // 时长（毫秒）
    val fee: Int = 0,               // 0=免费 1=单曲收费 8=会员
    // 少数推荐接口把封面挂在顶层
    val picUrl: String? = null
) {
    val artist: String
        get() = ar?.joinToString("/") { it.name ?: "" }.orEmpty().ifBlank { "未知艺术家" }
    val coverUrl: String
        get() = (al?.picUrl ?: picUrl)?.trim()?.takeIf { it.isNotBlank() }?.let { raw ->
            // 旧接口常给 http://，Android 9+ 默认禁明文，统一升到 https
            val url = if (raw.startsWith("http://")) "https://" + raw.removePrefix("http://") else raw
            if (url.contains("?")) url else "$url?param=300y300"
        } ?: ""
}

data class NeteaseArtist(val name: String? = null)

data class NeteaseAlbum(
    val picUrl: String? = null,
    val name: String? = null
)

/** /recommend/songs 每日推荐歌曲响应（需登录），歌曲结构复用 NeteaseSong */
data class NeteaseRecommendResponse(
    val code: Int = 0,
    val data: NeteaseRecommendData? = null
)

data class NeteaseRecommendData(
    @com.google.gson.annotations.SerializedName("dailySongs")
    val dailySongs: List<NeteaseSong>? = null
)

/**
 * /personal/fm/mode 私人漫游响应。
 *
 * 服务端把歌曲数组放在 data 里，但不同版本可能再包一层，所以用 JsonElement 收，
 * 由 [NeteasePersonalFmResponse.songs] 容错解析。
 */
data class NeteasePersonalFmResponse(
    val code: Int = 0,
    val data: com.google.gson.JsonElement? = null
) {
    fun songs(): List<NeteaseSong> {
        val element = data ?: return emptyList()
        val gson = com.google.gson.Gson()
        val array: com.google.gson.JsonArray = when {
            element.isJsonArray -> element.asJsonArray
            element.isJsonObject -> {
                // 不同模式下歌曲数组的键不一样：data / songs / recommends / tracks / resources
                val obj = element.asJsonObject
                listOf("data", "songs", "recommends", "tracks", "resources", "songList")
                    .asSequence()
                    .mapNotNull { key -> obj.get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
                    .firstOrNull() ?: return emptyList()
            }
            else -> return emptyList()
        }
        return array.mapNotNull { item ->
            runCatching { gson.fromJson(unwrapSongItem(item), NeteaseSong::class.java) }.getOrNull()
        }.filter { it.id > 0L || !it.name.isNullOrBlank() }
    }

    /**
     * 有些推荐/漫游接口把单曲再包一层：[{ song: {...} }] / [{ track: {...} }] / [{ data: {...} }]。
     * 直接按 NeteaseSong 解析会得到一堆空对象（歌名/歌手/封面全空 → 外部播放条显示不对），
     * 所以先剥一层。
     */
    private fun unwrapSongItem(item: com.google.gson.JsonElement): com.google.gson.JsonElement {
        if (!item.isJsonObject) return item
        val obj = item.asJsonObject
        for (key in listOf("song", "track", "data", "songInfo", "resource")) {
            val inner = obj.get(key)
            if (inner != null && inner.isJsonObject) {
                val innerObj = inner.asJsonObject
                if (innerObj.get("id") != null || innerObj.get("name") != null) return inner
            }
        }
        return item
    }
}

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

// ==================== 登录 / 账号 DTO ====================
// 接口按 网易云api.txt：/login/qr/* 扫码、/captcha/sent + /login/cellphone 手机验证码。
// 所有登录接口都必须带 timestamp——NCM 服务端有 2 分钟缓存，否则轮询拿的是旧状态。

/** /login/qr/key 响应（新版字段是 unikey，老版是 key，两个都接） */
data class NeteaseQrKeyResponse(
    val code: Int = 0,
    val data: NeteaseQrKeyData? = null
)

data class NeteaseQrKeyData(
    val code: Int = 0,
    val unikey: String? = null,
    val key: String? = null
) {
    val loginKey: String?
        get() = unikey?.takeIf { it.isNotBlank() } ?: key?.takeIf { it.isNotBlank() }
}

/** /login/qr/create 响应：qrimg 是 data:image/png;base64,... */
data class NeteaseQrCreateResponse(
    val code: Int = 0,
    val data: NeteaseQrCreateData? = null
)

data class NeteaseQrCreateData(
    val qrurl: String? = null,
    val qrimg: String? = null
)

/** /login/qr/check 响应：code 在顶层，800 过期 / 801 待扫码 / 802 待确认 / 803 成功 */
data class NeteaseQrCheckResponse(
    val code: Int = 0,
    val message: String? = null,
    val cookie: String? = null
)

/** 通用结果体（发送验证码 / 退出登录） */
data class NeteaseBaseResponse(
    val code: Int = 0,
    val message: String? = null,
    val msg: String? = null
) {
    val reason: String?
        get() = message?.takeIf { it.isNotBlank() } ?: msg?.takeIf { it.isNotBlank() }
}

/** /login/cellphone 响应；v3.30.0+ 会在 cookie 字段里直接返回登录凭证 */
data class NeteaseLoginResponse(
    val code: Int = 0,
    val message: String? = null,
    val msg: String? = null,
    val cookie: String? = null,
    val account: NeteaseAccount? = null,
    val profile: NeteaseProfile? = null
) {
    val reason: String?
        get() = message?.takeIf { it.isNotBlank() } ?: msg?.takeIf { it.isNotBlank() }
}

data class NeteaseAccount(
    val id: Long = 0,
    val userName: String? = null
)

/** 账号资料（/user/account 与 /login/cellphone 共用） */
data class NeteaseProfile(
    val userId: Long = 0,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val vipType: Int = 0
)

/** /user/account 响应 */
data class NeteaseAccountResponse(
    val code: Int = 0,
    val account: NeteaseAccount? = null,
    val profile: NeteaseProfile? = null
)

/** /login/status 响应（登录态校验） */
data class NeteaseLoginStatusResponse(
    val code: Int = 0,
    val data: NeteaseLoginStatusData? = null
)

data class NeteaseLoginStatusData(
    val code: Int = 0,
    val account: NeteaseAccount? = null,
    val profile: NeteaseProfile? = null
)

// ==================== 歌单 DTO ====================

/** /user/playlist 响应（登录后拉自己的歌单） */
data class NeteaseUserPlaylistResponse(
    val code: Int = 0,
    val more: Boolean = false,
    val playlist: List<NeteasePlaylist>? = null
)

data class NeteasePlaylist(
    val id: Long = 0,
    val name: String? = null,
    val coverImgUrl: String? = null,
    val trackCount: Int = 0,
    val playCount: Long = 0,
    val subscribed: Boolean = false,
    val specialType: Int = 0,
    val creator: NeteasePlaylistCreator? = null
)

data class NeteasePlaylistCreator(
    val userId: Long = 0,
    val nickname: String? = null
)

/** /playlist/track/all 响应（需登录，私密歌单也能读） */
data class NeteasePlaylistTracksResponse(
    val code: Int = 0,
    val songs: List<NeteaseSong>? = null
)

// ==================== Retrofit 接口 ====================
// 单曲搜索/取流/歌词 + 登录/账号；歌单与推荐按后续轮次接入

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

    /** 每日推荐歌曲（需登录），歌曲在 data.dailySongs */
    @GET("recommend/songs")
    suspend fun recommendSongs(
        @Query("timestamp") timestamp: Long = 0
    ): NeteaseRecommendResponse

    /**
     * 私人漫游（需登录）。
     * mode: DEFAULT/FAMILIAR/EXPLORE/PUZZLE_MODE_RCMD/SCENE_RCMD
     * submode: mode=SCENE_RCMD 时传场景码（EXERCISE/FOCUS/NIGHT_EMO/SLEEP_HELP/...）
     */
    @GET("personal/fm/mode")
    suspend fun personalFmMode(
        @Query("mode") mode: String,
        @Query("submode") subMode: String? = null,
        @Query("timestamp") timestamp: Long = 0
    ): NeteasePersonalFmResponse

    // ========== 登录 / 账号 ==========

    /** 二维码 key */
    @GET("login/qr/key")
    suspend fun qrKey(@Query("timestamp") timestamp: Long): NeteaseQrKeyResponse

    /** 二维码图片（qrimg=true 额外返回 base64 data URI） */
    @GET("login/qr/create")
    suspend fun qrCreate(
        @Query("key") key: String,
        @Query("qrimg") qrimg: Boolean,
        @Query("timestamp") timestamp: Long
    ): NeteaseQrCreateResponse

    /** 二维码状态轮询；扫码后若返回 502，按文档加 noCookie=true 重试 */
    @GET("login/qr/check")
    suspend fun qrCheck(
        @Query("key") key: String,
        @Query("timestamp") timestamp: Long,
        @Query("noCookie") noCookie: Boolean? = null
    ): NeteaseQrCheckResponse

    /** 发送手机验证码 */
    @GET("captcha/sent")
    suspend fun sendCaptcha(
        @Query("phone") phone: String,
        @Query("timestamp") timestamp: Long
    ): NeteaseBaseResponse

    /** 手机验证码登录（成功返回 cookie） */
    @GET("login/cellphone")
    suspend fun loginCellphone(
        @Query("phone") phone: String,
        @Query("captcha") captcha: String,
        @Query("timestamp") timestamp: Long
    ): NeteaseLoginResponse

    /** 当前登录账号资料（昵称/头像/uid） */
    @GET("user/account")
    suspend fun userAccount(@Query("timestamp") timestamp: Long): NeteaseAccountResponse

    /** 登录状态（cookie 失效校验） */
    @GET("login/status")
    suspend fun loginStatus(@Query("timestamp") timestamp: Long): NeteaseLoginStatusResponse

    /** 退出登录（尽力调用，失败也不该阻断本地清理） */
    @GET("logout")
    suspend fun logout(@Query("timestamp") timestamp: Long): NeteaseBaseResponse

    // ========== 歌单 ==========

    /** 用户歌单列表（uid=自己时含「我喜欢的音乐」与私密歌单） */
    @GET("user/playlist")
    suspend fun userPlaylists(
        @Query("uid") uid: Long,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("timestamp") timestamp: Long
    ): NeteaseUserPlaylistResponse

    /** 歌单歌曲（需登录；支持分页，私密歌单也可读） */
    @GET("playlist/track/all")
    suspend fun playlistTracks(
        @Query("id") id: Long,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("timestamp") timestamp: Long
    ): NeteasePlaylistTracksResponse
}

// ==================== 单例 ====================

object NeteaseApi {
    /** 网易云 API 默认端口（酷狗 3000 → 网易云 3001） */
    const val DEFAULT_PORT = 3001

    // 服务器地址由应用启动时设置：优先用户自定义（setting prefs "netease_server_url"），
    // 否则自动跟随酷狗服务器（同一主机 + 3001 端口）
    @Volatile
    var baseUrl: String = "http://your-server:3001/"

    /**
     * 网易云登录态 cookie（原始串，如 "MUSIC_U=xxx; __csrf=yyy"）。
     *
     * 由 [NeteaseAuth] 在启动时恢复、登录成功后写入；每次请求通过 Cookie 头带给 BFF，
     * 由 BFF 转发给内网 3001。不再依赖 OkHttp 的 CookieJar（那个只存在内存里，进程一死就丢）。
     */
    @Volatile
    var authCookie: String = ""

    /** cookie 变化回调（登录/退出/上游轮换），由 [NeteaseAuth] 负责落盘 */
    @Volatile
    var onAuthCookieChanged: ((String) -> Unit)? = null

    /** "a=1; b=2" → 有序 map */
    private fun parseCookie(raw: String): LinkedHashMap<String, String> {
        val map = linkedMapOf<String, String>()
        raw.split(';').forEach { part ->
            val idx = part.indexOf('=')
            if (idx > 0) {
                val name = part.substring(0, idx).trim()
                val value = part.substring(idx + 1).trim()
                if (name.isNotEmpty() && value.isNotEmpty()) map[name] = value
            }
        }
        return map
    }

    private val COOKIE_ATTRIBUTES = setOf(
        "max-age", "expires", "path", "domain", "secure", "httponly",
        "samesite", "priority", "version", "comment"
    )

    /**
     * 清洗登录接口返回的 cookie 串。
     *
     * NCM 的 /login/qr/check 803 把多份 Set-Cookie 用 ";" 拼在 body 的 cookie 字段里，
     * 形如 "MUSIC_U=xxx; Max-Age=15552000; Expires=...; Path=/;;__csrf=yyy; ..."。
     * 原样当 Cookie 头发出去后 NCM 解析不出会话（实测 /user/account 返回 account/profile 全 null），
     * 这里只保留 name=value，丢掉属性项与空值项（MUSIC_SNS= 这种）。
     */
    fun cleanCookie(raw: String): String {
        val map = linkedMapOf<String, String>()
        raw.split(';').forEach { part ->
            val text = part.trim()
            if (text.isEmpty()) return@forEach
            val idx = text.indexOf('=')
            if (idx <= 0) return@forEach
            val name = text.substring(0, idx).trim()
            val value = text.substring(idx + 1).trim()
            if (name.isEmpty() || value.isEmpty()) return@forEach
            if (name.lowercase() in COOKIE_ATTRIBUTES) return@forEach
            map[name] = value
        }
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** 写入登录态（空串=已退出），清洗后落库并通知持久化 */
    fun updateAuthCookie(cookie: String) {
        val trimmed = cleanCookie(cookie)
        if (trimmed == authCookie) return
        authCookie = trimmed
        onAuthCookieChanged?.invoke(trimmed)
    }

    /** 合并上游下发的 cookie（MUSIC_U 轮换 / 扫码 502 走 noCookie 时只剩 Set-Cookie） */
    fun mergeAuthCookies(updates: Map<String, String>) {
        if (updates.isEmpty()) return
        val map = parseCookie(authCookie)
        var changed = false
        updates.forEach { (name, value) ->
            if (value.isBlank()) {
                if (map.remove(name) != null) changed = true
            } else if (map[name] != value) {
                map[name] = value
                changed = true
            }
        }
        if (changed) updateAuthCookie(map.entries.joinToString("; ") { "${it.key}=${it.value}" })
    }

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

    /** 重建 API 服务（服务器地址变更后调用）；登录态 cookie 不随之清空 */
    fun rebuildService() {
        _service = null
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

    /** 是否持有网易云登录态（登录成功后 cookie 里一定带 MUSIC_U） */
    val isLoggedIn: Boolean
        get() = authCookie.contains("MUSIC_U=")

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

    /** 登录态注入：所有请求统一带上 Cookie 头（BFF 会原样转发给内网 NCM） */
    private fun cookieInterceptor(): okhttp3.Interceptor = okhttp3.Interceptor { chain ->
        val cookie = authCookie.trim()
        if (cookie.isEmpty()) return@Interceptor chain.proceed(chain.request())
        chain.proceed(chain.request().newBuilder().header("Cookie", cookie).build())
    }

    /**
     * 收下上游轮换下来的 Set-Cookie（BFF 已把 Domain 去掉）。
     * 二维码登录走 803 的 body cookie 就够，这里是兜底：MUSIC_U 轮换、或 502 后用
     * noCookie=true 重试时只有响应头里带 cookie。
     */
    private fun cookieSyncInterceptor(): okhttp3.Interceptor = okhttp3.Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val updates = response.headers("Set-Cookie").mapNotNull { header ->
            val pair = header.substringBefore(';')
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else pair.substring(0, idx).trim() to pair.substring(idx + 1).trim()
        }.toMap()
        if (updates.isNotEmpty()) mergeAuthCookies(updates)
        response
    }

    /** BFF 全量代理拦截器：配置了 bffUrl 时把网易云请求改写到 BFF（api/ne 前缀下的所有路径）并带激活凭证头，
     *  3001 端口即可只监听回环地址。
     *
     *  与酷狗侧一致：总是改写（不看原 URL 的 host/port），也不再对 404 回退直连
     *  （3001 已收回内网，回退只会白等一次连接超时）。 */
    private fun bffInterceptor(): okhttp3.Interceptor = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val bff = KuGouApi.bffUrl.trim()
        if (bff.isEmpty()) return@Interceptor chain.proceed(original)
        // 未激活：网易云在线接口同样快速失败（本地音乐模式不产生网络请求）
        if (!BffApi.onlineAllowed) {
            throw java.io.IOException("未激活，在线功能不可用")
        }

        var request = original
        val target = runCatching { java.net.URI(bff) }.getOrNull()
        val host = target?.host
        if (target != null && !host.isNullOrBlank()) {
            val builder = HttpUrl.Builder()
                .scheme(target.scheme ?: "http")
                .host(host)
                .encodedPath("/api/ne" + original.url.encodedPath)
                .encodedQuery(original.url.encodedQuery)
            if (target.port != -1) builder.port(target.port)
            request = original.newBuilder().url(builder.build()).build()
        }
        if (BffApi.vipCode.isNotBlank()) {
            request = request.newBuilder().header("X-Vip-Code", BffApi.vipCode).build()
        }
        if (BffApi.vipDevice.isNotBlank()) {
            request = request.newBuilder().header("X-Vip-Device", BffApi.vipDevice).build()
        }
        chain.proceed(request)
    }

    private fun createService(): NeteaseService {
        val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor { message ->
            android.util.Log.d("LxMusic_Netease", message)
        }.apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
            // 登录态不进日志：BODY 级别会打印请求头，Cookie 必须打码
            redactHeader("Cookie")
        }

        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(cookieInterceptor())
            .addInterceptor(cookieSyncInterceptor())
            .addInterceptor(bffInterceptor())
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