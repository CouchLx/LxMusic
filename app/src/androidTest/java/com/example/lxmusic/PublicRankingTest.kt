package com.example.lxmusic

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.lxmusic.data.search.PublicRankingProviders
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 多平台公开排行榜冒烟测试 */
@RunWith(AndroidJUnit4::class)
class PublicRankingTest {

    @Test
    fun ranksAllPlatforms() {
        runBlocking {
            for (platform in listOf("kg", "wy", "tx")) {
                val ranks = PublicRankingProviders.rankList(platform)
                val first = ranks.firstOrNull()
                Log.i(
                    "LxRankTest",
                    "$platform 榜单数=${ranks.size} 第一个=${first?.rankname} cover=${first?.coverUrl?.take(70)}"
                )
                assertTrue("$platform 榜单列表不能为空", ranks.isNotEmpty())
                if (first != null) {
                    val (songs, total) = PublicRankingProviders.rankSongs(platform, first.rankid, 1, 30)
                    Log.i(
                        "LxRankTest",
                        "$platform 榜单歌曲=${songs.size}/$total 第一首=${songs.firstOrNull()?.title}/${songs.firstOrNull()?.artist} path=${songs.firstOrNull()?.filePath?.take(60)} cover=${songs.firstOrNull()?.albumArtUri?.take(70)}"
                    )
                    assertTrue("$platform 榜单歌曲不能为空", songs.isNotEmpty())
                }
            }
            Unit
        }
    }
}
