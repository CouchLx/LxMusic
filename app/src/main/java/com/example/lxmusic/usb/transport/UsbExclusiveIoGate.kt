package com.example.lxmusic.usb.transport

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
 * File: moe.ouom.neriplayer.core.player.usb.transport/UsbExclusiveIoGate (adapted for LxMusic)
 */

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 写入闸门：跟踪进行中的 native 写操作，关闭时等待写线程排空，
 * 防止 close 时写半截数据。
 */
internal class UsbExclusiveIoGate {
    private val activeWrites = AtomicInteger(0)
    private val lock = ReentrantLock()
    private val drained: Condition = lock.newCondition()
    @Volatile
    private var closed = false

    fun tryEnterWrite(): Boolean {
        while (true) {
            val current = activeWrites.get()
            if (closed) return false
            if (activeWrites.compareAndSet(current, current + 1)) return true
        }
    }

    fun exitWrite() {
        activeWrites.decrementAndGet()
        if (activeWrites.get() == 0) {
            lock.withLock { drained.signalAll() }
        }
    }

    /** 等待在写线程排空，超时返回 false */
    fun awaitDrained(timeoutMs: Long): Boolean {
        lock.withLock {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (activeWrites.get() > 0) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return false
                drained.awaitNanos(remaining * 1_000_000L)
            }
        }
        return activeWrites.get() == 0
    }

    fun close() {
        closed = true
    }

    fun open() {
        closed = false
    }

    fun isClosed(): Boolean = closed
}
