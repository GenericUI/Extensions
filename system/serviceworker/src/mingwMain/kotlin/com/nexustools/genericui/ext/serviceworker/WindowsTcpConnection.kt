@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import platform.posix.INVALID_SOCKET
import platform.posix.closesocket
import platform.posix.fd_set
import platform.posix.posix_FD_ZERO
import platform.posix.recv
import platform.posix.send
import platform.posix.timeval
import platform.windows.select
import kotlin.concurrent.Volatile

/**
 * WinSock2 framed loopback TCP socket connection (`mingwX64`).
 */
internal class WindowsTcpConnection(
    private val socketHandle: ULong
) : DesktopTcpConnection {

    @Volatile
    private var closed: Boolean = socketHandle == INVALID_SOCKET

    override val isClosed: Boolean
        get() = closed || socketHandle == INVALID_SOCKET

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
        if (socketHandle != INVALID_SOCKET) {
            closesocket(socketHandle)
        }
    }

    private fun waitReadable(timeoutMs: Int): Boolean = memScoped {
        val readSet = alloc<fd_set>()
        posix_FD_ZERO(readSet.ptr)
        readSet.fd_count = 1u
        readSet.fd_array[0] = socketHandle
        val tv = alloc<timeval>()
        tv.tv_sec = (timeoutMs / 1000)
        tv.tv_usec = ((timeoutMs % 1000) * 1000)
        val sel = select(0, readSet.ptr, null, null, tv.ptr)
        sel > 0
    }

    private fun readExact(count: Int): ByteArray? {
        val out = ByteArray(count)
        var offset = 0
        out.usePinned { pinned ->
            val basePtr = pinned.addressOf(0).reinterpret<ByteVar>()
            while (offset < count) {
                val n = recv(socketHandle, basePtr + offset, count - offset, 0)
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
                val n = send(socketHandle, basePtr + offset, bytes.size - offset, 0)
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
