@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ext.ipc

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.posix.EAGAIN
import platform.posix.EINTR
import platform.posix.EWOULDBLOCK
import platform.posix.MSG_NOSIGNAL
import platform.posix.POLLIN
import platform.posix.SHUT_RDWR
import platform.posix.close
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.posix_errno
import platform.posix.recv
import platform.posix.send
import platform.posix.shutdown
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Linux `AF_UNIX` `SOCK_STREAM` [IpcConnection] (`linuxX64` / `linuxArm64`).
 */
internal class LinuxUnixSocketConnection(
    private val fd: Int,
    maxPacketBytes: Int = IpcPacketFramer.MAX_PACKET_BYTES
) : BaseFramedIpcConnection(maxPacketBytes = maxPacketBytes) {

    private val fdClosed = AtomicBoolean(fd < 0)

    init {
        if (fd < 0) {
            close()
        }
    }

    override fun writeFrameBytes(framedBytes: ByteArray): Boolean {
        var offset = 0
        framedBytes.usePinned { pinned ->
            val basePtr = pinned.addressOf(0).reinterpret<ByteVar>()
            while (offset < framedBytes.size) {
                if (isClosed) return false
                val n = send(fd, basePtr + offset, (framedBytes.size - offset).convert(), MSG_NOSIGNAL).toInt()
                if (n < 0) {
                    val err = posix_errno()
                    if (err == EINTR) continue
                    close()
                    return false
                }
                if (n == 0) {
                    close()
                    return false
                }
                offset += n
            }
        }
        return true
    }

    override fun readFrameBytes(
        count: Int,
        initialTimeoutMs: Int,
        isHeader: Boolean
    ): ByteArray? {
        val initialDeadline: TimeMark? = if (initialTimeoutMs > 0) {
            TimeSource.Monotonic.markNow() + initialTimeoutMs.milliseconds
        } else {
            null
        }
        var completionDeadline: TimeMark? = if (!isHeader && initialTimeoutMs > 0) {
            TimeSource.Monotonic.markNow() + initialTimeoutMs.milliseconds
        } else {
            null
        }

        val out = ByteArray(count)
        var offset = 0
        out.usePinned { pinned ->
            val basePtr = pinned.addressOf(0).reinterpret<ByteVar>()
            while (offset < count) {
                if (isClosed) return null

                val pollWaitMs: Int = when {
                    isHeader && offset == 0 -> when {
                        initialTimeoutMs == 0 -> 0
                        initialDeadline != null -> {
                            val rem = -initialDeadline.elapsedNow().inWholeMilliseconds
                            if (rem <= 0L) return null
                            rem.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        }
                        else -> -1
                    }
                    else -> {
                        val activeDeadline = if (isHeader) {
                            if (completionDeadline == null) {
                                val compMs = computeFrameCompletionTimeoutMs(initialTimeoutMs)
                                completionDeadline = if (compMs > 0) {
                                    TimeSource.Monotonic.markNow() + compMs.milliseconds
                                } else {
                                    null
                                }
                            }
                            completionDeadline
                        } else {
                            completionDeadline
                        }
                        if (activeDeadline != null) {
                            val rem = -activeDeadline.elapsedNow().inWholeMilliseconds
                            if (rem <= 0L) {
                                close()
                                return null
                            }
                            rem.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        } else {
                            -1
                        }
                    }
                }

                if (!waitReadable(pollWaitMs)) {
                    if (offset > 0 || !isHeader) {
                        close()
                    }
                    return null
                }

                val n = recv(fd, basePtr + offset, (count - offset).convert(), 0).toInt()
                if (n < 0) {
                    val err = posix_errno()
                    if (err == EINTR || err == EAGAIN || err == EWOULDBLOCK) {
                        continue
                    }
                    close()
                    return null
                }
                if (n == 0) {
                    close()
                    return null
                }
                offset += n
            }
        }
        return out
    }

    override fun closeTransport() {
        if (fdClosed.compareAndSet(expectedValue = false, newValue = true) && fd >= 0) {
            shutdown(fd, SHUT_RDWR)
            close(fd)
        }
    }

    private fun waitReadable(timeoutMs: Int): Boolean = memScoped {
        val pfd = alloc<pollfd>()
        pfd.fd = fd
        pfd.events = POLLIN.toShort()
        pfd.revents = 0
        while (!isClosed) {
            val rc = poll(pfd.ptr, 1.convert(), timeoutMs)
            if (rc > 0) return@memScoped true
            if (rc == 0) return@memScoped false
            if (posix_errno() != EINTR) return@memScoped false
        }
        false
    }
}
