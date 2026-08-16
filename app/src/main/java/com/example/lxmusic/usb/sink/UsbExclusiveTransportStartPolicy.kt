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
 * File: moe.ouom.neriplayer.core.player.usb.sink/UsbExclusiveTransportStartPolicy (adapted for LxMusic)
 */

import kotlin.math.min

/** 原生传输启动策略：预滚达标（或允许短预滚 / 恢复暂停传输）才启动 */
internal fun shouldStartUsbExclusiveNativeTransport(
    hasQueuedPcm: Boolean,
    queuedFrames: Long,
    requiredPrerollFrames: Long,
    pcmCapacityFrames: Long,
    allowShortPreroll: Boolean,
    resumingPausedTransport: Boolean
): Boolean {
    if (!hasQueuedPcm || queuedFrames <= 0L) return false
    val effectivePrerollFrames = effectiveUsbExclusivePrerollFrames(
        requiredPrerollFrames = requiredPrerollFrames,
        pcmCapacityFrames = pcmCapacityFrames
    )
    return allowShortPreroll ||
        resumingPausedTransport ||
        queuedFrames >= effectivePrerollFrames
}

internal fun effectiveUsbExclusivePrerollFrames(
    requiredPrerollFrames: Long,
    pcmCapacityFrames: Long
): Long {
    val requestedFrames = requiredPrerollFrames.coerceAtLeast(1L)
    if (pcmCapacityFrames <= 0L) return requestedFrames
    val capacityWatermarkFrames = (pcmCapacityFrames - pcmCapacityFrames / 4L)
        .coerceAtLeast(1L)
    return min(requestedFrames, capacityWatermarkFrames)
}
