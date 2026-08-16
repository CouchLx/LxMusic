package com.example.lxmusic.util

/**
 * 从音频文件提取歌词，按优先级尝试：
 * 1. 外部 .lrc 文件（与音频同名）
 * 2. MediaMetadataRetriever (METADATA_KEY_LYRICS)
 * 3. MP3 ID3v2 USLT 帧内嵌歌词
 */
fun extractLyrics(filePath: String): String? {
    // 1. 尝试外部 .lrc 文件
    val lrcFile = java.io.File(filePath.replaceAfterLast(".", "lrc"))
    if (lrcFile.exists()) {
        try {
            val content = lrcFile.readText(Charsets.UTF_8).trim()
            if (content.isNotBlank()) return content
        } catch (_: Exception) {}
        // 尝试 GBK 编码（老歌常见）
        try {
            val content = lrcFile.readText(charset("GBK")).trim()
            if (content.isNotBlank()) return content
        } catch (_: Exception) {}
    }

    // 2. 尝试 MediaMetadataRetriever
    try {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(filePath)
        val lrc = retriever.extractMetadata(26) // METADATA_KEY_LYRICS
        retriever.release()
        if (!lrc.isNullOrBlank()) return lrc
    } catch (_: Exception) {}

    // 3. 尝试解析 MP3 ID3v2 USLT 帧
    if (filePath.endsWith(".mp3", ignoreCase = true)) {
        try {
            val uslt = parseMp3USLT(filePath)
            if (!uslt.isNullOrBlank()) return uslt
        } catch (_: Exception) {}
    }

    // 4. 尝试解析 FLAC Vorbis Comments LYRICS 标签
    if (filePath.endsWith(".flac", ignoreCase = true)) {
        try {
            val flacLrc = parseFlacLyrics(filePath)
            if (!flacLrc.isNullOrBlank()) return flacLrc
        } catch (_: Exception) {}
    }

    return null
}

/**
 * 解析 MP3 文件的 ID3v2 USLT（Unsynchronised Lyrics）帧
 */
private fun parseMp3USLT(filePath: String): String? {
    val file = java.io.File(filePath)
    if (!file.exists() || file.length() < 10) return null

    // 只读取 ID3v2 头部区域，避免把整个音频文件读进内存（大文件会 OOM）。
    // 先读前 10 字节拿到标签大小，再按标签大小读取（上限 256KB，防止异常文件）。
    val raf = java.io.RandomAccessFile(file, "r")
    raf.use {
        val header = ByteArray(10)
        raf.readFully(header)

        // 检查 ID3v2 头
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
            return null
        }

        val version = header[3].toInt() and 0xFF // 3 = ID3v2.3, 4 = ID3v2.4
        val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                ((header[7].toInt() and 0x7F) shl 14) or
                ((header[8].toInt() and 0x7F) shl 7) or
                (header[9].toInt() and 0x7F)

        val readLen = minOf(tagSize, 256 * 1024).coerceAtLeast(0)
        val data = ByteArray(10 + readLen)
        System.arraycopy(header, 0, data, 0, 10)
        raf.readFully(data, 10, readLen)

        var pos = 10
        val end = minOf(10 + tagSize, data.size)

        while (pos + 10 <= end) {
            val frameId = String(data, pos, 4, Charsets.US_ASCII)
            val frameSize = if (version >= 4) {
                ((data[pos + 4].toInt() and 0x7F) shl 21) or
                        ((data[pos + 5].toInt() and 0x7F) shl 14) or
                        ((data[pos + 6].toInt() and 0x7F) shl 7) or
                        (data[pos + 7].toInt() and 0x7F)
            } else {
                ((data[pos + 4].toInt() and 0xFF) shl 24) or
                        ((data[pos + 5].toInt() and 0xFF) shl 16) or
                    ((data[pos + 6].toInt() and 0xFF) shl 8) or
                    (data[pos + 7].toInt() and 0xFF)
        }

        if (frameSize <= 0 || pos + 10 + frameSize > end) break

        if (frameId == "USLT") {
            val encoding = data[pos + 10].toInt() and 0xFF
            // 跳过 language (3 bytes)
            val textStart = pos + 10 + 1 + 3
            val textBytes = data.copyOfRange(textStart, pos + 10 + frameSize)
            return when (encoding) {
                0 -> String(textBytes, Charsets.ISO_8859_1).trim()
                1 -> String(textBytes, Charsets.UTF_16).trim()
                2 -> String(textBytes, Charsets.UTF_16BE).trim()
                3 -> String(textBytes, Charsets.UTF_8).trim()
                else -> String(textBytes, Charsets.UTF_8).trim()
            }
        }

        pos += 10 + frameSize
        }
    }

    return null
}

