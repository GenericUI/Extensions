package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.resources.ResourceID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass

/**
 * Base class for cross-platform GenericUI background Service Workers annotated with
 * `@MainServiceWorker`.
 *
 * Manages lifecycle (`onStart`, `onStop`, `onClientConnected`, `onClientDisconnected`), typed IPC
 * messaging & request/reply (`onMessage`, `onRequest`, `broadcast`), ongoing Android foreground
 * notifications & action buttons, Desktop system tray icons/menus, OS autostart toggles, and
 * main window launch/close coordination.
 */
open class ServiceWorker {

    private class WorkerMessageHandler(
        val typeId: String,
        val payloadClass: KClass<*>,
        val handler: suspend (ServiceWorkerClient, Any?) -> Unit
    )

    private class WorkerRequestHandler(
        val typeId: String,
        val payloadClass: KClass<*>,
        val handler: suspend (ServiceWorkerClient, Any?, ServiceWorkerReply) -> Unit
    )

    private var workerJob: Job = SupervisorJob()
    protected var workerScope: CoroutineScope = CoroutineScope(workerJob + Dispatchers.Default)
        private set

    private var host: ServiceWorkerRuntimeHost? = null
    private val clientsById = linkedMapOf<String, ServiceWorkerClient>()
    private val messageHandlers = mutableListOf<WorkerMessageHandler>()
    private val requestHandlers = mutableListOf<WorkerRequestHandler>()

    @Volatile
    var isRunning: Boolean = false
        private set

    var notificationTitle: String = "GenericUI"
        @kotlin.jvm.JvmName("assignNotificationTitle")
        set(value) {
            field = value
            syncNotificationIfAttached()
        }

    var notificationText: String = "Running in background"
        @kotlin.jvm.JvmName("assignNotificationText")
        set(value) {
            field = value
            syncNotificationIfAttached()
        }

    var notificationSubText: String? = null
        @kotlin.jvm.JvmName("assignNotificationSubText")
        set(value) {
            field = value
            syncNotificationIfAttached()
        }

    var notificationIcon: ResourceID? = null
        @kotlin.jvm.JvmName("assignNotificationIcon")
        set(value) {
            field = value
            syncNotificationIfAttached()
        }

    private val notificationActions = mutableListOf(
        ServiceWorkerNotificationAction.createDefaultStopAction()
    )

    private var trayIconPathOrUrl: String? = null
    private var trayIconResource: ResourceID? = null
    private var trayTooltip: String? = null
    private var trayBadgeText: String? = null
    private var trayMenu: ContextMenu? = null
    private var trayClickListener: (() -> Unit)? = null

    val connectedClients: List<ServiceWorkerClient>
        get() = clientsById.values.filter { it.isConnected }

    val currentTarget: BackendTarget?
        get() = ServiceWorkerExtension.currentBackendTarget()

    /**
     * Invoked when the background Service Worker starts and is ready to process work and IPC.
     */
    open fun onStart() {}

    /**
     * Invoked when the background Service Worker is stopping and should release resources.
     */
    open fun onStop() {}

    /**
     * Invoked whenever a UI client connects to this worker.
     */
    open fun onClientConnected(client: ServiceWorkerClient) {}

    /**
     * Invoked when an existing UI client disconnects from this worker.
     */
    open fun onClientDisconnected(client: ServiceWorkerClient) {}

    /**
     * Launches a coroutine scoped to the lifetime of this [ServiceWorker] (automatically cancelled on stop).
     */
    protected fun launch(block: suspend CoroutineScope.() -> Unit): Job =
        workerScope.launch(block = block)

    /**
     * Updates the ongoing foreground notification title (no-op on desktop/web).
     */
    fun setNotificationTitle(title: String) {
        notificationTitle = title
    }

    /**
     * Updates the ongoing foreground notification body text (no-op on desktop/web).
     */
    fun setNotificationText(text: String) {
        notificationText = text
    }

