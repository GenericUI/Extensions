package com.nexustools.genericui.ext.serviceworker

import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.concurrent.Volatile
import kotlin.concurrent.thread

internal actual class DesktopLoopbackTcpServer actual constructor() {

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    @Volatile
    private var stopped: Boolean = false

    @Volatile
    private var boundPort: Int = 0

    actual val port: Int
        get() = boundPort

    actual fun start(onClientConnected: (DesktopTcpConnection) -> Unit): Boolean {
        return try {
            stopped = false
            val ss = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1")).apply {
                soTimeout = 200
            }
            serverSocket = ss
            boundPort = ss.localPort
            val listenPort = boundPort
            acceptThread = thread(isDaemon = true, name = "GenericUI-SW-Accept-$listenPort") {
                while (!stopped && !ss.isClosed) {
                    try {
                        val client = ss.accept()
                        client.tcpNoDelay = true
                        onClientConnected(JvmTcpConnection(client))
                    } catch (_: SocketTimeoutException) {
                        // Poll loop so shutdown completes promptly
                    } catch (_: Throwable) {
                        if (!stopped) {
                            break
                        }
                    }
                }
            }
            true
        } catch (_: Throwable) {
            close()
            false
        }
    }

    actual fun close() {
        stopped = true
        runCatching { serverSocket?.close() }
        serverSocket = null
        boundPort = 0
        acceptThread = null
    }
}
