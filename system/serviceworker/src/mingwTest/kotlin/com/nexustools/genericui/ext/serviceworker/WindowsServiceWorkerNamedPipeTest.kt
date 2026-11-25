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
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import platform.windows.GetCurrentProcessId
import platform.windows.GetTempPathW
import platform.windows.MAX_PATH
import platform.windows.RemoveDirectoryW
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowsServiceWorkerNamedPipeTest {

    private class WinSettingsFragment : Fragment()

    private data class WinCommand(val name: String)
    private data class WinPing(val seq: Int)
    private data class WinPong(val seq: Int, val origin: String)

    private class WinPipeWorker : ServiceWorker() {
        private val lock = AtomicBoolean(false)
        val receivedFromFragment = mutableListOf<WinCommand>()
        val disconnectedIds = mutableListOf<String>()

        override fun onStart() {
            onFragmentMessage<WinSettingsFragment, WinCommand> { _, msg ->
                withLock { receivedFromFragment.add(msg) }
            }
            onFragmentRequest<WinSettingsFragment, WinPing, WinPong> { handle, req ->
                WinPong(req.seq, "worker:${handle.fragmentId}")
            }
        }

        override fun onFragmentDisconnected(handle: ServiceWorkerFragmentHandle) {
            withLock { disconnectedIds.add(handle.fragmentId) }
        }

        fun snapshotReceived(): List<WinCommand> = withLock { receivedFromFragment.toList() }
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

    private class WinPipeRuntime(
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
        override fun currentBackendTarget(): BackendTarget = BackendTarget.Windows
    }

    private val codecIds = listOf(
        "win.WinCommand",
        "win.WinPing",
        "win.WinPong"
    )

    private fun createTempDir(prefix: String): String {
        val base = memScoped {
            val buf = allocArray<UShortVar>(MAX_PATH * 2)
            val len = GetTempPathW((MAX_PATH * 2).toUInt(), buf).toInt()
            if (len > 0) buf.toKString().trimEnd('\\', '/') else "C:\\Temp"
        }
        val path = "$base\\$prefix-${GetCurrentProcessId()}-${Random.nextInt(100_000, 999_999)}"
        assertTrue(DesktopOsHelpers.ensureDirectoryExists(path))
        return path
    }

    private fun deleteTempDir(dir: String, vararg knownFiles: String) {
        for (file in knownFiles) {
            DesktopOsHelpers.deleteFileIfExists("$dir\\$file")
        }
        RemoveDirectoryW(dir)
    }

    private fun registerCodecsAndFragments() {
        FragmentRegistry.clear()
        FragmentRegistry.register(WinSettingsFragment::class) { WinSettingsFragment() }
        FragmentRegistry.registerMetadata(
            fragmentClass = WinSettingsFragment::class,
            fragmentId = "com.test.win.SettingsFragment",
            route = "/win-settings"
        )

        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<WinCommand> {
            override val typeId = "win.WinCommand"
            override val messageClass = WinCommand::class
            override fun encode(value: WinCommand) = PreferenceElement.ofObject(
                mapOf("name" to PreferenceElement.ofString(value.name))
            )
            override fun decode(element: PreferenceElement) = WinCommand(
                element.mapEntries["name"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<WinPing> {
            override val typeId = "win.WinPing"
            override val messageClass = WinPing::class
            override fun encode(value: WinPing) = PreferenceElement.ofObject(
                mapOf("seq" to PreferenceElement.ofInt(value.seq))
            )
            override fun decode(element: PreferenceElement) = WinPing(
                element.mapEntries["seq"]?.asInt() ?: 0
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<WinPong> {
            override val typeId = "win.WinPong"
            override val messageClass = WinPong::class
            override fun encode(value: WinPong) = PreferenceElement.ofObject(
                mapOf(
                    "seq" to PreferenceElement.ofInt(value.seq),
                    "origin" to PreferenceElement.ofString(value.origin)
                )
            )
            override fun decode(element: PreferenceElement) = WinPong(
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
    fun buildPipeNameIsDeterministicAndIsolatedAcrossRuntimeDirs() {
        val dirA1 = "C:\\Users\\Alice\\AppData\\Local\\GenericUI\\AppA"
        val dirA2 = "c:/users/alice/appdata/local/genericui/appa/"
        val dirB = "C:\\Users\\Alice\\AppData\\Local\\GenericUI\\AppB"

        val nameA1 = WindowsNamedPipeSecurity.buildPipeName("com.example.App", dirA1)
        val nameA2 = WindowsNamedPipeSecurity.buildPipeName("  COM.EXAMPLE.APP ", dirA2)
        val nameB = WindowsNamedPipeSecurity.buildPipeName("com.example.App", dirB)
        val nameDifferentApp = WindowsNamedPipeSecurity.buildPipeName("com.example.Other", dirA1)

        assertTrue(nameA1.startsWith("\\\\.\\pipe\\genericui."), "Unexpected pipe prefix: $nameA1")
        assertTrue(nameA1.endsWith(".sw"), "Unexpected pipe suffix: $nameA1")
        assertEquals(nameA1, nameA2, "Normalized appId + runtimeDir must hash identically")
        assertNotEquals(nameA1, nameB, "Different runtimeDirs must produce distinct pipe names")
        assertNotEquals(nameA1, nameDifferentApp, "Different appIds must produce distinct pipe names")

        val parts = nameA1.removePrefix("\\\\.\\pipe\\genericui.").removeSuffix(".sw").split('.')
        assertEquals(2, parts.size, "Pipe name must contain <sidHash8>.<scopeHash16>")
        assertEquals(8, parts[0].length, "User SID hash must be 8 hex chars")
        assertEquals(16, parts[1].length, "Scope hash must be 16 hex chars")
        assertTrue(parts[0].all { it in '0'..'9' || it in 'a'..'f' })
        assertTrue(parts[1].all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun currentUserSecurityAttributesConstructsValidDescriptor() {
        var sawNonNullAttributes = false
        val returned = WindowsNamedPipeSecurity.withCurrentUserPipeSecurityAttributes { saPtr ->
            sawNonNullAttributes = saPtr != null
            42
        }
        assertEquals(42, returned)
        assertTrue(sawNonNullAttributes, "Current-user DACL SECURITY_ATTRIBUTES must construct cleanly")
    }

    @Test
    fun namedPipeServerAndClientSupportConcurrentConnectionsAndCleanShutdown() = runDesktopBlockingTest {
        val tempDir = createTempDir("genericui-win-pipe-conc")
        val server = DesktopIpcServer()
        try {
            val serverConns = mutableListOf<DesktopIpcConnection>()
            val connLock = AtomicBoolean(false)
            assertTrue(
                server.start("com.test.win.concurrent", tempDir) { conn ->
                    while (!connLock.compareAndSet(expectedValue = false, newValue = true)) {}
                    try {
                        serverConns.add(conn)
                    } finally {
                        connLock.store(false)
                    }
                    val req = conn.receiveBody(timeoutMs = 3_000)
                    if (req != null) {
                        val reply = ("ack:" + req.decodeToString()).encodeToByteArray()
                        conn.sendBody(reply)
                    }
                },
                "Named pipe server must start"
            )
            assertEquals(DesktopEndpointDescriptor.TRANSPORT_WINDOWS_NAMED_PIPE, server.transportKind)
            assertTrue(server.endpointAddress.startsWith("\\\\.\\pipe\\genericui."))

            val results = withTimeout(8_000L) {
                (1..4).map { idx ->
                    async(Dispatchers.IO) {
                        val client = DesktopIpcClient.connect(
                            transportKind = server.transportKind,
                            address = server.endpointAddress,
                            timeoutMs = 4_000
                        )
                        assertNotNull(client, "Client $idx must connect to named pipe")
                        try {
                            assertTrue(client.sendBody("hello-$idx".encodeToByteArray()))
                            val reply = client.receiveBody(timeoutMs = 3_000)
                            assertNotNull(reply, "Client $idx must receive echo reply")
                            reply.decodeToString()
                        } finally {
                            client.close()
                        }
                    }
                }.awaitAll()
            }
            assertEquals(listOf("ack:hello-1", "ack:hello-2", "ack:hello-3", "ack:hello-4"), results)

            while (!connLock.compareAndSet(expectedValue = false, newValue = true)) {}
            try {
                serverConns.forEach { it.close() }
                serverConns.clear()
            } finally {
                connLock.store(false)
            }

            val pipeAddress = server.endpointAddress
            server.close()
            assertNull(
                DesktopIpcClient.connect(
                    transportKind = DesktopEndpointDescriptor.TRANSPORT_WINDOWS_NAMED_PIPE,
                    address = pipeAddress,
                    timeoutMs = 200
                ),
                "Connecting after server.close() must fail"
            )
        } finally {
            server.close()
            deleteTempDir(tempDir)
        }
    }

    @Test
    fun namedPipeHostRejectsBadTokenAndRoundTripsFragmentIpcAndCleanShutdown() = runDesktopBlockingTest {
        val tempDir = createTempDir("genericui-win-pipe-e2e")
        val endpointFile = "$tempDir\\serviceworker-endpoint.json"
        val worker = WinPipeWorker()
        val options = ServiceWorkerOptions(
            appId = "com.test.win.pipe.e2e",
            appName = "WinPipeTest",
            hasWorker = true
        )
        val host = DesktopServiceWorkerHost(
            options = options,
            worker = worker,
            endpointFilePath = endpointFile,
            mainBinaryBaseName = "WinApp",
            swingMainClassFqn = "",
            stopEventLoop = {},
            lockRelease = {}
        )
        try {
            assertTrue(host.start(), "DesktopServiceWorkerHost must start named pipe server")
            worker.attachAndStart(host, options)

            val rawEndpoint = DesktopOsHelpers.readTextFileOrNull(endpointFile)
            assertNotNull(rawEndpoint)
            assertTrue(
                rawEndpoint.contains(DesktopEndpointDescriptor.TRANSPORT_WINDOWS_NAMED_PIPE),
                "Endpoint JSON must advertise named_pipe transport"
            )
            val descriptor = DesktopEndpointDescriptor.parse(rawEndpoint)
            assertNotNull(descriptor)
            assertEquals(DesktopEndpointDescriptor.TRANSPORT_WINDOWS_NAMED_PIPE, descriptor.transport)

            val pipeAddress = WindowsNamedPipeSecurity.buildPipeName(options.appId, tempDir)
            assertEquals(pipeAddress, descriptor.address)

            // Verify bad auth token rejection over raw named pipe
            val badClient = DesktopIpcClient.connect(
                transportKind = DesktopEndpointDescriptor.TRANSPORT_WINDOWS_NAMED_PIPE,
                address = pipeAddress,
                timeoutMs = 4_000
            )
            assertNotNull(badClient, "Raw client must reach named pipe endpoint for auth test")
            try {
                val badAuth = ServiceWorkerEnvelope(
                    kind = ServiceWorkerEnvelope.KIND_AUTH,
                    clientId = "bad-client",
                    payloadJson = "wrong-token"
                )
                assertTrue(badClient.sendBody(ServiceWorkerFrameCodec.encodeBinaryBody(badAuth)))
                val authErrBytes = withTimeout(5_000L) {
                    badClient.receiveBody(timeoutMs = 3_000)
                }
                val authErrEnv = authErrBytes?.let { ServiceWorkerFrameCodec.decodeBinaryBody(it) }
                assertNotNull(authErrEnv)
                assertEquals(ServiceWorkerEnvelope.KIND_AUTH_ERR, authErrEnv.kind)
            } finally {
                badClient.close()
            }

            // Full bidirectional DesktopServiceWorkerHost <-> DesktopServiceWorkerChannelClient round-trip
            ServiceWorkerExtension.install(options)
            ServiceWorkerExtension.installRuntimeOverrideForTests(WinPipeRuntime(descriptor, worker))
            val channel = ServiceWorkerExtension.channel
            assertTrue(withTimeout(5_000L) { channel.connect() })

            val fragment = WinSettingsFragment()
            val fragReceived = mutableListOf<WinCommand>()
            val binding = ServiceWorkerFragmentBinding(
                fragment = fragment,
                channel = channel
            ).apply {
                onMessage<WinCommand> { msg -> fragReceived.add(msg) }
                onRequest<WinPing, WinPong> { req ->
                    WinPong(req.seq, "fragment:windows")
                }
                start()
            }

            val handleDeferred = async {
                worker.startFragmentAndAwait<WinSettingsFragment>(
                    args = FragmentBundle.of("pane" to "display"),
                    timeoutMs = 5_000L
                )
            }
            val handle = withTimeout(5_000L) { handleDeferred.await() }
            assertEquals("com.test.win.SettingsFragment", handle.fragmentId)
            assertTrue(worker.isFragmentActive<WinSettingsFragment>())

            worker.sendToFragment<WinSettingsFragment>(WinCommand("apply-hdr"))
            waitUntil { fragReceived.size == 1 }
            assertEquals(listOf(WinCommand("apply-hdr")), fragReceived)

            val fragReply = withTimeout(5_000L) {
                worker.requestFromFragment<WinSettingsFragment, WinPong>(
                    WinPing(17),
                    timeoutMs = 5_000L
                )
            }
            assertEquals(WinPong(17, "fragment:windows"), fragReply)

            binding.send(WinCommand("hdr-ready"))
            waitUntil { worker.snapshotReceived().size == 1 }
            assertEquals(listOf(WinCommand("hdr-ready")), worker.snapshotReceived())

            val workerReply = withTimeout(5_000L) {
                binding.request<WinPong>(WinPing(29), timeoutMs = 5_000L)
            }
            assertEquals(WinPong(29, "worker:com.test.win.SettingsFragment"), workerReply)

            channel.disconnect()
            waitUntil {
                !worker.isFragmentActive<WinSettingsFragment>() &&
                    worker.snapshotDisconnected().contains("com.test.win.SettingsFragment")
            }
            assertFalse(worker.isFragmentActive<WinSettingsFragment>())
            binding.dispose()

            worker.performStop()
            host.shutdown()

            assertNull(
                DesktopOsHelpers.readTextFileOrNull(endpointFile),
                "Clean shutdown must remove endpoint descriptor file"
            )
            assertNull(
                DesktopIpcClient.connect(
                    transportKind = DesktopEndpointDescriptor.TRANSPORT_WINDOWS_NAMED_PIPE,
                    address = pipeAddress,
                    timeoutMs = 200
                ),
                "Connecting after host.shutdown() must return null"
            )
        } finally {
            worker.performStop()
            host.shutdown()
            deleteTempDir(tempDir, "serviceworker-endpoint.json")
        }
    }
}