    /**
     * Updates the ongoing foreground notification subtext (no-op on desktop/web).
     */
    fun setNotificationSubText(subText: String?) {
        notificationSubText = subText
    }

    /**
     * Updates the ongoing foreground notification small icon (no-op on desktop/web).
     */
    fun setNotificationIcon(icon: ResourceID?) {
        notificationIcon = icon
    }

    /**
     * Appends custom notification action buttons while preserving the default `"Stop"` action
     * (or previously configured actions). Updates the active notification immediately.
     */
    fun extendNotificationActions(vararg actions: ServiceWorkerNotificationAction) {
        extendNotificationActions(actions.toList())
    }

    /**
     * Appends custom notification action buttons while preserving existing actions.
     */
    fun extendNotificationActions(actions: List<ServiceWorkerNotificationAction>) {
        for (action in actions) {
            notificationActions.removeAll { it.id == action.id }
            notificationActions.add(action)
        }
        syncNotificationIfAttached()
    }

    /**
     * Replaces all foreground notification action buttons (including removing the default `"Stop"`
     * action unless explicitly included). Updates the active notification immediately.
     */
    fun replaceNotificationActions(vararg actions: ServiceWorkerNotificationAction) {
        replaceNotificationActions(actions.toList())
    }

    /**
     * Replaces all foreground notification action buttons with [actions].
     */
    fun replaceNotificationActions(actions: List<ServiceWorkerNotificationAction>) {
        notificationActions.clear()
        notificationActions.addAll(actions)
        syncNotificationIfAttached()
    }

    fun getEffectiveNotificationActions(): List<ServiceWorkerNotificationAction> =
        notificationActions.toList()

    /**
     * Sets the desktop system tray / menu bar icon path or resource URI (no-op on mobile/web).
     */
    fun setTrayIcon(iconPathOrUrl: String?) {
        trayIconPathOrUrl = iconPathOrUrl
        trayIconResource = null
        syncTrayIfAttached()
    }

    /**
     * Sets the desktop system tray / menu bar icon resource ID (no-op on mobile/web).
     */
    fun setTrayIcon(icon: ResourceID) {
        trayIconResource = icon
        trayIconPathOrUrl = icon.toResourceUri()
        syncTrayIfAttached()
    }

    /**
     * Sets the desktop tray tooltip text (no-op on mobile/web).
     */
    fun setTrayTooltip(tooltip: String?) {
        trayTooltip = tooltip
        syncTrayIfAttached()
    }

    /**
     * Sets the desktop tray badge text where supported (no-op on mobile/web).
     */
    fun setTrayBadgeText(badgeText: String?) {
        trayBadgeText = badgeText
        syncTrayIfAttached()
    }

    /**
     * Sets the desktop tray context menu (no-op on mobile/web).
     */
    fun setTrayMenu(menu: ContextMenu?) {
        trayMenu = menu
        syncTrayIfAttached()
    }

    fun getEffectiveTrayMenu(): ContextMenu? = trayMenu

    /**
     * Registers the primary click listener for the desktop system tray icon.
     */
    fun setTrayClickListener(listener: (() -> Unit)?) {
        trayClickListener = listener
        syncTrayIfAttached()
    }

    /**
     * Opens or focuses the main application UI window (launching the main executable on Desktop,
     * main Activity on Android, or window/tab on Web).
     */
    fun openMainWindow(route: String? = null) {
        host?.openMainWindow(route)
            ?: ServiceWorkerExtension.openMainWindowFromHost(route)
    }

    /**
     * Requests closing the main UI window while keeping this background [ServiceWorker] running.
     */
    fun closeMainWindow() {
        host?.closeMainWindow()
            ?: ServiceWorkerExtension.closeMainWindowFromHost()
    }

    /**
     * Stops this background [ServiceWorker] (exits headless tray loop or Android foreground service).
     */
    fun stopSelf() {
        val currentHost = host
        performStop()
        currentHost?.stopSelf()
    }

    /**
     * Stops this worker and requests termination of the entire application (worker + connected UI clients).
     */
    fun exitApplication(exitCode: Int = 0) {
        val currentHost = host
        performStop()
        currentHost?.exitApplication(exitCode)
    }

