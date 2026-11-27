package com.nexustools.genericui.ext.ipc

import com.nexustools.genericui.component.system.PreferenceElement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmIpcTransportTest {

    @Test
    fun jvmUnixSocketServerAndClientSupportShortenedLongPathsAndLargePayloads() = runIpcBlockingTest {
        val baseDir = Files.createTempDirectory("gui-jvm-unix").toFile()
        try {
            val longRuntimeDir = File(
                baseDir,
                "alpha-0123456789/beta-0123456789/gamma-0123456789/delta-0123456789/epsilon-0123456789/runtime"
            )
            assertTrue(longRuntimeDir.mkdirs())
            assertTrue(longRuntimeDir.absolutePath.toByteArray(Charsets.UTF_8).size > 120)

            val driver = PlatformIpcServerDriver()
            val receivedPayload = CompletableDeferred<ByteArray>()
            assertTrue(
                driver.startWithTransport(
                    transportKind = IpcChannelAddress.TRANSPORT_UNIX_SOCKET,
                    channelName = "jvm-unix-long",
                    runtimeDir = longRuntimeDir.absolutePath
                ) { conn ->
                    try {
                        val req = conn.receivePacket(timeoutMs = 4_000)
                        if (req != null) {
                            req.payload.bytesOrNull()?.let { bytes ->
                                if (!receivedPayload.isCompleted) {
                                    receivedPayload.complete(bytes)
                                }
                            }
                            conn.sendPacket(
                                IpcPacket(
                                    id = req.id + 1L,
                                    type = "unix-ack",
                                    payload = req.payload,
                                    replyToId = req.id
                                )
                            )
                        }
                    } finally {
                        conn.close()
                    }
                }
            )
            val addr = assertNotNull(driver.address)
            assertEquals(IpcChannelAddress.TRANSPORT_UNIX_SOCKET, addr.transportKind)
            assertTrue(addr.endpointAddress.toByteArray(Charsets.UTF_8).size <= 96)
            val sockFile = File(addr.endpointAddress)
            assertTrue(sockFile.exists())

            try {
                val client = IpcClient.connect(addr, timeoutMs = 4_000)
                assertNotNull(client)
                try {
                    val payload = ByteArray(1_100_000) { ((it * 13) and 0xFF).toByte() }
                    assertTrue(
                        client.sendPacket(
                            IpcPacket(
                                id = 7L,
                                type = "blob",
                                payload = PreferenceElement.ofBytes(payload)
                            )
                        )
                    )
                    val serverGot = withTimeout(5_000L) { receivedPayload.await() }
                    assertTrue(payload.contentEquals(serverGot))

                    val reply = withTimeout(5_000L) {
                        var p: IpcPacket? = null
                        while (p == null) {
                            p = client.receivePacket(timeoutMs = 500)
                        }
                        p
                    }
                    assertEquals(7L, reply.replyToId)
                    assertTrue(payload.contentEquals(reply.payload.bytesOrNull()))
                } finally {
                    client.close()
                }
            } finally {
                driver.close()
            }
            assertFalse(sockFile.exists(), "Closing Unix socket server must delete socket file")
            assertNull(IpcClient.connect(addr, timeoutMs = 200))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun jvmWindowsNamedPipeServerRejectsDuplicateAndRoundTripsPackets() = runIpcBlockingTest {
        if (!JvmWindowsNamedPipeBridge.isSupported()) {
            return@runIpcBlockingTest
        }
        val tempDir = Files.createTempDirectory("gui-jvm-pipe").toFile()
        val server = IpcServer()
        val competing = IpcServer()
        try {
            val channel = "jvm-win-pipe-${System.nanoTime()}"
            assertTrue(
                server.start(channel, tempDir.absolutePath) { conn ->
                    try {
                        val req = conn.receivePacket(timeoutMs = 4_000)
                        if (req != null) {
                            conn.sendPacket(
                                IpcPacket(
                                    id = req.id + 1L,
                                    type = "pipe-reply",
                                    payload = req.payload,
                                    replyToId = req.id
                                )
                            )
                        }
                    } finally {
                        conn.close()
                    }
                }
            )
            val addr = assertNotNull(server.address)
            assertEquals(IpcChannelAddress.TRANSPORT_WINDOWS_NAMED_PIPE, addr.transportKind)
            assertTrue(addr.endpointAddress.startsWith("\\\\.\\pipe\\genericui."))

            assertFalse(
                competing.start(channel, tempDir.absolutePath) {},
                "Competing Named Pipe server on same endpoint must be rejected by FILE_FLAG_FIRST_PIPE_INSTANCE"
            )

            val client = IpcClient.connect(addr, timeoutMs = 4_000)
            assertNotNull(client)
            try {
                val payload = ByteArray(1_050_000) { ((it + 5) and 0xFF).toByte() }
                assertTrue(
                    client.sendPacket(
                        IpcPacket(
                            id = 55L,
                            type = "pipe-req",
                            payload = PreferenceElement.ofBytes(payload)
                        )
                    )
                )
                val reply = withTimeout(5_000L) {
                    var p: IpcPacket? = null
                    while (p == null) {
                        p = client.receivePacket(timeoutMs = 500)
                    }
                    p
                }
                assertEquals("pipe-reply", reply.type)
                assertEquals(55L, reply.replyToId)
                assertTrue(payload.contentEquals(reply.payload.bytesOrNull()))
            } finally {
                client.close()
            }
        } finally {
            competing.close()
            server.close()
            tempDir.deleteRecursively()
        }
    }
}
