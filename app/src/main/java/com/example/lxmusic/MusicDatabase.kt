package com.example.lxmusic

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SongEntity::class, CollectedSongEntity::class, UserPlaylistEntity::class, PlaylistSongCrossRef::class, LikedSongEntity::class, LikedPlaylistEntity::class],
    version = 8,
    exportSchema = true
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        // v5 → v6：新增「喜欢镜像」两张表（官方收藏模式本地先写），不丢任何老数据
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `liked_songs` (`filePath` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `albumArtUri` TEXT, `duration` INTEGER NOT NULL, `hash` TEXT NOT NULL, `audioId` INTEGER NOT NULL, `albumId` INTEGER NOT NULL, `mixsongid` INTEGER NOT NULL, `likedAt` INTEGER NOT NULL, PRIMARY KEY(`filePath`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `liked_playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `gid` TEXT NOT NULL, `coverUrl` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        // v6 → v7：liked_playlists 记录原歌单真实歌曲数（卡片显示"X 首歌曲"而非"已收藏"）
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `liked_playlists` ADD COLUMN `songcount` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v7 → v8：playlist_songs 增加 lyrics 列（本地歌曲歌词离线可用）
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlist_songs` ADD COLUMN `lyrics` TEXT")
            }
        }

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "lx_music_database"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}