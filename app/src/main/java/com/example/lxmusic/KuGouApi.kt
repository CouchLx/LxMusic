package com.example.lxmusic

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import com.example.lxmusic.model.SongInfo

// ==================== 排行榜列表 ====================

data class RankListResponse(
    val status: Int = 0,
    val data: RankListData? = null
)

data class RankListData(
    val info: List<RankItem>? = null,
    val total: Int = 0
)

/** 单个排行榜 */
data class RankItem(
    val rankid: Long = 0,
    val rankname: String? = null,
    val album_img_9: String? = null,
    val img_9: String? = null,
    val banner_9: String? = null,
    val classify: Int = 0,
    val play_times: Long = 0,
    val update_frequency_type: Int = 0,
    val songinfo: List<RankSongBrief>? = null
) {
    val coverUrl: String
        get() {
            val raw = album_img_9 ?: img_9 ?: banner_9 ?: ""
            if (raw.isBlank()) return ""
            return raw.replace("{size}", "240").replace("http://", "https://")
        }
}

/** 排行榜预览歌曲 */
data class RankSongBrief(
    val name: String? = null,
    val author: String? = null,
    val songname: String? = null,
    val album_audio_id: Long = 0
)

// ==================== 排行榜歌曲列表 ====================

data class RankAudioResponse(
    val status: Int = 0,
    val errcode: Int = 0,
    val data: RankAudioData? = null
)

data class RankAudioData(
    val songlist: List<RankSong>? = null,
    val total: Int = 0
)

/** 排行榜歌曲 */
data class RankSong(
    val songname: String? = null,
    val author_name: String? = null,
    val album_audio_id: Long = 0,
    val album_id: Long = 0,
    val audio_info: RankAudioInfo? = null,
    val album_info: RankAlbumInfo? = null,
    val trans_param: RankTransParam? = null
) {
    val title: String
        get() {
            val sn = songname ?: "未知歌曲"
            return sn.substringBefore(" - ").ifBlank { sn }.trim()
        }
    val artist: String
        get() {
            if (!author_name.isNullOrBlank()) return author_name
            val sn = songname ?: ""
            val part = sn.substringAfter(" - ", "").trim()
            return part.ifBlank { "未知艺术家" }
        }
    val hash: String
        get() = audio_info?.hash_320 ?: audio_info?.hash_128 ?: ""
    val durationMs: Long
        get() = (audio_info?.duration_320 ?: audio_info?.duration_128 ?: 0).toLong()
    val coverUrl: String
        get() {
            val raw = trans_param?.union_cover ?: album_info?.sizable_cover ?: ""
            if (raw.isBlank()) return ""
            return raw.replace("{size}", "240").replace("http://", "https://")
        }
}

data class RankAudioInfo(
    val hash_128: String? = null,
    val hash_320: String? = null,
    val hash_flac: String? = null,
    val duration_128: Int = 0,
    val duration_320: Int = 0,
    val bitrate: Int = 0
)

data class RankAlbumInfo(
    val sizable_cover: String? = null,
    val album_name: String? = null
)

data class RankTransParam(
    val union_cover: String? = null
)

// ==================== 歌曲播放 URL ====================

data class SongUrlResponse(
    val status: Int = 0,
    val errcode: Int = 0,
    val url: List<String>? = null,
    @com.google.gson.annotations.SerializedName("backupUrl")
    val backup_url: List<String>? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val bitRate: Int = 0,
    val extName: String? = null,
    val hash: String? = null,
    val timeLength: Int = 0
) {
    val play_url: String? get() = url?.firstOrNull()
    val play_backup_url: String? get() = backup_url?.firstOrNull()
}

// ==================== 登录 ====================

/** 设备注册响应 */
data class RegisterDevResponse(
    val status: Int = 0,
    val data: RegisterDevData? = null
)

data class RegisterDevData(
    val dfid: String? = null
)

/** 发送验证码响应 */
data class CaptchaResponse(
    val status: Int = 0,
    val errcode: Int = 0,
    val data: CaptchaData? = null
)

data class CaptchaData(
    val msg: String? = null
)

/** 登录响应 */
data class LoginResponse(
    val status: Int = 0,
    val errcode: Int = 0,
    val data: LoginData? = null
)

data class LoginData(
    val token: String? = null,
    val userid: Long = 0,
    val username: String? = null,
    val nickname: String? = null,
    val pic: String? = null,
    val vip_type: Int = 0
)

/** 用户详情响应 */
data class UserDetailResponse(
    val status: Int = 0,
    val data: UserDetailData? = null
)

data class UserDetailData(
    val userid: Long = 0,
    val nickname: String? = null,
    val pic: String? = null,
    val username: String? = null,
    val vip_type: Int = 0
)

// ==================== 每日推荐 ====================

data class DailyRecommendResponse(
    val status: Int = 0,
    val data: DailyRecommendData? = null
)

data class DailyRecommendData(
    @com.google.gson.annotations.SerializedName("song_list")
    val list: List<DailyRecommendSong>? = null
)

// 历史推荐列表响应
data class HistoryListResponse(
    val status: Int = 0,
    val data: HistoryListData? = null
)

data class HistoryListData(
    val list: List<HistoryItem>? = null
)

data class HistoryItem(
    val history_name: String? = null,
    val date: String? = null,
    val cover: String? = null
)

data class DailyRecommendSong(
    val songname: String? = null,
    val author_name: String? = null,
    val hash: String? = null,
    val album_img: String? = null,
    val sizable_cover: String? = null,
    val union_cover: String? = null,
    val album_audio_id: Long = 0,
    val album_id: Long = 0,
    val duration: Int = 0,
    val timelength_320: Double = 0.0,
    val audio_info: DailyAudioInfo? = null
) {
    val title: String get() {
        val sn = songname ?: "未知歌曲"
        return sn.substringBefore(" - ").ifBlank { sn }.trim()
    }
    val artist: String get() = author_name ?: "未知艺术家"
    val durationMs: Long
        get() = when {
            duration > 0 -> duration.toLong()
            audio_info?.duration_320 != null && audio_info!!.duration_320 > 0 -> audio_info!!.duration_320.toLong()
            audio_info?.duration_128 != null && audio_info!!.duration_128 > 0 -> audio_info!!.duration_128.toLong()
            timelength_320 > 0 -> (timelength_320 * 1000).toLong()
            else -> 0L
        }
    val coverUrl: String
        get() {
            val raw = sizable_cover ?: union_cover ?: album_img ?: ""
            if (raw.isBlank()) return ""
            return raw.replace("{size}", "240").replace("http://", "https://")
        }
}

data class DailyAudioInfo(
    val hash_128: String? = null,
    val hash_320: String? = null,
    val duration_128: Int = 0,
    val duration_320: Int = 0
)

// ==================== 私人FM ====================

data class PersonalFmResponse(
    val status: Int = 0,
    val data: PersonalFmData? = null
)

data class PersonalFmData(
    @com.google.gson.annotations.SerializedName("song_list")
    val list: List<PersonalFmSong>? = null
)

data class PersonalFmSong(
    val songname: String? = null,
    val singerinfo: List<PlaylistTrackSingerInfo>? = null,
    val hash: String? = null,
    val album_img: String? = null,
    val sizable_cover: String? = null,
    val union_cover: String? = null,
    val album_audio_id: Long = 0,
    val album_id: Long = 0,
    val timelen: Int = 0
) {
    val title: String get() {
        val sn = songname ?: "未知歌曲"
        return sn.substringBefore(" - ").ifBlank { sn }.trim()
    }
    val artist: String get() = singerinfo?.firstOrNull()?.name ?: "未知艺术家"
    val coverUrl: String
        get() {
            val raw = sizable_cover ?: union_cover ?: album_img ?: ""
            if (raw.isBlank()) return ""
            return raw.replace("{size}", "240").replace("http://", "https://")
        }
}

// ==================== 歌曲推荐 ====================

data class TopCardResponse(
    val status: Int = 0,
    val data: TopCardData? = null
)

data class TopCardData(
    @com.google.gson.annotations.SerializedName("song_list")
    val list: List<TopCardSong>? = null
)

data class TopCardSongTransParam(
    val union_cover: String? = null,
    val sizable_cover: String? = null
)

data class TopCardSongAlbumInfo(
    val sizable_cover: String? = null,
    val album_name: String? = null
)

data class TopCardAudioInfo(
    val timelength_128: String? = null,
    val timelength_320: String? = null,
    val timelength_flac: String? = null
)

