@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.ipc

import com.nexustools.genericui.component.system.PreferenceElement
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.withTimeout
import platform.posix.AF_UNIX
import platform.posix.F_OK
import platform.posix.SOCK_CLOEXEC
import platform.posix.SOCK_STREAM
import platform.posix.access
import platform.posix.bind
import platform.posix.close
import platform.posix.geteuid
import platform.posix.getpid
import platform.posix.lstat
import platform.posix.sockaddr
import platform.posix.socket
import platform.posix.stat
import platform.posix.unlink
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinuxUnixSocketIpcTest {

    private fun readPermissions(path: String): Int? = memScoped {
        val st = alloc<stat>()
        if (lstat(path, st.ptr) != 0) return@memScoped null
        st.st_mode.toInt() and 0x1FF
    }

    @Test
    fun linuxUnixSocketEnforcesPermissionsPeerCredStaleRecoveryAndLongPathShortening() = runIpcBlockingTest {
        val tempDir = DesktopIpcTestTempDir.createTempDir("gui-lnx-ipc")
        val channelName = "linux-ch-${getpid()}-${Random.nextInt(100_000, 999_999)}"
        val resolved = IpcChannelAddress.resolveDesktopEndpoint(channelName, tempDir, "linux")
        assertEquals(IpcChannelAddress.TRANSPORT_UNIX_SOCKET, resolved.transportKind)
        val socketPath = resolved.endpointAddress

        try {
            // Pre-create a real stale socket file
            assertTrue(LinuxUnixSocketAddressHelper.ensureSecureParentDir(socketPath))
            val staleFd = socket(AF_UNIX, SOCK_STREAM or SOCK_CLOEXEC, 0)
            assertTrue(staleFd >= 0)
            val staleSun = assertNotNull(LinuxUnixSocketAddressHelper.buildSockaddrUn(socketPath))
            val boundStale = staleSun.usePinned { pinned ->
                bind(
                    staleFd,
                    pinned.addressOf(0).reinterpret<sockaddr>(),
                    LinuxUnixSocketAddressHelper.sockaddrUnLength(socketPath).convert()
                ) == 0
            }
            close(staleFd)
            assertTrue(boundStale)
            assertEquals(0, access(socketPath, F_OK))

            val server = IpcServer()
            val competing = IpcServer()
            try {
                assertTrue(
                    server.start(channelName, tempDir) { conn ->
                        try {
                            val req = conn.receivePacket(timeoutMs = 3_000)
                            if (req != null) {
                                conn.sendPacket(
                                    IpcPacket(
                                        id = req.id + 1L,
                                        type = "linux-ack",
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
                assertEquals(0x1C0, readPermissions(tempDir), "Runtime parent dir must be 0700")
                assertEquals(0x180, readPermissions(socketPath), "Unix socket file must be 0600")
                assertFalse(
                    competing.start(channelName, tempDir) {},
                    "Competing server must not unlink a live socket"
                )

                val client = IpcClient.connect(assertNotNull(server.address), timeoutMs = 2_000)
                assertNotNull(client)
                try {
                    val probeFd = socket(AF_UNIX, SOCK_STREAM or SOCK_CLOEXEC, 0)
                    assertTrue(probeFd >= 0)
                    try {
                        val connected = staleSun.usePinned { pinned ->
                            platform.posix.connect(
                                probeFd,
                                pinned.addressOf(0).reinterpret<sockaddr>(),
                                LinuxUnixSocketAddressHelper.sockaddrUnLength(socketPath).convert()
                            ) == 0
                        }
                        assertTrue(connected)
                        val creds = assertNotNull(LinuxUnixSocketAddressHelper.readPeerCredentials(probeFd))
                        assertEquals(geteuid(), creds.second)
                        assertTrue(LinuxUnixSocketAddressHelper.verifyPeerCredentials(probeFd))
                    } finally {
                        close(probeFd)
                    }

                    val bigBytes = ByteArray(1_100_000) { (it and 0xFF).toByte() }
                    assertTrue(
                        client.sendPacket(
                            IpcPacket(id = 77L, type = "blob", payload = PreferenceElement.ofBytes(bigBytes))
                        )
                    )
                    val reply = withTimeout(5_000L) {
                        var p: IpcPacket? = null
                        while (p == null) {
                            p = client.receivePacket(timeoutMs = 300)
                        }
                        p
                    }
                    assertEquals(77L, reply.replyToId)
                    assertTrue(bigBytes.contentEquals(reply.payload.bytesOrNull()))
                } finally {
                    client.close()
                }
            } finally {
                competing.close()
                server.close()
            }
            assertTrue(access(socketPath, F_OK) != 0)
            assertNull(IpcClient.connect(resolved, timeoutMs = 200))

            // Long runtimeDir > 120 bytes shortens cleanly and connects
            val longRuntimeDir = "$tempDir/${"segment-".repeat(16)}dir"
            val longServer = IpcServer()
            assertTrue(longServer.start("long-linux", longRuntimeDir) { it.close() })
            try {
                val longAddr = assertNotNull(longServer.address)
                assertTrue(longAddr.endpointAddress.encodeToByteArray().size <= 96)
            } finally {
                longServer.close()
            }
        } finally {
            unlink(socketPath)
            DesktopIpcTestTempDir.deleteTempDir(tempDir)
        }
    }
}
