@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.ipc

import com.nexustools.genericui.component.system.PreferenceElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.atomics.AtomicInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopIpcRoundTripTest {

    @Test
    fun desktopServerAndClientExchangeMultiplePacketsConcurrentClientsAndLargeBytesPayload() = runIpcBlockingTest {
        IpcExtension.registerProviders()
        assertTrue(IpcExtension.isSupported)

        val tempDir = DesktopIpcTestTempDir.createTempDir("gui-ipc-rt-${Random.nextInt(100_000, 999_999)}")
        val server = IpcServer()
        val serverConnections = mutableListOf<IpcConnection>()
        val acceptedCount = AtomicInt(0)
        val channelName = "roundtrip-${Random.nextInt(100_000, 999_999)}"

        try {
            assertTrue(
                server.start(channelName, tempDir) { conn ->
                    serverConnections.add(conn)
                    acceptedCount.fetchAndAdd(1)
                    while (!conn.isClosed) {
                        val req = conn.receivePacket(timeoutMs = 250) ?: continue
                        when (req.type) {
                            "echo" -> {
                                conn.sendPacket(
                                    IpcPacket(
                                        id = req.id + 1000L,
                                        type = "echo-reply",
                                        channel = req.channel,
                                        payload = req.payload,
                                        replyToId = req.id,
                                        headers = mapOf("server" to "ok")
                                    )
                                )
                            }
                            "close-me" -> {
                                conn.sendPacket(IpcPacket(id = req.id + 1L, type = "bye", replyToId = req.id))
                                conn.close()
                                break
                            }
                        }
                    }
                },
                "IpcServer.start must succeed on desktop"
            )
            assertTrue(server.isRunning)
            val addr = assertNotNull(server.address)

            // 1. Multiple sequential packets + idle timeout behavior + >1 MB BYTES payload
            val client = withTimeout(5_000L) {
                IpcClient.connect(addr, timeoutMs = 4_000)
            }
            assertNotNull(client, "IpcClient.connect must succeed")
            assertEquals(IpcConnectionState.OPEN, client.state)
            assertFalse(client.isClosed)

            try {
                // Idle poll with no incoming data must return null without closing connection
                assertNull(client.receivePacket(timeoutMs = 40))
                assertFalse(client.isClosed)
                assertEquals(IpcConnectionState.OPEN, client.state)

                for (i in 1..5) {
                    assertTrue(
                        client.sendPacket(
                            IpcPacket(
                                id = i.toLong(),
                                type = "echo",
                                channel = "seq",
                                payload = PreferenceElement.ofString("msg-$i"),
                                headers = mapOf("seq" to i.toString())
                            )
                        )
                    )
                    val reply = withTimeout(5_000L) {
                        var pkt: IpcPacket? = null
                        while (pkt == null) {
                            pkt = client.receivePacket(timeoutMs = 500)
                        }
                        pkt
                    }
                    assertEquals("echo-reply", reply.type)
                    assertEquals(i.toLong(), reply.replyToId)
                    assertEquals("msg-$i", reply.payload.asString())
                    assertEquals("ok", reply.headers["server"])
                }

                // > 1 MB raw BYTES payload roundtrip
                val largeBytes = ByteArray(1_200_000) { ((it * 17 + 3) and 0xFF).toByte() }
                assertTrue(
                    client.sendPacket(
                        IpcPacket(
                            id = 99L,
                            type = "echo",
                            channel = "blob",
                            payload = PreferenceElement.ofBytes(largeBytes)
                        )
                    )
                )
                val largeReply = withTimeout(8_000L) {
                    var pkt: IpcPacket? = null
                    while (pkt == null) {
                        pkt = client.receivePacket(timeoutMs = 500)
                    }
                    pkt
                }
                assertEquals(99L, largeReply.replyToId)
                val echoedBytes = assertNotNull(largeReply.payload.bytesOrNull())
                assertEquals(largeBytes.size, echoedBytes.size)
                assertTrue(largeBytes.contentEquals(echoedBytes))

                // Server closes connection cleanly -> client sees CLOSED state on EOF
                assertTrue(client.sendPacket(IpcPacket(id = 100L, type = "close-me")))
                val bye = withTimeout(5_000L) {
                    var pkt: IpcPacket? = null
                    while (pkt == null) {
                        pkt = client.receivePacket(timeoutMs = 500)
                    }
                    pkt
                }
                assertEquals("bye", bye.type)
                withTimeout(5_000L) {
                    while (!client.isClosed) {
                        client.receivePacket(timeoutMs = 100)
                    }
                }
                assertEquals(IpcConnectionState.CLOSED, client.state)
            } finally {
                client.close()
                assertEquals(IpcConnectionState.CLOSED, client.state)
                assertTrue(client.isClosed)
            }

            // 2. Concurrent clients exchanging packets simultaneously
            val concurrentResults = withTimeout(10_000L) {
                (1..4).map { idx ->
                    async(Dispatchers.IO) {
                        val c = IpcClient.connect(addr, timeoutMs = 4_000)
                        assertNotNull(c, "Concurrent client $idx must connect")
                        try {
                            assertTrue(
                                c.sendPacket(
                                    IpcPacket(
                                        id = idx.toLong(),
                                        type = "echo",
                                        channel = "conc",
                                        payload = PreferenceElement.ofInt(idx * 11)
                                    )
                                )
                            )
                            var reply: IpcPacket? = null
                            while (reply == null) {
                                reply = c.receivePacket(timeoutMs = 500)
                            }
                            reply.payload.asInt()
                        } finally {
                            c.close()
                        }
                    }
                }.awaitAll()
            }
            assertEquals(listOf(11, 22, 33, 44), concurrentResults)

            // 3. Server close transitions state and refuses new connections
            server.close()
            assertFalse(server.isRunning)
            assertNull(server.address)
            assertNull(IpcClient.connect(addr, timeoutMs = 200))
        } finally {
            serverConnections.forEach { it.close() }
            server.close()
            DesktopIpcTestTempDir.deleteTempDir(tempDir)
        }
    }
}
