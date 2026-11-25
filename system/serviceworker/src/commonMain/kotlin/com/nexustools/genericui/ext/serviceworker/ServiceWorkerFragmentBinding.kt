@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.fragment.FragmentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.reflect.KClass

/**
 * UI-side IPC helper scoped to a [Fragment]'s lifecycle.
 *
 * Registers fragment-targeted [onMessage] / [onRequest] handlers, stamps [fragmentId] as
 * `sourceFragmentId` on outbound [send] / [request] frames, filters inbound envelopes by
 * `targetFragmentId == null || targetFragmentId == fragmentId`, emits
 * `__genericui.sw.control.fragmentMounted` once connected (and automatically on reconnect), and
 * emits `__genericui.sw.control.fragmentUnmounted` + disposes all handlers upon stop/destroy or
 * explicit [dispose].
 */
class ServiceWorkerFragmentBinding(
    val fragment: Fragment,
    val fragmentId: String = FragmentRegistry.resolveFragmentId(fragment::class),
    val instanceId: String = consumeAssignedInstanceId(fragment) ?: nextInstanceId(),
    val channel: ServiceWorkerChannel = ServiceWorkerExtension.channel
) : Disposable {

    val route: String? =
        FragmentRegistry.resolveRoute(fragment::class)
            ?: fragment.routeKey.takeIf { it.isNotBlank() }

    private val bindingScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mountMutex = Mutex()
    private val lock = AtomicBoolean(false)
    private val disposables = mutableListOf<Disposable>()

    private var lifecycleWatchJob: Job? = null
    private var started: Boolean = false
    private var lastMountedEpoch: Long = -1L

    @Volatile
    private var everSentMounted: Boolean = false

    @Volatile
    private var disposed: Boolean = false

    override val isDisposed: Boolean
        get() = disposed

    init {
        registerActiveBinding(fragment, this)
    }

    /**
     * Subscribes to messages of type [messageClass] targeted to this fragment (or global broadcasts).
     */
    fun <T : Any> onMessage(
        messageClass: KClass<T>,
        handler: suspend (T) -> Unit
    ): ServiceWorkerDisposableHandle {
        val handle = channel.onMessageForFragment(fragmentId, messageClass, handler)
        return trackDisposable(handle)
    }

    inline fun <reified T : Any> onMessage(
        noinline handler: suspend (T) -> Unit
    ): ServiceWorkerDisposableHandle = onMessage(T::class, handler)

    /**
     * Registers a request handler allowing the `@MainServiceWorker` to issue requests to this fragment
     * and respond via [ServiceWorkerReply].
     */
    fun <Req : Any> onRequest(
        requestClass: KClass<Req>,
        handler: suspend (Req, ServiceWorkerReply) -> Unit
    ): ServiceWorkerDisposableHandle {
        val handle = channel.onRequestForFragment(fragmentId, requestClass, handler)
        return trackDisposable(handle)
    }

    inline fun <reified Req : Any> onRequest(
        noinline handler: suspend (Req, ServiceWorkerReply) -> Unit
    ): ServiceWorkerDisposableHandle = onRequest(Req::class, handler)

    /**
     * Registers a suspending request handler returning [Res] directly for requests of [requestClass].
     */
    @Suppress("UNUSED_PARAMETER")
    fun <Req : Any, Res : Any> onRequest(
        requestClass: KClass<Req>,
        responseClass: KClass<Res>,
        handler: suspend (Req) -> Res
    ): ServiceWorkerDisposableHandle {
        val handle = channel.onRequestForFragment(fragmentId, requestClass) { req, reply ->
            try {
                reply.send(handler(req))
            } catch (t: Throwable) {
                if (!reply.isCompleted) {
                    reply.fail(t.message ?: t::class.simpleName ?: "Fragment request handler error")
                }
            }
        }
        return trackDisposable(handle)
    }

    inline fun <reified Req : Any, reified Res : Any> onRequest(
        noinline handler: suspend (Req) -> Res
    ): ServiceWorkerDisposableHandle = onRequest(Req::class, Res::class, handler)

    /**
     * Sends a one-way typed [message] from this fragment to the active `@MainServiceWorker`,
     * stamping `sourceFragmentId = fragmentId`.
     */
    suspend fun <T : Any> send(message: T) {
        if (disposed) {
            throw ServiceWorkerIpcException("ServiceWorkerFragmentBinding for '$fragmentId' is disposed.")
        }
        channel.sendInternal(
            message = message,
            targetFragmentId = null,
            sourceFragmentId = fragmentId
        )
    }

    /**
     * Sends a typed [payload] request from this fragment to the active `@MainServiceWorker`
     * (stamping `sourceFragmentId = fragmentId`) and awaits a response of [responseClass].
     */
    suspend fun <Req : Any, Res : Any> request(
        payload: Req,
        responseClass: KClass<Res>,
        timeoutMs: Long = 10_000L
    ): Res {
        if (disposed) {
            throw ServiceWorkerIpcException("ServiceWorkerFragmentBinding for '$fragmentId' is disposed.")
        }
        return channel.requestInternal(
            payload = payload,
            responseType = responseClass,
            timeoutMs = timeoutMs,
            targetFragmentId = null,
            sourceFragmentId = fragmentId
        )
    }

    suspend inline fun <reified Res : Any> request(
        payload: Any,
        timeoutMs: Long = 10_000L
    ): Res = request(payload, Res::class, timeoutMs)

    /**
     * Connects [channel] (if needed), emits `__genericui.sw.control.fragmentMounted`, and
     * synchronizes automatically across reconnects and fragment stop/destroy events.
     */
    fun start(): ServiceWorkerFragmentBinding {
        if (disposed || fragment.state == FragmentState.DESTROYED) {
            dispose()
            return this
        }
        val firstStart = withBindingLock {
            if (started) false else {
                started = true
                true
            }
        }
        if (firstStart) {
            trackDisposable(
                channel.onReconnected {
                    sendFragmentMountedIfNeeded()
                }
            )
            trackDisposable(
                channel.addConnectionListener { connected ->
                    if (connected && !disposed) {
                        bindingScope.launch {
                            sendFragmentMountedIfNeeded()
                        }
                    }
                }
            )
            startLifecycleWatcher()
        }
        if (channel.isSupported) {
            bindingScope.launch {
                connect()
            }
        }
        return this
    }

    /**
     * Connects [channel] if disconnected and emits `__genericui.sw.control.fragmentMounted` once
     * for the active connection epoch.
     */
    suspend fun connect(): Boolean {
        if (disposed || fragment.isDisposed || fragment.state == FragmentState.DESTROYED) {
            dispose()
            return false
        }
        if (!channel.isSupported) return false
        val ok = channel.isConnected || channel.connect()
        if (ok && !disposed && !fragment.isDisposed && fragment.state != FragmentState.DESTROYED) {
            sendFragmentMountedIfNeeded()
        }
        return ok
    }

    override fun dispose() {
        val shouldDispose = withBindingLock {
            if (disposed) return@withBindingLock false
            disposed = true
            true
        }
        if (!shouldDispose) return
        unregisterActiveBinding(fragment, this)
        lifecycleWatchJob?.cancel()
        lifecycleWatchJob = null

        val toDispose = withBindingLock {
            val snapshot = disposables.toList()
            disposables.clear()
            snapshot
        }
        for (item in toDispose) {
            item.dispose()
        }
        if (everSentMounted && channel.isConnected) {
            val payload = ServiceWorkerFragmentLaunchRequest.encodeMountControlPayload(
                fragmentId = fragmentId,
                instanceId = instanceId,
                route = route
            )
            channel.sendControlAsync(
                typeId = ServiceWorkerEnvelope.CONTROL_FRAGMENT_UNMOUNTED,
                payloadJson = payload,
                sourceFragmentId = fragmentId
            )
        }
        bindingScope.cancel()
    }

    private suspend fun sendFragmentMountedIfNeeded() {
        mountMutex.withLock {
            if (disposed || fragment.isDisposed || fragment.state == FragmentState.DESTROYED) return@withLock
            if (!channel.isConnected) return@withLock
            if (deferMountIfNeeded(this)) return@withLock
            val epoch = channel.connectionEpoch
            if (lastMountedEpoch == epoch && epoch > 0L) return@withLock
            val payload = ServiceWorkerFragmentLaunchRequest.encodeMountControlPayload(
                fragmentId = fragmentId,
                instanceId = instanceId,
                route = route
            )
            everSentMounted = true
            val sent = channel.sendControl(
                typeId = ServiceWorkerEnvelope.CONTROL_FRAGMENT_MOUNTED,
                payloadJson = payload,
                sourceFragmentId = fragmentId
            )
            if (sent) {
                lastMountedEpoch = epoch
            } else {
                if (lastMountedEpoch < 0L) {
                    everSentMounted = false
                }
            }
        }
    }

    private fun startLifecycleWatcher() {
        if (lifecycleWatchJob != null) return
        var sawActiveState = fragment.state == FragmentState.STARTED || fragment.state == FragmentState.RESUMED
        lifecycleWatchJob = bindingScope.launch {
            while (isActive && !disposed) {
                val state = fragment.state
                if (state == FragmentState.STARTED || state == FragmentState.RESUMED) {
                    sawActiveState = true
                }
                if (fragment.isDisposed || state == FragmentState.DESTROYED || (sawActiveState && state == FragmentState.STOPPED)) {
                    dispose()
                    break
                }
                delay(LIFECYCLE_POLL_INTERVAL_MS)
            }
        }
    }

    private fun trackDisposable(handle: ServiceWorkerDisposableHandle): ServiceWorkerDisposableHandle {
        val alreadyDisposed = withBindingLock {
            if (disposed) {
                true
            } else {
                disposables.add(handle)
                false
            }
        }
        if (alreadyDisposed) {
            handle.dispose()
        }
        return handle
    }

    private inline fun <T> withBindingLock(block: () -> T): T {
        while (!lock.compareAndSet(expectedValue = false, newValue = true)) {
            // Brief spin around binding state.
        }
        return try {
            block()
        } finally {
            lock.store(false)
        }
    }

    internal companion object {
        private const val LIFECYCLE_POLL_INTERVAL_MS: Long = 40L
        private val nextInstanceSeq = AtomicLong(1L)
        private val registryLock = AtomicBoolean(false)
        private val activeBindingsByFragment = linkedMapOf<Fragment, MutableList<ServiceWorkerFragmentBinding>>()
        private val pendingInstanceIdsByFragment = linkedMapOf<Fragment, String>()
        private var appStartupMountHold: Boolean = false
        private var deferredMountHoldDepth: Int = 0
        private val deferredBindingsPendingMount = linkedSetOf<ServiceWorkerFragmentBinding>()

        internal fun setAppStartupMountHold(active: Boolean) {
            val toFlush = withRegistryLock {
                appStartupMountHold = active
                drainReadyDeferredBindingsLocked()
            }
            flushDeferredBindings(toFlush)
        }

        internal inline fun <T> runWithDeferredMountEmissions(block: () -> T): T {
            withRegistryLock {
                deferredMountHoldDepth++
            }
            return try {
                block()
            } finally {
                val toFlush = withRegistryLock {
                    if (deferredMountHoldDepth > 0) {
                        deferredMountHoldDepth--
                    }
                    drainReadyDeferredBindingsLocked()
                }
                flushDeferredBindings(toFlush)
            }
        }

        private fun deferMountIfNeeded(binding: ServiceWorkerFragmentBinding): Boolean = withRegistryLock {
            if (appStartupMountHold || deferredMountHoldDepth > 0) {
                if (!binding.isDisposed && !binding.fragment.isDisposed && binding.fragment.state != FragmentState.DESTROYED) {
                    deferredBindingsPendingMount.add(binding)
                }
                true
            } else {
                false
            }
        }

        private fun drainReadyDeferredBindingsLocked(): List<ServiceWorkerFragmentBinding> {
            if (appStartupMountHold || deferredMountHoldDepth > 0 || deferredBindingsPendingMount.isEmpty()) {
                return emptyList()
            }
            val snapshot = deferredBindingsPendingMount.toList()
            deferredBindingsPendingMount.clear()
            return snapshot
        }

        private fun flushDeferredBindings(bindings: List<ServiceWorkerFragmentBinding>) {
            for (binding in bindings) {
                if (!binding.isDisposed && !binding.fragment.isDisposed && binding.fragment.state != FragmentState.DESTROYED) {
                    binding.bindingScope.launch {
                        binding.sendFragmentMountedIfNeeded()
                    }
                }
            }
        }

        fun nextInstanceId(): String = "frag-inst-${nextInstanceSeq.fetchAndAdd(1L)}"

        fun getOrCreate(fragment: Fragment): ServiceWorkerFragmentBinding {
            val existing = withRegistryLock {
                activeBindingsByFragment[fragment]?.firstOrNull { !it.isDisposed }
            }
            if (existing != null) return existing
            return ServiceWorkerFragmentBinding(fragment)
        }

        fun activeBindingFor(fragment: Fragment): ServiceWorkerFragmentBinding? = withRegistryLock {
            activeBindingsByFragment[fragment]?.firstOrNull { !it.isDisposed }
        }

        fun hasActiveBindingFor(fragment: Fragment): Boolean =
            activeBindingFor(fragment) != null

        fun assignPendingInstanceId(fragment: Fragment, instanceId: String) {
            withRegistryLock {
                pendingInstanceIdsByFragment[fragment] = instanceId
            }
        }

        fun disposeBindingsForFragment(fragment: Fragment) {
            val snapshot = withRegistryLock {
                pendingInstanceIdsByFragment.remove(fragment)
                activeBindingsByFragment.remove(fragment)?.toList().orEmpty()
            }
            for (binding in snapshot) {
                binding.dispose()
            }
        }

        fun disposeAllBindings() {
            val snapshot = withRegistryLock {
                appStartupMountHold = false
                deferredMountHoldDepth = 0
                deferredBindingsPendingMount.clear()
                pendingInstanceIdsByFragment.clear()
                val all = activeBindingsByFragment.values.flatten()
                activeBindingsByFragment.clear()
                all
            }
            for (binding in snapshot) {
                binding.dispose()
            }
        }

        fun clearAllForTests() {
            disposeAllBindings()
        }

        private fun consumeAssignedInstanceId(fragment: Fragment): String? = withRegistryLock {
            pendingInstanceIdsByFragment.remove(fragment)
        }

        private fun registerActiveBinding(fragment: Fragment, binding: ServiceWorkerFragmentBinding) {
            withRegistryLock {
                val list = activeBindingsByFragment.getOrPut(fragment) { mutableListOf() }
                if (binding !in list) {
                    list.add(binding)
                }
            }
        }

        private fun unregisterActiveBinding(fragment: Fragment, binding: ServiceWorkerFragmentBinding) {
            withRegistryLock {
                deferredBindingsPendingMount.remove(binding)
                pendingInstanceIdsByFragment.remove(fragment)
                val list = activeBindingsByFragment[fragment] ?: return@withRegistryLock
                list.remove(binding)
                if (list.isEmpty()) {
                    activeBindingsByFragment.remove(fragment)
                }
            }
        }

        private inline fun <T> withRegistryLock(block: () -> T): T {
            while (!registryLock.compareAndSet(expectedValue = false, newValue = true)) {
                // Brief spin around binding registry.
            }
            return try {
                block()
            } finally {
                registryLock.store(false)
            }
        }
    }
}
