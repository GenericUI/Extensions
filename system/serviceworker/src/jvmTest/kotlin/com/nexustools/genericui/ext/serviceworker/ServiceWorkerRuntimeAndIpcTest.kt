package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.threading.GenericUiThreading
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceWorkerRuntimeAndIpcTest {

    data class JobMsg(val name: String)
    data class ComputeReq(val x: Int, val y: Int)
    data class ComputeResp(val result: Int)
    data class StatusPush(val status: String)

    private class SampleWorker : ServiceWorker() {
        val started = CompletableDeferred<Unit>()
        val connectedClient = CompletableDeferred<String>()
        val disconnectedClient = CompletableDeferred<String>()
        val receivedJobs = mutableListOf<JobMsg>()
        val jobArrived = CompletableDeferred<JobMsg>()

        override fun onStart() {
            onMessage<JobMsg> { _, msg ->
                receivedJobs.add(msg)
                if (!jobArrived.isCompleted) {
                    jobArrived.complete(msg)
                }
            }
            onRequest<ComputeReq> { _, req, reply ->
                reply.send(ComputeResp(req.x * req.y))
            }
            if (!started.isCompleted) {
                started.complete(Unit)
            }
        }

        override fun onClientConnected(client: ServiceWorkerClient) {
            if (!connectedClient.isCompleted) {
                connectedClient.complete(client.clientId)
            }
        }

        override fun onClientDisconnected(client: ServiceWorkerClient) {
            if (!disconnectedClient.isCompleted) {
                disconnectedClient.complete(client.clientId)
            }
        }
    }

    private fun registerTestCodecs() {
        ServiceWorkerCodecRegistry.register(
            object : ServiceWorkerMessageCodec<JobMsg> {
                override val typeId: String = "jvmtest.JobMsg"
                override val messageClass = JobMsg::class
                override fun encode(value: JobMsg): PreferenceElement =
                    PreferenceElement.ofObject(mapOf("name" to PreferenceElement.ofString(value.name)))

                override fun decode(element: PreferenceElement): JobMsg =
                    JobMsg(element.mapEntries["name"]?.asString().orEmpty())
            }
        )
        ServiceWorkerCodecRegistry.register(
            object : ServiceWorkerMessageCodec<ComputeReq> {
                override val typeId: String = "jvmtest.ComputeReq"
                override val messageClass = ComputeReq::class
                override fun encode(value: ComputeReq): PreferenceElement =
                    PreferenceElement.ofObject(
                        mapOf(
                            "x" to PreferenceElement.ofInt(value.x),
                            "y" to PreferenceElement.ofInt(value.y)
                        )
                    )

                override fun decode(element: PreferenceElement): ComputeReq =
                    ComputeReq(
                        element.mapEntries["x"]?.asInt() ?: 0,
                        element.mapEntries["y"]?.asInt() ?: 0
                    )
            }
        )
        ServiceWorkerCodecRegistry.register(
            object : ServiceWorkerMessageCodec<ComputeResp> {
                override val typeId: String = "jvmtest.ComputeResp"
                override val messageClass = ComputeResp::class
                override fun encode(value: ComputeResp): PreferenceElement =
                    PreferenceElement.ofObject(mapOf("result" to PreferenceElement.ofInt(value.result)))

                override fun decode(element: PreferenceElement): ComputeResp =
                    ComputeResp(element.mapEntries["result"]?.asInt() ?: 0)
            }
        )
        ServiceWorkerCodecRegistry.register(
            object : ServiceWorkerMessageCodec<StatusPush> {
                override val typeId: String = "jvmtest.StatusPush"
                override val messageClass = StatusPush::class
                override fun encode(value: StatusPush): PreferenceElement =
                    PreferenceElement.ofObject(mapOf("status" to PreferenceElement.ofString(value.status)))

                override fun decode(element: PreferenceElement): StatusPush =
                    StatusPush(element.mapEntries["status"]?.asString().orEmpty())
            }
        )
    }

    @BeforeTest
    fun setUpCodecs() {
        registerTestCodecs()
    }

    @AfterTest
    fun cleanUpCodecs() {
        ServiceWorkerCodecRegistry.unregister("jvmtest.JobMsg")
        ServiceWorkerCodecRegistry.unregister("jvmtest.ComputeReq")
        ServiceWorkerCodecRegistry.unregister("jvmtest.ComputeResp")
        ServiceWorkerCodecRegistry.unregister("jvmtest.StatusPush")
        DesktopOsHelpers.releaseSingleInstanceLock()
    }

    @Test
    fun unixSocketHostAndAuthenticatedClientSupportTypedMessagesRequestsAndBroadcasts() = runBlocking {
        val tempDir = Files.createTempDirectory("genericui-sw-ipc").toFile()
        try {
            val endpointFile = File(tempDir, "serviceworker-endpoint.json")
            val worker = SampleWorker()
            val options = ServiceWorkerOptions(
                appId = "com.nexustools.test.sw",
                appName = "TestWorkerApp",
                hasWorker = true
            )
            var loopStopped = false
            val host = DesktopServiceWorkerHost(
                options = options,
                worker = worker,
                endpointFilePath = endpointFile.absolutePath,
                mainBinaryBaseName = "app",
                swingMainClassFqn = "",
                stopEventLoop = { loopStopped = true },
                lockRelease = {}
            )
            assertTrue(host.start(), "DesktopServiceWorkerHost should bind AF_UNIX socket")
            assertEquals(DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET, host.boundTransport)
            assertTrue(host.boundAddress.isNotBlank())
            val socketAddress = host.boundAddress
            assertTrue(File(socketAddress).exists(), "Bound Unix domain socket file must exist while running")
            worker.attachAndStart(host, options)
            try {
                withTimeout(5_000) { worker.started.await() }
                assertTrue(endpointFile.isFile)
                val parsedEndpoint = DesktopEndpointDescriptor.parse(endpointFile.readText(Charsets.UTF_8))
                assertNotNull(parsedEndpoint)
                assertEquals(host.boundTransport, parsedEndpoint.transport)
                assertEquals(host.boundAddress, parsedEndpoint.address)
                assertEquals(host.token, parsedEndpoint.token)

                val badClient = DesktopServiceWorkerChannelClient()
                assertFalse(
                    badClient.connectToEndpoint(
                        descriptor = parsedEndpoint.copy(token = "0000badtoken"),
                        onIncomingEnvelope = {},
                        onConnectionStateChanged = {}
                    ),
                    "Invalid authentication token must be rejected"
                )
                badClient.disconnect()

                val validIpcClient = DesktopServiceWorkerChannelClient()
                val channelRuntime = object : ServiceWorkerPlatformRuntime {
                    override val isSupported: Boolean = true
                    override val supportsMultipleWindows: Boolean = true
                    override val supportsFragmentLaunch: Boolean = true
                    override val supportsDialogFragments: Boolean = true
                    override val supportsSystemTray: Boolean = true
                    override val supportsOngoingNotification: Boolean = false
                    override val supportsAutostart: Boolean = true
                    override fun configure(options: ServiceWorkerOptions) = Unit
                    override fun isClientConnected(): Boolean = validIpcClient.isConnected
                    override suspend fun connectClient(
                        options: ServiceWorkerOptions,
                        onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
                        onConnectionChanged: (Boolean) -> Unit
                    ): Boolean = validIpcClient.connectToEndpoint(
                        descriptor = parsedEndpoint,
                        onIncomingEnvelope = onIncomingEnvelope,
                        onConnectionStateChanged = onConnectionChanged
                    )

                    override suspend fun disconnectClient() = validIpcClient.disconnect()
                    override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean =
                        validIpcClient.sendEnvelope(envelope)

                    override fun prepareDesktopMainLaunch(
                        args: Array<String>,
                        options: ServiceWorkerOptions,
                        workerBinaryBaseName: String
                    ): Boolean = true

                    override fun runDesktopWorkerMain(
                        args: Array<String>,
                        options: ServiceWorkerOptions,
                        workerFactory: () -> ServiceWorker,
                        backendInit: () -> Unit,
                        eventLoopRunner: () -> Unit,
                        eventLoopStopper: () -> Unit,
                        mainBinaryBaseName: String
                    ): Int = 0

                    override fun ensureBackgroundStarted(
                        options: ServiceWorkerOptions,
                        workerFactory: (() -> ServiceWorker)?
                    ): Boolean = true

                    override fun activeWorker(): ServiceWorker? = worker
                    override fun currentBackendTarget(): BackendTarget = BackendTarget.Swing
                    override fun isAutostartEnabled(options: ServiceWorkerOptions): Boolean = false
                    override fun setAutostartEnabled(options: ServiceWorkerOptions, enabled: Boolean): Boolean = false
                    override fun openMainWindowFromHost(options: ServiceWorkerOptions, route: String?) = Unit
                    override fun closeMainWindowFromHost(options: ServiceWorkerOptions) = Unit
                }

                val channel = ServiceWorkerChannel(
                    runtimeResolver = { channelRuntime },
                    optionsResolver = { options }
                )
                val pushedDeferred = CompletableDeferred<StatusPush>()
                channel.onMessage<StatusPush> { push ->
                    if (!pushedDeferred.isCompleted) {
                        pushedDeferred.complete(push)
                    }
                }

                assertTrue(channel.connect())
                val connectedCid = withTimeout(5_000) { worker.connectedClient.await() }
                assertTrue(connectedCid.isNotBlank())

                channel.send(JobMsg("encode-video"))
                val receivedJob = withTimeout(5_000) { worker.jobArrived.await() }
                assertEquals("encode-video", receivedJob.name)

                val resp = channel.request<ComputeResp>(ComputeReq(6, 7), timeoutMs = 5_000)
                assertEquals(42, resp.result)

                val deliveredCount = worker.broadcast(StatusPush("ready"))
                assertEquals(1, deliveredCount)
                val push = withTimeout(5_000) { pushedDeferred.await() }
                assertEquals("ready", push.status)

                channel.disconnect()
                withTimeout(5_000) { worker.disconnectedClient.await() }
                assertFalse(loopStopped)
            } finally {
                worker.performStop()
                host.shutdown()
            }
            assertFalse(File(socketAddress).exists(), "Unix domain socket file must be removed on host shutdown")
            assertFalse(endpointFile.exists(), "Endpoint descriptor file must be removed on host shutdown")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun longRuntimeDirectoryShortensUnixSocketPathBelowLimitAndConnectsEndToEnd() = runBlocking {
        val baseTempDir = Files.createTempDirectory("genericui-sw-long").toFile()
        try {
            val longRuntimeDir = File(
                baseTempDir,
                "segment-alpha-0123456789/segment-beta-0123456789/segment-gamma-0123456789/segment-delta-0123456789/runtime"
            )
            assertTrue(longRuntimeDir.mkdirs())
            assertTrue(
                longRuntimeDir.absolutePath.toByteArray(Charsets.UTF_8).size > 120,
                "Test runtimeDir must exceed 120 UTF-8 bytes"
            )
            val appId = "com.nexustools.test.longpath.${System.nanoTime()}"
            val resolvedSocketPath = JvmUnixDomainSocketBridge.resolveSocketPath(appId, longRuntimeDir.absolutePath)
            assertTrue(
                resolvedSocketPath.toString().toByteArray(Charsets.UTF_8).size <= 96,
                "Shortened AF_UNIX socket path must fit within 96 UTF-8 bytes"
            )
            assertFalse(resolvedSocketPath.toString().startsWith(longRuntimeDir.absolutePath))

            val endpointFile = File(longRuntimeDir, "serviceworker-endpoint.json")
            val worker = SampleWorker()
            val options = ServiceWorkerOptions(
                appId = appId,
                appName = "LongPathWorkerApp",
                hasWorker = true
            )
            val host = DesktopServiceWorkerHost(
                options = options,
                worker = worker,
                endpointFilePath = endpointFile.absolutePath,
                mainBinaryBaseName = "app",
                swingMainClassFqn = "",
                stopEventLoop = {},
                lockRelease = {}
            )
            assertTrue(host.start(), "Host must start using shortened AF_UNIX socket path")
            assertEquals(DesktopEndpointDescriptor.TRANSPORT_UNIX_SOCKET, host.boundTransport)
            val socketAddress = host.boundAddress
            assertTrue(socketAddress.toByteArray(Charsets.UTF_8).size <= 96)
            assertTrue(File(socketAddress).exists())
            worker.attachAndStart(host, options)
            try {
                withTimeout(5_000) { worker.started.await() }
                val parsedEndpoint = DesktopEndpointDescriptor.parse(endpointFile.readText(Charsets.UTF_8))
                assertNotNull(parsedEndpoint)
                assertEquals(socketAddress, parsedEndpoint.address)

                val ipcClient = DesktopServiceWorkerChannelClient()
                val channelRuntime = object : ServiceWorkerPlatformRuntime {
                    override val isSupported: Boolean = true
                    override val supportsMultipleWindows: Boolean = true
                    override val supportsFragmentLaunch: Boolean = true
                    override val supportsDialogFragments: Boolean = true
                    override val supportsSystemTray: Boolean = true
                    override val supportsOngoingNotification: Boolean = false
                    override val supportsAutostart: Boolean = true
                    override fun configure(options: ServiceWorkerOptions) = Unit
                    override fun isClientConnected(): Boolean = ipcClient.isConnected
                    override suspend fun connectClient(
                        options: ServiceWorkerOptions,
                        onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
                        onConnectionChanged: (Boolean) -> Unit
                    ): Boolean = ipcClient.connectToEndpoint(parsedEndpoint, onIncomingEnvelope, onConnectionChanged)

                    override suspend fun disconnectClient() = ipcClient.disconnect()
                    override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean =
                        ipcClient.sendEnvelope(envelope)
                }
                val channel = ServiceWorkerChannel(
                    runtimeResolver = { channelRuntime },
                    optionsResolver = { options }
                )
                assertTrue(channel.connect())
                withTimeout(5_000) { worker.connectedClient.await() }

                val resp = channel.request<ComputeResp>(ComputeReq(8, 9), timeoutMs = 5_000)
                assertEquals(72, resp.result)

                channel.disconnect()
                withTimeout(5_000) { worker.disconnectedClient.await() }
            } finally {
                worker.performStop()
                host.shutdown()
            }
            assertFalse(File(socketAddress).exists(), "Shortened AF_UNIX socket file must be removed on host shutdown")
            assertFalse(endpointFile.exists(), "Endpoint descriptor file must be removed on host shutdown")
        } finally {
            baseTempDir.deleteRecursively()
        }
    }

    @Test
    fun singleInstanceLockRejectsSecondAcquisitionUntilReleased() {
        val tempDir = Files.createTempDirectory("genericui-sw-lock").toFile()
        try {
            val lockFile = File(tempDir, "serviceworker.lock").absolutePath
            val key = "GenericUI-Lock-Test-${System.nanoTime()}"
            assertTrue(DesktopOsHelpers.acquireSingleInstanceLock(key, lockFile))
            assertFalse(DesktopOsHelpers.acquireSingleInstanceLock(key, lockFile))
            DesktopOsHelpers.releaseSingleInstanceLock()
            assertTrue(DesktopOsHelpers.acquireSingleInstanceLock(key, lockFile))
            DesktopOsHelpers.releaseSingleInstanceLock()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun windowsShortcutWriterProducesValidMsShllinkHeaderAndAtomicFilesRoundTrip() {
        val bytes = WindowsShortcutWriter.buildLnkBytes(
            targetPath = "C:\\Program Files\\GenericUI\\serviceworker.exe",
            arguments = "--autostart",
            workingDirectory = "C:\\Program Files\\GenericUI",
            description = "GenericUI Background Worker"
        )
        assertTrue(bytes.size > 76)
        assertEquals(0x4C.toByte(), bytes[0])
        assertEquals(0x00.toByte(), bytes[1])
        assertEquals(0x00.toByte(), bytes[2])
        assertEquals(0x00.toByte(), bytes[3])
        assertEquals(0x01.toByte(), bytes[4])
        assertEquals(0x14.toByte(), bytes[5])
        assertEquals(0x02.toByte(), bytes[6])
        assertEquals(0x46.toByte(), bytes[19])

        val tempDir = Files.createTempDirectory("genericui-sw-atomic").toFile()
        try {
            val file = File(tempDir, "nested/atomic.json").absolutePath
            assertTrue(DesktopOsHelpers.atomicWriteTextFile(file, "{\"ok\":true}", ownerOnlyPermissions = true))
            assertEquals("{\"ok\":true}", DesktopOsHelpers.readTextFileOrNull(file))
            assertTrue(DesktopOsHelpers.deleteFileIfExists(file))
            assertNull(DesktopOsHelpers.readTextFileOrNull(file))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun notificationActionsExtendPreservesStopAndReplaceOverridesActions() {
        val recordedActions = mutableListOf<List<ServiceWorkerNotificationAction>>()
        val host = object : ServiceWorkerRuntimeHost {
            override val backendName: String = "Android"
            override val supportsMultipleWindows: Boolean = false
            override val supportsFragmentLaunch: Boolean = true
            override val supportsDialogFragments: Boolean = true
            override val supportsSystemTray: Boolean = false
            override val supportsOngoingNotification: Boolean = true
            override val supportsAutostart: Boolean = true

            override fun updateNotification(
                title: String,
                text: String,
                subText: String?,
                icon: ResourceID?,
                actions: List<ServiceWorkerNotificationAction>
            ) {
                recordedActions.add(actions)
            }

            override fun updateTray(
                iconPathOrUrl: String?,
                iconResource: ResourceID?,
                tooltip: String?,
                badgeText: String?,
                menu: ContextMenu?,
                onClick: (() -> Unit)?,
                macosIconPathOrUrl: String?
            ) = Unit

            override fun openMainWindow(route: String?) = Unit
            override fun closeMainWindow() = Unit
            override fun requestLaunchFragment(request: ServiceWorkerFragmentLaunchRequest) = Unit
            override fun requestCloseFragment(fragmentId: String, instanceId: String?) = Unit
            override fun stopSelf() = Unit
            override fun exitApplication(exitCode: Int) = Unit
            override fun isAutostartEnabled(): Boolean = false
            override fun setAutostartEnabled(enabled: Boolean): Boolean = false
            override suspend fun sendToClient(clientId: String, envelope: ServiceWorkerEnvelope): Boolean = false
            override suspend fun broadcastEnvelope(envelope: ServiceWorkerEnvelope): Int = 0
            override fun disconnectClient(clientId: String) = Unit
        }

        val worker = SampleWorker()
        worker.attachAndStart(host, ServiceWorkerOptions())
        try {
            worker.extendNotificationActions(
                ServiceWorkerNotificationAction(id = "pause", label = "Pause")
            )
            val afterExtend = worker.getEffectiveNotificationActions()
            assertEquals(2, afterExtend.size)
            assertEquals(ServiceWorkerNotificationAction.DEFAULT_STOP_ACTION_ID, afterExtend[0].id)
            assertEquals("pause", afterExtend[1].id)

            worker.replaceNotificationActions(
                ServiceWorkerNotificationAction(id = "custom_only", label = "Custom")
            )
            val afterReplace = worker.getEffectiveNotificationActions()
            assertEquals(1, afterReplace.size)
            assertEquals("custom_only", afterReplace[0].id)
            assertTrue(recordedActions.size >= 2)
        } finally {
            worker.performStop()
        }
    }

    @Test
    fun multiKeySingleInstanceLockTracksIndependentKeysWithoutClobbering() {
        val tempDir = Files.createTempDirectory("genericui-sw-multilock").toFile()
        try {
            val workerLockFile = File(tempDir, "serviceworker.lock").absolutePath
            val mainAppLockFile = File(tempDir, "mainapp.lock").absolutePath
            val workerKey = "GenericUI-Worker-Test-${System.nanoTime()}"
            val mainKey = "GenericUI-Main-Test-${System.nanoTime()}"

            assertTrue(DesktopOsHelpers.acquireSingleInstanceLock(workerKey, workerLockFile))
            assertTrue(DesktopOsHelpers.acquireSingleInstanceLock(mainKey, mainAppLockFile))
            assertFalse(DesktopOsHelpers.acquireSingleInstanceLock(workerKey, workerLockFile))
            assertFalse(DesktopOsHelpers.acquireSingleInstanceLock(mainKey, mainAppLockFile))

            DesktopOsHelpers.releaseSingleInstanceLock(mainKey)
            assertFalse(
                DesktopOsHelpers.acquireSingleInstanceLock(workerKey, workerLockFile),
                "Releasing mainapp.lock key must not release held serviceworker.lock key"
            )
            assertTrue(DesktopOsHelpers.acquireSingleInstanceLock(mainKey, mainAppLockFile))

            DesktopOsHelpers.releaseSingleInstanceLock(workerKey)
            DesktopOsHelpers.releaseSingleInstanceLock(mainKey)
            assertTrue(DesktopOsHelpers.acquireSingleInstanceLock(workerKey, workerLockFile))
            DesktopOsHelpers.releaseSingleInstanceLock()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun prepareDesktopMainLaunchDeduplicatesDuplicateMainWindowAndForwardsActivateRequest() = runBlocking {
        val testAppId = "com.nexustools.test.dedup.${System.nanoTime()}"
        val runtimeDataDir = File(DesktopOsHelpers.runtimeDataDirectory(testAppId))
        val endpointFile = File(runtimeDataDir, "serviceworker-endpoint.json")
        val worker = SampleWorker()
        val options = ServiceWorkerOptions(
            appId = testAppId,
            appName = "DedupTestApp",
            openWindowOnLaunch = false,
            hasWorker = false
        )
        val host = DesktopServiceWorkerHost(
            options = options,
            worker = worker,
            endpointFilePath = endpointFile.absolutePath,
            mainBinaryBaseName = "app",
            swingMainClassFqn = "",
            stopEventLoop = {},
            lockRelease = {}
        )
        val runtime = DesktopServiceWorkerRuntime(BackendTarget.Swing)
        val activeUiClient = DesktopServiceWorkerChannelClient()
        try {
            assertTrue(host.start())
            worker.attachAndStart(host, options)
            withTimeout(5_000) { worker.started.await() }

            val descriptor = DesktopEndpointDescriptor.parse(endpointFile.readText(Charsets.UTF_8))
            assertNotNull(descriptor)

            val receivedOpenRoute = CompletableDeferred<String>()
            assertTrue(
                activeUiClient.connectToEndpoint(
                    descriptor = descriptor,
                    onIncomingEnvelope = { env ->
                        if (env.kind == ServiceWorkerEnvelope.KIND_MESSAGE &&
                            env.typeId == "__genericui.sw.control.openMainWindow" &&
                            !receivedOpenRoute.isCompleted
                        ) {
                            receivedOpenRoute.complete(env.payloadJson)
                        }
                    },
                    onConnectionStateChanged = {}
                )
            )
            withTimeout(5_000) { worker.connectedClient.await() }

            assertFalse(
                runtime.prepareDesktopMainLaunch(emptyArray(), options, "serviceworker"),
                "openWindowOnLaunch=false without --open-window should exit before acquiring mainapp.lock"
            )
            assertTrue(
                runtime.prepareDesktopMainLaunch(arrayOf("--open-window"), options, "serviceworker"),
                "First main window launch should acquire mainapp.lock and proceed"
            )
            assertFalse(
                runtime.prepareDesktopMainLaunch(
                    arrayOf("--open-window", "--route=settings"),
                    options,
                    "serviceworker"
                ),
                "Duplicate main window launch while mainapp.lock is held must return false"
            )

            assertEquals("settings", withTimeout(5_000) { receivedOpenRoute.await() })
        } finally {
            activeUiClient.disconnect()
            runtime.resetForTests()
            worker.performStop()
            host.shutdown()
            runtimeDataDir.deleteRecursively()
        }
    }

    @Test
    fun openMainWindowDeduplicatesPendingSpawnsAndDeliversQueuedRouteOnConnect() = runBlocking {
        val tempDir = Files.createTempDirectory("genericui-sw-spawn-dedup").toFile()
        try {
            val endpointFile = File(tempDir, "serviceworker-endpoint.json")
            val worker = SampleWorker()
            val options = ServiceWorkerOptions(
                appId = "com.nexustools.test.spawndedup",
                appName = "SpawnDedupApp",
                hasWorker = true
            )
            val spawnedCalls = mutableListOf<Pair<String, List<String>>>()
            val host = DesktopServiceWorkerHost(
                options = options,
                worker = worker,
                endpointFilePath = endpointFile.absolutePath,
                mainBinaryBaseName = "app",
                swingMainClassFqn = "com.example.MainKt",
                stopEventLoop = {},
                lockRelease = {},
                spawnLauncher = { exe, args, _ ->
                    spawnedCalls.add(exe to args)
                    true
                },
                siblingCommandResolver = { base, _ -> "/fake/bin/$base" to emptyList() }
            )
            assertTrue(host.start())
            worker.attachAndStart(host, options)
            val client = DesktopServiceWorkerChannelClient()
            try {
                withTimeout(5_000) { worker.started.await() }

                host.openMainWindow(null)
                assertEquals(1, spawnedCalls.size)
                assertEquals(listOf("--open-window"), spawnedCalls[0].second)

                host.openMainWindow("settings")
                assertEquals(
                    1,
                    spawnedCalls.size,
                    "In-flight pending spawn must deduplicate subsequent openMainWindow requests"
                )

                val descriptor = DesktopEndpointDescriptor.parse(endpointFile.readText(Charsets.UTF_8))
                assertNotNull(descriptor)
                val queuedOpenRoute = CompletableDeferred<String>()
                val liveOpenRoute = CompletableDeferred<String>()
                assertTrue(
                    client.connectToEndpoint(
                        descriptor = descriptor,
                        onIncomingEnvelope = { env ->
                            if (env.kind == ServiceWorkerEnvelope.KIND_MESSAGE &&
                                env.typeId == "__genericui.sw.control.openMainWindow"
                            ) {
                                if (!queuedOpenRoute.isCompleted) {
                                    queuedOpenRoute.complete(env.payloadJson)
                                } else if (!liveOpenRoute.isCompleted) {
                                    liveOpenRoute.complete(env.payloadJson)
                                }
                            }
                        },
                        onConnectionStateChanged = {}
                    )
                )

                assertEquals("settings", withTimeout(5_000) { queuedOpenRoute.await() })

                host.openMainWindow("about")
                assertEquals(
                    1,
                    spawnedCalls.size,
                    "Connected main app must receive openMainWindow over IPC without spawning a second OS process"
                )
                assertEquals("about", withTimeout(5_000) { liveOpenRoute.await() })
            } finally {
                client.disconnect()
                worker.performStop()
                host.shutdown()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun trayMenuWithoutClickListenerKeepsOnClickNullAndWiresMacosTrayIcon() {
        val tempDir = Files.createTempDirectory("genericui-sw-tray").toFile()
        try {
            val endpointFile = File(tempDir, "serviceworker-endpoint.json")
            val recordedMacosIcons = mutableListOf<String?>()
            val spyHost = object : ServiceWorkerRuntimeHost {
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
                    icon: ResourceID?,
                    actions: List<ServiceWorkerNotificationAction>
                ) = Unit

                override fun updateTray(
                    iconPathOrUrl: String?,
                    iconResource: ResourceID?,
                    tooltip: String?,
                    badgeText: String?,
                    menu: ContextMenu?,
                    onClick: (() -> Unit)?,
                    macosIconPathOrUrl: String?
                ) {
                    recordedMacosIcons.add(macosIconPathOrUrl)
                }

                override fun openMainWindow(route: String?) = Unit
                override fun closeMainWindow() = Unit
                override fun requestLaunchFragment(request: ServiceWorkerFragmentLaunchRequest) = Unit
                override fun requestCloseFragment(fragmentId: String, instanceId: String?) = Unit
                override fun stopSelf() = Unit
                override fun exitApplication(exitCode: Int) = Unit
                override fun isAutostartEnabled(): Boolean = false
                override fun setAutostartEnabled(enabled: Boolean): Boolean = false
                override suspend fun sendToClient(clientId: String, envelope: ServiceWorkerEnvelope): Boolean = false
                override suspend fun broadcastEnvelope(envelope: ServiceWorkerEnvelope): Int = 0
                override fun disconnectClient(clientId: String) = Unit
            }

            val optionsWithMacIcon = ServiceWorkerOptions(
                appId = "com.nexustools.test.tray",
                appName = "TrayApp",
                trayIcon = "icons/tray.svg",
                macosTrayIcon = "icons/macos-tray.svg"
            )
            val spyWorker = SampleWorker()
            spyWorker.attachAndStart(spyHost, optionsWithMacIcon)
            assertEquals("icons/macos-tray.svg", recordedMacosIcons.last())
            spyWorker.setMacosTrayIcon("icons/override-macos.png")
            assertEquals("icons/override-macos.png", recordedMacosIcons.last())
            spyWorker.performStop()

            val menuWorker = object : ServiceWorker() {
                override fun onStart() {
                    setTrayMenu(ContextMenu())
                }
            }
            val host = DesktopServiceWorkerHost(
                options = optionsWithMacIcon,
                worker = menuWorker,
                endpointFilePath = endpointFile.absolutePath,
                mainBinaryBaseName = "app",
                swingMainClassFqn = "",
                stopEventLoop = {},
                lockRelease = {}
            ).apply {
                traySupportedForTests = true
            }
            host.initializeDefaultTrayIfSupported()
            menuWorker.attachAndStart(host, optionsWithMacIcon)
            try {
                val tray = host.currentTrayForTests
                assertNotNull(tray)
                assertEquals("icons/macos-tray.svg", tray.macosIconPathOrUrl)
                assertEquals("icons/tray.svg", tray.iconPathOrUrl)
                assertNotNull(tray.contextMenu)
                assertNull(
                    tray.onClick,
                    "When a ContextMenu is configured without an explicit click listener, tray.onClick must stay null so native menu opens"
                )
                menuWorker.setTrayClickListener {}
                assertNotNull(tray.onClick, "Explicit click listener should still attach when requested")
            } finally {
                menuWorker.performStop()
                host.shutdown()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun serviceWorkerChannelDispatchesOpenMainWindowControlToExtensionListener() = runBlocking {
        val tempDir = Files.createTempDirectory("genericui-sw-open-listener").toFile()
        val openedRoutes = mutableListOf<String?>()
        val handle = ServiceWorkerExtension.onOpenMainWindowRequested { route ->
            openedRoutes.add(route)
        }
        try {
            val endpointFile = File(tempDir, "serviceworker-endpoint.json")
            val worker = SampleWorker()
            val options = ServiceWorkerOptions(appId = "com.nexustools.test.openlistener", appName = "OpenListenerApp")
            val host = DesktopServiceWorkerHost(
                options = options,
                worker = worker,
                endpointFilePath = endpointFile.absolutePath,
                mainBinaryBaseName = "app",
                swingMainClassFqn = "",
                stopEventLoop = {},
                lockRelease = {}
            )
            assertTrue(host.start())
            worker.attachAndStart(host, options)
            val tcpClient = DesktopServiceWorkerChannelClient()
            try {
                val descriptor = DesktopEndpointDescriptor.parse(endpointFile.readText(Charsets.UTF_8))
                assertNotNull(descriptor)
                val channelRuntime = object : ServiceWorkerPlatformRuntime {
                    override val isSupported: Boolean = true
                    override val supportsMultipleWindows: Boolean = true
                    override val supportsFragmentLaunch: Boolean = true
                    override val supportsDialogFragments: Boolean = true
                    override val supportsSystemTray: Boolean = true
                    override val supportsOngoingNotification: Boolean = false
                    override val supportsAutostart: Boolean = true
                    override fun configure(options: ServiceWorkerOptions) = Unit
                    override fun isClientConnected(): Boolean = tcpClient.isConnected
                    override suspend fun connectClient(
                        options: ServiceWorkerOptions,
                        onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
                        onConnectionChanged: (Boolean) -> Unit
                    ): Boolean = tcpClient.connectToEndpoint(descriptor, onIncomingEnvelope, onConnectionChanged)

                    override suspend fun disconnectClient() = tcpClient.disconnect()
                    override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean =
                        tcpClient.sendEnvelope(envelope)
                }
                val channel = ServiceWorkerChannel(
                    runtimeResolver = { channelRuntime },
                    optionsResolver = { options }
                )
                assertTrue(channel.connect())
                withTimeout(5_000) { worker.connectedClient.await() }

                host.openMainWindow("settings")
                val deadline = System.currentTimeMillis() + 5_000L
                while (openedRoutes.isEmpty() && System.currentTimeMillis() < deadline) {
                    GenericUiThreading.flushAllQueues()
                    Thread.sleep(20)
                }
                assertEquals(listOf<String?>("settings"), openedRoutes)
                channel.disconnect()
            } finally {
                worker.performStop()
                host.shutdown()
            }
        } finally {
            handle.dispose()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun prepareDesktopMainLaunchQueriesRunningWorkerAndRespectsShouldOpenMainWindow() = runBlocking {
        val testAppId = "com.nexustools.test.launchquery.${System.nanoTime()}"
        val runtimeDataDir = File(DesktopOsHelpers.runtimeDataDirectory(testAppId))
        val endpointFile = File(runtimeDataDir, "serviceworker-endpoint.json")
        var allowOpen = false
        val worker = object : ServiceWorker() {
            val started = CompletableDeferred<Unit>()
            override fun onStart() {
                if (!started.isCompleted) {
                    started.complete(Unit)
                }
            }

            override fun onMainAppLaunchedWhileRunning(): Boolean = allowOpen
        }
        val options = ServiceWorkerOptions(
            appId = testAppId,
            appName = "LaunchQueryTestApp",
            openWindowOnLaunch = false,
            hasWorker = true
        )
        val host = DesktopServiceWorkerHost(
            options = options,
            worker = worker,
            endpointFilePath = endpointFile.absolutePath,
            mainBinaryBaseName = "app",
            swingMainClassFqn = "",
            stopEventLoop = {},
            lockRelease = {}
        )
        val runtime = DesktopServiceWorkerRuntime(BackendTarget.Swing)
        val activeUiClient = DesktopServiceWorkerChannelClient()
        try {
            assertTrue(host.start())
            worker.attachAndStart(host, options)
            withTimeout(5_000) { worker.started.await() }

            // 1. Running worker returns false -> prepareDesktopMainLaunch returns false without acquiring mainapp.lock
            allowOpen = false
            assertFalse(
                runtime.prepareDesktopMainLaunch(emptyArray(), options, "serviceworker"),
                "When running worker returns onMainAppLaunchedWhileRunning() == false, prepareDesktopMainLaunch must return false"
            )

            // 2. Running worker returns true -> first launch acquires mainapp.lock and returns true
            allowOpen = true
            assertTrue(
                runtime.prepareDesktopMainLaunch(emptyArray(), options, "serviceworker"),
                "When running worker returns onMainAppLaunchedWhileRunning() == true, first launch must return true"
            )

            val descriptor = DesktopEndpointDescriptor.parse(endpointFile.readText(Charsets.UTF_8))
            assertNotNull(descriptor)
            val activated = CompletableDeferred<Unit>()
            assertTrue(
                activeUiClient.connectToEndpoint(
                    descriptor = descriptor,
                    onIncomingEnvelope = { env ->
                        if (env.kind == ServiceWorkerEnvelope.KIND_MESSAGE &&
                            env.typeId == ServiceWorkerEnvelope.CONTROL_OPEN_MAIN_WINDOW &&
                            !activated.isCompleted
                        ) {
                            activated.complete(Unit)
                        }
                    },
                    onConnectionStateChanged = {}
                )
            )

            // 3. Second launch while running worker returns true & mainapp.lock held activates existing window and returns false
            assertFalse(
                runtime.prepareDesktopMainLaunch(emptyArray(), options, "serviceworker"),
                "Second launch while mainapp.lock is held must return false after activating existing window"
            )
            withTimeout(5_000) { activated.await() }
        } finally {
            activeUiClient.disconnect()
            runtime.resetForTests()
            worker.performStop()
            host.shutdown()
            runtimeDataDir.deleteRecursively()
        }
    }
}
