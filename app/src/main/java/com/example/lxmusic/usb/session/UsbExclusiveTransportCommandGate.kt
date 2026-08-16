package com.example.lxmusic.usb.session

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
 * File: moe.ouom.neriplayer.core.player.usb.session/UsbExclusiveTransportCommandGate (adapted for LxMusic)
 */

import java.util.concurrent.atomic.AtomicBoolean

/** 传输命令串行门：play/pause/flush 同一时刻仅允许一个在途 */
internal class UsbExclusiveTransportCommandGate {
    private val commandInFlight = AtomicBoolean(false)

    fun tryAcquire(): Boolean = commandInFlight.compareAndSet(false, true)

    fun release() {
        commandInFlight.set(false)
    }

    fun isHeld(): Boolean = commandInFlight.get()
}
