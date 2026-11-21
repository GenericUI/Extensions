package com.nexustools.genericui.ext.serviceworker

import kotlin.concurrent.Volatile

/**
 * Handle passed to `ServiceWorker.onRequest` callbacks for sending a single response or error back
 * to the requesting client.
 */
class ServiceWorkerReply internal constructor(
    private val sendSuccess: (Any?) -> Unit,
    private val sendFailure: (String) -> Unit
) {
    @Volatile
    private var completed: Boolean = false

    val isCompleted: Boolean
        get() = completed

    /**
     * Completes the pending client request with [payload] (which may be `Unit` or `null` for empty responses).
     */
    fun send(payload: Any? = Unit) {
        if (completed) {
            throw IllegalStateException("ServiceWorkerReply has already been completed.")
        }
        completed = true
        sendSuccess(payload)
    }

    /**
     * Fails the pending client request with an error message propagated back to the caller.
     */
    fun fail(reason: String) {
        if (completed) {
            throw IllegalStateException("ServiceWorkerReply has already been completed.")
        }
        completed = true
        sendFailure(reason)
    }
}
