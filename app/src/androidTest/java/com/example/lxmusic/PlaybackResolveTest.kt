package com.example.lxmusic

import android.util.Log
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lxmusic.data.plugin.PluginPlayback
import com.example.lxmusic.data.plugin.PluginSongStore
import com.example.lxmusic.data.plugin.PluginSourceProvider
import com.example.lxmusic.data.plugin.PluginStore
import com.example.lxmusic.data.search.PublicSearchProviders
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真实播放链路测试（不经过 UI）：
 * 多平台搜索 → 落库 musicInfo → SongDataSource.open()（与 App 内播放完全相同的分支）。
 */
@RunWith(AndroidJUnit4::class)
class PlaybackResolveTest {

    @Test
    fun playAllPlatforms() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // 测试进程不会启动 MainActivity，手动初始化插件 Context；并模拟“未激活”
        com.example.lxmusic.data.plugin.PluginContext.init(ctx)
        BffApi.onlineAllowed = false
        // 确保已导入的插件处于启用状态
        PluginStore.list(ctx).forEach { PluginStore.setEnabled(ctx, it.id, true) }
        Log.i("LxPlayTest", "已启用插件: ${PluginStore.enabled(ctx).map { it.name }}")

        val failed = mutableListOf<String>()
        suspend fun test(platform: String) {
            val songs = PublicSearchProviders.search(platform, "晴天", 1, 3)
            if (songs.isEmpty()) {
                Log.i("LxPlayTest", "$platform 搜索无结果")
                return
            }
            val song = songs.first()
            val metaKey = when (platform) {
                "kg" -> "kgmeta|${song.id}|${song.albumAudioId}"
                "wy" -> "wymeta|${song.id}"
                else -> "meta|${song.id}"
            }
            PluginSongStore.put(ctx, metaKey, song.musicInfoJson())
            val info = song.toSongInfo()
            PluginPlayback.currentSong = info
            val uri = when (platform) {
                "kg" -> "song://${song.id}|${song.albumAudioId}"
                "wy" -> "netease://${song.id}"
                else -> info.filePath
            }
            val ds = SongDataSource()
            val result = runCatching {
                val spec = DataSpec.Builder().setUri(uri).setPosition(0).build()
                val len = ds.open(spec)
                runCatching { ds.close() }
                len
            }
            Log.i(
                "LxPlayTest",
                "$platform ${song.title}/${song.artist} uri=$uri -> len=${result.getOrNull()} err=${result.exceptionOrNull()?.message} pluginErr=${PluginSourceProvider.lastResolveError}"
            )
            if (result.getOrNull() == null) failed += "$platform(${result.exceptionOrNull()?.message})"
        }

        test("kg")
        test("wy")
        test("tx")
        test("kw")
        Log.i("LxPlayTest", "失败平台: $failed")
        Unit
    }
}
