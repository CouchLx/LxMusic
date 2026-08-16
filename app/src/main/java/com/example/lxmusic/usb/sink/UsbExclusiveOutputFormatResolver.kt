package com.example.lxmusic.usb.sink

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.core.player.usb.sink/UsbExclusiveOutputFormatResolver (adapted for LxMusic)
 */

import androidx.media3.common.C

/** 映射后的输入 PCM 格式（写入 native 的数据格式） */
internal data class PreparedUsbInputPcmFormat(
    val encoding: Int,
    val bytesPerSample: Int
)

/**
 * USB 独占输入编码解析：
 * - 非 FLOAT 输入原样透传（Media3 编码常量与 native usb_pcm_codec 完全一致：16bit=2、24bit=21、32bit=22、BE=0x10000000/0x50000000/0x60000000）
 * - FLOAT 输入按输出 subslot 映射为 16/24/32-bit 整数编码，native 侧以映射后编码 prepare，
 *   Kotlin 侧在写路径把 float 量化成对应整数位深，保证字节账目一致
 */
internal object UsbExclusiveOutputFormatResolver {

    fun preparedInputPcmFormat(
        inputEncoding: Int,
        subslotBytes: Int?
    ): PreparedUsbInputPcmFormat? {
        if (inputEncoding != C.ENCODING_PCM_FLOAT) {
            val bytesPerSample = pcmBytesPerSampleForEncoding(inputEncoding) ?: return null
            return PreparedUsbInputPcmFormat(
                encoding = inputEncoding,
                bytesPerSample = bytesPerSample
            )
        }
        return when (subslotBytes) {
            2 -> PreparedUsbInputPcmFormat(
                encoding = C.ENCODING_PCM_16BIT,
                bytesPerSample = 2
            )
            3 -> PreparedUsbInputPcmFormat(
                encoding = C.ENCODING_PCM_24BIT,
                bytesPerSample = 3
            )
            4 -> PreparedUsbInputPcmFormat(
                encoding = C.ENCODING_PCM_32BIT,
                bytesPerSample = 4
            )
            else -> null
        }
    }

    fun preparedInputPcmFormat(
        inputEncoding: Int,
        outputDescription: String?
    ): PreparedUsbInputPcmFormat? {
        if (outputDescription.isNullOrBlank()) return null
        return preparedInputPcmFormat(
            inputEncoding = inputEncoding,
            subslotBytes = parseOutputSubslotBytes(outputDescription)
        )
    }

    fun pcmBytesPerSampleForEncoding(encoding: Int): Int? {
        return when (encoding) {
            C.ENCODING_PCM_8BIT -> 1
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_32BIT_BIG_ENDIAN,
            C.ENCODING_PCM_FLOAT -> 4
            else -> null
        }
    }

    /** 解析输出格式描述串（"rate=x channels=y bits=z subslot=w"）中的 subslot 字节数 */
    private fun parseOutputSubslotBytes(description: String): Int? {
        val regex = Regex("(?:^|\\s)subslot=([^\\s]+)")
        return regex.find(description)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