data class TopCardSong(
    val songname: String? = null,
    val ori_audio_name: String? = null,
    val author_name: String? = null,
    val hash: String? = null,
    val album_img: String? = null,
    val sizable_cover: String? = null,
    val union_cover: String? = null,
    val album_audio_id: Long = 0,
    val album_id: Long = 0,
    val duration: Int = 0,
    val timelen: Int = 0,
    val timelength_320: Double = 0.0,
    val audio_info: TopCardAudioInfo? = null,
    val trans_param: TopCardSongTransParam? = null,
    val album_info: TopCardSongAlbumInfo? = null
) {
    val title: String get() = songname ?: ori_audio_name ?: "未知歌曲"
    val artist: String get() = author_name ?: "未知艺术家"
    val coverUrl: String
        get() {
            val raw = sizable_cover
                ?: union_cover
                ?: trans_param?.union_cover
                ?: album_info?.sizable_cover
                ?: album_img ?: ""
            if (raw.isBlank()) return ""
            return raw.replace("{size}", "240").replace("http://", "https://")
        }
    val durationMs: Long
        get() = when {
            duration > 0 -> duration.toLong()
            timelen > 0 -> timelen.toLong()
            timelength_320 > 0 -> (timelength_320 * 1000).toLong()
            audio_info?.timelength_320 != null -> {
                val ms = audio_info!!.timelength_320?.toLongOrNull() ?: 0L
                if (ms > 1000) ms else ms * 1000 // 有些API返回的是毫秒，有些是秒
            }
            else -> 0L
        }
}

// ==================== 添加到歌单 ====================

data class AddToPlaylistResponse(
    val status: Int = 0,
    val errcode: Int = 0,
    @com.google.gson.annotations.SerializedName("errmsg", alternate = ["error_msg", "err_msg", "msg", "error", "message"])
    val errmsg: String? = null,
    val data: Any? = null
)

// 新建歌单响应 (playlist/add → v5/add_list)
data class CreatePlaylistData(
    @com.google.gson.annotations.SerializedName("newlistid", alternate = ["listid", "list_id"])
    val newlistid: Long = 0,
    @com.google.gson.annotations.SerializedName("listid", alternate = ["newlistid", "list_id"])
    val listid: Long = 0
)

data class CreatePlaylistResponse(
    val status: Int = 0,
    val errcode: Int = 0,
    val data: CreatePlaylistData? = null
)

// ==================== 歌词 ====================

data class LyricSearchResponse(
    val status: Int = 0,
    val candidates: List<LyricCandidate>? = null
)

data class LyricCandidate(
    val id: String? = null,
    val accesskey: String? = null,
    val song: String? = null,
    val singer: String? = null,
    val duration: Int = 0
)

data class LyricContentResponse(
    val status: Int = 0,
    val content: String? = null
)

// ==================== 新歌速递 ====================

data class TopSongResponse(
    val status: Int = 0,
    val data: TopSongData? = null
)

data class TopSongData(
    @com.google.gson.annotations.SerializedName("song_list")
    val list: List<TopCardSong>? = null
)

// ==================== 用户歌单 ====================

data class UserPlaylistResponse(
    val status: Int = 0,
    val data: UserPlaylistData? = null
)

data class UserPlaylistData(
    @com.google.gson.annotations.SerializedName("info")
    val list: List<UserPlaylistItem>? = null,
    val total: Int = 0
)

data class UserPlaylistItem(
    val listid: Long = 0,
    @com.google.gson.annotations.SerializedName("name")
    val listname: String? = null,
    val pic: String? = null,
    @com.google.gson.annotations.SerializedName("count")
    val songcount: Int = 0,
    val list_create_userid: Long = 0,
    @com.google.gson.annotations.SerializedName("is_def")
    val is_default: Int = 0,
    val global_collection_id: String? = null,
    // 歌单版本号：酷狗增删歌曲需要带当前版本，否则会返回“版本不符”错误
    @com.google.gson.annotations.SerializedName("list_ver", alternate = ["listver"])
    val list_ver: Long = 0
) {
    val coverUrl: String
        get() {
            val raw = pic ?: ""
            if (raw.isBlank()) return ""
            return raw.replace("{size}", "240").replace("http://", "https://")
        }
}

// ==================== 歌单歌曲 ====================

data class PlaylistTracksResponse(
    val status: Int = 0,
    val data: PlaylistTracksData? = null
)

data class PlaylistTracksData(
    @com.google.gson.annotations.SerializedName("songs", alternate = ["info", "list", "songlist", "lists", "data"])
    val list: List<PlaylistTrackSong>? = null,
    @com.google.gson.annotations.SerializedName("count", alternate = ["total", "totalcount", "total_count", "songcount"])
    val count: Int = 0
)

// 新版歌单歌曲 API 响应 (playlist/track/all/new)
data class PlaylistTracksNewResponse(
    val status: Int = 0,
    val data: PlaylistTracksNewData? = null
)

data class PlaylistTracksNewData(
    @com.google.gson.annotations.SerializedName("info", alternate = ["songs", "list", "songlist", "lists", "data"])
    val info: List<PlaylistTrackSong>? = null,
    @com.google.gson.annotations.SerializedName("count", alternate = ["total", "totalcount", "total_count", "songcount"])
    val count: Int = 0,
    // 歌单版本号：增删歌曲需要带当前版本，否则酷狗返厂会拒绝（版本不符）
    @com.google.gson.annotations.SerializedName("list_ver", alternate = ["listver"])
    val list_ver: Long = 0
)

data class PlaylistTrackSingerInfo(
    @com.google.gson.annotations.SerializedName("id", alternate = ["singerid", "singer_id", "author_id"])
    val id: Long = 0,
    @com.google.gson.annotations.SerializedName("name", alternate = ["singername", "singer_name", "author_name"])
    val name: String? = null
)

data class PlaylistAudioInfo(
    val hash_128: String? = null,
    val hash_320: String? = null,
    val hash_flac: String? = null,
    val duration_128: Int = 0,
    val duration_320: Int = 0
)

data class PlaylistTrackSong(
    @com.google.gson.annotations.SerializedName("name", alternate = ["songname", "song_name", "filename", "FileName", "title", "audio_name"])
    val name: String? = null,
    @com.google.gson.annotations.SerializedName("singerinfo", alternate = ["authors", "singers"])
    val singerinfo: List<PlaylistTrackSingerInfo>? = null,
    @com.google.gson.annotations.SerializedName("singername", alternate = ["singer_name", "author_name", "SingerName", "artist"])
    val singername: String? = null,
    @com.google.gson.annotations.SerializedName("hash", alternate = ["Hash", "FileHash", "fileHash", "hash_128"])
    val hash: String? = null,
    @com.google.gson.annotations.SerializedName("album_id", alternate = ["albumid", "AlbumID", "albumId"])
    val album_id: Long = 0,
    @com.google.gson.annotations.SerializedName("audio_id", alternate = ["audioid", "AudioID", "audioId"])
    val audio_id: Long = 0,
    @com.google.gson.annotations.SerializedName("mixsongid", alternate = ["MixSongID", "album_audio_id", "mix_song_id", "id"])
    val mixsongid: Long = 0,
    @com.google.gson.annotations.SerializedName("timelen", alternate = ["duration", "Duration", "duration_128", "time_len", "timeLen"])
    val timelen: Int = 0,
    @com.google.gson.annotations.SerializedName("cover", alternate = ["img", "album_img", "sizable_cover", "union_cover", "pic", "image", "coverUrl"])
    val cover: String? = null,
    val fileid: Long = 0,  // 新版 API 返回的歌单内歌曲 ID，用于精准删除
    val audio_info: PlaylistAudioInfo? = null
) {
    val title: String
        get() {
            val n = name ?: "未知歌曲"
            // API 返回格式: "歌手 - 歌名.mp3"
            val afterDash = n.substringAfter(" - ", "").trim().removeSuffix(".mp3")
            if (afterDash.isNotBlank()) return afterDash
            return n.substringBefore(" - ").trim().removeSuffix(".mp3").ifBlank { n }
        }
    val artist: String
        get() {
            if (!singername.isNullOrBlank()) return singername
            singerinfo?.firstOrNull()?.name?.takeIf { it.isNotBlank() }?.let { return it }
            val n = name ?: ""
            // API 返回格式: "歌手 - 歌名.mp3"
            val beforeDash = n.substringBefore(" - ", "").trim()
            if (beforeDash.isNotBlank() && n.contains(" - ")) return beforeDash
            return "未知艺术家"
        }
    val durationMs: Long
        get() {
            if (timelen > 1000) return timelen.toLong()
            if (timelen > 0) return timelen.toLong() * 1000L
            val audioDur = audio_info?.duration_320 ?: audio_info?.duration_128 ?: 0
            if (audioDur > 1000) return audioDur.toLong()
            if (audioDur > 0) return audioDur.toLong() * 1000L
            return 0L
        }
    val coverUrl: String
        get() {
            val raw = cover ?: ""
            if (raw.isBlank()) return ""
            return raw.replace("{size}", "240").replace("http://", "https://")
        }
}

// ==================== 搜索 ====================

data class SearchResponse(
    val status: Int = 0,
    val data: SearchData? = null
)

data class SearchData(
    val lists: List<SearchSong>? = null,
    val total: Int = 0
)

