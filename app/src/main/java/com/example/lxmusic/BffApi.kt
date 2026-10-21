package com.example.lxmusic

import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * BFF 后端客户端（Java music-hub-backend）。
 * 默认关闭：设置页里填写 bff_url 后才启用；任何失败都自动回退原有直连链路。
 *
 * 身份透传：把当前登录 token / 激活借号凭据 / dfid 通过请求头带给 BFF，
 * BFF 不保存任何账号凭据。
 */
object BffApi {

    const val PREFS_NAME = "settings"
    const val KEY_BFF_URL = "bff_url"

    /**
     * 激活凭证（BFF 访问控制）：激活码 + 设备ID。
     * 由 VipConfigManager 在恢复/激活成功后写入，激活状态清理时同步清空。
     */
    @Volatile var vipCode: String = ""
    @Volatile var vipDevice: String = ""

    /** 在线访问开关：设备已激活时为 true。未激活时 BFF 调用直接返回 null（不发请求）。 */
    @Volatile var onlineAllowed: Boolean = true

    private val gson = Gson()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun baseUrl(): String {
        val raw = KuGouApi.bffUrl.trim()
        if (raw.isEmpty()) return ""
        return if (raw.endsWith("/")) raw else "$raw/"
    }

    val isEnabled: Boolean get() = baseUrl().isNotEmpty()

    private fun authHeaders(): Map<String, String> = buildMap {
        if (KuGouApi.token.isNotBlank()) put("X-Kugou-Token", KuGouApi.token)
        if (KuGouApi.userid.isNotBlank()) put("X-Kugou-Userid", KuGouApi.userid)
        if (KuGouApi.ownerToken.isNotBlank()) put("X-Kugou-Owner-Token", KuGouApi.ownerToken)
        if (KuGouApi.ownerUserid.isNotBlank()) put("X-Kugou-Owner-Userid", KuGouApi.ownerUserid)
        if (KuGouApi.dfid.isNotBlank()) put("X-Kugou-Dfid", KuGouApi.dfid)
        // 激活凭证：BFF 侧校验「激活码 + 设备绑定」，未激活返回 401
        if (vipCode.isNotBlank()) put("X-Vip-Code", vipCode)
        if (vipDevice.isNotBlank()) put("X-Vip-Device", vipDevice)
    }

    private fun get(path: String): String? {
        if (!isEnabled || !onlineAllowed) return null
        val request = Request.Builder()
            .url(baseUrl() + path)
            .apply { authHeaders().forEach { (k, v) -> addHeader(k, v) } }
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                // 登录等接口由 BFF 透传上游 Set-Cookie（t1/vip_token 等），需要落到 KuGouApi 的会话 Cookie
                val setCookies = response.headers("Set-Cookie")
                if (setCookies.isNotEmpty()) {
                    val cookieMap = setCookies.mapNotNull { header ->
                        val first = header.substringBefore(';').trim()
                        val eq = first.indexOf('=')
                        if (eq <= 0) null else first.substring(0, eq).trim() to first.substring(eq + 1).trim()
                    }.toMap()
                    if (cookieMap.isNotEmpty()) KuGouApi.mergeServerCookies(cookieMap)
                }
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    Log.d("LxMusic", "BFF $path OK (${body?.length ?: 0} chars)")
                    body
                } else {
                    if (response.code == 401) {
                        Log.w("LxMusic", "BFF $path 401 未激活，回退原链路")
                    } else {
                        Log.w("LxMusic", "BFF $path HTTP ${response.code}")
                    }
                    null
                }
            }
        } catch (e: Exception) {
            Log.w("LxMusic", "BFF $path 请求失败: ${e.message}")
            null
        }
    }

    /** 统一搜索 */
    fun search(query: String, page: Int = 1, pageSize: Int = 30): BffSearchResult? {
        if (!isEnabled) return null
        val q = URLEncoder.encode(query, "UTF-8")
        return get("api/music/search?q=$q&page=$page&pageSize=$pageSize")?.let {
            runCatching { gson.fromJson(it, BffSearchResult::class.java) }.getOrNull()
        }
    }

    /** 统一取流（songId 形如 kg:hash:albumAudioId） */
    fun play(songId: String, quality: String): BffPlayResult? {
        if (!isEnabled) return null
        val id = URLEncoder.encode(songId, "UTF-8")
        val q = URLEncoder.encode(quality, "UTF-8")
        return get("api/music/play?songId=$id&quality=$q")?.let {
            runCatching { gson.fromJson(it, BffPlayResult::class.java) }.getOrNull()
        }
    }

    /** 首页聚合（1 个请求拿全部区块）；force=true 时绕过服务端缓存（手动刷新场景） */
    fun homeFeed(force: Boolean = false): BffHomeFeed? {
        if (!isEnabled) return null
        val suffix = if (force) "?force=true" else ""
        return get("api/home/feed$suffix")?.let {
            runCatching { gson.fromJson(it, BffHomeFeed::class.java) }.getOrNull()
        }
    }

    /** 发现页排行榜列表 */
    fun ranks(): BffRanks? {
        if (!isEnabled) return null
        return get("api/discover/ranks")?.let {
            runCatching { gson.fromJson(it, BffRanks::class.java) }.getOrNull()
        }
    }

    /** 排行榜歌曲（分页） */
    fun rankSongs(rankId: Long, page: Int = 1, pageSize: Int = 30): BffSongList? {
        if (!isEnabled) return null
        return get("api/discover/rank/songs?rankId=$rankId&page=$page&pageSize=$pageSize")?.let {
            runCatching { gson.fromJson(it, BffSongList::class.java) }.getOrNull()
        }
    }
}

