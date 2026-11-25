@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.resources.ResourceID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServiceWorkerColdStartQueueAndIpcTest {

    private class QueueTargetFragment : Fragment()
    private class OtherFragment : Fragment()

    private data class QueuedCmd(val seq: Int, val name: String)
    private data class StatusUpdate(val label: String)
    private data class FragCalcReq(val a: Int, val b: Int)
    private data class FragCalcResp(val product: Int)

    private class QueueWorker : ServiceWorker() {
        private val lock = AtomicBoolean(false)
        val connectedHandles = mutableListOf<ServiceWorkerFragmentHandle>()
        val disconnectedHandles = mutableListOf<ServiceWorkerFragmentHandle>()
        val receivedFromTarget = mutableListOf<Pair<String, StatusUpdate>>()

        override fun onStart() {
            onFragmentMessage<QueueTargetFragment, StatusUpdate> { handle, msg ->
                withLock {
                    receivedFromTarget.add(handle.instanceId to msg)
                }
            }
            onFragmentRequest<QueueTargetFragment, FragCalcReq, FragCalcResp> { _, req ->
                FragCalcResp(req.a * req.b)
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
        fun snapshotMessages(): List<Pair<String, StatusUpdate>> = withLock { receivedFromTarget.toList() }

        private inline fun <T> withLock(block: () -> T): T {
            while (!lock.compareAndSet(expectedValue = false, newValue = true)) {
                // Spin briefly around in-memory test collections.
            }
            return try {
                block()
            } finally {
                lock.store(false)
            }
        }
    }

    private class RecordingRuntimeHost : ServiceWorkerRuntimeHost {
        private val lock = AtomicBoolean(false)
        val launchRequests = mutableListOf<ServiceWorkerFragmentLaunchRequest>()
        val closeRequests = mutableListOf<Pair<String, String?>>()
        val sentEnvelopes = mutableListOf<ServiceWorkerEnvelope>()

        override val backendName: String = "Swing"
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
        ) {}

        override fun updateTray(
            iconPathOrUrl: String?,
            iconResource: ResourceID?,
            tooltip: String?,
            badgeText: String?,
            menu: ContextMenu?,
            onClick: (() -> Unit)?,
            macosIconPathOrUrl: String?
        ) {}

        override fun openMainWindow(route: String?) {}
        override fun closeMainWindow() {}

        override fun requestLaunchFragment(request: ServiceWorkerFragmentLaunchRequest) {
            withLock { launchRequests.add(request) }
        }

        override fun requestCloseFragment(fragmentId: String, instanceId: String?) {
            withLock { closeRequests.add(fragmentId to instanceId) }
        }

        override fun stopSelf() {}
        override fun exitApplication(exitCode: Int) {}
        override fun isAutostartEnabled(): Boolean = false
        override fun setAutostartEnabled(enabled: Boolean): Boolean = false

        override suspend fun sendToClient(clientId: String, envelope: ServiceWorkerEnvelope): Boolean {
            withLock { sentEnvelopes.add(envelope) }
            return true
        }

        override suspend fun broadcastEnvelope(envelope: ServiceWorkerEnvelope): Int {
            withLock { sentEnvelopes.add(envelope) }
            return 1
        }

        override fun disconnectClient(clientId: String) {}

        fun snapshotLaunches(): List<ServiceWorkerFragmentLaunchRequest> = withLock { launchRequests.toList() }
        fun snapshotCloses(): List<Pair<String, String?>> = withLock { closeRequests.toList() }
        fun snapshotSent(): List<ServiceWorkerEnvelope> = withLock { sentEnvelopes.toList() }

        private inline fun <T> withLock(block: () -> T): T {
            while (!lock.compareAndSet(expectedValue = false, newValue = true)) {
                // Spin briefly around in-memory test collections.
            }
            return try {
                block()
            } finally {
                lock.store(false)
            }
        }
    }

    private val registeredTypeIds = listOf(
        "test.q.QueuedCmd",
        "test.q.StatusUpdate",
        "test.q.FragCalcReq",
        "test.q.FragCalcResp"
    )

    private fun registerCodecs() {
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<QueuedCmd> {
            override val typeId = "test.q.QueuedCmd"
            override val messageClass = QueuedCmd::class
            override fun encode(value: QueuedCmd) = PreferenceElement.ofObject(
                mapOf(
                    "seq" to PreferenceElement.ofInt(value.seq),
                    "name" to PreferenceElement.ofString(value.name)
                )
            )
            override fun decode(element: PreferenceElement) = QueuedCmd(
                seq = element.mapEntries["seq"]?.asInt() ?: 0,
                name = element.mapEntries["name"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<StatusUpdate> {
            override val typeId = "test.q.StatusUpdate"
            override val messageClass = StatusUpdate::class
            override fun encode(value: StatusUpdate) = PreferenceElement.ofObject(
                mapOf("label" to PreferenceElement.ofString(value.label))
            )
            override fun decode(element: PreferenceElement) = StatusUpdate(
                label = element.mapEntries["label"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<FragCalcReq> {
            override val typeId = "test.q.FragCalcReq"
            override val messageClass = FragCalcReq::class
            override fun encode(value: FragCalcReq) = PreferenceElement.ofObject(
                mapOf(
                    "a" to PreferenceElement.ofInt(value.a),
                    "b" to PreferenceElement.ofInt(value.b)
                )
            )
            override fun decode(element: PreferenceElement) = FragCalcReq(
                a = element.mapEntries["a"]?.asInt() ?: 0,
                b = element.mapEntries["b"]?.asInt() ?: 0
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<FragCalcResp> {
            override val typeId = "test.q.FragCalcResp"
            override val messageClass = FragCalcResp::class
            override fun encode(value: FragCalcResp) = PreferenceElement.ofObject(
                mapOf("product" to PreferenceElement.ofInt(value.product))
            )
            override fun decode(element: PreferenceElement) = FragCalcResp(
                product = element.mapEntries["product"]?.asInt() ?: 0
            )
        })
    }

    private fun registerFragments() {
        FragmentRegistry.register(QueueTargetFragment::class) { QueueTargetFragment() }
        FragmentRegistry.registerMetadata(
            fragmentClass = QueueTargetFragment::class,
            fragmentId = "com.test.QueueTarget",
            route = "/queue-target"
        )
        FragmentRegistry.register(OtherFragment::class) { OtherFragment() }
        FragmentRegistry.registerMetadata(
            fragmentClass = OtherFragment::class,
            fragmentId = "com.test.Other",
            route = "/other"
        )
    }

    private suspend fun waitUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!predicate()) {
                delay(10L)
            }
        }
    }

    @BeforeTest
    fun setUp() {
        FragmentRegistry.clear()
        registerFragments()
        registerCodecs()
    }

    @AfterTest
    fun tearDown() {
        registeredTypeIds.forEach { ServiceWorkerCodecRegistry.unregister(it) }
        FragmentRegistry.clear()
    }

    @Test
    fun coldStartQueuesAndCoalescesLaunchThenFlushesStrictFifoAndSupportsRequests() = runDesktopBlockingTest {
        val host = RecordingRuntimeHost()
        val worker = QueueWorker()
        worker.attachAndStart(host, ServiceWorkerOptions(appId = "test.queue", appName = "QueueTest"))
        try {
            assertFailsWith<ServiceWorkerIpcException> {
                worker.sendToFragment<QueueTargetFragment>(
                    message = QueuedCmd(0, "no-launch"),
                    launchIfNeeded = false
                )
            }
            assertEquals(0, host.snapshotLaunches().size)

            val launchArgs = FragmentBundle.of("tab" to "jobs")
            worker.sendToFragment<QueueTargetFragment>(QueuedCmd(1, "first"), launchIfNeeded = true, launchArgs = launchArgs)
            worker.sendToFragment<QueueTargetFragment>(QueuedCmd(2, "second"), launchIfNeeded = true)
            worker.sendToFragment<QueueTargetFragment>(QueuedCmd(3, "third"), launchIfNeeded = true)

            val coldReqDeferred = async {
                worker.requestFromFragment<QueueTargetFragment, FragCalcResp>(
                    payload = FragCalcReq(6, 7),
                    timeoutMs = 5_000L,
                    launchIfNeeded = true
                )
            }

            waitUntil { host.snapshotLaunches().size == 1 }
            // Give the queued launch a moment to ensure extra launches do not fire before mount.
            delay(50L)
            assertEquals(1, host.snapshotLaunches().size)
            val launch = host.snapshotLaunches().single()
            assertEquals("com.test.QueueTarget", launch.fragmentId)
            assertEquals("/queue-target", launch.route)
            assertFalse(launch.openAdditionalWindow)
            assertFalse(launch.clearTask)
            assertTrue(launch.bringToFront)
            assertEquals("jobs", launch.arguments?.getString("tab"))
            assertEquals(0, host.snapshotSent().size)
            assertFalse(worker.isFragmentActive<QueueTargetFragment>())

            worker.dispatchClientConnected("ui-1")
            worker.dispatchIncomingEnvelope(
                clientId = "ui-1",
                envelope = ServiceWorkerEnvelope(
                    kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                    correlationId = 0L,
                    clientId = "ui-1",
                    typeId = ServiceWorkerEnvelope.CONTROL_FRAGMENT_MOUNTED,
                    payloadJson = ServiceWorkerFragmentLaunchRequest.encodeMountControlPayload(
                        fragmentId = "com.test.QueueTarget",
                        instanceId = "inst-q1",
                        route = "/queue-target"
                    ),
                    sourceFragmentId = "com.test.QueueTarget"
                )
            )

            waitUntil { host.snapshotSent().size >= 4 && worker.snapshotConnected().size == 1 }
            assertTrue(worker.isFragmentActive<QueueTargetFragment>())
            assertFalse(worker.isFragmentActive<OtherFragment>())
            assertEquals(1, worker.activeFragments<QueueTargetFragment>().size)

            val delivered = host.snapshotSent()
            val flushedMsgs = delivered.filter { it.typeId == "test.q.QueuedCmd" }
            assertEquals(3, flushedMsgs.size)
            val seqs = flushedMsgs.map { env ->
                assertEquals("com.test.QueueTarget", env.targetFragmentId)
                (ServiceWorkerCodecRegistry.decodePayload(env.typeId, env.payloadJson) as QueuedCmd).seq
            }
            assertEquals(listOf(1, 2, 3), seqs)

            val reqEnv = delivered.firstOrNull { it.typeId == "test.q.FragCalcReq" }
            assertNotNull(reqEnv)
            assertEquals("com.test.QueueTarget", reqEnv.targetFragmentId)

            val (_, respJson) = ServiceWorkerCodecRegistry.encodePayload(FragCalcResp(42))
            worker.dispatchIncomingEnvelope(
                clientId = "ui-1",
                envelope = ServiceWorkerEnvelope(
                    kind = ServiceWorkerEnvelope.KIND_RESPONSE,
                    correlationId = reqEnv.correlationId,
                    clientId = "ui-1",
                    typeId = "test.q.FragCalcResp",
                    payloadJson = respJson,
                    sourceFragmentId = "com.test.QueueTarget"
                )
            )
            assertEquals(FragCalcResp(42), coldReqDeferred.await())

            // Fragment -> Worker targeted message and request/reply
            val (_, statusJson) = ServiceWorkerCodecRegistry.encodePayload(StatusUpdate("synced"))
            worker.dispatchIncomingEnvelope(
                clientId = "ui-1",
                envelope = ServiceWorkerEnvelope(
                    kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                    correlationId = 0L,
                    clientId = "ui-1",
                    typeId = "test.q.StatusUpdate",
                    payloadJson = statusJson,
                    sourceFragmentId = "com.test.QueueTarget"
                )
            )
            waitUntil { worker.snapshotMessages().size == 1 }
            assertEquals("inst-q1" to StatusUpdate("synced"), worker.snapshotMessages().single())

            val (_, fragReqJson) = ServiceWorkerCodecRegistry.encodePayload(FragCalcReq(8, 9))
            worker.dispatchIncomingEnvelope(
                clientId = "ui-1",
                envelope = ServiceWorkerEnvelope(
                    kind = ServiceWorkerEnvelope.KIND_REQUEST,
                    correlationId = 777L,
                    clientId = "ui-1",
                    typeId = "test.q.FragCalcReq",
                    payloadJson = fragReqJson,
                    sourceFragmentId = "com.test.QueueTarget"
                )
            )
            waitUntil {
                host.snapshotSent().any { it.correlationId == 777L && it.kind == ServiceWorkerEnvelope.KIND_RESPONSE }
            }
            val workerResp = host.snapshotSent().first { it.correlationId == 777L }
            assertEquals(
                FragCalcResp(72),
                ServiceWorkerCodecRegistry.decodePayload(workerResp.typeId, workerResp.payloadJson)
            )

            // Unmount notifies onFragmentDisconnected
            worker.dispatchIncomingEnvelope(
                clientId = "ui-1",
                envelope = ServiceWorkerEnvelope(
                    kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                    correlationId = 0L,
                    clientId = "ui-1",
                    typeId = ServiceWorkerEnvelope.CONTROL_FRAGMENT_UNMOUNTED,
                    payloadJson = ServiceWorkerFragmentLaunchRequest.encodeMountControlPayload(
                        fragmentId = "com.test.QueueTarget",
                        instanceId = "inst-q1",
                        route = "/queue-target"
                    ),
                    sourceFragmentId = "com.test.QueueTarget"
                )
            )
            waitUntil { !worker.isFragmentActive<QueueTargetFragment>() }
            assertEquals(1, worker.snapshotDisconnected().size)
        } finally {
            worker.performStop()
        }
    }

    @Test
    fun startFragmentAndAwaitResolvesHandleAndCloseEmitsControlClose() = runDesktopBlockingTest {
        val host = RecordingRuntimeHost()
        val worker = QueueWorker()
        worker.attachAndStart(host, ServiceWorkerOptions(appId = "test.await", appName = "AwaitTest"))
        try {
            val handleDeferred = async {
                worker.startFragmentAndAwait<OtherFragment>(
                    args = FragmentBundle.of("mode" to "compact"),
                    timeoutMs = 5_000L
                )
            }
            waitUntil { host.snapshotLaunches().size == 1 }
            assertEquals("com.test.Other", host.snapshotLaunches().single().fragmentId)

            worker.dispatchClientConnected("ui-2")
            worker.dispatchIncomingEnvelope(
                clientId = "ui-2",
                envelope = ServiceWorkerEnvelope(
                    kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                    correlationId = 0L,
                    clientId = "ui-2",
                    typeId = ServiceWorkerEnvelope.CONTROL_FRAGMENT_MOUNTED,
                    payloadJson = ServiceWorkerFragmentLaunchRequest.encodeMountControlPayload(
                        fragmentId = "com.test.Other",
                        instanceId = "inst-o1",
                        route = "/other"
                    ),
                    sourceFragmentId = "com.test.Other"
                )
            )
            val handle = withTimeout(5_000L) { handleDeferred.await() }
            assertEquals("com.test.Other", handle.fragmentId)
            assertEquals("inst-o1", handle.instanceId)
            assertEquals("/other", handle.route)

            handle.send(QueuedCmd(99, "direct-handle"))
            val sent = host.snapshotSent().last()
            assertEquals("com.test.Other", sent.targetFragmentId)

            handle.close()
            val closeEnv = host.snapshotSent().last()
            assertEquals(ServiceWorkerEnvelope.CONTROL_CLOSE_FRAGMENT, closeEnv.typeId)
            assertEquals("com.test.Other", closeEnv.targetFragmentId)
            assertEquals(
                "com.test.Other" to "inst-o1",
                ServiceWorkerFragmentLaunchRequest.decodeCloseControlPayload(closeEnv.payloadJson)
            )
            worker.closeFragment<OtherFragment>()
            assertEquals("com.test.Other" to null, host.snapshotCloses().last())
        } finally {
            worker.performStop()
        }
    }
}
