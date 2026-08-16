package com.example.lxmusic

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CollectionDao {

    // ========== 收藏歌曲 ==========

    @Query("SELECT * FROM collected_songs ORDER BY collectedAt DESC")
    suspend fun getAllCollectedSongs(): List<CollectedSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollectedSong(song: CollectedSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollectedSongs(songs: List<CollectedSongEntity>)

    @Query("DELETE FROM collected_songs WHERE filePath = :filePath")
    suspend fun deleteCollectedSong(filePath: String)

    @Query("SELECT EXISTS(SELECT 1 FROM collected_songs WHERE filePath = :filePath)")
    suspend fun isSongCollected(filePath: String): Boolean

    @Query("SELECT * FROM collected_songs WHERE filePath = :filePath LIMIT 1")
    suspend fun getSongByFilePath(filePath: String): CollectedSongEntity?

    @Query("SELECT COUNT(*) FROM collected_songs")
    suspend fun getCollectedSongCount(): Int

    // ========== 用户歌单 ==========

    @Query("SELECT * FROM user_playlists ORDER BY createdAt ASC")
    suspend fun getAllUserPlaylists(): List<UserPlaylistEntity>

    @Insert
    suspend fun createPlaylist(playlist: UserPlaylistEntity): Long

    @Query("DELETE FROM user_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deletePlaylistSongs(playlistId: Long)

    @androidx.room.Transaction
    suspend fun deletePlaylistWithSongs(playlistId: Long) {
        deletePlaylistSongs(playlistId)
        deletePlaylist(playlistId)
    }

    // ========== 歌单歌曲关联 ==========

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY rowid ASC")
    suspend fun getPlaylistSongs(playlistId: Long): List<PlaylistSongCrossRef>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songFilePath = :songFilePath")
    suspend fun removeSongFromPlaylist(playlistId: Long, songFilePath: String)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_songs WHERE playlistId = :playlistId AND songFilePath = :songFilePath)")
    suspend fun isSongInPlaylist(playlistId: Long, songFilePath: String): Boolean

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getPlaylistSongCount(playlistId: Long): Int

    // ========== 清除所有数据 ==========

    @Query("DELETE FROM collected_songs")
    suspend fun clearAllCollectedSongs()

    @Query("DELETE FROM playlist_songs")
    suspend fun clearAllPlaylistSongs()

    @Query("DELETE FROM user_playlists")
    suspend fun clearAllUserPlaylists()

    @androidx.room.Transaction
    suspend fun clearAllData() {
        clearAllPlaylistSongs()
        clearAllUserPlaylists()
        clearAllCollectedSongs()
    }
}