data class SearchSong(
    val FileName: String? = null,
    val SingerName: String? = null,
    val FileHash: String? = null,
    val Audioid: Long = 0,
    val Duration: Int = 0,
    val AlbumID: String? = null,
    val Image: String? = null,
    val MixSongID: Long = 0,
    val SQ: SearchSongQuality? = null,
    val HQ: SearchSongQuality? = null
) {
    val title: String
        get() {
            val n = FileName ?: "未知歌曲"
            return n.substringAfter(" - ", n).trim()
        }
    val artist: String get() = SingerName ?: "未知艺术家"
    val hash: String get() = SQ?.Hash ?: FileHash ?: ""
    val album_audio_id: Long get() = MixSongID.takeIf { it > 0 } ?: Audioid
    val coverUrl: String
        get() {
            val raw = Image ?: ""
            if (raw.isBlank()) return ""
            return raw.replace("{size}", "240").replace("http://", "https://")
        }
}

data class SearchSongQuality(
    val Hash: String? = null,
    val FileSize: Long = 0,
    val Privilege: Int = 0
)

// ==================== 歌单搜索 ====================

data class SearchPlaylistResponse(
    val status: Int = 0,
    val data: SearchPlaylistData? = null
)

data class SearchPlaylistData(
    val lists: List<SearchPlaylistItem>? = null,
    val total: Int = 0
)

data class SearchPlaylistItem(
    val specialid: Long = 0,
    val specialname: String? = null,
    val song_count: Int = 0,
    val img: String? = null,
    val nickname: String? = null,
    val play_count: String? = null,
    val collect_count: String? = null,
    val intro: String? = null,
    val gid: String? = null
) {
    val coverUrl: String
        get() {
            val raw = img ?: ""
            if (raw.isBlank()) return ""
            return raw.replace("{size}", "240").replace("http://", "https://")
        }
}

// ==================== 歌手搜索 ====================

data class SearchAuthorResponse(
    val status: Int = 0,
    val data: SearchAuthorData? = null
)

data class SearchAuthorData(
    @com.google.gson.annotations.SerializedName("lists", alternate = ["info", "data", "list"])
    val lists: List<SearchAuthorItem>? = null,
    val total: Int = 0
)

data class SearchAuthorItem(
    @com.google.gson.annotations.SerializedName("authorid", alternate = ["id", "singerid", "singer_id"])
    val authorid: Long = 0,
    @com.google.gson.annotations.SerializedName("authorname", alternate = ["name", "singername", "singer_name", "author_name"])
    val authorname: String? = null,
    @com.google.gson.annotations.SerializedName("avatar", alternate = ["img", "pic", "image", "sizable_cover", "union_cover"])
    val avatar: String? = null,
    @com.google.gson.annotations.SerializedName("songcount", alternate = ["songs_count", "song_count"])
    val songcount: Int = 0,
    @com.google.gson.annotations.SerializedName("albumcount", alternate = ["albums_count", "album_count"])
    val albumcount: Int = 0
) {
    val coverUrl: String
        get() {
            val raw = avatar ?: ""
            if (raw.isBlank()) return ""
            return raw.replace("{size}", "240").replace("http://", "https://")
        }
}

// ==================== 专辑搜索 ====================

data class SearchAlbumResponse(
    val status: Int = 0,
    val data: SearchAlbumData? = null
)

data class SearchAlbumData(
    @com.google.gson.annotations.SerializedName("lists", alternate = ["info", "data", "list"])
    val lists: List<SearchAlbumItem>? = null,
    val total: Int = 0
)

data class SearchAlbumItem(
    @com.google.gson.annotations.SerializedName("albumid", alternate = ["id", "album_id"])
    val albumid: Long = 0,
    @com.google.gson.annotations.SerializedName("albumname", alternate = ["name", "album_name"])
    val albumname: String? = null,
    @com.google.gson.annotations.SerializedName("cover", alternate = ["img", "pic", "image", "sizable_cover", "union_cover"])
    val cover: String? = null,
    @com.google.gson.annotations.SerializedName("authorname", alternate = ["singername", "singer_name", "author_name"])
    val authorname: String? = null,
    @com.google.gson.annotations.SerializedName("songcount", alternate = ["songs_count", "song_count"])
    val songcount: Int = 0
) {
    val coverUrl: String
        get() {
            val raw = cover ?: ""
            if (raw.isBlank()) return ""
            return raw.replace("{size}", "240").replace("http://", "https://")
        }
}

// ==================== MV 搜索 ====================

data class SearchMvResponse(
    val status: Int = 0,
    val data: SearchMvData? = null
)

data class SearchMvData(
    @com.google.gson.annotations.SerializedName("lists", alternate = ["info", "data", "list"])
    val lists: List<SearchMvItem>? = null,
    val total: Int = 0
)

data class SearchMvItem(
    @com.google.gson.annotations.SerializedName("mvid", alternate = ["id", "mv_id"])
    val mvid: Long = 0,
    @com.google.gson.annotations.SerializedName("mvname", alternate = ["name", "title", "mv_name"])
    val mvname: String? = null,
    @com.google.gson.annotations.SerializedName("authorname", alternate = ["singername", "singer_name", "author_name"])
    val authorname: String? = null,
    @com.google.gson.annotations.SerializedName("cover", alternate = ["img", "pic", "image", "sizable_cover", "union_cover", "thumbnail"])
    val cover: String? = null,
    @com.google.gson.annotations.SerializedName("duration", alternate = ["timelen", "time_len"])
    val duration: Int = 0,
    @com.google.gson.annotations.SerializedName("playcount", alternate = ["play_count", "play_times"])
    val playcount: String? = null
) {
    val coverUrl: String
        get() {
            val raw = cover ?: ""
            if (raw.isBlank()) return ""
            return raw.replace("{size}", "240").replace("http://", "https://")
        }
    val durationText: String
        get() {
            if (duration <= 0) return ""
            val min = duration / 60
            val sec = duration % 60
            return "${min}:${String.format("%02d", sec)}"
        }
}

// ==================== 热搜 ====================

data class HotSearchResponse(
    val status: Int = 0,
    val data: HotSearchData? = null
)

data class HotSearchData(
    val list: List<HotSearchCategory>? = null
)

data class HotSearchCategory(
    val name: String? = null,
    val keywords: List<HotSearchItem>? = null
)

data class HotSearchItem(
    val keyword: String? = null,
    val reason: String? = null,
    val type: Int = 0
)

// ==================== 搜索建议 ====================

data class SearchSuggestResponse(
    val status: Int = 0,
    val data: List<SearchSuggestCategory>? = null
)

data class SearchSuggestCategory(
    val RecordDatas: List<SearchSuggestItem>? = null,
    val RecordCount: Int = 0,
    val LableName: String? = null
)

data class SearchSuggestItem(
    val HintInfo: String? = null,
    val Hot: Int = 0
)

// ==================== 默认搜索关键词 ====================

data class DefaultKeywordResponse(
    val status: Int = 0,
    val data: DefaultKeywordData? = null
)

data class DefaultKeywordData(
    val keyword: String? = null
)

// ==================== Retrofit 接口 ====================

interface KuGouService {
    @GET("captcha/sent")
    suspend fun sendCaptcha(
        @Query("mobile") mobile: String
    ): CaptchaResponse

    @GET("login/cellphone")
    suspend fun loginWithPhone(
        @Query("mobile") mobile: String,
        @Query("code") code: String
    ): LoginResponse

    @GET("user/detail")
    suspend fun getUserDetail(
        @Query("token") token: String,
        @Query("userid") userid: Long
    ): UserDetailResponse

    @GET("everyday/recommend")
    suspend fun getDailyRecommend(
        @Query("token") token: String,
        @Query("userid") userid: Long,
        @Query("platform") platform: String = "android",
        @Query("timestamp") timestamp: Long = 0
    ): DailyRecommendResponse

    @GET("personal/fm")
    suspend fun getPersonalFm(
        @Query("hash") hash: String? = null,
        @Query("remain_songcnt") remainSongCnt: Int = 0
    ): PersonalFmResponse

    // 历史推荐列表（获取可用的历史日期）
    @GET("everyday/history")
    suspend fun getHistoryList(
        @Query("token") token: String = "",
        @Query("userid") userid: Long = 0,
        @Query("platform") platform: String = "android",
        @Query("mode") mode: String = "list",
        @Query("timestamp") timestamp: Long = 0
    ): HistoryListResponse

    // 历史推荐歌曲（获取指定日期的歌曲）
    @GET("everyday/history")
    suspend fun getHistorySongs(
        @Query("token") token: String = "",
        @Query("userid") userid: Long = 0,
        @Query("platform") platform: String = "android",
        @Query("mode") mode: String = "song",
        @Query("history_name") historyName: String = "",
        @Query("date") date: String = "",
        @Query("timestamp") timestamp: Long = 0
    ): DailyRecommendResponse

