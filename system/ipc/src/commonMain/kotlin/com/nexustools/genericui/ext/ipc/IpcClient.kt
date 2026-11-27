package com.nexustools.genericui.ext.ipc

/**
 * Local IPC client connecting to an [IpcChannelAddress] exposed by an [IpcServer].
 */
object IpcClient {
    fun connect(
        address: IpcChannelAddress,
        timeoutMs: Int = 5_000,
        config: IpcConfig = IpcConfig()
    ): IpcConnection? {
        if (address.endpointAddress.isBlank() || address.transportKind.isBlank()) {
            return null
        }
        return PlatformIpcClientDriver.connect(address, timeoutMs, config)
    }
}
