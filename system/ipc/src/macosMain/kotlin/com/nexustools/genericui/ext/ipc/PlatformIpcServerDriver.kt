@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ext.ipc

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.posix.EAGAIN
import platform.posix.EINTR
import platform.posix.EWOULDBLOCK
import platform.posix.POLLIN
import platform.posix.SHUT_RDWR
import platform.posix.accept
import platform.posix.close
import platform.posix.errno
import platform.posix.geteuid
import platform.posix.pipe
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.shutdown
import platform.posix.unlink
import platform.posix.usleep
import platform.posix.write
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * macOS two-tier [PlatformIpcServerDriver]:
 * - Tier 1: `launchd`-registered XPC Mach service listener (`macos-xpc-mach`) when running with
 *   the default Application Support runtime directory and a registered LaunchAgent Mach service.
 * - Tier 2: Owner-only (`0600`) `AF_UNIX` `SOCK_STREAM` socket (`unix-socket`) fallback.
 */
internal actual class PlatformIpcServerDriver actual constructor() {

    private var xpcHandle: XpcListenerHandle? = null
    private var unixListenFd: Int = -1
    private var wakeupReadFd: Int = -1
    private var wakeupWriteFd: Int = -1
    private var acceptJob: Job? = null
    private var acceptScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clientScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var stopped: Boolean = true
    private val acceptFinished = AtomicBoolean(true)

    @Volatile
    private var boundAddress: IpcChannelAddress? = null

    actual val address: IpcChannelAddress?
        get() = boundAddress

    actual val isRunning: Boolean
        get() = !stopped && boundAddress != null

    actual fun start(
        channelName: String,
        runtimeDir: String,
        config: IpcConfig,
        onClientConnected: (IpcConnection) -> Unit
    ): Boolean {
        close()
        stopped = false

        if (MacosXpcListenerSupport.isRunningWithDefaultRuntimeDir(channelName, runtimeDir)) {
            val machName = MacosXpcListenerSupport.machServiceNameForChannel(channelName)
            val handle = MacosXpcListenerSupport.tryStartMachServiceListener(
                machName = machName,
                config = config,
                expectedUid = geteuid(),
                onClientConnected = onClientConnected
            )
            if (handle != null) {
                xpcHandle = handle
                boundAddress = IpcChannelAddress(
                    transportKind = IpcChannelAddress.TRANSPORT_MACOS_XPC_MACH,
                    endpointAddress = machName
                )
                return true
            }
        }

        val resolved = IpcChannelAddress.resolveDesktopEndpoint(
            channelName = channelName,
            runtimeDir = runtimeDir,
            osHint = "macos"
        )
        val socketPath = resolved.endpointAddress
        val fd = MacosUnixSocketSupport.bindAndListen(socketPath)
        if (fd < 0) {
            stopped = true
            return false
        }

        val wakePair = memScoped {
            val fds = allocArray<IntVar>(2)
            if (pipe(fds) != 0) null else (fds[0] to fds[1])
        }
        if (wakePair == null) {
            close(fd)
            unlink(socketPath)
            stopped = true
            return false
        }
        MacosUnixSocketSupport.setCloseOnExec(wakePair.first)
        MacosUnixSocketSupport.setCloseOnExec(wakePair.second)
        MacosUnixSocketSupport.setNonBlocking(wakePair.first)
        MacosUnixSocketSupport.setNonBlocking(wakePair.second)

        unixListenFd = fd
        wakeupReadFd = wakePair.first
        wakeupWriteFd = wakePair.second
        boundAddress = resolved
        acceptFinished.store(false)

        if (acceptScope.coroutineContext[Job]?.isActive != true) {
            acceptScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        if (clientScope.coroutineContext[Job]?.isActive != true) {
            clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        acceptJob = acceptScope.launch {
            try {
                while (!stopped && unixListenFd == fd) {
                    val ready = memScoped {
                        val pfds = allocArray<pollfd>(2)
                        pfds[0].fd = fd
                        pfds[0].events = POLLIN.toShort()
                        pfds[0].revents = 0
                        pfds[1].fd = wakePair.first
                        pfds[1].events = POLLIN.toShort()
                        pfds[1].revents = 0
                        val rc = poll(pfds, 2u, 200)
                        rc > 0 && (pfds[0].revents.toInt() and POLLIN) != 0
                    }
                    if (!ready || stopped || unixListenFd != fd) continue
                    val clientFd = accept(fd, null, null)
                    if (clientFd < 0) {
                        val err = errno
                        if (err == EINTR || err == EAGAIN || err == EWOULDBLOCK) {
                            continue
                        }
                        if (stopped || unixListenFd != fd) break
                        continue
                    }
                    MacosUnixSocketSupport.configureSocketFd(clientFd)
                    if (!MacosUnixSocketSupport.verifyPeerSameUid(clientFd)) {
                        close(clientFd)
                        continue
                    }
                    val conn = MacosUnixSocketConnection(clientFd, config.maxPacketBytes)
                    clientScope.launch { onClientConnected(conn) }
                }
            } finally {
                acceptFinished.store(true)
            }
        }
        return true
    }

    actual fun close() {
        stopped = true
        xpcHandle?.close()
        xpcHandle = null

        val wWrite = wakeupWriteFd
        val wRead = wakeupReadFd
        val fd = unixListenFd
        val socketPath = boundAddress
            ?.takeIf { it.transportKind == IpcChannelAddress.TRANSPORT_UNIX_SOCKET }
            ?.endpointAddress

        unixListenFd = -1
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
        if (!socketPath.isNullOrEmpty()) {
            unlink(socketPath)
        }

        acceptJob?.cancel()
        acceptJob = null
        acceptScope.cancel()
        clientScope.cancel()
    }
}
