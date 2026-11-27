package com.nexustools.genericui.ext.ipc

/**
 * JVM [PlatformIpcServerDriver] supporting both Win32 Named Pipes on Windows (JDK 22+ FFM)
 * and `AF_UNIX` Unix domain sockets (JEP 380) across Windows, macOS, and Linux.
 */
internal actual class PlatformIpcServerDriver actual constructor() {

    private var pipeServer: JvmWin32NamedPipeServer? = null
    private var unixServer: JvmUnixSocketServer? = null

    actual val address: IpcChannelAddress?
        get() = pipeServer?.address ?: unixServer?.address

    actual val isRunning: Boolean
        get() = pipeServer?.isRunning == true || unixServer?.isRunning == true

    actual fun start(
        channelName: String,
        runtimeDir: String,
        config: IpcConfig,
        onClientConnected: (IpcConnection) -> Unit
    ): Boolean {
        close()
        val resolved = IpcChannelAddress.resolveDesktopEndpoint(channelName, runtimeDir)
        return startWithResolvedAddress(resolved, config, onClientConnected)
    }

    internal fun startWithTransport(
        transportKind: String,
        channelName: String,
        runtimeDir: String,
        config: IpcConfig = IpcConfig(),
        onClientConnected: (IpcConnection) -> Unit
    ): Boolean {
        close()
        val osHint = when (transportKind) {
            IpcChannelAddress.TRANSPORT_WINDOWS_NAMED_PIPE -> "windows"
            IpcChannelAddress.TRANSPORT_UNIX_SOCKET -> "unix"
            else -> return false
        }
        val resolved = IpcChannelAddress.resolveDesktopEndpoint(channelName, runtimeDir, osHint)
        return startWithResolvedAddress(resolved, config, onClientConnected)
    }

    private fun startWithResolvedAddress(
        resolved: IpcChannelAddress,
        config: IpcConfig,
        onClientConnected: (IpcConnection) -> Unit
    ): Boolean {
        return when (resolved.transportKind) {
            IpcChannelAddress.TRANSPORT_WINDOWS_NAMED_PIPE -> {
                if (!JvmWindowsNamedPipeBridge.isSupported()) {
                    return false
                }
                val server = JvmWin32NamedPipeServer()
                if (!server.start(resolved.endpointAddress, config, onClientConnected)) {
                    server.close()
                    return false
                }
                pipeServer = server
                true
            }
            IpcChannelAddress.TRANSPORT_UNIX_SOCKET -> {
                val server = JvmUnixSocketServer()
                if (!server.start(resolved.endpointAddress, config, onClientConnected)) {
                    server.close()
                    return false
                }
                unixServer = server
                true
            }
            else -> false
        }
    }

    actual fun close() {
        pipeServer?.close()
        pipeServer = null
        unixServer?.close()
        unixServer = null
    }
}
