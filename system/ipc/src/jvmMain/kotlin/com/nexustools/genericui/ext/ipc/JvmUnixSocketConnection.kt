package com.nexustools.genericui.ext.ipc

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
 * Framed full-duplex JEP 380 `AF_UNIX` [IpcConnection] using dedicated non-blocking
 * read and write [Selector]s.
 */
internal class JvmUnixSocketConnection(
    private val channel: SocketChannel,
    maxPacketBytes: Int = IpcPacketFramer.MAX_PACKET_BYTES
) : BaseFramedIpcConnection(maxPacketBytes = maxPacketBytes) {

    private companion object {
        const val WRITE_TIMEOUT_MS: Long = 15_000L
    }

    private val readSelector: Selector = Selector.open()
    private val writeSelector: Selector = Selector.open()

    @Volatile
    var lastTeardownError: Throwable? = null
        private set

    init {
        channel.configureBlocking(false)
        channel.register(readSelector, SelectionKey.OP_READ)
        channel.register(writeSelector, SelectionKey.OP_WRITE)
    }

    override fun writeFrameBytes(framedBytes: ByteArray): Boolean {
        return try {
            val buf = ByteBuffer.wrap(framedBytes)
            val deadlineNanos = System.nanoTime() + WRITE_TIMEOUT_MS * 1_000_000L
            while (buf.hasRemaining()) {
                if (isClosed) return false
                val written = channel.write(buf)
                if (written == 0) {
                    val remNanos = deadlineNanos - System.nanoTime()
                    if (remNanos <= 0L) {
                        close()
                        return false
                    }
                    val waitMs = ((remNanos + 999_999L) / 1_000_000L).coerceAtLeast(1L)
                    writeSelector.select(waitMs)
                    writeSelector.selectedKeys().clear()
                }
            }
            true
        } catch (e: ClosedSelectorException) {
            recordErrorAndClose(e)
            false
        } catch (e: ClosedChannelException) {
            recordErrorAndClose(e)
            false
        } catch (e: AsynchronousCloseException) {
            recordErrorAndClose(e)
            false
        } catch (e: IOException) {
            recordErrorAndClose(e)
            false
        }
    }

    override fun readFrameBytes(
        count: Int,
        initialTimeoutMs: Int,
        isHeader: Boolean
    ): ByteArray? {
        return try {
            val out = ByteArray(count)
            val buf = ByteBuffer.wrap(out)

            if (isHeader) {
                if (!awaitFirstByte(buf, initialTimeoutMs)) {
                    return null
                }
                if (!buf.hasRemaining()) {
                    return out
                }
                val completionTimeoutMs = computeFrameCompletionTimeoutMs(initialTimeoutMs)
                val completionDeadline = if (completionTimeoutMs > 0) {
                    System.nanoTime() + completionTimeoutMs.toLong() * 1_000_000L
                } else {
                    null
                }
                if (!readRemaining(buf, completionDeadline)) {
                    return null
                }
                return out
            }

            val deadlineNanos = if (initialTimeoutMs > 0) {
                System.nanoTime() + initialTimeoutMs.toLong() * 1_000_000L
            } else {
                null
            }
            if (!readRemaining(buf, deadlineNanos)) {
                return null
            }
            out
        } catch (e: ClosedSelectorException) {
            recordErrorAndClose(e)
            null
        } catch (e: ClosedChannelException) {
            recordErrorAndClose(e)
            null
        } catch (e: AsynchronousCloseException) {
            recordErrorAndClose(e)
            null
        } catch (e: IOException) {
            recordErrorAndClose(e)
            null
        }
    }

    override fun closeTransport() {
        readSelector.wakeup()
        writeSelector.wakeup()
        if (channel.isOpen) {
            try {
                channel.close()
            } catch (e: IOException) {
                lastTeardownError = lastTeardownError ?: e
            }
        }
        if (readSelector.isOpen) {
            try {
                readSelector.close()
            } catch (e: IOException) {
                lastTeardownError = lastTeardownError ?: e
            }
        }
        if (writeSelector.isOpen) {
            try {
                writeSelector.close()
            } catch (e: IOException) {
                lastTeardownError = lastTeardownError ?: e
            }
        }
    }

    private fun awaitFirstByte(buf: ByteBuffer, timeoutMs: Int): Boolean {
        if (timeoutMs == 0) {
            if (isClosed) return false
            val read = channel.read(buf)
            if (read < 0) {
                close()
                return false
            }
            if (read > 0) return true
            readSelector.selectNow()
            readSelector.selectedKeys().clear()
            if (isClosed) return false
            val retryRead = channel.read(buf)
            if (retryRead < 0) {
                close()
                return false
            }
            return retryRead > 0
        }

        val deadlineNanos = if (timeoutMs > 0) {
            System.nanoTime() + timeoutMs.toLong() * 1_000_000L
        } else {
            null
        }
        while (buf.position() == 0) {
            if (isClosed) return false
            val read = channel.read(buf)
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

    private fun readRemaining(buf: ByteBuffer, deadlineNanos: Long?): Boolean {
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

    private fun recordErrorAndClose(error: Throwable) {
        lastTeardownError = lastTeardownError ?: error
        close()
    }
}
