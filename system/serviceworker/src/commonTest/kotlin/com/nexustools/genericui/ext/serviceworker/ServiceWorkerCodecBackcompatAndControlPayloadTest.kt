package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.fragment.FragmentBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceWorkerCodecBackcompatAndControlPayloadTest {

    private fun buildLegacyFourFieldBinaryBody(
        kind: Byte,
        correlationId: Long,
        clientId: String,
        typeId: String,
        payloadJson: String,
        error: String
    ): ByteArray {
        val parts = listOf(
            clientId.encodeToByteArray(),
            typeId.encodeToByteArray(),
            payloadJson.encodeToByteArray(),
            error.encodeToByteArray()
        )
        val total = 1 + 8 + parts.sumOf { 4 + it.size }
        val out = ByteArray(total)
        var offset = 0
        out[offset++] = kind
        for (shift in 56 downTo 0 step 8) {
            out[offset++] = ((correlationId ushr shift) and 0xFF).toByte()
        }
        for (part in parts) {
            val len = part.size
            out[offset++] = ((len ushr 24) and 0xFF).toByte()
            out[offset++] = ((len ushr 16) and 0xFF).toByte()
            out[offset++] = ((len ushr 8) and 0xFF).toByte()
            out[offset++] = (len and 0xFF).toByte()
            part.copyInto(out, destinationOffset = offset)
            offset += len
        }
        return out
    }

    @Test
    fun decodesLegacyFourFieldBinaryAndWebFramesWithNullFragmentRouting() {
        val legacyBody = buildLegacyFourFieldBinaryBody(
            kind = ServiceWorkerEnvelope.KIND_REQUEST,
            correlationId = 42L,
            clientId = "client-1",
            typeId = "test.Legacy",
            payloadJson = """{"hello":"world"}""",
            error = ""
        )
        val decodedLegacy = ServiceWorkerFrameCodec.decodeBinaryBody(legacyBody)
        assertNotNull(decodedLegacy)
        assertEquals(ServiceWorkerEnvelope.KIND_REQUEST, decodedLegacy.kind)
        assertEquals(42L, decodedLegacy.correlationId)
        assertEquals("client-1", decodedLegacy.clientId)
        assertEquals("test.Legacy", decodedLegacy.typeId)
        assertEquals("""{"hello":"world"}""", decodedLegacy.payloadJson)
        assertEquals("", decodedLegacy.error)
        assertNull(decodedLegacy.targetFragmentId)
        assertNull(decodedLegacy.sourceFragmentId)

        val legacyWebText =
            """__gsw_frame__:{"k":1,"c":7,"cid":"web-1","t":"kotlin.String","p":"\"ping\"","e":""}"""
        val decodedWeb = ServiceWorkerFrameCodec.decodeWebText(legacyWebText)
        assertNotNull(decodedWeb)
        assertEquals("web-1", decodedWeb.clientId)
        assertEquals("kotlin.String", decodedWeb.typeId)
        assertNull(decodedWeb.targetFragmentId)
        assertNull(decodedWeb.sourceFragmentId)
    }

    @Test
    fun roundTripsTargetAndSourceFragmentRoutingAcrossBinaryAndWebCodecs() {
        val cases = listOf(
            ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                correlationId = 101L,
                clientId = "c-a",
                typeId = "t.A",
                payloadJson = "{}",
                error = "",
                targetFragmentId = "com.example.SettingsFragment",
                sourceFragmentId = null
            ),
            ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_REQUEST,
                correlationId = 202L,
                clientId = "c-b",
                typeId = "t.B",
                payloadJson = """{"v":1}""",
                error = "",
                targetFragmentId = null,
                sourceFragmentId = "com.example.MainFragment"
            ),
            ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_RESPONSE,
                correlationId = 303L,
                clientId = "c-c",
                typeId = "t.C",
                payloadJson = "true",
                error = "",
                targetFragmentId = "com.example.MainFragment",
                sourceFragmentId = "com.example.SettingsFragment"
            )
        )

        for (env in cases) {
            val binDecoded = ServiceWorkerFrameCodec.decodeBinaryBody(
                ServiceWorkerFrameCodec.encodeBinaryBody(env)
            )
            assertEquals(env, binDecoded)

            val webDecoded = ServiceWorkerFrameCodec.decodeWebText(
                ServiceWorkerFrameCodec.encodeWebText(env)
            )
            assertEquals(env, webDecoded)
        }
    }

    @Test
    fun roundTripsFragmentControlPayloadsIncludingArgumentsBundle() {
        val args = FragmentBundle.of(
            "section" to "audio",
            "volume" to 85,
            "muted" to false
        )
        val launchReq = ServiceWorkerFragmentLaunchRequest(
            fragmentId = "com.example.SettingsFragment",
            route = "/settings",
            arguments = args,
            openAdditionalWindow = true,
            clearTask = false,
            bringToFront = true
        )
        val decodedLaunch = ServiceWorkerFragmentLaunchRequest.fromControlPayloadJson(
            launchReq.toControlPayloadJson()
        )
        assertNotNull(decodedLaunch)
        assertEquals("com.example.SettingsFragment", decodedLaunch.fragmentId)
        assertEquals("/settings", decodedLaunch.route)
        assertEquals(true, decodedLaunch.openAdditionalWindow)
        assertEquals(false, decodedLaunch.clearTask)
        assertEquals(true, decodedLaunch.bringToFront)
        assertEquals("audio", decodedLaunch.arguments?.getString("section"))
        assertEquals(85, decodedLaunch.arguments?.getInt("volume"))
        assertEquals(false, decodedLaunch.arguments?.getBoolean("muted", true))

        val closePayload = ServiceWorkerFragmentLaunchRequest.encodeCloseControlPayload(
            fragmentId = "com.example.SettingsFragment",
            instanceId = "inst-9"
        )
        val decodedClose = ServiceWorkerFragmentLaunchRequest.decodeCloseControlPayload(closePayload)
        assertEquals("com.example.SettingsFragment" to "inst-9", decodedClose)

        val mountPayload = ServiceWorkerFragmentLaunchRequest.encodeMountControlPayload(
            fragmentId = "com.example.SettingsFragment",
            instanceId = "inst-9",
            route = "/settings"
        )
        val decodedMount = ServiceWorkerFragmentLaunchRequest.decodeMountControlPayload(mountPayload)
        assertNotNull(decodedMount)
        assertTrue(decodedMount.first == "com.example.SettingsFragment")
        assertEquals("inst-9", decodedMount.second)
        assertEquals("/settings", decodedMount.third)
    }
}
