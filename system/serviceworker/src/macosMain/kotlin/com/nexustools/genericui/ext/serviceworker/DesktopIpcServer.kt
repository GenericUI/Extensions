@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.posix.EINTR
import platform.posix.POLLIN
import platform.posix.accept
import platform.posix.close
import platform.posix.errno
import platform.posix.geteuid
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.unlink
import kotlin.concurrent.Volatile

/**
 * macOS two-tier IPC server:
 * - Tier 1: `launchd`-registered XPC Mach service listener (`xpc_mach`) when running with the
 *   default `runtimeDir` and a registered LaunchAgent Mach service.
 * - Tier 2: Owner-only (`0600`) `AF_UNIX` `SOCK_STREAM` socket (`unix`) fallback for direct
 *   binary launches, tests, and non-`launchd` sessions.
 */
internal actual class DesktopIpcServer actual constructor() {

    private var xpcHandle: XpcListenerHandle? = null
    private var unixListenFd: Int = -1
    private var unixSocketPath: String? = null
    private var acceptJob: Job? = null
    private var acceptScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var stopped: Boolean = false

    @Volatile
    private var activeTransportKind: String = ""

    @Volatile
    private var activeEndpointAddress: String = ""

    actual val transportKind: String
        get() = activeTransportKind

    actual val endpointAddress: String
        get() = activeEndpointAddress

    actual fun start(
        appId: String,
        runtimeDir: String,
        onClientConnected: (DesktopIpcConnection) -> Unit
    ): Boolean {
        close()
        stopped = false

        if (MacosXpcListenerSupport.isRunningWithDefaultRuntimeDir(appId, runtimeDir)) {
            val machName = MacosXpcListenerSupport.machServiceNameForAppId(appId)
            val handle = MacosXpcListenerSupport.tryStartMachServiceListener(
                machName = machName,
                expectedUid = geteuid(),
                onClientConnected = onClientConnected
            )
            if (handle != null) {
                xpcHandle = handle
                activeTransportKind = DesktopEndpointDescriptor.TRANSPORT_MACOS_XPC_MACH
                activeEndpointAddress = machName
                return true
            }
        }

        val socketPath = MacosUnixSocketSupport.resolveSocketPath(appId, runtimeDir) ?: return false
        val fd = MacosUnixSocketSupport.bindAndListen(socketPath)
        if (fd < 0) return false

        unixListenFd = fd
        unixSocketPath = socketPath
        activeTransportKind = DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET
        activeEndpointAddress = socketPath

        if (acceptScope.coroutineContext[Job]?.isActive != true) {
            acceptScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        acceptJob = acceptScope.launch {
            while (!stopped && unixListenFd >= 0) {
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
                    MacosUnixSocketSupport.configureSocketFd(clientFd)
                    if (!MacosUnixSocketSupport.verifyPeerSameUid(clientFd)) {
                        close(clientFd)
                        continue
                    }
                    onClientConnected(MacosUnixSocketConnection(clientFd))
                }
            }
        }
        return true
    }

    actual fun close() {
        stopped = true
        xpcHandle?.close()
        xpcHandle = null

        val fd = unixListenFd
        unixListenFd = -1
        if (fd >= 0) {
            close(fd)
        }
        val socketPath = unixSocketPath
        unixSocketPath = null
        if (socketPath != null) {
            unlink(socketPath)
        }
        activeTransportKind = ""
        activeEndpointAddress = ""

        acceptJob?.cancel()
        acceptJob = null
        acceptScope.cancel()
    }
}
