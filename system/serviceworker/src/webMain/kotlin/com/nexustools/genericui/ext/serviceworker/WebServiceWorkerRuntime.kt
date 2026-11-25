package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.exception.SystemTrayUnsupportedException
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

    override val backendName: String = BackendTarget.Web.displayName
    override val isSupported: Boolean = true
    override val supportsMultipleWindows: Boolean = false
    override val supportsFragmentLaunch: Boolean
        get() = if (WebWorkerGlobalBridge.isWorkerGlobalScope) {
            WebServiceWorkerWindowBridge.canLaunchClientWindow()
        } else {
            incomingClientListener != null || WebServiceWorkerClientBridge.isServiceWorkerSupported
        }
    override val supportsDialogFragments: Boolean
        get() = supportsFragmentLaunch
    override val supportsSystemTray: Boolean = false
    override val supportsOngoingNotification: Boolean = false
    override val supportsAutostart: Boolean = false

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
        throw ServiceWorkerOngoingNotificationUnsupportedException(BackendTarget.Web.displayName)
    }

    override fun updateTray(
        iconPathOrUrl: String?,
        iconResource: ResourceID?,
        tooltip: String?,
        badgeText: String?,
        menu: ContextMenu?,
        onClick: (() -> Unit)?,
        macosIconPathOrUrl: String?
    ) {
        throw SystemTrayUnsupportedException(BackendTarget.Web.displayName)
    }

    override fun openMainWindow(route: String?) {
        val env = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_MESSAGE,
            typeId = ServiceWorkerEnvelope.CONTROL_OPEN_MAIN_WINDOW,
            payloadJson = route.orEmpty()
        )
        if (WebWorkerGlobalBridge.isWorkerGlobalScope) {
            val openUrl = if (route.isNullOrBlank()) "./" else "./?route=${encodePercentQuery(route)}"
            if (!WebServiceWorkerWindowBridge.launchOrFocusClientWindow(
                    launchControlWireText = ServiceWorkerFrameCodec.encodeWebText(env),
                    openUrl = openUrl
                )
            ) {
                throw ServiceWorkerFragmentLaunchUnsupportedException(
                    fragmentId = "__main__",
                    backendName = BackendTarget.Web.displayName,
                    message = "Web ServiceWorker has no controllable client window or openWindow capability to open main window."
                )
            }
            return
        }
        val listener = incomingClientListener
            ?: throw ServiceWorkerFragmentLaunchUnsupportedException(
                fragmentId = "__main__",
                backendName = BackendTarget.Web.displayName,
                message = "Web UI client listener is unavailable to open main window."
            )
        listener(env)
    }

    override fun closeMainWindow() {
        val env = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_MESSAGE,
            typeId = ServiceWorkerEnvelope.CONTROL_CLOSE_MAIN_WINDOW,
            payloadJson = "null"
        )
        if (WebWorkerGlobalBridge.isWorkerGlobalScope) {
            WebWorkerGlobalBridge.broadcastToClients(ServiceWorkerFrameCodec.encodeWebText(env))
            return
        }
        incomingClientListener?.invoke(env)
    }

    override fun requestLaunchFragment(request: ServiceWorkerFragmentLaunchRequest) {
        if (!supportsFragmentLaunch) {
            throw ServiceWorkerFragmentLaunchUnsupportedException(
                fragmentId = request.fragmentId,
                backendName = BackendTarget.Web.displayName
            )
        }
        val env = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_MESSAGE,
            typeId = ServiceWorkerEnvelope.CONTROL_LAUNCH_FRAGMENT,
            payloadJson = request.toControlPayloadJson(),
            targetFragmentId = request.fragmentId
        )
        if (WebWorkerGlobalBridge.isWorkerGlobalScope) {
            if (!WebServiceWorkerWindowBridge.launchOrFocusClientWindow(
                    launchControlWireText = ServiceWorkerFrameCodec.encodeWebText(env),
                    openUrl = buildLaunchUrl(request)
                )
            ) {
                throw ServiceWorkerFragmentLaunchUnsupportedException(
                    fragmentId = request.fragmentId,
                    backendName = BackendTarget.Web.displayName,
                    message = "Web ServiceWorker has no controllable WindowClient or openWindow() capability for fragment '${request.fragmentId}'."
                )
            }
            return
        }
        val listener = incomingClientListener
            ?: throw ServiceWorkerFragmentLaunchUnsupportedException(
                fragmentId = request.fragmentId,
                backendName = BackendTarget.Web.displayName,
                message = "No active Web UI client channel is attached to launch fragment '${request.fragmentId}'."
            )
        listener(env)
    }

    override fun requestCloseFragment(fragmentId: String, instanceId: String?) {
        val env = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_MESSAGE,
            typeId = ServiceWorkerEnvelope.CONTROL_CLOSE_FRAGMENT,
            payloadJson = ServiceWorkerFragmentLaunchRequest.encodeCloseControlPayload(fragmentId, instanceId),
            targetFragmentId = fragmentId
        )
        if (WebWorkerGlobalBridge.isWorkerGlobalScope) {
            WebWorkerGlobalBridge.broadcastToClients(ServiceWorkerFrameCodec.encodeWebText(env))
            return
        }
        incomingClientListener?.invoke(env)
    }

    override fun stopSelf() {
        workerBridgeListener?.let { WebWorkerGlobalBridge.removeIncomingMessageHandler(it) }
        workerBridgeListener = null
        runningWorker = null
    }

    override fun exitApplication(exitCode: Int) {
        stopSelf()
    }

    override fun isAutostartEnabled(): Boolean = false

    override fun setAutostartEnabled(enabled: Boolean): Boolean {
        throw ServiceWorkerAutostartUnsupportedException(BackendTarget.Web.displayName)
    }

    override fun setAutostartEnabled(options: ServiceWorkerOptions, enabled: Boolean): Boolean {
        throw ServiceWorkerAutostartUnsupportedException(BackendTarget.Web.displayName)
    }

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

    private fun buildLaunchUrl(request: ServiceWorkerFragmentLaunchRequest): String {
        val params = mutableListOf<String>()
        if (request.fragmentId.isNotBlank()) {
            params.add("fragment=${encodePercentQuery(request.fragmentId)}")
        }
        request.route?.takeIf { it.isNotBlank() }?.let { route ->
            params.add("route=${encodePercentQuery(route)}")
        }
        params.add("openAdditionalWindow=${request.openAdditionalWindow}")
        if (request.clearTask) {
            params.add("clearTask=true")
        }
        val args = request.arguments
        if (args != null && args.keys().isNotEmpty()) {
            params.add("args=${encodePercentQuery(args.toBase64Url())}")
        }
        return if (params.isEmpty()) "./" else "./?${params.joinToString("&")}"
    }

    private fun encodePercentQuery(value: String): String {
        val bytes = value.encodeToByteArray()
        val sb = StringBuilder(bytes.size)
        val hex = "0123456789ABCDEF"
        for (b in bytes) {
            val ch = b.toInt() and 0xFF
            val isUnreserved = (ch in 'A'.code..'Z'.code) ||
                (ch in 'a'.code..'z'.code) ||
                (ch in '0'.code..'9'.code) ||
                ch == '-'.code || ch == '_'.code || ch == '.'.code || ch == '~'.code
            if (isUnreserved) {
                sb.append(ch.toChar())
            } else {
                sb.append('%')
                sb.append(hex[(ch ushr 4) and 0x0F])
                sb.append(hex[ch and 0x0F])
            }
        }
        return sb.toString()
    }
}
