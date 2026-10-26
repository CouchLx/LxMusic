package com.example.lxmusic.data.search

import android.util.Log
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.KgRecommendSong
import com.example.lxmusic.model.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 酷狗「乐库 → 语种/风格」页数据。
 *
 * 页面结构（对齐酷狗客户端）：
 * - 推荐歌单：官方歌单接口 /top/playlist?category_id=<tagId>
 * - 单曲推荐：/everyday/style/recommend?tagids=<S/L/M/C 码>，可组合多码凑够 50 首
 * - 电子页额外有子风格 chips（Phonk/House/…），这些子标签没有公开分类接口，
 *   用公开搜索 + 电子池过滤来近似，保证切换 chip 后单曲确实不同
 *
 * 所有接口匿名可用（经 BFF 更稳），不需要登录；未激活时由 UI 给出提示。
 */
object KugouYuekuProvider {

    data class Genre(
        val key: String,
        val label: String,
        /** /top/playlist 的 category_id（歌单分类 tag） */
        val playlistTagId: String,
        /** 单曲推荐码：S=风格、L=语种、M=心情、C=场景，可组合 */
        val recommendCodes: List<String>,
        /** 只保留指定语言（语种/纯音乐页用） */
        val languageFilter: String? = null,
        /** 只保留 tags 里含该关键词的歌（电子页用） */
        val tagKeyword: String? = null
    )

    val GENRES: List<Genre> = listOf(
        Genre("guoyu", "国语", "84", listOf("L1", "S14", "M1", "C6"), languageFilter = "国语"),
        Genre("riyu", "日语", "21", listOf("L3", "S17"), languageFilter = "日语"),
        Genre("yingyu", "英语", "20", listOf("L2", "S18"), languageFilter = "英语"),
        Genre("yueyu", "粤语", "19", listOf("L5", "S15"), languageFilter = "粤语"),
        Genre("hanyu", "韩语", "23", listOf("L4", "S16"), languageFilter = "韩语"),
        Genre("dianzi", "电子", "33", listOf("S3", "C9", "M6"), tagKeyword = "电子"),
        Genre("liuxing", "流行", "9", listOf("S14")),
        Genre("acg", "ACG", "77", listOf("S10")),
        Genre("chunyinyue", "纯音乐", "34", listOf("S20"), languageFilter = "纯音乐"),
        Genre("guofeng", "国风", "574", listOf("S2")),
        Genre("minyao", "民谣", "83", listOf("S5")),
        Genre("xiha", "嘻哈", "31", listOf("S4")),
        Genre("jazz", "爵士", "32", listOf("S6")),
        Genre("rnb", "R&B", "30", listOf("S7")),
        Genre("rock", "摇滚", "27", listOf("S11")),
        Genre("classical", "古典", "28", listOf("S12")),
        Genre("dj", "DJ", "17", listOf("S9")),
        Genre("wangluo", "网络", "35", listOf("S19"))
    )

    const val DEFAULT_GENRE = "guoyu"

    /** 电子页顶部子风格（与酷狗客户端一致） */
    val ELECTRONIC_SUB_GENRES = listOf(
        "全部", "Phonk", "EDM热歌", "House舞曲", "迪斯科", "Remix", "电子乐",
        "硬派音乐", "欧陆舞曲", "新浪潮-后朋克复兴", "智能舞曲", "回响贝斯"
    )

    private const val TAG = "KugouYueku"
    private val songPoolCache = mutableMapOf<String, List<SongInfo>>()
    private val playlistCache = mutableMapOf<String, List<PublicPlaylistProviders.PublicPlaylist>>()

    fun genre(key: String): Genre = GENRES.firstOrNull { it.key == key } ?: GENRES.first()

    suspend fun playlists(genre: Genre): List<PublicPlaylistProviders.PublicPlaylist> =
        withContext(Dispatchers.IO) {
            playlistCache[genre.key]?.let { return@withContext it }
            val list = PublicPlaylistProviders.kugouGenrePlaylists(genre.playlistTagId, 1, 12)
            if (list.isNotEmpty()) playlistCache[genre.key] = list
            list
        }

