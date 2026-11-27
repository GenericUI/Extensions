package com.nexustools.genericui.ext.ipc

/**
 * Lifecycle state of an [IpcConnection].
 */
enum class IpcConnectionState {
    CONNECTING,
    OPEN,
    CLOSING,
    CLOSED
}
