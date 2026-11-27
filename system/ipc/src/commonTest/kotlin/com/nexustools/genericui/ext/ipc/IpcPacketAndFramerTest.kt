package com.nexustools.genericui.ext.ipc

import com.nexustools.genericui.component.system.DataClassCodecRegistry
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceElementKind
import com.nexustools.genericui.util.BinaryJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IpcPacketAndFramerTest {

    @Test
    fun packetEncodesAsBinaryJsonObjectAndRoundTripsFieldsAndBytesPayload() {
        IpcExtension.registerProviders()
        assertTrue(DataClassCodecRegistry.hasCodec<IpcPacket>())

        val binaryPayload = ByteArray(4096) { ((it * 37) and 0xFF).toByte() }
        val packet = IpcPacket(
            id = 9001L,
            type = "render.frame",
            channel = "gpu-main",
            payload = PreferenceElement.ofBytes(binaryPayload),
            replyToId = 123L,
            error = "warn:partial",
            headers = mapOf("origin" to "renderer", "trace" to "abc-123")
        )

        val element = packet.toPreferenceElement()
        assertEquals(PreferenceElementKind.OBJECT, element.kind)

        val encodedBytes = packet.toBinaryJsonBytes()
        assertEquals(BinaryJson.TYPE_OBJECT, encodedBytes[0])
        assertTrue(BinaryJson.isBinaryJson(encodedBytes))

        val decoded = IpcPacket.fromBinaryJsonBytes(encodedBytes)
        assertNotNull(decoded)
        assertEquals(packet, decoded)
        assertEquals(PreferenceElementKind.BYTES, decoded.payload.kind)
        assertTrue(binaryPayload.contentEquals(decoded.payload.bytesOrNull()))
        assertEquals(123L, decoded.replyToId)
        assertEquals("warn:partial", decoded.error)
        assertEquals(mapOf("origin" to "renderer", "trace" to "abc-123"), decoded.headers)

        val codecEncoded = BinaryJson.encodeDataClass(packet)
        assertEquals(BinaryJson.TYPE_OBJECT, codecEncoded[0])
        val codecDecoded = BinaryJson.decodeDataClass<IpcPacket>(codecEncoded)
        assertEquals(packet, codecDecoded)
    }

    @Test
    fun packetRoundTripsNullErrorNullReplyToIdAndEmptyBytes() {
        val packet = IpcPacket(
            id = 0L,
            type = "ping",
            channel = "",
            payload = PreferenceElement.ofBytes(ByteArray(0)),
            replyToId = null,
            error = null,
            headers = emptyMap()
        )
        val framed = IpcPacketFramer.framePacket(packet)
        val bodyLen = IpcPacketFramer.readIntBE(framed, 0)
        assertEquals(framed.size - 4, bodyLen)
        val body = framed.copyOfRange(4, framed.size)
        val decoded = IpcPacketFramer.decodePacket(body)
        assertEquals(packet, decoded)
        assertTrue(decoded?.payload?.bytesOrNull()?.isEmpty() == true)
    }

    @Test
    fun framerRejectsOversizeEmptyAndTruncatedFramesAndValidatesIntBounds() {
        val smallPacket = IpcPacket(
            id = 1L,
            type = "oversize-test",
            payload = PreferenceElement.ofBytes(ByteArray(512) { 0x5A })
        )
        assertFailsWith<IpcException> {
            IpcPacketFramer.framePacket(smallPacket, maxBytes = 128)
        }
        assertFailsWith<IpcException> {
            IpcPacketFramer.encodeFrame(ByteArray(0))
        }
        assertFailsWith<IpcException> {
            IpcPacketFramer.encodeFrame(ByteArray(64), maxBytes = 32)
        }

        val validBytes = smallPacket.toBinaryJsonBytes()
        val truncated = validBytes.copyOf(validBytes.size - 5)
        assertNull(IpcPacketFramer.decodePacket(truncated))
        assertNull(IpcPacketFramer.decodePacket(ByteArray(0)))
        assertNull(IpcPacket.fromBinaryJsonBytes("""{"type":"raw-json-text"}""".encodeToByteArray()))
        assertNull(IpcPacket.fromPreferenceElement(PreferenceElement.ofObject(mapOf("id" to PreferenceElement.ofLong(1L)))))

        val intBuf = ByteArray(4)
        IpcPacketFramer.writeIntBE(intBuf, 0, 0x12345678)
        assertEquals(0x12345678, IpcPacketFramer.readIntBE(intBuf, 0))
        assertFailsWith<IpcException> {
            IpcPacketFramer.writeIntBE(intBuf, 1, 1)
        }
        assertFailsWith<IpcException> {
            IpcPacketFramer.readIntBE(intBuf, 2)
        }
        assertFalse(BinaryJson.isBinaryJson(truncated))
    }
}