/** BffSong -> 客户端内部 SongInfo（filePath 格式与原有链路一致） */
fun BffSong.toSongInfo(): com.example.lxmusic.model.SongInfo = com.example.lxmusic.model.SongInfo(
    title = title ?: "未知歌曲",
    artist = artist ?: "未知艺术家",
    filePath = "$hash|$albumAudioId",
    albumArtUri = coverUrl ?: "",
    duration = durationMs ?: 0L
)

/** 统一歌曲（BFF 契约；字段可空以兼容 Gson 反射实例化） */
data class BffSong(
    val songId: String? = null,
    val source: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val coverUrl: String? = null
) {
    val hash: String get() = songId?.split(":")?.getOrNull(1) ?: ""
    val albumAudioId: Long get() = songId?.split(":")?.getOrNull(2)?.toLongOrNull() ?: 0L
}

data class BffSearchResult(
    val source: String? = null,
    val page: Int? = null,
    val pageSize: Int? = null,
    val total: Int? = null,
    val items: List<BffSong>? = null
)

data class BffPlayResult(
    val ok: Boolean = false,
    val songId: String? = null,
    val quality: String? = null,
    val bitRate: Int? = null,
    val extName: String? = null,
    val durationMs: Long? = null,
    val url: String? = null,
    val backupUrl: String? = null,
    val message: String? = null
)

data class BffHomeFeed(
    val userId: String? = null,
    val updatedAt: Long? = null,
    val daily: List<BffSong>? = null,
    val history: List<BffSong>? = null,
    val vip: List<BffSong>? = null,
    val style: List<BffSong>? = null,
    val surge: List<BffSong>? = null,
    val hot: List<BffSong>? = null,
    val newSongs: List<BffSong>? = null,
    val collect: List<BffSong>? = null,
    val niche: List<BffSong>? = null,
    val personal: List<BffSong>? = null,
    val concept: List<BffSong>? = null,
    val trend: List<BffSong>? = null,
    val million: List<BffSong>? = null,
    val network: List<BffSong>? = null
)

/** 排行榜条目 */
data class BffPreviewSong(
    val name: String? = null,
    val author: String? = null
)

data class BffRank(
    val id: Long? = null,
    val name: String? = null,
    val coverUrl: String? = null,
    val playTimes: Long? = null,
    val songs: List<BffPreviewSong>? = null
)

data class BffRanks(
    val items: List<BffRank>? = null
)

data class BffSongList(
    val items: List<BffSong>? = null,
    val total: Int? = null
)
