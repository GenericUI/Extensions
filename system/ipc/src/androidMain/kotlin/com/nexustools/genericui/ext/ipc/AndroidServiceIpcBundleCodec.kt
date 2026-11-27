package com.nexustools.genericui.ext.ipc

import android.os.Bundle
import android.os.Parcel

/**
 * `BinaryJson` serialization helpers for writing and reading [IpcPacket]s across Android
 * [Parcel]s and [Bundle]s without JSON text conversion.
 */
object AndroidServiceIpcBundleCodec {

    fun writeToParcel(parcel: Parcel, packet: IpcPacket) {
        val bytes = packet.toBinaryJsonBytes()
        if (bytes.size > IpcPacketFramer.MAX_PACKET_BYTES) {
            throw IpcException(
                "Encoded IpcPacket size ${bytes.size} exceeds MAX_PACKET_BYTES=${IpcPacketFramer.MAX_PACKET_BYTES}."
            )
        }
        parcel.writeByteArray(bytes)
    }

    fun readFromParcel(parcel: Parcel): IpcPacket? {
        val bytes = parcel.createByteArray() ?: return null
        if (bytes.isEmpty() || bytes.size > IpcPacketFramer.MAX_PACKET_BYTES) {
            return null
        }
        return IpcPacket.fromBinaryJsonBytes(bytes)
    }

    fun writeToBundle(
        bundle: Bundle,
        packet: IpcPacket,
        key: String = AndroidServiceIpcProtocol.EXTRA_IPC_PACKET_BYTES
    ) {
        val bytes = packet.toBinaryJsonBytes()
        if (bytes.size > IpcPacketFramer.MAX_PACKET_BYTES) {
            throw IpcException(
                "Encoded IpcPacket size ${bytes.size} exceeds MAX_PACKET_BYTES=${IpcPacketFramer.MAX_PACKET_BYTES}."
            )
        }
        bundle.putByteArray(key, bytes)
    }

    fun readFromBundle(
        bundle: Bundle?,
        key: String = AndroidServiceIpcProtocol.EXTRA_IPC_PACKET_BYTES
    ): IpcPacket? {
        if (bundle == null) return null
        val bytes = bundle.getByteArray(key) ?: return null
        if (bytes.isEmpty() || bytes.size > IpcPacketFramer.MAX_PACKET_BYTES) {
            return null
        }
        return IpcPacket.fromBinaryJsonBytes(bytes)
    }
}
