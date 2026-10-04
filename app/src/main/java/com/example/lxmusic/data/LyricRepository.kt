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

    fun getCacheKey(song: SongInfo): String {
        val isNetease = NeteaseApi.isNeteasePath(song.filePath)
        if (isNetease) {
            val id = NeteaseApi.neteaseSongIdOf(song.filePath) ?: 0L
            return "netease_$id"
        }
        val isLocal = song.filePath.startsWith("/")
        if (!isLocal) {
            val hash = song.filePath.split("|").firstOrNull()?.takeIf { it.isNotBlank() }
            if (!hash.isNullOrBlank()) {
                return "kugou_$hash"
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
        try {
            if (NeteaseApi.isNeteasePath(song.filePath)) {
                val id = NeteaseApi.neteaseSongIdOf(song.filePath) ?: return@withContext null
                val lyricResp = NeteaseApi.service.getLyric(id)
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
                        val lyricResp = KuGouApi.service.getLyric(candidate.id, candidate.accesskey)
                        val content = lyricResp.content
                        if (!content.isNullOrBlank()) {
                            val decoded = try {
                                String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
                            } catch (_: Exception) { content }
                            if (decoded.isNotBlank()) return@withContext decoded
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchNativeLyric failed: ${e.message}")
        }
        null
    }

    /**
     * 拉取 AMLL TTML 逐字歌词（高品质音节与间奏）或降级网易云 YRC 逐字
     */
    suspend fun fetchAmllLyric(song: SongInfo): String? = withContext(Dispatchers.IO) {
        try {
            if (NeteaseApi.isNeteasePath(song.filePath)) {
                val id = NeteaseApi.neteaseSongIdOf(song.filePath) ?: return@withContext null
                // 1. 优先从 AMLL TTML 库获取
                val amll = AmllLyricFetcher.fetchAMLLByNeteaseId(id)
                if (!amll.isNullOrBlank()) return@withContext amll
                // 2. 网易云原生逐字 YRC
                val lyricResp = NeteaseApi.service.getLyric(id)
                val yrc = lyricResp.yrc?.lyric
                if (!yrc.isNullOrBlank()) return@withContext yrc
                // 3. QQ 音乐 QRC 逐字库（全网最全逐字）
                val qqQrc = com.example.lxmusic.data.qq.QQMusicApi.searchAndFetchBestQrc(
                    title = song.title,
                    artist = song.artist,
                    durationMs = song.duration
                )
                if (!qqQrc.isNullOrBlank()) return@withContext qqQrc
                // 4. 网易云普通 LRC 兜底
                val lrc = lyricResp.lrc?.lyric
                if (!lrc.isNullOrBlank()) return@withContext lrc
                null
            } else {
                val result = AmllLyricFetcher.searchAndFetchAMLL(
                    title = song.title,
                    artist = song.artist,
                    durationMs = song.duration
                )
                if (!result.isNullOrBlank() && (isTtmlLyrics(result) || isNeteaseYrc(result))) {
                    return@withContext result
                }
                // 若网易云与 AMLL 均无逐字，由 QQ 音乐 QRC 兜底
                val qqQrc = com.example.lxmusic.data.qq.QQMusicApi.searchAndFetchBestQrc(
                    title = song.title,
                    artist = song.artist,
                    durationMs = song.duration
                )
                if (!qqQrc.isNullOrBlank()) return@withContext qqQrc
                result
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAmllLyric failed: ${e.message}")
            null
        }
    }

    /**
     * 后台静默预加载指定歌曲（预加载下一首或当前列表曲目）
     */
    suspend fun preload(context: Context, song: SongInfo?) = withContext(Dispatchers.IO) {
        if (song == null) return@withContext
        val existing = getCachedLyric(context, song)
        if (!existing.isNullOrBlank() && (isTtmlLyrics(existing) || isNeteaseYrc(existing) || com.example.lxmusic.ui.lyrics.isQrcLyrics(existing))) {
            return@withContext // 已经是最高精度逐字歌词，无需预加载
        }
        // 1. 极速先预加载 Native 确保保底命中（~150ms）
        if (existing.isNullOrBlank()) {
            val native = fetchNativeLyric(song)
            if (!native.isNullOrBlank()) {
                putCachedLyric(context, song, native)
            }
        }
        // 2. 后台获取最高精度逐字歌词（AMLL TTML、网易云 YRC 或 QQ 音乐 QRC）并升级持久化
        val advanced = fetchAmllLyric(song)
        if (!advanced.isNullOrBlank()) {
            putCachedLyric(context, song, advanced)
        }
    }
}