    // 风格推荐
    @GET("everyday/style/recommend")
    suspend fun getStyleRecommend(
        @Query("token") token: String = "",
        @Query("userid") userid: Long = 0,
        @Query("platform") platform: String = "android",
        @Query("timestamp") timestamp: Long = 0
    ): DailyRecommendResponse

    @GET("top/card")
    suspend fun getTopCard(
        @Query("card_id") cardId: Int = 1,
        @Query("timestamp") timestamp: Long = 0
    ): TopCardResponse

    // 概念版歌曲推荐
    @GET("top/card/youth")
    suspend fun getTopCardYouth(
        @Query("card_id") cardId: Int,
        @Query("timestamp") timestamp: Long = 0
    ): TopCardResponse

    // 新歌速递
    @GET("top/song")
    suspend fun getTopSong(): TopSongResponse

    @GET("user/playlist")
    suspend fun getUserPlaylist(
        @Query("token") token: String,
        @Query("userid") userid: Long,
        @Query("page") page: Int = 1,
        @Query("pagesize") pageSize: Int = 30
    ): UserPlaylistResponse

    @GET("playlist/track/all")
    suspend fun getPlaylistTracks(
        @Query("id") collectionId: String,
        @Query("page") page: Int = 1,
        @Query("pagesize") pageSize: Int = 30
    ): PlaylistTracksResponse

    @GET("playlist/track/all")
    suspend fun getPlaylistTracksRaw(
        @Query("id") id: String,
        @Query("page") page: Int = 1,
        @Query("pagesize") pageSize: Int = 30
    ): okhttp3.ResponseBody

    @GET("special/song")
    suspend fun getSpecialSongRaw(
        @Query("specialid") specialid: Long,
        @Query("page") page: Int = 1,
        @Query("pagesize") pageSize: Int = 30
    ): okhttp3.ResponseBody

    // 新版歌单歌曲接口 (POST /v4/get_list_all_file)，返回包含正确的 fileid 用于删除
    @GET("playlist/track/all/new")
    suspend fun getPlaylistTracksNew(
        @Query("listid") listId: Long,
        @Query("page") page: Int = 1,
        @Query("pagesize") pageSize: Int = 30
    ): PlaylistTracksNewResponse

    @GET("rank/list")
    suspend fun getRankList(): RankListResponse

    @GET("rank/audio")
    suspend fun getRankAudio(
        @Query("rankid") rankId: Long,
        @Query("page") page: Int = 1,
        @Query("pagesize") pageSize: Int = 30
    ): RankAudioResponse

    @GET("search")
    suspend fun search(
        @Query("keywords") keywords: String,
        @Query("page") page: Int = 1,
        @Query("pagesize") pageSize: Int = 30
    ): SearchResponse

    @GET("song/url")
    suspend fun getSongUrl(
        @Query("hash") hash: String,
        @Query("album_audio_id") albumAudioId: Long = 0,
        @Query("quality") quality: String? = null
    ): SongUrlResponse

    @GET("register/dev")
    suspend fun registerDev(): RegisterDevResponse

    // 搜索歌词
    @GET("search/lyric")
    suspend fun searchLyric(
        @Query("hash") hash: String
    ): LyricSearchResponse

    // 获取歌词内容
    @GET("lyric")
    suspend fun getLyric(
        @Query("id") id: String,
        @Query("accesskey") accessKey: String,
        @Query("fmt") fmt: String = "lrc",
        @Query("decode") decode: String = "true"
    ): LyricContentResponse

    // 搜索歌曲
    @GET("search")
    suspend fun search(
        @Query("keywords") keywords: String,
        @Query("page") page: Int = 1,
        @Query("pagesize") pageSize: Int = 30,
        @Query("type") type: String = "song"
    ): SearchResponse

    // 搜索歌单
    @GET("search")
    suspend fun searchPlaylist(
        @Query("keywords") keywords: String,
        @Query("page") page: Int = 1,
        @Query("pagesize") pageSize: Int = 30,
        @Query("type") type: String = "special"
    ): SearchPlaylistResponse

    // 搜索歌手
    @GET("search")
    suspend fun searchAuthor(
        @Query("keywords") keywords: String,
        @Query("page") page: Int = 1,
        @Query("pagesize") pageSize: Int = 30,
        @Query("type") type: String = "author"
    ): SearchAuthorResponse

    // 搜索专辑
    @GET("search")
    suspend fun searchAlbum(
        @Query("keywords") keywords: String,
        @Query("page") page: Int = 1,
        @Query("pagesize") pageSize: Int = 30,
        @Query("type") type: String = "album"
    ): SearchAlbumResponse

    // 搜索 MV
    @GET("search")
    suspend fun searchMv(
        @Query("keywords") keywords: String,
        @Query("page") page: Int = 1,
        @Query("pagesize") pageSize: Int = 30,
        @Query("type") type: String = "mv"
    ): SearchMvResponse

    // 歌单详情（获取歌单元数据：名称、封面、歌曲数、创建者等）
    @GET("special/detail")
    suspend fun getSpecialDetail(
        @Query("specialid") specialId: Long
    ): okhttp3.ResponseBody

    // 热搜列表
    @GET("search/hot")
    suspend fun getHotSearch(): HotSearchResponse

    // 搜索建议
    @GET("search/suggest")
    suspend fun getSearchSuggest(
        @Query("keywords") keywords: String
    ): SearchSuggestResponse

    // 默认搜索关键词
    @GET("search/default")
    suspend fun getDefaultSearchKeyword(): DefaultKeywordResponse

    // 添加歌曲到歌单
    @GET("playlist/tracks/add")
    suspend fun addToPlaylist(
        @Query("listid") listId: Long,
        @Query("data") data: String,
        @Query("list_ver") listVer: Long = 0
    ): AddToPlaylistResponse

    // 从歌单删除歌曲
    @GET("playlist/tracks/del")
    suspend fun removeFromPlaylist(
        @Query("listid") listId: Long,
        @Query("fileids") fileids: String,
        @Query("list_ver") listVer: Long = 0
    ): AddToPlaylistResponse

    // 新建歌单 (playlist/add；type=0 创建空歌单，source=1) —— 返回原始 body 手动解析
    @GET("playlist/add")
    suspend fun createPlaylistRaw(
        @Query("type") type: Int = 0,
        @Query("name") name: String,
        @Query("is_pri") isPri: Int = 0,
        @Query("source") source: Int = 1
    ): okhttp3.ResponseBody

    // 删除/取消收藏歌单 (playlist/del → v2/delete_list)
    @GET("playlist/del")
    suspend fun deletePlaylist(
        @Query("listid") listId: Long
    ): AddToPlaylistResponse

    // 收藏歌单到酷狗账号 (playlist/add?type=1 → v5/add_list 收藏已存在的在线歌单) —— 原始 body 手动解析
    @GET("playlist/add")
    suspend fun collectPlaylistRaw(
        @Query("type") type: Int = 1,
        @Query("source") source: Int = 1,
        @Query("name") name: String,
        @Query("list_create_userid") listCreateUserId: Long = 0,
        @Query("list_create_listid") listCreateListid: Long = 0,
        @Query("list_create_gid") listCreateGid: String = ""
    ): okhttp3.ResponseBody
}

// ==================== 单例 ====================

object KuGouApi {
    // 服务器地址由构建注入（keystore.properties 的 LX_SERVER_URL），开源仓库中为占位值
    val DEFAULT_BASE_URL: String = BuildConfig.LX_SERVER_URL.ifBlank { "http://your-server:3000/" }

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL

    // dfid 和 token 由应用层设置
    @Volatile
    var dfid: String = ""
    @Volatile
    var token: String = ""
    @Volatile
    var userid: String = ""

    // VIP 账号（用于获取播放 URL）- 由登录时自动设置
    @Volatile
    var ownerToken: String = ""
    @Volatile
    var ownerUserid: String = ""

    // 手动设置的 VIP userid（优先级高于 ownerToken）
    @Volatile
    var vipUserid: String = ""

    // 标记当前请求是否使用 VIP 账号
    @Volatile
    var useOwnerAuth: Boolean = false

    // 播放音质: null=默认, "128", "320", "flac", "high"
    @Volatile
    var audioQuality: String? = null

    // 当前播放歌曲的音质信息（由 SongDataSource 更新）
    @Volatile
    var lastBitRate: Int = 0
    @Volatile
    var lastExtName: String? = null

    private var _cookieStore: MutableMap<String, String>? = null

    /** 清除服务器 Cookie（每次启动时调用，确保获取新设备标识） */
    fun clearServerCookies() {
        _cookieStore?.clear()
        android.util.Log.d("LxMusic", "已清除服务器 Cookie")
    }

    /** 重建 API 服务（服务器地址变更后调用） */
    fun rebuildService() {
        _service = null
        _cookieStore = null
        android.util.Log.d("LxMusic", "API 服务已重建，baseUrl=$baseUrl")
    }

