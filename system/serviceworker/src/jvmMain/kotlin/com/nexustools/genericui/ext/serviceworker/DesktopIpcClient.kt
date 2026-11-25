package com.nexustools.genericui.ext.serviceworker

import java.io.IOException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.file.Paths

/**
 * JVM JEP 380 `AF_UNIX` [DesktopIpcClient] connecting to a [DesktopIpcServer] Unix domain socket.
 */
internal actual object DesktopIpcClient {

    actual fun connect(
        transportKind: String,
        address: String,
        timeoutMs: Int
    ): DesktopIpcConnection? {
        if (transportKind != DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET || address.isBlank()) {
            return null
        }
        val socketPath = Paths.get(address)
        val channel = JvmUnixDomainSocketBridge.openSocketChannel()
        var conn: JvmUnixSocketConnection? = null
        return try {
            channel.configureBlocking(false)
            val unixAddress = JvmUnixDomainSocketBridge.unixSocketAddressOf(socketPath)
            var connected = channel.connect(unixAddress)
            if (!connected) {
                val selector = Selector.open()
                val key = channel.register(selector, SelectionKey.OP_CONNECT)
                try {
                    val deadlineNanos = System.nanoTime() + timeoutMs.coerceAtLeast(100).toLong() * 1_000_000L
                    while (!connected) {
                        val remNanos = deadlineNanos - System.nanoTime()
                        if (remNanos <= 0L) {
                            return null
                        }
                        val waitMs = ((remNanos + 999_999L) / 1_000_000L).coerceAtLeast(1L)
                        val ready = selector.select(waitMs)
                        selector.selectedKeys().clear()
                        if (ready > 0 && key.isValid && key.isConnectable) {
                            connected = channel.finishConnect()
                        }
                    }
                } finally {
                    key.cancel()
                    try {
                        selector.close()
                    } catch (_: IOException) {
                        // Closing temporary connect selector.
                    }
                }
            }
            conn = JvmUnixSocketConnection(channel)
            conn
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } finally {
            if (conn == null) {
                try {
                    channel.close()
                } catch (_: IOException) {
                    // Closing failed client socket channel.
                }
            }
        }
    }
}
