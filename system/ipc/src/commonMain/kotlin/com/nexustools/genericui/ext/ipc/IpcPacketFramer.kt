package com.nexustools.genericui.ext.ipc

/**
 * Length-prefixed binary frame encoder and [IpcPacket] codec (`[4-byte BE length][BinaryJson body]`).
 */
object IpcPacketFramer {
    const val MAX_PACKET_BYTES: Int = 16 * 1024 * 1024

    fun encodeFrame(binaryBody: ByteArray, maxBytes: Int = MAX_PACKET_BYTES): ByteArray {
        if (maxBytes <= 0) {
            throw IpcException("maxBytes must be > 0, was $maxBytes.")
        }
        if (binaryBody.isEmpty()) {
            throw IpcException("IPC binary frame body must not be empty.")
        }
        if (binaryBody.size > maxBytes) {
            throw IpcException("IPC packet body size ${binaryBody.size} exceeds maxBytes=$maxBytes.")
        }
        val framed = ByteArray(4 + binaryBody.size)
        writeIntBE(framed, 0, binaryBody.size)
        binaryBody.copyInto(framed, destinationOffset = 4)
        return framed
    }

    fun framePacket(packet: IpcPacket, maxBytes: Int = MAX_PACKET_BYTES): ByteArray {
        val body = packet.toBinaryJsonBytes()
        return encodeFrame(body, maxBytes)
    }

    fun decodePacket(binaryBody: ByteArray, maxBytes: Int = MAX_PACKET_BYTES): IpcPacket? {
        if (binaryBody.isEmpty() || binaryBody.size > maxBytes) {
            return null
        }
        return IpcPacket.fromBinaryJsonBytes(binaryBody)
    }

    fun writeIntBE(target: ByteArray, offset: Int, value: Int) {
        if (offset < 0 || offset + 4 > target.size) {
            throw IpcException("writeIntBE out of bounds: offset=$offset, size=${target.size}.")
        }
        target[offset] = ((value ushr 24) and 0xFF).toByte()
        target[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 3] = (value and 0xFF).toByte()
    }

    fun readIntBE(source: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > source.size) {
            throw IpcException("readIntBE out of bounds: offset=$offset, size=${source.size}.")
        }
        return ((source[offset].toInt() and 0xFF) shl 24) or
            ((source[offset + 1].toInt() and 0xFF) shl 16) or
            ((source[offset + 2].toInt() and 0xFF) shl 8) or
            (source[offset + 3].toInt() and 0xFF)
    }
}
