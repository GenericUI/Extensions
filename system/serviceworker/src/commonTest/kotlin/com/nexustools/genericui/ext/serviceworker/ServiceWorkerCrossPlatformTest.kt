package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.system.PreferenceElement
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceWorkerCrossPlatformTest {

    enum class TestMode {
        FAST,
        ACCURATE
    }

    data class NestedInfo(val code: Int, val note: String?)

    data class SampleMessage(
        val id: Long,
        val text: String,
        val ratio: Double,
        val mode: TestMode,
        val tags: List<String>,
        val attributes: Map<String, Int>,
        val nested: NestedInfo?
    )

    @Test
    fun codecRegistryAndEnvelopeFramingRoundTripAcrossAllTargets() {
        val nestedCodec = object : ServiceWorkerMessageCodec<NestedInfo> {
            override val typeId: String = "test.NestedInfo"
            override val messageClass = NestedInfo::class
            override fun encode(value: NestedInfo): PreferenceElement = PreferenceElement.ofObject(
                mapOf(
                    "code" to PreferenceElement.ofInt(value.code),
                    "note" to (value.note?.let { PreferenceElement.ofString(it) } ?: PreferenceElement.Null)
                )
            )

            override fun decode(element: PreferenceElement): NestedInfo {
                val noteEl = element.mapEntries["note"]
                val note = if (noteEl == null || noteEl.isNull) null else noteEl.asString()
                return NestedInfo(
                    code = element.mapEntries["code"]?.asInt() ?: 0,
                    note = note
                )
            }
        }
        val sampleCodec = object : ServiceWorkerMessageCodec<SampleMessage> {
            override val typeId: String = "test.SampleMessage"
            override val messageClass = SampleMessage::class
            override fun encode(value: SampleMessage): PreferenceElement = PreferenceElement.ofObject(
                mapOf(
                    "id" to PreferenceElement.ofLong(value.id),
                    "text" to PreferenceElement.ofString(value.text),
                    "ratio" to PreferenceElement.ofDouble(value.ratio),
                    "mode" to PreferenceElement.ofString(value.mode.name),
                    "tags" to PreferenceElement.ofArray(value.tags.map { PreferenceElement.ofString(it) }),
                    "attributes" to PreferenceElement.ofMap(
                        value.attributes.mapValues { (_, v) -> PreferenceElement.ofInt(v) }
                    ),
                    "nested" to (value.nested?.let { nestedCodec.encode(it) } ?: PreferenceElement.Null)
                )
            )

            override fun decode(element: PreferenceElement): SampleMessage {
                val entries = element.mapEntries
                val tags = entries["tags"]?.arrayElements.orEmpty().map { it.asString().orEmpty() }
                val attrs = entries["attributes"]?.mapEntries.orEmpty().mapValues { (_, v) -> v.asInt() }
                val nestedEl = entries["nested"]
                val nested = if (nestedEl != null && !nestedEl.isNull) nestedCodec.decode(nestedEl) else null
                return SampleMessage(
                    id = entries["id"]?.asLong() ?: 0L,
                    text = entries["text"]?.asString().orEmpty(),
                    ratio = entries["ratio"]?.asDouble() ?: 0.0,
                    mode = TestMode.valueOf(entries["mode"]?.asString() ?: TestMode.FAST.name),
                    tags = tags,
                    attributes = attrs,
                    nested = nested
                )
            }
        }

        ServiceWorkerCodecRegistry.register(nestedCodec)
        ServiceWorkerCodecRegistry.register(sampleCodec)
        try {
            val original = SampleMessage(
                id = 9876543210L,
                text = "hello\nworker|frame",
                ratio = 0.75,
                mode = TestMode.ACCURATE,
                tags = listOf("alpha", "beta"),
                attributes = mapOf("k1" to 42, "k2" to 99),
                nested = NestedInfo(code = 7, note = null)
            )
            val (typeId, json) = ServiceWorkerCodecRegistry.encodePayload(original)
            assertEquals("test.SampleMessage", typeId)
            val decoded = ServiceWorkerCodecRegistry.decodePayload(typeId, json)
            assertEquals(original, decoded)

            val envelope = ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_REQUEST,
                correlationId = 17L,
                clientId = "c-1",
                typeId = typeId,
                payloadJson = json,
                error = ""
            )
            val wireBytes = ServiceWorkerFrameCodec.encodeLengthPrefixedFrame(envelope)
            assertTrue(wireBytes.size > 4)
            val bodyBytes = wireBytes.copyOfRange(4, wireBytes.size)
            val roundTripped = ServiceWorkerFrameCodec.decodeBinaryBody(bodyBytes)
            assertNotNull(roundTripped)
            assertEquals(envelope, roundTripped)

            val webText = ServiceWorkerFrameCodec.encodeWebText(envelope)
            assertEquals(envelope, ServiceWorkerFrameCodec.decodeWebText(webText))
            assertNull(ServiceWorkerFrameCodec.decodeWebText("not-a-valid-frame"))
        } finally {
            ServiceWorkerCodecRegistry.unregister("test.SampleMessage")
            ServiceWorkerCodecRegistry.unregister("test.NestedInfo")
        }
    }

    @Test
    fun primitiveRoundTripsWorkWithoutCustomCodecs() {
        val inputs: List<Any?> = listOf(Unit, null, "text", 123, 4567890123L, 1.5f, 2.25, true, byteArrayOf(1, 2, 3))
        for (input in inputs) {
            val (typeId, json) = ServiceWorkerCodecRegistry.encodePayload(input)
            val decoded = ServiceWorkerCodecRegistry.decodePayload(typeId, json)
            if (input is ByteArray && decoded is ByteArray) {
                assertContentEquals(input, decoded)
            } else {
                assertEquals(input, decoded)
            }
        }
    }
}
