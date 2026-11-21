@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.close
import platform.posix.connect
import platform.posix.memset
import platform.posix.sockaddr_in
import platform.posix.socket

internal actual object DesktopLoopbackTcpClient {
    actual fun connect(port: Int, timeoutMs: Int): DesktopTcpConnection? {
        if (port <= 0) return null
        return memScoped {
            val fd = socket(AF_INET, SOCK_STREAM, 0)
            if (fd < 0) return@memScoped null

            val addr = alloc<sockaddr_in>()
            memset(addr.ptr, 0, sizeOf<sockaddr_in>().convert())
            addr.sin_len = sizeOf<sockaddr_in>().toUByte()
            addr.sin_family = AF_INET.toUByte()
            val bePort = ((port and 0xFF) shl 8) or ((port ushr 8) and 0xFF)
            addr.sin_port = bePort.toUShort()
            addr.sin_addr.s_addr = 0x0100007Fu // 127.0.0.1 in little-endian u32

            if (connect(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0) {
                close(fd)
                return@memScoped null
            }
            MacosTcpConnection(fd)
        }
    }
}
