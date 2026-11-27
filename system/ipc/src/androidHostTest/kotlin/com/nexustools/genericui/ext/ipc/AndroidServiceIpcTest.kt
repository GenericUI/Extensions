package com.nexustools.genericui.ext.ipc

import android.content.ComponentName
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.DeadObjectException
import android.os.Looper
import android.os.Parcel
import com.nexustools.genericui.component.system.PreferenceElement
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidServiceIpcTest {

    @Test
    fun androidStubReportsUnsupportedAndDoesNotStartOrConnect() {
        assertFalse(IpcExtension.isSupported)
        assertEquals("Android", IpcExtension.backendName)
        IpcExtension.registerProviders()

        val server = IpcServer()
        assertFalse(server.start(channelName = "android-ipc-stub") { })
        assertFalse(server.isRunning)
        assertNull(server.address)
        server.close()

        val clientConn = IpcClient.connect(
            IpcChannelAddress(
                transportKind = IpcChannelAddress.TRANSPORT_UNIX_SOCKET,
                endpointAddress = "/data/local/tmp/unused.sock"
            )
        )
        assertNull(clientConn)
    }

    @Test
    fun bundleCodecRoundTripsBinaryJsonParcelAndBundle() {
        val rawBytes = ByteArray(1024) { ((it * 13) and 0xFF).toByte() }
        val packet = IpcPacket(
            id = 77L,
            type = "android.bundle.roundtrip",
            channel = "ipc-host",
            payload = PreferenceElement.ofBytes(rawBytes),
            replyToId = 12L,
            error = null,
            headers = mapOf("k1" to "v1", "k2" to "v2")
        )

        val parcel = Parcel.obtain()
        try {
            AndroidServiceIpcBundleCodec.writeToParcel(parcel, packet)
            parcel.setDataPosition(0)
            val decoded = AndroidServiceIpcBundleCodec.readFromParcel(parcel)
            assertEquals(packet, decoded)
            assertContentEquals(rawBytes, decoded?.payload?.bytesOrNull())
        } finally {
            parcel.recycle()
        }

        val bundle = Bundle()
        AndroidServiceIpcBundleCodec.writeToBundle(bundle, packet)
        val fromBundle = AndroidServiceIpcBundleCodec.readFromBundle(bundle)
        assertEquals(packet, fromBundle)
        assertContentEquals(rawBytes, fromBundle?.payload?.bytesOrNull())
        assertNull(AndroidServiceIpcBundleCodec.readFromBundle(null))
        assertNull(AndroidServiceIpcBundleCodec.readFromBundle(Bundle()))
    }

    @Test
    fun chunkAssemblerSplitsReassemblesAndRejectsInvalidOrStaleTransfers() {
        var fakeClockMs = 1_000L
        val assembler = AndroidBinderChunkAssembler(
            maxPacketBytes = 2 * 1024 * 1024,
            chunkSizeBytes = AndroidServiceIpcProtocol.MAX_DIRECT_PARCEL_BYTES
        ).apply {
            clockMs = { fakeClockMs }
            staleTransferTtlMs = 5_000L
        }

        val largePayload = ByteArray(1_200_000) { ((it * 7 + 3) and 0xFF).toByte() }
        val chunks = assembler.split(largePayload)
        assertTrue(chunks.size > 4)

        assertTrue(assembler.begin(transferId = 101L, totalBytes = largePayload.size, chunkCount = chunks.size))
        assertFalse(assembler.begin(transferId = 101L, totalBytes = largePayload.size, chunkCount = chunks.size))
        for (i in chunks.indices) {
            assertTrue(assembler.append(transferId = 101L, chunkIndex = i, chunkData = chunks[i]))
        }
        val reassembled = assembler.finish(101L)
        assertNotNull(reassembled)
        assertContentEquals(largePayload, reassembled)
        assertNull(assembler.finish(101L))

        assertFailsWith<IpcException> {
            assembler.split(ByteArray(0))
        }
        assertFailsWith<IpcException> {
            assembler.split(ByteArray(3 * 1024 * 1024))
        }

        assertFalse(assembler.begin(transferId = 202L, totalBytes = 3 * 1024 * 1024, chunkCount = 16))
        assertFalse(assembler.begin(transferId = 203L, totalBytes = 0, chunkCount = 1))

        assertTrue(assembler.begin(transferId = 303L, totalBytes = 64, chunkCount = 2))
        assertFalse(assembler.append(transferId = 303L, chunkIndex = 1, chunkData = ByteArray(32)))
        assertEquals(0, assembler.activeTransferCount())

        assertTrue(assembler.begin(transferId = 404L, totalBytes = 64, chunkCount = 2))
        assertTrue(assembler.append(transferId = 404L, chunkIndex = 0, chunkData = ByteArray(32)))
        assertEquals(1, assembler.activeTransferCount())
        fakeClockMs += 10_000L
        assertEquals(1, assembler.expireStaleTransfers())
        assertEquals(0, assembler.activeTransferCount())
        assertNull(assembler.finish(404L))
    }

    @Test
    fun clientBindsViaServiceConnectionAndHandlesDirectAndChunkedRoundTrips() {
        val app = RuntimeEnvironment.getApplication()
        val receivedOnServer = ArrayList<IpcPacket>()
        val binder = AndroidServiceIpcBinder { incoming ->
            receivedOnServer.add(incoming)
            when (incoming.type) {
                "echo.request" -> IpcPacket(
                    id = incoming.id + 1L,
                    type = "echo.response",
                    channel = incoming.channel,
                    payload = incoming.payload,
                    replyToId = incoming.id,
                    headers = mapOf("handled" to "true")
                )
                "chunked.ack" -> IpcPacket(
                    id = incoming.id + 1L,
                    type = "chunked.ack.reply",
                    channel = incoming.channel,
                    payload = PreferenceElement.ofString("ack:${incoming.payload.bytesOrNull()?.size ?: -1}"),
                    replyToId = incoming.id,
                    headers = mapOf("handled" to "true")
                )
                "fire.and.forget" -> null
                else -> null
            }
        }

        val serviceComponent = ComponentName(app.packageName, "com.nexustools.genericui.ext.ipc.FakeBoundService")
        val serviceIntent = Intent().setComponent(serviceComponent)
        val shadowApp = shadowOf(app)
        shadowApp.setComponentNameAndServiceForBindService(serviceComponent, binder)

        val connectionEvents = ArrayList<Boolean>()
        val clientPushes = ArrayList<IpcPacket>()
        val client = AndroidServiceIpcClient(app).apply {
            setOnConnectionStateChanged { connected -> connectionEvents.add(connected) }
            setOnPacketReceived { packet -> clientPushes.add(packet) }
        }

        assertTrue(client.bind(serviceIntent))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(client.isBound)
        assertTrue(client.isConnected)
        assertTrue(client.ping())
        assertEquals(1, binder.callbackCount())
        assertEquals(listOf(true), connectionEvents)

        val directNotice = IpcPacket(
            id = 10L,
            type = "fire.and.forget",
            channel = "control",
            payload = PreferenceElement.ofBytes(byteArrayOf(1, 2, 3, 4))
        )
        assertTrue(client.sendPacket(directNotice))
        assertEquals(1, receivedOnServer.size)
        assertEquals(directNotice, receivedOnServer.first())

        val echoReq = IpcPacket(
            id = 11L,
            type = "echo.request",
            channel = "rpc",
            payload = PreferenceElement.ofBytes(ByteArray(2048) { (it and 0xFF).toByte() })
        )
        val echoReply = client.requestPacket(echoReq)
        assertNotNull(echoReply)
        assertEquals("echo.response", echoReply.type)
        assertEquals(11L, echoReply.replyToId)
        assertEquals("true", echoReply.headers["handled"])

        // Send a > 240 KB payload (1.1 MB) to force chunked BEGIN/PART/END transactions.
        val hugePayload = ByteArray(1_100_000) { ((it * 19) and 0xFF).toByte() }
        val chunkedNotice = IpcPacket(
            id = 12L,
            type = "fire.and.forget",
            channel = "bulk-upload",
            payload = PreferenceElement.ofBytes(hugePayload),
            headers = mapOf("encoding" to "chunked")
        )
        assertTrue(client.sendPacket(chunkedNotice))
        assertEquals(3, receivedOnServer.size)
        assertContentEquals(hugePayload, receivedOnServer[2].payload.bytesOrNull())

        val largeAckReq = IpcPacket(
            id = 13L,
            type = "chunked.ack",
            channel = "bulk-rpc",
            payload = PreferenceElement.ofBytes(hugePayload)
        )
        val largeAckReply = client.requestPacket(largeAckReq)
        assertEquals(4, receivedOnServer.size)
        assertNotNull(largeAckReply)
        assertEquals("chunked.ack.reply", largeAckReply.type)
        assertEquals(13L, largeAckReply.replyToId)
        assertEquals(PreferenceElement.ofString("ack:${hugePayload.size}"), largeAckReply.payload)

        // Async pushes from Service -> Client (both direct and > 240 KB chunked).
        val directPush = IpcPacket(
            id = 200L,
            type = "push.direct",
            payload = PreferenceElement.ofString("hello-client")
        )
        assertTrue(binder.dispatchToClient(directPush))

        val hugePushBytes = ByteArray(900_000) { ((it * 29 + 5) and 0xFF).toByte() }
        val chunkedPush = IpcPacket(
            id = 201L,
            type = "push.chunked",
            payload = PreferenceElement.ofBytes(hugePushBytes)
        )
        assertEquals(1, binder.broadcastPacket(chunkedPush))

        assertEquals(2, clientPushes.size)
        assertEquals(directPush, clientPushes[0])
        assertContentEquals(hugePushBytes, clientPushes[1].payload.bytesOrNull())

        client.unbind()
        assertFalse(client.isConnected)
        assertFalse(client.isBound)
        assertEquals(0, binder.callbackCount())
        assertEquals(listOf(true, false), connectionEvents)
    }

    @Test
    fun binderPrunesDeadCallbacksAndSurvivesMultiClientAttach() {
        val app = RuntimeEnvironment.getApplication()
        val binder = AndroidServiceIpcBinder()

        val healthyClient = AndroidServiceIpcClient(app)
        val received = ArrayList<IpcPacket>()
        healthyClient.setOnPacketReceived { received.add(it) }
        assertTrue(healthyClient.attachBinder(binder))
        assertEquals(1, binder.callbackCount())

        val dyingCallback = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                throw DeadObjectException("Simulated dead binder during callback push")
            }
        }
        assertTrue(binder.registerCallback(dyingCallback))
        assertEquals(2, binder.callbackCount())

        val broadcast = IpcPacket(
            id = 500L,
            type = "broadcast.healthcheck",
            payload = PreferenceElement.ofString("ok")
        )
        val delivered = binder.broadcastPacket(broadcast)
        assertEquals(1, delivered)
        assertEquals(1, binder.callbackCount())
        assertEquals(listOf(broadcast), received)

        healthyClient.unbind()
        assertEquals(0, binder.callbackCount())
    }
}
