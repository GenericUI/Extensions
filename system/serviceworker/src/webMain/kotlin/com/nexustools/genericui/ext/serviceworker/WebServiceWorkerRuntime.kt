package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.web.WebServiceWorkerClientBridge
import com.nexustools.genericui.web.WebWorkerGlobalBridge

/**
 * Web (`js` / `wasmJs`) [ServiceWorkerPlatformRuntime] handling both:
 * 1. Service Worker / Dedicated Worker global scope (`WebWorkerGlobalBridge.isWorkerGlobalScope`),
 *    instantiating `@MainServiceWorker` and handling incoming `postMessage` frames.
 * 2. Browser window / DOM client scope (`WebServiceWorkerClientBridge`), connecting
 *    `ServiceWorkerExtension.channel` to `./serviceworker.js` and forwarding frames.
 */
internal class WebServiceWorkerRuntime : ServiceWorkerPlatformRuntime, ServiceWorkerRuntimeHost {

    override val isSupported: Boolean = true

    override val isAutostartSupported: Boolean = false

    private var currentOptions: ServiceWorkerOptions = ServiceWorkerOptions()
    private var runningWorker: ServiceWorker? = null
    private var clientConnected: Boolean = false
    private var incomingClientListener: ((ServiceWorkerEnvelope) -> Unit)? = null
    private var connectionStateListener: ((Boolean) -> Unit)? = null
    private var domListener: ((String) -> Unit)? = null
    private var workerBridgeListener: ((String) -> Unit)? = null

    private val browserClientId: String = "web-client"

    override fun configure(options: ServiceWorkerOptions) {
        currentOptions = options
    }

    override fun isClientConnected(): Boolean = clientConnected

    override fun activeWorker(): ServiceWorker? = runningWorker

    override fun currentBackendTarget(): BackendTarget = BackendTarget.Web

    override suspend fun connectClient(
        options: ServiceWorkerOptions,
        onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
        onConnectionChanged: (Boolean) -> Unit
    ): Boolean {
        currentOptions = options
        incomingClientListener = onIncomingEnvelope
        connectionStateListener = onConnectionChanged

        if (WebWorkerGlobalBridge.isWorkerGlobalScope) {
            ensureBackgroundStarted(options, ServiceWorkerExtension.resolvedWorkerFactory())
            clientConnected = runningWorker != null
            onConnectionChanged(clientConnected)
            return clientConnected
        }

        if (WebServiceWorkerClientBridge.isServiceWorkerSupported) {
            installDomMessageListener()
            if (!WebServiceWorkerClientBridge.isControlled()) {
                WebServiceWorkerClientBridge.register("./serviceworker.js") { ok, _ ->
                    if (!ok && runningWorker == null) {
                        clientConnected = false
                        onConnectionChanged(false)
                    }
                }
            }
            clientConnected = true
            onConnectionChanged(true)
            return true
        }

        // Headless non-browser JS context (such as Node.js tests): allow explicit test-registered worker fallback.
        if (runningWorker != null || ServiceWorkerExtension.resolvedWorkerFactory() != null) {
            ensureBackgroundStarted(options, ServiceWorkerExtension.resolvedWorkerFactory())
            runningWorker?.dispatchClientConnected(browserClientId)
            clientConnected = runningWorker != null
            onConnectionChanged(clientConnected)
            return clientConnected
        }

        clientConnected = false
        onConnectionChanged(false)
        return false
    }

    override suspend fun disconnectClient() {
        domListener?.let { WebServiceWorkerClientBridge.removeMessageListener(it) }
        domListener = null
        runningWorker?.dispatchClientDisconnected(browserClientId)
        clientConnected = false
        connectionStateListener?.invoke(false)
    }

    override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean {
        if (WebWorkerGlobalBridge.isWorkerGlobalScope || runningWorker != null && !WebServiceWorkerClientBridge.isServiceWorkerSupported) {
            val worker = runningWorker ?: return false
            worker.dispatchIncomingEnvelope(browserClientId, envelope)
            return true
        }
        if (!WebServiceWorkerClientBridge.isServiceWorkerSupported) {
            return false
        }
        val wireText = ServiceWorkerFrameCodec.encodeWebText(envelope)
        return WebServiceWorkerClientBridge.postMessage(wireText)
    }

