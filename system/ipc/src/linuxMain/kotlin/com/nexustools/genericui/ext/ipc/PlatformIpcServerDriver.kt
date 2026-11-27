@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ext.ipc

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
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
import platform.posix.pipe
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.posix_errno
import platform.posix.shutdown
import platform.posix.sockaddr
import platform.posix.socket
import platform.posix.unlink
import platform.posix.usleep
import platform.posix.write
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * Linux kernel-native `AF_UNIX` `SOCK_STREAM` [PlatformIpcServerDriver] verifying peer UID via
 * `SO_PEERCRED` and enforcing `0700` directory and `0600` socket permissions.
 */
internal actual class PlatformIpcServerDriver actual constructor() {

    private var listenFd: Int = -1
    private var wakeupReadFd: Int = -1
    private var wakeupWriteFd: Int = -1
    private var acceptJob: Job? = null
    private var acceptScope: CoroutineScope? = null
    private var clientScope: CoroutineScope? = null

    @Volatile
    private var stopped: Boolean = true
    private val acceptFinished = AtomicBoolean(true)

    @Volatile
    private var boundAddress: IpcChannelAddress? = null

    actual val address: IpcChannelAddress?
        get() = boundAddress

    actual val isRunning: Boolean
        get() = !stopped && boundAddress != null && listenFd >= 0

    actual fun start(
        channelName: String,
        runtimeDir: String,
        config: IpcConfig,
        onClientConnected: (IpcConnection) -> Unit
    ): Boolean {
        if (!stopped || listenFd >= 0) {
            close()
        }
        LinuxUnixSocketAddressHelper.ensureSigPipeIgnored()

        val resolved = IpcChannelAddress.resolveDesktopEndpoint(
            channelName = channelName,
            runtimeDir = runtimeDir,
            osHint = "linux"
        )
        val socketPath = resolved.endpointAddress
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

        val wakePair = memScoped {
            val fds = allocArray<IntVar>(2)
            if (pipe(fds) != 0) null else (fds[0] to fds[1])
        }
        if (wakePair == null) {
            close(fd)
            unlink(socketPath)
            return false
        }
        LinuxUnixSocketAddressHelper.setCloseOnExec(wakePair.first)
        LinuxUnixSocketAddressHelper.setCloseOnExec(wakePair.second)
        LinuxUnixSocketAddressHelper.setNonBlocking(wakePair.first)
        LinuxUnixSocketAddressHelper.setNonBlocking(wakePair.second)

        listenFd = fd
        wakeupReadFd = wakePair.first
        wakeupWriteFd = wakePair.second
        boundAddress = resolved
        stopped = false
        acceptFinished.store(false)

        val aScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val cScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        acceptScope = aScope
        clientScope = cScope
        acceptJob = aScope.launch {
            try {
                while (!stopped && listenFd == fd) {
                    val ready = memScoped {
                        val pfds = allocArray<pollfd>(2)
                        pfds[0].fd = fd
                        pfds[0].events = POLLIN.toShort()
                        pfds[0].revents = 0
                        pfds[1].fd = wakePair.first
                        pfds[1].events = POLLIN.toShort()
                        pfds[1].revents = 0
                        val rc = poll(pfds, 2.convert(), 200)
                        rc > 0 && (pfds[0].revents.toInt() and POLLIN) != 0
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
                    val conn = LinuxUnixSocketConnection(clientFd, config.maxPacketBytes)
                    cScope.launch { onClientConnected(conn) }
                }
            } finally {
                acceptFinished.store(true)
            }
        }
        return true
    }

    actual fun close() {
        stopped = true
        val wWrite = wakeupWriteFd
        val wRead = wakeupReadFd
        val fd = listenFd
        val path = boundAddress?.endpointAddress
        listenFd = -1
        wakeupWriteFd = -1
        wakeupReadFd = -1
        boundAddress = null

        if (wWrite >= 0) {
            memScoped {
                val signalByte = alloc<ByteVar>()
                write(wWrite, signalByte.ptr, 1.convert())
            }
        }
        if (fd >= 0) {
            shutdown(fd, SHUT_RDWR)
            close(fd)
        }
        var waitedUs = 0
        while (!acceptFinished.load() && waitedUs < 2_000_000) {
            usleep(1_000u)
            waitedUs += 1_000
        }
        if (wRead >= 0) close(wRead)
        if (wWrite >= 0) close(wWrite)
        if (!path.isNullOrEmpty()) {
            unlink(path)
        }
        acceptJob?.cancel()
        acceptJob = null
        acceptScope?.cancel()
        acceptScope = null
        clientScope?.cancel()
        clientScope = null
    }
}
