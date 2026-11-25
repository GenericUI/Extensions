@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.serviceworker.dsl

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.serviceworker.ServiceWorker
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerCodecRegistry
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerEnvelope
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerExtension
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerFragmentBinding
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerMessageCodec
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerNotificationAction
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerOptions
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerPlatformRuntime
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerRuntimeHost
import com.nexustools.genericui.resources.ResourceID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ServiceWorkerFragmentDslTest {

    private data class DslUiCmd(val text: String)
    private data class DslStatusReq(val probe: String)
    private data class DslStatusResp(val answer: String)
    private data class DslFragEvent(val count: Int)
    private data class DslWorkerEchoReq(val value: Int)
    private data class DslWorkerEchoResp(val echoed: Int)

    private class DslSampleFragment : Fragment() {
        val received = mutableListOf<DslUiCmd>()
        lateinit var binding: ServiceWorkerFragmentBinding
            private set

        override fun onStart() {
            binding = bindServiceWorker {
                onMessage<DslUiCmd> { cmd ->
                    received.add(cmd)
                }
                onRequest<DslStatusReq, DslStatusResp> { req ->
                    DslStatusResp("dsl:${req.probe}")
                }
            }
        }
    }

    private class DslTestWorker : ServiceWorker() {
        private val lock = AtomicBoolean(false)
        val events = mutableListOf<Pair<String, DslFragEvent>>()

        override fun onStart() {
            onFragmentMessage<DslSampleFragment, DslFragEvent> { handle, event ->
                withLock { events.add(handle.fragmentId to event) }
            }
            onFragmentRequest<DslSampleFragment, DslWorkerEchoReq, DslWorkerEchoResp> { _, req ->
                DslWorkerEchoResp(req.value * 10)
            }
        }

        fun snapshotEvents(): List<Pair<String, DslFragEvent>> = withLock { events.toList() }

        private inline fun <T> withLock(block: () -> T): T {
            while (!lock.compareAndSet(expectedValue = false, newValue = true)) {}
            return try {
                block()
            } finally {
                lock.store(false)
            }
        }
    }

    private class DslBridgeRuntime(
        private val worker: ServiceWorker
    ) : ServiceWorkerPlatformRuntime, ServiceWorkerRuntimeHost {
        override val backendName: String = BackendTarget.Swing.displayName
        override val isSupported: Boolean = true
        override val supportsMultipleWindows: Boolean = true
        override val supportsFragmentLaunch: Boolean = true
        override val supportsDialogFragments: Boolean = true
        override val supportsSystemTray: Boolean = true
        override val supportsOngoingNotification: Boolean = false
        override val supportsAutostart: Boolean = false

        @Volatile
        private var connected: Boolean = false

        @Volatile
        private var incomingToUi: ((ServiceWorkerEnvelope) -> Unit)? = null

        @Volatile
        private var connChanged: ((Boolean) -> Unit)? = null

        override fun configure(options: ServiceWorkerOptions) {}
        override fun isClientConnected(): Boolean = connected

        override suspend fun connectClient(
            options: ServiceWorkerOptions,
            onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
            onConnectionChanged: (Boolean) -> Unit
        ): Boolean {
            if (!worker.isRunning) {
                worker.attachAndStart(this, options)
            }
            incomingToUi = onIncomingEnvelope
            connChanged = onConnectionChanged
            connected = true
            worker.dispatchClientConnected("dsl-ui")
            onConnectionChanged(true)
            return true
        }

        override suspend fun disconnectClient() {
            connected = false
            connChanged?.invoke(false)
            worker.dispatchClientDisconnected("dsl-ui")
        }

        override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean {
            if (!connected) return false
            worker.dispatchIncomingEnvelope("dsl-ui", envelope.copy(clientId = "dsl-ui"))
            return true
        }

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
        override fun requestLaunchFragment(request: com.nexustools.genericui.ext.serviceworker.ServiceWorkerFragmentLaunchRequest) {}
        override fun requestCloseFragment(fragmentId: String, instanceId: String?) {}
        override fun stopSelf() {}
        override fun exitApplication(exitCode: Int) {}
        override fun isAutostartEnabled(): Boolean = false
        override fun setAutostartEnabled(enabled: Boolean): Boolean = false

        override suspend fun sendToClient(clientId: String, envelope: ServiceWorkerEnvelope): Boolean {
            if (!connected) return false
            incomingToUi?.invoke(envelope)
            return true
        }

        override suspend fun broadcastEnvelope(envelope: ServiceWorkerEnvelope): Int {
            if (!connected) return 0
            incomingToUi?.invoke(envelope)
            return 1
        }

        override fun disconnectClient(clientId: String) {}
        override fun currentBackendTarget(): BackendTarget = BackendTarget.Swing
        override fun activeWorker(): ServiceWorker = worker
    }

    private val codecIds = listOf(
        "dslfrag.DslUiCmd",
        "dslfrag.DslStatusReq",
        "dslfrag.DslStatusResp",
        "dslfrag.DslFragEvent",
        "dslfrag.DslWorkerEchoReq",
        "dslfrag.DslWorkerEchoResp"
    )

    private fun registerCodecs() {
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<DslUiCmd> {
            override val typeId = "dslfrag.DslUiCmd"
            override val messageClass = DslUiCmd::class
            override fun encode(value: DslUiCmd) = PreferenceElement.ofObject(
                mapOf("text" to PreferenceElement.ofString(value.text))
            )
            override fun decode(element: PreferenceElement) = DslUiCmd(
                element.mapEntries["text"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<DslStatusReq> {
            override val typeId = "dslfrag.DslStatusReq"
            override val messageClass = DslStatusReq::class
            override fun encode(value: DslStatusReq) = PreferenceElement.ofObject(
                mapOf("probe" to PreferenceElement.ofString(value.probe))
            )
            override fun decode(element: PreferenceElement) = DslStatusReq(
                element.mapEntries["probe"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<DslStatusResp> {
            override val typeId = "dslfrag.DslStatusResp"
            override val messageClass = DslStatusResp::class
            override fun encode(value: DslStatusResp) = PreferenceElement.ofObject(
                mapOf("answer" to PreferenceElement.ofString(value.answer))
            )
            override fun decode(element: PreferenceElement) = DslStatusResp(
                element.mapEntries["answer"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<DslFragEvent> {
            override val typeId = "dslfrag.DslFragEvent"
            override val messageClass = DslFragEvent::class
            override fun encode(value: DslFragEvent) = PreferenceElement.ofObject(
                mapOf("count" to PreferenceElement.ofInt(value.count))
            )
            override fun decode(element: PreferenceElement) = DslFragEvent(
                element.mapEntries["count"]?.asInt() ?: 0
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<DslWorkerEchoReq> {
            override val typeId = "dslfrag.DslWorkerEchoReq"
            override val messageClass = DslWorkerEchoReq::class
            override fun encode(value: DslWorkerEchoReq) = PreferenceElement.ofObject(
                mapOf("value" to PreferenceElement.ofInt(value.value))
            )
            override fun decode(element: PreferenceElement) = DslWorkerEchoReq(
                element.mapEntries["value"]?.asInt() ?: 0
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<DslWorkerEchoResp> {
            override val typeId = "dslfrag.DslWorkerEchoResp"
            override val messageClass = DslWorkerEchoResp::class
            override fun encode(value: DslWorkerEchoResp) = PreferenceElement.ofObject(
                mapOf("echoed" to PreferenceElement.ofInt(value.echoed))
            )
            override fun decode(element: PreferenceElement) = DslWorkerEchoResp(
                element.mapEntries["echoed"]?.asInt() ?: 0
            )
        })
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
        ServiceWorkerExtension.resetForTests()
        FragmentRegistry.clear()
        FragmentRegistry.register(DslSampleFragment::class) { DslSampleFragment() }
        FragmentRegistry.registerMetadata(
            fragmentClass = DslSampleFragment::class,
            fragmentId = "com.test.dsl.DslSampleFragment",
            route = "/dsl-sample"
        )
        registerCodecs()
    }

    @AfterTest
    fun tearDown() {
        ServiceWorkerExtension.resetForTests()
        codecIds.forEach { ServiceWorkerCodecRegistry.unregister(it) }
        FragmentRegistry.clear()
    }

    @Test
    fun fragmentBindServiceWorkerDslRegistersHandlersBeforeMountAndSupportsBidirectionalIpc() = runBlocking {
        val worker = DslTestWorker()
        val bridge = DslBridgeRuntime(worker)
        val options = ServiceWorkerOptions(appId = "com.test.dsl.frag", appName = "DslFragTest", hasWorker = true)
        ServiceWorkerExtension.install(options)
        ServiceWorkerExtension.installRuntimeOverrideForTests(bridge)

        val hostContainer = FragmentContainer(hostWindow = Window(title = "DslHost"))
        val fragment = DslSampleFragment()
        hostContainer.setFragment(fragment)
        fragment.onStart()
        val binding = fragment.binding
        assertSame(binding, fragment.bindServiceWorker {}, "Repeated bindServiceWorker reuses existing active binding.")

        waitUntil { worker.isFragmentActive<DslSampleFragment>() }

        worker.sendToFragment<DslSampleFragment>(DslUiCmd("hello-dsl"))
        waitUntil { fragment.received.size == 1 }
        assertEquals(listOf(DslUiCmd("hello-dsl")), fragment.received)

        val fragResp = worker.requestFromFragment<DslSampleFragment, DslStatusResp>(
            DslStatusReq("health"),
            timeoutMs = 5_000L
        )
        assertEquals(DslStatusResp("dsl:health"), fragResp)

        binding.send(DslFragEvent(7))
        waitUntil { worker.snapshotEvents().size == 1 }
        assertEquals("com.test.dsl.DslSampleFragment" to DslFragEvent(7), worker.snapshotEvents().single())

        val echoResp = binding.request<DslWorkerEchoResp>(DslWorkerEchoReq(6), timeoutMs = 5_000L)
        assertEquals(DslWorkerEchoResp(60), echoResp)

        hostContainer.clearCurrentFragment()
        waitUntil { binding.isDisposed && !worker.isFragmentActive<DslSampleFragment>() }
        assertTrue(binding.isDisposed)
        assertFalse(worker.isFragmentActive<DslSampleFragment>())
        worker.performStop()
    }
}
