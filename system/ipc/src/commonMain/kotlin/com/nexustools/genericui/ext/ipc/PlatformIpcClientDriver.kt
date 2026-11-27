package com.nexustools.genericui.ext.ipc

/**
 * Platform backend driver backing [IpcClient].
 */
internal expect object PlatformIpcClientDriver {
    fun connect(
        address: IpcChannelAddress,
        timeoutMs: Int,
        config: IpcConfig
    ): IpcConnection?
}