    private var _service: KuGouService? = null
    val service: KuGouService
        get() = _service ?: createService().also { _service = it }

    // ==================== 收藏到酷狗（喜欢/歌单增删） ====================

    private var _kugouLikePlaylist: UserPlaylistItem? = null
    private var _kugouLikeHashes: Set<String>? = null
    // 喜欢歌单内 hash -> fileid（删除优先用 fileid，比 hash 更稳）
    private var _kugouLikeFileIds: Map<String, Long> = emptyMap()
    private var _kugouLikeHashesAt: Long = 0L

    /** 统一规范化歌曲 hash（去掉 |后缀、去空格、转大写），避免同一首歌大小写不同导致去重失败 */
    fun normalizedHash(raw: String): String = raw.substringBefore("|").trim().uppercase()

    /** 解析当前账号的「喜欢」歌单（优先名字含「喜欢」的，如"我喜欢的音乐/我喜欢"；否则退回默认歌单），内存缓存 */
    suspend fun resolveKugouLikePlaylist(): UserPlaylistItem? {
        _kugouLikePlaylist?.let { return it }
        if (token.isBlank() || userid.isBlank()) return null
        val uid = userid.toLongOrNull() ?: return null
        return try {
            val resp = service.getUserPlaylist(token, uid)
            val list = resp.data?.list ?: return null
            val defaults = list.filter { it.is_default == 1 }
            if (defaults.isNotEmpty()) {
                android.util.Log.i(
                    "LxMusic_KugouLike",
                    "账号默认歌单: " + defaults.joinToString(" | ") { "${it.listname ?: ""}(${it.listid})" }
                )
            }
            val picked = list.firstOrNull { it.listname?.contains("喜欢") == true }
                ?: defaults.firstOrNull()
                ?: list.firstOrNull()
            picked?.let {
                android.util.Log.i(
                    "LxMusic_KugouLike",
                    "收藏目标歌单: ${it.listname ?: ""} (listid=${it.listid}, is_def=${it.is_default}, list_ver=${it.list_ver})"
                )
            }
            _kugouLikePlaylist = picked
            picked
        } catch (e: Exception) {
            android.util.Log.w("LxMusic_KugouLike", "解析喜欢歌单失败: ${e.message}")
            null
        }
    }

    /** 拉取「喜欢」歌单内的歌曲（hash 集合 + hash->fileid 映射），带 60 秒新鲜度缓存，供红心判亮与删除 */
    suspend fun kugouLikeHashes(refresh: Boolean = false): Set<String> {
        val now = System.currentTimeMillis()
        if (!refresh && _kugouLikeHashes != null && now - _kugouLikeHashesAt < 60_000L) {
            return _kugouLikeHashes!!
        }
        val playlist = resolveKugouLikePlaylist() ?: return _kugouLikeHashes ?: emptySet()
        val hashes = mutableSetOf<String>()
        val fileIds = HashMap<String, Long>()
        try {
            var page = 1
            while (page <= 20) {
                val resp = service.getPlaylistTracksNew(playlist.listid, page, 100)
                val info = resp.data?.info.orEmpty()
                info.forEach { t ->
                    val h = t.hash
                    if (h != null && h.isNotBlank()) {
                        val norm = normalizedHash(h)
                        hashes.add(norm)
                        if (t.fileid > 0) fileIds[norm] = t.fileid
                    }
                }
                if (info.size < 100) break
                if ((resp.data?.count ?: 0) in 1..(page * 100)) break
                page++
            }
        } catch (e: Exception) {
            android.util.Log.w("LxMusic_KugouLike", "拉取喜欢歌单歌曲失败: ${e.message}")
        }
        _kugouLikeHashes = hashes
        _kugouLikeFileIds = fileIds
        _kugouLikeHashesAt = now
        return hashes
    }

    /** 判断某首歌是否已在「喜欢」歌单（红心判亮用，统一大小写） */
    suspend fun isSongLikedKugou(filePath: String): Boolean {
        val key = normalizedHash(filePath)
        if (key.isBlank()) return false
        return kugouLikeHashes().contains(key)
    }

    /** 获取「喜欢」歌单里某个 hash 对应的 fileid（删除用），没有则 0 */
    fun kugouLikeFileId(hash: String): Long = _kugouLikeFileIds[normalizedHash(hash)] ?: 0L

    /** 拉取官方「喜欢」歌单里的全部歌曲（用于官方模式下「我喜欢的」列表：官方已有的 + 本地先写的合并显示） */
    suspend fun fetchKugouLikeSongs(): List<SongInfo> {
        val playlist = resolveKugouLikePlaylist() ?: return emptyList()
        val gid = playlist.global_collection_id
        val result = mutableListOf<SongInfo>()
        try {
            var page = 1
            while (page <= 50) {
                val list: List<SongInfo>
                if (!gid.isNullOrBlank()) {
                    val (s, _) = fetchSpecialPlaylistSongs(0L, gid, page, 100)
                    list = s
                } else {
                    list = service.getPlaylistTracksNew(playlist.listid, page, 100).data?.info?.mapNotNull { t ->
                        val h = t.hash ?: return@mapNotNull null
                        SongInfo(
                            title = t.title,
                            artist = t.artist,
                            filePath = "$h|${t.mixsongid}",
                            albumArtUri = t.coverUrl,
                            duration = t.durationMs,
                            albumId = t.album_id,
                            mixsongid = t.mixsongid
                        )
                    }.orEmpty()
                }
                if (list.isEmpty()) break
                result.addAll(list)
                if (list.size < 100) break
                page++
            }
        } catch (e: Exception) {
            android.util.Log.w("LxMusic_KugouLike", "拉取官方喜欢歌曲失败: ${e.message}")
        }
        return result
    }

    private val kugouListVerCache = HashMap<Long, Long>()

    /** 获取酷狗歌单当前的 list_ver（增删歌曲需要同步版本号，版本不符酷狗会拒绝），内存缓存 */
    suspend fun fetchKugouListVer(listid: Long): Long {
        if (listid <= 0) return 0L
        kugouListVerCache[listid]?.let { return it }
        return try {
            val ver = service.getPlaylistTracksNew(listid, 1, 1).data?.list_ver ?: 0L
            if (ver > 0) kugouListVerCache[listid] = ver
            ver
        } catch (e: Exception) {
            android.util.Log.w("LxMusic_KugouLike", "获取歌单 $listid list_ver 失败: ${e.message}")
            0L
        }
    }

    /** 把一首歌加入指定酷狗歌单（喜欢歌单/自建歌单都走这里） */
    suspend fun addSongToKugouPlaylist(listid: Long, song: SongInfo, listVer: Long = 0): Boolean {
        val hash = song.filePath.substringBefore("|")
        if (hash.isBlank() || listid <= 0) return false
        val name = song.title.ifBlank { hash }
        val data = "$name|$hash|${song.albumId}|${song.mixsongid}"
        // 加歌对版本号宽松，不再额外请求 list_ver（删除才需要），减少一次往返
        return try {
            val t0 = System.currentTimeMillis()
            val resp = service.addToPlaylist(listid, data, listVer)
            val elapsed = System.currentTimeMillis() - t0
            val ok = resp.status == 1 && resp.errcode == 0
            android.util.Log.i(
                "LxMusic_KugouLike",
                "加歌到歌单 $listid: ok=$ok, status=${resp.status}, errcode=${resp.errcode}, errmsg=${resp.errmsg}, 耗时=${elapsed}ms"
            )
            if (ok) recordLocallyAdded(listid, hash, song)
            ok
        } catch (e: Exception) {
            android.util.Log.w("LxMusic_KugouLike", "加歌到歌单 $listid 异常: ${e.message}")
            false
        }
    }

    /** 从指定酷狗歌单移除歌曲（按 hash） */
    suspend fun removeFromKugouPlaylist(listid: Long, song: SongInfo, listVer: Long = 0): Boolean {
        val hash = song.filePath.substringBefore("|")
        if (hash.isBlank() || listid <= 0) return false
        var ver = listVer
        if (ver <= 0) ver = fetchKugouListVer(listid)
        return try {
            val t0 = System.currentTimeMillis()
            val resp = service.removeFromPlaylist(listid, hash, ver)
            val elapsed = System.currentTimeMillis() - t0
            val ok = resp.status == 1 && resp.errcode == 0
            android.util.Log.i(
                "LxMusic_KugouLike",
                "从歌单 $listid 删歌(hash): ok=$ok, status=${resp.status}, errcode=${resp.errcode}, errmsg=${resp.errmsg}, 耗时=${elapsed}ms"
            )
            ok
        } catch (e: Exception) {
            android.util.Log.w("LxMusic_KugouLike", "从歌单 $listid 删歌(hash)异常: ${e.message}")
            false
        }
    }

