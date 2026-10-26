package com.example.lxmusic.data.plugin

import android.content.Context
import android.util.Log
import com.example.lxmusic.model.SongInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 插件音源提供者。
 *
 * 落雪自定义源的定位是「解析器」：搜索由 App 自己做，插件负责把已知歌曲解析成
 * 播放地址/歌词/封面（lx.on(EVENT_NAMES.request, ...)）。
 *
 * 同时保留对早期 module.exports.api 简化格式（含 search/getMusicUrl）的兼容。
 */
object PluginSourceProvider {

    private const val TAG = "PluginSource"
    private const val PREFS = "settings"
    private const val KEY_RESOLVE_FIRST = "plugin_resolve_first"

    private val gson = Gson()
    private val runtimes = ConcurrentHashMap<String, LxPluginRuntime>()

    /** 是否优先用已启用插件解析播放地址（失败自动回退内置链路） */
    fun isResolveFirst(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_RESOLVE_FIRST, true)

    fun setResolveFirst(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_RESOLVE_FIRST, enabled).apply()
    }

    private fun runtime(context: Context, pluginId: String): LxPluginRuntime? {
        runtimes[pluginId]?.let { return it }
        val source = PluginStore.readSource(context, pluginId) ?: return null
        val rt = LxPluginRuntime(pluginId, source)
        return try {
            rt.start()
            runtimes.putIfAbsent(pluginId, rt) ?: rt
        } catch (t: Throwable) {
            Log.w(TAG, "插件加载失败 $pluginId: ${t.message}")
            runCatching { rt.close() }
            PluginStore.updateError(context, pluginId, t.message)
            null
        }
    }

    fun close(pluginId: String) {
        runtimes.remove(pluginId)?.let { runCatching { it.close() } }
    }

    fun closeAll() {
        runtimes.keys.toList().forEach { close(it) }
    }

    private fun platformsOf(plugin: PluginStore.PluginInfo): List<LxPluginRuntime.Platform> {
        if (plugin.platforms.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<LxPluginRuntime.Platform>>(
                plugin.platforms,
                object : TypeToken<List<LxPluginRuntime.Platform>>() {}.type
            )
        }.getOrElse { emptyList() }
    }

    fun supportedPlatformNames(plugin: PluginStore.PluginInfo): String =
        platformsOf(plugin).joinToString("、") { it.name }

    fun hasPlatform(plugin: PluginStore.PluginInfo, platform: String, action: String): Boolean {
        val p = platformsOf(plugin).firstOrNull { it.key == platform } ?: return false
        return p.actions.contains(action)
    }

    // ==================== 落雪事件协议：解析播放地址 / 歌词 / 封面 ====================

    /** 最近一次取流失败原因（供播放失败提示） */
    @Volatile
    var lastResolveError: String? = null
        private set

    fun resolveMusicUrl(
        context: Context,
        platform: String,
        musicInfoJson: String,
        requestedQuality: String,
        timeoutMs: Long = 6_000
    ): String? {
        if (!isResolveFirst(context)) return null
        lastResolveError = null
        var anyPlugin = false
        for (plugin in PluginStore.enabled(context)) {
            val plat = platformsOf(plugin).firstOrNull { it.key == platform } ?: continue
            if (!plat.actions.contains("musicUrl")) continue
            anyPlugin = true
            val rt = runtime(context, plugin.id) ?: run {
                lastResolveError = "${plugin.name}: 插件加载失败"
                continue
            }
            val quality = pickQuality(plat.qualities, requestedQuality)
            val infoJson = """{"musicInfo":$musicInfoJson,"type":${gson.toJson(quality)}}"""
            val result = rt.request(platform, "musicUrl", infoJson, timeoutMs) ?: run {
                lastResolveError = "${plugin.name}: ${rt.lastError ?: "未返回播放地址"}"
                continue
            }
            val url = unwrapUrl(result)?.takeIf { it.startsWith("http", ignoreCase = true) }
            if (!url.isNullOrBlank()) {
                Log.d(TAG, "插件解析成功: ${plugin.name} $platform -> ${url.take(80)}")
                return url
            }
            lastResolveError = "${plugin.name}: 返回内容不是有效链接"
        }
        if (!anyPlugin) lastResolveError = "没有启用支持该平台的音源插件"
        return null
    }

    fun resolveLyric(
        context: Context,
        platform: String,
        musicInfoJson: String,
        timeoutMs: Long = 8_000
    ): Pair<String?, String?>? {
        if (!isResolveFirst(context)) return null
        for (plugin in PluginStore.enabled(context)) {
            val plat = platformsOf(plugin).firstOrNull { it.key == platform } ?: continue
            if (!plat.actions.contains("lyric")) continue
            val rt = runtime(context, plugin.id) ?: continue
            val infoJson = """{"musicInfo":$musicInfoJson}"""
            val result = rt.request(platform, "lyric", infoJson, timeoutMs) ?: continue
            val parsed = parseLyricResult(result) ?: continue
            if (!parsed.first.isNullOrBlank() || !parsed.second.isNullOrBlank()) return parsed
        }
        return null
    }

    fun resolvePic(context: Context, platform: String, musicInfoJson: String): String? {
        if (!isResolveFirst(context)) return null
        for (plugin in PluginStore.enabled(context)) {
            val plat = platformsOf(plugin).firstOrNull { it.key == platform } ?: continue
            if (!plat.actions.contains("pic")) continue
            val rt = runtime(context, plugin.id) ?: continue
            val infoJson = """{"musicInfo":$musicInfoJson}"""
            val result = rt.request(platform, "pic", infoJson) ?: continue
            val url = unwrapUrl(result)?.takeIf { it.startsWith("http", ignoreCase = true) }
            if (!url.isNullOrBlank()) return url
        }
        return null
    }

    private fun pickQuality(available: List<String>, requested: String): String {
        if (available.isEmpty()) {
            return when (requested) {
                "flac", "high" -> "flac"
                "320" -> "320k"
                "128" -> "128k"
                else -> requested
            }
        }
        val req = when (requested) {
            "flac", "high" -> "flac"
            "320" -> "320k"
            "128" -> "128k"
            else -> requested
        }
        if (req in available) return req
        for (candidate in listOf("flac", "320k", "128k")) {
            if (candidate in available) return candidate
        }
        return available.first()
    }

    private fun parseLyricResult(json: String): Pair<String?, String?>? {
        val trimmed = json.trim()
        if (trimmed.startsWith("{")) {
            val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
            val lyric = firstString(obj, "lyric", "lrc", "content")
            val trans = firstString(obj, "tlyric", "trans", "translation")
            if (lyric != null || trans != null) return lyric to trans
        }
        val text = runCatching { gson.fromJson(trimmed, String::class.java) }.getOrNull()
            ?: return null
        return text.takeIf { it.isNotBlank() } to null
    }

    private fun unwrapUrl(json: String): String? {
        val trimmed = json.trim()
        if (trimmed.startsWith("\"")) {
            return runCatching { gson.fromJson(trimmed, String::class.java) }.getOrNull()
        }
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        return firstString(obj, "url", "musicUrl", "playUrl", "src")
    }

    // ==================== 兼容：早期 module.exports.api 插件（含搜索） ====================

    fun search(context: Context, pluginId: String, keyword: String, page: Int = 1, limit: Int = 30): List<SongInfo> {
        val rt = runtime(context, pluginId) ?: return emptyList()
        val resultJson = rt.callApi("search", listOf(keyword, page, limit)) ?: return emptyList()
        return extractSongArray(resultJson).mapNotNull { item -> toSongInfo(context, pluginId, item) }
    }

    private fun extractSongArray(json: String): List<JSONObject> {
        val trimmed = json.trim()
        runCatching { JSONArray(trimmed) }.getOrNull()?.let { return it.toObjectList() }
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
        for (key in listOf("list", "data", "songs", "result")) {
            obj.optJSONArray(key)?.let { return it.toObjectList() }
            obj.optJSONObject(key)?.let { nested ->
                for (nestedKey in listOf("list", "songs", "data")) {
                    nested.optJSONArray(nestedKey)?.let { return it.toObjectList() }
                }
            }
        }
        return emptyList()
    }

    private fun JSONArray.toObjectList(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    private fun toSongInfo(context: Context, pluginId: String, item: JSONObject): SongInfo? {
        val title = firstString(item, "name", "songname", "title", "songName") ?: return null
        val artist = artistOf(item)
        val pic = firstString(item, "pic", "cover", "img", "albumPic", "picUrl").orEmpty()
        val duration = durationMsOf(item)
        val raw = item.toString()
        val path = "lxsrc://$pluginId/${sha1(raw)}"
        PluginSongStore.put(context, path, raw)
        return SongInfo(
            title = title,
            artist = artist.ifBlank { "未知艺术家" },
            filePath = path,
            albumArtUri = pic,
            duration = duration
        )
    }

    private fun artistOf(item: JSONObject): String {
        firstString(item, "singer", "artist", "author", "singername", "artistName")?.let { return it }
        item.optJSONArray("ar")?.let { arr ->
            val names = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
            }
            if (names.isNotEmpty()) return names.joinToString("、")
        }
        item.optJSONArray("singer")?.let { arr ->
            val names = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: arr.optString(i).takeIf { it.isNotBlank() }
            }
            if (names.isNotEmpty()) return names.joinToString("、")
        }
        return ""
    }

    private fun durationMsOf(item: JSONObject): Long {
        item.optLong("duration", 0L).takeIf { it > 0 }?.let { return it }
        item.optDouble("interval", 0.0).takeIf { it > 0 }?.let { return (it * 1000).toLong() }
        item.optLong("dt", 0L).takeIf { it > 0 }?.let { return it }
        return 0L
    }

    fun getMusicUrlLegacy(context: Context, path: String, quality: String): String? {
        val pluginId = pluginIdOf(path) ?: return null
        val raw = PluginSongStore.get(context, path)?.toString() ?: return null
        val rt = runtime(context, pluginId) ?: return null
        val songArg = runCatching { gson.fromJson(raw, Map::class.java) }.getOrNull() ?: return null
        val q = when (quality) {
            "flac", "high" -> "flac"
            "320" -> "320k"
            "128" -> "128k"
            else -> quality
        }
        val result = rt.callApi("getMusicUrl", listOf(songArg, q)) ?: return null
        return unwrapUrl(result)
    }

    fun getLyricLegacy(context: Context, path: String): Pair<String?, String?>? {
        val pluginId = pluginIdOf(path) ?: return null
        val raw = PluginSongStore.get(context, path)?.toString() ?: return null
        val rt = runtime(context, pluginId) ?: return null
        val songArg = runCatching { gson.fromJson(raw, Map::class.java) }.getOrNull() ?: return null
        val result = rt.callApi("getLyric", listOf(songArg)) ?: return null
        return parseLyricResult(result)
    }

    fun pluginIdOf(path: String): String? =
        if (path.startsWith("lxsrc://")) path.removePrefix("lxsrc://").substringBefore('/') else null

    fun isPluginPath(path: String): Boolean = path.startsWith("lxsrc://")

    private fun firstString(obj: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            val v = obj.optString(k, "")
            if (v.isNotBlank() && v != "null") return v
        }
        return null
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/** Application Context holder：DataSource/歌词仓库等无 Context 的组件使用 */
object PluginContext {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(): Context? = appContext
}

/** 当前播放歌曲（DataSource 无法拿到 SongInfo 元信息，用它补全插件的 musicInfo） */
object PluginPlayback {
    @Volatile
    var currentSong: SongInfo? = null
}
