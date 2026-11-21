@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.posix.AF_INET
import platform.posix.INVALID_SOCKET
import platform.posix.IPPROTO_TCP
import platform.posix.SOCK_STREAM
import platform.posix.WSAData
import platform.posix.WSAStartup
import platform.posix.accept
import platform.posix.bind
import platform.posix.closesocket
import platform.posix.fd_set
import platform.posix.getsockname
import platform.posix.inet_addr
import platform.posix.listen
import platform.posix.memset
import platform.posix.posix_FD_ZERO
import platform.posix.posix_htons
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.timeval
import platform.windows.select
import kotlin.concurrent.Volatile

internal actual class DesktopLoopbackTcpServer actual constructor() {

    private var listenSocket: ULong = INVALID_SOCKET
    private var acceptJob: Job? = null
    private val acceptScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var stopped: Boolean = false

    @Volatile
    private var boundPort: Int = 0

    actual val port: Int
        get() = boundPort

    actual fun start(onClientConnected: (DesktopTcpConnection) -> Unit): Boolean {
        ensureWinsockInitialized()
        return memScoped {
            val sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
            if (sock == INVALID_SOCKET) return@memScoped false

            val addr = alloc<sockaddr_in>()
            memset(addr.ptr, 0, sizeOf<sockaddr_in>().toULong())
            addr.sin_family = AF_INET.toShort()
            addr.sin_port = 0u
            addr.sin_addr.S_un.S_addr = inet_addr("127.0.0.1")

            if (bind(sock, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().toInt()) != 0 ||
                listen(sock, 32) != 0
            ) {
                closesocket(sock)
                return@memScoped false
            }

            val boundAddr = alloc<sockaddr_in>()
            val boundLen = alloc<IntVar>().apply { value = sizeOf<sockaddr_in>().toInt() }
            if (getsockname(sock, boundAddr.ptr.reinterpret(), boundLen.ptr) != 0) {
                closesocket(sock)
                return@memScoped false
            }

            boundPort = posix_htons(boundAddr.sin_port.toShort()).toInt() and 0xFFFF
            listenSocket = sock
            stopped = false

            acceptJob = acceptScope.launch {
                while (!stopped && listenSocket != INVALID_SOCKET) {
                    val ready = memScoped {
                        val readSet = alloc<fd_set>()
                        posix_FD_ZERO(readSet.ptr)
                        readSet.fd_count = 1u
                        readSet.fd_array[0] = sock
                        val tv = alloc<timeval>().apply {
                            tv_sec = 0
                            tv_usec = 200_000
                        }
                        select(0, readSet.ptr, null, null, tv.ptr) > 0
                    }
                    if (!ready || stopped) continue
                    val clientSock = accept(sock, null, null)
                    if (clientSock != INVALID_SOCKET) {
                        onClientConnected(WindowsTcpConnection(clientSock))
                    }
                }
            }
            true
        }
    }

    actual fun close() {
        stopped = true
        val sock = listenSocket
        listenSocket = INVALID_SOCKET
        boundPort = 0
        if (sock != INVALID_SOCKET) {
            closesocket(sock)
        }
        acceptJob?.cancel()
        acceptScope.cancel()
    }

    private companion object {
        @Volatile
        private var wsaStarted: Boolean = false

        fun ensureWinsockInitialized() {
            if (wsaStarted) return
            memScoped {
                val wsa = alloc<WSAData>()
                if (WSAStartup(0x0202u, wsa.ptr) == 0) {
                    wsaStarted = true
                }
            }
        }
    }
}
