package com.example.lxmusic.data.plugin

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 插件歌曲原始对象存储：插件 getMusicUrl/getLyric 需要搜索时返回的完整对象，
 * 以 filePath（lxsrc://<id>/<key>）为键持久化，保证重启/收藏后仍可取流。
 */
object PluginSongStore {

    private const val TAG = "PluginSongStore"
    private const val FILE = "plugin_songs.json"
    private const val MAX_ENTRIES = 3000

    private val cache = ConcurrentHashMap<String, String>()
    private val loaded = AtomicBoolean(false)

    private fun file(context: Context): File = File(context.filesDir, FILE)

    private fun ensureLoaded(context: Context) {
        if (!loaded.compareAndSet(false, true)) return
        runCatching {
            val f = file(context)
            if (!f.exists()) return@runCatching
            val obj = JSONObject(f.readText())
            obj.keys().forEach { key -> cache[key] = obj.optString(key) }
        }.onFailure { Log.w(TAG, "加载失败: ${it.message}") }
    }

    fun put(context: Context, path: String, rawJson: String) {
        ensureLoaded(context)
        cache[path] = rawJson
        persist(context)
    }

    fun get(context: Context, path: String): JSONObject? {
        ensureLoaded(context)
        val raw = cache[path] ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun persist(context: Context) {
        runCatching {
            val snapshot = if (cache.size <= MAX_ENTRIES) cache.toMap()
            else cache.entries.toList().takeLast(MAX_ENTRIES).associate { it.key to it.value }
            val obj = JSONObject()
            snapshot.forEach { (k, v) -> obj.put(k, v) }
            file(context).writeText(obj.toString())
        }.onFailure { Log.w(TAG, "保存失败: ${it.message}") }
    }
}
