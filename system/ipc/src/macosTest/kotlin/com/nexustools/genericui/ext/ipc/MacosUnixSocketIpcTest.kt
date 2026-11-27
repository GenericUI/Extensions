@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.ipc

import com.nexustools.genericui.component.system.PreferenceElement
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.withTimeout
import platform.posix.F_OK
import platform.posix.access
import platform.posix.getpid
import platform.posix.lstat
import platform.posix.stat
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacosUnixSocketIpcTest {

    private fun readPermissions(path: String): Int? = memScoped {
        val st = alloc<stat>()
        if (lstat(path, st.ptr) != 0) return@memScoped null
        st.st_mode.toInt() and 0x1FF
    }

    @Test
    fun macosUnixSocketLongPathShorteningAndAnonymousXpcRoundTripWork() = runIpcBlockingTest {
        val tempDir = DesktopIpcTestTempDir.createTempDir("gui-macos-ipc-${"seg-".repeat(20)}")
        try {
            assertTrue(tempDir.encodeToByteArray().size > 96)
            val server = IpcServer()
            val channelName = "macos-ch-${getpid()}-${Random.nextInt(100_000, 999_999)}"
            assertTrue(
                server.start(channelName, tempDir) { conn ->
                    try {
                        val req = conn.receivePacket(timeoutMs = 3_000)
                        if (req != null) {
                            conn.sendPacket(
                                IpcPacket(
                                    id = req.id + 1L,
                                    type = "macos-ack",
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
            assertEquals(IpcChannelAddress.TRANSPORT_UNIX_SOCKET, addr.transportKind)
            assertTrue(addr.endpointAddress.encodeToByteArray().size <= 96)
            assertEquals(0x180, readPermissions(addr.endpointAddress))

            val competing = IpcServer()
            assertFalse(competing.start(channelName, tempDir) {})
            competing.close()

            try {
                val client = IpcClient.connect(addr, timeoutMs = 2_000)
                assertNotNull(client)
                try {
                    val payload = ByteArray(1_050_000) { ((it * 7) and 0xFF).toByte() }
                    assertTrue(
                        client.sendPacket(
                            IpcPacket(id = 21L, type = "blob", payload = PreferenceElement.ofBytes(payload))
                        )
                    )
                    val reply = withTimeout(5_000L) {
                        var p: IpcPacket? = null
                        while (p == null) {
                            p = client.receivePacket(timeoutMs = 300)
                        }
                        p
                    }
                    assertEquals(21L, reply.replyToId)
                    assertTrue(payload.contentEquals(reply.payload.bytesOrNull()))
                } finally {
                    client.close()
                }
            } finally {
                server.close()
            }
            assertTrue(access(addr.endpointAddress, F_OK) != 0)
            assertNull(IpcClient.connect(addr, timeoutMs = 200))

            // Verify libxpc anonymous kernel Mach endpoint roundtrip
            val xpcPair = assertNotNull(
                MacosXpcListenerSupport.startAnonymousListener { serverConn ->
                    val req = serverConn.receivePacket(timeoutMs = 3_000)
                    if (req != null) {
                        serverConn.sendPacket(
                            IpcPacket(
                                id = req.id + 1L,
                                type = "xpc-reply",
                                payload = req.payload,
                                replyToId = req.id
                            )
                        )
                    }
                }
            )
            val (listenerHandle, endpoint) = xpcPair
            try {
                val xpcClient = assertNotNull(MacosXpcListenerSupport.connectAnonymousEndpoint(endpoint))
                try {
                    val xpcBlob = ByteArray(400_000) { ((it * 19) and 0xFF).toByte() }
                    assertTrue(
                        xpcClient.sendPacket(
                            IpcPacket(id = 303L, type = "xpc-blob", payload = PreferenceElement.ofBytes(xpcBlob))
                        )
                    )
                    val xpcReply = withTimeout(5_000L) {
                        var p: IpcPacket? = null
                        while (p == null) {
                            p = xpcClient.receivePacket(timeoutMs = 300)
                        }
                        p
                    }
                    assertEquals("xpc-reply", xpcReply.type)
                    assertEquals(303L, xpcReply.replyToId)
                    assertTrue(xpcBlob.contentEquals(xpcReply.payload.bytesOrNull()))
                } finally {
                    xpcClient.close()
                }
            } finally {
                listenerHandle.close()
            }
        } finally {
            DesktopIpcTestTempDir.deleteTempDir(tempDir)
        }
    }
}
