package com.nexustools.genericui.ext.ipc

/**
 * macOS [PlatformIpcClientDriver] supporting both libxpc Mach services (`macos-xpc-mach`)
 * and `AF_UNIX` domain sockets (`unix-socket`).
 */
internal actual object PlatformIpcClientDriver {
    actual fun connect(
        address: IpcChannelAddress,
        timeoutMs: Int,
        config: IpcConfig
    ): IpcConnection? {
        if (address.endpointAddress.isBlank()) return null
        return when (address.transportKind) {
            IpcChannelAddress.TRANSPORT_MACOS_XPC_MACH ->
                MacosXpcListenerSupport.connectMachService(address.endpointAddress, config)

            IpcChannelAddress.TRANSPORT_UNIX_SOCKET ->
                MacosUnixSocketSupport.connect(address.endpointAddress, timeoutMs, config)

            else -> null
        }
    }
}
