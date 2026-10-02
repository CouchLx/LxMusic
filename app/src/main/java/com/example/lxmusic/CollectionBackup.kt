package com.example.lxmusic

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.InputStream
import java.io.OutputStream

/**
 * 本地收藏数据备份文件结构。
 *
 * v1：仅 5 类数据；v2 起追加 exportedAt / appVersion 元信息。
 * Gson 反序列化时缺失字段走默认值，所以 v1 老备份文件仍然可以直接导入。
 */
data class CollectionBackup(
    val version: Int = 2,
    val exportedAt: Long = 0L,
    val appVersion: String = "",
    val likedSongs: List<LikedSongEntity> = emptyList(),
    val likedPlaylists: List<LikedPlaylistEntity> = emptyList(),
    val collectedSongs: List<CollectedSongEntity> = emptyList(),
    val playlists: List<UserPlaylistEntity> = emptyList(),
    val playlistSongs: List<PlaylistSongCrossRef> = emptyList()
) {
    /** 五类数据是否全空（空备份没有导入价值） */
    val isEmpty: Boolean
        get() = likedSongs.isEmpty() && likedPlaylists.isEmpty() && collectedSongs.isEmpty() &&
                playlists.isEmpty() && playlistSongs.isEmpty()
}

/** 导出前的数据统计，用于确认弹窗与结果提示 */
data class BackupStats(
    val collectedCount: Int,
    val likedCount: Int,
    val likedPlaylistCount: Int,
    val playlistCount: Int,
    val playlistSongCount: Int
) {
    val isEmpty: Boolean
        get() = collectedCount == 0 && likedCount == 0 && likedPlaylistCount == 0 && playlistCount == 0

    val summary: String
        get() = "收藏歌曲 $collectedCount 首 · 喜欢 $likedCount 首 · 自建歌单 $playlistCount 个（$playlistSongCount 首）· 收藏歌单 $likedPlaylistCount 个"
}

/** 导入方式：合并共存（保留现有数据）/ 清空替换（先清空再完整还原） */
enum class ImportMode { MERGE, REPLACE }

/** 导入结果统计，用于 Toast 反馈 */
data class ImportReport(
    val mode: ImportMode,
    val collectedAdded: Int = 0,
    val likedAdded: Int = 0,
    val likedPlaylistsAdded: Int = 0,
    val playlistsAdded: Int = 0,
    val playlistsMerged: Int = 0,
    val playlistSongsAdded: Int = 0
) {
    val summary: String
        get() = if (mode == ImportMode.MERGE) {
            "新增歌曲 ${collectedAdded + likedAdded} 首、新增歌单 ${playlistsAdded + likedPlaylistsAdded} 个" +
                    (if (playlistsMerged > 0) "、合并同名歌单 $playlistsMerged 个" else "") +
                    (if (playlistSongsAdded > 0) "，歌单新增 $playlistSongsAdded 首" else "")
        } else {
            "歌曲 ${collectedAdded + likedAdded} 首、歌单 ${playlistsAdded + likedPlaylistsAdded} 个"
        }
}

object CollectionBackupIO {
    private val gson = Gson()

    private val KNOWN_FIELDS = listOf(
        "version", "exportedAt", "appVersion",
        "likedSongs", "likedPlaylists", "collectedSongs", "playlists", "playlistSongs"
    )

    /** 统计当前可导出的数据量（不读歌词等大字段之外的额外开销） */
    suspend fun stats(dao: CollectionDao): BackupStats {
        val playlists = dao.getAllUserPlaylists()
        return BackupStats(
            collectedCount = dao.getCollectedSongCount(),
            likedCount = dao.getLikedSongCount(),
            likedPlaylistCount = dao.getAllLikedPlaylists().size,
            playlistCount = playlists.size,
            playlistSongCount = playlists.sumOf { dao.getPlaylistSongCount(it.id) }
        )
    }

    /** 导出：把全部本地收藏数据（喜欢镜像/我的收藏/本地歌单及其歌曲）写成一个 JSON 文件 */
    suspend fun export(dao: CollectionDao, out: OutputStream): BackupStats {
        val playlistSongs = buildList {
            dao.getAllUserPlaylists().forEach { pl -> addAll(dao.getPlaylistSongs(pl.id)) }
        }
        val backup = CollectionBackup(
            version = 2,
            exportedAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            likedSongs = dao.getAllLikedSongs(),
            likedPlaylists = dao.getAllLikedPlaylists(),
            collectedSongs = dao.getAllCollectedSongs(),
            playlists = dao.getAllUserPlaylists(),
            playlistSongs = playlistSongs
        )
        out.bufferedWriter(Charsets.UTF_8).use { it.write(gson.toJson(backup)) }
        return BackupStats(
            collectedCount = backup.collectedSongs.size,
            likedCount = backup.likedSongs.size,
            likedPlaylistCount = backup.likedPlaylists.size,
            playlistCount = backup.playlists.size,
            playlistSongCount = backup.playlistSongs.size
        )
    }

    /** 解析备份文件（只读不改库）；返回 null 表示文件格式不正确 */
    suspend fun read(input: InputStream): CollectionBackup? {
        val json = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (json.isBlank()) return null
        val root = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull() ?: return null
        // Gson 会把任意 JSON 对象都填成默认值对象，先用字段名校验一次，避免把无关文件当成空备份
        if (KNOWN_FIELDS.none { root.has(it) }) return null
        return runCatching { gson.fromJson(root, CollectionBackup::class.java) }.getOrNull()
    }

