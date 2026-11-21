@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import platform.posix.AF_INET
import platform.posix.INVALID_SOCKET
import platform.posix.IPPROTO_TCP
import platform.posix.SOCK_STREAM
import platform.posix.WSAData
import platform.posix.WSAStartup
import platform.posix.closesocket
import platform.posix.connect
import platform.posix.inet_addr
import platform.posix.memset
import platform.posix.posix_htons
import platform.posix.sockaddr_in
import platform.posix.socket
import kotlin.concurrent.Volatile

internal actual object DesktopLoopbackTcpClient {

    @Volatile
    private var wsaStarted: Boolean = false

    actual fun connect(port: Int, timeoutMs: Int): DesktopTcpConnection? {
        if (port <= 0) return null
        ensureWinsockInitialized()
        return memScoped {
            val sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
            if (sock == INVALID_SOCKET) return@memScoped null

            val addr = alloc<sockaddr_in>()
            memset(addr.ptr, 0, sizeOf<sockaddr_in>().toULong())
            addr.sin_family = AF_INET.toShort()
            addr.sin_port = posix_htons(port.toShort()).toUShort()
            addr.sin_addr.S_un.S_addr = inet_addr("127.0.0.1")

            if (connect(sock, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().toInt()) != 0) {
                closesocket(sock)
                return@memScoped null
            }
            WindowsTcpConnection(sock)
        }
    }

    private fun ensureWinsockInitialized() {
        if (wsaStarted) return
        memScoped {
            val wsa = alloc<WSAData>()
            if (WSAStartup(0x0202u, wsa.ptr) == 0) {
                wsaStarted = true
            }
        }
    }
}
