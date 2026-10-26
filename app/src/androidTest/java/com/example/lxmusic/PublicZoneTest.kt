package com.example.lxmusic

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.lxmusic.data.search.PublicZoneProviders
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 多平台公开专区（分类）冒烟测试 */
@RunWith(AndroidJUnit4::class)
class PublicZoneTest {

    @Test
    fun zonesAllPlatforms() {
        runBlocking {
            for (platform in listOf("kg", "wy", "tx")) {
                val categories = PublicZoneProviders.categories(platform)
                val first = categories.firstOrNull()
                Log.i("LxZoneTest", "$platform 专区数=${categories.size} 第一个=${first?.name} id=${first?.id}")
                assertTrue("$platform 专区列表不能为空", categories.isNotEmpty())
                if (first != null) {
                    val cover = PublicZoneProviders.coverFor(platform, first)
                    val songs = PublicZoneProviders.randomSongs(platform, first, 50)
                    Log.i(
                        "LxZoneTest",
                        "$platform ${first.name} cover=${cover.take(70)} 歌曲=${songs.size} 第一首=${songs.firstOrNull()?.title}/${songs.firstOrNull()?.artist} path=${songs.firstOrNull()?.filePath?.take(60)}"
                    )
                    assertTrue("$platform 专区歌曲不能为空", songs.isNotEmpty())
                }
                val last = categories.lastOrNull()
                if (last != null && last.id != first?.id) {
                    val songs = PublicZoneProviders.randomSongs(platform, last, 50)
                    Log.i("LxZoneTest", "$platform 末尾分类 ${last.name} 歌曲=${songs.size}")
                    assertTrue("$platform 末尾分类应有歌曲", songs.isNotEmpty())
                }
            }
            Unit
        }
    }
}
