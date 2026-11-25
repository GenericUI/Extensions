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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.posix.F_OK
import platform.posix.access
import platform.posix.geteuid
import platform.posix.getpid
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MacosServiceWorkerFragmentIpcTest {

    private class MacosSettingsFragment : Fragment()

    private data class MacosCommand(val name: String)
    private data class MacosPing(val seq: Int)
    private data class MacosPong(val seq: Int, val origin: String)

    private class MacosIpcWorker : ServiceWorker() {
        private val lock = AtomicBoolean(false)
        val receivedFromClient = mutableListOf<MacosCommand>()
        val receivedFromFragment = mutableListOf<MacosCommand>()
        val recordedClients = mutableListOf<ServiceWorkerClient>()
        val disconnectedClientIds = mutableListOf<String>()
        val disconnectedFragmentIds = mutableListOf<String>()

        override fun onStart() {
            onMessage<MacosCommand> { _, msg ->
                withLock { receivedFromClient.add(msg) }
            }
            onRequest<MacosPing> { _, req, reply ->
                reply.send(MacosPong(req.seq, "worker:xpc"))
            }
            onFragmentMessage<MacosSettingsFragment, MacosCommand> { _, msg ->
                withLock { receivedFromFragment.add(msg) }
            }
            onFragmentRequest<MacosSettingsFragment, MacosPing, MacosPong> { handle, req ->
                MacosPong(req.seq, "worker:${handle.fragmentId}")
            }
        }

        override fun onClientConnected(client: ServiceWorkerClient) {
            withLock { recordedClients.add(client) }
        }

        override fun onClientDisconnected(client: ServiceWorkerClient) {
            withLock { disconnectedClientIds.add(client.clientId) }
        }

        override fun onFragmentDisconnected(handle: ServiceWorkerFragmentHandle) {
            withLock { disconnectedFragmentIds.add(handle.fragmentId) }
        }

        fun snapshotReceivedClient(): List<MacosCommand> = withLock { receivedFromClient.toList() }
        fun snapshotReceivedFragment(): List<MacosCommand> = withLock { receivedFromFragment.toList() }
        fun snapshotConnectedClients(): List<ServiceWorkerClient> = withLock { recordedClients.toList() }
        fun snapshotDisconnectedClients(): List<String> = withLock { disconnectedClientIds.toList() }
        fun snapshotDisconnectedFragments(): List<String> = withLock { disconnectedFragmentIds.toList() }

        private inline fun <T> withLock(block: () -> T): T {
            while (!lock.compareAndSet(expectedValue = false, newValue = true)) {}
            return try {
                block()
            } finally {
                lock.store(false)
            }
        }
    }

    private class MacosIpcRuntime(
        private val descriptor: DesktopEndpointDescriptor,
        private val worker: ServiceWorker,
        private val ipcClient: DesktopServiceWorkerChannelClient = DesktopServiceWorkerChannelClient()
    ) : ServiceWorkerPlatformRuntime {
        override val isSupported: Boolean = true
        override val supportsMultipleWindows: Boolean = true
        override val supportsFragmentLaunch: Boolean = true
        override val supportsDialogFragments: Boolean = true
        override val supportsSystemTray: Boolean = true
        override val supportsOngoingNotification: Boolean = false
        override val supportsAutostart: Boolean = true
        override fun configure(options: ServiceWorkerOptions) {}
        override fun isClientConnected(): Boolean = ipcClient.isConnected

        override suspend fun connectClient(
            options: ServiceWorkerOptions,
            onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
            onConnectionChanged: (Boolean) -> Unit
        ): Boolean = ipcClient.connectToEndpoint(
            descriptor = descriptor,
            onIncomingEnvelope = onIncomingEnvelope,
            onConnectionStateChanged = onConnectionChanged
        )

        override suspend fun disconnectClient() = ipcClient.disconnect()
        override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean =
            ipcClient.sendEnvelope(envelope)

        override fun activeWorker(): ServiceWorker = worker
        override fun currentBackendTarget(): BackendTarget = BackendTarget.MacOS
    }

    private val codecIds = listOf(
        "macos.MacosCommand",
        "macos.MacosPing",
        "macos.MacosPong"
    )

    private fun createTempDir(prefix: String): String {
        val base = NSTemporaryDirectory().trimEnd('/')
        val path = "$base/$prefix-${getpid()}-${kotlin.random.Random.nextInt(100_000, 999_999)}"
        DesktopOsHelpers.ensureDirectoryExists(path)
        return path
    }

    private fun registerCodecsAndFragments() {
        FragmentRegistry.clear()
        FragmentRegistry.register(MacosSettingsFragment::class) { MacosSettingsFragment() }
        FragmentRegistry.registerMetadata(
            fragmentClass = MacosSettingsFragment::class,
            fragmentId = "com.test.macos.SettingsFragment",
            route = "/macos-settings"
        )

        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<MacosCommand> {
            override val typeId = "macos.MacosCommand"
            override val messageClass = MacosCommand::class
            override fun encode(value: MacosCommand) = PreferenceElement.ofObject(
                mapOf("name" to PreferenceElement.ofString(value.name))
            )
            override fun decode(element: PreferenceElement) = MacosCommand(
                element.mapEntries["name"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<MacosPing> {
            override val typeId = "macos.MacosPing"
            override val messageClass = MacosPing::class
            override fun encode(value: MacosPing) = PreferenceElement.ofObject(
                mapOf("seq" to PreferenceElement.ofInt(value.seq))
            )
            override fun decode(element: PreferenceElement) = MacosPing(
                element.mapEntries["seq"]?.asInt() ?: 0
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<MacosPong> {
            override val typeId = "macos.MacosPong"
            override val messageClass = MacosPong::class
            override fun encode(value: MacosPong) = PreferenceElement.ofObject(
                mapOf(
                    "seq" to PreferenceElement.ofInt(value.seq),
                    "origin" to PreferenceElement.ofString(value.origin)
                )
            )
            override fun decode(element: PreferenceElement) = MacosPong(
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
    }

    @Test
    fun macosChildProcessDoesNotInheritFlockOrUnixSocketFds() {
        val tempDir = createTempDir("genericui-macos-cloexec")
        try {
            val lockFile = "$tempDir/worker.lock"
            val lockKey1 = "cloexec-key-1-${getpid()}"
            val lockKey2 = "cloexec-key-2-${getpid()}"

            assertTrue(DesktopOsHelpers.acquireSingleInstanceLock(lockKey1, lockFile))
            val ipcServer = DesktopIpcServer()
            assertTrue(
                ipcServer.start("com.test.macos.cloexec", tempDir) {},
                "DesktopIpcServer must bind AF_UNIX socket cleanly"
            )
            assertEquals(DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET, ipcServer.transportKind)

            try {
                assertTrue(
                    DesktopOsHelpers.spawnDetachedProcess(
                        executablePath = "/bin/sh",
                        args = listOf("-c", "sleep 1"),
                        workingDir = tempDir
                    ),
                    "Spawning detached macOS child via NSTask with null stdio must succeed"
                )

                // Release lock in parent while child process is still alive: because FD_CLOEXEC
                // was set on lockFile FD, the child process does not hold an inherited flock reference.
                DesktopOsHelpers.releaseSingleInstanceLock(lockKey1)
                assertTrue(
                    DesktopOsHelpers.acquireSingleInstanceLock(lockKey2, lockFile),
                    "Single-instance flock must immediately re-acquire while spawned child is alive"
                )

                // Closing the server while child is still alive must release the socket cleanly so a
                // fresh server can bind the same path immediately without FD inheritance conflicts.
                ipcServer.close()
                val reboundServer = DesktopIpcServer()
                try {
                    assertTrue(
                        reboundServer.start("com.test.macos.cloexec", tempDir) {},
                        "DesktopIpcServer must re-bind immediately while spawned child is alive"
                    )
                } finally {
                    reboundServer.close()
                }
            } finally {
                DesktopOsHelpers.releaseSingleInstanceLock()
                ipcServer.close()
            }
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(tempDir, error = null)
        }
    }

    @Test
    fun macosUnixSocketRoundTripsFragmentLaunchAndBidirectionalIpc() = runBlocking {
        // Ensure >96-byte runtimeDir so Darwin AF_UNIX falls back to hashed /tmp/genericui-<uid>/<hash>.sock
        val tempDir = createTempDir(
            "genericui-macos-very-long-runtime-directory-path-for-unix-socket-truncation-verification"
        )
        try {
            assertTrue(tempDir.encodeToByteArray().size > 96)
            val appId = "com.test.macos.frag.unix"
            val resolvedSocketPath = MacosUnixSocketSupport.resolveSocketPath(appId, tempDir)
            assertNotNull(resolvedSocketPath)
            assertTrue(
                resolvedSocketPath.encodeToByteArray().size <= 96,
                "Resolved Darwin AF_UNIX socket path must stay <= 96 UTF-8 bytes"
            )
            assertTrue(
                resolvedSocketPath.startsWith("/tmp/genericui-${geteuid()}/"),
                "Long runtimeDir must hash into /tmp/genericui-<euid>/: $resolvedSocketPath"
            )

            val endpointFile = "$tempDir/serviceworker-endpoint.json"
            val worker = MacosIpcWorker()
            val options = ServiceWorkerOptions(
                appId = appId,
                appName = "MacosFragTest",
                hasWorker = true
            )
            val host = DesktopServiceWorkerHost(
                options = options,
                worker = worker,
                endpointFilePath = endpointFile,
                mainBinaryBaseName = "MacosApp",
                swingMainClassFqn = "",
                stopEventLoop = {},
                lockRelease = {}
            )
            assertTrue(host.start())
            assertEquals(DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET, host.boundTransport)
            assertEquals(0, access(resolvedSocketPath, F_OK), "AF_UNIX socket file must exist while host runs")
            worker.attachAndStart(host, options)
            try {
                val rawEndpoint = DesktopOsHelpers.readTextFileOrNull(endpointFile)
                assertNotNull(rawEndpoint)
                val descriptor = DesktopEndpointDescriptor.parse(rawEndpoint)
                assertNotNull(descriptor)

                ServiceWorkerExtension.install(options)
                ServiceWorkerExtension.installRuntimeOverrideForTests(MacosIpcRuntime(descriptor, worker))
                val channel = ServiceWorkerExtension.channel
                assertTrue(channel.connect())

                val fragment = MacosSettingsFragment()
                val fragReceived = mutableListOf<MacosCommand>()
                val binding = ServiceWorkerFragmentBinding(
                    fragment = fragment,
                    channel = channel
                ).apply {
                    onMessage<MacosCommand> { msg -> fragReceived.add(msg) }
                    onRequest<MacosPing, MacosPong> { req ->
                        MacosPong(req.seq, "fragment:macos")
                    }
                    start()
                }

                val handleDeferred = async {
                    worker.startFragmentAndAwait<MacosSettingsFragment>(
                        args = FragmentBundle.of("pane" to "audio"),
                        timeoutMs = 5_000L
                    )
                }
                val handle = withTimeout(5_000L) { handleDeferred.await() }
                assertEquals("com.test.macos.SettingsFragment", handle.fragmentId)
                assertTrue(worker.isFragmentActive<MacosSettingsFragment>())

                worker.sendToFragment<MacosSettingsFragment>(MacosCommand("apply-audio"))
                waitUntil { fragReceived.size == 1 }
                assertEquals(listOf(MacosCommand("apply-audio")), fragReceived)

                val fragReply = worker.requestFromFragment<MacosSettingsFragment, MacosPong>(
                    MacosPing(11),
                    timeoutMs = 5_000L
                )
                assertEquals(MacosPong(11, "fragment:macos"), fragReply)

                binding.send(MacosCommand("audio-ready"))
                waitUntil { worker.snapshotReceivedFragment().size == 1 }
                assertEquals(listOf(MacosCommand("audio-ready")), worker.snapshotReceivedFragment())

                val workerReply = binding.request<MacosPong>(MacosPing(22), timeoutMs = 5_000L)
                assertEquals(MacosPong(22, "worker:com.test.macos.SettingsFragment"), workerReply)

                channel.disconnect()
                waitUntil {
                    !worker.isFragmentActive<MacosSettingsFragment>() &&
                        worker.snapshotDisconnectedFragments().contains("com.test.macos.SettingsFragment")
                }
                assertFalse(worker.isFragmentActive<MacosSettingsFragment>())
                binding.dispose()
            } finally {
                worker.performStop()
                host.shutdown()
            }
            assertTrue(
                access(resolvedSocketPath, F_OK) != 0,
                "AF_UNIX socket file must be unlinked on host shutdown"
            )
            assertEquals(null, DesktopOsHelpers.readTextFileOrNull(endpointFile))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(tempDir, error = null)
        }
    }

    @Test
    fun macosXpcAnonymousEndpointRoundTripsAuthMessagesRequestsLargePayloadAndDisconnect() = runBlocking {
        val authToken = DesktopOsHelpers.generateRandomHexToken(32)
        val worker = MacosIpcWorker()
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sessionLock = AtomicBoolean(false)
        var activeServerConn: DesktopIpcConnection? = null
        val badAuthRejected = AtomicBoolean(false)

        fun <T> withSessionLock(block: () -> T): T {
            while (!sessionLock.compareAndSet(expectedValue = false, newValue = true)) {}
            return try {
                block()
            } finally {
                sessionLock.store(false)
            }
        }

        val listenerPair = MacosXpcListenerSupport.startAnonymousListener { serverConn ->
            ioScope.launch {
                var authedClientId: String? = null
                try {
                    val authBytes = serverConn.receiveBody(timeoutMs = 3_000)
                    val authEnv = authBytes?.let { ServiceWorkerFrameCodec.decodeBinaryBody(it) }
                    if (authEnv == null ||
                        authEnv.kind != ServiceWorkerEnvelope.KIND_AUTH ||
                        authEnv.payloadJson != authToken
                    ) {
                        badAuthRejected.store(true)
                        serverConn.sendBody(
                            ServiceWorkerFrameCodec.encodeBinaryBody(
                                ServiceWorkerEnvelope(
                                    kind = ServiceWorkerEnvelope.KIND_AUTH_ERR,
                                    error = "Invalid XPC token"
                                )
                            )
                        )
                        serverConn.close()
                        return@launch
                    }
                    val cid = authEnv.clientId.ifBlank { "xpc-client-1" }
                    authedClientId = cid
                    withSessionLock { activeServerConn = serverConn }
                    serverConn.sendBody(
                        ServiceWorkerFrameCodec.encodeBinaryBody(
                            ServiceWorkerEnvelope(kind = ServiceWorkerEnvelope.KIND_AUTH_OK, clientId = cid)
                        )
                    )
                    worker.dispatchClientConnected(cid)
                    while (!serverConn.isClosed) {
                        val body = serverConn.receiveBody(timeoutMs = 100) ?: continue
                        val env = ServiceWorkerFrameCodec.decodeBinaryBody(body) ?: continue
                        worker.dispatchIncomingEnvelope(cid, env)
                        if (env.kind == ServiceWorkerEnvelope.KIND_BYE) {
                            break
                        }
                    }
                } finally {
                    val cid = authedClientId
                    if (cid != null) {
                        withSessionLock {
                            if (activeServerConn === serverConn) {
                                activeServerConn = null
                            }
                        }
                        worker.dispatchClientDisconnected(cid)
                    }
                    serverConn.close()
                }
            }
        }
        assertNotNull(listenerPair, "Anonymous kernel XPC listener must start")
        val (listenerHandle, endpoint) = listenerPair

        val runtimeHost = object : ServiceWorkerRuntimeHost {
            override val backendName: String = "MacOS"
            override val supportsMultipleWindows: Boolean = true
            override val supportsFragmentLaunch: Boolean = true
            override val supportsDialogFragments: Boolean = true
            override val supportsSystemTray: Boolean = true
            override val supportsOngoingNotification: Boolean = false
            override val supportsAutostart: Boolean = true
            override fun updateNotification(
                title: String,
                text: String,
                subText: String?,
                icon: com.nexustools.genericui.resources.ResourceID?,
                actions: List<ServiceWorkerNotificationAction>
            ) {}
            override fun updateTray(
                iconPathOrUrl: String?,
                iconResource: com.nexustools.genericui.resources.ResourceID?,
                tooltip: String?,
                badgeText: String?,
                menu: com.nexustools.genericui.component.shell.ContextMenu?,
                onClick: (() -> Unit)?,
                macosIconPathOrUrl: String?
            ) {}
            override fun openMainWindow(route: String?) {}
            override fun closeMainWindow() {}
            override fun requestLaunchFragment(request: ServiceWorkerFragmentLaunchRequest) {}
            override fun requestCloseFragment(fragmentId: String, instanceId: String?) {}
            override fun stopSelf() {}
            override fun exitApplication(exitCode: Int) {}
            override fun isAutostartEnabled(): Boolean = false
            override fun setAutostartEnabled(enabled: Boolean): Boolean = false
            override suspend fun sendToClient(clientId: String, envelope: ServiceWorkerEnvelope): Boolean {
                val conn = withSessionLock { activeServerConn } ?: return false
                return conn.sendBody(ServiceWorkerFrameCodec.encodeBinaryBody(envelope))
            }
            override suspend fun broadcastEnvelope(envelope: ServiceWorkerEnvelope): Int {
                val conn = withSessionLock { activeServerConn } ?: return 0
                return if (conn.sendBody(ServiceWorkerFrameCodec.encodeBinaryBody(envelope))) 1 else 0
            }
            override fun disconnectClient(clientId: String) {
                withSessionLock { activeServerConn }?.close()
            }
        }

        val options = ServiceWorkerOptions(appId = "com.test.macos.xpc", appName = "MacosXpcTest", hasWorker = true)
        worker.attachAndStart(runtimeHost, options)

        var xpcClientConn: DesktopIpcConnection? = null
        val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            // 1. Bad token rejection
            val badConn = MacosXpcListenerSupport.connectAnonymousEndpoint(endpoint)
            assertNotNull(badConn)
            assertTrue(
                badConn.sendBody(
                    ServiceWorkerFrameCodec.encodeBinaryBody(
                        ServiceWorkerEnvelope(
                            kind = ServiceWorkerEnvelope.KIND_AUTH,
                            clientId = "bad-xpc-client",
                            payloadJson = "wrong-token"
                        )
                    )
                )
            )
            val errReplyBytes = badConn.receiveBody(timeoutMs = 3_000)
            val errReply = errReplyBytes?.let { ServiceWorkerFrameCodec.decodeBinaryBody(it) }
            assertNotNull(errReply)
            assertEquals(ServiceWorkerEnvelope.KIND_AUTH_ERR, errReply.kind)
            waitUntil { badAuthRejected.load() && badConn.isClosed }
            badConn.close()

            // 2. Valid token handshake + bidirectional typed messages, requests, >256KB payload, disconnect
            val xpcConnected = AtomicBoolean(false)
            val xpcRuntime = object : ServiceWorkerPlatformRuntime {
                @Volatile
                private var onIncoming: ((ServiceWorkerEnvelope) -> Unit)? = null
                @Volatile
                private var onConnState: ((Boolean) -> Unit)? = null

                override val isSupported: Boolean = true
                override val supportsMultipleWindows: Boolean = true
                override val supportsFragmentLaunch: Boolean = true
                override val supportsDialogFragments: Boolean = true
                override val supportsSystemTray: Boolean = true
                override val supportsOngoingNotification: Boolean = false
                override val supportsAutostart: Boolean = true
                override fun configure(options: ServiceWorkerOptions) {}
                override fun isClientConnected(): Boolean = xpcConnected.load() && xpcClientConn?.isClosed == false

                override suspend fun connectClient(
                    options: ServiceWorkerOptions,
                    onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
                    onConnectionChanged: (Boolean) -> Unit
                ): Boolean {
                    onIncoming = onIncomingEnvelope
                    onConnState = onConnectionChanged
                    val conn = MacosXpcListenerSupport.connectAnonymousEndpoint(endpoint) ?: return false
                    val auth = ServiceWorkerEnvelope(
                        kind = ServiceWorkerEnvelope.KIND_AUTH,
                        clientId = "xpc-ui-client",
                        payloadJson = authToken
                    )
                    if (!conn.sendBody(ServiceWorkerFrameCodec.encodeBinaryBody(auth))) {
                        conn.close()
                        return false
                    }
                    val okBytes = conn.receiveBody(timeoutMs = 3_000)
                    val okEnv = okBytes?.let { ServiceWorkerFrameCodec.decodeBinaryBody(it) }
                    if (okEnv == null || okEnv.kind != ServiceWorkerEnvelope.KIND_AUTH_OK) {
                        conn.close()
                        return false
                    }
                    xpcClientConn = conn
                    xpcConnected.store(true)
                    onConnectionChanged(true)
                    clientScope.launch {
                        try {
                            while (xpcConnected.load() && !conn.isClosed) {
                                val body = conn.receiveBody(timeoutMs = 100) ?: continue
                                val env = ServiceWorkerFrameCodec.decodeBinaryBody(body) ?: continue
                                onIncoming?.invoke(env)
                                if (env.kind == ServiceWorkerEnvelope.KIND_BYE) break
                            }
                        } finally {
                            xpcConnected.store(false)
                            conn.close()
                            onConnState?.invoke(false)
                        }
                    }
                    return true
                }

                override suspend fun disconnectClient() {
                    val conn = xpcClientConn
                    xpcConnected.store(false)
                    xpcClientConn = null
                    if (conn != null && !conn.isClosed) {
                        conn.sendBody(
                            ServiceWorkerFrameCodec.encodeBinaryBody(
                                ServiceWorkerEnvelope(kind = ServiceWorkerEnvelope.KIND_BYE, clientId = "xpc-ui-client")
                            )
                        )
                        conn.close()
                    }
                    onConnState?.invoke(false)
                }

                override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean {
                    val conn = xpcClientConn ?: return false
                    if (!xpcConnected.load() || conn.isClosed) return false
                    return conn.sendBody(ServiceWorkerFrameCodec.encodeBinaryBody(envelope))
                }

                override fun activeWorker(): ServiceWorker = worker
                override fun currentBackendTarget(): BackendTarget = BackendTarget.MacOS
            }

            ServiceWorkerExtension.install(options)
            ServiceWorkerExtension.installRuntimeOverrideForTests(xpcRuntime)
            val channel = ServiceWorkerExtension.channel
            val clientReceived = mutableListOf<MacosCommand>()
            val msgSub = channel.onMessage<MacosCommand> { msg -> clientReceived.add(msg) }
            val reqSub = channel.onRequest<MacosPing> { req, reply ->
                reply.send(MacosPong(req.seq, "client:xpc"))
            }

            assertTrue(channel.connect())
            waitUntil { worker.snapshotConnectedClients().isNotEmpty() }
            val workerClientHandle = worker.snapshotConnectedClients().first()

            // Bidirectional messages
            channel.send(MacosCommand("xpc-to-worker"))
            waitUntil { worker.snapshotReceivedClient().size == 1 }
            assertEquals(listOf(MacosCommand("xpc-to-worker")), worker.snapshotReceivedClient())

            assertEquals(1, worker.broadcast(MacosCommand("xpc-to-client")))
            waitUntil { clientReceived.size == 1 }
            assertEquals(listOf(MacosCommand("xpc-to-client")), clientReceived)

            // Bidirectional requests/replies
            val workerReply = channel.request<MacosPong>(MacosPing(77), timeoutMs = 5_000L)
            assertEquals(MacosPong(77, "worker:xpc"), workerReply)

            val clientReply = workerClientHandle.request<MacosPong>(MacosPing(88), timeoutMs = 5_000L)
            assertEquals(MacosPong(88, "client:xpc"), clientReply)

            // Large > 256 KB payload in both directions
            val largePayload = "X".repeat(300 * 1024)
            assertTrue(largePayload.encodeToByteArray().size > 256 * 1024)

            channel.send(MacosCommand(largePayload))
            waitUntil { worker.snapshotReceivedClient().size == 2 }
            assertEquals(largePayload, worker.snapshotReceivedClient().last().name)

            assertEquals(1, worker.broadcast(MacosCommand(largePayload)))
            waitUntil { clientReceived.size == 2 }
            assertEquals(largePayload, clientReceived.last().name)

            // Disconnect cleanup
            channel.disconnect()
            waitUntil { !channel.isConnected && worker.snapshotDisconnectedClients().contains("xpc-ui-client") }
            assertFalse(channel.isConnected)
            msgSub.dispose()
            reqSub.dispose()
        } finally {
            xpcClientConn?.close()
            clientScope.cancel()
            worker.performStop()
            listenerHandle.close()
            ioScope.cancel()
        }
    }

    @Test
    fun launchAgentPlistXmlIncludesMachServicesMatchingServiceWorkerLabel() {
        val xml = DesktopOsHelpers.buildLaunchAgentPlistXmlForTest(
            appId = "com.example.My App&Co",
            targetExecutablePath = "/Applications/MyApp.app/Contents/MacOS/serviceworker",
            args = listOf("--background", "--tag=<test>")
        )
        val expectedLabel = "com.example.My_App_Co.serviceworker"
        assertTrue(xml.contains("<key>Label</key>\n    <string>$expectedLabel</string>"))
        assertTrue(
            xml.contains(
                """
    <key>MachServices</key>
    <dict>
        <key>$expectedLabel</key>
        <true/>
    </dict>""".trimStart('\n')
            ),
            "LaunchAgent plist XML must register MachServices for $expectedLabel:\n$xml"
        )
        assertTrue(xml.contains("<string>/Applications/MyApp.app/Contents/MacOS/serviceworker</string>"))
        assertTrue(xml.contains("<string>--tag=&lt;test&gt;</string>"))
        assertTrue(xml.contains("<key>RunAtLoad</key>\n    <true/>"))
    }

    @Test
    fun macosDefaultRuntimeDirWithoutLaunchdFallsBackCleanlyFromXpcToUnixSocket() = runBlocking {
        val testAppId = "com.test.macos.defaultdir.${getpid()}.${kotlin.random.Random.nextInt(100_000, 999_999)}"
        val runtimeDir = DesktopOsHelpers.runtimeDataDirectory(testAppId)
        val endpointFile = "$runtimeDir/serviceworker-endpoint.json"
        val worker = MacosIpcWorker()
        val options = ServiceWorkerOptions(
            appId = testAppId,
            appName = "MacosDefaultDirTest",
            hasWorker = true
        )
        val host = DesktopServiceWorkerHost(
            options = options,
            worker = worker,
            endpointFilePath = endpointFile,
            mainBinaryBaseName = "MacosApp",
            swingMainClassFqn = "",
            stopEventLoop = {},
            lockRelease = {}
        )
        assertTrue(host.start(), "Host with default runtimeDir outside launchd must fall back cleanly to AF_UNIX")
        worker.attachAndStart(host, options)
        val client = DesktopServiceWorkerChannelClient()
        try {
            assertEquals(DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET, host.boundTransport)
            val descriptor = DesktopEndpointDescriptor.parse(DesktopOsHelpers.readTextFileOrNull(endpointFile))
            assertNotNull(descriptor)
            assertEquals(DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET, descriptor.transport)
            assertEquals(host.boundAddress, descriptor.address)

            ServiceWorkerExtension.install(options)
            val runtime = object : ServiceWorkerPlatformRuntime {
                override val isSupported: Boolean = true
                override val supportsMultipleWindows: Boolean = true
                override val supportsFragmentLaunch: Boolean = true
                override val supportsDialogFragments: Boolean = true
                override val supportsSystemTray: Boolean = true
                override val supportsOngoingNotification: Boolean = false
                override val supportsAutostart: Boolean = true
                override fun configure(options: ServiceWorkerOptions) {}
                override fun isClientConnected(): Boolean = client.isConnected
                override suspend fun connectClient(
                    options: ServiceWorkerOptions,
                    onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
                    onConnectionChanged: (Boolean) -> Unit
                ): Boolean = client.connectToEndpoint(descriptor, onIncomingEnvelope, onConnectionChanged)

                override suspend fun disconnectClient() = client.disconnect()
                override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean =
                    client.sendEnvelope(envelope)

                override fun activeWorker(): ServiceWorker = worker
                override fun currentBackendTarget(): BackendTarget = BackendTarget.MacOS
            }
            ServiceWorkerExtension.installRuntimeOverrideForTests(runtime)
            val channel = ServiceWorkerExtension.channel
            assertTrue(channel.connect())

            val reply = channel.request<MacosPong>(MacosPing(42), timeoutMs = 5_000L)
            assertEquals(MacosPong(42, "worker:xpc"), reply)
            channel.disconnect()
        } finally {
            client.disconnect()
            worker.performStop()
            host.shutdown()
            NSFileManager.defaultManager.removeItemAtPath(runtimeDir, error = null)
        }
    }

    @Test
    fun macosControlClientQueriesShouldOpenMainWindowOverUnixSocket() = runBlocking {
        val tempDir = createTempDir("genericui-macos-query-open")
        try {
            val endpointFile = "$tempDir/serviceworker-endpoint.json"
            var allowOpen = false
            val worker = object : ServiceWorker() {
                override fun onMainAppLaunchedWhileRunning(): Boolean = allowOpen
            }
            val options = ServiceWorkerOptions(
                appId = "com.test.macos.queryopen.${getpid()}",
                appName = "MacosQueryOpen",
                hasWorker = true
            )
            val host = DesktopServiceWorkerHost(
                options = options,
                worker = worker,
                endpointFilePath = endpointFile,
                mainBinaryBaseName = "MacosApp",
                swingMainClassFqn = "",
                stopEventLoop = {},
                lockRelease = {}
            )
            assertTrue(host.start())
            worker.attachAndStart(host, options)
            try {
                val descriptor = DesktopEndpointDescriptor.parse(DesktopOsHelpers.readTextFileOrNull(endpointFile))
                assertNotNull(descriptor)

                fun queryOnce(): String? {
                    val conn = DesktopIpcClient.connect(descriptor.transport, descriptor.address, timeoutMs = 1_500)
                        ?: return null
                    return try {
                        val cid = "${DESKTOP_CONTROL_CLIENT_PREFIX}test-${getpid()}"
                        assertTrue(
                            conn.sendBody(
                                ServiceWorkerFrameCodec.encodeBinaryBody(
                                    ServiceWorkerEnvelope(
                                        kind = ServiceWorkerEnvelope.KIND_AUTH,
                                        clientId = cid,
                                        payloadJson = descriptor.token
                                    )
                                )
                            )
                        )
                        val authReply = conn.receiveBody(1_500)?.let { ServiceWorkerFrameCodec.decodeBinaryBody(it) }
                        assertEquals(ServiceWorkerEnvelope.KIND_AUTH_OK, authReply?.kind)
                        assertTrue(
                            conn.sendBody(
                                ServiceWorkerFrameCodec.encodeBinaryBody(
                                    ServiceWorkerEnvelope(
                                        kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                                        clientId = cid,
                                        typeId = ServiceWorkerEnvelope.CONTROL_QUERY_SHOULD_OPEN_MAIN_WINDOW,
                                        payloadJson = "null"
                                    )
                                )
                            )
                        )
                        val reply = conn.receiveBody(1_500)?.let { ServiceWorkerFrameCodec.decodeBinaryBody(it) }
                        conn.sendBody(
                            ServiceWorkerFrameCodec.encodeBinaryBody(
                                ServiceWorkerEnvelope(kind = ServiceWorkerEnvelope.KIND_BYE, clientId = cid)
                            )
                        )
                        assertEquals(ServiceWorkerEnvelope.KIND_MESSAGE, reply?.kind)
                        assertEquals(ServiceWorkerEnvelope.CONTROL_QUERY_SHOULD_OPEN_MAIN_WINDOW, reply?.typeId)
                        reply?.payloadJson
                    } finally {
                        conn.close()
                    }
                }

                assertEquals("false", queryOnce())
                allowOpen = true
                assertEquals("true", queryOnce())
            } finally {
                worker.performStop()
                host.shutdown()
            }
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(tempDir, error = null)
        }
    }
}