    /** 从指定酷狗歌单移除歌曲（按 fileid，更稳） */
    suspend fun removeFromKugouPlaylistByFileid(listid: Long, fileid: Long, listVer: Long = 0): Boolean {
        if (fileid <= 0 || listid <= 0) return false
        var ver = listVer
        if (ver <= 0) ver = fetchKugouListVer(listid)
        return try {
            val t0 = System.currentTimeMillis()
            val resp = service.removeFromPlaylist(listid, fileid.toString(), ver)
            val elapsed = System.currentTimeMillis() - t0
            val ok = resp.status == 1 && resp.errcode == 0
            android.util.Log.i(
                "LxMusic_KugouLike",
                "从歌单 $listid 删歌(fileid=$fileid): ok=$ok, status=${resp.status}, errcode=${resp.errcode}, errmsg=${resp.errmsg}, 耗时=${elapsed}ms"
            )
            ok
        } catch (e: Exception) {
            android.util.Log.w("LxMusic_KugouLike", "从歌单 $listid 删歌(fileid=$fileid)异常: ${e.message}")
            false
        }
    }

    /** 收藏到「喜欢」歌单 */
    suspend fun addToKugouLike(song: SongInfo): Boolean {
        val p = resolveKugouLikePlaylist() ?: return false
        val ok = addSongToKugouPlaylist(p.listid, song, p.list_ver)
        if (ok) noteKugouLikeAdded(song.filePath.substringBefore("|"))
        return ok
    }

    /** 从「喜欢」歌单取消：优先按 fileid 删除，没有 fileid 再按 hash 删除 */
    suspend fun removeFromKugouLike(song: SongInfo): Boolean {
        val p = resolveKugouLikePlaylist() ?: return false
        val hash = song.filePath.substringBefore("|")
        var ok = false
        val fileId = kugouLikeFileId(hash)
        if (fileId > 0) {
            ok = removeFromKugouPlaylistByFileid(p.listid, fileId, p.list_ver)
        }
        if (!ok) {
            ok = removeFromKugouPlaylist(p.listid, song, p.list_ver)
        }
        if (ok) {
            noteKugouLikeRemoved(hash)
            recordLocallyRemoved(p.listid, hash)
        }
        return ok
    }

    /** 更新内存里的喜欢 hash 缓存（收藏成功后调用） */
    fun noteKugouLikeAdded(hash: String) {
        val norm = normalizedHash(hash)
        if (norm.isBlank()) return
        val s = _kugouLikeHashes
        if (s != null) _kugouLikeHashes = s + norm
    }

    /** 更新内存里的喜欢 hash 缓存（取消收藏后调用） */
    fun noteKugouLikeRemoved(hash: String) {
        val norm = normalizedHash(hash)
        if (norm.isBlank()) return
        val s = _kugouLikeHashes
        if (s != null) _kugouLikeHashes = s - norm
        if (_kugouLikeFileIds.containsKey(norm)) _kugouLikeFileIds = _kugouLikeFileIds - norm
    }

    /** 清除酷狗「喜欢」歌单相关缓存（登录/退出/切换账号时调用，避免串账号） */
    fun clearKugouLikeCache() {
        _kugouLikePlaylist = null
        _kugouLikeHashes = null
        _kugouLikeFileIds = emptyMap()
        _kugouLikeHashesAt = 0L
        kugouListVerCache.clear()
        playlistRealCounts.clear()
        locallyAddedSongs.clear()
        locallyRemovedHashes.clear()
        locallyAddedPlaylists.clear()
        collectedPlaylistListids.clear()
        _likedCount = null
    }

    // ---- 云端歌单「真实歌曲数」/「本地乐观新增」缓存（酷狗读端同步慢，用于即时显示） ----

    private val playlistRealCounts = HashMap<Long, Int>()

    fun recordPlaylistRealCount(listid: Long, count: Int) {
        if (listid <= 0 || count < 0) return
        val cur = playlistRealCounts[listid]
        if (cur == null || count > cur) playlistRealCounts[listid] = count
    }

    fun playlistRealCount(listid: Long): Int? = playlistRealCounts[listid]

    // 「我喜欢的」数量：官方喜欢 ∪ 本地喜欢镜像 的合并数（由喜欢详情页记录，收藏/取消增减）
    private var _likedCount: Int? = null

    fun recordLikedCount(count: Int) {
        if (count >= 0) _likedCount = count
    }

    fun likedCount(): Int? = _likedCount

    fun bumpLikedCount(delta: Int) {
        val cur = _likedCount
        if (cur != null && cur + delta >= 0) _likedCount = cur + delta
    }

    private val locallyAddedSongs = HashMap<Long, MutableMap<String, SongInfo>>()

    /** 记录一次成功加歌（用于详情页即时显示与卡片计数+1） */
    fun recordLocallyAdded(listid: Long, hash: String, song: SongInfo) {
        val norm = normalizedHash(hash)
        if (listid <= 0 || norm.isBlank()) return
        locallyAddedSongs.getOrPut(listid) { HashMap() }[norm] = song
        recordPlaylistRealCount(listid, (playlistRealCounts[listid] ?: 0) + 1)
    }

    /** 记录一次成功删除（计数-1、移除本地乐观显示、标记已删除以便从列表过滤） */
    fun recordLocallyRemoved(listid: Long, hash: String) {
        val norm = normalizedHash(hash)
        if (listid <= 0 || norm.isBlank()) return
        locallyRemovedHashes.getOrPut(listid) { HashSet() }.add(norm)
        locallyAddedSongs[listid]?.remove(norm)
        val cur = playlistRealCounts[listid]
        if (cur != null && cur > 0) playlistRealCounts[listid] = cur - 1
    }

    // 已删除的歌曲 hash（从列表显示中过滤，配合酷狗读端同步慢时的即时删除）
    private val locallyRemovedHashes = HashMap<Long, MutableSet<String>>()

    /** 只过滤本地已删除的歌（追加页用，避免多页时重复前置新增） */
    fun filterLocallyRemoved(listid: Long, serverSongs: List<SongInfo>): List<SongInfo> {
        val removed = locallyRemovedHashes[listid] ?: return serverSongs
        if (removed.isEmpty()) return serverSongs
        return serverSongs.filter { normalizedHash(it.filePath) !in removed }
    }

    /** 详情页合并：先滤掉本地已删除的歌，再前置本地乐观新增（按 hash 去重 + 歌名/歌手兜底） */
    fun applyLocalOps(listid: Long, serverSongs: List<SongInfo>): List<SongInfo> {
        val filtered = filterLocallyRemoved(listid, serverSongs)
        val local = locallyAddedSongs[listid] ?: return filtered
        if (local.isEmpty()) return filtered
        val serverHashes = filtered.mapTo(HashSet()) { normalizedHash(it.filePath) }
        val serverTitleKeys = filtered.mapTo(HashSet()) { titleArtistKey(it) }
        val toPrepend = mutableListOf<SongInfo>()
        val toDrop = mutableListOf<String>()
        local.forEach { (key, song) ->
            val norm = normalizedHash(key)
            if (norm in serverHashes || titleArtistKey(song) in serverTitleKeys) {
                // 酷狗已经同步到这首歌，不再需要本地假显示
                toDrop.add(key)
            } else {
                toPrepend.add(song)
            }
        }
        toDrop.forEach { locallyAddedSongs[listid]?.remove(it) }
        if (toPrepend.isEmpty()) return filtered
        return toPrepend.reversed() + filtered
    }

    /** 歌名+歌手 去重键（用于 hash 变体时的兜底去重） */
    private fun titleArtistKey(song: SongInfo): String =
        (song.title.trim() + "|" + song.artist.trim()).lowercase()

    /** 按本地保存的顺序编号给歌单列表排序（未列出的新歌单追加到末尾） */
    fun applyLocalPlaylistOrder(list: List<UserPlaylistItem>, orderRaw: String?): List<UserPlaylistItem> {
        if (orderRaw.isNullOrBlank()) return list
        val order = orderRaw.split(",").mapNotNull { it.toLongOrNull() }
        if (order.isEmpty()) return list
        val byId = list.associateBy { it.listid }
        val ordered = order.mapNotNull { byId[it] }
        val orderedIds = ordered.map { it.listid }.toSet()
        return ordered + list.filter { it.listid !in orderedIds }
    }

    /** 把歌单列表转成本地顺序字符串（仅本地显示顺序，不同步酷狗） */
    fun encodePlaylistOrder(list: List<UserPlaylistItem>): String =
        list.joinToString(",") { it.listid.toString() }

    /** 稳定化歌单顺序：已保存顺序固定不动，新出现的按当前顺序追加并入「本地顺序」，返回(稳定列表,新顺序串) */
    fun stabilizePlaylistOrder(list: List<UserPlaylistItem>, orderRaw: String?): Pair<List<UserPlaylistItem>, String> {
        val orderedIds = orderRaw?.split(",")?.mapNotNull { it.toLongOrNull() }.orEmpty()
        val byId = list.associateBy { it.listid }
        val ordered = orderedIds.mapNotNull { byId[it] }
        val known = ordered.map { it.listid }.toSet()
        val rest = list.filter { it.listid !in known }
        val stable = ordered + rest
        return Pair(stable, stable.joinToString(",") { it.listid.toString() })
    }

