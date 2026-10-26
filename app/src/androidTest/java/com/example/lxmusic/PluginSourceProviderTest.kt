package com.example.lxmusic

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lxmusic.data.plugin.PluginSourceProvider
import com.example.lxmusic.data.plugin.PluginStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 插件音源全链路（桩插件）：
 * 导入 → 搜索 → 原始对象落盘 → getMusicUrl（lx.request）→ getLyric。
 */
@RunWith(AndroidJUnit4::class)
class PluginSourceProviderTest {

    @Test
    fun stubPluginEndToEnd() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ctx = instrumentation.targetContext
        val code = instrumentation.context.assets
            .open("lx_test_plugin.js").bufferedReader().use { it.readText() }

        val info = PluginStore.import(ctx, "测试源", code)
        try {
            assertNull("插件应导入成功: ${info.error}", info.error)
            assertTrue(info.id.isNotBlank())

            val songs = PluginSourceProvider.search(ctx, info.id, "hello", 1, 30)
            assertEquals("搜索应返回 1 首", 1, songs.size)
            assertEquals("Hello hello", songs[0].title)
            assertTrue("路径应为 lxsrc", songs[0].filePath.startsWith("lxsrc://${info.id}/"))

            // 落雪事件协议（简化格式自动合成 legacy 平台）
            assertTrue("应上报 legacy 平台: ${info.platforms}", info.platforms.contains("legacy"))
            val musicInfo = """{"id":"1","name":"Hello hello","singer":"Tester"}"""
            val url = PluginSourceProvider.resolveMusicUrl(ctx, "legacy", musicInfo, "320")
            assertNotNull("应通过 lx.request 拿到结果", url)
            assertTrue("状态码应为 200: $url", url!!.contains("200"))

            val lyric = PluginSourceProvider.resolveLyric(ctx, "legacy", musicInfo)
            assertNotNull("歌词不应为空", lyric)
            assertTrue("歌词内容: ${lyric!!.first}", lyric.first!!.contains("测试歌词"))
        } finally {
            PluginStore.delete(ctx, info.id)
        }
    }
}
