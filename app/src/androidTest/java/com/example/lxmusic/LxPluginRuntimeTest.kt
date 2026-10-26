package com.example.lxmusic

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lxmusic.data.plugin.LxPluginRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * QuickJS 插件运行时冒烟测试：用桩插件验证
 * 1) CommonJS 加载与元信息；2) 同步/异步 API 调用；3) lx.request 回调；4) lx.utils.crypto。
 * 桩插件在 androidTest/assets，不包含任何真实音源。
 */
@RunWith(AndroidJUnit4::class)
class LxPluginRuntimeTest {

    @Test
    fun fakePluginWorks() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val source = assets.open("lx_test_plugin.js").bufferedReader().use { it.readText() }

        val runtime = LxPluginRuntime("test", source)
        try {
            val startResult = runtime.start()
            assertEquals("测试源", startResult.meta.name)
            assertEquals("1.0.0", startResult.meta.version)
            assertTrue("应合成 legacy 平台", startResult.platforms.any { it.key == "legacy" })

            val search = runtime.callApi("search", listOf("hello", 1, 30))
            assertNotNull("search 应该返回结果", search)
            assertTrue("search 结果应包含关键字: $search", search!!.contains("Hello hello"))

            val md5 = runtime.callApi("debugMd5", emptyList())
            assertEquals("\"900150983cd24fb0d6963f7d28e17f72\"", md5)

            val url = runtime.callApi("getMusicUrl", listOf(mapOf("id" to "1"), "320"))
            assertNotNull("getMusicUrl 应通过 lx.request 返回", url)
            assertTrue("应拿到 200 状态: $url", url!!.contains("200"))

            val lyric = runtime.callApi("getLyric", listOf(mapOf("id" to "1")))
            assertTrue("歌词应包含测试文本: $lyric", lyric!!.contains("测试歌词"))
        } finally {
            runtime.close()
        }
    }
}
