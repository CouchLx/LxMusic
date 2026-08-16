package com.example.lxmusic.coil

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import java.io.File

/**
 * Coil Fetcher：从音频文件中提取专辑封面
 * 支持 mp3/flac/m4a/aac/wav 等格式的内嵌封面
 */
class AudioArtFetcher(
    private val file: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        if (!file.exists() || !isAudioFile(file)) return null

        val artBytes = extractAlbumArtBytes(file) ?: return null

        // 先获取图片尺寸，计算降采样率（目标256px足够封面显示）
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, bounds)
        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, 256)

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, decodeOptions)
            ?: return null

        return DrawableResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = sampleSize > 1,
            dataSource = DataSource.DISK
        )
    }

    private fun calculateSampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > maxSize * 2 || height / sampleSize > maxSize * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun isAudioFile(file: File): Boolean {
        val audioExtensions = setOf("mp3", "flac", "m4a", "aac", "wav", "ogg", "wma")
        return file.extension.lowercase() in audioExtensions
    }

    private fun extractAlbumArtBytes(file: File): ByteArray? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val artBytes = retriever.embeddedPicture
            retriever.release()
            artBytes
        } catch (e: Exception) {
            null
        }
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(
            data: File,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher? {
            val audioExtensions = setOf("mp3", "flac", "m4a", "aac", "wav", "ogg", "wma")
            return if (data.extension.lowercase() in audioExtensions) {
                AudioArtFetcher(data, options)
            } else {
                null
            }
        }
    }
}
