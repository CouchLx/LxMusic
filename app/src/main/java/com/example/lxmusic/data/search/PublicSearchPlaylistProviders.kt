package com.example.lxmusic.data.search

import android.util.Log
import com.example.lxmusic.KuGouApi
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 多平台「歌单」搜索（搜索结果统一映射成 PublicPlaylist，点进去复用公共歌单详情页）。
 * - 酷狗：后端 /search?type=special（经 BFF，激活后可用）
 * - 网易云：music.163.com/api/search/get/web?type=1000
 * - QQ：musicu DoSearchForQQMusicDesktop search_type=3（songlist）
 */
object PublicSearchPlaylistProviders {

    data class Result(
        val playlists: List<PublicPlaylistProviders.PublicPlaylist>,
        val total: Int
    )

    private const val TAG = "PublicPlaylistSearch"
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private const val UA =
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

    suspend fun search(
        platform: String,
        keyword: String,
        page: Int,
        pageSize: Int = 30
    ): Result = withContext(Dispatchers.IO) {
        when (platform) {
            "kugou", "kg" -> kugou(keyword, page, pageSize)
            "netease", "wy" -> netease(keyword, page, pageSize)
            "tx" -> qq(keyword, page, pageSize)
            else -> Result(emptyList(), 0)
        }
    }

    private suspend fun kugou(keyword: String, page: Int, pageSize: Int): Result {
        val resp = runCatching {
            KuGouApi.service.searchPlaylist(keyword, page = page, pageSize = pageSize)
        }.getOrNull() ?: return Result(emptyList(), 0)
        val items = resp.data?.lists.orEmpty().mapNotNull { p ->
            val name = p.specialname ?: return@mapNotNull null
            PublicPlaylistProviders.PublicPlaylist(
                platform = "kg",
                id = p.specialid.toString(),
                name = name,
                coverUrl = p.coverUrl,
                playCount = p.play_count?.replace("[^0-9]".toRegex(), "")?.toLongOrNull() ?: 0L,
                trackCount = p.song_count,
                creator = p.nickname.orEmpty()
            )
        }
        return Result(items, resp.data?.total ?: 0)
    }

    private fun netease(keyword: String, page: Int, pageSize: Int): Result {
        val offset = (page - 1) * pageSize
        val url = "https://music.163.com/api/search/get/web" +
            "?type=1000&s=${encode(keyword)}&limit=$pageSize&offset=$offset"
        val body = get(url, referer = "https://music.163.com/") ?: return Result(emptyList(), 0)
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
            ?: return Result(emptyList(), 0)
        val result = root.getAsJsonObject("result") ?: return Result(emptyList(), 0)
        val total = result.get("playlistCount")?.asInt ?: 0
        val arr = result.getAsJsonArray("playlists") ?: return Result(emptyList(), total)
        val items = arr.mapNotNull { el ->
            val o = el.asJsonObject
            val id = o.get("id")?.asString ?: return@mapNotNull null
            val name = o.get("name")?.asString ?: return@mapNotNull null
            PublicPlaylistProviders.PublicPlaylist(
                platform = "wy",
                id = id,
                name = name,
                coverUrl = o.get("coverImgUrl")?.asString.orEmpty().replace("http://", "https://"),
                playCount = o.get("playCount")?.asLong ?: 0L,
                trackCount = o.get("trackCount")?.asInt ?: 0,
                creator = o.getAsJsonObject("creator")?.get("nickname")?.asString.orEmpty()
            )
        }
        return Result(items, total)
    }

    private fun qq(keyword: String, page: Int, pageSize: Int): Result {
        val payload = JsonObject().apply {
            val comm = JsonObject().apply {
                addProperty("ct", "19")
                addProperty("cv", "1859")
                addProperty("uin", "0")
            }
            val req = JsonObject().apply {
                addProperty("method", "DoSearchForQQMusicDesktop")
                addProperty("module", "music.search.SearchCgiService")
                val param = JsonObject().apply {
                    addProperty("grp", 1)
                    addProperty("num_per_page", pageSize)
                    addProperty("page_num", page)
                    addProperty("query", keyword)
                    addProperty("search_type", 3)
                }
                add("param", param)
            }
            add("comm", comm)
            add("req", req)
        }
        val body = runCatching {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                .header("User-Agent", UA)
                .header("Referer", "https://y.qq.com/")
                .post(payload.toString().toRequestBody(mediaType))
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull() ?: return Result(emptyList(), 0)
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
            ?: return Result(emptyList(), 0)
        val songlist = root.getAsJsonObject("req")
            ?.getAsJsonObject("data")
            ?.getAsJsonObject("body")
            ?.getAsJsonObject("songlist")
            ?: return Result(emptyList(), 0)
        val total = songlist.get("totalnum")?.asInt ?: 0
        val arr = songlist.getAsJsonArray("list") ?: return Result(emptyList(), total)
        val items = arr.mapNotNull { el ->
            val o = el.asJsonObject
            val id = o.get("dissid")?.asString ?: return@mapNotNull null
            val name = o.get("dissname")?.asString ?: return@mapNotNull null
            PublicPlaylistProviders.PublicPlaylist(
                platform = "tx",
                id = id,
                name = name,
                coverUrl = o.get("imgurl")?.asString.orEmpty().replace("http://", "https://"),
                playCount = o.get("listennum")?.asLong ?: 0L,
                trackCount = o.get("song_count")?.asInt ?: 0,
                creator = o.getAsJsonObject("creator")?.get("name")?.asString.orEmpty()
            )
        }
        return Result(items, total)
    }
}
