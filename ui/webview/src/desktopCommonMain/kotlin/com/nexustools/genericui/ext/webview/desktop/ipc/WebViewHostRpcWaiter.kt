package com.nexustools.genericui.ext.webview.desktop.ipc

import com.nexustools.genericui.ext.ipc.IpcPacket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Synchronization waiter associating a pending outbound [IpcPacket] request ID with its reply.
 */
internal class WebViewHostRpcWaiter(
    val requestId: Long
) {
    private val deferred = CompletableDeferred<IpcPacket>()

    fun complete(packet: IpcPacket) {
        deferred.complete(packet)
    }

    fun fail(error: Throwable) {
        deferred.completeExceptionally(error)
    }

    fun awaitBlocking(timeoutMs: Long): IpcPacket? = runBlocking {
        awaitSuspend(timeoutMs)
    }

    suspend fun awaitSuspend(timeoutMs: Long): IpcPacket? =
        withTimeoutOrNull(timeoutMs) {
            deferred.await()
        }
}
