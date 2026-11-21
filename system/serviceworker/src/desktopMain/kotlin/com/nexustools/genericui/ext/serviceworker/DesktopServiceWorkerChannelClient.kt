package com.nexustools.genericui.ext.serviceworker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * UI-client-side authenticated loopback TCP client connecting to [DesktopServiceWorkerHost].
 */
internal class DesktopServiceWorkerChannelClient {

    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var connection: DesktopTcpConnection? = null

    @Volatile
    private var connected: Boolean = false

    private var onEnvelopeListener: ((ServiceWorkerEnvelope) -> Unit)? = null
    private var onConnectionChanged: ((Boolean) -> Unit)? = null
    private var assignedClientId: String = "desktop-client-${DesktopOsHelpers.currentProcessId()}"

    val isConnected: Boolean
        get() = connected && connection?.isClosed == false

    fun connectToEndpoint(
        descriptor: DesktopEndpointDescriptor,
        onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
        onConnectionStateChanged: (Boolean) -> Unit
    ): Boolean {
        disconnect()
        onEnvelopeListener = onIncomingEnvelope
        onConnectionChanged = onConnectionStateChanged
        if (!scope.coroutineContext[kotlinx.coroutines.Job]!!.isActive) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }

        val conn = DesktopLoopbackTcpClient.connect(descriptor.port, timeoutMs = 4_000) ?: return false
        val authEnv = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_AUTH,
            clientId = assignedClientId,
            payloadJson = descriptor.token
        )
        if (!conn.sendBody(ServiceWorkerFrameCodec.encodeBinaryBody(authEnv))) {
            conn.close()
            return false
        }
        val replyBytes = conn.receiveBody(timeoutMs = 3_000)
        val replyEnv = replyBytes?.let { ServiceWorkerFrameCodec.decodeBinaryBody(it) }
        if (replyEnv == null || replyEnv.kind != ServiceWorkerEnvelope.KIND_AUTH_OK) {
            conn.close()
            return false
        }

        assignedClientId = replyEnv.clientId.ifBlank { assignedClientId }
        connection = conn
        connected = true
        onConnectionStateChanged(true)

        scope.launch {
            try {
                while (connected && !conn.isClosed) {
                    val body = conn.receiveBody(timeoutMs = 250) ?: continue
                    val env = ServiceWorkerFrameCodec.decodeBinaryBody(body) ?: continue
                    onEnvelopeListener?.invoke(env)
                    if (env.kind == ServiceWorkerEnvelope.KIND_BYE) {
                        break
                    }
                }
            } finally {
                connected = false
                if (connection === conn) {
                    connection = null
                }
                conn.close()
                onConnectionChanged?.invoke(false)
            }
        }
        return true
    }

    fun sendEnvelope(envelope: ServiceWorkerEnvelope): Boolean {
        val conn = connection ?: return false
        if (!connected || conn.isClosed) return false
        val withCid = if (envelope.clientId.isEmpty()) {
            envelope.copy(clientId = assignedClientId)
        } else {
            envelope
        }
        return conn.sendBody(ServiceWorkerFrameCodec.encodeBinaryBody(withCid))
    }

    fun disconnect() {
        val conn = connection
        connected = false
        connection = null
        if (conn != null && !conn.isClosed) {
            runCatching {
                conn.sendBody(
                    ServiceWorkerFrameCodec.encodeBinaryBody(
                        ServiceWorkerEnvelope(kind = ServiceWorkerEnvelope.KIND_BYE, clientId = assignedClientId)
                    )
                )
            }
            runCatching { conn.close() }
        }
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        onConnectionChanged?.invoke(false)
    }
}
