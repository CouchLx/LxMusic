package com.example.lxmusic

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lxmusic.data.plugin.PluginSourceProvider
import com.example.lxmusic.data.plugin.PluginStore
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 真实落雪源兼容测试（文件需由开发机推送到 App 私有目录，仓库不含任何源）：
 *   files/test_source_xinghai.js / test_source_yehua.js
 *
 * 只验证协议兼容性（inited 平台解析 + 网易云取流），第三方服务可用性不作硬断言。
 */
@RunWith(AndroidJUnit4::class)
class RealLxSourceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun load(name: String): Pair<File, String?> {
        val ctx = instrumentation.targetContext
        val f = File(ctx.filesDir, name)
        if (!f.exists()) return f to null
        return f to runCatching { f.readText() }.getOrNull()
    }

    @Test
    fun searchThenResolve() {
        val (_, code) = load("test_source_xinghai.js")
        if (code.isNullOrBlank()) {
            Log.i("LxRealTest", "跳过：未找到源文件")
            return
        }
        val ctx = instrumentation.targetContext
        val info = PluginStore.import(ctx, "星海音乐源", code)
        try {
            // 用公开搜索拿到真实歌曲，再交给插件解析（模拟 App 内搜索→播放链路）
            val songs = runBlocking { com.example.lxmusic.data.search.PublicSearchProviders.search("wy", "起风了", 1, 3) }
            Log.i("LxRealTest", "公开搜索网易云: ${songs.map { "${it.title}/${it.artist}" }}")
            assertTrue("搜索应有结果", songs.isNotEmpty())
            val song = songs.first()
            val url = PluginSourceProvider.resolveMusicUrl(ctx, "wy", song.musicInfoJson(), "320k")
            Log.i("LxRealTest", "搜索→插件解析: ${song.title} -> $url")
            assertTrue("应拿到直链", !url.isNullOrBlank() && url.startsWith("http"))

            // 酷狗链路（源后端可能超时，仅记录不断言）
            val kgSongs = runBlocking { com.example.lxmusic.data.search.PublicSearchProviders.search("kg", "晴天", 1, 2) }
            if (kgSongs.isNotEmpty()) {
                val kgUrl = PluginSourceProvider.resolveMusicUrl(ctx, "kg", kgSongs.first().musicInfoJson(), "320k")
                Log.i("LxRealTest", "酷狗搜索→插件解析: ${kgSongs.first().title} -> $kgUrl")
            }
        } finally {
            PluginStore.delete(ctx, info.id)
        }
    }

    @Test
    fun xinghaiSource() {
        val (file, code) = load("test_source_xinghai.js")
        if (code.isNullOrBlank()) {
            Log.i("LxRealTest", "跳过：未找到 ${file.absolutePath}")
            return
        }
        val ctx = instrumentation.targetContext
        val info = PluginStore.import(ctx, "星海音乐源", code)
        try {
            Log.i("LxRealTest", "导入结果: name=${info.name}, ver=${info.version}, err=${info.error}")
            Log.i("LxRealTest", "平台: ${info.platforms}")
            assertNull("源应成功加载: ${info.error}", info.error)
            assertTrue("应解析出平台", info.platforms.contains("\"key\""))

            val cases = listOf(
                Triple(
                    "wy",
                    """{"id":1974443814,"songmid":"1974443814","name":"起风了","singer":"买辣椒也用券","albumName":"","interval":325}""",
                    "网易云-起风了"
                ),
                Triple(
                    "wy",
                    """{"id":1330348068,"songmid":"1330348068","name":"孤勇者","singer":"陈奕迅","albumName":"","interval":256}""",
                    "网易云-孤勇者"
                ),
                Triple(
                    "kg",
                    """{"hash":"0A69169202DE95AAF24A9944CCF0730D","songmid":"0A69169202DE95AAF24A9944CCF0730D","albumId":32100650,"albumAudioId":32100650,"name":"晴天","singer":"周杰伦","interval":269}""",
                    "酷狗-晴天"
                )
            )
            for ((platform, musicInfo, label) in cases) {
                val url = PluginSourceProvider.resolveMusicUrl(ctx, platform, musicInfo, "320k")
                Log.i("LxRealTest", "$label 取流: $url")
                if (!url.isNullOrBlank()) {
                    assertTrue("应返回 http 链接: $url", url.startsWith("http"))
                    val lyric = PluginSourceProvider.resolveLyric(ctx, platform, musicInfo)
                    Log.i("LxRealTest", "$label 歌词: ${lyric?.first?.take(80)}")
                }
            }
        } finally {
            PluginStore.delete(ctx, info.id)
        }
    }

    @Test
    fun yehuaSource() {
        val (file, code) = load("test_source_yehua.js")
        if (code.isNullOrBlank()) {
            Log.i("LxRealTest", "跳过：未找到 ${file.absolutePath}")
            return
        }
        val ctx = instrumentation.targetContext
        val info = PluginStore.import(ctx, "野花", code)
        try {
            Log.i("LxRealTest", "野花导入: name=${info.name}, err=${info.error}, platforms=${info.platforms}")
        } finally {
            PluginStore.delete(ctx, info.id)
        }
    }
}