    /** 单曲推荐池（不超过 150 首，供 UI 轮换切片） */
    suspend fun pool(genre: Genre): List<SongInfo> = withContext(Dispatchers.IO) {
        songPoolCache[genre.key]?.let { return@withContext it }
        val pages = coroutineScope {
            genre.recommendCodes.map { code ->
                async(Dispatchers.IO) {
                    runCatching {
                        KuGouApi.service.getStyleRecommend(code).data?.songs.orEmpty()
                    }.getOrElse { emptyList() }
                }
            }.awaitAll()
        }
        val filtered = pages.flatten()
            .filter { item -> item.matches(genre) }
            .distinctBy { it.hash }
        var songs = filtered.mapNotNull { it.toSongInfo() }.shuffled().take(150)
        // 部分风格官方推荐不足 50：用本风格的公开搜索补足，保证单曲推荐凑到 50 首
        if (songs.size < 50) {
            val keyword = if (genre.key == "dianzi") "电子" else genre.label
            val extra = coroutineScope {
                listOf(1, 2).map { p ->
                    async(Dispatchers.IO) {
                        runCatching { PublicSearchProviders.search("kg", keyword, p, 30) }
                            .getOrElse { emptyList() }
                    }
                }.awaitAll().flatten()
            }.map { it.toSongInfo() }
            songs = (songs + extra).distinctBy { it.filePath }.shuffled().take(150)
        }
        if (songs.isNotEmpty()) {
            songPoolCache[genre.key] = songs
        }
        Log.d(TAG, "pool genre=${genre.label} raw=${pages.sumOf { it.size }} filtered=${filtered.size} songs=${songs.size}")
        songs
    }

    /**
     * 子风格单曲：先搜该子风格的歌（公开搜索，两页），再用本风格池兜底过滤。
     * seed 递增可以让“换一批”换到不同的搜索页。
     */
    suspend fun subGenreSongs(genre: Genre, sub: String, seed: Int): List<SongInfo> =
        withContext(Dispatchers.IO) {
            if (sub == "全部") return@withContext pool(genre)
            val keyword = normalizeSubGenre(sub)
            val page = 1 + (seed % 3)
            val results = coroutineScope {
                listOf(page, page + 1).map { p ->
                    async(Dispatchers.IO) {
                        runCatching {
                            PublicSearchProviders.search("kg", keyword, p, 30)
                        }.getOrElse { emptyList() }
                    }
                }.awaitAll().flatten()
            }.distinctBy { it.platform + it.id }
                .map { it.toSongInfo() }
            val fromPool = pool(genre).filter {
                it.title.contains(keyword, ignoreCase = true) ||
                    it.artist.contains(keyword, ignoreCase = true)
            }
            (results + fromPool).distinctBy { it.filePath }.shuffled().take(50)
        }

    private fun normalizeSubGenre(sub: String): String = when {
        sub.contains("EDM") -> "EDM"
        sub.contains("House") -> "House"
        sub.contains("新浪潮") -> "后朋克"
        sub.contains("电子乐") -> "电子"
        sub.contains("硬派") -> "Hardstyle"
        sub.contains("智能舞曲") -> "IDM"
        sub.contains("回响贝斯") -> "Dubstep"
        sub.contains("欧陆") -> "Eurodance"
        else -> sub
    }

    private fun KgRecommendSong.matches(genre: Genre): Boolean {
        val language = language.orEmpty()
        if (genre.languageFilter != null && language != genre.languageFilter) return false
        if (genre.tagKeyword != null) {
            val hitTag = tags.orEmpty().any { it.tagName?.contains(genre.tagKeyword) == true }
            val hitTitle = songname?.contains(genre.tagKeyword, ignoreCase = true) == true
            if (!hitTag && !hitTitle) {
                // 电子页的补充码（夜店/动感）里混有其他风格，补一层“舞曲/电子”判断
                val hitDance = tags.orEmpty().any {
                    it.tagName?.contains("舞曲") == true || it.tagName?.contains("电子") == true
                }
                if (!hitDance) return false
            }
        }
        return true
    }

    private fun KgRecommendSong.toSongInfo(): SongInfo? {
        val h = hash?.takeIf { it.isNotBlank() } ?: return null
        val audioId = if (albumAudioId > 0) albumAudioId else mixSongId
        val title = songname?.takeIf { it.isNotBlank() }
            ?: fileName?.substringAfter(" - ", "")?.takeIf { it.isNotBlank() }
            ?: return null
        val artist = authorName?.takeIf { it.isNotBlank() }
            ?: fileName?.substringBefore(" - ", "").orEmpty()
        return SongInfo(
            title = title,
            artist = artist,
            filePath = "$h|$audioId",
            albumArtUri = cover?.replace("{size}", "240")?.replace("http://", "https://"),
            duration = durationSec.toLong() * 1000
        )
    }
}
