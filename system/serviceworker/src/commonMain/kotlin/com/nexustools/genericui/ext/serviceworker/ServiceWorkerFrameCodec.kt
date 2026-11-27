package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.util.BinaryJson
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * `BinaryJson`-backed envelope encoder/decoder for [ServiceWorkerEnvelope].
 */
internal object ServiceWorkerFrameCodec {

    private const val WEB_PREFIX: String = "__gsw_frame__:"
    const val MAX_FRAME_BYTES: Int = 16 * 1024 * 1024

    fun encodeBinaryBody(envelope: ServiceWorkerEnvelope): ByteArray {
        val body = BinaryJson.encodeElement(envelope.toPreferenceElement())
        if (body.size > MAX_FRAME_BYTES) {
            throw ServiceWorkerIpcException(
                "ServiceWorker envelope size (${body.size} bytes) exceeds maximum ($MAX_FRAME_BYTES bytes)."
            )
        }
        return body
    }

    fun decodeBinaryBody(body: ByteArray): ServiceWorkerEnvelope? {
        if (body.isEmpty() || body.size > MAX_FRAME_BYTES) return null
        val elem = BinaryJson.decodeElementOrNull(body) ?: return null
        return ServiceWorkerEnvelope.fromPreferenceElement(elem)
    }

    fun encodeLengthPrefixedFrame(envelope: ServiceWorkerEnvelope): ByteArray {
        val body = encodeBinaryBody(envelope)
        val framed = ByteArray(4 + body.size)
        writeIntBE(framed, 0, body.size)
        body.copyInto(framed, destinationOffset = 4)
        return framed
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun encodeWebText(envelope: ServiceWorkerEnvelope): String {
        val body = encodeBinaryBody(envelope)
        val encoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(body)
        return WEB_PREFIX + encoded
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decodeWebText(raw: String): ServiceWorkerEnvelope? {
        if (!raw.startsWith(WEB_PREFIX)) return null
        val b64 = raw.removePrefix(WEB_PREFIX)
        if (b64.isEmpty()) {
            throw ServiceWorkerIpcException("Malformed Web ServiceWorker frame: empty payload.")
        }
        val bytes = try {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(b64)
        } catch (e: IllegalArgumentException) {
            throw ServiceWorkerIpcException("Malformed Web ServiceWorker frame: ${e.message}", e)
        }
        return decodeBinaryBody(bytes)
            ?: throw ServiceWorkerIpcException("Malformed Web ServiceWorker BinaryJson envelope.")
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
}
