package com.example.lxmusic

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

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
    val data: Any? = null
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
    val global_collection_id: String? = null
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
    @com.google.gson.annotations.SerializedName("songs")
    val list: List<PlaylistTrackSong>? = null,
    val count: Int = 0
)

// 新版歌单歌曲 API 响应 (playlist/track/all/new)
data class PlaylistTracksNewResponse(
    val status: Int = 0,
    val data: PlaylistTracksNewData? = null
)

data class PlaylistTracksNewData(
    val info: List<PlaylistTrackSong>? = null,
    val count: Int = 0
)

data class PlaylistTrackSingerInfo(
    val id: Long = 0,
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
    val name: String? = null,
    val singerinfo: List<PlaylistTrackSingerInfo>? = null,
    val hash: String? = null,
    val album_id: Long = 0,
    val audio_id: Long = 0,
    val mixsongid: Long = 0,
    val timelen: Int = 0,
    val cover: String? = null,
    val id: Long = 0,
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
            singerinfo?.firstOrNull()?.name?.takeIf { it.isNotBlank() }?.let { return it }
            val n = name ?: ""
            // API 返回格式: "歌手 - 歌名.mp3"
            val beforeDash = n.substringBefore(" - ", "").trim()
            if (beforeDash.isNotBlank() && n.contains(" - ")) return beforeDash
            return "未知艺术家"
        }
    val durationMs: Long
        get() {
            if (timelen > 0) return timelen.toLong()
            return (audio_info?.duration_320 ?: audio_info?.duration_128 ?: 0).toLong()
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
    val intro: String? = null
) {
    val coverUrl: String
        get() {
            val raw = img ?: ""
            if (raw.isBlank()) return ""
            return raw.replace("{size}", "240").replace("http://", "https://")
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
        @Query("data") data: String
    ): AddToPlaylistResponse

    // 从歌单删除歌曲
    @GET("playlist/tracks/del")
    suspend fun removeFromPlaylist(
        @Query("listid") listId: Long,
        @Query("fileids") fileids: String
    ): AddToPlaylistResponse
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