/**
 * 解析 FLAC 文件的 Vorbis Comments 中的 LYRICS 标签
 * FLAC 结构：fLaC marker → metadata blocks → audio frames
 * Metadata block type 4 = Vorbis Comments
 */
private fun parseFlacLyrics(filePath: String): String? {
    val file = java.io.File(filePath)
    if (!file.exists() || file.length() < 4) return null

    val raf = java.io.RandomAccessFile(file, "r")
    raf.use {
        // 检查 fLaC marker
        val marker = ByteArray(4)
        raf.readFully(marker)
        if (marker[0] != 'f'.code.toByte() || marker[1] != 'L'.code.toByte() ||
            marker[2] != 'a'.code.toByte() || marker[3] != 'C'.code.toByte()) {
            return null
        }

        // 遍历 metadata blocks
        while (raf.filePointer < raf.length()) {
            val headerByte = raf.readUnsignedByte()
            val isLast = (headerByte and 0x80) != 0
            val blockType = headerByte and 0x7F

            // 读取 block 长度（3 字节大端）
            val b1 = raf.readUnsignedByte()
            val b2 = raf.readUnsignedByte()
            val b3 = raf.readUnsignedByte()
            val blockLength = (b1 shl 16) or (b2 shl 8) or b3

            if (blockType == 4) {
                // Vorbis Comments block
                val blockData = ByteArray(blockLength)
                raf.readFully(blockData)
                return parseVorbisComments(blockData)
            } else {
                // 跳过这个 block
                raf.skipBytes(blockLength)
            }

            if (isLast) break
        }
    }
    return null
}

/**
 * 解析 Vorbis Comments 数据，查找 LYRICS 标签
 */
private fun parseVorbisComments(data: ByteArray): String? {
    if (data.size < 8) return null

    var pos = 0

    // 读取 vendor string 长度（小端 4 字节）
    val vendorLen = (data[pos].toInt() and 0xFF) or
            ((data[pos + 1].toInt() and 0xFF) shl 8) or
            ((data[pos + 2].toInt() and 0xFF) shl 16) or
            ((data[pos + 3].toInt() and 0xFF) shl 24)
    pos += 4 + vendorLen

    if (pos + 4 > data.size) return null

    // 读取 comment 数量
    val commentCount = (data[pos].toInt() and 0xFF) or
            ((data[pos + 1].toInt() and 0xFF) shl 8) or
            ((data[pos + 2].toInt() and 0xFF) shl 16) or
            ((data[pos + 3].toInt() and 0xFF) shl 24)
    pos += 4

    for (i in 0 until commentCount) {
        if (pos + 4 > data.size) break

        val commentLen = (data[pos].toInt() and 0xFF) or
                ((data[pos + 1].toInt() and 0xFF) shl 8) or
                ((data[pos + 2].toInt() and 0xFF) shl 16) or
                ((data[pos + 3].toInt() and 0xFF) shl 24)
        pos += 4

        if (pos + commentLen > data.size) break

        val comment = String(data, pos, commentLen, Charsets.UTF_8)
        pos += commentLen

        // Vorbis Comments 格式：KEY=VALUE
        if (comment.startsWith("LYRICS=", ignoreCase = true)) {
            return comment.substring(7).trim()
        }
    }

    return null
}

/**
 * 解析 LRC 歌词文件的每一行
 * 格式: [mm:ss.xx]歌词文本 或 [mm:ss.xxx]歌词文本
 * 返回: Pair<时间戳毫秒, 歌词文本>
 */
fun parseLrcLine(line: String): Pair<Long, String>? {
    val trimmed = line.trim()
    if (trimmed.isBlank()) return null

    // 匹配时间标签 [mm:ss.xx] 或 [mm:ss.xxx]
    val regex = """\[(\d{2}):(\d{2})\.(\d{2,3})]""".toRegex()
    val matches = regex.findAll(trimmed).toList()

    if (matches.isEmpty()) return null

    // 提取歌词文本（去掉所有时间标签）
    var text = trimmed
    for (match in matches) {
        text = text.replace(match.value, "")
    }
    text = text.trim()

    // 取第一个时间标签
    val firstMatch = matches.first()
    val minutes = firstMatch.groupValues[1].toLong()
    val seconds = firstMatch.groupValues[2].toLong()
    val millisStr = firstMatch.groupValues[3]
    val millis = when (millisStr.length) {
        2 -> millisStr.toLong() * 10  // .xx → 毫秒
        3 -> millisStr.toLong()       // .xxx → 毫秒
        else -> millisStr.toLong() * 10
    }

    val timestamp = minutes * 60 * 1000 + seconds * 1000 + millis
    return Pair(timestamp, text)
}
