package com.nexustools.genericui.ext.serviceworker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
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

    private val pendingRequests = mutableListOf<PendingWorkerRequest>()
    private var nextCorrelationId: Long = 1_000_000L

    @Volatile
    var isConnected: Boolean = true
        internal set

    /**
     * Pushes an asynchronous message [message] from the [ServiceWorker] to this client.
     */
    suspend fun send(message: Any) {
        if (!isConnected) {
            throw IllegalStateException("ServiceWorkerClient '$clientId' is disconnected.")
        }
        val (typeId, payloadJson) = ServiceWorkerCodecRegistry.encodePayload(message)
        val env = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_MESSAGE,
            correlationId = 0L,
            clientId = clientId,
            typeId = typeId,
            payloadJson = payloadJson
        )
        if (!host.sendToClient(clientId, env)) {
            throw IllegalStateException("Failed to send message to client '$clientId'.")
        }
    }

    /**
     * Sends a request [payload] to this client and suspends for a response of [responseType].
     */
    suspend fun <R : Any> request(
        payload: Any,
        responseType: KClass<R>,
        timeoutMs: Long = 30_000L
    ): R {
        if (!isConnected) {
            throw IllegalStateException("ServiceWorkerClient '$clientId' is disconnected.")
        }
        val (typeId, payloadJson) = ServiceWorkerCodecRegistry.encodePayload(payload)
        val corrId = nextCorrelationId++
        val deferred = CompletableDeferred<ServiceWorkerEnvelope>()
        val entry = PendingWorkerRequest(corrId, deferred)
        pendingRequests.add(entry)
        try {
            val env = ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_REQUEST,
                correlationId = corrId,
                clientId = clientId,
                typeId = typeId,
                payloadJson = payloadJson
            )
            if (!host.sendToClient(clientId, env)) {
                throw IllegalStateException("Failed to deliver request to client '$clientId'.")
            }
            val reply = withTimeout(timeoutMs) { deferred.await() }
            if (reply.kind == ServiceWorkerEnvelope.KIND_RESPONSE_ERR) {
                throw IllegalStateException(reply.error.ifEmpty { "Client request failed." })
            }
            return ServiceWorkerCodecRegistry.decodeAs(reply.typeId, reply.payloadJson, responseType)
        } catch (t: TimeoutCancellationException) {
            throw IllegalStateException("Timed out waiting for response from client '$clientId'.", t)
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

    internal fun completeResponse(envelope: ServiceWorkerEnvelope): Boolean {
        val entry = pendingRequests.firstOrNull { it.correlationId == envelope.correlationId } ?: return false
        pendingRequests.remove(entry)
        entry.deferred.complete(envelope)
        return true
    }

    internal fun failAllPending(reason: String) {
        isConnected = false
        val copy = pendingRequests.toList()
        pendingRequests.clear()
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
}
