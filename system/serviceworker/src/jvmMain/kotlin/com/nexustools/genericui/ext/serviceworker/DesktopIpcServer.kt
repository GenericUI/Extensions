package com.nexustools.genericui.ext.serviceworker

import java.io.IOException
import java.nio.channels.ClosedChannelException
import java.nio.channels.ClosedSelectorException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.Volatile
import kotlin.concurrent.thread

/**
 * JVM JEP 380 `AF_UNIX` [DesktopIpcServer] listening on a user-private Unix domain socket.
 */
internal actual class DesktopIpcServer actual constructor() {

    private var serverChannel: ServerSocketChannel? = null
    private var acceptSelector: Selector? = null
    private var acceptThread: Thread? = null
    private var socketPath: Path? = null

    @Volatile
    private var stopped: Boolean = false

    @Volatile
    private var boundAddress: String = ""

    actual val transportKind: String
        get() = DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET

    actual val endpointAddress: String
        get() = boundAddress

    actual fun start(
        appId: String,
        runtimeDir: String,
        onClientConnected: (DesktopIpcConnection) -> Unit
    ): Boolean {
        if (serverChannel != null) {
            close()
        }
        return try {
            stopped = false
            val resolvedPath = JvmUnixDomainSocketBridge.resolveSocketPath(appId, runtimeDir)
            val parent = resolvedPath.parent
            if (parent != null) {
                Files.createDirectories(parent)
                JvmUnixDomainSocketBridge.applyOwnerOnlyDirPermissions(parent)
            }
            if (Files.exists(resolvedPath)) {
                if (JvmUnixDomainSocketBridge.isSocketAcceptingConnections(resolvedPath)) {
                    return false
                }
                Files.deleteIfExists(resolvedPath)
            }

            val server = JvmUnixDomainSocketBridge.openServerSocketChannel()
            socketPath = resolvedPath
            serverChannel = server

            server.bind(JvmUnixDomainSocketBridge.unixSocketAddressOf(resolvedPath), 32)
            JvmUnixDomainSocketBridge.applyOwnerOnlyFilePermissions(resolvedPath)
            server.configureBlocking(false)

            val selector = Selector.open()
            acceptSelector = selector
            server.register(selector, SelectionKey.OP_ACCEPT)
            boundAddress = resolvedPath.toAbsolutePath().toString()

            acceptThread = thread(isDaemon = true, name = "GenericUI-SW-Accept") {
                while (!stopped && server.isOpen && selector.isOpen) {
                    try {
                        selector.select(200L)
                        val keys = selector.selectedKeys()
                        val iter = keys.iterator()
                        while (iter.hasNext()) {
                            val key = iter.next()
                            iter.remove()
                            if (!key.isValid || !key.isAcceptable) continue
                            while (!stopped) {
                                val client = server.accept() ?: break
                                val conn = try {
                                    JvmUnixSocketConnection(client)
                                } catch (_: IOException) {
                                    try {
                                        client.close()
                                    } catch (_: IOException) {
                                        // Closing aborted client channel.
                                    }
                                    continue
                                }
                                onClientConnected(conn)
                            }
                        }
                    } catch (_: ClosedSelectorException) {
                        break
                    } catch (_: ClosedChannelException) {
                        break
                    } catch (_: IOException) {
                        if (stopped || !server.isOpen) {
                            break
                        }
                    }
                }
            }
            true
        } catch (e: UnsupportedOperationException) {
            close()
            throw e
        } catch (_: IOException) {
            close()
            false
        } catch (_: SecurityException) {
            close()
            false
        }
    }

    actual fun close() {
        stopped = true
        acceptSelector?.wakeup()
        try {
            serverChannel?.close()
        } catch (_: IOException) {
            // Closing listening UNIX server channel during shutdown.
        }
        try {
            acceptSelector?.close()
        } catch (_: IOException) {
            // Closing accept selector during shutdown.
        }
        val path = socketPath
        if (path != null) {
            try {
                Files.deleteIfExists(path)
            } catch (_: IOException) {
                // Deleting UNIX socket file during shutdown.
            }
        }
        serverChannel = null
        acceptSelector = null
        socketPath = null
        acceptThread = null
        boundAddress = ""
    }
}
