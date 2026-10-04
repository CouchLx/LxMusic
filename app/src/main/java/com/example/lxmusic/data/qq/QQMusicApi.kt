package com.example.lxmusic.data.qq

import android.util.Base64
import android.util.Log
import com.example.lxmusic.ui.lyrics.QRCParser
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * QQ 音乐跨平台歌词检索与拉取服务（参考 MeiloX）
 * 端点：https://u.y.qq.com/cgi-bin/musicu.fcg
 * 功能：通过清洗后的歌名 + 歌手智能搜索，精准比对时长（±6秒），
 * 拉取 QQ 音乐官方海量 QRC 逐字歌词库，并通过 Triple-DES 解密为标准逐字格式。
 */
object QQMusicApi {
    private const val TAG = "QQMusicApi"
    private const val BASE_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.164 Safari/537.36")
                .header("Referer", "https://y.qq.com/")
                .build()
            chain.proceed(req)
        }
        .build()

    private val gson = Gson()

    private fun b64encode(str: String): String {
        return Base64.encodeToString(str.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /**
     * 自动清洗歌名括号中的干扰标记（Live、伴奏、混音、Remix 等）
     */
    fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""[\(（\[【][^)）\]】]*[\)）\]】]"""), "")
            .replace(Regex("""^.+?\s*-\s*"""), "")
            .replace(Regex("""\s*-\s*.+?$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    data class QQCandidate(
        val id: Long,
        val title: String,
        val singer: String,
        val album: String,
        val intervalSec: Long
    )

    /**
     * 检索 QQ 音乐候选曲目列表
     */
    suspend fun searchSongs(keyword: String): List<QQCandidate> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        try {
            val payload = JsonObject().apply {
                val comm = JsonObject().apply {
                    addProperty("ct", 11)
                    addProperty("cv", "1003006")
                    addProperty("v", "1003006")
                    addProperty("os_ver", "15")
                    addProperty("phonetype", "24122RKC7C")
                    addProperty("tmeAppID", "qqmusiclight")
                    addProperty("nettype", "NETWORK_WIFI")
                    addProperty("udid", "0")
                }
                val req = JsonObject().apply {
                    addProperty("method", "DoSearchForQQMusicLite")
                    addProperty("module", "music.search.SearchCgiService")
                    val param = JsonObject().apply {
                        addProperty("query", keyword)
                        addProperty("search_type", 0)
                        addProperty("page_num", 1)
                        addProperty("num_per_page", 10)
                        addProperty("highlight", 0)
                        addProperty("nqc_flag", 0)
                        addProperty("page_id", 1)
                        addProperty("grp", 1)
                    }
                    add("param", param)
                }
                add("comm", comm)
                add("request", req)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(BASE_URL)
                .post(payload.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = gson.fromJson(body, JsonObject::class.java) ?: return@withContext emptyList()
                val itemSongs = root.getAsJsonObject("request")
                    ?.getAsJsonObject("data")
                    ?.getAsJsonObject("body")
                    ?.getAsJsonArray("item_song")
                    ?: return@withContext emptyList()

                val result = mutableListOf<QQCandidate>()
                for (elem in itemSongs) {
                    val obj = elem.asJsonObject ?: continue
                    val id = obj.get("id")?.asLong ?: continue
                    val title = obj.get("title")?.asString.orEmpty()
                    val interval = obj.get("interval")?.asLong ?: 0L
                    val album = obj.getAsJsonObject("album")?.get("title")?.asString.orEmpty()
                    val singers = obj.getAsJsonArray("singer")
                        ?.mapNotNull { it.asJsonObject?.get("name")?.asString }
                        ?.joinToString(",")
                        .orEmpty()

                    result.add(QQCandidate(id, title, singers, album, interval))
                }
                result
            }
        } catch (e: Exception) {
            Log.w(TAG, "searchSongs failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * 拉取并解密指定 QQ 歌曲的 QRC / LRC 歌词
     * @return (解密后的主歌词[已转通用 YRC 或 LRC], 解密后的 LRC 翻译)；无可用歌词时为 null
     */
    suspend fun fetchLyric(candidate: QQCandidate): Pair<String, String?>? = withContext(Dispatchers.IO) {
        try {
            val payload = JsonObject().apply {
                val comm = JsonObject().apply {
                    addProperty("_os_version", "6.2.9200-2")
                    addProperty("ct", 11)
                    addProperty("cv", "1003006")
                    addProperty("patch", "118")
                    addProperty("tmeAppID", "qqmusiclight")
                }
                val info = JsonObject().apply {
                    addProperty("method", "GetPlayLyricInfo")
                    addProperty("module", "music.musichallSong.PlayLyricInfo")
                    val param = JsonObject().apply {
                        addProperty("albumName", b64encode(candidate.album))
                        addProperty("crypt", 1)
                        addProperty("ct", 19)
                        addProperty("cv", 2111)
                        addProperty("interval", candidate.intervalSec)
                        addProperty("lrc_t", 0)
                        addProperty("qrc", 1)
                        addProperty("qrc_t", 0)
                        addProperty("roma", 1)
                        addProperty("roma_t", 0)
                        addProperty("singerName", b64encode(candidate.singer))
                        addProperty("songID", candidate.id)
                        addProperty("songName", b64encode(candidate.title))
                        addProperty("trans", 1)
                        addProperty("trans_t", 0)
                        addProperty("type", 0)
                    }
                    add("param", param)
                }
                add("comm", comm)
                add("music.musichallSong.PlayLyricInfo.GetPlayLyricInfo", info)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(BASE_URL)
                .post(payload.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val root = gson.fromJson(body, JsonObject::class.java) ?: return@withContext null
                val data = root.getAsJsonObject("music.musichallSong.PlayLyricInfo.GetPlayLyricInfo")
                    ?.getAsJsonObject("data") ?: return@withContext null

                val encryptedLyric = data.get("lyric")?.asString.orEmpty()
                val encryptedTrans = data.get("trans")?.asString.orEmpty()
                val qrcT = data.get("qrc_t")?.asLong ?: data.get("qrcT")?.asLong ?: 0L

                if (encryptedLyric.isBlank()) return@withContext null

                val decodedLyric = QRCUtils.decodeLyric(encryptedLyric)
                if (decodedLyric.isBlank()) return@withContext null

                val decodedTrans = if (encryptedTrans.isNotBlank()) {
                    QRCUtils.decodeLyric(encryptedTrans).takeIf { it.isNotBlank() }
                } else null

                // 若包含 QRC 逐字时间戳或 qrcT != 0，转换为通用的 YRC 格式
                val isQrc = qrcT != 0L || decodedLyric.contains(Regex("""\[\d+,\d+\][^\(\)]*\((\d+),(\d+)\)"""))
                if (isQrc) {
                    val yrc = QRCParser.qrcToYrc(decodedLyric)
                    if (yrc.isNotBlank()) {
                        Log.d(TAG, "Successfully matched & decoded QQ Music QRC for ${candidate.title}")
                        return@withContext Pair(yrc, decodedTrans)
                    }
                    // QRC 转换失败时不能漏出原始 QRC 文本：它会被 YRC 解析器
                    // 解析成带时间戳的乱码并被缓存固化，宁可返回 null 走下一候选
                    Log.w(TAG, "QRC -> YRC conversion failed for ${candidate.title}, skip")
                    return@withContext null
                }

                // 否则返回普通解密后的 LRC（附翻译）
                Pair(decodedLyric, decodedTrans)
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchLyric failed for ${candidate.id}: ${e.message}")
            null
        }
    }

    /**
     * 智能跨平台搜索并拉取最佳 QRC 逐字歌词
     * 支持歌名清洗与时长容差（±6秒）匹配
     * @param onTranslation 命中候选自带 LRC 翻译时回调（QQ trans，供翻译链路缓存）
     */
    suspend fun searchAndFetchBestQrc(
        title: String,
        artist: String,
        durationMs: Long,
        onTranslation: ((String) -> Unit)? = null
    ): String? {
        val (lyric, trans) = searchAndFetchLyricWithTranslation(title, artist, durationMs)
        if (!trans.isNullOrBlank()) onTranslation?.invoke(trans)
        return lyric
    }

    /**
     * 搜索 + 拉取最佳候选的 (主歌词, 翻译)；
     * 单独暴露是为了翻译兜底通路能只取 trans 而不复用主歌词。
     */
    suspend fun searchAndFetchLyricWithTranslation(
        title: String,
        artist: String,
        durationMs: Long
    ): Pair<String, String?> = withContext(Dispatchers.IO) {
        val rawTitle = title.trim()
        val cleanedTitle = cleanTitle(rawTitle)
        val cleanArtist = artist.takeIf { it != "未知艺术家" }?.trim().orEmpty()
        val targetSec = if (durationMs > 0) durationMs / 1000 else 0L

        val queries = mutableListOf<String>()
        if (cleanedTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            queries.add("$cleanedTitle $cleanArtist")
        }
        if (cleanedTitle.isNotBlank() && !queries.contains(cleanedTitle)) {
            queries.add(cleanedTitle)
        }
        if (rawTitle != cleanedTitle && cleanArtist.isNotBlank()) {
            queries.add("$rawTitle $cleanArtist")
        }

        for (query in queries) {
            val candidates = searchSongs(query)
            if (candidates.isEmpty()) continue

            // 过滤时长匹配的歌曲（±6秒）
            val matchedCandidates = if (targetSec > 10L) {
                candidates.filter { abs(it.intervalSec - targetSec) <= 6L }
            } else {
                candidates
            }

            for (cand in matchedCandidates.take(3)) {
                val result = fetchLyric(cand) ?: continue
                val (lyric, trans) = result
                if (lyric.isNotBlank()) {
                    return@withContext Pair(lyric, trans)
                }
            }
        }
        Pair("", null)
    }
}
