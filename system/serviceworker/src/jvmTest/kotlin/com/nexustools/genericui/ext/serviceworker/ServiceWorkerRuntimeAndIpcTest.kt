package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.resources.ResourceID
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
    fun loopbackTcpHostAndAuthenticatedClientSupportTypedMessagesRequestsAndBroadcasts() = runBlocking {
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
            assertTrue(host.start(), "DesktopServiceWorkerHost should bind 127.0.0.1:0")
            worker.attachAndStart(host, options)
            try {
                withTimeout(5_000) { worker.started.await() }
                assertTrue(host.boundPort > 0)
                assertTrue(endpointFile.isFile)
                val parsedEndpoint = DesktopEndpointDescriptor.parse(endpointFile.readText(Charsets.UTF_8))
                assertNotNull(parsedEndpoint)
                assertEquals(host.boundPort, parsedEndpoint.port)
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

                val validTcpClient = DesktopServiceWorkerChannelClient()
                val channelRuntime = object : ServiceWorkerPlatformRuntime {
                    override val isSupported: Boolean = true
                    override val isAutostartSupported: Boolean = true
                    override fun configure(options: ServiceWorkerOptions) = Unit
                    override fun isClientConnected(): Boolean = validTcpClient.isConnected
                    override suspend fun connectClient(
                        options: ServiceWorkerOptions,
                        onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
                        onConnectionChanged: (Boolean) -> Unit
                    ): Boolean = validTcpClient.connectToEndpoint(
                        descriptor = parsedEndpoint,
                        onIncomingEnvelope = onIncomingEnvelope,
                        onConnectionStateChanged = onConnectionChanged
                    )

                    override suspend fun disconnectClient() = validTcpClient.disconnect()
                    override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean =
                        validTcpClient.sendEnvelope(envelope)

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
            assertFalse(endpointFile.exists(), "Endpoint descriptor file must be removed on host shutdown")
        } finally {
            tempDir.deleteRecursively()
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
                onClick: (() -> Unit)?
            ) = Unit

            override fun openMainWindow(route: String?) = Unit
            override fun closeMainWindow() = Unit
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
}