    /** 应用备份数据。MERGE=保留现有数据只补缺失；REPLACE=清空后完整还原 */
    suspend fun apply(dao: CollectionDao, backup: CollectionBackup, mode: ImportMode): ImportReport {
        return if (mode == ImportMode.REPLACE) applyReplace(dao, backup) else applyMerge(dao, backup)
    }

    private suspend fun applyReplace(dao: CollectionDao, backup: CollectionBackup): ImportReport {
        // 先清空再恢复（逐表写入，Room 每条自带事务）
        dao.clearAllData()
        if (backup.collectedSongs.isNotEmpty()) dao.insertCollectedSongs(backup.collectedSongs)
        if (backup.likedSongs.isNotEmpty()) dao.insertLikedSongs(backup.likedSongs)
        if (backup.likedPlaylists.isNotEmpty()) dao.insertLikedPlaylists(backup.likedPlaylists)

        // 本地歌单：id 自增会变，建立 旧id→新id 映射后再写歌单歌曲（保留封面与创建时间）
        val idMap = HashMap<Long, Long>()
        backup.playlists.forEach { pl ->
            val newId = dao.createPlaylist(
                UserPlaylistEntity(name = pl.name, coverUrl = pl.coverUrl, createdAt = pl.createdAt)
            )
            idMap[pl.id] = newId
        }
        val remapped = backup.playlistSongs.mapNotNull { c ->
            val newPid = idMap[c.playlistId] ?: return@mapNotNull null
            c.copy(playlistId = newPid)
        }
        if (remapped.isNotEmpty()) dao.addSongsToPlaylist(remapped)

        return ImportReport(
            mode = ImportMode.REPLACE,
            collectedAdded = backup.collectedSongs.size,
            likedAdded = backup.likedSongs.size,
            likedPlaylistsAdded = backup.likedPlaylists.size,
            playlistsAdded = backup.playlists.size,
            playlistSongsAdded = remapped.size
        )
    }

    private suspend fun applyMerge(dao: CollectionDao, backup: CollectionBackup): ImportReport {
        // 歌曲表：主键 filePath，INSERT OR REPLACE 天然去重；先统计真正新增的数量
        val existingCollected = dao.getAllCollectedSongs().mapTo(HashSet()) { it.filePath }
        val collectedAdded = backup.collectedSongs.count { it.filePath !in existingCollected }
        if (backup.collectedSongs.isNotEmpty()) dao.insertCollectedSongs(backup.collectedSongs)

        val existingLiked = dao.getAllLikedSongs().mapTo(HashSet()) { it.filePath }
        val likedAdded = backup.likedSongs.count { it.filePath !in existingLiked }
        if (backup.likedSongs.isNotEmpty()) dao.insertLikedSongs(backup.likedSongs)

        // 收藏歌单（酷狗镜像）：按 gid 去重，gid 为空时按名字去重；id 置 0 交给自增，避免旧 id 覆盖现有行
        val existingLikedPlaylists = dao.getAllLikedPlaylists()
        val existingGids = existingLikedPlaylists.mapNotNullTo(HashSet()) { it.gid.takeIf { g -> g.isNotBlank() } }
        val existingLikedNames = existingLikedPlaylists.mapTo(HashSet()) { it.name }
        val newLikedPlaylists = backup.likedPlaylists.filterNot { pl ->
            (pl.gid.isNotBlank() && pl.gid in existingGids) || (pl.gid.isBlank() && pl.name in existingLikedNames)
        }.map { it.copy(id = 0) }
        if (newLikedPlaylists.isNotEmpty()) dao.insertLikedPlaylists(newLikedPlaylists)

        // 自建歌单：同名 → 歌曲合并进现有歌单；不同名 → 新建（保留封面/创建时间）
        val existingByName = dao.getAllUserPlaylists().groupBy { it.name.trim() }
        val idMap = HashMap<Long, Long>()
        var playlistsAdded = 0
        var playlistsMerged = 0
        backup.playlists.forEach { pl ->
            val target = existingByName[pl.name.trim()]?.firstOrNull()
            if (target != null) {
                idMap[pl.id] = target.id
                playlistsMerged++
            } else {
                idMap[pl.id] = dao.createPlaylist(
                    UserPlaylistEntity(name = pl.name, coverUrl = pl.coverUrl, createdAt = pl.createdAt)
                )
                playlistsAdded++
            }
        }

        val remapped = backup.playlistSongs.mapNotNull { c -> idMap[c.playlistId]?.let { c.copy(playlistId = it) } }
        var playlistSongsAdded = 0
        if (remapped.isNotEmpty()) {
            val targetExisting = HashMap<Long, MutableSet<String>>()
            remapped.forEach { c ->
                val set = targetExisting.getOrPut(c.playlistId) {
                    dao.getPlaylistSongs(c.playlistId).mapTo(HashSet()) { it.songFilePath }
                }
                if (set.add(c.songFilePath)) playlistSongsAdded++
            }
            dao.addSongsToPlaylist(remapped)
        }

        return ImportReport(
            mode = ImportMode.MERGE,
            collectedAdded = collectedAdded,
            likedAdded = likedAdded,
            likedPlaylistsAdded = newLikedPlaylists.size,
            playlistsAdded = playlistsAdded,
            playlistsMerged = playlistsMerged,
            playlistSongsAdded = playlistSongsAdded
        )
    }
}
