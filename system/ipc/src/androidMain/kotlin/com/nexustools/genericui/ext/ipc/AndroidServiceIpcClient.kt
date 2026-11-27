package com.nexustools.genericui.ext.ipc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import java.util.NoSuchElementException
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.Volatile

/**
 * Android client-side Service Binder IPC coordinator managing `ServiceConnection`,
 * [IBinder.DeathRecipient], callback registration for async Service -> Client pushes,
 * automatic chunking for packets > 240 KB, synchronous request/reply, and clean `unbind()`.
 */
class AndroidServiceIpcClient(
    private val context: Context,
    val config: IpcConfig = IpcConfig()
) {
    private class TransactOutcome(
        val accepted: Boolean,
        val replyPacket: IpcPacket?
    )

    private companion object {
        const val REPLY_ACCEPTED_NO_RESPONSE: Int = 0
        const val REPLY_HAS_RESPONSE_PACKET: Int = 1
    }

    private val stateLock = Any()
    private val outboundAssembler = AndroidBinderChunkAssembler(
        maxPacketBytes = config.maxPacketBytes,
        chunkSizeBytes = AndroidServiceIpcProtocol.MAX_DIRECT_PARCEL_BYTES
    )
    private val inboundAssembler = AndroidBinderChunkAssembler(
        maxPacketBytes = config.maxPacketBytes,
        chunkSizeBytes = AndroidServiceIpcProtocol.MAX_DIRECT_PARCEL_BYTES
    )
    private val nextTransferId = AtomicLong(1L)

    @Volatile
    private var remoteBinder: IBinder? = null

    @Volatile
    private var boundByClient: Boolean = false

    @Volatile
    private var onPacketReceivedListener: ((IpcPacket) -> Unit)? = null

    @Volatile
    private var onConnectionStateChangedListener: ((Boolean) -> Unit)? = null

    private val deathRecipient = IBinder.DeathRecipient {
        handleRemoteLost()
    }

    private val callbackBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                AndroidServiceIpcProtocol.CALLBACK_TRANSACT_ON_PACKET -> {
                    data.enforceInterface(AndroidServiceIpcProtocol.CALLBACK_INTERFACE_DESCRIPTOR)
                    val bytes = data.createByteArray()
                    val packet = decodeBoundedPacket(bytes)
                    val ok = if (packet != null) {
                        onPacketReceivedListener?.invoke(packet)
                        true
                    } else {
                        false
                    }
                    reply?.writeNoException()
                    reply?.writeInt(if (ok) 1 else 0)
                    true
                }

                AndroidServiceIpcProtocol.CALLBACK_TRANSACT_CHUNK_BEGIN -> {
                    data.enforceInterface(AndroidServiceIpcProtocol.CALLBACK_INTERFACE_DESCRIPTOR)
                    val transferId = data.readLong()
                    val totalBytes = data.readInt()
                    val chunkCount = data.readInt()
                    val ok = inboundAssembler.begin(transferId, totalBytes, chunkCount)
                    reply?.writeNoException()
                    reply?.writeInt(if (ok) 1 else 0)
                    true
                }

                AndroidServiceIpcProtocol.CALLBACK_TRANSACT_CHUNK_PART -> {
                    data.enforceInterface(AndroidServiceIpcProtocol.CALLBACK_INTERFACE_DESCRIPTOR)
                    val transferId = data.readLong()
                    val chunkIndex = data.readInt()
                    val chunkData = data.createByteArray()
                    val ok = chunkData != null && inboundAssembler.append(transferId, chunkIndex, chunkData)
                    reply?.writeNoException()
                    reply?.writeInt(if (ok) 1 else 0)
                    true
                }

                AndroidServiceIpcProtocol.CALLBACK_TRANSACT_CHUNK_END -> {
                    data.enforceInterface(AndroidServiceIpcProtocol.CALLBACK_INTERFACE_DESCRIPTOR)
                    val transferId = data.readLong()
                    val assembled = inboundAssembler.finish(transferId)
                    val packet = decodeBoundedPacket(assembled)
                    val ok = if (packet != null) {
                        onPacketReceivedListener?.invoke(packet)
                        true
                    } else {
                        false
                    }
                    reply?.writeNoException()
                    reply?.writeInt(if (ok) 1 else 0)
                    true
                }

                else -> super.onTransact(code, data, reply, flags)
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service != null) {
                attachBinder(service)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            handleRemoteLost()
        }

        override fun onBindingDied(name: ComponentName?) {
            handleRemoteLost()
        }

        override fun onNullBinding(name: ComponentName?) {
            handleRemoteLost()
        }
    }

    val isBound: Boolean
        get() = boundByClient || remoteBinder != null

    val isConnected: Boolean
        get() = remoteBinder?.isBinderAlive == true

    fun setOnPacketReceived(listener: ((IpcPacket) -> Unit)?) {
        onPacketReceivedListener = listener
    }

    fun setOnConnectionStateChanged(listener: ((Boolean) -> Unit)?) {
        onConnectionStateChangedListener = listener
    }

    fun bind(serviceIntent: Intent, flags: Int = Context.BIND_AUTO_CREATE): Boolean {
        synchronized(stateLock) {
            if (boundByClient) {
                return true
            }
            val started = try {
                context.bindService(serviceIntent, serviceConnection, flags)
            } catch (_: SecurityException) {
                false
            }
            if (started) {
                boundByClient = true
            }
            return started
        }
    }

    fun attachBinder(binder: IBinder): Boolean {
        if (!binder.isBinderAlive) {
            return false
        }
        val previous: IBinder?
        synchronized(stateLock) {
            previous = remoteBinder
            if (previous === binder && binder.isBinderAlive) {
                return true
            }
            try {
                binder.linkToDeath(deathRecipient, 0)
            } catch (_: RemoteException) {
                return false
            }
            remoteBinder = binder
        }
        if (previous != null && previous !== binder) {
            unlinkDeathSafe(previous)
        }
        val registered = registerCallbackOnRemote(binder)
        if (!registered) {
            synchronized(stateLock) {
                if (remoteBinder === binder) {
                    remoteBinder = null
                }
            }
            unlinkDeathSafe(binder)
            return false
        }
        onConnectionStateChangedListener?.invoke(true)
        return true
    }

    fun sendPacket(packet: IpcPacket): Boolean =
        transactPacketInternal(packet).accepted

    fun requestPacket(packet: IpcPacket): IpcPacket? =
        transactPacketInternal(packet).replyPacket

    fun ping(): Boolean {
        val binder = remoteBinder ?: return false
        if (!binder.isBinderAlive) {
            handleRemoteLost()
            return false
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(AndroidServiceIpcProtocol.INTERFACE_DESCRIPTOR)
            data.setDataPosition(0)
            val ok = binder.transact(AndroidServiceIpcProtocol.TRANSACT_PING, data, reply, 0)
            if (!ok) {
                handleRemoteLost()
                return false
            }
            reply.setDataPosition(0)
            reply.readException()
            reply.readInt() == 1
        } catch (_: RemoteException) {
            handleRemoteLost()
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun unbind() {
        val binder: IBinder?
        val shouldUnbindService: Boolean
        val wasConnected: Boolean
        synchronized(stateLock) {
            binder = remoteBinder
            remoteBinder = null
            shouldUnbindService = boundByClient
            boundByClient = false
            wasConnected = binder != null
        }
        if (binder != null) {
            unregisterCallbackOnRemote(binder)
            unlinkDeathSafe(binder)
        }
        if (shouldUnbindService) {
            try {
                context.unbindService(serviceConnection)
            } catch (_: IllegalArgumentException) {
                // ServiceConnection was not registered or already unbound by system.
            }
        }
        inboundAssembler.clear()
        if (wasConnected) {
            onConnectionStateChangedListener?.invoke(false)
        }
    }

    private fun transactPacketInternal(packet: IpcPacket): TransactOutcome {
        val bytes = packet.toBinaryJsonBytes()
        if (bytes.size > config.maxPacketBytes) {
            throw IpcException(
                "Packet size ${bytes.size} exceeds maxPacketBytes=${config.maxPacketBytes}."
            )
        }
        val binder = remoteBinder ?: return TransactOutcome(accepted = false, replyPacket = null)
        if (!binder.isBinderAlive) {
            handleRemoteLost()
            return TransactOutcome(accepted = false, replyPacket = null)
        }

        return try {
            if (bytes.size <= AndroidServiceIpcProtocol.MAX_DIRECT_PARCEL_BYTES) {
                sendTerminalTransact(
                    binder = binder,
                    code = AndroidServiceIpcProtocol.TRANSACT_SEND_PACKET
                ) { data ->
                    data.writeByteArray(bytes)
                }
            } else {
                val chunks = outboundAssembler.split(bytes)
                val transferId = nextTransferId.getAndIncrement()
                val beginOk = sendAckTransact(
                    binder = binder,
                    code = AndroidServiceIpcProtocol.TRANSACT_CHUNK_BEGIN
                ) { data ->
                    data.writeLong(transferId)
                    data.writeInt(bytes.size)
                    data.writeInt(chunks.size)
                }
                if (!beginOk) {
                    return TransactOutcome(accepted = false, replyPacket = null)
                }
                for (index in chunks.indices) {
                    val partOk = sendAckTransact(
                        binder = binder,
                        code = AndroidServiceIpcProtocol.TRANSACT_CHUNK_PART
                    ) { data ->
                        data.writeLong(transferId)
                        data.writeInt(index)
                        data.writeByteArray(chunks[index])
                    }
                    if (!partOk) {
                        return TransactOutcome(accepted = false, replyPacket = null)
                    }
                }
                sendTerminalTransact(
                    binder = binder,
                    code = AndroidServiceIpcProtocol.TRANSACT_CHUNK_END
                ) { data ->
                    data.writeLong(transferId)
                }
            }
        } catch (_: RemoteException) {
            handleRemoteLost()
            TransactOutcome(accepted = false, replyPacket = null)
        }
    }

    private fun sendAckTransact(
        binder: IBinder,
        code: Int,
        writeBody: (Parcel) -> Unit
    ): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(AndroidServiceIpcProtocol.INTERFACE_DESCRIPTOR)
            writeBody(data)
            data.setDataPosition(0)
            val ok = binder.transact(code, data, reply, 0)
            if (!ok) return false
            reply.setDataPosition(0)
            reply.readException()
            return reply.readInt() == 1
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun sendTerminalTransact(
        binder: IBinder,
        code: Int,
        writeBody: (Parcel) -> Unit
    ): TransactOutcome {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(AndroidServiceIpcProtocol.INTERFACE_DESCRIPTOR)
            writeBody(data)
            data.setDataPosition(0)
            val ok = binder.transact(code, data, reply, 0)
            if (!ok) {
                return TransactOutcome(accepted = false, replyPacket = null)
            }
            reply.setDataPosition(0)
            reply.readException()
            val status = reply.readInt()
            return when (status) {
                REPLY_ACCEPTED_NO_RESPONSE -> TransactOutcome(accepted = true, replyPacket = null)
                REPLY_HAS_RESPONSE_PACKET -> {
                    val respBytes = reply.createByteArray()
                    val respPacket = decodeBoundedPacket(respBytes)
                    TransactOutcome(accepted = true, replyPacket = respPacket)
                }
                else -> TransactOutcome(accepted = false, replyPacket = null)
            }
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun registerCallbackOnRemote(binder: IBinder): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(AndroidServiceIpcProtocol.INTERFACE_DESCRIPTOR)
            data.writeStrongBinder(callbackBinder)
            data.setDataPosition(0)
            val ok = binder.transact(AndroidServiceIpcProtocol.TRANSACT_REGISTER_CALLBACK, data, reply, 0)
            if (!ok) return false
            reply.setDataPosition(0)
            reply.readException()
            reply.readInt() == 1
        } catch (_: RemoteException) {
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun unregisterCallbackOnRemote(binder: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(AndroidServiceIpcProtocol.INTERFACE_DESCRIPTOR)
            data.writeStrongBinder(callbackBinder)
            data.setDataPosition(0)
            binder.transact(AndroidServiceIpcProtocol.TRANSACT_UNREGISTER_CALLBACK, data, reply, 0)
        } catch (_: RemoteException) {
            // Remote binder already disconnected during unbind.
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun handleRemoteLost() {
        val lostBinder = synchronized(stateLock) {
            val prev = remoteBinder
            remoteBinder = null
            prev
        }
        if (lostBinder != null) {
            unlinkDeathSafe(lostBinder)
            inboundAssembler.clear()
            onConnectionStateChangedListener?.invoke(false)
        }
    }

    private fun unlinkDeathSafe(binder: IBinder) {
        try {
            binder.unlinkToDeath(deathRecipient, 0)
        } catch (_: NoSuchElementException) {
            // Death recipient already unlinked or binder finalized.
        }
    }

    private fun decodeBoundedPacket(bytes: ByteArray?): IpcPacket? {
        if (bytes == null || bytes.isEmpty() || bytes.size > config.maxPacketBytes) {
            return null
        }
        return IpcPacket.fromBinaryJsonBytes(bytes)
    }
}
