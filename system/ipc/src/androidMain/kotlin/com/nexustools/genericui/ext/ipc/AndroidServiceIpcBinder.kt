package com.nexustools.genericui.ext.ipc

import android.os.Binder
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import java.util.NoSuchElementException
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.Volatile

/**
 * Android [Binder] endpoint helper supporting `PING`, client callback registration/pruning,
 * direct and >240 KB chunked [IpcPacket] `BinaryJson` transactions, synchronous reply parcels,
 * and asynchronous callback pushes.
 */
open class AndroidServiceIpcBinder(
    val config: IpcConfig = IpcConfig(),
    onPacketReceived: ((IpcPacket) -> IpcPacket?)? = null
) : Binder() {

    private companion object {
        const val REPLY_INVALID_OR_FAILED: Int = -1
        const val REPLY_ACCEPTED_NO_RESPONSE: Int = 0
        const val REPLY_HAS_RESPONSE_PACKET: Int = 1
    }

    @Volatile
    private var packetHandler: ((IpcPacket) -> IpcPacket?)? = onPacketReceived

    private val inboundAssembler = AndroidBinderChunkAssembler(
        maxPacketBytes = config.maxPacketBytes,
        chunkSizeBytes = AndroidServiceIpcProtocol.MAX_DIRECT_PARCEL_BYTES
    )
    private val outboundAssembler = AndroidBinderChunkAssembler(
        maxPacketBytes = config.maxPacketBytes,
        chunkSizeBytes = AndroidServiceIpcProtocol.MAX_DIRECT_PARCEL_BYTES
    )
    private val callbackLock = Any()
    private val callbacks = LinkedHashMap<IBinder, IBinder.DeathRecipient>()
    private val nextOutboundTransferId = AtomicLong(1L)

    fun setPacketHandler(handler: ((IpcPacket) -> IpcPacket?)?) {
        packetHandler = handler
    }

    fun registerCallback(callback: IBinder): Boolean {
        if (!callback.isBinderAlive) {
            return false
        }
        val recipient = IBinder.DeathRecipient {
            unregisterCallback(callback)
        }
        synchronized(callbackLock) {
            if (callbacks.containsKey(callback)) {
                return true
            }
            try {
                callback.linkToDeath(recipient, 0)
            } catch (_: RemoteException) {
                return false
            }
            callbacks[callback] = recipient
            return true
        }
    }

    fun unregisterCallback(callback: IBinder): Boolean {
        val recipient = synchronized(callbackLock) {
            callbacks.remove(callback)
        } ?: return false
        try {
            callback.unlinkToDeath(recipient, 0)
        } catch (_: NoSuchElementException) {
            // Binder already died or was unlinked.
        }
        return true
    }

    fun callbackCount(): Int = synchronized(callbackLock) {
        callbacks.size
    }

    fun dispatchToClient(packet: IpcPacket): Boolean =
        broadcastPacket(packet) > 0

    fun broadcastPacket(packet: IpcPacket): Int {
        val bytes = packet.toBinaryJsonBytes()
        if (bytes.size > config.maxPacketBytes) {
            throw IpcException(
                "Outbound IpcPacket size ${bytes.size} exceeds maxPacketBytes=${config.maxPacketBytes}."
            )
        }
        val snapshot = synchronized(callbackLock) { callbacks.keys.toList() }
        var delivered = 0
        for (callback in snapshot) {
            if (sendBytesToCallback(callback, bytes)) {
                delivered++
            }
        }
        return delivered
    }

    fun sendToCallback(callback: IBinder, packet: IpcPacket): Boolean {
        val bytes = packet.toBinaryJsonBytes()
        if (bytes.size > config.maxPacketBytes) {
            throw IpcException(
                "Outbound IpcPacket size ${bytes.size} exceeds maxPacketBytes=${config.maxPacketBytes}."
            )
        }
        return sendBytesToCallback(callback, bytes)
    }

    fun clearCallbacks() {
        val snapshot = synchronized(callbackLock) {
            val entries = callbacks.entries.toList()
            callbacks.clear()
            entries
        }
        for ((binder, recipient) in snapshot) {
            try {
                binder.unlinkToDeath(recipient, 0)
            } catch (_: NoSuchElementException) {
                // Binder already died or was unlinked.
            }
        }
        inboundAssembler.clear()
    }

    protected open fun handlePacket(packet: IpcPacket): IpcPacket? =
        packetHandler?.invoke(packet)

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            AndroidServiceIpcProtocol.TRANSACT_PING -> {
                data.enforceInterface(AndroidServiceIpcProtocol.INTERFACE_DESCRIPTOR)
                reply?.writeNoException()
                reply?.writeInt(1)
                true
            }

            AndroidServiceIpcProtocol.TRANSACT_REGISTER_CALLBACK -> {
                data.enforceInterface(AndroidServiceIpcProtocol.INTERFACE_DESCRIPTOR)
                val callback = data.readStrongBinder()
                val ok = callback != null && registerCallback(callback)
                reply?.writeNoException()
                reply?.writeInt(if (ok) 1 else 0)
                true
            }

            AndroidServiceIpcProtocol.TRANSACT_UNREGISTER_CALLBACK -> {
                data.enforceInterface(AndroidServiceIpcProtocol.INTERFACE_DESCRIPTOR)
                val callback = data.readStrongBinder()
                val ok = callback != null && unregisterCallback(callback)
                reply?.writeNoException()
                reply?.writeInt(if (ok) 1 else 0)
                true
            }

            AndroidServiceIpcProtocol.TRANSACT_SEND_PACKET -> {
                data.enforceInterface(AndroidServiceIpcProtocol.INTERFACE_DESCRIPTOR)
                val bytes = data.createByteArray()
                val packet = decodeBoundedPacket(bytes)
                dispatchIncomingAndWriteReply(packet, reply)
                true
            }

            AndroidServiceIpcProtocol.TRANSACT_CHUNK_BEGIN -> {
                data.enforceInterface(AndroidServiceIpcProtocol.INTERFACE_DESCRIPTOR)
                val transferId = data.readLong()
                val totalBytes = data.readInt()
                val chunkCount = data.readInt()
                val ok = inboundAssembler.begin(transferId, totalBytes, chunkCount)
                reply?.writeNoException()
                reply?.writeInt(if (ok) 1 else 0)
                true
            }

            AndroidServiceIpcProtocol.TRANSACT_CHUNK_PART -> {
                data.enforceInterface(AndroidServiceIpcProtocol.INTERFACE_DESCRIPTOR)
                val transferId = data.readLong()
                val chunkIndex = data.readInt()
                val chunkData = data.createByteArray()
                val ok = chunkData != null && inboundAssembler.append(transferId, chunkIndex, chunkData)
                reply?.writeNoException()
                reply?.writeInt(if (ok) 1 else 0)
                true
            }

            AndroidServiceIpcProtocol.TRANSACT_CHUNK_END -> {
                data.enforceInterface(AndroidServiceIpcProtocol.INTERFACE_DESCRIPTOR)
                val transferId = data.readLong()
                val assembled = inboundAssembler.finish(transferId)
                val packet = decodeBoundedPacket(assembled)
                dispatchIncomingAndWriteReply(packet, reply)
                true
            }

            else -> super.onTransact(code, data, reply, flags)
        }
    }

    private fun decodeBoundedPacket(bytes: ByteArray?): IpcPacket? {
        if (bytes == null || bytes.isEmpty() || bytes.size > config.maxPacketBytes) {
            return null
        }
        return IpcPacket.fromBinaryJsonBytes(bytes)
    }

    private fun dispatchIncomingAndWriteReply(packet: IpcPacket?, reply: Parcel?) {
        if (packet == null) {
            reply?.writeNoException()
            reply?.writeInt(REPLY_INVALID_OR_FAILED)
            return
        }
        val responsePacket = handlePacket(packet)
        reply?.writeNoException()
        if (responsePacket == null) {
            reply?.writeInt(REPLY_ACCEPTED_NO_RESPONSE)
            return
        }
        val responseBytes = responsePacket.toBinaryJsonBytes()
        if (responseBytes.size > config.maxPacketBytes) {
            reply?.writeInt(REPLY_INVALID_OR_FAILED)
            return
        }
        reply?.writeInt(REPLY_HAS_RESPONSE_PACKET)
        reply?.writeByteArray(responseBytes)
    }

    private fun sendBytesToCallback(callback: IBinder, bytes: ByteArray): Boolean {
        if (!callback.isBinderAlive) {
            unregisterCallback(callback)
            return false
        }
        return try {
            if (bytes.size <= AndroidServiceIpcProtocol.MAX_DIRECT_PARCEL_BYTES) {
                transactCallbackBytes(
                    callback = callback,
                    code = AndroidServiceIpcProtocol.CALLBACK_TRANSACT_ON_PACKET
                ) { data ->
                    data.writeByteArray(bytes)
                }
            } else {
                val chunks = outboundAssembler.split(bytes)
                val transferId = nextOutboundTransferId.getAndIncrement()
                val beginOk = transactCallbackBytes(
                    callback = callback,
                    code = AndroidServiceIpcProtocol.CALLBACK_TRANSACT_CHUNK_BEGIN
                ) { data ->
                    data.writeLong(transferId)
                    data.writeInt(bytes.size)
                    data.writeInt(chunks.size)
                }
                if (!beginOk) return false

                for (index in chunks.indices) {
                    val partOk = transactCallbackBytes(
                        callback = callback,
                        code = AndroidServiceIpcProtocol.CALLBACK_TRANSACT_CHUNK_PART
                    ) { data ->
                        data.writeLong(transferId)
                        data.writeInt(index)
                        data.writeByteArray(chunks[index])
                    }
                    if (!partOk) return false
                }

                transactCallbackBytes(
                    callback = callback,
                    code = AndroidServiceIpcProtocol.CALLBACK_TRANSACT_CHUNK_END
                ) { data ->
                    data.writeLong(transferId)
                }
            }
        } catch (_: DeadObjectException) {
            unregisterCallback(callback)
            false
        } catch (_: RemoteException) {
            unregisterCallback(callback)
            false
        }
    }

    private fun transactCallbackBytes(
        callback: IBinder,
        code: Int,
        writeBody: (Parcel) -> Unit
    ): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(AndroidServiceIpcProtocol.CALLBACK_INTERFACE_DESCRIPTOR)
            writeBody(data)
            data.setDataPosition(0)
            val ok = callback.transact(code, data, reply, 0)
            if (!ok) {
                unregisterCallback(callback)
                return false
            }
            reply.setDataPosition(0)
            reply.readException()
            return reply.readInt() == 1
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