    /** 分组排序：我喜欢的置顶 → 自建/默认 → 收藏的别人 + 本地镜像（按名字去重）；返回(列表, 镜像条目的 listid 集合) */
    fun groupPlaylists(
        official: List<UserPlaylistItem>,
        mirror: List<UserPlaylistItem>,
        uid: Long,
        likeName: String
    ): Pair<List<UserPlaylistItem>, Set<Long>> {
        val officialNames = official.mapNotNull { it.listname }.toHashSet()
        val mirrorVisible = mirror.filter { !officialNames.contains(it.listname) }
        val top = official.filter { it.listname == likeName }
        val topNames = top.mapNotNull { it.listname }.toSet()
        val mine = official.filter {
            (it.is_default == 1 || it.list_create_userid == uid) && it.listname !in topNames
        }
        val collected = official.filter { it.is_default != 1 && it.list_create_userid != uid }
        val mirrorIds = mirrorVisible.map { it.listid }.toSet()
        return Pair(top + mine + collected + mirrorVisible, mirrorIds)
    }

    // ===== 已删除官方歌单黑名单（本地删除立即生效，官方读取端同步前不“回弹”） =====

    fun readDeletedPlaylistIds(prefs: android.content.SharedPreferences, uid: Long): Set<Long> =
        prefs.getString("deleted_kugou_playlists_$uid", null)
            ?.split(",")?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

    fun addDeletedPlaylistId(prefs: android.content.SharedPreferences, uid: Long, listid: Long) {
        if (listid <= 0) return
        val ids = readDeletedPlaylistIds(prefs, uid).toMutableSet()
        if (ids.add(listid)) prefs.edit().putString("deleted_kugou_playlists_$uid", ids.joinToString(",")).apply()
    }

    fun removeDeletedPlaylistId(prefs: android.content.SharedPreferences, uid: Long, listid: Long) {
        val ids = readDeletedPlaylistIds(prefs, uid).toMutableSet()
        if (ids.remove(listid)) prefs.edit().putString("deleted_kugou_playlists_$uid", ids.joinToString(",")).apply()
    }

    /** 删除酷狗在线歌单（自建=删除，收藏别人的=取消收藏），外网不可逆操作 */
    suspend fun deleteKuGouPlaylist(listid: Long): Boolean {
        if (listid <= 0) return false
        return try {
            val resp = service.deletePlaylist(listid)
            val ok = resp.status == 1 && resp.errcode == 0
            android.util.Log.i(
                "LxMusic_KugouLike",
                "删除歌单 $listid: ok=$ok, status=${resp.status}, errcode=${resp.errcode}, errmsg=${resp.errmsg}"
            )
            ok
        } catch (e: Exception) {
            android.util.Log.w("LxMusic_KugouLike", "删除歌单 $listid 异常: ${e.message}")
            false
        }
    }

