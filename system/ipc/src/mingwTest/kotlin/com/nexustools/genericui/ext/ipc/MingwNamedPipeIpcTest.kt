@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.ipc

import com.nexustools.genericui.component.system.PreferenceElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeout
import platform.windows.GetCurrentProcessId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MingwNamedPipeIpcTest {

    @Test
    fun currentUserPipeSecurityAttributesAndCompetingInstanceProtectionWork() = runIpcBlockingTest {
        var nonNullSa = false
        val value = WindowsNamedPipeSecurity.withCurrentUserPipeSecurityAttributes { saPtr ->
            nonNullSa = saPtr != null
            99
        }
        assertEquals(99, value)
        assertTrue(nonNullSa)

        val tempDir = DesktopIpcTestTempDir.createTempDir("gui-mingw-pipe")
        val channelName = "mingw-pipe-${GetCurrentProcessId()}-${Random.nextInt(100_000, 999_999)}"
        val server = IpcServer()
        val competing = IpcServer()
        try {
            assertTrue(
                server.start(channelName, tempDir) { conn ->
                    try {
                        while (!conn.isClosed) {
                            val req = conn.receivePacket(timeoutMs = 250) ?: continue
                            conn.sendPacket(
                                IpcPacket(
                                    id = req.id + 1L,
                                    type = "mingw-ack",
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
                competing.start(channelName, tempDir) {},
                "Competing Win32 Named Pipe server must fail via FILE_FLAG_FIRST_PIPE_INSTANCE"
            )

            val replies = withTimeout(8_000L) {
                (1..4).map { idx ->
                    async(Dispatchers.IO) {
                        val client = IpcClient.connect(addr, timeoutMs = 4_000)
                        assertNotNull(client, "Client $idx must connect to Win32 pipe")
                        try {
                            val blob = if (idx == 1) {
                                ByteArray(1_150_000) { ((it * 29) and 0xFF).toByte() }
                            } else {
                                "win-$idx".encodeToByteArray()
                            }
                            assertTrue(
                                client.sendPacket(
                                    IpcPacket(
                                        id = idx.toLong(),
                                        type = "win-req",
                                        payload = PreferenceElement.ofBytes(blob)
                                    )
                                )
                            )
                            var resp: IpcPacket? = null
                            while (resp == null) {
                                resp = client.receivePacket(timeoutMs = 500)
                            }
                            assertEquals(idx.toLong(), resp.replyToId)
                            assertTrue(blob.contentEquals(resp.payload.bytesOrNull()))
                            idx
                        } finally {
                            client.close()
                        }
                    }
                }.awaitAll()
            }
            assertEquals(listOf(1, 2, 3, 4), replies)

            server.close()
            assertFalse(server.isRunning)
            assertNull(server.address)
            assertNull(IpcClient.connect(addr, timeoutMs = 200))
        } finally {
            competing.close()
            server.close()
            DesktopIpcTestTempDir.deleteTempDir(tempDir)
        }
    }
}
