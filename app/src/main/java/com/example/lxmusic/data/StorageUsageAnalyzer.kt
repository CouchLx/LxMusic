package com.example.lxmusic.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 存储占用分析器（适配 Lxmusic 实际缓存结构，参考 NeriPlayer 的 StorageUsageAnalyzer）。
 *
 * 分类：
 * - 图片缓存：Coil 磁盘缓存（cacheDir/image_cache）
 * - 背景图片：filesDir/bg_image.jpg
 * - 主题预设图片：filesDir/theme_images
 * - 播放缓存：cacheDir 下除 image_cache 外的其它缓存
 * - 应用数据：filesDir 下除已知文件外的数据（数据库等，仅统计不删除）
 */

enum class StorageCacheKind {
    Image,
    BackgroundImage,
    ThemeImages,
    Playback,
    AppData
}

data class StorageCacheClearOptions(
    val imageCache: Boolean = true,
    val backgroundImage: Boolean = true,
    val themeImages: Boolean = true,
    val playbackCache: Boolean = true
) {
    val hasSelection: Boolean
        get() = imageCache || backgroundImage || themeImages || playbackCache
}

data class StorageUsageItem(
    val title: String,
    val description: String,
    val path: String?,
    val sizeBytes: Long,
    val fileCount: Int,
    val cacheKind: StorageCacheKind? = null
)

data class StorageUsageSection(
    val title: String,
    val items: List<StorageUsageItem>
) {
    val sizeBytes: Long = items.sumOf { it.sizeBytes }
    val fileCount: Int = items.sumOf { it.fileCount }
}

data class StorageUsageSummary(
    val sections: List<StorageUsageSection>
) {
    val totalSizeBytes: Long = sections.sumOf { it.sizeBytes }
    val totalFileCount: Int = sections.sumOf { it.fileCount }

    fun sizeOf(kind: StorageCacheKind): Long {
        return sections.asSequence()
            .flatMap { it.items.asSequence() }
            .filter { it.cacheKind == kind }
            .sumOf { it.sizeBytes }
    }

    companion object {
        val Empty = StorageUsageSummary(emptyList())
    }
}

data class ClearCacheResult(
    val freedBytes: Long,
    val deletedFiles: Int
)

private data class FileStats(
    val sizeBytes: Long,
    val fileCount: Int
) {
    operator fun plus(other: FileStats): FileStats = FileStats(
        sizeBytes = sizeBytes + other.sizeBytes,
        fileCount = fileCount + other.fileCount
    )

    companion object {
        val Empty = FileStats(0L, 0)
    }
}

suspend fun analyzeStorageUsage(context: Context): StorageUsageSummary = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val filesDir = appContext.filesDir
    val cacheDir = appContext.cacheDir

    val imageCacheDir = File(cacheDir, "image_cache")
    val bgImageFile = File(filesDir, "bg_image.jpg")
    val themeImagesDir = File(filesDir, "theme_images")
    val playbackCacheDir = cacheDir // 播放缓存 = cacheDir 排除 image_cache 的部分

    val appDataRoot = filesDir

    StorageUsageSummary(
        sections = listOf(
            StorageUsageSection(
                title = "可清理缓存",
                items = listOf(
                    usageItem(
                        title = "图片缓存",
                        description = "专辑封面、背景图片等网络图片缓存（Coil）",
                        file = imageCacheDir,
                        cacheKind = StorageCacheKind.Image
                    ),
                    usageItem(
                        title = "播放缓存",
                        description = "播放器运行期产生的临时缓存",
                        file = playbackCacheDir,
                        excludedRoots = listOf(imageCacheDir),
                        cacheKind = StorageCacheKind.Playback
                    ),
                    usageItem(
                        title = "背景图片",
                        description = "应用内设置的背景图片",
                        file = bgImageFile,
                        cacheKind = StorageCacheKind.BackgroundImage
                    ),
                    usageItem(
                        title = "主题预设图片",
                        description = "主题自定义中保存的预设背景图片",
                        file = themeImagesDir,
                        cacheKind = StorageCacheKind.ThemeImages
                    )
                )
            ),
            StorageUsageSection(
                title = "应用数据",
                items = listOf(
                    usageItem(
                        title = "应用数据",
                        description = "本地数据库、设置等（不含缓存）",
                        file = appDataRoot,
                        excludedRoots = listOf(bgImageFile, themeImagesDir)
                    )
                )
            )
        )
    )
}

suspend fun clearStorageCaches(
    context: Context,
    options: StorageCacheClearOptions
): ClearCacheResult = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val targets = buildList {
        if (options.imageCache) add(File(appContext.cacheDir, "image_cache"))
        if (options.playbackCache) {
            // 播放缓存：清理 cacheDir 下除 image_cache 外的文件
            addAll(
                appContext.cacheDir.listFiles().orEmpty().filter {
                    it.name != "image_cache"
                }
            )
        }
        if (options.backgroundImage) add(File(appContext.filesDir, "bg_image.jpg"))
        if (options.themeImages) add(File(appContext.filesDir, "theme_images"))
    }

    var freedBytes = 0L
    var deletedFiles = 0
    targets.forEach { target ->
        if (!target.exists()) return@forEach
        val before = statsOf(target)
        if (before.fileCount == 0 && before.sizeBytes == 0L) return@forEach
        val deleted = runCatching {
            val ok = target.deleteRecursively()
            if (ok && target.isDirectory) target.mkdirs()
            ok
        }.getOrDefault(false)
        if (deleted) {
            freedBytes += before.sizeBytes
            deletedFiles += before.fileCount
        }
    }
    ClearCacheResult(freedBytes = freedBytes, deletedFiles = deletedFiles)
}

private fun usageItem(
    title: String,
    description: String,
    file: File?,
    cacheKind: StorageCacheKind? = null,
    excludedRoots: List<File> = emptyList()
): StorageUsageItem {
    val stats = statsOf(file, excludedRoots)
    return StorageUsageItem(
        title = title,
        description = description,
        path = file?.absolutePath,
        sizeBytes = stats.sizeBytes,
        fileCount = stats.fileCount,
        cacheKind = cacheKind
    )
}

private fun statsOf(file: File?, excludedRoots: List<File> = emptyList()): FileStats {
    if (file == null || !file.exists()) return FileStats.Empty
    return runCatching {
        if (file.isFile) {
            FileStats(file.length(), 1)
        } else {
            file.walkTopDown()
                .filter { entry -> entry.isFile && excludedRoots.none { entry.isUnder(it) } }
                .fold(FileStats.Empty) { acc, entry ->
                    acc + FileStats(entry.length(), 1)
                }
        }
    }.getOrDefault(FileStats.Empty)
}

private fun File.isUnder(root: File): Boolean {
    val rootPath = runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)
    val filePath = runCatching { canonicalPath }.getOrDefault(absolutePath)
    return filePath == rootPath || filePath.startsWith("$rootPath${File.separator}")
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
