@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.reflect.KClass

/**
 * Handle representing an individual UI window, Activity, or browser tab connected to a running
 * [ServiceWorker].
 */
class ServiceWorkerClient internal constructor(
    val clientId: String,
    private val host: ServiceWorkerRuntimeHost
) {
    private class PendingWorkerRequest(
        val correlationId: Long,
        val deferred: CompletableDeferred<ServiceWorkerEnvelope>
    )

    private val pendingLock = AtomicBoolean(false)
    private val pendingRequests = mutableListOf<PendingWorkerRequest>()
    private val nextCorrelationSeq = AtomicLong(1_000_000L)

    @Volatile
    var isConnected: Boolean = true
        internal set

    /**
     * Pushes an asynchronous message [message] from the [ServiceWorker] to this client.
     */
    suspend fun send(message: Any) {
        sendInternal(message = message, targetFragmentId = null, sourceFragmentId = null)
    }

    /**
     * Sends a request [payload] to this client and suspends for a response of [responseType].
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

    suspend inline fun <reified R : Any> request(
        payload: Any,
        timeoutMs: Long = 30_000L
    ): R = request(payload, R::class, timeoutMs)

    internal suspend fun sendInternal(
        message: Any,
        targetFragmentId: String? = null,
        sourceFragmentId: String? = null
    ) {
        if (!isConnected) {
            throw ServiceWorkerIpcException("ServiceWorkerClient '$clientId' is disconnected.")
        }
        val (typeId, payload) = ServiceWorkerCodecRegistry.encodePayload(message)
        val env = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_MESSAGE,
            correlationId = 0L,
            clientId = clientId,
            typeId = typeId,
            payload = payload,
            targetFragmentId = targetFragmentId,
            sourceFragmentId = sourceFragmentId
        )
        if (!host.sendToClient(clientId, env)) {
            throw ServiceWorkerIpcException("Failed to send message to client '$clientId'.")
        }
    }

    internal suspend fun <R : Any> requestInternal(
        payload: Any,
        responseType: KClass<R>,
        timeoutMs: Long = 30_000L,
        targetFragmentId: String? = null,
        sourceFragmentId: String? = null
    ): R {
        if (!isConnected) {
            throw ServiceWorkerIpcException("ServiceWorkerClient '$clientId' is disconnected.")
        }
        val (typeId, encodedPayload) = ServiceWorkerCodecRegistry.encodePayload(payload)
        val corrId = allocateCorrelationId()
        val deferred = CompletableDeferred<ServiceWorkerEnvelope>()
        if (!registerPendingDeferred(corrId, deferred)) {
            throw ServiceWorkerIpcException("ServiceWorkerClient '$clientId' is disconnected.")
        }
        try {
            val env = ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_REQUEST,
                correlationId = corrId,
                clientId = clientId,
                typeId = typeId,
                payload = encodedPayload,
                targetFragmentId = targetFragmentId,
                sourceFragmentId = sourceFragmentId
            )
            if (!host.sendToClient(clientId, env)) {
                throw ServiceWorkerIpcException("Failed to deliver request to client '$clientId'.")
            }
            val reply = withTimeout(timeoutMs) { deferred.await() }
            if (reply.kind == ServiceWorkerEnvelope.KIND_RESPONSE_ERR) {
                throw ServiceWorkerIpcException(reply.error.ifEmpty { "Client request failed." })
            }
            return ServiceWorkerCodecRegistry.decodeAs(reply.typeId, reply.payload, responseType)
        } catch (t: TimeoutCancellationException) {
            throw ServiceWorkerIpcException("Timed out waiting for response from client '$clientId'.", t)
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            removePendingDeferred(corrId)
        }
    }

    internal suspend fun sendControlCloseFragment(fragmentId: String, instanceId: String? = null): Boolean {
        if (!isConnected) return false
        val env = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_MESSAGE,
            correlationId = 0L,
            clientId = clientId,
            typeId = ServiceWorkerEnvelope.CONTROL_CLOSE_FRAGMENT,
            payload = ServiceWorkerFragmentLaunchRequest.encodeCloseControlPayload(fragmentId, instanceId),
            targetFragmentId = fragmentId
        )
        return host.sendToClient(clientId, env)
    }

    internal fun allocateCorrelationId(): Long = nextCorrelationSeq.fetchAndAdd(1L)

    internal fun registerPendingDeferred(
        correlationId: Long,
        deferred: CompletableDeferred<ServiceWorkerEnvelope>
    ): Boolean = withPendingLock {
        if (!isConnected) return@withPendingLock false
        pendingRequests.add(PendingWorkerRequest(correlationId, deferred))
        true
    }

    internal fun removePendingDeferred(correlationId: Long) {
        withPendingLock {
            pendingRequests.removeAll { it.correlationId == correlationId }
        }
    }

    internal fun completeResponse(envelope: ServiceWorkerEnvelope): Boolean {
        val entry = withPendingLock {
            val idx = pendingRequests.indexOfFirst { it.correlationId == envelope.correlationId }
            if (idx < 0) null else pendingRequests.removeAt(idx)
        } ?: return false
        entry.deferred.complete(envelope)
        return true
    }

    internal fun failAllPending(reason: String) {
        val copy = withPendingLock {
            isConnected = false
            val snapshot = pendingRequests.toList()
            pendingRequests.clear()
            snapshot
        }
        for (entry in copy) {
            entry.deferred.complete(
                ServiceWorkerEnvelope(
                    kind = ServiceWorkerEnvelope.KIND_RESPONSE_ERR,
                    correlationId = entry.correlationId,
                    clientId = clientId,
                    error = reason
                )
            )
        }
    }

    /**
     * Closes this client's IPC session cleanly.
     */
    fun disconnect() {
        failAllPending("Disconnected")
        host.disconnectClient(clientId)
    }

    private inline fun <T> withPendingLock(block: () -> T): T {
        while (!pendingLock.compareAndSet(expectedValue = false, newValue = true)) {
            // Brief spin around in-memory pending request updates.
        }
        return try {
            block()
        } finally {
            pendingLock.store(false)
        }
    }
}
