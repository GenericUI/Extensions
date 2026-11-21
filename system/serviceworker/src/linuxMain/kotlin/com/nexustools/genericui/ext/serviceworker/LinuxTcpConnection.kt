@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

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
import platform.posix.POLLIN
import platform.posix.close
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.recv
import platform.posix.send
import kotlin.concurrent.Volatile

/**
 * Linux POSIX framed loopback TCP socket connection (`linuxX64` / `linuxArm64`).
 */
internal class LinuxTcpConnection(
    private val fd: Int
) : DesktopTcpConnection {

    @Volatile
    private var closed: Boolean = fd < 0

    override val isClosed: Boolean
        get() = closed || fd < 0

    override fun sendBody(bodyBytes: ByteArray): Boolean {
        if (isClosed) return false
        val framed = ByteArray(4 + bodyBytes.size)
        ServiceWorkerFrameCodec.writeIntBE(framed, 0, bodyBytes.size)
        bodyBytes.copyInto(framed, destinationOffset = 4)
        return writeExact(framed)
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
        if (closed) return
        closed = true
        if (fd >= 0) {
            close(fd)
        }
    }

    private fun waitReadable(timeoutMs: Int): Boolean = memScoped {
        val pfd = alloc<pollfd>()
        pfd.fd = fd
        pfd.events = POLLIN.toShort()
        pfd.revents = 0
        poll(pfd.ptr, 1.convert(), timeoutMs) > 0
    }

    private fun readExact(count: Int): ByteArray? {
        val out = ByteArray(count)
        var offset = 0
        out.usePinned { pinned ->
            val basePtr = pinned.addressOf(0).reinterpret<ByteVar>()
            while (offset < count) {
                val n = recv(fd, basePtr + offset, (count - offset).convert(), 0).toInt()
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
                val n = send(fd, basePtr + offset, (bytes.size - offset).convert(), 0).toInt()
                if (n <= 0) {
                    close()
                    return false
                }
                offset += n
            }
        }
        return true
    }
}
