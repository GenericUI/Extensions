package com.nexustools.genericui.ext.serviceworker

internal actual object DesktopIpcClient {
    actual fun connect(
        transportKind: String,
        address: String,
        timeoutMs: Int
    ): DesktopIpcConnection? {
        if (address.isBlank()) return null
        return when (transportKind) {
            DesktopEndpointDescriptor.TRANSPORT_MACOS_XPC_MACH ->
                MacosXpcListenerSupport.connectMachService(address)

            DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET ->
                MacosUnixSocketSupport.connect(address, timeoutMs)

            else -> null
        }
    }
}
