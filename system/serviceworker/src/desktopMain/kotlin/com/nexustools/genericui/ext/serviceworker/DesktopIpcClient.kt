package com.nexustools.genericui.ext.serviceworker

/**
 * Platform-specific native IPC client connecting to a [DesktopIpcServer] endpoint.
 */
internal expect object DesktopIpcClient {
    fun connect(
        transportKind: String,
        address: String,
        timeoutMs: Int = 5_000
    ): DesktopIpcConnection?
}
