package com.nexustools.genericui.ext.ipc

/**
 * Full-duplex framed local IPC connection exchanging [IpcPacket]s or raw length-prefixed bodies.
 */
interface IpcConnection {
    val state: IpcConnectionState
    val isClosed: Boolean

    fun sendPacket(packet: IpcPacket): Boolean

    fun receivePacket(timeoutMs: Int = 250): IpcPacket?

    fun sendBody(bodyBytes: ByteArray): Boolean

    fun readBody(timeoutMs: Int = 250): ByteArray?

    fun close()
}
