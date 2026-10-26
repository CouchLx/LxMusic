package com.example.lxmusic

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.lxmusic.data.search.PublicPlaylistProviders
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 多平台公开歌单广场冒烟测试 */
@RunWith(AndroidJUnit4::class)
class PublicPlaylistTest {

    @Test
    fun playlistsAllPlatforms() {
        runBlocking {
            for (platform in listOf("kg", "wy", "tx")) {
                val (list, total) = PublicPlaylistProviders.playlists(platform, null, 1, 30)
                val first = list.firstOrNull()
                Log.i(
                    "LxPlaylistTest",
                    "$platform 歌单=${list.size}/$total 第一个=${first?.name} cover=${first?.coverUrl?.take(70)} tracks=${first?.trackCount} play=${first?.playCount}"
                )
                assertTrue("$platform 歌单列表不能为空", list.isNotEmpty())
                if (first != null) {
                    var songs = PublicPlaylistProviders.songs(platform, first!!.id)
                    var used = first!!
                    if (songs.isEmpty()) {
                        // QQ 有少量歌单受隐私限制，换下一个可用歌单验证
                        for (candidate in list.drop(1).take(4)) {
                            val s2 = PublicPlaylistProviders.songs(platform, candidate.id)
                            if (s2.isNotEmpty()) { songs = s2; used = candidate; break }
                        }
                    }
                    Log.i(
                        "LxPlaylistTest",
                        "$platform ${used.name} 歌曲=${songs.size} 第一首=${songs.firstOrNull()?.title}/${songs.firstOrNull()?.artist} path=${songs.firstOrNull()?.filePath?.take(60)} cover=${songs.firstOrNull()?.albumArtUri?.take(60)}"
                    )
                    assertTrue("$platform 歌单歌曲不能为空", songs.isNotEmpty())
                }
            }
            Unit
        }
    }
}
