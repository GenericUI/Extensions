package com.nexustools.genericui.ext.serviceworker

/**
 * Platform-specific loopback TCP server (`127.0.0.1:0`) accepting local UI client connections.
 */
internal expect class DesktopLoopbackTcpServer() {
    val port: Int

    fun start(onClientConnected: (DesktopTcpConnection) -> Unit): Boolean

    fun close()
}
