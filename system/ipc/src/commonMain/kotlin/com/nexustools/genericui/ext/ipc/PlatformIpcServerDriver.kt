package com.nexustools.genericui.ext.ipc

/**
 * Platform backend driver backing [IpcServer].
 */
internal expect class PlatformIpcServerDriver() {
    val address: IpcChannelAddress?
    val isRunning: Boolean

    fun start(
        channelName: String,
        runtimeDir: String,
        config: IpcConfig,
        onClientConnected: (IpcConnection) -> Unit
    ): Boolean

    fun close()
}
