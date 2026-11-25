@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.AF_UNIX
import platform.posix.EAGAIN
import platform.posix.EINPROGRESS
import platform.posix.EINTR
import platform.posix.EWOULDBLOCK
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.O_NONBLOCK
import platform.posix.POLLOUT
import platform.posix.SOCK_CLOEXEC
import platform.posix.SOCK_NONBLOCK
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_ERROR
import platform.posix.close
import platform.posix.connect
import platform.posix.fcntl
import platform.posix.getsockopt
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.posix_errno
import platform.posix.sockaddr
import platform.posix.socket
import platform.posix.socklen_tVar

/**
 * Linux `AF_UNIX` `SOCK_STREAM` [DesktopIpcClient] using non-blocking connect with `poll(POLLOUT)`
 * and server `SO_PEERCRED` verification.
 */
internal actual object DesktopIpcClient {

    actual fun connect(
        transportKind: String,
        address: String,
        timeoutMs: Int
    ): DesktopIpcConnection? {
        if (transportKind != DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET || address.isBlank()) {
            return null
        }
        LinuxUnixSocketAddressHelper.ensureSigPipeIgnored()

        val sunBytes = LinuxUnixSocketAddressHelper.buildSockaddrUn(address) ?: return null
        val addrLen = LinuxUnixSocketAddressHelper.sockaddrUnLength(address)

        val fd = socket(AF_UNIX, SOCK_STREAM or SOCK_NONBLOCK or SOCK_CLOEXEC, 0)
        if (fd < 0) return null
        LinuxUnixSocketAddressHelper.setCloseOnExec(fd)

        val connectRc = sunBytes.usePinned { pinned ->
            connect(fd, pinned.addressOf(0).reinterpret<sockaddr>(), addrLen.convert())
        }
        if (connectRc != 0) {
            val err = posix_errno()
            if (err != EINPROGRESS && err != EAGAIN && err != EWOULDBLOCK) {
                close(fd)
                return null
            }
            val effectiveTimeout = timeoutMs.coerceAtLeast(1)
            val ready = memScoped {
                val pfd = alloc<pollfd>()
                pfd.fd = fd
                pfd.events = POLLOUT.toShort()
                pfd.revents = 0
                var pollRc: Int
                do {
                    pollRc = poll(pfd.ptr, 1.convert(), effectiveTimeout)
                } while (pollRc < 0 && posix_errno() == EINTR)
                if (pollRc <= 0) {
                    return@memScoped false
                }
                val soErr = alloc<IntVar>()
                val soLen = alloc<socklen_tVar>().apply { value = sizeOf<IntVar>().convert() }
                getsockopt(fd, SOL_SOCKET, SO_ERROR, soErr.ptr, soLen.ptr) == 0 && soErr.value == 0
            }
            if (!ready) {
                close(fd)
                return null
            }
        }

        val flags = fcntl(fd, F_GETFL, 0)
        if (flags >= 0) {
            fcntl(fd, F_SETFL, flags and O_NONBLOCK.inv())
        }
        if (!LinuxUnixSocketAddressHelper.verifyPeerCredentials(fd)) {
            close(fd)
            return null
        }
        return LinuxUnixSocketConnection(fd)
    }
}
