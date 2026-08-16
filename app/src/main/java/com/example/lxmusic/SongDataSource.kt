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

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        if (uri.scheme == "song") {
            // song://hash|album_audio_id -> 去掉可能的 "//" 前缀
            val path = (uri.schemeSpecificPart ?: "").removePrefix("//")
            val parts = path.split("|")
            val hash = parts.getOrElse(0) { "" }
            val albumAudioId = parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L

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
                    KuGouApi.useOwnerAuth = true
                    android.util.Log.d("LxMusic", "SongDataSource: vipUserid='${KuGouApi.vipUserid}', ownerToken='${KuGouApi.ownerToken.take(5)}', token='${KuGouApi.token.take(5)}'")
                    val authMethod = when {
                        KuGouApi.vipUserid.isNotBlank() -> "vipUserid=${KuGouApi.vipUserid}"
                        KuGouApi.ownerToken.isNotBlank() -> "ownerToken"
                        else -> "userToken"
                    }
                    android.util.Log.d("LxMusic", "SongDataSource: 解析 hash=$hash, auth=$authMethod")
                    val lastIndex = qualityChain.size - 1
                    for ((idx, quality) in qualityChain.withIndex()) {
                        val qParam = quality.ifBlank { null }
                        val resp = KuGouApi.service.getSongUrl(hash, albumAudioId, qParam)
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
                    KuGouApi.useOwnerAuth = false
                } catch (e: Exception) {
                    KuGouApi.useOwnerAuth = false
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
