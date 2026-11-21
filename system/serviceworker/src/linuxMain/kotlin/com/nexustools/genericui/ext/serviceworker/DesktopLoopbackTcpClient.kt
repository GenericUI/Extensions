@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.addrinfo
import platform.posix.close
import platform.posix.connect
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.memset
import platform.posix.socket

internal actual object DesktopLoopbackTcpClient {
    actual fun connect(port: Int, timeoutMs: Int): DesktopTcpConnection? {
        if (port <= 0) return null
        return memScoped {
            val hints = alloc<addrinfo>()
            memset(hints.ptr, 0, sizeOf<addrinfo>().convert())
            hints.ai_family = AF_INET
            hints.ai_socktype = SOCK_STREAM

            val resPtr = alloc<CPointerVar<addrinfo>>()
            if (getaddrinfo("127.0.0.1", port.toString(), hints.ptr, resPtr.ptr) != 0) {
                return@memScoped null
            }
            val head = resPtr.value ?: return@memScoped null
            try {
                val info = head.pointed
                val fd = socket(info.ai_family, info.ai_socktype, info.ai_protocol)
                if (fd < 0) return@memScoped null
                if (connect(fd, info.ai_addr, info.ai_addrlen) != 0) {
                    close(fd)
                    return@memScoped null
                }
                LinuxTcpConnection(fd)
            } finally {
                freeaddrinfo(head)
            }
        }
    }
}
