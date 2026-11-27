@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.ipc

import kotlin.concurrent.atomics.AtomicReference

/**
 * Shared base [IpcConnection] implementing length-prefixed binary framing, partial-read protection,
 * `maxPacketBytes` validation, [IpcPacket] BinaryJson serialization, and atomic [IpcConnectionState]
 * lifecycle transitions across all stream/pipe/socket desktop transports.
 */
abstract class BaseFramedIpcConnection(
    protected val maxPacketBytes: Int = IpcPacketFramer.MAX_PACKET_BYTES,
    initialState: IpcConnectionState = IpcConnectionState.OPEN
) : IpcConnection {

    private companion object {
        const val FRAME_HEADER_BYTES = 4
        const val MIN_FRAME_COMPLETION_TIMEOUT_MS = 10_000
    }

    private val stateRef = AtomicReference(initialState)
    private val readMutex = IpcMutex()
    private val writeMutex = IpcMutex()

    override val state: IpcConnectionState
        get() = stateRef.load()

    override val isClosed: Boolean
        get() {
            val current = stateRef.load()
            return current == IpcConnectionState.CLOSING || current == IpcConnectionState.CLOSED
        }

    protected fun markOpen() {
        stateRef.compareAndSet(IpcConnectionState.CONNECTING, IpcConnectionState.OPEN)
    }

    override fun sendPacket(packet: IpcPacket): Boolean {
        if (isClosed) return false
        val body = packet.toBinaryJsonBytes()
        return sendBody(body)
    }

    override fun receivePacket(timeoutMs: Int): IpcPacket? {
        val body = readBody(timeoutMs) ?: return null
        return IpcPacketFramer.decodePacket(body, maxPacketBytes)
    }

    override fun sendBody(bodyBytes: ByteArray): Boolean {
        if (isClosed || bodyBytes.isEmpty() || bodyBytes.size > maxPacketBytes) {
            return false
        }
        val framed = IpcPacketFramer.encodeFrame(bodyBytes, maxPacketBytes)
        return writeMutex.withLock {
            if (isClosed) return@withLock false
            writeFrameBytes(framed)
        }
    }

    override fun readBody(timeoutMs: Int): ByteArray? {
        if (isClosed) return null
        return readMutex.withLock {
            if (isClosed) return@withLock null
            val header = readFrameBytes(
                count = FRAME_HEADER_BYTES,
                initialTimeoutMs = timeoutMs,
                isHeader = true
            ) ?: return@withLock null
            val length = IpcPacketFramer.readIntBE(header, 0)
            if (length <= 0 || length > maxPacketBytes) {
                close()
                return@withLock null
            }
            val bodyTimeoutMs = when {
                timeoutMs > 0 -> maxOf(timeoutMs, MIN_FRAME_COMPLETION_TIMEOUT_MS)
                timeoutMs == 0 -> MIN_FRAME_COMPLETION_TIMEOUT_MS
                else -> -1
            }
            readFrameBytes(
                count = length,
                initialTimeoutMs = bodyTimeoutMs,
                isHeader = false
            )
        }
    }

    final override fun close() {
        while (true) {
            val current = stateRef.load()
            if (current == IpcConnectionState.CLOSING || current == IpcConnectionState.CLOSED) {
                return
            }
            if (stateRef.compareAndSet(current, IpcConnectionState.CLOSING)) {
                break
            }
        }
        try {
            closeTransport()
        } finally {
            stateRef.store(IpcConnectionState.CLOSED)
        }
    }

    protected fun computeFrameCompletionTimeoutMs(initialTimeoutMs: Int): Int = when {
        initialTimeoutMs > 0 -> maxOf(initialTimeoutMs, MIN_FRAME_COMPLETION_TIMEOUT_MS)
        initialTimeoutMs == 0 -> MIN_FRAME_COMPLETION_TIMEOUT_MS
        else -> -1
    }

    protected abstract fun writeFrameBytes(framedBytes: ByteArray): Boolean

    protected abstract fun readFrameBytes(
        count: Int,
        initialTimeoutMs: Int,
        isHeader: Boolean
    ): ByteArray?

    protected abstract fun closeTransport()
}
