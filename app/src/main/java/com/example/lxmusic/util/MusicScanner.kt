package com.example.lxmusic.util

import android.content.Context
import android.os.Build
import com.example.lxmusic.SongDao
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.model.toEntity
import com.example.lxmusic.model.toSongInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun loadCachedSongs(
    songDao: SongDao,
    onResult: (List<SongInfo>) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        val cachedSongs = songDao.getAllSongs()
        val songInfoList = cachedSongs.map { it.toSongInfo() }
        withContext(Dispatchers.Main) {
            onResult(songInfoList)
        }
    }
}

fun scanForNewSongs(
    context: Context,
    songDao: SongDao,
    onResult: (List<SongInfo>) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        // 先快速扫描（不提取歌词），拿到基本元数据。
        // 这里用 minDurationMs = 0 拿全部文件路径做比对：<60s 的短歌曲（铃声/通知音）
        // 虽然不展示，但不能因此把它们当"已删除"清库。
        val scannedAll = scanLocalMusicViaMediaStore(context, extractLyrics = false, minDurationMs = 0L)
        val scannedPaths = scannedAll.map { it.filePath }.toSet()
        // 展示/入库时仍过滤掉 <60s 的杂音，保持原行为
        val scannedSongs = scannedAll.filter { it.duration >= 60_000 }

        // 已有歌曲的歌词映射（从数据库读取，避免重复提取）
        val existingEntities = songDao.getAllSongs()
        val existingLyricsMap = existingEntities.associate { it.filePath to it.lyrics }
        val existingPaths = existingLyricsMap.keys

        // 清理已删除的歌曲：仅当文件在磁盘上真实不存在时才删除，
        // 避免短歌曲被 <60s 过滤后误删，也避免临时挂载失败时误清库
        val deletedPaths = existingPaths.filter { path ->
            path !in scannedPaths && !java.io.File(path).exists()
        }
        if (deletedPaths.isNotEmpty()) {
            songDao.deleteSongsByPaths(deletedPaths)
        }

        // 为已有歌曲补上数据库中的歌词，为新歌曲提取歌词
        val newSongPaths = scannedPaths - existingPaths
        val songsWithLyrics = scannedSongs.map { song ->
            if (song.filePath in newSongPaths) {
                // 新歌曲：提取歌词
                song.copy(lyrics = extractLyrics(song.filePath))
            } else {
                val cachedLyrics = existingLyricsMap[song.filePath]
                if (cachedLyrics.isNullOrBlank()) {
                    // 已有歌曲但无歌词：重新尝试提取（可能用户新添加了 .lrc 文件）
                    song.copy(lyrics = extractLyrics(song.filePath))
                } else {
                    // 已有歌曲且有歌词：用数据库中的歌词
                    song.copy(lyrics = cachedLyrics)
                }
            }
        }

        // 保存新歌曲和歌词更新到数据库
        val entitiesToSave = songsWithLyrics
            .filter { it.filePath in newSongPaths || (it.filePath in existingPaths && existingLyricsMap[it.filePath].isNullOrBlank() && !it.lyrics.isNullOrBlank()) }
            .map { it.toEntity() }
        if (entitiesToSave.isNotEmpty()) {
            songDao.insertSongs(entitiesToSave)
        }

        withContext(Dispatchers.Main) {
            onResult(songsWithLyrics)
        }
    }
}

fun fullScanAndSave(
    context: Context,
    songDao: SongDao,
    onResult: (List<SongInfo>) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        val scannedSongs = scanLocalMusicViaMediaStore(context)
        songDao.insertSongs(scannedSongs.map { it.toEntity() })
        withContext(Dispatchers.Main) {
            onResult(scannedSongs)
        }
    }
}

fun scanLocalMusicViaMediaStore(
    context: Context,
    extractLyrics: Boolean = true,
    minDurationMs: Long = 60_000
): List<SongInfo> {
    val songs = mutableListOf<SongInfo>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL)
    } else {
        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
        android.provider.MediaStore.Audio.Media._ID,
        android.provider.MediaStore.Audio.Media.TITLE,
        android.provider.MediaStore.Audio.Media.ARTIST,
        android.provider.MediaStore.Audio.Media.DATA,
        android.provider.MediaStore.Audio.Media.DURATION,
        android.provider.MediaStore.Audio.Media.ALBUM_ID
    )
    val selection = "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${android.provider.MediaStore.Audio.Media.TITLE} ASC"

    context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
        val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
        val durationCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
        val albumIdCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM_ID)

        // 用 albumId 去重封面 URI，同专辑共用一张封面
        val albumArtUriMap = mutableMapOf<Long, String?>()

        while (cursor.moveToNext()) {
            val title = cursor.getString(titleCol) ?: "未知歌曲"
            val artist = cursor.getString(artistCol) ?: "未知艺术家"
            val filePath = cursor.getString(dataCol) ?: continue
            val duration = cursor.getLong(durationCol)
            // 过滤掉小于阈值的铃声、通知音等杂音（默认60秒）
            if (minDurationMs > 0 && duration < minDurationMs) continue
            val albumId = cursor.getLong(albumIdCol)

            // 用 albumId 构建封面 URI（系统已缓存的封面文件，不需要打开音频文件）
            val albumArtUri = albumArtUriMap.getOrPut(albumId) {
                try {
                    val artUri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        albumId
                    )
                    // 检查这个专辑是否有封面
                    val artCursor = context.contentResolver.query(
                        artUri,
                        arrayOf(android.provider.MediaStore.Audio.Albums.ALBUM_ART),
                        null, null, null
                    )
                    artCursor?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                } catch (_: Exception) {
                    null
                }
            }

            // 提取歌词（内嵌 + 外部 .lrc 文件），快速扫描时跳过
            val lyrics = if (extractLyrics) extractLyrics(filePath) else null

            songs.add(
                SongInfo(
                    title = title,
                    artist = artist,
                    filePath = filePath,
                    albumArtUri = albumArtUri,
                    duration = duration,
                    lyrics = lyrics
                )
            )
        }
    }
    return songs
}
