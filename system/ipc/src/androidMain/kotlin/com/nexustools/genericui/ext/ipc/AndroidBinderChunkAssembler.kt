package com.nexustools.genericui.ext.ipc

/**
 * Splits oversized BinaryJson payloads (> [chunkSizeBytes]) into Binder-safe chunks and
 * reassembles incoming chunked transfers by `transferId`, enforcing [maxPacketBytes] and
 * expiring stale incomplete transfers.
 */
class AndroidBinderChunkAssembler(
    val maxPacketBytes: Int = IpcPacketFramer.MAX_PACKET_BYTES,
    val chunkSizeBytes: Int = AndroidServiceIpcProtocol.MAX_DIRECT_PARCEL_BYTES
) {
    private class PendingTransfer(
        val totalBytes: Int,
        val expectedChunks: Int,
        val buffer: ByteArray,
        var writtenBytes: Int = 0,
        var receivedChunks: Int = 0,
        var lastUpdatedMs: Long
    )

    private val lock = Any()
    private val transfers = LinkedHashMap<Long, PendingTransfer>()

    internal var staleTransferTtlMs: Long = AndroidServiceIpcProtocol.DEFAULT_STALE_TRANSFER_TTL_MS
    internal var clockMs: () -> Long = { System.nanoTime() / 1_000_000L }

    init {
        require(maxPacketBytes > 0) { "maxPacketBytes must be > 0, was $maxPacketBytes" }
        require(chunkSizeBytes > 0) { "chunkSizeBytes must be > 0, was $chunkSizeBytes" }
    }

    fun split(payload: ByteArray): List<ByteArray> {
        if (payload.isEmpty()) {
            throw IpcException("Cannot chunk empty Binder IPC payload.")
        }
        if (payload.size > maxPacketBytes) {
            throw IpcException(
                "Binder IPC payload size ${payload.size} exceeds maxPacketBytes=$maxPacketBytes."
            )
        }
        val chunkCount = (payload.size + chunkSizeBytes - 1) / chunkSizeBytes
        val chunks = ArrayList<ByteArray>(chunkCount)
        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + chunkSizeBytes, payload.size)
            chunks.add(payload.copyOfRange(offset, end))
            offset = end
        }
        return chunks
    }

    fun begin(transferId: Long, totalBytes: Int, chunkCount: Int): Boolean {
        if (totalBytes <= 0 || totalBytes > maxPacketBytes || chunkCount <= 0) {
            return false
        }
        val minExpectedChunks = (totalBytes.toLong() + chunkSizeBytes.toLong() - 1L) / chunkSizeBytes.toLong()
        if (chunkCount.toLong() < minExpectedChunks || chunkCount > totalBytes) {
            return false
        }
        val now = clockMs()
        synchronized(lock) {
            purgeExpiredLocked(now)
            if (transfers.containsKey(transferId)) {
                return false
            }
            transfers[transferId] = PendingTransfer(
                totalBytes = totalBytes,
                expectedChunks = chunkCount,
                buffer = ByteArray(totalBytes),
                lastUpdatedMs = now
            )
            return true
        }
    }

    fun append(transferId: Long, chunkIndex: Int, chunkData: ByteArray): Boolean {
        if (chunkData.isEmpty() || chunkData.size > chunkSizeBytes || chunkIndex < 0) {
            return false
        }
        val now = clockMs()
        synchronized(lock) {
            purgeExpiredLocked(now)
            val transfer = transfers[transferId] ?: return false
            if (chunkIndex != transfer.receivedChunks || chunkIndex >= transfer.expectedChunks) {
                transfers.remove(transferId)
                return false
            }
            val nextWritten = transfer.writtenBytes.toLong() + chunkData.size.toLong()
            if (nextWritten > transfer.totalBytes.toLong()) {
                transfers.remove(transferId)
                return false
            }
            chunkData.copyInto(transfer.buffer, destinationOffset = transfer.writtenBytes)
            transfer.writtenBytes += chunkData.size
            transfer.receivedChunks++
            transfer.lastUpdatedMs = now
            return true
        }
    }

    fun finish(transferId: Long): ByteArray? {
        val now = clockMs()
        synchronized(lock) {
            purgeExpiredLocked(now)
            val transfer = transfers.remove(transferId) ?: return null
            if (transfer.receivedChunks != transfer.expectedChunks ||
                transfer.writtenBytes != transfer.totalBytes
            ) {
                return null
            }
            return transfer.buffer
        }
    }

    fun abort(transferId: Long) {
        synchronized(lock) {
            transfers.remove(transferId)
        }
    }

    fun expireStaleTransfers(): Int {
        val now = clockMs()
        synchronized(lock) {
            return purgeExpiredLocked(now)
        }
    }

    fun activeTransferCount(): Int = synchronized(lock) {
        transfers.size
    }

    fun clear() {
        synchronized(lock) {
            transfers.clear()
        }
    }

    private fun purgeExpiredLocked(nowMs: Long): Int {
        val ttl = staleTransferTtlMs
        if (ttl < 0L || transfers.isEmpty()) return 0
        var removed = 0
        val iter = transfers.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (nowMs - entry.value.lastUpdatedMs >= ttl) {
                iter.remove()
                removed++
            }
        }
        return removed
    }
}
