@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.enums.system.BackendTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceWorkerFragmentIpcE2ETest {

    private class SettingsIpcFragment : Fragment()
    private class DetailsIpcFragment : Fragment()

    private data class IpcSettingsCommand(val action: String, val value: String)
    private data class IpcUiSnapshotReq(val includeTheme: Boolean)
    private data class IpcUiSnapshotResp(val theme: String, val section: String)
    private data class IpcFragMetric(val count: Int)
    private data class IpcWorkerLookupReq(val key: String)
    private data class IpcWorkerLookupResp(val resolved: String)

    private class IpcFragmentWorker : ServiceWorker() {
        private val lock = AtomicBoolean(false)
        val connectedHandles = mutableListOf<ServiceWorkerFragmentHandle>()
        val disconnectedHandles = mutableListOf<ServiceWorkerFragmentHandle>()
        val receivedMetrics = mutableListOf<Pair<String, IpcFragMetric>>()

        override fun onStart() {
            onFragmentMessage<SettingsIpcFragment, IpcFragMetric> { handle, msg ->
                withLock { receivedMetrics.add(handle.fragmentId to msg) }
            }
            onFragmentRequest<SettingsIpcFragment, IpcWorkerLookupReq, IpcWorkerLookupResp> { handle, req ->
                IpcWorkerLookupResp("${handle.fragmentId}:${req.key}")
            }
        }

        override fun onFragmentConnected(handle: ServiceWorkerFragmentHandle) {
            withLock { connectedHandles.add(handle) }
        }

        override fun onFragmentDisconnected(handle: ServiceWorkerFragmentHandle) {
            withLock { disconnectedHandles.add(handle) }
        }

        fun snapshotConnected(): List<ServiceWorkerFragmentHandle> = withLock { connectedHandles.toList() }
        fun snapshotDisconnected(): List<ServiceWorkerFragmentHandle> = withLock { disconnectedHandles.toList() }
        fun snapshotMetrics(): List<Pair<String, IpcFragMetric>> = withLock { receivedMetrics.toList() }

        private inline fun <T> withLock(block: () -> T): T {
            while (!lock.compareAndSet(expectedValue = false, newValue = true)) {}
            return try {
                block()
            } finally {
                lock.store(false)
            }
        }
    }

    private class IpcClientBackedRuntime(
        private val endpointDescriptor: DesktopEndpointDescriptor,
        private val worker: ServiceWorker,
        private val ipcClient: DesktopServiceWorkerChannelClient = DesktopServiceWorkerChannelClient()
    ) : ServiceWorkerPlatformRuntime {
        private val lock = AtomicBoolean(false)
        val receivedControlTypes = mutableListOf<String>()
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
            descriptor = endpointDescriptor,
            onIncomingEnvelope = { env ->
                if (env.typeId.startsWith(DESKTOP_CONTROL_CLIENT_PREFIX)) {
                    while (!lock.compareAndSet(expectedValue = false, newValue = true)) {}
                    try {
                        receivedControlTypes.add(env.typeId)
                    } finally {
                        lock.store(false)
                    }
                }
                onIncomingEnvelope(env)
            },
            onConnectionStateChanged = onConnectionChanged
        )

        fun snapshotControlTypes(): List<String> {
            while (!lock.compareAndSet(expectedValue = false, newValue = true)) {}
            return try {
                receivedControlTypes.toList()
            } finally {
                lock.store(false)
            }
        }

        override suspend fun disconnectClient() = ipcClient.disconnect()

        override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean =
            ipcClient.sendEnvelope(envelope)

        override fun activeWorker(): ServiceWorker = worker
        override fun currentBackendTarget(): BackendTarget = BackendTarget.Swing
    }

    private val codecIds = listOf(
        "ipctest.SettingsCommand",
        "ipctest.UiSnapshotReq",
        "ipctest.UiSnapshotResp",
        "ipctest.FragMetric",
        "ipctest.WorkerLookupReq",
        "ipctest.WorkerLookupResp"
    )

    private fun registerCodecsAndFragments() {
        FragmentRegistry.clear()
        FragmentRegistry.register(SettingsIpcFragment::class) { SettingsIpcFragment() }
        FragmentRegistry.registerMetadata(
            fragmentClass = SettingsIpcFragment::class,
            fragmentId = "com.test.ipc.SettingsFragment",
            route = "/ipc-settings",
            multipleWindows = true
        )
        FragmentRegistry.register(DetailsIpcFragment::class) { DetailsIpcFragment() }
        FragmentRegistry.registerMetadata(
            fragmentClass = DetailsIpcFragment::class,
            fragmentId = "com.test.ipc.DetailsFragment",
            route = "/ipc-details"
        )

        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<IpcSettingsCommand> {
            override val typeId = "ipctest.SettingsCommand"
            override val messageClass = IpcSettingsCommand::class
            override fun encode(value: IpcSettingsCommand) = PreferenceElement.ofObject(
                mapOf(
                    "action" to PreferenceElement.ofString(value.action),
                    "value" to PreferenceElement.ofString(value.value)
                )
            )

            override fun decode(element: PreferenceElement) = IpcSettingsCommand(
                action = element.mapEntries["action"]?.asString().orEmpty(),
                value = element.mapEntries["value"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<IpcUiSnapshotReq> {
            override val typeId = "ipctest.UiSnapshotReq"
            override val messageClass = IpcUiSnapshotReq::class
            override fun encode(value: IpcUiSnapshotReq) = PreferenceElement.ofObject(
                mapOf("includeTheme" to PreferenceElement.ofBoolean(value.includeTheme))
            )

            override fun decode(element: PreferenceElement) = IpcUiSnapshotReq(
                includeTheme = element.mapEntries["includeTheme"]?.asBoolean(false) ?: false
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<IpcUiSnapshotResp> {
            override val typeId = "ipctest.UiSnapshotResp"
            override val messageClass = IpcUiSnapshotResp::class
            override fun encode(value: IpcUiSnapshotResp) = PreferenceElement.ofObject(
                mapOf(
                    "theme" to PreferenceElement.ofString(value.theme),
                    "section" to PreferenceElement.ofString(value.section)
                )
            )

            override fun decode(element: PreferenceElement) = IpcUiSnapshotResp(
                theme = element.mapEntries["theme"]?.asString().orEmpty(),
                section = element.mapEntries["section"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<IpcFragMetric> {
            override val typeId = "ipctest.FragMetric"
            override val messageClass = IpcFragMetric::class
            override fun encode(value: IpcFragMetric) = PreferenceElement.ofObject(
                mapOf("count" to PreferenceElement.ofInt(value.count))
            )

            override fun decode(element: PreferenceElement) = IpcFragMetric(
                count = element.mapEntries["count"]?.asInt() ?: 0
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<IpcWorkerLookupReq> {
            override val typeId = "ipctest.WorkerLookupReq"
            override val messageClass = IpcWorkerLookupReq::class
            override fun encode(value: IpcWorkerLookupReq) = PreferenceElement.ofObject(
                mapOf("key" to PreferenceElement.ofString(value.key))
            )

            override fun decode(element: PreferenceElement) = IpcWorkerLookupReq(
                key = element.mapEntries["key"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<IpcWorkerLookupResp> {
            override val typeId = "ipctest.WorkerLookupResp"
            override val messageClass = IpcWorkerLookupResp::class
            override fun encode(value: IpcWorkerLookupResp) = PreferenceElement.ofObject(
                mapOf("resolved" to PreferenceElement.ofString(value.resolved))
            )

            override fun decode(element: PreferenceElement) = IpcWorkerLookupResp(
                resolved = element.mapEntries["resolved"]?.asString().orEmpty()
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
    }

    @Test
    fun coldSpawnIncludesEncodedFragmentCliArgsCoalescesMidBootAndFlushesOnUnixSocketConnect() = runBlocking {
        val tempDir = Files.createTempDirectory("genericui-sw-frag-cold").toFile()
        try {
            val endpointFile = File(tempDir, "serviceworker-endpoint.json")
            val worker = IpcFragmentWorker()
            val options = ServiceWorkerOptions(
                appId = "com.test.ipc.cold",
                appName = "IpcColdTest",
                hasWorker = true
            )
            val spawns = mutableListOf<Pair<String, List<String>>>()
            val host = DesktopServiceWorkerHost(
                options = options,
                worker = worker,
                endpointFilePath = endpointFile.absolutePath,
                mainBinaryBaseName = "app-main",
                swingMainClassFqn = "",
                stopEventLoop = {},
                lockRelease = {},
                siblingCommandResolver = { base, _ -> "/opt/app/$base" to emptyList() },
                spawnLauncher = { exe, args, _ ->
                    spawns.add(exe to args)
                    true
                }
            )
            assertTrue(host.start())
            assertEquals(DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET, host.boundTransport)
            assertTrue(host.boundAddress.isNotBlank())
            val socketAddress = host.boundAddress
            assertTrue(File(socketAddress).exists())
            worker.attachAndStart(host, options)
            try {
                worker.startFragment<SettingsIpcFragment>(
                    args = FragmentBundle.of("tab" to "network", "limit" to 5),
                    openAdditionalWindow = true
                )
                // Second fragment launch during pending spawn grace window must coalesce cleanly.
                worker.startFragment<DetailsIpcFragment>(
                    args = FragmentBundle.of("itemId" to 42)
                )

                assertEquals(1, spawns.size, "Concurrent cold spawns must coalesce to a single OS spawn.")
                val (exe, args) = spawns.single()
                assertEquals("/opt/app/app-main", exe)
                assertTrue("--open-window" in args)
                assertTrue("--fragment=com.test.ipc.SettingsFragment" in args)
                assertTrue("--route=/ipc-settings" in args)
                assertTrue("--open-additional-window=true" in args)

                val parsedCli = FragmentBundle.fromCommandLineArgs(args.toTypedArray())
                assertEquals("com.test.ipc.SettingsFragment", parsedCli.getString(FragmentBundle.KEY_LAUNCH_FRAGMENT_ID))
                assertEquals("/ipc-settings", parsedCli.getString(FragmentBundle.KEY_LAUNCH_ROUTE))
                assertTrue(parsedCli.getBoolean(FragmentBundle.KEY_LAUNCH_OPEN_ADDITIONAL_WINDOW, false))
                assertEquals("network", parsedCli.getString("tab"))
                assertEquals(5, parsedCli.getInt("limit"))

                // Once UI process connects over AF_UNIX IPC, the coalesced mid-boot fragment launch arrives.
                val descriptor = DesktopEndpointDescriptor.parse(endpointFile.readText(Charsets.UTF_8))!!
                assertEquals(DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET, descriptor.transport)
                val ipcClient = DesktopServiceWorkerChannelClient()
                val arrivedControlPayloads = mutableListOf<ServiceWorkerFragmentLaunchRequest>()
                val arrivedDeferred = CompletableDeferred<Unit>()
                assertTrue(
                    ipcClient.connectToEndpoint(
                        descriptor = descriptor,
                        onIncomingEnvelope = { env ->
                            if (env.typeId == ServiceWorkerEnvelope.CONTROL_LAUNCH_FRAGMENT) {
                                ServiceWorkerFragmentLaunchRequest.fromControlPayloadJson(env.payloadJson)?.let {
                                    arrivedControlPayloads.add(it)
                                    arrivedDeferred.complete(Unit)
                                }
                            }
                        },
                        onConnectionStateChanged = {}
                    )
                )
                withTimeout(5_000L) { arrivedDeferred.await() }
                assertEquals(1, arrivedControlPayloads.size)
                assertEquals("com.test.ipc.DetailsFragment", arrivedControlPayloads.single().fragmentId)
                assertEquals(42, arrivedControlPayloads.single().arguments?.getInt("itemId"))
                ipcClient.disconnect()
            } finally {
                worker.performStop()
                host.shutdown()
            }
            assertFalse(File(socketAddress).exists(), "Unix domain socket file must be removed on host shutdown")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun unixSocketSupportsFullBidirectionalFragmentIpcAndDisconnectCleanup() = runBlocking {
        val tempDir = Files.createTempDirectory("genericui-sw-frag-ipc").toFile()
        try {
            val endpointFile = File(tempDir, "serviceworker-endpoint.json")
            val worker = IpcFragmentWorker()
            val options = ServiceWorkerOptions(
                appId = "com.test.ipc.frag",
                appName = "UnixSocketIpcTest",
                hasWorker = true
            )
            val host = DesktopServiceWorkerHost(
                options = options,
                worker = worker,
                endpointFilePath = endpointFile.absolutePath,
                mainBinaryBaseName = "app-main",
                swingMainClassFqn = "",
                stopEventLoop = {},
                lockRelease = {}
            )
            assertTrue(host.start())
            assertEquals(DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET, host.boundTransport)
            assertTrue(host.boundAddress.isNotBlank())
            val socketAddress = host.boundAddress
            assertTrue(File(socketAddress).exists())
            worker.attachAndStart(host, options)
            try {
                val descriptor = DesktopEndpointDescriptor.parse(endpointFile.readText(Charsets.UTF_8))!!
                assertEquals(DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET, descriptor.transport)
                assertEquals(socketAddress, descriptor.address)
                val runtime = IpcClientBackedRuntime(descriptor, worker)
                ServiceWorkerExtension.install(options)
                ServiceWorkerExtension.installRuntimeOverrideForTests(runtime)
                val channel = ServiceWorkerExtension.channel

                assertTrue(channel.connect())
                assertTrue(channel.connect(), "Repeated connect() on live AF_UNIX client must be idempotent.")

                val fragment = SettingsIpcFragment()
                val receivedCmds = mutableListOf<IpcSettingsCommand>()
                val binding = ServiceWorkerFragmentBinding(
                    fragment = fragment,
                    channel = channel
                ).apply {
                    onMessage<IpcSettingsCommand> { cmd ->
                        receivedCmds.add(cmd)
                    }
                    onRequest<IpcUiSnapshotReq, IpcUiSnapshotResp> { req ->
                        IpcUiSnapshotResp(
                            theme = if (req.includeTheme) "midnight" else "omitted",
                            section = "proxy"
                        )
                    }
                    start()
                }

                // Simulate receiving startFragment over AF_UNIX and awaiting mount handle from worker
                val handleDeferred = async {
                    worker.startFragmentAndAwait<SettingsIpcFragment>(
                        args = FragmentBundle.of("entry" to "unix"),
                        timeoutMs = 5_000L
                    )
                }
                val handle = withTimeout(5_000L) { handleDeferred.await() }
                assertEquals("com.test.ipc.SettingsFragment", handle.fragmentId)
                assertTrue(worker.isFragmentActive<SettingsIpcFragment>())
                assertEquals(1, worker.snapshotConnected().size)
                waitUntil { ServiceWorkerEnvelope.CONTROL_LAUNCH_FRAGMENT in runtime.snapshotControlTypes() }

                // Worker -> Fragment message & request over live AF_UNIX
                worker.sendToFragment<SettingsIpcFragment>(IpcSettingsCommand("highlight", "socks5"))
                waitUntil { receivedCmds.size == 1 }
                assertEquals(IpcSettingsCommand("highlight", "socks5"), receivedCmds.single())

                val snapshot = worker.requestFromFragment<SettingsIpcFragment, IpcUiSnapshotResp>(
                    IpcUiSnapshotReq(includeTheme = true),
                    timeoutMs = 5_000L
                )
                assertEquals(IpcUiSnapshotResp("midnight", "proxy"), snapshot)

                // Fragment -> Worker message & request over live AF_UNIX
                binding.send(IpcFragMetric(9))
                waitUntil { worker.snapshotMetrics().size == 1 }
                assertEquals("com.test.ipc.SettingsFragment" to IpcFragMetric(9), worker.snapshotMetrics().single())

                val lookup = binding.request<IpcWorkerLookupResp>(
                    IpcWorkerLookupReq("api.endpoint"),
                    timeoutMs = 5_000L
                )
                assertEquals("com.test.ipc.SettingsFragment:api.endpoint", lookup.resolved)

                // Close control frame travels over AF_UNIX cleanly
                handle.close()
                waitUntil { ServiceWorkerEnvelope.CONTROL_CLOSE_FRAGMENT in runtime.snapshotControlTypes() }

                // Disconnect AF_UNIX client cleans up fragment handles & notifies onFragmentDisconnected
                channel.disconnect()
                waitUntil {
                    !worker.isFragmentActive<SettingsIpcFragment>() &&
                        worker.snapshotDisconnected().size == 1
                }
                assertFalse(worker.isFragmentActive<SettingsIpcFragment>())
                binding.dispose()
            } finally {
                worker.performStop()
                host.shutdown()
            }
            assertFalse(File(socketAddress).exists(), "Unix domain socket file must be removed on host shutdown")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
