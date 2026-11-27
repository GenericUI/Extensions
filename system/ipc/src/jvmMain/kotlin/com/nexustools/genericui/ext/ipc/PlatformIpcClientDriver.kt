package com.nexustools.genericui.ext.ipc

/**
 * JVM [PlatformIpcClientDriver] dispatching to Win32 Named Pipes or `AF_UNIX` domain sockets.
 */
internal actual object PlatformIpcClientDriver {
    actual fun connect(
        address: IpcChannelAddress,
        timeoutMs: Int,
        config: IpcConfig
    ): IpcConnection? {
        return when (address.transportKind) {
            IpcChannelAddress.TRANSPORT_WINDOWS_NAMED_PIPE -> {
                if (JvmWindowsNamedPipeBridge.isSupported()) {
                    JvmWindowsNamedPipeBridge.connect(address.endpointAddress, timeoutMs, config)
                } else if (System.getProperty("os.name").orEmpty().lowercase().contains("win")) {
                    JvmFallbackNamedPipeClientConnection.connect(address.endpointAddress, timeoutMs, config)
                } else {
                    null
                }
            }
            IpcChannelAddress.TRANSPORT_UNIX_SOCKET ->
                JvmUnixDomainSocketBridge.connect(address.endpointAddress, timeoutMs, config)
            else -> null
        }
    }
}
