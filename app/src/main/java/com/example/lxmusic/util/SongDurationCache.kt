package com.example.lxmusic.util

object SongDurationCache {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun get(hash: String): Long? = cache[hash]

    fun put(hash: String, durationMs: Long) {
        cache[hash] = durationMs
    }
}
