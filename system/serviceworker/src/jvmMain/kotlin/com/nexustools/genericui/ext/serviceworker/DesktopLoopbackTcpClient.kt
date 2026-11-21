package com.nexustools.genericui.ext.serviceworker

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

internal actual object DesktopLoopbackTcpClient {
    actual fun connect(port: Int, timeoutMs: Int): DesktopTcpConnection? {
        if (port <= 0) return null
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), timeoutMs.coerceAtLeast(100))
            socket.tcpNoDelay = true
            JvmTcpConnection(socket)
        } catch (_: Throwable) {
            null
        }
    }
}
