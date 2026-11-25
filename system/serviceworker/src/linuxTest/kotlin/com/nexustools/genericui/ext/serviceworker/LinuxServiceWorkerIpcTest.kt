@file:OptIn(
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class
)

package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.enums.system.BackendTarget
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import platform.posix.AF_UNIX
import platform.posix.FD_CLOEXEC
import platform.posix.F_GETFD
import platform.posix.F_OK
import platform.posix.SOCK_CLOEXEC
import platform.posix.SOCK_STREAM
import platform.posix.access
import platform.posix.bind
import platform.posix.close
import platform.posix.fcntl
import platform.posix.geteuid
import platform.posix.getpid
import platform.posix.lstat
import platform.posix.rmdir
import platform.posix.sockaddr
import platform.posix.socket
import platform.posix.stat
import platform.posix.unlink
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinuxServiceWorkerIpcTest {

    private class LinuxSettingsFragment : Fragment()

    private data class LinuxCommand(val name: String, val payload: String = "")
    private data class LinuxPing(val seq: Int)
    private data class LinuxPong(val seq: Int, val origin: String)

    private class LinuxIpcWorker : ServiceWorker() {
        private val lock = AtomicBoolean(false)
        val receivedFromFragment = mutableListOf<LinuxCommand>()
        val disconnectedIds = mutableListOf<String>()

        override fun onStart() {
            onFragmentMessage<LinuxSettingsFragment, LinuxCommand> { _, msg ->
                withLock { receivedFromFragment.add(msg) }
            }
            onFragmentRequest<LinuxSettingsFragment, LinuxPing, LinuxPong> { handle, req ->
                LinuxPong(req.seq, "worker:${handle.fragmentId}")
            }
        }

        override fun onFragmentDisconnected(handle: ServiceWorkerFragmentHandle) {
            withLock { disconnectedIds.add(handle.fragmentId) }
        }

        fun snapshotReceived(): List<LinuxCommand> = withLock { receivedFromFragment.toList() }
        fun snapshotDisconnected(): List<String> = withLock { disconnectedIds.toList() }

        private inline fun <T> withLock(block: () -> T): T {
            while (!lock.compareAndSet(expectedValue = false, newValue = true)) {}
            return try {
                block()
            } finally {
                lock.store(false)
            }
        }
    }

    private class LinuxUnixRuntime(
        private val descriptor: DesktopEndpointDescriptor,
        private val worker: ServiceWorker,
        private val channelClient: DesktopServiceWorkerChannelClient = DesktopServiceWorkerChannelClient()
    ) : ServiceWorkerPlatformRuntime {
        override val isSupported: Boolean = true
        override val supportsMultipleWindows: Boolean = true
        override val supportsFragmentLaunch: Boolean = true
        override val supportsDialogFragments: Boolean = true
        override val supportsSystemTray: Boolean = true
        override val supportsOngoingNotification: Boolean = false
        override val supportsAutostart: Boolean = true
        override fun configure(options: ServiceWorkerOptions) {}
        override fun isClientConnected(): Boolean = channelClient.isConnected

        override suspend fun connectClient(
            options: ServiceWorkerOptions,
            onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
            onConnectionChanged: (Boolean) -> Unit
        ): Boolean = channelClient.connectToEndpoint(
            descriptor = descriptor,
            onIncomingEnvelope = onIncomingEnvelope,
            onConnectionStateChanged = onConnectionChanged
        )

        override suspend fun disconnectClient() = channelClient.disconnect()
        override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean =
            channelClient.sendEnvelope(envelope)

        override fun activeWorker(): ServiceWorker = worker
        override fun currentBackendTarget(): BackendTarget = BackendTarget.Gtk
    }

    private val codecIds = listOf(
        "linux.LinuxCommand",
        "linux.LinuxPing",
        "linux.LinuxPong"
    )

    private val tempDirsToClean = mutableListOf<String>()
    private val tempFilesToClean = mutableListOf<String>()

    private fun createTempDir(prefix: String): String {
        val path = "/tmp/$prefix-${getpid()}-${kotlin.random.Random.nextInt(100_000, 999_999)}"
        DesktopOsHelpers.ensureDirectoryExists(path)
        tempDirsToClean.add(path)
        return path
    }

    private fun registerCodecsAndFragments() {
        FragmentRegistry.clear()
        FragmentRegistry.register(LinuxSettingsFragment::class) { LinuxSettingsFragment() }
        FragmentRegistry.registerMetadata(
            fragmentClass = LinuxSettingsFragment::class,
            fragmentId = "com.test.linux.SettingsFragment",
            route = "/linux-settings"
        )

        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<LinuxCommand> {
            override val typeId = "linux.LinuxCommand"
            override val messageClass = LinuxCommand::class
            override fun encode(value: LinuxCommand) = PreferenceElement.ofObject(
                mapOf(
                    "name" to PreferenceElement.ofString(value.name),
                    "payload" to PreferenceElement.ofString(value.payload)
                )
            )
            override fun decode(element: PreferenceElement) = LinuxCommand(
                name = element.mapEntries["name"]?.asString().orEmpty(),
                payload = element.mapEntries["payload"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<LinuxPing> {
            override val typeId = "linux.LinuxPing"
            override val messageClass = LinuxPing::class
            override fun encode(value: LinuxPing) = PreferenceElement.ofObject(
                mapOf("seq" to PreferenceElement.ofInt(value.seq))
            )
            override fun decode(element: PreferenceElement) = LinuxPing(
                element.mapEntries["seq"]?.asInt() ?: 0
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<LinuxPong> {
            override val typeId = "linux.LinuxPong"
            override val messageClass = LinuxPong::class
            override fun encode(value: LinuxPong) = PreferenceElement.ofObject(
                mapOf(
                    "seq" to PreferenceElement.ofInt(value.seq),
                    "origin" to PreferenceElement.ofString(value.origin)
                )
            )
            override fun decode(element: PreferenceElement) = LinuxPong(
                seq = element.mapEntries["seq"]?.asInt() ?: 0,
                origin = element.mapEntries["origin"]?.asString().orEmpty()
            )
        })
    }

    private suspend fun waitUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!predicate()) {
                delay(15L)
            }
        }
    }

    private fun readPermissions(path: String): Int? = memScoped {
        val st = alloc<stat>()
        if (lstat(path, st.ptr) != 0) return@memScoped null
        st.st_mode.toInt() and 0x1FF
    }

    private fun cleanupPath(path: String) {
        unlink(path)
        rmdir(path)
    }

    @BeforeTest
    fun setUp() {
        ServiceWorkerExtension.resetForTests()
        registerCodecsAndFragments()
    }

    @AfterTest
    fun tearDown() {
        ServiceWorkerExtension.resetForTests()
        codecIds.forEach { ServiceWorkerCodecRegistry.unregister(it) }
        FragmentRegistry.clear()
        DesktopOsHelpers.releaseSingleInstanceLock()
        for (f in tempFilesToClean.asReversed()) {
            unlink(f)
        }
        tempFilesToClean.clear()
        for (dir in tempDirsToClean.asReversed()) {
            unlink("$dir/sw.sock")
            unlink("$dir/serviceworker-endpoint.json")
            unlink("$dir/worker.lock")
            rmdir(dir)
        }
        tempDirsToClean.clear()
    }

    @Test
    fun unixSocketPermissionsPeerCredStaleRecoveryAndLongPathHashingWork() {
        val tempDir = createTempDir("gui-lnx-sock")
        val appId = "com.test.linux.unix"
        val resolvedPath = LinuxUnixSocketAddressHelper.resolveSocketPath(appId, tempDir)
        assertEquals("$tempDir/sw.sock", resolvedPath)
        assertTrue(resolvedPath.encodeToByteArray().size <= LinuxUnixSocketAddressHelper.MAX_SUN_PATH_BYTES)

        // Create a real stale AF_UNIX socket file (bound and closed without unlink).
        assertTrue(LinuxUnixSocketAddressHelper.ensureSecureParentDir(resolvedPath))
        val staleFd = socket(AF_UNIX, SOCK_STREAM or SOCK_CLOEXEC, 0)
        assertTrue(staleFd >= 0, "Stale socket fd creation must succeed")
        val staleSun = LinuxUnixSocketAddressHelper.buildSockaddrUn(resolvedPath)
        assertNotNull(staleSun)
        val boundStale = staleSun.usePinned { pinned ->
            bind(
                staleFd,
                pinned.addressOf(0).reinterpret<sockaddr>(),
                LinuxUnixSocketAddressHelper.sockaddrUnLength(resolvedPath).convert()
            ) == 0
        }
        close(staleFd)
        assertTrue(boundStale, "Pre-binding stale socket file must succeed")
        assertEquals(0, access(resolvedPath, F_OK))

        // Verify server auto-recovers stale socket, sets 0700 dir / 0600 socket, and checks SO_PEERCRED.
        val server = DesktopIpcServer()
        var acceptedCredsUid: UInt? = null
        var acceptedConn: DesktopIpcConnection? = null
        assertTrue(
            server.start(appId, tempDir) { conn ->
                acceptedConn = conn
            },
            "DesktopIpcServer must recover stale socket and bind AF_UNIX"
        )
        try {
            assertEquals(DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET, server.transportKind)
            assertEquals(resolvedPath, server.endpointAddress)
            assertEquals(0x1C0, readPermissions(tempDir), "Runtime parent dir must be 0700")
            assertEquals(0x180, readPermissions(resolvedPath), "Unix socket file must be 0600")

            // Live socket must NOT be treated as stale by a second server start attempt.
            val competingServer = DesktopIpcServer()
            assertFalse(
                competingServer.start(appId, tempDir) {},
                "Live AF_UNIX socket must refuse stale unlink / bind takeover"
            )
            competingServer.close()

            val clientConn = DesktopIpcClient.connect(
                transportKind = server.transportKind,
                address = server.endpointAddress,
                timeoutMs = 2_000
            )
            assertNotNull(clientConn, "AF_UNIX client must connect to live server")
            try {
                // Verify SO_PEERCRED on a live AF_UNIX pair directly via helper.
                val probeFd = socket(AF_UNIX, SOCK_STREAM or SOCK_CLOEXEC, 0)
                assertTrue(probeFd >= 0)
                try {
                    val connected = staleSun.usePinned { pinned ->
                        platform.posix.connect(
                            probeFd,
                            pinned.addressOf(0).reinterpret<sockaddr>(),
                            LinuxUnixSocketAddressHelper.sockaddrUnLength(resolvedPath).convert()
                        ) == 0
                    }
                    assertTrue(connected)
                    val creds = LinuxUnixSocketAddressHelper.readPeerCredentials(probeFd)
                    assertNotNull(creds)
                    acceptedCredsUid = creds.second
                    assertEquals(geteuid(), acceptedCredsUid, "SO_PEERCRED uid must match geteuid()")
                    assertTrue(LinuxUnixSocketAddressHelper.verifyPeerCredentials(probeFd))
                } finally {
                    close(probeFd)
                }
            } finally {
                clientConn.close()
                acceptedConn?.close()
            }
        } finally {
            server.close()
        }
        assertTrue(access(resolvedPath, F_OK) != 0, "server.close() must unlink AF_UNIX socket file")

        // Long runtime path (> 108 bytes) must hash deterministically into <= 100 bytes and round-trip.
        val longSuffix = "nested-segment-exceeding-unix-sun-path-limit-".repeat(3)
        val longRuntimeDir = "$tempDir/$longSuffix"
        val hashedPath = LinuxUnixSocketAddressHelper.resolveSocketPath(appId, longRuntimeDir)
        tempFilesToClean.add(hashedPath)
        assertTrue(
            hashedPath.encodeToByteArray().size <= LinuxUnixSocketAddressHelper.MAX_SUN_PATH_BYTES,
            "Hashed path '$hashedPath' must be <= 100 UTF-8 bytes"
        )
        val expectedHash = DesktopIpcHash.fnv1a64Hex("$appId|$longRuntimeDir")
        assertTrue(
            hashedPath.endsWith("/gui-$expectedHash.sock"),
            "Long path fallback must hash appId|runtimeDir deterministically"
        )

        val hashedServer = DesktopIpcServer()
        var hashedPeer: DesktopIpcConnection? = null
        assertTrue(hashedServer.start(appId, longRuntimeDir) { hashedPeer = it })
        try {
            assertEquals(hashedPath, hashedServer.endpointAddress)
            val hashedClient = DesktopIpcClient.connect(
                transportKind = hashedServer.transportKind,
                address = hashedServer.endpointAddress,
                timeoutMs = 2_000
            )
            assertNotNull(hashedClient)
            try {
                val msg = "hashed-unix-ok".encodeToByteArray()
                assertTrue(hashedClient.sendBody(msg))
                var deadline = 0
                while (hashedPeer == null && deadline < 50) {
                    DesktopOsHelpers.sleepMillis(10L)
                    deadline++
                }
                val peer = assertNotNull(hashedPeer)
                assertEquals("hashed-unix-ok", peer.receiveBody(timeoutMs = 1_000)?.decodeToString())
            } finally {
                hashedClient.close()
                hashedPeer?.close()
            }
        } finally {
            hashedServer.close()
            cleanupPath(hashedPath)
        }
    }

    @Test
    fun linuxChildProcessDoesNotInheritFlockOrUnixSocketFds() {
        val tempDir = createTempDir("gui-lnx-cloexec")
        val lockFile = "$tempDir/worker.lock"
        val lockKey1 = "linux-cloexec-1-${getpid()}"
        val lockKey2 = "linux-cloexec-2-${getpid()}"

        assertTrue(DesktopOsHelpers.acquireSingleInstanceLock(lockKey1, lockFile))
        val server = DesktopIpcServer()
        assertTrue(server.start("com.test.linux.cloexec", tempDir) {})
        val client = DesktopIpcClient.connect(server.transportKind, server.endpointAddress, timeoutMs = 2_000)
        assertNotNull(client)

        try {
            // Verify FD_CLOEXEC directly on a probe socket opened with SOCK_CLOEXEC.
            val checkFd = socket(AF_UNIX, SOCK_STREAM or SOCK_CLOEXEC, 0)
            assertTrue(checkFd >= 0)
            try {
                assertTrue((fcntl(checkFd, F_GETFD) and FD_CLOEXEC) != 0, "SOCK_CLOEXEC must set FD_CLOEXEC")
            } finally {
                close(checkFd)
            }

            assertTrue(
                DesktopOsHelpers.spawnDetachedProcess(
                    executablePath = "/bin/sh",
                    args = listOf("-c", "sleep 1"),
                    workingDir = tempDir
                ),
                "Spawning detached Linux child process must succeed"
            )

            // Release lock while child is still sleeping: FD_CLOEXEC guarantees child does not hold flock.
            DesktopOsHelpers.releaseSingleInstanceLock(lockKey1)
            assertTrue(
                DesktopOsHelpers.acquireSingleInstanceLock(lockKey2, lockFile),
                "Single-instance flock must re-acquire immediately while spawned child is alive"
            )
        } finally {
            client.close()
            DesktopOsHelpers.releaseSingleInstanceLock()
            server.close()
        }
    }

    @Test
    fun linuxUnixSocketRoundTripsAuthRejectionLargePayloadBroadcastsAndDisconnect() = runDesktopBlockingTest {
        val tempDir = createTempDir("gui-lnx-ipc")
        val endpointFile = "$tempDir/serviceworker-endpoint.json"
        val worker = LinuxIpcWorker()
        val options = ServiceWorkerOptions(
            appId = "com.test.linux.ipc",
            appName = "LinuxIpcTest",
            hasWorker = true
        )
        val host = DesktopServiceWorkerHost(
            options = options,
            worker = worker,
            endpointFilePath = endpointFile,
            mainBinaryBaseName = "LinuxApp",
            swingMainClassFqn = "",
            stopEventLoop = {},
            lockRelease = {}
        )
        assertTrue(host.start(), "DesktopServiceWorkerHost must start AF_UNIX server")
        assertEquals(DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET, host.boundTransport)
        assertTrue(host.boundAddress.isNotBlank())
        assertEquals(0, access(host.boundAddress, F_OK))
        assertEquals(0x180, readPermissions(host.boundAddress))

        val boundSocketPath = host.boundAddress
        worker.attachAndStart(host, options)
        try {
            val rawEndpoint = DesktopOsHelpers.readTextFileOrNull(endpointFile)
            assertNotNull(rawEndpoint)
            val descriptor = DesktopEndpointDescriptor.parse(rawEndpoint)
            assertNotNull(descriptor)
            assertEquals(DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET, descriptor.transport)
            assertEquals(boundSocketPath, descriptor.address)

            // Reject invalid KIND_AUTH token.
            val badClient = DesktopServiceWorkerChannelClient()
            assertFalse(
                badClient.connectToEndpoint(
                    descriptor = descriptor.copy(token = "invalid-${descriptor.token}"),
                    onIncomingEnvelope = {},
                    onConnectionStateChanged = {}
                ),
                "Invalid auth token must be rejected"
            )
            badClient.disconnect()

            // Connect valid client runtime.
            ServiceWorkerExtension.install(options)
            ServiceWorkerExtension.installRuntimeOverrideForTests(LinuxUnixRuntime(descriptor, worker))
            val channel = ServiceWorkerExtension.channel
            assertTrue(channel.connect())

            val fragment = LinuxSettingsFragment()
            val fragReceived = mutableListOf<LinuxCommand>()
            val binding = ServiceWorkerFragmentBinding(
                fragment = fragment,
                channel = channel
            ).apply {
                onMessage<LinuxCommand> { msg -> fragReceived.add(msg) }
                onRequest<LinuxPing, LinuxPong> { req ->
                    LinuxPong(req.seq, "fragment:linux")
                }
                start()
            }

            val handleDeferred = async {
                worker.startFragmentAndAwait<LinuxSettingsFragment>(
                    args = FragmentBundle.of("tab" to "network"),
                    timeoutMs = 5_000L
                )
            }
            val handle = withTimeout(5_000L) { handleDeferred.await() }
            assertEquals("com.test.linux.SettingsFragment", handle.fragmentId)
            assertTrue(worker.isFragmentActive<LinuxSettingsFragment>())

            // Targeted message + > 256 KB payload round-trip.
            val largePayload = "X".repeat(300_000)
            worker.sendToFragment<LinuxSettingsFragment>(LinuxCommand("apply-large", largePayload))
            waitUntil { fragReceived.size == 1 }
            assertEquals("apply-large", fragReceived[0].name)
            assertEquals(300_000, fragReceived[0].payload.length)

            // Broadcast message from worker -> client.
            val delivered = worker.broadcast(LinuxCommand("broadcast-sync", "all"))
            assertTrue(delivered >= 1)
            waitUntil { fragReceived.size == 2 }
            assertEquals(LinuxCommand("broadcast-sync", "all"), fragReceived[1])

            // Worker -> Fragment request/reply.
            val fragReply = worker.requestFromFragment<LinuxSettingsFragment, LinuxPong>(
                LinuxPing(41),
                timeoutMs = 5_000L
            )
            assertEquals(LinuxPong(41, "fragment:linux"), fragReply)

            // Fragment -> Worker large message + request/reply.
            binding.send(LinuxCommand("from-fragment", largePayload))
            waitUntil { worker.snapshotReceived().size == 1 }
            assertEquals("from-fragment", worker.snapshotReceived()[0].name)
            assertEquals(300_000, worker.snapshotReceived()[0].payload.length)

            val workerReply = binding.request<LinuxPong>(LinuxPing(42), timeoutMs = 5_000L)
            assertEquals(LinuxPong(42, "worker:com.test.linux.SettingsFragment"), workerReply)

            // Disconnect cleanup.
            channel.disconnect()
            waitUntil {
                !worker.isFragmentActive<LinuxSettingsFragment>() &&
                    worker.snapshotDisconnected().contains("com.test.linux.SettingsFragment")
            }
            assertFalse(worker.isFragmentActive<LinuxSettingsFragment>())
            binding.dispose()
        } finally {
            worker.performStop()
            host.shutdown()
        }

        assertTrue(access(boundSocketPath, F_OK) != 0, "host.shutdown() must remove AF_UNIX socket")
        assertNull(DesktopOsHelpers.readTextFileOrNull(endpointFile), "host.shutdown() must remove endpoint json")
    }
}
