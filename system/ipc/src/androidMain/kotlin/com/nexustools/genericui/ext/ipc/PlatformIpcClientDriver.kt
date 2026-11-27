package com.nexustools.genericui.ext.ipc

/**
 * Android [PlatformIpcClientDriver] stub; desktop socket/pipe clients are unsupported on Android.
 */
internal actual object PlatformIpcClientDriver {
    actual fun connect(
        address: IpcChannelAddress,
        timeoutMs: Int,
        config: IpcConfig
    ): IpcConnection? = null
}
