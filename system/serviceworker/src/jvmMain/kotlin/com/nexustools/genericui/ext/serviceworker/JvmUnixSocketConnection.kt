package com.nexustools.genericui.ext.serviceworker

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.ClosedChannelException
import java.nio.channels.ClosedSelectorException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import kotlin.concurrent.Volatile

/**
 * Framed full-duplex JEP 380 `AF_UNIX` [DesktopIpcConnection] using dedicated non-blocking
 * read and write [Selector]s. Never calls `channel.socket()`, which throws on UNIX channels.
 */
internal class JvmUnixSocketConnection(
    private val channel: SocketChannel
) : DesktopIpcConnection {

    private companion object {
        const val FRAME_COMPLETION_TIMEOUT_MS: Long = 5_000L
        const val WRITE_TIMEOUT_MS: Long = 10_000L
    }

    private val writeLock = Any()
    private val readLock = Any()
    private val readSelector: Selector = Selector.open()
    private val writeSelector: Selector = Selector.open()

    @Volatile
    private var closed: Boolean = !channel.isOpen

    init {
        channel.configureBlocking(false)
        channel.register(readSelector, SelectionKey.OP_READ)
        channel.register(writeSelector, SelectionKey.OP_WRITE)
    }

    override val isClosed: Boolean
        get() = closed || !channel.isOpen

    override fun sendBody(bodyBytes: ByteArray): Boolean {
        if (isClosed) return false
        return synchronized(writeLock) {
            if (isClosed) return@synchronized false
            try {
                val framed = ByteArray(4 + bodyBytes.size)
                ServiceWorkerFrameCodec.writeIntBE(framed, 0, bodyBytes.size)
                bodyBytes.copyInto(framed, destinationOffset = 4)
                val buf = ByteBuffer.wrap(framed)
                val deadlineNanos = System.nanoTime() + WRITE_TIMEOUT_MS * 1_000_000L
                while (buf.hasRemaining()) {
                    if (isClosed) return@synchronized false
                    val written = channel.write(buf)
                    if (written == 0) {
                        val remNanos = deadlineNanos - System.nanoTime()
                        if (remNanos <= 0L) {
                            close()
                            return@synchronized false
                        }
                        val waitMs = ((remNanos + 999_999L) / 1_000_000L).coerceAtLeast(1L)
                        writeSelector.select(waitMs)
                        writeSelector.selectedKeys().clear()
                    }
                }
                true
            } catch (_: ClosedSelectorException) {
                close()
                false
            } catch (_: ClosedChannelException) {
                close()
                false
            } catch (_: AsynchronousCloseException) {
                close()
                false
            } catch (_: IOException) {
                close()
                false
            }
        }
    }

    override fun receiveBody(timeoutMs: Int): ByteArray? {
        if (isClosed) return null
        return synchronized(readLock) {
            if (isClosed) return@synchronized null
            try {
                val headerBytes = ByteArray(4)
                val headerBuf = ByteBuffer.wrap(headerBytes)

                if (!awaitFirstByte(headerBuf, timeoutMs)) {
                    return@synchronized null
                }

                val frameDeadlineNanos = if (timeoutMs > 0) {
                    System.nanoTime() + maxOf(timeoutMs.toLong(), FRAME_COMPLETION_TIMEOUT_MS) * 1_000_000L
                } else {
                    null
                }

                if (!readFully(headerBuf, frameDeadlineNanos)) {
                    return@synchronized null
                }

                val len = ServiceWorkerFrameCodec.readIntBE(headerBytes, 0)
                if (len <= 0 || len > ServiceWorkerFrameCodec.MAX_FRAME_BYTES) {
                    close()
                    return@synchronized null
                }

                val bodyBytes = ByteArray(len)
                val bodyBuf = ByteBuffer.wrap(bodyBytes)
                if (!readFully(bodyBuf, frameDeadlineNanos)) {
                    return@synchronized null
                }
                bodyBytes
            } catch (_: ClosedSelectorException) {
                close()
                null
            } catch (_: ClosedChannelException) {
                close()
                null
            } catch (_: AsynchronousCloseException) {
                close()
                null
            } catch (_: IOException) {
                close()
                null
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        readSelector.wakeup()
        writeSelector.wakeup()
        try {
            channel.close()
        } catch (_: IOException) {
            // Closing UNIX socket channel during teardown.
        }
        try {
            readSelector.close()
        } catch (_: IOException) {
            // Closing read selector during teardown.
        }
        try {
            writeSelector.close()
        } catch (_: IOException) {
            // Closing write selector during teardown.
        }
    }

    private fun awaitFirstByte(headerBuf: ByteBuffer, timeoutMs: Int): Boolean {
        val deadlineNanos = if (timeoutMs > 0) {
            System.nanoTime() + timeoutMs.toLong() * 1_000_000L
        } else {
            null
        }
        while (headerBuf.position() == 0) {
            if (isClosed) return false
            val read = channel.read(headerBuf)
            if (read < 0) {
                close()
                return false
            }
            if (read > 0) {
                return true
            }
            if (deadlineNanos == null) {
                readSelector.select()
            } else {
                val remNanos = deadlineNanos - System.nanoTime()
                if (remNanos <= 0L) {
                    return false
                }
                val waitMs = ((remNanos + 999_999L) / 1_000_000L).coerceAtLeast(1L)
                readSelector.select(waitMs)
            }
            readSelector.selectedKeys().clear()
        }
        return true
    }

    private fun readFully(buf: ByteBuffer, deadlineNanos: Long?): Boolean {
        while (buf.hasRemaining()) {
            if (isClosed) return false
            val read = channel.read(buf)
            if (read < 0) {
                close()
                return false
            }
            if (read == 0) {
                if (deadlineNanos == null) {
                    readSelector.select()
                } else {
                    val remNanos = deadlineNanos - System.nanoTime()
                    if (remNanos <= 0L) {
                        close()
                        return false
                    }
                    val waitMs = ((remNanos + 999_999L) / 1_000_000L).coerceAtLeast(1L)
                    readSelector.select(waitMs)
                }
                readSelector.selectedKeys().clear()
            }
        }
        return true
    }
}
