package com.example.lxmusic.data

import android.util.Log
import com.example.lxmusic.NeteaseApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * AMLL (Apple Music Like Lyrics) TTML 数据库歌词获取器
 * 数据源：https://amlldb.bikonoo.com/ncm-lyrics/$id.ttml
 * 提供高质量逐字/逐音节歌词数据与伴奏间奏时间轴
 */
object AmllLyricFetcher {
    private const val TAG = "AmllLyricFetcher"
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    /**
     * 清理歌名中的干扰标签与括号（如 Live、伴奏、翻唱、歌手前缀等）
     */
    fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""[\(（\[【][^)）\]】]*[\)）\]】]"""), "")
            .replace(Regex("""^.+?\s*-\s*"""), "") // 去除类似 "歌手 - 歌名" 中的前缀
            .replace(Regex("""\s*-\s*.+?$"""), "") // 去除类似 "歌名 - 伴奏/副标题" 中的后缀
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * 根据网易云歌曲 ID 获取 AMLL TTML 歌词（支持官方源与 jsdelivr CDN 镜像容灾）
     */
    suspend fun fetchAMLLByNeteaseId(id: Long): String? = withContext(Dispatchers.IO) {
        if (id <= 0) return@withContext null
        val mirrors = listOf(
            "https://amlldb.bikonoo.com/ncm-lyrics/$id.ttml",
            "https://cdn.jsdelivr.net/gh/Steve-xmh/amll-ttml-db@main/ncm-lyrics/$id.ttml"
        )
        for (url in mirrors) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank() && body != "歌词不存在" && body.contains("<tt")) {
                            Log.d(TAG, "AMLL TTML hit for Netease ID $id via $url")
                            return@withContext body
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchAMLLByNeteaseId mirror $url failed for $id: ${e.message}")
            }
        }
        null
    }

    /**
     * 智能搜索匹配 AMLL TTML
     * 结合歌名清洗与时长比对，使用并发多候选者探测（async），
     * 若 AMLL 未收录则回退网易云官方高品质逐字 YRC。
     * @param title 歌名
     * @param artist 歌手
     * @param durationMs 歌曲总时长（毫秒）
     */
    suspend fun searchAndFetchAMLL(
        title: String,
        artist: String,
        durationMs: Long = 0L
    ): String? = withContext(Dispatchers.IO) {
        val rawTitle = title.trim()
        val cleanedTitle = cleanTitle(rawTitle)
        val cleanArtist = artist.takeIf { it != "未知艺术家" }?.trim().orEmpty()

        // 构造搜索关键词（优先精准：歌名 + 歌手）
        val queries = mutableListOf<String>()
        if (cleanedTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            queries.add("$cleanedTitle $cleanArtist")
        } else if (cleanedTitle.isNotBlank()) {
            queries.add(cleanedTitle)
        }
        if (rawTitle != cleanedTitle && cleanArtist.isNotBlank()) {
            queries.add("$rawTitle $cleanArtist")
        }

        var fallbackYrcOrLrc: String? = null

        for (query in queries) {
            try {
                Log.d(TAG, "Searching Netease for AMLL match: query='$query', durationMs=$durationMs")
                val searchResp = NeteaseApi.service.search(keywords = query, limit = 6)
                val songs = searchResp.result?.songs.orEmpty()
                if (songs.isEmpty()) continue

                // 优先根据时长匹配（±8000ms）
                val sortedCandidates = if (durationMs > 10_000L) {
                    songs.sortedBy { abs(it.dt - durationMs) }
                } else {
                    songs
                }

                val validCandidates = sortedCandidates
                    .filter { durationMs <= 10_000L || abs(it.dt - durationMs) <= 8_000L }
                    .take(3)

                if (validCandidates.isNotEmpty()) {
                    // 并发同时探测候选者的 AMLL TTML（提升 3 倍拉取速度）
                    val ttmlResult: String? = coroutineScope {
                        val deferreds = validCandidates.map { candidate ->
                            async { fetchAMLLByNeteaseId(candidate.id) }
                        }
                        deferreds.mapNotNull { it.await() }.firstOrNull()
                    }
                    if (!ttmlResult.isNullOrBlank()) {
                        Log.d(TAG, "Matched AMLL TTML for '$title' (query='$query')")
                        return@withContext ttmlResult
                    }

                    // 若 AMLL TTML 库未收录，则拉取首选歌曲的网易云原生高品质逐字 YRC 兜底
                    if (fallbackYrcOrLrc == null) {
                        val bestId = validCandidates.first().id
                        try {
                            val lyricResp = NeteaseApi.service.getLyric(bestId)
                            val yrc = lyricResp.yrc?.lyric
                            if (!yrc.isNullOrBlank()) {
                                fallbackYrcOrLrc = yrc
                            } else {
                                val lrc = lyricResp.lrc?.lyric
                                if (!lrc.isNullOrBlank()) fallbackYrcOrLrc = lrc
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search query '$query' failed: ${e.message}")
            }
        }

        if (fallbackYrcOrLrc == null || !com.example.lxmusic.ui.lyrics.isNeteaseYrc(fallbackYrcOrLrc)) {
            val qqQrc = com.example.lxmusic.data.qq.QQMusicApi.searchAndFetchBestQrc(title, artist, durationMs)
            if (!qqQrc.isNullOrBlank()) {
                Log.d(TAG, "Fallback to QQ Music QRC for '$title' ($artist)")
                return@withContext qqQrc
            }
        }
        fallbackYrcOrLrc
    }
}
