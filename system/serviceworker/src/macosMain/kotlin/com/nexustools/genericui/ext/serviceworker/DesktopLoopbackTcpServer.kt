@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
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
import platform.posix.POLLIN
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_REUSEADDR
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.memset
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import kotlin.concurrent.Volatile

internal actual class DesktopLoopbackTcpServer actual constructor() {

    private var listenFd: Int = -1
    private var acceptJob: Job? = null
    private val acceptScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var stopped: Boolean = false

    @Volatile
    private var boundPort: Int = 0

    actual val port: Int
        get() = boundPort

    actual fun start(onClientConnected: (DesktopTcpConnection) -> Unit): Boolean {
        return memScoped {
            val fd = socket(AF_INET, SOCK_STREAM, 0)
            if (fd < 0) return@memScoped false

            val reuse = alloc<IntVar>().apply { value = 1 }
            setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, reuse.ptr, sizeOf<IntVar>().convert())

            val addr = alloc<sockaddr_in>()
            memset(addr.ptr, 0, sizeOf<sockaddr_in>().convert())
            addr.sin_len = sizeOf<sockaddr_in>().toUByte()
            addr.sin_family = AF_INET.toUByte()
            addr.sin_port = 0u
            addr.sin_addr.s_addr = 0x0100007Fu // 127.0.0.1 in little-endian u32

            if (bind(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0 ||
                listen(fd, 32) != 0
            ) {
                close(fd)
                return@memScoped false
            }

            val bound = alloc<sockaddr_in>()
            val boundLen = alloc<UIntVar>().apply { value = sizeOf<sockaddr_in>().convert() }
            if (getsockname(fd, bound.ptr.reinterpret(), boundLen.ptr) != 0) {
                close(fd)
                return@memScoped false
            }

            val rawPort = bound.sin_port.toInt() and 0xFFFF
            boundPort = ((rawPort and 0xFF) shl 8) or ((rawPort ushr 8) and 0xFF)
            listenFd = fd
            stopped = false

            acceptJob = acceptScope.launch {
                while (!stopped && listenFd >= 0) {
                    val ready = memScoped {
                        val pfd = alloc<pollfd>()
                        pfd.fd = fd
                        pfd.events = POLLIN.toShort()
                        pfd.revents = 0
                        poll(pfd.ptr, 1u, 200) > 0
                    }
                    if (!ready || stopped) continue
                    val clientFd = accept(fd, null, null)
                    if (clientFd >= 0) {
                        onClientConnected(MacosTcpConnection(clientFd))
                    }
                }
            }
            true
        }
    }

    actual fun close() {
        stopped = true
        val fd = listenFd
        listenFd = -1
        boundPort = 0
        if (fd >= 0) {
            close(fd)
        }
        acceptJob?.cancel()
        acceptScope.cancel()
    }
}
