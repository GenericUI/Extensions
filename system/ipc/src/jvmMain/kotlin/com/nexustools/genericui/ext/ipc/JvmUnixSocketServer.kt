package com.nexustools.genericui.ext.ipc

import java.io.IOException
import java.nio.channels.ClosedChannelException
import java.nio.channels.ClosedSelectorException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.Volatile
import kotlin.concurrent.thread

/**
 * JVM JEP 380 `AF_UNIX` server listening on a user-private Unix domain socket.
 */
internal class JvmUnixSocketServer {

    private var serverChannel: ServerSocketChannel? = null
    private var acceptSelector: Selector? = null
    private var acceptThread: Thread? = null
    private var acceptDoneLatch: CountDownLatch? = null
    private var clientExecutor: ExecutorService? = null
    private var socketPath: Path? = null

    @Volatile
    private var stopped: Boolean = true

    @Volatile
    private var boundAddress: IpcChannelAddress? = null

    @Volatile
    var lastError: Throwable? = null
        private set

    val address: IpcChannelAddress?
        get() = boundAddress

    val isRunning: Boolean
        get() = !stopped && boundAddress != null && serverChannel?.isOpen == true

    fun start(
        endpointPath: String,
        config: IpcConfig,
        onClientConnected: (IpcConnection) -> Unit
    ): Boolean {
        if (!stopped || serverChannel != null) {
            close()
        }
        return try {
            val resolvedPath = Paths.get(endpointPath).toAbsolutePath()
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

            val threadCounter = AtomicInteger(1)
            val executor = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "GenericUI-IPC-Unix-Client-${threadCounter.getAndIncrement()}").apply {
                    isDaemon = true
                }
            }
            clientExecutor = executor

            val latch = CountDownLatch(1)
            acceptDoneLatch = latch
            stopped = false
            boundAddress = IpcChannelAddress(
                transportKind = IpcChannelAddress.TRANSPORT_UNIX_SOCKET,
                endpointAddress = resolvedPath.toString()
            )

            acceptThread = thread(isDaemon = true, name = "GenericUI-IPC-Unix-Accept") {
                try {
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
                                        JvmUnixSocketConnection(client, config.maxPacketBytes)
                                    } catch (e: IOException) {
                                        lastError = lastError ?: e
                                        if (client.isOpen) {
                                            try {
                                                client.close()
                                            } catch (closeErr: IOException) {
                                                lastError = lastError ?: closeErr
                                            }
                                        }
                                        continue
                                    }
                                    executor.execute {
                                        onClientConnected(conn)
                                    }
                                }
                            }
                        } catch (_: ClosedSelectorException) {
                            break
                        } catch (_: ClosedChannelException) {
                            break
                        } catch (e: IOException) {
                            lastError = lastError ?: e
                            if (stopped || !server.isOpen) {
                                break
                            }
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
            true
        } catch (e: UnsupportedOperationException) {
            close()
            throw e
        } catch (e: IOException) {
            lastError = e
            close()
            false
        } catch (e: SecurityException) {
            lastError = e
            close()
            false
        }
    }

    fun close() {
        stopped = true
        boundAddress = null
        acceptSelector?.wakeup()
        val server = serverChannel
        if (server != null && server.isOpen) {
            try {
                server.close()
            } catch (e: IOException) {
                lastError = lastError ?: e
            }
        }
        val selector = acceptSelector
        if (selector != null && selector.isOpen) {
            try {
                selector.close()
            } catch (e: IOException) {
                lastError = lastError ?: e
            }
        }
        val latch = acceptDoneLatch
        if (latch != null && Thread.currentThread() !== acceptThread) {
            try {
                latch.await(2_000L, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        val path = socketPath
        if (path != null) {
            try {
                Files.deleteIfExists(path)
            } catch (e: IOException) {
                lastError = lastError ?: e
            }
        }
        clientExecutor?.shutdown()
        serverChannel = null
        acceptSelector = null
        socketPath = null
        acceptThread = null
        acceptDoneLatch = null
        clientExecutor = null
    }
}
