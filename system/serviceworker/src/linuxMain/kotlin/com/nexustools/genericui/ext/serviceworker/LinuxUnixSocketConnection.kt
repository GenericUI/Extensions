@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.posix.EINTR
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
import platform.posix.usleep
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * Linux `AF_UNIX` `SOCK_STREAM` framed [DesktopIpcConnection] (`linuxX64` / `linuxArm64`).
 */
internal class LinuxUnixSocketConnection(
    private val fd: Int
) : DesktopIpcConnection {

    private val closeOnce = AtomicBoolean(fd < 0)
    private val sendLock = AtomicBoolean(false)

    @Volatile
    private var closed: Boolean = fd < 0

    override val isClosed: Boolean
        get() = closed || fd < 0

    override fun sendBody(bodyBytes: ByteArray): Boolean {
        if (isClosed) return false
        if (bodyBytes.size > ServiceWorkerFrameCodec.MAX_FRAME_BYTES) return false
        val framed = ByteArray(4 + bodyBytes.size)
        ServiceWorkerFrameCodec.writeIntBE(framed, 0, bodyBytes.size)
        bodyBytes.copyInto(framed, destinationOffset = 4)
        return withSendLock {
            if (isClosed) false else writeExact(framed)
        }
    }

    override fun receiveBody(timeoutMs: Int): ByteArray? {
        if (isClosed) return null
        if (timeoutMs > 0 && !waitReadable(timeoutMs)) {
            return null
        }
        val header = readExact(4) ?: return null
        val len = ServiceWorkerFrameCodec.readIntBE(header, 0)
        if (len <= 0 || len > ServiceWorkerFrameCodec.MAX_FRAME_BYTES) {
            close()
            return null
        }
        return readExact(len)
    }

    override fun close() {
        if (!closeOnce.compareAndSet(expectedValue = false, newValue = true)) {
            return
        }
        closed = true
        if (fd >= 0) {
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

    private fun readExact(count: Int): ByteArray? {
        val out = ByteArray(count)
        var offset = 0
        out.usePinned { pinned ->
            val basePtr = pinned.addressOf(0).reinterpret<ByteVar>()
            while (offset < count) {
                val n = recv(fd, basePtr + offset, (count - offset).convert(), 0).toInt()
                if (n < 0 && posix_errno() == EINTR) {
                    continue
                }
                if (n <= 0) {
                    close()
                    return null
                }
                offset += n
            }
        }
        return out
    }

    private fun writeExact(bytes: ByteArray): Boolean {
        var offset = 0
        bytes.usePinned { pinned ->
            val basePtr = pinned.addressOf(0).reinterpret<ByteVar>()
            while (offset < bytes.size) {
                val n = send(fd, basePtr + offset, (bytes.size - offset).convert(), MSG_NOSIGNAL).toInt()
                if (n < 0 && posix_errno() == EINTR) {
                    continue
                }
                if (n <= 0) {
                    close()
                    return false
                }
                offset += n
            }
        }
        return true
    }

    private inline fun <T> withSendLock(block: () -> T): T {
        while (!sendLock.compareAndSet(expectedValue = false, newValue = true)) {
            usleep(50u)
        }
        return try {
            block()
        } finally {
            sendLock.store(false)
        }
    }
}
