@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.posix.AF_UNIX
import platform.posix.EAGAIN
import platform.posix.EINTR
import platform.posix.EWOULDBLOCK
import platform.posix.POLLIN
import platform.posix.SHUT_RDWR
import platform.posix.SOCK_CLOEXEC
import platform.posix.SOCK_STREAM
import platform.posix.S_IRUSR
import platform.posix.S_IWUSR
import platform.posix.accept
import platform.posix.bind
import platform.posix.chmod
import platform.posix.close
import platform.posix.listen
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.posix_errno
import platform.posix.shutdown
import platform.posix.sockaddr
import platform.posix.socket
import platform.posix.unlink
import kotlin.concurrent.Volatile

/**
 * Linux kernel-native `AF_UNIX` `SOCK_STREAM` [DesktopIpcServer] verifying peer UID via
 * `SO_PEERCRED` and hardening permissions to `0700` dir / `0600` socket.
 */
internal actual class DesktopIpcServer actual constructor() {

    private var listenFd: Int = -1
    private var acceptJob: Job? = null
    private var acceptScope: CoroutineScope? = null

    @Volatile
    private var stopped: Boolean = false

    @Volatile
    private var boundTransportKind: String = ""

    @Volatile
    private var boundAddress: String = ""

    actual val transportKind: String
        get() = boundTransportKind

    actual val endpointAddress: String
        get() = boundAddress

    actual fun start(
        appId: String,
        runtimeDir: String,
        onClientConnected: (DesktopIpcConnection) -> Unit
    ): Boolean {
        if (listenFd >= 0) {
            close()
        }
        LinuxUnixSocketAddressHelper.ensureSigPipeIgnored()

        val socketPath = LinuxUnixSocketAddressHelper.resolveSocketPath(appId, runtimeDir)
        if (!LinuxUnixSocketAddressHelper.ensureSecureParentDir(socketPath)) {
            return false
        }
        if (!LinuxUnixSocketAddressHelper.recoverStaleSocketIfNeeded(socketPath)) {
            return false
        }
        val sunBytes = LinuxUnixSocketAddressHelper.buildSockaddrUn(socketPath) ?: return false
        val addrLen = LinuxUnixSocketAddressHelper.sockaddrUnLength(socketPath)

        val fd = socket(AF_UNIX, SOCK_STREAM or SOCK_CLOEXEC, 0)
        if (fd < 0) return false
        LinuxUnixSocketAddressHelper.setCloseOnExec(fd)

        val bound = sunBytes.usePinned { pinned ->
            bind(fd, pinned.addressOf(0).reinterpret<sockaddr>(), addrLen.convert()) == 0
        }
        if (!bound) {
            close(fd)
            return false
        }
        if (chmod(socketPath, (S_IRUSR or S_IWUSR).convert()) != 0 || listen(fd, 32) != 0) {
            close(fd)
            unlink(socketPath)
            return false
        }

        listenFd = fd
        boundTransportKind = DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET
        boundAddress = socketPath
        stopped = false

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        acceptScope = scope
        acceptJob = scope.launch {
            while (!stopped && listenFd == fd) {
                val ready = memScoped {
                    val pfd = alloc<pollfd>()
                    pfd.fd = fd
                    pfd.events = POLLIN.toShort()
                    pfd.revents = 0
                    val rc = poll(pfd.ptr, 1.convert(), 200)
                    rc > 0
                }
                if (!ready || stopped || listenFd != fd) continue
                val clientFd = accept(fd, null, null)
                if (clientFd < 0) {
                    val err = posix_errno()
                    if (err == EINTR || err == EAGAIN || err == EWOULDBLOCK) {
                        continue
                    }
                    if (stopped || listenFd != fd) break
                    continue
                }
                LinuxUnixSocketAddressHelper.setCloseOnExec(clientFd)
                if (!LinuxUnixSocketAddressHelper.verifyPeerCredentials(clientFd)) {
                    close(clientFd)
                    continue
                }
                onClientConnected(LinuxUnixSocketConnection(clientFd))
            }
        }
        return true
    }

    actual fun close() {
        stopped = true
        val fd = listenFd
        val path = boundAddress
        listenFd = -1
        boundTransportKind = ""
        boundAddress = ""
        if (fd >= 0) {
            shutdown(fd, SHUT_RDWR)
            close(fd)
        }
        if (path.isNotEmpty()) {
            unlink(path)
        }
        acceptJob?.cancel()
        acceptJob = null
        acceptScope?.cancel()
        acceptScope = null
    }
}
