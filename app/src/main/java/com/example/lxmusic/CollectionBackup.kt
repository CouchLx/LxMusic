package com.example.lxmusic

import com.google.gson.Gson
import java.io.InputStream
import java.io.OutputStream

/** 本地收藏数据备份文件结构（喜欢镜像 + 我的收藏 + 本地歌单 + 歌单内歌曲） */
data class CollectionBackup(
    val version: Int = 1,
    val likedSongs: List<LikedSongEntity> = emptyList(),
    val likedPlaylists: List<LikedPlaylistEntity> = emptyList(),
    val collectedSongs: List<CollectedSongEntity> = emptyList(),
    val playlists: List<UserPlaylistEntity> = emptyList(),
    val playlistSongs: List<PlaylistSongCrossRef> = emptyList()
)

object CollectionBackupIO {
    private val gson = Gson()

    /** 导出：把全部本地收藏数据（喜欢镜像/我的收藏/本地歌单及其歌曲）写成一个 JSON 文件 */
    suspend fun export(dao: CollectionDao, out: OutputStream) {
        val playlistSongs = buildList {
            dao.getAllUserPlaylists().forEach { pl ->
                addAll(dao.getPlaylistSongs(pl.id))
            }
        }
        val backup = CollectionBackup(
            likedSongs = dao.getAllLikedSongs(),
            likedPlaylists = dao.getAllLikedPlaylists(),
            collectedSongs = dao.getAllCollectedSongs(),
            playlists = dao.getAllUserPlaylists(),
            playlistSongs = playlistSongs
        )
        out.bufferedWriter(Charsets.UTF_8).use { it.write(gson.toJson(backup)) }
    }

    /** 导入：清空本地全部收藏数据并恢复备份；返回备份对象（null=解析失败） */
    suspend fun import(db: MusicDatabase, dao: CollectionDao, input: InputStream): CollectionBackup? {
        val json = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val backup = runCatching { gson.fromJson(json, CollectionBackup::class.java) }.getOrNull() ?: return null
        // 先清空再恢复（逐表写入，Room 每条自带事务）
        dao.clearAllData()
        if (backup.collectedSongs.isNotEmpty()) dao.insertCollectedSongs(backup.collectedSongs)
        if (backup.likedSongs.isNotEmpty()) dao.insertLikedSongs(backup.likedSongs)
        if (backup.likedPlaylists.isNotEmpty()) dao.insertLikedPlaylists(backup.likedPlaylists)
            // 本地歌单：id 自增会变，建立 旧id→新id 映射后再写歌单歌曲
        val idMap = HashMap<Long, Long>()
        backup.playlists.forEach { pl ->
            val newId = dao.createPlaylist(UserPlaylistEntity(name = pl.name, createdAt = pl.createdAt))
            idMap[pl.id] = newId
        }
        val remapped = backup.playlistSongs.mapNotNull { c ->
            val newPid = idMap[c.playlistId] ?: return@mapNotNull null
            c.copy(playlistId = newPid)
        }
        if (remapped.isNotEmpty()) dao.addSongsToPlaylist(remapped)
        return backup
    }
}