package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.threading.GenericUiThreading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.reflect.KClass

/**
 * Client-side typed IPC communication channel between UI components (`@MainAppFragment`, views,
 * controllers) and the active `@MainServiceWorker`.
 *
 * On iOS / iPadOS, [isSupported] is `false` and [send] / [request] fail fast with
 * [UnsupportedOperationException] (or `Result.failure(...)` from [tryRequest]).
 */
class ServiceWorkerChannel internal constructor(
    private val runtimeResolver: () -> ServiceWorkerPlatformRuntime,
    private val optionsResolver: () -> ServiceWorkerOptions
) {
    private class ChannelMessageHandler(
        val typeId: String,
        val payloadClass: KClass<*>,
        val handler: suspend (Any?) -> Unit
    )

    private class ChannelRequestHandler(
        val typeId: String,
        val payloadClass: KClass<*>,
        val handler: suspend (Any?, ServiceWorkerReply) -> Unit
    )

    private class PendingChannelRequest(
        val correlationId: Long,
        val deferred: CompletableDeferred<ServiceWorkerEnvelope>
    )

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val messageHandlers = mutableListOf<ChannelMessageHandler>()
    private val requestHandlers = mutableListOf<ChannelRequestHandler>()
    private val connectionListeners = mutableListOf<(Boolean) -> Unit>()
    private val pendingRequests = mutableListOf<PendingChannelRequest>()
    private var nextCorrelationId: Long = 1L
    private var lastClientId: String = "client-main"

    val isSupported: Boolean
        get() = runtimeResolver().isSupported

    val isConnected: Boolean
        get() = runtimeResolver().isClientConnected()

    /**
     * Connects to the background Service Worker IPC endpoint (or in-process binder / Web Worker).
     * Returns `false` cleanly on unsupported platforms (iOS / iPadOS).
     */
    suspend fun connect(): Boolean {
        val runtime = runtimeResolver()
        if (!runtime.isSupported) {
            return false
        }
        return runtime.connectClient(
            options = optionsResolver(),
            onIncomingEnvelope = ::handleIncomingEnvelope,
            onConnectionChanged = ::notifyConnectionChanged
        )
    }

    /**
     * Disconnects this client channel cleanly without stopping the background Service Worker.
     */
    suspend fun disconnect() {
        failPendingRequests("ServiceWorkerChannel disconnected.")
        runtimeResolver().disconnectClient()
        notifyConnectionChanged(false)
    }

    /**
     * Sends a one-way typed [message] to the active `@MainServiceWorker`.
     */
    suspend fun send(message: Any) {
        val runtime = runtimeResolver()
        if (!runtime.isSupported) {
            throw UnsupportedOperationException(
                "Background ServiceWorker is not supported on iOS/iPadOS."
            )
        }
        if (!runtime.isClientConnected()) {
            val connected = connect()
            if (!connected) {
                throw IllegalStateException("Unable to connect ServiceWorkerChannel to background ServiceWorker.")
            }
        }
        val (typeId, payloadJson) = ServiceWorkerCodecRegistry.encodePayload(message)
        val env = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_MESSAGE,
            correlationId = 0L,
            clientId = lastClientId,
            typeId = typeId,
            payloadJson = payloadJson
        )
        if (!runtime.sendClientEnvelope(env)) {
            throw IllegalStateException("Failed to send message of type '$typeId' to ServiceWorker.")
        }
    }

    /**
     * Sends a typed [payload] request to the active `@MainServiceWorker` and awaits a response of [responseType].
     */
    suspend fun <R : Any> request(
        payload: Any,
        responseType: KClass<R>,
        timeoutMs: Long = 30_000L
    ): R {
        val runtime = runtimeResolver()
        if (!runtime.isSupported) {
            throw UnsupportedOperationException(
                "Background ServiceWorker is not supported on iOS/iPadOS."
            )
        }
        if (!runtime.isClientConnected()) {
            val connected = connect()
            if (!connected) {
                throw IllegalStateException("Unable to connect ServiceWorkerChannel to background ServiceWorker.")
            }
        }
        val (typeId, payloadJson) = ServiceWorkerCodecRegistry.encodePayload(payload)
        val corrId = nextCorrelationId++
        val deferred = CompletableDeferred<ServiceWorkerEnvelope>()
        val entry = PendingChannelRequest(corrId, deferred)
        pendingRequests.add(entry)
        try {
            val env = ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_REQUEST,
                correlationId = corrId,
                clientId = lastClientId,
                typeId = typeId,
                payloadJson = payloadJson
            )
            if (!runtime.sendClientEnvelope(env)) {
                throw IllegalStateException("Failed to send request '$typeId' to ServiceWorker.")
            }
            val response = withTimeout(timeoutMs) { deferred.await() }
            if (response.kind == ServiceWorkerEnvelope.KIND_RESPONSE_ERR) {
                throw IllegalStateException(
                    response.error.ifEmpty { "ServiceWorker rejected request '$typeId'." }
                )
            }
            return ServiceWorkerCodecRegistry.decodeAs(response.typeId, response.payloadJson, responseType)
        } catch (t: TimeoutCancellationException) {
            throw IllegalStateException("Timed out waiting for ServiceWorker response for '$typeId'.", t)
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            pendingRequests.remove(entry)
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
            return Result.failure(
                UnsupportedOperationException("Background ServiceWorker is not supported on iOS/iPadOS.")
            )
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
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> onMessage(type: KClass<T>, handler: suspend (T) -> Unit): Disposable {
        val entry = ChannelMessageHandler(
            typeId = ServiceWorkerCodecRegistry.resolveTypeId(type),
            payloadClass = type,
            handler = { decoded -> handler(decoded as T) }
        )
        messageHandlers.add(entry)
        return ServiceWorkerDisposableHandle { messageHandlers.remove(entry) }
    }

    inline fun <reified T : Any> onMessage(noinline handler: suspend (T) -> Unit): Disposable =
        onMessage(T::class, handler)

    /**
     * Registers a request handler allowing the `@MainServiceWorker` to issue requests to this UI client.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> onRequest(
        type: KClass<T>,
        handler: suspend (T, ServiceWorkerReply) -> Unit
    ): Disposable {
        val entry = ChannelRequestHandler(
            typeId = ServiceWorkerCodecRegistry.resolveTypeId(type),
            payloadClass = type,
            handler = { decoded, reply -> handler(decoded as T, reply) }
        )
        requestHandlers.add(entry)
        return ServiceWorkerDisposableHandle { requestHandlers.remove(entry) }
    }

    inline fun <reified T : Any> onRequest(
        noinline handler: suspend (T, ServiceWorkerReply) -> Unit
    ): Disposable = onRequest(T::class, handler)

    /**
     * Observes connection status changes (`true` = connected, `false` = disconnected).
     */
    fun onConnectionStateChanged(listener: (Boolean) -> Unit): Disposable {
        connectionListeners.add(listener)
        return ServiceWorkerDisposableHandle { connectionListeners.remove(listener) }
    }

    internal fun reset() {
        failPendingRequests("ServiceWorkerChannel reset.")
        messageHandlers.clear()
        requestHandlers.clear()
        connectionListeners.clear()
    }

    private fun handleIncomingEnvelope(envelope: ServiceWorkerEnvelope) {
        when (envelope.kind) {
            ServiceWorkerEnvelope.KIND_RESPONSE,
            ServiceWorkerEnvelope.KIND_RESPONSE_ERR -> {
                val entry = pendingRequests.firstOrNull { it.correlationId == envelope.correlationId }
                if (entry != null) {
                    pendingRequests.remove(entry)
                    entry.deferred.complete(envelope)
                }
            }
            ServiceWorkerEnvelope.KIND_MESSAGE -> {
                if (envelope.typeId == "__genericui.sw.control.closeMainWindow") {
                    GenericUiThreading.runOnUi {
                        Application.current?.mainWindow?.close()
                    }
                    return
                }
                if (envelope.typeId == "__genericui.sw.control.exitApp") {
                    GenericUiThreading.runOnUi {
                        Application.current?.exit(0)
                    }
                    return
                }
                val matching = messageHandlers.filter {
                    it.typeId == envelope.typeId ||
                        ServiceWorkerCodecRegistry.resolveTypeId(it.payloadClass) == envelope.typeId
                }
                if (matching.isNotEmpty()) {
                    scope.launch {
                        for (entry in matching) {
                            runCatching {
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
            }
            ServiceWorkerEnvelope.KIND_REQUEST -> {
                val entry = requestHandlers.firstOrNull {
                    it.typeId == envelope.typeId ||
                        ServiceWorkerCodecRegistry.resolveTypeId(it.payloadClass) == envelope.typeId
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
                                    payloadJson = respJson
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
                                    error = err
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
        for (listener in connectionListeners.toList()) {
            runCatching { listener(connected) }
        }
    }

    private fun failPendingRequests(reason: String) {
        val copy = pendingRequests.toList()
        pendingRequests.clear()
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
}