    /**
     * Returns whether OS login/boot autostart is currently enabled for this app's Service Worker.
     */
    fun isAutostartEnabled(): Boolean =
        host?.isAutostartEnabled() ?: ServiceWorkerExtension.isAutostartEnabled()

    /**
     * Enables or disables OS login/boot autostart registration at runtime.
     */
    fun setAutostartEnabled(enabled: Boolean): Boolean =
        host?.setAutostartEnabled(enabled) ?: ServiceWorkerExtension.setAutostartEnabled(enabled)

    /**
     * Registers a one-way message handler for payloads of type [type] sent from UI clients.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> onMessage(
        type: KClass<T>,
        handler: suspend (client: ServiceWorkerClient, message: T) -> Unit
    ): Disposable {
        val entry = WorkerMessageHandler(
            typeId = ServiceWorkerCodecRegistry.resolveTypeId(type),
            payloadClass = type,
            handler = { client, msg -> handler(client, msg as T) }
        )
        messageHandlers.add(entry)
        return ServiceWorkerDisposableHandle { messageHandlers.remove(entry) }
    }

    inline fun <reified T : Any> onMessage(
        noinline handler: suspend (client: ServiceWorkerClient, message: T) -> Unit
    ): Disposable = onMessage(T::class, handler)

    /**
     * Registers a request/reply handler for requests of type [type] sent from UI clients.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> onRequest(
        type: KClass<T>,
        handler: suspend (client: ServiceWorkerClient, request: T, reply: ServiceWorkerReply) -> Unit
    ): Disposable {
        val entry = WorkerRequestHandler(
            typeId = ServiceWorkerCodecRegistry.resolveTypeId(type),
            payloadClass = type,
            handler = { client, req, reply -> handler(client, req as T, reply) }
        )
        requestHandlers.add(entry)
        return ServiceWorkerDisposableHandle { requestHandlers.remove(entry) }
    }

    inline fun <reified T : Any> onRequest(
        noinline handler: suspend (client: ServiceWorkerClient, request: T, reply: ServiceWorkerReply) -> Unit
    ): Disposable = onRequest(T::class, handler)

    /**
     * Broadcasts [message] to all connected UI clients.
     */
    suspend fun broadcast(message: Any): Int {
        val currentHost = host ?: return 0
        val (typeId, payloadJson) = ServiceWorkerCodecRegistry.encodePayload(message)
        return currentHost.broadcastEnvelope(
            ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                correlationId = 0L,
                clientId = "",
                typeId = typeId,
                payloadJson = payloadJson
            )
        )
    }

    internal fun attachAndStart(
        runtimeHost: ServiceWorkerRuntimeHost,
        options: ServiceWorkerOptions
    ) {
        if (notificationTitle == "GenericUI" && options.appName.isNotBlank()) {
            notificationTitle = options.appName
        }
        if (trayTooltip == null && options.appName.isNotBlank()) {
            trayTooltip = options.appName
        }
        if (trayIconPathOrUrl == null && !options.trayIcon.isNullOrBlank()) {
            trayIconPathOrUrl = options.trayIcon
        }
        host = runtimeHost
        if (!workerJob.isActive) {
            workerJob = SupervisorJob()
            workerScope = CoroutineScope(workerJob + Dispatchers.Default)
        }
        isRunning = true
        onStart()
        syncNotificationIfAttached()
        syncTrayIfAttached()
    }

    internal fun performStop() {
        if (!isRunning) return
        isRunning = false
        runCatching { onStop() }
        for (client in clientsById.values.toList()) {
            client.failAllPending("ServiceWorker stopped.")
        }
        clientsById.clear()
        workerScope.cancel()
    }

    internal fun dispatchClientConnected(clientId: String): ServiceWorkerClient {
        val currentHost = host ?: throw IllegalStateException("ServiceWorker host not attached.")
        val existing = clientsById[clientId]
        if (existing != null && existing.isConnected) {
            return existing
        }
        val client = ServiceWorkerClient(clientId, currentHost)
        clientsById[clientId] = client
        onClientConnected(client)
        return client
    }

    internal fun dispatchClientDisconnected(clientId: String) {
        val client = clientsById.remove(clientId) ?: return
        client.failAllPending("Client disconnected")
        onClientDisconnected(client)
    }

    internal fun dispatchIncomingEnvelope(clientId: String, envelope: ServiceWorkerEnvelope) {
        val currentHost = host ?: return
        val client = clientsById[clientId] ?: dispatchClientConnected(clientId)
        when (envelope.kind) {
            ServiceWorkerEnvelope.KIND_MESSAGE -> {
                val matching = messageHandlers.filter {
                    it.typeId == envelope.typeId ||
                        ServiceWorkerCodecRegistry.resolveTypeId(it.payloadClass) == envelope.typeId
                }
                if (matching.isNotEmpty()) {
                    workerScope.launch {
                        for (entry in matching) {
                            runCatching {
                                val decoded = ServiceWorkerCodecRegistry.decodeAs(
                                    envelope.typeId,
                                    envelope.payloadJson,
                                    entry.payloadClass
                                )
                                entry.handler(client, decoded)
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
                val reply = ServiceWorkerReply(
                    sendSuccess = { responsePayload ->
                        workerScope.launch {
                            val (respType, respJson) = ServiceWorkerCodecRegistry.encodePayload(responsePayload)
                            currentHost.sendToClient(
                                clientId,
                                ServiceWorkerEnvelope(
                                    kind = ServiceWorkerEnvelope.KIND_RESPONSE,
                                    correlationId = envelope.correlationId,
                                    clientId = clientId,
                                    typeId = respType,
                                    payloadJson = respJson
                                )
                            )
                        }
                    },
                    sendFailure = { errMsg ->
                        workerScope.launch {
                            currentHost.sendToClient(
                                clientId,
                                ServiceWorkerEnvelope(
                                    kind = ServiceWorkerEnvelope.KIND_RESPONSE_ERR,
                                    correlationId = envelope.correlationId,
                                    clientId = clientId,
                                    error = errMsg
                                )
                            )
                        }
                    }
                )
                if (entry == null) {
                    reply.fail("No @MainServiceWorker handler registered for request type '${envelope.typeId}'.")
                    return
                }
                workerScope.launch {
                    try {
                        val decoded = ServiceWorkerCodecRegistry.decodeAs(
                            envelope.typeId,
                            envelope.payloadJson,
                            entry.payloadClass
                        )
                        entry.handler(client, decoded, reply)
                    } catch (t: Throwable) {
                        if (!reply.isCompleted) {
                            reply.fail(t.message ?: t::class.simpleName ?: "ServiceWorker request error")
                        }
                    }
                }
            }
            ServiceWorkerEnvelope.KIND_RESPONSE,
            ServiceWorkerEnvelope.KIND_RESPONSE_ERR -> {
                client.completeResponse(envelope)
            }
            ServiceWorkerEnvelope.KIND_BYE -> {
                dispatchClientDisconnected(clientId)
            }
        }
    }

    internal fun dispatchNotificationActionById(actionId: String): Boolean {
        val action = notificationActions.firstOrNull { it.id == actionId } ?: return false
        runCatching { action.onClick() }
        if (action.triggersDefaultStop) {
            stopSelf()
        }
        return true
    }

    private fun syncNotificationIfAttached() {
        host?.updateNotification(
            title = notificationTitle,
            text = notificationText,
            subText = notificationSubText,
            icon = notificationIcon,
            actions = notificationActions.toList()
        )
    }

    private fun syncTrayIfAttached() {
        host?.updateTray(
            iconPathOrUrl = trayIconPathOrUrl,
            iconResource = trayIconResource,
            tooltip = trayTooltip,
            badgeText = trayBadgeText,
            menu = trayMenu,
            onClick = trayClickListener
        )
    }
}
