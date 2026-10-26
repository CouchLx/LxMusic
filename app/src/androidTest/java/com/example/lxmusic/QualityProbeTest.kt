package com.example.lxmusic

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lxmusic.data.plugin.PluginSourceProvider
import com.example.lxmusic.data.plugin.PluginStore
import com.example.lxmusic.data.search.PublicSearchProviders
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** 诊断：插件在不同音质下的解析耗时与结果 */
@RunWith(AndroidJUnit4::class)
class QualityProbeTest {

    @Test
    fun probeQualities() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        PluginStore.list(ctx).forEach { PluginStore.setEnabled(ctx, it.id, true) }
        Log.i("LxProbe", "启用插件=${PluginStore.enabled(ctx).map { it.name }}")

        suspend fun probe(platform: String, keyword: String) {
            val songs = PublicSearchProviders.search(platform, keyword, 1, 2)
            if (songs.isEmpty()) { Log.i("LxProbe", "$platform 无搜索结果"); return }
            val song = songs.first()
            for (q in listOf("flac", "320k", "128k")) {
                val t0 = System.currentTimeMillis()
                val url = PluginSourceProvider.resolveMusicUrl(ctx, platform, song.musicInfoJson(), q)
                Log.i(
                    "LxProbe",
                    "$platform ${song.title} q=$q cost=${System.currentTimeMillis() - t0}ms ok=${!url.isNullOrBlank()} url=${url?.take(60)}"
                )
            }
        }

        probe("kg", "晴天")
        probe("wy", "起风了")
    }
}
