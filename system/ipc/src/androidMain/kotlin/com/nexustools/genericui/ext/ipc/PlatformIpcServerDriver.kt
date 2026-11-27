package com.nexustools.genericui.ext.ipc

/**
 * Android [PlatformIpcServerDriver] stub; desktop socket/pipe servers are unsupported on Android.
 */
internal actual class PlatformIpcServerDriver actual constructor() {
    actual val address: IpcChannelAddress? = null
    actual val isRunning: Boolean = false

    actual fun start(
        channelName: String,
        runtimeDir: String,
        config: IpcConfig,
        onClientConnected: (IpcConnection) -> Unit
    ): Boolean = false

    actual fun close() = Unit
}
