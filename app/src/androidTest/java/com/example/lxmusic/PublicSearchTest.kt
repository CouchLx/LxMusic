package com.example.lxmusic

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lxmusic.data.search.PublicSearchProviders
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 多平台公开搜索冒烟测试：只验证元数据（歌名/歌手/封面），不涉及播放。
 */
@RunWith(AndroidJUnit4::class)
class PublicSearchTest {

    @Test
    fun searchAllPlatforms() = runBlocking {
        val platforms = listOf("kg" to "酷狗", "wy" to "网易云", "tx" to "QQ", "kw" to "酷我")
        val empty = mutableListOf<String>()
        for ((platform, label) in platforms) {
            val list = runCatching { PublicSearchProviders.search(platform, "晴天", 1, 5) }
                .getOrElse {
                    Log.e("LxSearchTest", "$label($platform) 异常: ${it.message}")
                    emptyList()
                }
            val first = list.firstOrNull()
            Log.i(
                "LxSearchTest",
                "$label($platform) size=${list.size} first=${first?.title}/${first?.artist} cover=${first?.coverUrl?.take(80)} id=${first?.id}"
            )
            if (first == null) empty += label
        }
        assertTrue("以下平台没有搜索结果: $empty", empty.isEmpty())
    }
}
