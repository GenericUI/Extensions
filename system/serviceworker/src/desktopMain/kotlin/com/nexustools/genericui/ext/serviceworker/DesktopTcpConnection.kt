package com.nexustools.genericui.ext.serviceworker

/**
 * Framed full-duplex loopback TCP socket connection between [DesktopServiceWorkerHost]
 * and [DesktopServiceWorkerChannelClient].
 */
internal interface DesktopTcpConnection {
    val isClosed: Boolean

    /**
     * Writes a length-prefixed binary body payload `[4-byte BE length][bodyBytes]`.
     */
    fun sendBody(bodyBytes: ByteArray): Boolean

    /**
     * Reads the next length-prefixed binary body payload, returning `null` on timeout or EOF.
     */
    fun receiveBody(timeoutMs: Int = 250): ByteArray?

    fun close()
}
