@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.posix.AF_INET
import platform.posix.AI_PASSIVE
import platform.posix.NI_NUMERICHOST
import platform.posix.NI_NUMERICSERV
import platform.posix.POLLIN
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_REUSEADDR
import platform.posix.accept
import platform.posix.addrinfo
import platform.posix.bind
import platform.posix.close
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.getnameinfo
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.memset
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.setsockopt
import platform.posix.sockaddr
import platform.posix.sockaddr_storage
import platform.posix.socket
import platform.posix.socklen_tVar
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
            val hints = alloc<addrinfo>()
            memset(hints.ptr, 0, sizeOf<addrinfo>().convert())
            hints.ai_family = AF_INET
            hints.ai_socktype = SOCK_STREAM
            hints.ai_flags = AI_PASSIVE

            val resPtr = alloc<CPointerVar<addrinfo>>()
            if (getaddrinfo("127.0.0.1", "0", hints.ptr, resPtr.ptr) != 0) {
                return@memScoped false
            }
            val head = resPtr.value ?: return@memScoped false
            try {
                val info = head.pointed
                val fd = socket(info.ai_family, info.ai_socktype, info.ai_protocol)
                if (fd < 0) return@memScoped false

                val reuse = alloc<IntVar>().apply { value = 1 }
                setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, reuse.ptr, sizeOf<IntVar>().convert())

                if (bind(fd, info.ai_addr, info.ai_addrlen) != 0 || listen(fd, 32) != 0) {
                    close(fd)
                    return@memScoped false
                }

                val sa = alloc<sockaddr_storage>()
                val saLen = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_storage>().convert() }
                if (getsockname(fd, sa.ptr.reinterpret<sockaddr>(), saLen.ptr) != 0) {
                    close(fd)
                    return@memScoped false
                }

                val hostBuf = allocArray<ByteVar>(64)
                val servBuf = allocArray<ByteVar>(32)
                if (getnameinfo(
                        sa.ptr.reinterpret<sockaddr>(),
                        saLen.value,
                        hostBuf,
                        64u.convert(),
                        servBuf,
                        32u.convert(),
                        NI_NUMERICHOST or NI_NUMERICSERV
                    ) != 0
                ) {
                    close(fd)
                    return@memScoped false
                }

                val parsedPort = servBuf.toKString().toIntOrNull() ?: 0
                if (parsedPort <= 0) {
                    close(fd)
                    return@memScoped false
                }

                boundPort = parsedPort
                listenFd = fd
                stopped = false

                acceptJob = acceptScope.launch {
                    while (!stopped && listenFd >= 0) {
                        val ready = memScoped {
                            val pfd = alloc<pollfd>()
                            pfd.fd = fd
                            pfd.events = POLLIN.toShort()
                            pfd.revents = 0
                            poll(pfd.ptr, 1.convert(), 200) > 0
                        }
                        if (!ready || stopped) continue
                        val clientFd = accept(fd, null, null)
                        if (clientFd >= 0) {
                            onClientConnected(LinuxTcpConnection(clientFd))
                        }
                    }
                }
                true
            } finally {
                freeaddrinfo(head)
            }
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
