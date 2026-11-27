package com.nexustools.genericui.ext.ipc

/**
 * Configuration options governing desktop and Android Binder IPC timeouts, frame limits, and buffers.
 */
data class IpcConfig(
    val connectTimeoutMs: Int = 5_000,
    val maxPacketBytes: Int = 16 * 1024 * 1024,
    val pipeBufferSizeBytes: Int = 65_536
) {
    init {
        require(connectTimeoutMs >= 0) { "connectTimeoutMs must be >= 0, was $connectTimeoutMs" }
        require(maxPacketBytes > 0) { "maxPacketBytes must be > 0, was $maxPacketBytes" }
        require(pipeBufferSizeBytes > 0) { "pipeBufferSizeBytes must be > 0, was $pipeBufferSizeBytes" }
    }
}