    /** 在酷狗新建空歌单，返回新歌单 listid；失败返回 null */
    suspend fun createKugouPlaylist(name: String): Long? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        return try {
            val (newId, raw) = parsePlaylistAddListid(service.createPlaylistRaw(name = trimmed))
            android.util.Log.i("LxMusic_KugouLike", "新建歌单响应: ${raw.take(300)}")
            if (newId <= 0) {
                android.util.Log.w("LxMusic_KugouLike", "新建歌单未返回 listid: $trimmed, raw=${raw.take(300)}")
                return null
            }
            android.util.Log.i("LxMusic_KugouLike", "新建歌单成功: $trimmed -> listid=$newId")
            newId
        } catch (e: Exception) {
            android.util.Log.w("LxMusic_KugouLike", "新建歌单异常: ${e.message}")
            null
        }
    }

    /** 解析 /playlist/add 返回的 JSON，提取新歌单 listid（data/data.info/顶层里的 newlistid·listid，或 data.info.global_collection_id） */
    private fun parsePlaylistAddListid(body: okhttp3.ResponseBody): Pair<Long, String> {
        val str = runCatching { body.string() }.getOrDefault("")
        body.close()
        val id = runCatching {
            val json = org.json.JSONObject(str)
            val d = json.opt("data") as? org.json.JSONObject
            val info = d?.opt("info") as? org.json.JSONObject
            var v = d?.optLong("newlistid", -1L) ?: -1L
            if (v <= 0) v = d?.optLong("listid", -1L) ?: -1L
            if (v <= 0) v = info?.optLong("newlistid", -1L) ?: -1L
            if (v <= 0) v = info?.optLong("listid", -1L) ?: -1L
            if (v <= 0) v = json.optLong("newlistid", -1L)
            if (v <= 0) v = json.optLong("listid", -1L)
            if (v <= 0) v = json.optLong("list_id", -1L)
            // 收藏歌单时新 listid 常藏在 data.info.global_collection_id（collection_{type}_{uid}_{listid}_{x}）
            if (v <= 0) {
                val gid = info?.optString("global_collection_id", "") ?: ""
                v = parseGid(gid)?.second ?: 0L
            }
            if (v > 0) v else 0L
        }.getOrDefault(0L)
        return Pair(id, str)
    }

    // ---- 收藏歌单（把搜索到的在线歌单收藏到酷狗 + 本地乐观置顶） ----

    private val locallyAddedPlaylists = LinkedHashMap<Long, UserPlaylistItem>()
    private val collectedPlaylistListids = HashMap<String, Long>()

    /** 记住某个 gid 收藏成功后在酷狗返回的 listid（供取消收藏时删除） */
    fun rememberCollectedPlaylist(gid: String, collectedListid: Long) {
        if (gid.isNotBlank() && collectedListid > 0) collectedPlaylistListids[gid] = collectedListid
    }

    fun collectedPlaylistListid(gid: String): Long = collectedPlaylistListids[gid] ?: 0L

    /** 记录一条“刚刚收藏”的歌单，用于在歌单列表顶部优先显示（酷狗未同步前） */
    fun recordLocallyAddedPlaylist(item: UserPlaylistItem) {
        if (item.listid <= 0) return
        locallyAddedPlaylists[item.listid] = item
    }

    /** 把本地乐观新增的歌单合并到在线歌单列表（置顶；服务器已返回的用服务器那份） */
    fun mergeLocallyAddedPlaylists(serverList: List<UserPlaylistItem>): List<UserPlaylistItem> {
        if (locallyAddedPlaylists.isEmpty()) return serverList
        val serverIds = serverList.mapTo(HashSet()) { it.listid }
        val added = locallyAddedPlaylists.values.filter { it.listid !in serverIds }
        if (added.isEmpty()) return serverList
        return added.toList() + serverList
    }

    /** 从 gid（collection_{type}_{userid}_{listid}_{x}）解析原歌单 owner userid 与 listid */
    fun parseGid(gid: String): Pair<Long, Long>? {
        val parts = gid.split("_")
        if (parts.size >= 4) {
            val uid = parts.getOrNull(2)?.toLongOrNull()
            val listid = parts.getOrNull(3)?.toLongOrNull()
            if (uid != null && listid != null && uid > 0 && listid > 0) return Pair(uid, listid)
        }
        return null
    }

    /** 把搜索到的在线歌单收藏到酷狗账号（type=1 收藏歌单），成功返回收藏后的 listid */
    suspend fun kuGouCollectPlaylist(name: String, gid: String): Long? {
        val parsed = parseGid(gid) ?: run {
            android.util.Log.w("LxMusic_KugouLike", "收藏歌单: gid 无法解析, gid=$gid（跳过官方收藏）")
            return null
        }
        val (ownerUid, ownerListid) = parsed
        if (name.isBlank()) return null
        return try {
            val (newId, raw) = parsePlaylistAddListid(
                service.collectPlaylistRaw(
                    name = name.trim(),
                    listCreateUserId = ownerUid,
                    listCreateListid = ownerListid,
                    listCreateGid = gid
                )
            )
            android.util.Log.i("LxMusic_KugouLike", "收藏歌单响应: gid=$gid, raw=${raw.take(300)}")
            if (newId <= 0) return null
            android.util.Log.i("LxMusic_KugouLike", "收藏歌单成功: $name -> listid=$newId")
            newId
        } catch (e: Exception) {
            android.util.Log.w("LxMusic_KugouLike", "收藏歌单异常: ${e.message}")
            null
        }
    }

    /**
     * 获取搜索/网络精选歌单内的歌曲列表（支持 gid 与酷狗官方 CDN 多源降级与全量日志调试输出）
     */
    suspend fun fetchSpecialPlaylistSongs(
        specialId: Long,
        gid: String? = null,
        page: Int = 1,
        pageSize: Int = 30
    ): Pair<List<SongInfo>, Int> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val tag = "LxMusic_Playlist"
        android.util.Log.d(tag, "==================================================")
        android.util.Log.d(tag, ">>> [歌单歌曲请求] specialId=$specialId, gid=$gid, page=$page, pageSize=$pageSize")

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val candidateUrls = mutableListOf<String>()

        // 1. 如果有 gid (例如 collection_3_xxx)，优先使用后端接口
        if (!gid.isNullOrBlank()) {
            candidateUrls.add("${baseUrl.trimEnd('/')}/playlist/track/all?id=$gid&page=$page&pagesize=$pageSize")
        }

        // 2. 优先：通过后端 special/song 获取歌曲列表（解析更稳定，字段映射一致）
        candidateUrls.add("${baseUrl.trimEnd('/')}/special/song?specialid=$specialId&page=$page&pagesize=$pageSize")

        // 3. 酷狗官方移动端 CDN 接口 (高可用、返回标准 json)
        candidateUrls.add("http://mobilecdnbss.kugou.com/api/v3/special/song?specialid=$specialId&page=$page&pagesize=$pageSize&plat=0&version=9108")

        // 4. 酷狗 M 站 plist 接口
        candidateUrls.add("https://m.kugou.com/plist/list/$specialId?json=true&page=$page&pagesize=$pageSize")

        // 5. 酷狗 specialsearch 搜索接口
        candidateUrls.add("http://specialsearch.kugou.com/v1/special/song?specialid=$specialId&page=$page&pagesize=$pageSize&plat=0&version=9108")

        // 6. 后端 API 服务器的 playlist/track/all（兜底：用 specialId 作为 id 查询）
        candidateUrls.add("${baseUrl.trimEnd('/')}/playlist/track/all?id=$specialId&page=$page&pagesize=$pageSize")

        val gson = com.google.gson.Gson()

        for (url in candidateUrls) {
            android.util.Log.d(tag, "--> 尝试请求歌单候选地址: $url")
            try {
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Mobile Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()

                val response = client.newCall(request).execute()
                val code = response.code
                val bodyStr = response.body?.string() ?: ""
                response.close()

                android.util.Log.d(tag, "<-- 接口响应: HTTP $code, 长度 ${bodyStr.length} 字符")
                if (code in 200..299 && bodyStr.isNotBlank() && bodyStr.contains("{")) {
                    val rootObj = try {
                        gson.fromJson(bodyStr, com.google.gson.JsonObject::class.java)
                    } catch (_: Exception) { null }

                    if (rootObj != null) {
                        // 检查是否有错误码报错
                        val status = rootObj.get("status")?.run { if (isJsonPrimitive && asJsonPrimitive.isNumber) asInt else null }
                        val errCode = rootObj.get("error_code")?.run { if (isJsonPrimitive && asJsonPrimitive.isNumber) asInt else null }
                            ?: rootObj.get("errcode")?.run { if (isJsonPrimitive && asJsonPrimitive.isNumber) asInt else null }
                        if (status == 0 && errCode != null && errCode != 0) {
                            android.util.Log.w(tag, "接口返回业务错误: error_code=$errCode, body=${bodyStr.take(120)}")
                            continue
                        }

                        val songArray = findSongArray(rootObj)
                        val totalCount = findTotalCount(rootObj)

                        if (songArray != null && songArray.size() > 0) {
                            val parsedSongs = mutableListOf<SongInfo>()
                            for (item in songArray) {
                                if (item.isJsonObject) {
                                    try {
                                        val track = gson.fromJson(item, PlaylistTrackSong::class.java)
                                        val hash = track.hash ?: ""
                                        if (hash.isNotBlank()) {
                                            val audioId = if (track.mixsongid > 0) track.mixsongid else track.audio_id
                                            parsedSongs.add(
                                                SongInfo(
                                                    title = track.title,
                                                    artist = track.artist,
                                                    filePath = "$hash|$audioId",
                                                    albumArtUri = track.coverUrl,
                                                    duration = track.durationMs
                                                )
                                            )
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w(tag, "解析单曲出错: ${e.message}")
                                    }
                                }
                            }

                            if (parsedSongs.isNotEmpty()) {
                                val finalTotal = if (totalCount > 0) totalCount else parsedSongs.size
                                android.util.Log.d(tag, "=== [歌单解析成功] 来源接口: $url, 解析歌曲数: ${parsedSongs.size}, 歌单总数: $finalTotal ===")
                                android.util.Log.d(tag, "==================================================")
                                return@withContext Pair(parsedSongs, finalTotal)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(tag, "请求 $url 异常: ${e.message}")
            }
        }

        android.util.Log.e(tag, "!!! 所有歌单接口均未返回有效数据，请检查网络或后端接口实现 !!!")
        android.util.Log.d(tag, "==================================================")
        Pair(emptyList(), 0)
    }

    private fun findSongArray(element: com.google.gson.JsonElement): com.google.gson.JsonArray? {
        if (element.isJsonArray) {
            val arr = element.asJsonArray
            if (arr.size() > 0 && arr[0].isJsonObject) {
                val firstObj = arr[0].asJsonObject
                if (firstObj.has("hash") || firstObj.has("Hash") || firstObj.has("filename") ||
                    firstObj.has("songname") || firstObj.has("name") || firstObj.has("title") ||
                    firstObj.has("audio_id") || firstObj.has("audio_name")) {
                    return arr
                }
            }
        } else if (element.isJsonObject) {
            val obj = element.asJsonObject
            // 优先查找常见键名
            for (key in listOf("info", "songs", "list", "songlist", "lists", "data")) {
                if (obj.has(key)) {
                    val found = findSongArray(obj.get(key))
                    if (found != null && found.size() > 0) return found
                }
            }
            // 深度遍历子节点
            for ((_, value) in obj.entrySet()) {
                if (value.isJsonObject || value.isJsonArray) {
                    val found = findSongArray(value)
                    if (found != null && found.size() > 0) return found
                }
            }
        }
        return null
    }

    private fun findTotalCount(element: com.google.gson.JsonElement): Int {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            for (key in listOf("total", "count", "total_count", "songcount", "totalcount", "song_count")) {
                val v = obj.get(key)
                if (v != null && v.isJsonPrimitive) {
                    try {
                        val c = v.asInt
                        if (c > 0) return c
                    } catch (_: Exception) {}
                }
            }
            for ((_, value) in obj.entrySet()) {
                if (value.isJsonObject) {
                    val c = findTotalCount(value)
                    if (c > 0) return c
                }
            }
        }
        return 0
    }

    private fun createService(): KuGouService {
        val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor { message ->
            android.util.Log.d("LxMusic_HTTP", message)
        }.apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        }

        // 自动保存和回传 Cookie
        val cookieStore = mutableMapOf<String, String>()
        // 暴露 cookieStore 以便清除
        this._cookieStore = cookieStore
        val cookieJar = object : okhttp3.CookieJar {
            override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                cookies.forEach { cookie ->
                    if (cookie.value.isNotBlank()) {
                        cookieStore[cookie.name] = cookie.value
                    } else if (cookieStore.containsKey(cookie.name)) {
                        // 服务器返回空值时，删除旧值（不要用空值覆盖）
                        cookieStore.remove(cookie.name)
                    }
                }
            }
            override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
                // 合并服务器 Cookie 和自定义认证 Cookie
                val allCookies = cookieStore.toMutableMap()
                if (dfid.isNotBlank()) allCookies["dfid"] = dfid

                // 根据 useOwnerAuth 选择使用 VIP 账号或当前用户账号
                val authToken: String
                val authUserid: String
                if (useOwnerAuth) {
                    // 优先使用手动设置的 vipUserid，其次用 ownerToken
                    if (vipUserid.isNotBlank()) {
                        authToken = token  // 用当前用户的 token
                        authUserid = vipUserid
                    } else if (ownerToken.isNotBlank()) {
                        authToken = ownerToken
                        authUserid = ownerUserid
                    } else {
                        authToken = token
                        authUserid = userid
                    }
                } else {
                    authToken = token
                    authUserid = userid
                }
                if (authToken.isNotBlank()) allCookies["token"] = authToken
                if (authUserid.isNotBlank()) allCookies["userid"] = authUserid

                // 过滤掉空值 cookie
                val result = allCookies.filter { it.value.isNotBlank() }.map { (name, value) ->
                    okhttp3.Cookie.Builder()
                        .domain(url.host)
                        .path("/")
                        .name(name)
                        .value(value)
                        .build()
                }
                return result
            }
        }

        // 响应体日志拦截器
        val bodyLogInterceptor = okhttp3.Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val url = response.request.url.toString()
            if (url.contains("song/url")) {
                val body = response.peekBody(4096).string()
                android.util.Log.d("LxMusic", "song/url response body: $body")
            }
            response
        }

        val client = okhttp3.OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(bodyLogInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KuGouService::class.java)
    }
}
