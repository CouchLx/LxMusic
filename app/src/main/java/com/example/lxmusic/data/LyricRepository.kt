package com.example.lxmusic.data

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.NeteaseApi
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.ui.lyrics.isNeteaseYrc
import com.example.lxmusic.ui.lyrics.isTtmlLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 歌词三级缓存与并发加载仓库（内存 LruCache + 磁盘本地持久化 + 并发多源拉取 + 预加载）
 * 解决播放器打开时“歌词正在加载”卡顿几秒的问题，实现毫秒级秒开与无缝升级。
 */
object LyricRepository {
    private const val TAG = "LyricRepository"

    // 内存 LruCache（保留最近 200 首歌的歌词，0ms 响应）
    private val memoryCache = LruCache<String, String>(200)

    // 翻译内存缓存（进程内；主歌词已持久化，翻译体积小走内存即可）
    private val translationMemory = LruCache<String, String>(200)

    // 缓存版本：歌词来源策略变更（2026-10-25 改为平台原生精确匹配）后，
    // 旧缓存里可能存着跨平台按歌名搜到的串版本歌词，用版本前缀强制失效一次
    private const val CACHE_VERSION = "v2_"

    fun getCacheKey(song: SongInfo): String {
        if (song.filePath.startsWith("lxsrc://")) {
            return "lx_" + song.filePath.removePrefix("lxsrc://")
        }
        if (song.filePath.startsWith("tx://") || song.filePath.startsWith("kw://")) {
            return CACHE_VERSION + "third_" + song.filePath
        }
        val isNetease = NeteaseApi.isNeteasePath(song.filePath)
        if (isNetease) {
            val id = NeteaseApi.neteaseSongIdOf(song.filePath) ?: 0L
            return CACHE_VERSION + "netease_$id"
        }
        val isLocal = song.filePath.startsWith("/")
        if (!isLocal) {
            val hash = song.filePath.split("|").firstOrNull()?.takeIf { it.isNotBlank() }
            if (!hash.isNullOrBlank()) {
                return CACHE_VERSION + "kugou_$hash"
            }
        }
        return "meta_${song.title.trim()}_${song.artist.trim()}"
    }

    /**
     * 同步读取内存缓存（0ms 极速响应，专供 Compose 首帧无闪烁渲染）
     */
    fun getMemoryCachedLyric(song: SongInfo): String? {
        val key = getCacheKey(song)
        return memoryCache.get(key)
    }

    /**
     * 读取翻译内存缓存（LRC 文本，供逐字歌词视图的 translatedLyrics 管线）
     */
    fun getTranslationMemory(song: SongInfo): String? {
        return translationMemory.get(getCacheKey(song))
    }

    private fun putTranslationMemory(song: SongInfo, text: String) {
        if (text.isNotBlank()) {
            translationMemory.put(getCacheKey(song), text)
        }
    }

    private fun getDiskFile(context: Context, key: String): File {
        val dir = File(context.cacheDir, "lyrics_cache").apply { if (!exists()) mkdirs() }
        val md5 = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        val fileName = md5.joinToString("") { "%02x".format(it) } + ".lrc"
        return File(dir, fileName)
    }

