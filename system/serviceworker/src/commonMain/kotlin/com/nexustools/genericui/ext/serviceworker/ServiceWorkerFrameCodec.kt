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
        val hasFragmentRouting = envelope.targetFragmentId != null || envelope.sourceFragmentId != null
        val targetBytes = envelope.targetFragmentId?.encodeToByteArray()
        val sourceBytes = envelope.sourceFragmentId?.encodeToByteArray()
        val routingBytesLen = if (hasFragmentRouting) {
            (4 + (targetBytes?.size ?: 0)) + (4 + (sourceBytes?.size ?: 0))
        } else {
            0
        }
        val totalLen = 1 + 8 +
            4 + clientBytes.size +
            4 + typeBytes.size +
            4 + payloadBytes.size +
            4 + errorBytes.size +
            routingBytesLen
        val out = ByteArray(totalLen)
        var offset = 0
        out[offset++] = envelope.kind
        writeLongBE(out, offset, envelope.correlationId)
        offset += 8
        offset = writeLengthPrefixed(out, offset, clientBytes)
        offset = writeLengthPrefixed(out, offset, typeBytes)
        offset = writeLengthPrefixed(out, offset, payloadBytes)
        offset = writeLengthPrefixed(out, offset, errorBytes)
        if (hasFragmentRouting) {
            offset = writeNullableLengthPrefixed(out, offset, targetBytes)
            writeNullableLengthPrefixed(out, offset, sourceBytes)
        }
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
        val (error, next4) = readLengthPrefixed(body, offset) ?: return null
        offset = next4
        var targetFragmentId: String? = null
        var sourceFragmentId: String? = null
        if (offset < body.size) {
            val decodedTarget = readNullableLengthPrefixed(body, offset) ?: return null
            targetFragmentId = decodedTarget.first
            offset = decodedTarget.second
        }
        if (offset < body.size) {
            val decodedSource = readNullableLengthPrefixed(body, offset) ?: return null
            sourceFragmentId = decodedSource.first
        }
        return ServiceWorkerEnvelope(
            kind = kind,
            correlationId = corrId,
            clientId = clientId,
            typeId = typeId,
            payloadJson = payloadJson,
            error = error,
            targetFragmentId = targetFragmentId,
            sourceFragmentId = sourceFragmentId
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
        if (envelope.targetFragmentId != null) {
            map["tf"] = PreferenceElement.ofString(envelope.targetFragmentId)
        }
        if (envelope.sourceFragmentId != null) {
            map["sf"] = PreferenceElement.ofString(envelope.sourceFragmentId)
        }
        return WEB_PREFIX + PreferenceObjectCodec.elementToJson(PreferenceElement.ofObject(map))
    }

    fun decodeWebText(raw: String): ServiceWorkerEnvelope? {
        if (!raw.startsWith(WEB_PREFIX)) return null
        val json = raw.removePrefix(WEB_PREFIX)
        val elem = try {
            PreferenceObjectCodec.fromJsonElement(json)
        } catch (e: IllegalArgumentException) {
            throw ServiceWorkerIpcException("Malformed Web ServiceWorker frame: ${e.message}", e)
        }
        if (!elem.isObject && !elem.isMap) return null
        val kind = elem["k"]?.asInt(0)?.toByte() ?: return null
        val corrId = elem["c"]?.asLong(0L) ?: 0L
        val cid = elem["cid"]?.asString("") ?: ""
        val typeId = elem["t"]?.asString("") ?: ""
        val payloadJson = elem["p"]?.asString("null") ?: "null"
        val err = elem["e"]?.asString("") ?: ""
        val targetFragId = elem["tf"]?.let { if (it.isNull) null else it.asString() }
        val sourceFragId = elem["sf"]?.let { if (it.isNull) null else it.asString() }
        return ServiceWorkerEnvelope(
            kind = kind,
            correlationId = corrId,
            clientId = cid,
            typeId = typeId,
            payloadJson = payloadJson,
            error = err,
            targetFragmentId = targetFragId,
            sourceFragmentId = sourceFragId
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

    private fun writeNullableLengthPrefixed(buf: ByteArray, offset: Int, bytes: ByteArray?): Int {
        if (bytes == null) {
            writeIntBE(buf, offset, -1)
            return offset + 4
        }
        return writeLengthPrefixed(buf, offset, bytes)
    }

    private fun readLengthPrefixed(buf: ByteArray, offset: Int): Pair<String, Int>? {
        if (offset + 4 > buf.size) return null
        val len = readIntBE(buf, offset)
        val start = offset + 4
        if (len < 0 || start + len > buf.size) return null
        val text = buf.decodeToString(startIndex = start, endIndex = start + len)
        return text to (start + len)
    }

    private fun readNullableLengthPrefixed(buf: ByteArray, offset: Int): Pair<String?, Int>? {
        if (offset + 4 > buf.size) return null
        val len = readIntBE(buf, offset)
        val start = offset + 4
        if (len == -1) {
            return null to start
        }
        if (len < -1 || start + len > buf.size) return null
        val text = buf.decodeToString(startIndex = start, endIndex = start + len)
        return text to (start + len)
    }
}
