package com.example.lxmusic

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网易云有两套歌曲结构，这里把两种真实返回都钉死：
 * - 新版：ar / al / dt（搜索、每日推荐）
 * - 旧版：artists / album / duration（私人漫游 /personal/fm/mode）
 *
 * 旧版如果不兼容，漫游出来的歌就会「未知艺术家 + 没封面」。
 */
class NeteaseSongParseTest {

    private val gson = Gson()

    @Test
    fun parsesNewSchema() {
        val json = """
            {"code":200,"data":{"dailySongs":[
              {"id":1,"name":"新格式","ar":[{"name":"歌手A"},{"name":"歌手B"}],
               "al":{"picUrl":"https://p1.music.126.net/new.jpg","name":"专辑"},"dt":240000}
            ]}}
        """.trimIndent()
        val resp = gson.fromJson(json, NeteaseRecommendResponse::class.java)
        val song = resp.data?.dailySongs.orEmpty().single()
        assertEquals("歌手A/歌手B", song.artist)
        assertEquals(240000L, song.dt)
        assertTrue(song.coverUrl.startsWith("https://p1.music.126.net/new.jpg"))
    }

    @Test
    fun parsesOldFmSchema() {
        val json = """
            {"code":200,"data":[
              {"id":123,"name":"神のまにまに",
               "artists":[{"id":9,"name":"れるりり"}],
               "album":{"id":8,"name":"みんなの作品","picUrl":"http://p1.music.126.net/old.jpg"},
               "duration":255000,"fee":0}
            ]}
        """.trimIndent()
        val resp = gson.fromJson(json, NeteasePersonalFmResponse::class.java)
        val song = resp.songs().single()
        assertEquals("れるりり", song.artist)
        assertEquals(255000L, song.dt)
        // 旧接口的 http:// 要升到 https，否则 Android 9+ 取不到图
        assertEquals("https://p1.music.126.net/old.jpg?param=300y300", song.coverUrl)
        assertEquals("netease://123", song.toSongInfo().filePath)
        assertEquals("https://p1.music.126.net/old.jpg?param=300y300", song.toSongInfo().albumArtUri)
    }

    @Test
    fun parsesOldSchemaWrappedAgain() {
        // 有些版本会再包一层 data.data
        val json = """
            {"code":200,"data":{"data":[
              {"id":7,"name":"包两层","artists":[{"name":"某人"}],"album":{"picUrl":"https://x/y.jpg"},"duration":1000}
            ]}}
        """.trimIndent()
        val resp = gson.fromJson(json, NeteasePersonalFmResponse::class.java)
        assertEquals("某人", resp.songs().single().artist)
    }

    @Test
    fun parsesSongsWrappedInSongField() {
        // 熟悉/探索/拼图/场景模式有时把单曲再包一层：[{ song: {...} }]
        val json = """
            {"code":200,"data":{"songs":[
              {"recommendReason":"因为你喜欢","song":{"id":42,"name":"包一层","ar":[{"name":"歌手X"}],
                "al":{"picUrl":"https://p1.music.126.net/wrapped.jpg"},"dt":180000}}
            ]}}
        """.trimIndent()
        val resp = gson.fromJson(json, NeteasePersonalFmResponse::class.java)
        val song = resp.songs().single()
        assertEquals(42L, song.id)
        assertEquals("包一层", song.name)
        assertEquals("歌手X", song.artist)
        assertEquals("https://p1.music.126.net/wrapped.jpg?param=300y300", song.coverUrl)
    }

    @Test
    fun parsesTopLevelPicUrlFallback() {
        // 少数接口把封面挂在顶层 picUrl，没有 al/album
        val json = """
            {"code":200,"data":[
              {"id":9,"name":"顶层封面","artists":[{"name":"歌手Y"}],"picUrl":"https://p1.music.126.net/top.jpg","duration":120000}
            ]}
        """.trimIndent()
        val resp = gson.fromJson(json, NeteasePersonalFmResponse::class.java)
        val song = resp.songs().single()
        assertEquals("https://p1.music.126.net/top.jpg?param=300y300", song.coverUrl)
        assertEquals("歌手Y", song.artist)
    }

    @Test
    fun ignoresEmptyPlaceholderItems() {
        // 解析失败的占位项（全空）不应该混进播放队列，否则外部播放条会显示「未知歌曲」
        val json = """
            {"code":200,"data":[
              {},
              {"id":11,"name":"正常","ar":[{"name":"歌手Z"}],"al":{"picUrl":"https://x/z.jpg"},"dt":1000}
            ]}
        """.trimIndent()
        val resp = gson.fromJson(json, NeteasePersonalFmResponse::class.java)
        assertEquals(1, resp.songs().size)
        assertEquals("正常", resp.songs().single().name)
    }
}
