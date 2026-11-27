package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.util.BinaryJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceWorkerCodecBackcompatAndControlPayloadTest {

    @Test
    fun encodesBinaryJsonTypeObjectRejectsRawTextJsonAndDecodesOmittedRoutingFields() {
        val minimalObject = PreferenceElement.ofObject(
            mapOf(
                "kind" to PreferenceElement.ofByte(ServiceWorkerEnvelope.KIND_REQUEST),
                "correlationId" to PreferenceElement.ofLong(42L),
                "clientId" to PreferenceElement.ofString("client-1"),
                "typeId" to PreferenceElement.ofString("test.Minimal"),
                "payload" to PreferenceElement.ofObject(
                    mapOf("hello" to PreferenceElement.ofString("world"))
                ),
                "error" to PreferenceElement.ofString("")
            )
        )
        val minimalBytes = BinaryJson.encodeElement(minimalObject)
        assertEquals(BinaryJson.TYPE_OBJECT, minimalBytes[0])

        val decodedMinimal = ServiceWorkerFrameCodec.decodeBinaryBody(minimalBytes)
        assertNotNull(decodedMinimal)
        assertEquals(ServiceWorkerEnvelope.KIND_REQUEST, decodedMinimal.kind)
        assertEquals(42L, decodedMinimal.correlationId)
        assertEquals("client-1", decodedMinimal.clientId)
        assertEquals("test.Minimal", decodedMinimal.typeId)
        assertEquals("world", decodedMinimal.payload["hello"]?.asString())
        assertEquals("", decodedMinimal.error)
        assertNull(decodedMinimal.targetFragmentId)
        assertNull(decodedMinimal.sourceFragmentId)

        // Raw UTF-8 JSON text must never be accepted by BinaryJson binary frame decoding.
        assertNull(ServiceWorkerFrameCodec.decodeBinaryBody("""{"kind":5,"clientId":"c"}""".encodeToByteArray()))
        assertFailsWith<ServiceWorkerIpcException> {
            ServiceWorkerFrameCodec.decodeWebText("""__gsw_frame__:{"k":1,"c":7}""")
        }
    }

    @Test
    fun roundTripsTargetAndSourceFragmentRoutingAcrossBinaryAndWebCodecs() {
        val cases = listOf(
            ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                correlationId = 101L,
                clientId = "c-a",
                typeId = "t.A",
                payload = PreferenceElement.ofObject(emptyMap()),
                error = "",
                targetFragmentId = "com.example.SettingsFragment",
                sourceFragmentId = null
            ),
            ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_REQUEST,
                correlationId = 202L,
                clientId = "c-b",
                typeId = "t.B",
                payload = PreferenceElement.ofObject(mapOf("v" to PreferenceElement.ofInt(1))),
                error = "",
                targetFragmentId = null,
                sourceFragmentId = "com.example.MainFragment"
            ),
            ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_RESPONSE,
                correlationId = 303L,
                clientId = "c-c",
                typeId = "t.C",
                payload = PreferenceElement.ofBoolean(true),
                error = "",
                targetFragmentId = "com.example.MainFragment",
                sourceFragmentId = "com.example.SettingsFragment"
            )
        )

        for (env in cases) {
            val binaryBytes = ServiceWorkerFrameCodec.encodeBinaryBody(env)
            assertEquals(BinaryJson.TYPE_OBJECT, binaryBytes[0])
            val binDecoded = ServiceWorkerFrameCodec.decodeBinaryBody(binaryBytes)
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
        val decodedLaunch = ServiceWorkerFragmentLaunchRequest.fromControlPayloadElement(
            launchReq.toControlPayloadElement()
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
