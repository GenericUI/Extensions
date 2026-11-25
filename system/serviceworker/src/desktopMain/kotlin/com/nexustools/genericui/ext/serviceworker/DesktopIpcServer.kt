package com.nexustools.genericui.ext.serviceworker

/**
 * Platform-specific native IPC server accepting local UI client connections.
 */
internal expect class DesktopIpcServer() {
    val transportKind: String
    val endpointAddress: String

    fun start(
        appId: String,
        runtimeDir: String,
        onClientConnected: (DesktopIpcConnection) -> Unit
    ): Boolean

    fun close()
}