    override fun ensureBackgroundStarted(
        options: ServiceWorkerOptions,
        workerFactory: (() -> ServiceWorker)?
    ): Boolean {
        currentOptions = options
        if (runningWorker != null) return true
        val shouldStartHere = WebWorkerGlobalBridge.isWorkerGlobalScope ||
            (!WebServiceWorkerClientBridge.isServiceWorkerSupported && workerFactory != null)
        if (!shouldStartHere) {
            return true
        }
        val factory = workerFactory ?: return false
        val worker = factory()
        runningWorker = worker
        installWorkerGlobalMessageHandler(worker)
        worker.attachAndStart(this, options)
        return true
    }

    override fun updateNotification(
        title: String,
        text: String,
        subText: String?,
        icon: ResourceID?,
        actions: List<ServiceWorkerNotificationAction>
    ) {
        // Ongoing foreground service notifications are Android-specific; no-op on Web.
    }

    override fun updateTray(
        iconPathOrUrl: String?,
        iconResource: ResourceID?,
        tooltip: String?,
        badgeText: String?,
        menu: ContextMenu?,
        onClick: (() -> Unit)?
    ) {
        // Desktop tray icon is unsupported inside browser Service Workers; no-op on Web.
    }

    override fun openMainWindow(route: String?) {
        // Broadcast navigation request to clients if running inside worker global scope.
    }

    override fun closeMainWindow() {}

    override fun stopSelf() {
        workerBridgeListener?.let { WebWorkerGlobalBridge.removeIncomingMessageHandler(it) }
        workerBridgeListener = null
        runningWorker = null
    }

    override fun exitApplication(exitCode: Int) {
        stopSelf()
    }

    override fun isAutostartEnabled(): Boolean = false

    override fun setAutostartEnabled(enabled: Boolean): Boolean = false

    override suspend fun sendToClient(clientId: String, envelope: ServiceWorkerEnvelope): Boolean {
        if (WebWorkerGlobalBridge.isWorkerGlobalScope) {
            return WebWorkerGlobalBridge.broadcastToClients(ServiceWorkerFrameCodec.encodeWebText(envelope))
        }
        incomingClientListener?.invoke(envelope)
        return true
    }

    override suspend fun broadcastEnvelope(envelope: ServiceWorkerEnvelope): Int {
        if (WebWorkerGlobalBridge.isWorkerGlobalScope) {
            val ok = WebWorkerGlobalBridge.broadcastToClients(ServiceWorkerFrameCodec.encodeWebText(envelope))
            return if (ok) 1 else 0
        }
        val listener = incomingClientListener ?: return 0
        listener(envelope)
        return 1
    }

    override fun disconnectClient(clientId: String) {
        runningWorker?.dispatchClientDisconnected(clientId)
    }

    override fun resetForTests() {
        domListener?.let { WebServiceWorkerClientBridge.removeMessageListener(it) }
        domListener = null
        workerBridgeListener?.let { WebWorkerGlobalBridge.removeIncomingMessageHandler(it) }
        workerBridgeListener = null
        runningWorker?.performStop()
        runningWorker = null
        clientConnected = false
        incomingClientListener = null
        connectionStateListener = null
        currentOptions = ServiceWorkerOptions()
    }

    private fun installDomMessageListener() {
        if (domListener != null) return
        val listener: (String) -> Unit = { raw ->
            val decoded = ServiceWorkerFrameCodec.decodeWebText(raw)
            if (decoded != null) {
                incomingClientListener?.invoke(decoded)
            }
        }
        domListener = listener
        WebServiceWorkerClientBridge.addMessageListener(listener)
    }

    private fun installWorkerGlobalMessageHandler(worker: ServiceWorker) {
        if (workerBridgeListener != null) return
        val handler: (String) -> Unit = { raw ->
            val decoded = ServiceWorkerFrameCodec.decodeWebText(raw)
            if (decoded != null) {
                val cid = decoded.clientId.ifEmpty { browserClientId }
                worker.dispatchIncomingEnvelope(cid, decoded)
            }
        }
        workerBridgeListener = handler
        WebWorkerGlobalBridge.addIncomingMessageHandler(handler)
    }
}
