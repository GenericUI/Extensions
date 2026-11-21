package com.nexustools.genericui.ext.serviceworker

/**
 * Platform-specific loopback TCP client connecting strictly to `127.0.0.1`.
 */
internal expect object DesktopLoopbackTcpClient {
    fun connect(port: Int, timeoutMs: Int = 5_000): DesktopTcpConnection?
}