    /**
     * 同步读取缓存：先查内存（0ms），再查磁盘（1-2ms）
     */
    fun getCachedLyric(context: Context, song: SongInfo): String? {
        val key = getCacheKey(song)
        // 1. 内存命中
        val mem = memoryCache.get(key)
        if (!mem.isNullOrBlank()) return mem

        // 2. 磁盘命中
        return try {
            val file = getDiskFile(context, key)
            if (file.exists() && file.length() > 0) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    memoryCache.put(key, content)
                    content
                } else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Read disk lyric failed: ${e.message}")
            null
        }
    }

    /**
     * 写入缓存（内存 + 异步磁盘）
     */
    suspend fun putCachedLyric(context: Context, song: SongInfo, lyric: String) = withContext(Dispatchers.IO) {
        if (lyric.isBlank()) return@withContext
        val key = getCacheKey(song)
        memoryCache.put(key, lyric)
        try {
            val file = getDiskFile(context, key)
            file.writeText(lyric)
        } catch (e: Exception) {
            Log.w(TAG, "Write disk lyric failed: ${e.message}")
        }
    }

    /**
     * 极速拉取平台原生歌词（网易云或酷狗），通常 150ms-200ms 以内返回
     */
    suspend fun fetchNativeLyric(song: SongInfo): String? = withContext(Dispatchers.IO) {
        // 插件音源：直接问插件要歌词（LRC/逐字由插件决定）
        if (song.filePath.startsWith("lxsrc://")) {
            val ctx = com.example.lxmusic.data.plugin.PluginContext.get()
            if (ctx != null) {
                val result = runCatching {
                    com.example.lxmusic.data.plugin.PluginSourceProvider.getLyricLegacy(ctx, song.filePath)
                }.getOrNull()
                result?.second?.takeIf { it.isNotBlank() }?.let { putTranslationMemory(song, it) }
                result?.first?.takeIf { it.isNotBlank() }?.let { return@withContext it }
            }
            return@withContext null
        }
        // QQ/酷我：内置链路的歌词接口已不可达（服务器内网化），只有插件能提供
        val thirdParty = when {
            song.filePath.startsWith("tx://") -> "tx"
            song.filePath.startsWith("kw://") -> "kw"
            else -> null
        }
        if (thirdParty != null) {
            // QQ：先按 songmid 精确取 QQ 自己的 QRC/LRC（比按歌名搜索可靠，不会串版本）
            if (thirdParty == "tx") {
                val songmid = song.filePath.removePrefix("tx://").substringBefore('?')
                if (songmid.isNotBlank()) {
                    val direct = runCatching {
                        com.example.lxmusic.data.qq.QQMusicApi.fetchLyricBySongmid(
                            songmid = songmid,
                            title = song.title,
                            singer = song.artist,
                            intervalSec = (song.duration / 1000).coerceAtLeast(0)
                        )
                    }.getOrNull()
                    direct?.second?.takeIf { it.isNotBlank() }?.let { putTranslationMemory(song, it) }
                    direct?.first?.takeIf { it.isNotBlank() }?.let { return@withContext it }
                }
            }
            // QQ 直连失败 / 酷我：走插件
            val ctx = com.example.lxmusic.data.plugin.PluginContext.get()
            if (ctx != null) {
                val rawId = song.filePath.substringAfter("://").substringBefore('?')
                val albumMid = runCatching {
                    android.net.Uri.parse(song.filePath).getQueryParameter("albummid")
                }.getOrNull()
                val musicInfo = org.json.JSONObject().apply {
                    put("id", rawId)
                    put("songmid", rawId)
                    if (thirdParty == "kw") put("hash", rawId)
                    if (thirdParty == "tx" && !albumMid.isNullOrBlank()) put("albumMid", albumMid)
                    put("name", song.title)
                    put("singer", song.artist)
                    put("interval", (song.duration / 1000).toInt())
                }.toString()
                val fromPlugin = runCatching {
                    com.example.lxmusic.data.plugin.PluginSourceProvider.resolveLyric(
                        ctx, thirdParty, musicInfo
                    )
                }.getOrNull()
                fromPlugin?.second?.takeIf { it.isNotBlank() }?.let { putTranslationMemory(song, it) }
                fromPlugin?.first?.takeIf { it.isNotBlank() }?.let { return@withContext it }
            }
            return@withContext null
        }
        // 插件优先：支持该平台的落雪源先尝试提供歌词（失败自动回退内置）
        val pluginCtx = com.example.lxmusic.data.plugin.PluginContext.get()
        if (pluginCtx != null) {
            val platform = when {
                NeteaseApi.isNeteasePath(song.filePath) -> "wy"
                !song.filePath.startsWith("/") -> "kg"
                else -> null
            }
            if (platform != null) {
                val musicInfo = org.json.JSONObject().apply {
                    if (platform == "wy") {
                        val id = NeteaseApi.neteaseSongIdOf(song.filePath) ?: 0L
                        put("id", id)
                        put("songmid", id.toString())
                    } else {
                        val hash = song.filePath.substringBefore('|')
                        put("hash", hash)
                        put("songmid", hash)
                        put("albumAudioId", song.filePath.substringAfter('|', "0"))
                    }
                    put("name", song.title)
                    put("singer", song.artist)
                    put("interval", (song.duration / 1000).toInt())
                }.toString()
                val fromPlugin = runCatching {
                    com.example.lxmusic.data.plugin.PluginSourceProvider.resolveLyric(
                        pluginCtx, platform, musicInfo
                    )
                }.getOrNull()
                fromPlugin?.second?.takeIf { it.isNotBlank() }?.let { putTranslationMemory(song, it) }
                fromPlugin?.first?.takeIf { it.isNotBlank() }?.let { return@withContext it }
            }
        }
        try {
            if (NeteaseApi.isNeteasePath(song.filePath)) {
                val id = NeteaseApi.neteaseSongIdOf(song.filePath) ?: return@withContext null
                val lyricResp = NeteaseApi.service.getLyric(id)
                // 顺带缓存网易云翻译（tlyric），翻译通路 0 额外请求
                lyricResp.tlyric?.lyric?.takeIf { it.isNotBlank() }?.let { putTranslationMemory(song, it) }
                val yrc = lyricResp.yrc?.lyric
                if (!yrc.isNullOrBlank()) return@withContext yrc
                val lrc = lyricResp.lrc?.lyric
                if (!lrc.isNullOrBlank()) return@withContext lrc
            } else if (!song.filePath.startsWith("/")) {
                val hash = song.filePath.split("|").firstOrNull()?.takeIf { it.isNotBlank() }
                if (!hash.isNullOrBlank()) {
                    val searchResp = KuGouApi.service.searchLyric(hash)
                    val candidate = searchResp.candidates?.firstOrNull()
                    if (candidate?.id != null && candidate.accesskey != null) {
                        // 1. 酷狗自己的逐字 KRC（按 hash 精确匹配，decodeContent 已解码）
                        val krc = runCatching {
                            KuGouApi.service.getLyric(
                                candidate.id,
                                candidate.accesskey,
                                fmt = "krc",
                                decode = "true"
                            )
                        }.getOrNull()
                        krc?.decodeContent?.takeIf { it.isNotBlank() }?.let { return@withContext it }
                        // 2. 普通 LRC 兜底
                        val lyricResp = runCatching {
                            KuGouApi.service.getLyric(candidate.id, candidate.accesskey, fmt = "lrc")
                        }.getOrNull()
                        val lrcText = lyricResp?.decodeContent?.takeIf { it.isNotBlank() }
                            ?: lyricResp?.content?.let { content ->
                                try {
                                    String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
                                } catch (_: Exception) {
                                    content
                                }
                            }
                        if (!lrcText.isNullOrBlank()) return@withContext lrcText
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchNativeLyric failed: ${e.message}")
        }
        null
    }

    /**
     * 平台对应的高精度逐字歌词：
     * - 网易云：AMLL（按网易云 id）→ 网易云 YRC，不再按歌名去 QQ 搜索
     * - 酷狗：逐字 KRC 已在 fetchNativeLyric 按 hash 精确取到，这里不再跨平台搜索
     * - QQ / 酷我 / 插件源：本平台直连或插件在 fetchNativeLyric 处理
     * - 本地文件：仍用 AMLL 按歌名+歌手+时长匹配
     */
    suspend fun fetchAmllLyric(song: SongInfo): String? = withContext(Dispatchers.IO) {
        try {
            when {
                NeteaseApi.isNeteasePath(song.filePath) -> {
                    val id = NeteaseApi.neteaseSongIdOf(song.filePath) ?: return@withContext null
                    // 1. AMLL TTML（按网易云 id 精确匹配）
                    val amll = AmllLyricFetcher.fetchAMLLByNeteaseId(id)
                    if (!amll.isNullOrBlank()) return@withContext amll
                    // 2. 网易云原生逐字 YRC
                    val lyricResp = NeteaseApi.service.getLyric(id)
                    lyricResp.tlyric?.lyric?.takeIf { it.isNotBlank() }?.let { putTranslationMemory(song, it) }
                    lyricResp.yrc?.lyric?.takeIf { it.isNotBlank() }?.let { return@withContext it }
                    null
                }
                song.filePath.startsWith("/") -> {
                    val result = AmllLyricFetcher.searchAndFetchAMLL(
                        title = song.title,
                        artist = song.artist,
                        durationMs = song.duration
                    )
                    if (!result.isNullOrBlank() && (isTtmlLyrics(result) || isNeteaseYrc(result))) result else null
                }
                // 在线歌曲：酷狗 KRC / QQ QRC / 酷我 / 插件均已在 fetchNativeLyric
                // 按本平台 id 精确匹配，这里返回 null，避免按歌名跨平台搜索导致歌词串版本
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAmllLyric failed: ${e.message}")
            null
        }
    }

    /**
     * 翻译兜底通路：主歌词已缓存（逐字命中、A/B 通路被跳过）时补拉翻译。
     * 网易云走 tlyric；酷狗/本地走 QQ 搜索通路只取 trans。
     */
    suspend fun fetchTranslation(song: SongInfo): String? = withContext(Dispatchers.IO) {
        getTranslationMemory(song)?.let { return@withContext it }
        try {
            if (NeteaseApi.isNeteasePath(song.filePath)) {
                val id = NeteaseApi.neteaseSongIdOf(song.filePath) ?: return@withContext null
                val lyricResp = NeteaseApi.service.getLyric(id)
                val t = lyricResp.tlyric?.lyric
                if (!t.isNullOrBlank()) {
                    putTranslationMemory(song, t)
                    return@withContext t
                }
                null
            } else {
                val (_, trans) = com.example.lxmusic.data.qq.QQMusicApi.searchAndFetchLyricWithTranslation(
                    title = song.title,
                    artist = song.artist,
                    durationMs = song.duration
                )
                if (!trans.isNullOrBlank()) {
                    putTranslationMemory(song, trans)
                    trans
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchTranslation failed: ${e.message}")
            null
        }
    }

    /**
     * 后台静默预加载指定歌曲（预加载下一首或当前列表曲目）
     */
    suspend fun preload(context: Context, song: SongInfo?) = withContext(Dispatchers.IO) {
        if (song == null) return@withContext
        val existing = getCachedLyric(context, song)
        if (!existing.isNullOrBlank() && (
                isTtmlLyrics(existing) ||
                    isNeteaseYrc(existing) ||
                    com.example.lxmusic.ui.lyrics.isQrcLyrics(existing) ||
                    com.example.lxmusic.ui.lyrics.isKrcLyrics(existing)
                )
        ) {
            return@withContext // 已经是最高精度逐字歌词，无需预加载
        }
        // 1. 极速先预加载 Native 确保保底命中（~150ms）；酷狗这里会拿到逐字 KRC，
        //    旧 LRC 缓存也顺带升级
        val native = fetchNativeLyric(song)
        if (!native.isNullOrBlank()) {
            val nativeIsBetter = existing.isNullOrBlank() ||
                isTtmlLyrics(native) ||
                isNeteaseYrc(native) ||
                com.example.lxmusic.ui.lyrics.isQrcLyrics(native) ||
                com.example.lxmusic.ui.lyrics.isKrcLyrics(native)
            if (nativeIsBetter) {
                putCachedLyric(context, song, native)
            }
        }
        // 2. 后台获取高精度逐字歌词（网易云 AMLL/YRC；本地文件 AMLL 匹配）
        val advanced = fetchAmllLyric(song)
        if (!advanced.isNullOrBlank()) {
            putCachedLyric(context, song, advanced)
        }
    }
}
