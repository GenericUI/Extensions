package com.nexustools.genericui.ext.serviceworker

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.concurrent.Volatile

/**
 * JVM `java.net.Socket` framed loopback connection.
 */
internal class JvmTcpConnection(
    private val socket: Socket
) : DesktopTcpConnection {

    private val writeLock = Any()
    private val readLock = Any()
    private val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
    private val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

    @Volatile
    private var closed: Boolean = socket.isClosed

    override val isClosed: Boolean
        get() = closed || socket.isClosed

    override fun sendBody(bodyBytes: ByteArray): Boolean {
        if (isClosed) return false
        return synchronized(writeLock) {
            try {
                output.writeInt(bodyBytes.size)
                output.write(bodyBytes)
                output.flush()
                true
            } catch (_: Throwable) {
                close()
                false
            }
        }
    }

    override fun receiveBody(timeoutMs: Int): ByteArray? {
        if (isClosed) return null
        return synchronized(readLock) {
            try {
                socket.soTimeout = if (timeoutMs > 0) timeoutMs else 0
                val len = input.readInt()
                if (len <= 0 || len > ServiceWorkerFrameCodec.MAX_FRAME_BYTES) {
                    close()
                    return@synchronized null
                }
                val buf = ByteArray(len)
                input.readFully(buf)
                buf
            } catch (_: SocketTimeoutException) {
                null
            } catch (_: EOFException) {
                close()
                null
            } catch (_: Throwable) {
                close()
                null
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { socket.close() }
    }
}
