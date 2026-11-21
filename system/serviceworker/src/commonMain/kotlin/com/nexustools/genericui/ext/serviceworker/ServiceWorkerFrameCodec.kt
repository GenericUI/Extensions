package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceObjectCodec

/**
 * Compact length-prefixed binary & Web envelope encoder/decoder for [ServiceWorkerEnvelope].
 */
internal object ServiceWorkerFrameCodec {

    private const val WEB_PREFIX: String = "__gsw_frame__:"
    const val MAX_FRAME_BYTES: Int = 16 * 1024 * 1024

    fun encodeBinaryBody(envelope: ServiceWorkerEnvelope): ByteArray {
        val clientBytes = envelope.clientId.encodeToByteArray()
        val typeBytes = envelope.typeId.encodeToByteArray()
        val payloadBytes = envelope.payloadJson.encodeToByteArray()
        val errorBytes = envelope.error.encodeToByteArray()
        val totalLen = 1 + 8 + 4 + clientBytes.size + 4 + typeBytes.size + 4 + payloadBytes.size + 4 + errorBytes.size
        val out = ByteArray(totalLen)
        var offset = 0
        out[offset++] = envelope.kind
        writeLongBE(out, offset, envelope.correlationId)
        offset += 8
        offset = writeLengthPrefixed(out, offset, clientBytes)
        offset = writeLengthPrefixed(out, offset, typeBytes)
        offset = writeLengthPrefixed(out, offset, payloadBytes)
        writeLengthPrefixed(out, offset, errorBytes)
        return out
    }

    fun decodeBinaryBody(body: ByteArray): ServiceWorkerEnvelope? {
        if (body.size < 1 + 8 + 16) return null
        var offset = 0
        val kind = body[offset++]
        val corrId = readLongBE(body, offset)
        offset += 8
        val (clientId, next1) = readLengthPrefixed(body, offset) ?: return null
        offset = next1
        val (typeId, next2) = readLengthPrefixed(body, offset) ?: return null
        offset = next2
        val (payloadJson, next3) = readLengthPrefixed(body, offset) ?: return null
        offset = next3
        val (error, _) = readLengthPrefixed(body, offset) ?: return null
        return ServiceWorkerEnvelope(
            kind = kind,
            correlationId = corrId,
            clientId = clientId,
            typeId = typeId,
            payloadJson = payloadJson,
            error = error
        )
    }

    fun encodeLengthPrefixedFrame(envelope: ServiceWorkerEnvelope): ByteArray {
        val body = encodeBinaryBody(envelope)
        val framed = ByteArray(4 + body.size)
        writeIntBE(framed, 0, body.size)
        body.copyInto(framed, destinationOffset = 4)
        return framed
    }

    fun encodeWebText(envelope: ServiceWorkerEnvelope): String {
        val map = linkedMapOf(
            "k" to PreferenceElement.ofInt(envelope.kind.toInt()),
            "c" to PreferenceElement.ofLong(envelope.correlationId),
            "cid" to PreferenceElement.ofString(envelope.clientId),
            "t" to PreferenceElement.ofString(envelope.typeId),
            "p" to PreferenceElement.ofString(envelope.payloadJson),
            "e" to PreferenceElement.ofString(envelope.error)
        )
        return WEB_PREFIX + PreferenceObjectCodec.elementToJson(PreferenceElement.ofObject(map))
    }

    fun decodeWebText(raw: String): ServiceWorkerEnvelope? {
        if (!raw.startsWith(WEB_PREFIX)) return null
        val json = raw.removePrefix(WEB_PREFIX)
        val elem = runCatching { PreferenceObjectCodec.fromJsonElement(json) }.getOrNull() ?: return null
        if (!elem.isObject && !elem.isMap) return null
        val kind = elem["k"]?.asInt(0)?.toByte() ?: return null
        val corrId = elem["c"]?.asLong(0L) ?: 0L
        val cid = elem["cid"]?.asString("") ?: ""
        val typeId = elem["t"]?.asString("") ?: ""
        val payloadJson = elem["p"]?.asString("null") ?: "null"
        val err = elem["e"]?.asString("") ?: ""
        return ServiceWorkerEnvelope(
            kind = kind,
            correlationId = corrId,
            clientId = cid,
            typeId = typeId,
            payloadJson = payloadJson,
            error = err
        )
    }

    fun writeIntBE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }

    fun readIntBE(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 24) or
            ((buf[offset + 1].toInt() and 0xFF) shl 16) or
            ((buf[offset + 2].toInt() and 0xFF) shl 8) or
            (buf[offset + 3].toInt() and 0xFF)

    private fun writeLongBE(buf: ByteArray, offset: Int, value: Long) {
        for (i in 0..7) {
            val shift = (7 - i) * 8
            buf[offset + i] = ((value ushr shift) and 0xFFL).toByte()
        }
    }

    private fun readLongBE(buf: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0..7) {
            result = (result shl 8) or (buf[offset + i].toLong() and 0xFFL)
        }
        return result
    }

    private fun writeLengthPrefixed(buf: ByteArray, offset: Int, bytes: ByteArray): Int {
        writeIntBE(buf, offset, bytes.size)
        bytes.copyInto(buf, destinationOffset = offset + 4)
        return offset + 4 + bytes.size
    }

    private fun readLengthPrefixed(buf: ByteArray, offset: Int): Pair<String, Int>? {
        if (offset + 4 > buf.size) return null
        val len = readIntBE(buf, offset)
        val start = offset + 4
        if (len < 0 || start + len > buf.size) return null
        val text = buf.decodeToString(startIndex = start, endIndex = start + len)
        return text to (start + len)
    }
}
