@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.enums.system.BackendTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceWorkerChannelConcurrencyAndReconnectTest {

    private class SampleUiFragment : Fragment()

    private class MockChannelRuntime : ServiceWorkerPlatformRuntime {
        private val lock = AtomicBoolean(false)
        override val isSupported: Boolean = true
        override val supportsMultipleWindows: Boolean = true
        override val supportsFragmentLaunch: Boolean = true
        override val supportsDialogFragments: Boolean = true
        override val supportsSystemTray: Boolean = true
        override val supportsOngoingNotification: Boolean = false
        override val supportsAutostart: Boolean = false

        val connectCalls = AtomicInt(0)
        var connectGate: CompletableDeferred<Boolean>? = null
        val sentEnvelopes = mutableListOf<ServiceWorkerEnvelope>()

        @Volatile
        var connectedFlag: Boolean = false

        @Volatile
        private var incomingCallback: ((ServiceWorkerEnvelope) -> Unit)? = null

        @Volatile
        private var connectionCallback: ((Boolean) -> Unit)? = null

        override fun configure(options: ServiceWorkerOptions) {}

        override fun isClientConnected(): Boolean = connectedFlag

        override suspend fun connectClient(
            options: ServiceWorkerOptions,
            onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
            onConnectionChanged: (Boolean) -> Unit
        ): Boolean {
            connectCalls.fetchAndAdd(1)
            incomingCallback = onIncomingEnvelope
            connectionCallback = onConnectionChanged
            val gate = connectGate
            val result = gate?.await() ?: true
            connectedFlag = result
            if (result) {
                onConnectionChanged(true)
            }
            return result
        }

        override suspend fun disconnectClient() {
            connectedFlag = false
            connectionCallback?.invoke(false)
        }

        override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean {
            if (!connectedFlag) return false
            withLock { sentEnvelopes.add(envelope) }
            return true
        }

        override fun currentBackendTarget(): BackendTarget = BackendTarget.Swing

        fun simulateIncoming(envelope: ServiceWorkerEnvelope) {
            incomingCallback?.invoke(envelope)
        }

        fun simulateConnectionDropAndReconnect() {
            connectedFlag = false
            connectionCallback?.invoke(false)
            connectedFlag = true
            connectionCallback?.invoke(true)
        }

        fun snapshotSent(): List<ServiceWorkerEnvelope> = withLock { sentEnvelopes.toList() }

        private inline fun <T> withLock(block: () -> T): T {
            while (!lock.compareAndSet(expectedValue = false, newValue = true)) {
                // Spin briefly around in-memory test list.
            }
            return try {
                block()
            } finally {
                lock.store(false)
            }
        }
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
        FragmentRegistry.register(SampleUiFragment::class) { SampleUiFragment() }
        FragmentRegistry.registerMetadata(
            fragmentClass = SampleUiFragment::class,
            fragmentId = "com.test.SampleUiFragment",
            route = "/sample"
        )
    }

    @AfterTest
    fun tearDown() {
        ServiceWorkerExtension.resetForTests()
        FragmentRegistry.clear()
    }

    @Test
    fun concurrentAndRepeatedConnectCallsAreSerializedAndIdempotent() = runDesktopBlockingTest {
        val runtime = MockChannelRuntime()
        val gate = CompletableDeferred<Boolean>()
        runtime.connectGate = gate
        ServiceWorkerExtension.installRuntimeOverrideForTests(runtime)
        val channel = ServiceWorkerExtension.channel

        val jobs = (1..8).map {
            async { channel.connect() }
        }
        waitUntil { runtime.connectCalls.load() == 1 }
        delay(30L)
        assertEquals(1, runtime.connectCalls.load(), "Mutex must serialize concurrent connect() callers.")

        gate.complete(true)
        val results = withTimeout(5_000L) { jobs.awaitAll() }
        assertTrue(results.all { it })
        assertEquals(1, runtime.connectCalls.load())

        // Repeated calls when already connected short-circuit immediately without reconnecting.
        assertTrue(channel.connect())
        assertTrue(channel.connect())
        assertEquals(1, runtime.connectCalls.load())
        assertEquals(1L, channel.connectionEpoch)
    }

    @Test
    fun fragmentBindingReEmitsMountedOnReconnectAndDisposesCleanly() = runDesktopBlockingTest {
        val runtime = MockChannelRuntime()
        ServiceWorkerExtension.installRuntimeOverrideForTests(runtime)
        val channel = ServiceWorkerExtension.channel

        val hostContainer = FragmentContainer(hostWindow = Window(title = "TestHost"))
        val fragment = SampleUiFragment()
        hostContainer.setFragment(fragment)
        val received = mutableListOf<String>()
        val binding = ServiceWorkerFragmentBinding(
            fragment = fragment,
            channel = channel
        ).apply {
            onMessage<String> { msg -> received.add(msg) }
            start()
        }

        waitUntil {
            runtime.snapshotSent().any { it.typeId == ServiceWorkerEnvelope.CONTROL_FRAGMENT_MOUNTED }
        }
        val initialMounted = runtime.snapshotSent().filter {
            it.typeId == ServiceWorkerEnvelope.CONTROL_FRAGMENT_MOUNTED
        }
        assertEquals(1, initialMounted.size)
        val initialMountPayload = ServiceWorkerFragmentLaunchRequest.decodeMountControlPayload(
            initialMounted.single().payload
        )
        assertEquals("com.test.SampleUiFragment", initialMountPayload?.first)
        assertEquals(binding.instanceId, initialMountPayload?.second)

        // Simulate disconnect -> reconnect cycle: binding must automatically re-emit fragmentMounted.
        runtime.simulateConnectionDropAndReconnect()
        waitUntil {
            runtime.snapshotSent().count { it.typeId == ServiceWorkerEnvelope.CONTROL_FRAGMENT_MOUNTED } == 2
        }
        val secondMounted = runtime.snapshotSent().filter {
            it.typeId == ServiceWorkerEnvelope.CONTROL_FRAGMENT_MOUNTED
        }.last()
        val secondMountPayload = ServiceWorkerFragmentLaunchRequest.decodeMountControlPayload(secondMounted.payload)
        assertEquals(binding.instanceId, secondMountPayload?.second)

        // Inbound targeted message arrives cleanly while bound.
        val (typeId, payload) = ServiceWorkerCodecRegistry.encodePayload("hello-bound")
        runtime.simulateIncoming(
            ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                correlationId = 0L,
                clientId = "ui",
                typeId = typeId,
                payload = payload,
                targetFragmentId = "com.test.SampleUiFragment"
            )
        )
        waitUntil { received.size == 1 }
        assertEquals(listOf("hello-bound"), received)

        // Destroying via FragmentContainer triggers DESTROYED state -> unmounted control + removes handlers.
        hostContainer.clearCurrentFragment()
        waitUntil {
            binding.isDisposed &&
                runtime.snapshotSent().any { it.typeId == ServiceWorkerEnvelope.CONTROL_FRAGMENT_UNMOUNTED }
        }
        assertTrue(binding.isDisposed)
        val unmounted = runtime.snapshotSent().first {
            it.typeId == ServiceWorkerEnvelope.CONTROL_FRAGMENT_UNMOUNTED
        }
        val unmountPayload = ServiceWorkerFragmentLaunchRequest.decodeMountControlPayload(unmounted.payload)
        assertEquals("com.test.SampleUiFragment", unmountPayload?.first)
        assertEquals(binding.instanceId, unmountPayload?.second)

        // Subsequent inbound message must NOT be delivered to disposed binding handlers.
        val (t2, p2) = ServiceWorkerCodecRegistry.encodePayload("after-dispose")
        runtime.simulateIncoming(
            ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                correlationId = 0L,
                clientId = "ui",
                typeId = t2,
                payload = p2,
                targetFragmentId = "com.test.SampleUiFragment"
            )
        )
        delay(40L)
        assertEquals(listOf("hello-bound"), received)
        assertFalse(runtime.snapshotSent().count { it.typeId == ServiceWorkerEnvelope.CONTROL_FRAGMENT_UNMOUNTED } > 1)
    }

    @Test
    fun fifoMessageDispatchAndDeferredMountEmissionsWaitUntilWindowAndStyleSettle() = runDesktopBlockingTest {
        val runtime = MockChannelRuntime()
        ServiceWorkerExtension.installRuntimeOverrideForTests(runtime)
        val channel = ServiceWorkerExtension.channel

        val hostContainer = FragmentContainer(hostWindow = Window(title = "DeferHost"))
        val fragment = SampleUiFragment()
        hostContainer.setFragment(fragment)

        val orderLog = mutableListOf<String>()
        val binding = ServiceWorkerFragmentBinding(
            fragment = fragment,
            channel = channel
        ).apply {
            onMessage<String> { msg ->
                if (msg == "first") {
                    delay(35L)
                }
                orderLog.add(msg)
            }
        }

        // Hold mount emission until styling and Window.show complete.
        ServiceWorkerFragmentBinding.runWithDeferredMountEmissions {
            binding.start()
            waitUntil { runtime.connectedFlag }
            delay(40L)
            assertEquals(
                0,
                runtime.snapshotSent().count { it.typeId == ServiceWorkerEnvelope.CONTROL_FRAGMENT_MOUNTED },
                "CONTROL_FRAGMENT_MOUNTED must be held until runWithDeferredMountEmissions exits."
            )
        }

        waitUntil {
            runtime.snapshotSent().count { it.typeId == ServiceWorkerEnvelope.CONTROL_FRAGMENT_MOUNTED } == 1
        }

        // Back-to-back inbound KIND_MESSAGE frames must execute strictly FIFO without overtaking.
        val (t1, p1) = ServiceWorkerCodecRegistry.encodePayload("first")
        val (t2, p2) = ServiceWorkerCodecRegistry.encodePayload("second")
        runtime.simulateIncoming(
            ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                correlationId = 0L,
                clientId = "ui",
                typeId = t1,
                payload = p1,
                targetFragmentId = "com.test.SampleUiFragment"
            )
        )
        runtime.simulateIncoming(
            ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                correlationId = 0L,
                clientId = "ui",
                typeId = t2,
                payload = p2,
                targetFragmentId = "com.test.SampleUiFragment"
            )
        )

        waitUntil { orderLog.size == 2 }
        assertEquals(listOf("first", "second"), orderLog)
    }
}
