package com.nexustools.genericui.ext.serviceworker

import kotlin.reflect.KClass

/**
 * Worker-side handle representing a live mounted [com.nexustools.genericui.component.fragment.Fragment]
 * instance on a connected UI [ServiceWorkerClient].
 */
class ServiceWorkerFragmentHandle internal constructor(
    val client: ServiceWorkerClient,
    val fragmentId: String,
    val instanceId: String,
    val route: String?
) {
    /**
     * Sends a one-way typed [message] targeted specifically at [fragmentId] on [client].
     */
    suspend fun <T : Any> send(message: T) {
        client.sendInternal(
            message = message,
            targetFragmentId = fragmentId
        )
    }

    /**
     * Sends a typed [payload] request targeted specifically at [fragmentId] on [client] and
     * awaits a response of [responseClass].
     */
    suspend fun <Req : Any, Res : Any> request(
        payload: Req,
        responseClass: KClass<Res>,
        timeoutMs: Long = 10_000L
    ): Res = client.requestInternal(
        payload = payload,
        responseType = responseClass,
        timeoutMs = timeoutMs,
        targetFragmentId = fragmentId
    )

    suspend inline fun <reified Res : Any> request(
        payload: Any,
        timeoutMs: Long = 10_000L
    ): Res = request(payload, Res::class, timeoutMs)

    /**
     * Requests closing this mounted fragment instance on [client].
     */
    suspend fun close() {
        client.sendControlCloseFragment(
            fragmentId = fragmentId,
            instanceId = instanceId
        )
    }
}
