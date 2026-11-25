@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.threading.GenericUiThreading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.reflect.KClass

/**
 * Client-side typed IPC communication channel between UI components (`@MainAppFragment`, views,
 * controllers) and the active `@MainServiceWorker`.
 *
 * On iOS / iPadOS, [isSupported] is `false` and [send] / [request] fail fast with
 * [ServiceWorkerUnsupportedException] (or `Result.failure(...)` from [tryRequest]).
 */
class ServiceWorkerChannel internal constructor(
    private val runtimeResolver: () -> ServiceWorkerPlatformRuntime,
    private val optionsResolver: () -> ServiceWorkerOptions
) {
    private class ChannelMessageHandler(
        val typeId: String,
        val payloadClass: KClass<*>,
        val targetFragmentId: String? = null,
        val handler: suspend (Any?) -> Unit
    )

    private class ChannelRequestHandler(
        val typeId: String,
        val payloadClass: KClass<*>,
        val targetFragmentId: String? = null,
        val handler: suspend (Any?, ServiceWorkerReply) -> Unit
    )

    private class PendingChannelRequest(
        val correlationId: Long,
        val deferred: CompletableDeferred<ServiceWorkerEnvelope>
    )

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inboundMessageQueue: Channel<suspend () -> Unit> = Channel(Channel.UNLIMITED)
    private val connectionMutex = Mutex()
    private val stateLock = AtomicBoolean(false)

    private val messageHandlers = mutableListOf<ChannelMessageHandler>()
    private val requestHandlers = mutableListOf<ChannelRequestHandler>()
    private val connectionListeners = mutableListOf<(Boolean) -> Unit>()
    private val reconnectedListeners = mutableListOf<suspend () -> Unit>()
    private val pendingRequests = mutableListOf<PendingChannelRequest>()

    private var nextCorrelationId: Long = 1L
    private var lastClientId: String = "client-main"
    private var lastKnownConnectedState: Boolean? = null
    private var hasConnectedOnce: Boolean = false

    @Volatile
    internal var connectionEpoch: Long = 0L
        private set

    init {
        scope.launch {
            for (dispatchOp in inboundMessageQueue) {
                try {
                    dispatchOp()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    // Keep the FIFO message dispatcher alive if a message handler throws.
                }
            }
        }
    }

    val isSupported: Boolean
        get() = runtimeResolver().isSupported

    val isConnected: Boolean
        get() = runtimeResolver().isClientConnected()

    /**
     * Connects to the background Service Worker IPC endpoint (or in-process binder / Web Worker).
     * Concurrent or repeated calls while connected/connecting are serialized and idempotent.
     * Returns `false` cleanly on unsupported platforms (iOS / iPadOS).
     */
    suspend fun connect(): Boolean = connectionMutex.withLock {
        val runtime = runtimeResolver()
        if (!runtime.isSupported) {
            return@withLock false
        }
        if (runtime.isClientConnected()) {
            val needsConnectedNotification = withChannelLock { lastKnownConnectedState != true }
            if (needsConnectedNotification) {
                notifyConnectionChanged(true)
            }
            return@withLock true
        }
        val ok = runtime.connectClient(
            options = optionsResolver(),
            onIncomingEnvelope = ::handleIncomingEnvelope,
            onConnectionChanged = ::notifyConnectionChanged
        )
        if (ok) {
            val needsConnectedNotification = withChannelLock { lastKnownConnectedState != true }
            if (needsConnectedNotification) {
                notifyConnectionChanged(true)
            }
        }
        ok
    }

    /**
     * Disconnects this client channel cleanly without stopping the background Service Worker.
     */
    suspend fun disconnect() = connectionMutex.withLock {
        failPendingRequests("ServiceWorkerChannel disconnected.")
        runtimeResolver().disconnectClient()
        notifyConnectionChanged(false)
    }

    /**
     * Sends a one-way typed [message] to the active `@MainServiceWorker`.
     */
    suspend fun send(message: Any) {
        sendInternal(message = message, targetFragmentId = null, sourceFragmentId = null)
    }

    internal suspend fun sendInternal(
        message: Any,
        targetFragmentId: String? = null,
        sourceFragmentId: String? = null
    ) {
        val runtime = runtimeResolver()
        if (!runtime.isSupported) {
            throw ServiceWorkerUnsupportedException(backendName(runtime))
        }
        if (!runtime.isClientConnected()) {
            val connected = connect()
            if (!connected) {
                throw ServiceWorkerIpcException("Unable to connect ServiceWorkerChannel to background ServiceWorker.")
            }
        }
        val (typeId, payloadJson) = ServiceWorkerCodecRegistry.encodePayload(message)
        val env = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_MESSAGE,
            correlationId = 0L,
            clientId = lastClientId,
            typeId = typeId,
            payloadJson = payloadJson,
            targetFragmentId = targetFragmentId,
            sourceFragmentId = sourceFragmentId
        )
        if (!runtime.sendClientEnvelope(env)) {
            throw ServiceWorkerIpcException("Failed to send message of type '$typeId' to ServiceWorker.")
        }
    }

    internal suspend fun sendControl(
        typeId: String,
        payloadJson: String,
        targetFragmentId: String? = null,
        sourceFragmentId: String? = null
    ): Boolean {
        val runtime = runtimeResolver()
        if (!runtime.isSupported) {
            throw ServiceWorkerUnsupportedException(backendName(runtime))
        }
        if (!runtime.isClientConnected()) {
            if (!connect()) return false
        }
        val env = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_MESSAGE,
            correlationId = 0L,
            clientId = lastClientId,
            typeId = typeId,
            payloadJson = payloadJson,
            targetFragmentId = targetFragmentId,
            sourceFragmentId = sourceFragmentId
        )
        return runtime.sendClientEnvelope(env)
    }

    internal fun sendControlAsync(
        typeId: String,
        payloadJson: String,
        targetFragmentId: String? = null,
        sourceFragmentId: String? = null
    ) {
        scope.launch {
            val runtime = runtimeResolver()
            if (!runtime.isSupported || !runtime.isClientConnected()) return@launch
            runtime.sendClientEnvelope(
                ServiceWorkerEnvelope(
                    kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                    correlationId = 0L,
                    clientId = lastClientId,
                    typeId = typeId,
                    payloadJson = payloadJson,
                    targetFragmentId = targetFragmentId,
                    sourceFragmentId = sourceFragmentId
                )
            )
        }
    }

    /**
     * Sends a typed [payload] request to the active `@MainServiceWorker` and awaits a response of [responseType].
     */
    suspend fun <R : Any> request(
        payload: Any,
        responseType: KClass<R>,
        timeoutMs: Long = 30_000L
    ): R = requestInternal(
        payload = payload,
        responseType = responseType,
        timeoutMs = timeoutMs,
        targetFragmentId = null,
        sourceFragmentId = null
    )

    internal suspend fun <R : Any> requestInternal(
        payload: Any,
        responseType: KClass<R>,
        timeoutMs: Long = 30_000L,
        targetFragmentId: String? = null,
        sourceFragmentId: String? = null
    ): R {
        val runtime = runtimeResolver()
        if (!runtime.isSupported) {
            throw ServiceWorkerUnsupportedException(backendName(runtime))
        }
        if (!runtime.isClientConnected()) {
            val connected = connect()
            if (!connected) {
                throw ServiceWorkerIpcException("Unable to connect ServiceWorkerChannel to background ServiceWorker.")
            }
        }
        val (typeId, payloadJson) = ServiceWorkerCodecRegistry.encodePayload(payload)
        val deferred = CompletableDeferred<ServiceWorkerEnvelope>()
        val entry = withChannelLock {
            val corrId = nextCorrelationId++
            PendingChannelRequest(corrId, deferred).also { pendingRequests.add(it) }
        }
        try {
            val env = ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_REQUEST,
                correlationId = entry.correlationId,
                clientId = lastClientId,
                typeId = typeId,
                payloadJson = payloadJson,
                targetFragmentId = targetFragmentId,
                sourceFragmentId = sourceFragmentId
            )
            if (!runtime.sendClientEnvelope(env)) {
                throw ServiceWorkerIpcException("Failed to send request '$typeId' to ServiceWorker.")
            }
            val response = withTimeout(timeoutMs) { deferred.await() }
            if (response.kind == ServiceWorkerEnvelope.KIND_RESPONSE_ERR) {
                throw ServiceWorkerIpcException(
                    response.error.ifEmpty { "ServiceWorker rejected request '$typeId'." }
                )
            }
            return ServiceWorkerCodecRegistry.decodeAs(response.typeId, response.payloadJson, responseType)
        } catch (t: TimeoutCancellationException) {
            throw ServiceWorkerIpcException("Timed out waiting for ServiceWorker response for '$typeId'.", t)
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            withChannelLock {
                pendingRequests.remove(entry)
            }
        }
    }

    suspend inline fun <reified R : Any> request(
        payload: Any,
        timeoutMs: Long = 30_000L
    ): R = request(payload, R::class, timeoutMs)

    /**
     * Non-throwing variant of [request] returning [Result.failure] on iOS/iPadOS or transport errors.
     */
    suspend fun <R : Any> tryRequest(
        payload: Any,
        responseType: KClass<R>,
        timeoutMs: Long = 30_000L
    ): Result<R> {
        val runtime = runtimeResolver()
        if (!runtime.isSupported) {
            return Result.failure(ServiceWorkerUnsupportedException(backendName(runtime)))
        }
        return try {
            Result.success(request(payload, responseType, timeoutMs))
        } catch (t: TimeoutCancellationException) {
            Result.failure(t)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend inline fun <reified R : Any> tryRequest(
        payload: Any,
        timeoutMs: Long = 30_000L
    ): Result<R> = tryRequest(payload, R::class, timeoutMs)

    /**
     * Subscribes to messages of type [type] pushed from the active `@MainServiceWorker`.
     */
    fun <T : Any> onMessage(type: KClass<T>, handler: suspend (T) -> Unit): Disposable =
        registerMessageHandler(type = type, targetFragmentId = null, handler = handler)

    internal fun <T : Any> onMessageForFragment(
        fragmentId: String,
        type: KClass<T>,
        handler: suspend (T) -> Unit
    ): ServiceWorkerDisposableHandle =
        registerMessageHandler(type = type, targetFragmentId = fragmentId, handler = handler)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> registerMessageHandler(
        type: KClass<T>,
        targetFragmentId: String?,
        handler: suspend (T) -> Unit
    ): ServiceWorkerDisposableHandle {
        val entry = ChannelMessageHandler(
            typeId = ServiceWorkerCodecRegistry.resolveTypeId(type),
            payloadClass = type,
            targetFragmentId = targetFragmentId,
            handler = { decoded -> handler(decoded as T) }
        )
        withChannelLock {
            messageHandlers.add(entry)
        }
        return ServiceWorkerDisposableHandle {
            withChannelLock {
                messageHandlers.remove(entry)
            }
        }
    }

    inline fun <reified T : Any> onMessage(noinline handler: suspend (T) -> Unit): Disposable =
        onMessage(T::class, handler)

    /**
     * Registers a request handler allowing the `@MainServiceWorker` to issue requests to this UI client.
     */
    fun <T : Any> onRequest(
        type: KClass<T>,
        handler: suspend (T, ServiceWorkerReply) -> Unit
    ): Disposable = registerRequestHandler(type = type, targetFragmentId = null, handler = handler)

    internal fun <T : Any> onRequestForFragment(
        fragmentId: String,
        type: KClass<T>,
        handler: suspend (T, ServiceWorkerReply) -> Unit
    ): ServiceWorkerDisposableHandle =
        registerRequestHandler(type = type, targetFragmentId = fragmentId, handler = handler)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> registerRequestHandler(
        type: KClass<T>,
        targetFragmentId: String?,
        handler: suspend (T, ServiceWorkerReply) -> Unit
    ): ServiceWorkerDisposableHandle {
        val entry = ChannelRequestHandler(
            typeId = ServiceWorkerCodecRegistry.resolveTypeId(type),
            payloadClass = type,
            targetFragmentId = targetFragmentId,
            handler = { decoded, reply -> handler(decoded as T, reply) }
        )
        withChannelLock {
            requestHandlers.add(entry)
        }
        return ServiceWorkerDisposableHandle {
            withChannelLock {
                requestHandlers.remove(entry)
            }
        }
    }

    inline fun <reified T : Any> onRequest(
        noinline handler: suspend (T, ServiceWorkerReply) -> Unit
    ): Disposable = onRequest(T::class, handler)

    /**
     * Observes connection status changes (`true` = connected, `false` = disconnected).
     */
    fun onConnectionStateChanged(listener: (Boolean) -> Unit): Disposable =
        addConnectionListener(listener)

    /**
     * Registers a connection state listener (`true` = connected, `false` = disconnected).
     */
    fun addConnectionListener(listener: (Boolean) -> Unit): ServiceWorkerDisposableHandle {
        withChannelLock {
            connectionListeners.add(listener)
        }
        return ServiceWorkerDisposableHandle {
            withChannelLock {
                connectionListeners.remove(listener)
            }
        }
    }

    /**
     * Registers a callback invoked automatically whenever this channel reconnects after a prior
     * connection/disconnect cycle.
     */
    fun onReconnected(listener: suspend () -> Unit): ServiceWorkerDisposableHandle {
        withChannelLock {
            reconnectedListeners.add(listener)
        }
        return ServiceWorkerDisposableHandle {
            withChannelLock {
                reconnectedListeners.remove(listener)
            }
        }
    }

    internal fun reset() {
        failPendingRequests("ServiceWorkerChannel reset.")
        withChannelLock {
            messageHandlers.clear()
            requestHandlers.clear()
            connectionListeners.clear()
            reconnectedListeners.clear()
            lastKnownConnectedState = null
            hasConnectedOnce = false
            connectionEpoch = 0L
        }
    }

    private fun handleIncomingEnvelope(envelope: ServiceWorkerEnvelope) {
        when (envelope.kind) {
            ServiceWorkerEnvelope.KIND_RESPONSE,
            ServiceWorkerEnvelope.KIND_RESPONSE_ERR -> {
                val entry = withChannelLock {
                    val idx = pendingRequests.indexOfFirst { it.correlationId == envelope.correlationId }
                    if (idx < 0) null else pendingRequests.removeAt(idx)
                }
                entry?.deferred?.complete(envelope)
            }
            ServiceWorkerEnvelope.KIND_MESSAGE -> {
                if (envelope.typeId == ServiceWorkerEnvelope.CONTROL_OPEN_MAIN_WINDOW) {
                    val route = envelope.payloadJson.trim()
                        .removeSurrounding("\"")
                        .takeIf { it.isNotEmpty() && it != "null" }
                    GenericUiThreading.runOnUi {
                        val window = Application.current?.mainWindow
                        if (window != null) {
                            if (!window.isVisible) {
                                window.show()
                            }
                            if (window.getWindowState() == WindowState.MINIMIZED) {
                                window.restore()
                            }
                            window.bringToFront()
                            window.focus()
                        }
                        ServiceWorkerExtension.dispatchOpenMainWindowRequested(route)
                    }
                    return
                }
                if (envelope.typeId == ServiceWorkerEnvelope.CONTROL_CLOSE_MAIN_WINDOW) {
                    GenericUiThreading.runOnUi {
                        Application.current?.mainWindow?.close()
                    }
                    return
                }
                if (envelope.typeId == ServiceWorkerEnvelope.CONTROL_EXIT_APP) {
                    GenericUiThreading.runOnUi {
                        Application.current?.exit(0)
                    }
                    return
                }
                if (envelope.typeId == ServiceWorkerEnvelope.CONTROL_LAUNCH_FRAGMENT) {
                    val req = ServiceWorkerFragmentLaunchRequest.fromControlPayloadJson(envelope.payloadJson)
                    if (req != null) {
                        GenericUiThreading.runOnUi {
                            ServiceWorkerUiWindowCoordinator.handleLaunchFragment(req)
                        }
                    }
                    return
                }
                if (envelope.typeId == ServiceWorkerEnvelope.CONTROL_CLOSE_FRAGMENT) {
                    val closeTarget = ServiceWorkerFragmentLaunchRequest.decodeCloseControlPayload(envelope.payloadJson)
                    if (closeTarget != null) {
                        GenericUiThreading.runOnUi {
                            ServiceWorkerUiWindowCoordinator.handleCloseFragment(
                                fragmentId = closeTarget.first,
                                instanceId = closeTarget.second
                            )
                        }
                    }
                    return
                }
                val matching = withChannelLock {
                    messageHandlers.filter { handler ->
                        val typeMatches = handler.typeId == envelope.typeId ||
                            ServiceWorkerCodecRegistry.resolveTypeId(handler.payloadClass) == envelope.typeId
                        val fragmentMatches = handler.targetFragmentId == null ||
                            envelope.targetFragmentId == null ||
                            handler.targetFragmentId == envelope.targetFragmentId
                        typeMatches && fragmentMatches
                    }
                }
                if (matching.isNotEmpty()) {
                    inboundMessageQueue.trySend {
                        for (entry in matching) {
                            val decoded = ServiceWorkerCodecRegistry.decodeAs(
                                envelope.typeId,
                                envelope.payloadJson,
                                entry.payloadClass
                            )
                            entry.handler(decoded)
                        }
                    }
                }
            }
            ServiceWorkerEnvelope.KIND_REQUEST -> {
                val entry = withChannelLock {
                    val typeMatching = requestHandlers.filter { handler ->
                        handler.typeId == envelope.typeId ||
                            ServiceWorkerCodecRegistry.resolveTypeId(handler.payloadClass) == envelope.typeId
                    }
                    if (envelope.targetFragmentId != null) {
                        typeMatching.firstOrNull { it.targetFragmentId == envelope.targetFragmentId }
                            ?: typeMatching.firstOrNull { it.targetFragmentId == null }
                    } else {
                        typeMatching.firstOrNull()
                    }
                }
                val runtime = runtimeResolver()
                val reply = ServiceWorkerReply(
                    sendSuccess = { responsePayload ->
                        scope.launch {
                            val (respType, respJson) = ServiceWorkerCodecRegistry.encodePayload(responsePayload)
                            runtime.sendClientEnvelope(
                                ServiceWorkerEnvelope(
                                    kind = ServiceWorkerEnvelope.KIND_RESPONSE,
                                    correlationId = envelope.correlationId,
                                    clientId = envelope.clientId,
                                    typeId = respType,
                                    payloadJson = respJson,
                                    targetFragmentId = envelope.sourceFragmentId,
                                    sourceFragmentId = envelope.targetFragmentId
                                )
                            )
                        }
                    },
                    sendFailure = { err ->
                        scope.launch {
                            runtime.sendClientEnvelope(
                                ServiceWorkerEnvelope(
                                    kind = ServiceWorkerEnvelope.KIND_RESPONSE_ERR,
                                    correlationId = envelope.correlationId,
                                    clientId = envelope.clientId,
                                    error = err,
                                    targetFragmentId = envelope.sourceFragmentId,
                                    sourceFragmentId = envelope.targetFragmentId
                                )
                            )
                        }
                    }
                )
                if (entry == null) {
                    reply.fail("No client request handler registered for '${envelope.typeId}'.")
                    return
                }
                scope.launch {
                    try {
                        val decoded = ServiceWorkerCodecRegistry.decodeAs(
                            envelope.typeId,
                            envelope.payloadJson,
                            entry.payloadClass
                        )
                        entry.handler(decoded, reply)
                    } catch (t: Throwable) {
                        if (!reply.isCompleted) {
                            reply.fail(t.message ?: t::class.simpleName ?: "Client handler error")
                        }
                    }
                }
            }
            ServiceWorkerEnvelope.KIND_BYE -> {
                failPendingRequests("ServiceWorker shut down.")
                notifyConnectionChanged(false)
            }
        }
    }

    private fun notifyConnectionChanged(connected: Boolean) {
        val stateSnapshot = withChannelLock {
            val prev = lastKnownConnectedState
            if (prev == connected) {
                return@withChannelLock null
            }
            val wasEverConnected = hasConnectedOnce
            lastKnownConnectedState = connected
            if (connected) {
                connectionEpoch += 1L
                hasConnectedOnce = true
            }
            val shouldFireReconnect = connected && wasEverConnected && prev == false
            Triple(
                connectionListeners.toList(),
                if (shouldFireReconnect) reconnectedListeners.toList() else emptyList(),
                connected
            )
        } ?: return

        val (listeners, reconnects, state) = stateSnapshot
        for (listener in listeners) {
            listener(state)
        }
        if (reconnects.isNotEmpty()) {
            scope.launch {
                for (listener in reconnects) {
                    listener()
                }
            }
        }
    }

    private fun backendName(runtime: ServiceWorkerPlatformRuntime): String =
        runtime.currentBackendTarget()?.displayName ?: "Unknown"

    private fun failPendingRequests(reason: String) {
        val copy = withChannelLock {
            val snapshot = pendingRequests.toList()
            pendingRequests.clear()
            snapshot
        }
        for (entry in copy) {
            entry.deferred.complete(
                ServiceWorkerEnvelope(
                    kind = ServiceWorkerEnvelope.KIND_RESPONSE_ERR,
                    correlationId = entry.correlationId,
                    error = reason
                )
            )
        }
    }

    private inline fun <T> withChannelLock(block: () -> T): T {
        while (!stateLock.compareAndSet(expectedValue = false, newValue = true)) {
            // Brief spin around in-memory handler/pending lists.
        }
        return try {
            block()
        } finally {
            stateLock.store(false)
        }
    }
}
