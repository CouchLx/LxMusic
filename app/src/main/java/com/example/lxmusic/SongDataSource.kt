package com.example.lxmusic

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import kotlinx.coroutines.runBlocking

/**
 * 自定义 DataSource：接收 song://hash|album_audio_id 格式的 URI，
 * 自动从 API 获取真实播放 URL，然后用 DefaultHttpDataSource 播放。
 */
@OptIn(UnstableApi::class)
class SongDataSource : DataSource {

    private var inner: DataSource? = null

    companion object {
        // 存储最近获取的歌曲时长（毫秒），供外部读取
        var lastTimeLength: Long = 0
            private set
    }

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {}

    /** 插件解析预算：未激活时插件是唯一出路，给足时间；已激活时快速失败回退内置 */
    private fun pluginBudgetMs(): Long = if (BffApi.onlineAllowed) 5_000L else 12_000L

    private fun notifyPluginFailure(context: android.content.Context, label: String) {
        if (BffApi.onlineAllowed) return
        val reason = com.example.lxmusic.data.plugin.PluginSourceProvider.lastResolveError
            ?: "未返回播放地址"
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(
                context, "$label 音源解析失败：$reason", android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openWithUrl(dataSpec: DataSpec, url: String): Long {
        val innerSource = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)
            .createDataSource()
        val innerSpec = DataSpec.Builder()
            .setUri(url)
            .setPosition(dataSpec.position)
            .setLength(dataSpec.length)
            .setKey(dataSpec.key)
            .build()
        inner = innerSource
        return innerSource.open(innerSpec)
    }

    /** QQ/酷我：只有导入支持该平台的落雪音源才能播放 */
    private fun openThirdPartyPlatform(dataSpec: DataSpec, uri: Uri, platform: String): Long {
        val rawId = (uri.schemeSpecificPart ?: "").removePrefix("//").substringBefore('?')
        val albumMid = runCatching { uri.getQueryParameter("albummid") }.getOrNull().orEmpty()
        val context = com.example.lxmusic.data.plugin.PluginContext.get()
            ?: throw java.io.IOException("未初始化插件运行时")
        val current = com.example.lxmusic.data.plugin.PluginPlayback.currentSong
        val infoJson = org.json.JSONObject().apply {
            put("id", rawId)
            put("songmid", rawId)
            if (platform == "kw") put("hash", rawId)
            if (platform == "tx" && albumMid.isNotBlank()) put("albumMid", albumMid)
            if (current != null) {
                put("name", current.title)
                put("singer", current.artist)
                put("interval", (current.duration / 1000).toInt())
            }
        }.toString()
        val url = runBlocking {
            com.example.lxmusic.data.plugin.PluginSourceProvider.resolveMusicUrl(
                context, platform, infoJson, KuGouApi.audioQuality ?: "flac", pluginBudgetMs()
            )
        }
        if (url.isNullOrBlank()) {
            val label = if (platform == "tx") "QQ音乐" else "酷我音乐"
            notifyPluginFailure(context, label)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    context, "$label 需要导入支持该平台的音源插件后才能播放", android.widget.Toast.LENGTH_LONG
                ).show()
            }
            throw java.io.IOException("$label 需要音源插件: $rawId")
        }
        android.util.Log.d("LxMusic", "SongDataSource: 插件直链($platform) id=$rawId")
        KuGouApi.lastBitRate = 320000
        KuGouApi.lastExtName = url.substringBefore('?').substringAfterLast('.', "")
        lastTimeLength = 0
        return openWithUrl(dataSpec, url)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        if (uri.scheme == "tx" || uri.scheme == "kw") {
            return openThirdPartyPlatform(dataSpec, uri, uri.scheme ?: "")
        }
        if (uri.scheme == "netease") {
            // netease://<songId> -> 网易云歌曲：调网易云 API 获取真实播放 URL
            val songId = (uri.schemeSpecificPart ?: "").removePrefix("//").toLongOrNull() ?: 0L
            if (songId <= 0) {
                throw java.io.IOException("无效的网易云歌曲地址: $uri")
            }
            // 插件优先：已启用且支持 wy 的落雪源先尝试解析（失败自动回退内置链路）
            val pluginContext = com.example.lxmusic.data.plugin.PluginContext.get()
            if (pluginContext != null) {
                val current = com.example.lxmusic.data.plugin.PluginPlayback.currentSong
                // 搜索结果里保存过完整 musicInfo（含 albumName/interval），优先使用
                val storedInfo = com.example.lxmusic.data.plugin.PluginSongStore
                    .get(pluginContext, "wymeta|$songId")?.toString()
                val infoJson = storedInfo ?: org.json.JSONObject().apply {
                    put("id", songId)
                    put("songmid", songId.toString())
                    if (current != null && current.filePath == "netease://$songId") {
                        put("name", current.title)
                        put("singer", current.artist)
                        put("albumName", "")
                        put("interval", (current.duration / 1000).toInt())
                    }
                }.toString()
                val pluginUrl = runBlocking {
                    com.example.lxmusic.data.plugin.PluginSourceProvider.resolveMusicUrl(
                        pluginContext, "wy", infoJson, KuGouApi.audioQuality ?: "flac", pluginBudgetMs()
                    )
                }
                if (pluginUrl.isNullOrBlank()) notifyPluginFailure(pluginContext, "网易云")
                if (!pluginUrl.isNullOrBlank()) {
                    android.util.Log.d("LxMusic", "SongDataSource: 插件直链(网易云) id=$songId")
                    KuGouApi.lastBitRate = 320000
                    KuGouApi.lastExtName = pluginUrl.substringBefore('?').substringAfterLast('.', "")
                    lastTimeLength = 0
                    val innerSource = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setConnectTimeoutMs(15000)
                        .setReadTimeoutMs(15000)
                        .setAllowCrossProtocolRedirects(true)
                        .createDataSource()
                    val innerSpec = DataSpec.Builder()
                        .setUri(pluginUrl)
                        .setPosition(dataSpec.position)
                        .setLength(dataSpec.length)
                        .setKey(dataSpec.key)
                        .build()
                    inner = innerSource
                    return innerSource.open(innerSpec)
                }
            }
            val playUrl = runBlocking {
                var resultUrl: String? = null
                try {
                    for ((idx, level) in NeteaseApi.qualityLevels().withIndex()) {
                        val resp = NeteaseApi.service.getSongUrl(songId, level)
                        val data = resp.data?.firstOrNull()
                        val url = data?.url
                        if (!url.isNullOrBlank()) {
                            if (data.isTrial) {
                                android.util.Log.w("LxMusic", "SongDataSource: 网易云歌曲为试听片段 id=$songId level=$level")
                            }
                            // 复用酷狗音质徽章字段（PlayerStage 读取），按 level 估算码率
                            KuGouApi.lastBitRate = NeteaseApi.bitrateOf(level)
                            KuGouApi.lastExtName = data.type
                            lastTimeLength = 0
                            android.util.Log.d("LxMusic", "SongDataSource: 网易云 id=$songId, level=$level, type=${data.type}")
                            resultUrl = url
                            break
                        }
                        android.util.Log.d("LxMusic", "SongDataSource: 网易云 id=$songId level=$level 不可用, 尝试下一档")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LxMusic", "SongDataSource: 网易云获取URL失败", e)
                }
                resultUrl
            }
            if (playUrl.isNullOrBlank()) {
                android.util.Log.e("LxMusic", "SongDataSource: 无法获取网易云播放地址 id=$songId")
                throw java.io.IOException("无法获取网易云歌曲播放地址: $songId")
            }
            val innerSource = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true)
                .createDataSource()
            val innerSpec = DataSpec.Builder()
                .setUri(playUrl)
                .setPosition(dataSpec.position)
                .setLength(dataSpec.length)
                .setKey(dataSpec.key)
                .build()
            inner = innerSource
            return innerSource.open(innerSpec)
        }
        if (uri.scheme == "lxsrc") {
            // lxsrc://<pluginId>/<songKey> -> 用户导入的 JS 音源插件取流
            val path = "lxsrc://" + (uri.schemeSpecificPart ?: "").removePrefix("//")
            val playUrl = runBlocking {
                val context = com.example.lxmusic.data.plugin.PluginContext.get()
                    ?: return@runBlocking null
                com.example.lxmusic.data.plugin.PluginSourceProvider.getMusicUrlLegacy(
                    context, path, KuGouApi.audioQuality ?: "flac"
                )
            }
            if (playUrl.isNullOrBlank()) {
                android.util.Log.e("LxMusic", "SongDataSource: 插件未返回播放地址 $path")
                throw java.io.IOException("插件未返回播放地址")
            }
            KuGouApi.lastBitRate = 320000
            KuGouApi.lastExtName = playUrl.substringBefore('?').substringAfterLast('.', "")
            lastTimeLength = 0
            android.util.Log.d("LxMusic", "SongDataSource: 插件直链 path=$path")
            val innerSource = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true)
                .createDataSource()
            val innerSpec = DataSpec.Builder()
                .setUri(playUrl)
                .setPosition(dataSpec.position)
                .setLength(dataSpec.length)
                .setKey(dataSpec.key)
                .build()
            inner = innerSource
            return innerSource.open(innerSpec)
        }
        if (uri.scheme == "song") {
            // song://hash|album_audio_id -> 去掉可能的 "//" 前缀
            val path = (uri.schemeSpecificPart ?: "").removePrefix("//")
            val parts = path.split("|")
            val hash = parts.getOrElse(0) { "" }
            val albumAudioId = parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L

            // 插件优先：已启用且支持 kg 的落雪源先尝试解析
            val pluginContext = com.example.lxmusic.data.plugin.PluginContext.get()
            if (pluginContext != null) {
                val current = com.example.lxmusic.data.plugin.PluginPlayback.currentSong
                // 搜索结果里保存过完整 musicInfo（含真实 albumId），优先使用
                val storedInfo = com.example.lxmusic.data.plugin.PluginSongStore
                    .get(pluginContext, "kgmeta|$hash|$albumAudioId")?.toString()
                val infoJson = storedInfo ?: org.json.JSONObject().apply {
                    put("hash", hash)
                    put("songmid", hash)
                    put("albumId", albumAudioId)
                    put("albumAudioId", albumAudioId)
                    if (current != null && current.filePath.substringBefore('|').equals(hash, true)) {
                        put("name", current.title)
                        put("singer", current.artist)
                        put("interval", (current.duration / 1000).toInt())
                    }
                }.toString()
                val pluginUrl = runBlocking {
                    com.example.lxmusic.data.plugin.PluginSourceProvider.resolveMusicUrl(
                        pluginContext, "kg", infoJson, KuGouApi.audioQuality ?: "flac", pluginBudgetMs()
                    )
                }
                if (pluginUrl.isNullOrBlank()) notifyPluginFailure(pluginContext, "酷狗")
                if (!pluginUrl.isNullOrBlank()) {
                    android.util.Log.d("LxMusic", "SongDataSource: 插件直链(酷狗) hash=$hash")
                    KuGouApi.lastBitRate = 320000
                    KuGouApi.lastExtName = pluginUrl.substringBefore('?').substringAfterLast('.', "")
                    lastTimeLength = 0
                    val innerSource = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setConnectTimeoutMs(15000)
                        .setReadTimeoutMs(15000)
                        .setAllowCrossProtocolRedirects(true)
                        .createDataSource()
                    val innerSpec = DataSpec.Builder()
                        .setUri(pluginUrl)
                        .setPosition(dataSpec.position)
                        .setLength(dataSpec.length)
                        .setKey(dataSpec.key)
                        .build()
                    inner = innerSource
                    return innerSource.open(innerSpec)
                }
            }

            // 音质降级链：从用户选择的音质开始，逐级降到最低
            // 明确音质(320/flac)优先于模糊音质(high)，避免 "high" 返回低码率就停止
            val explicitQualities = listOf("flac", "320", "128")
            val qualityChain = buildList {
                val q = KuGouApi.audioQuality
                if (q != null) add(q)
                // 先尝试明确音质，再尝试 high，最后默认
                for (fallback in explicitQualities) {
                    if (fallback != q) add(fallback)
                }
                if (q != "high") add("high")
                add("") // 最后用默认
            }.distinct()

            val playUrl = runBlocking {
                var resultUrl: String? = null
                lastTimeLength = 0
                try {
                    // BFF 优先：设置里配置了 bff_url 时走 Java 聚合后端（失败自动回退原链路）
                    if (BffApi.isEnabled) {
                        val bff = BffApi.play("kg:$hash:$albumAudioId", KuGouApi.audioQuality ?: "flac")
                        if (bff?.ok == true && !bff.url.isNullOrBlank()) {
                            KuGouApi.lastBitRate = if ((bff.bitRate ?: 0) > 0) bff.bitRate!! else 320000
                            KuGouApi.lastExtName = bff.extName
                            lastTimeLength = bff.durationMs ?: 0L
                            android.util.Log.d("LxMusic", "SongDataSource: BFF 直链 hash=$hash, ext=${bff.extName}")
                            return@runBlocking bff.url!!
                        }
                        android.util.Log.w("LxMusic", "SongDataSource: BFF 取流不可用(${bff?.message})，回退原链路")
                    }
                    // 借号取流：走独立的 ownerService（Cookie 上下文与普通请求隔离，
                    // 已激活时固定用 owner 账号身份，未激活回退当前用户），不再用全局开关
                    val authMethod = when {
                        KuGouApi.ownerToken.isNotBlank() -> "ownerToken"
                        else -> "userToken"
                    }
                    android.util.Log.d("LxMusic", "SongDataSource: 解析 hash=$hash, auth=$authMethod")
                    val lastIndex = qualityChain.size - 1
                    for ((idx, quality) in qualityChain.withIndex()) {
                        val qParam = quality.ifBlank { null }
                        val resp = KuGouApi.ownerService.getSongUrl(hash, albumAudioId, qParam)
                        val url = resp.play_url
                        val isLast = idx == lastIndex
                        // 成功条件：status=1、有URL、bitRate>0
                        // 非最后一项时要求 bitRate>=128，避免 "high" 返回低码率就停止尝试
                        val accepted = resp.status == 1 && !url.isNullOrBlank() && resp.bitRate > 0
                                && (isLast || resp.bitRate >= 128)
                        if (accepted) {
                            KuGouApi.lastBitRate = resp.bitRate
                            KuGouApi.lastExtName = resp.extName
                            lastTimeLength = resp.timeLength.toLong() * 1000 // 转为毫秒
                            android.util.Log.d("LxMusic", "SongDataSource: hash=$hash, quality=$qParam, bitRate=${resp.bitRate}, extName=${resp.extName}, timeLength=${resp.timeLength}")
                            resultUrl = url
                            break
                        }
                        val reason = when {
                            resp.status != 1 -> "status=${resp.status}"
                            url.isNullOrBlank() -> "URL为空"
                            resp.bitRate == 0 -> "bitRate=0"
                            resp.bitRate < 128 -> "bitRate=${resp.bitRate}<128"
                            else -> "未知"
                        }
                        android.util.Log.d("LxMusic", "SongDataSource: hash=$hash, quality=$qParam 不可用($reason), 尝试下一音质")
                    }
                } catch (e: Exception) {
                    KuGouApi.lastBitRate = 0
                    KuGouApi.lastExtName = null
                    android.util.Log.e("LxMusic", "SongDataSource: 获取URL失败", e)
                }
                resultUrl
            }

            if (playUrl.isNullOrBlank()) {
                android.util.Log.e("LxMusic", "SongDataSource: 无法获取播放URL hash=$hash, ownerToken=${KuGouApi.ownerToken.take(10)}, token=${KuGouApi.token.take(10)}")
                throw java.io.IOException("无法获取歌曲播放地址: $hash")
            }

            // 用真实 URL 创建内部 DataSource
            val innerSource = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true)
                .createDataSource()

            val innerSpec = DataSpec.Builder()
                .setUri(playUrl)
                .setPosition(dataSpec.position)
                .setLength(dataSpec.length)
                .setKey(dataSpec.key)
                .build()

            inner = innerSource
            return innerSource.open(innerSpec)
        }

        // 本地文件用 FileDataSource
        if (uri.scheme == "file" || uri.scheme == "content") {
            val fileSource = androidx.media3.datasource.FileDataSource()
            inner = object : DataSource {
                override fun addTransferListener(l: androidx.media3.datasource.TransferListener) {}
                override fun open(spec: DataSpec): Long = fileSource.open(spec)
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = fileSource.read(buffer, offset, length)
                override fun getUri(): Uri? = fileSource.uri
                override fun close() = fileSource.close()
            }
            return fileSource.open(dataSpec)
        }

        // 其他 URL（http/https）用 DefaultHttpDataSource
        val defaultSource = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)
            .createDataSource()
        inner = defaultSource
        return defaultSource.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return inner?.read(buffer, offset, length) ?: -1
    }

    override fun getUri(): Uri? = inner?.uri

    override fun close() {
        inner?.close()
        inner = null
    }

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = SongDataSource()
    }
}
