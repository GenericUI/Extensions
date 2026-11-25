@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.exception.FragmentMultipleWindowsUnsupportedException
import com.nexustools.genericui.exception.FragmentNotRegisteredException
import com.nexustools.genericui.exception.MultipleWindowsUnsupportedException
import com.nexustools.genericui.exception.SystemTrayUnsupportedException
import com.nexustools.genericui.exception.UnsupportedBackendCapabilityException
import com.nexustools.genericui.resources.ResourceID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.reflect.KClass

/**
 * Base class for cross-platform GenericUI background Service Workers annotated with
 * `@MainServiceWorker`.
 *
 * Manages lifecycle (`onStart`, `onStop`, `onClientConnected`, `onClientDisconnected`,
 * `onFragmentConnected`, `onFragmentDisconnected`), typed IPC messaging & request/reply (`onMessage`,
 * `onRequest`, `broadcast`, `startFragment`, `startFragmentAndAwait`, `sendToFragment`,
 * `requestFromFragment`, `onFragmentMessage`, `onFragmentRequest`), ongoing Android foreground
 * notifications & action buttons, Desktop system tray icons/menus, OS autostart toggles, and
 * window launch/close coordination.
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

    private class WorkerFragmentMessageHandler(
        val fragmentClass: KClass<out Fragment>,
        val fragmentId: String,
        val typeId: String,
        val payloadClass: KClass<*>,
        val handler: suspend (ServiceWorkerFragmentHandle, Any?) -> Unit
    )

    private class WorkerFragmentRequestHandler(
        val fragmentClass: KClass<out Fragment>,
        val fragmentId: String,
        val typeId: String,
        val payloadClass: KClass<*>,
        val handler: suspend (ServiceWorkerFragmentHandle, Any?, ServiceWorkerReply) -> Unit
    )

    private data class FragmentHandleKey(
        val clientId: String,
        val fragmentId: String,
        val instanceId: String
    )

    private class PendingOutboundItem(
        val envelope: ServiceWorkerEnvelope,
        val responseDeferred: CompletableDeferred<ServiceWorkerEnvelope>? = null,
        val onFailed: ((String) -> Unit)? = null
    )

    private class FragmentOutboundQueueState(
        val queue: ArrayDeque<PendingOutboundItem> = ArrayDeque(),
        var launchTriggered: Boolean = false
    )

    private var workerJob: Job = SupervisorJob()
    protected var workerScope: CoroutineScope = CoroutineScope(workerJob + Dispatchers.Default)
        private set

    private val stateLock = AtomicBoolean(false)
    private val nextFragmentCorrSeq = AtomicLong(2_000_000L)

    private var host: ServiceWorkerRuntimeHost? = null
    private val clientsById = linkedMapOf<String, ServiceWorkerClient>()
    private val messageHandlers = mutableListOf<WorkerMessageHandler>()
    private val requestHandlers = mutableListOf<WorkerRequestHandler>()
    private val fragmentMessageHandlers = mutableListOf<WorkerFragmentMessageHandler>()
    private val fragmentRequestHandlers = mutableListOf<WorkerFragmentRequestHandler>()
    private val fragmentHandles = linkedMapOf<FragmentHandleKey, ServiceWorkerFragmentHandle>()
    private val pendingOutboundByFragment = linkedMapOf<String, FragmentOutboundQueueState>()
    private val pendingMountWaitersByFragment = linkedMapOf<String, MutableList<CompletableDeferred<ServiceWorkerFragmentHandle>>>()
    private val fragmentSendMutexes = linkedMapOf<String, Mutex>()

    @Volatile
    var isRunning: Boolean = false
        private set

    private var _notificationTitle: String = "GenericUI"
    private var _notificationText: String = "Running in background"
    private var _notificationSubText: String? = null
    private var _notificationIcon: ResourceID? = null

    var notificationTitle: String
        get() = _notificationTitle
        @kotlin.jvm.JvmName("assignNotificationTitle")
        set(value) {
            requireOngoingNotificationSupport()
            _notificationTitle = value
            syncNotificationIfAttached()
        }

    var notificationText: String
        get() = _notificationText
        @kotlin.jvm.JvmName("assignNotificationText")
        set(value) {
            requireOngoingNotificationSupport()
            _notificationText = value
            syncNotificationIfAttached()
        }

    var notificationSubText: String?
        get() = _notificationSubText
        @kotlin.jvm.JvmName("assignNotificationSubText")
        set(value) {
            requireOngoingNotificationSupport()
            _notificationSubText = value
            syncNotificationIfAttached()
        }

    var notificationIcon: ResourceID?
        get() = _notificationIcon
        @kotlin.jvm.JvmName("assignNotificationIcon")
        set(value) {
            requireOngoingNotificationSupport()
            _notificationIcon = value
            syncNotificationIfAttached()
        }

    private val notificationActions = mutableListOf(
        ServiceWorkerNotificationAction.createDefaultStopAction()
    )

    private var trayIconPathOrUrl: String? = null
    private var macosTrayIconPathOrUrl: String? = null
    private var trayIconResource: ResourceID? = null
    private var trayTooltip: String? = null
    private var trayBadgeText: String? = null
    private var trayMenu: ContextMenu? = null
    private var trayClickListener: (() -> Unit)? = null

    val connectedClients: List<ServiceWorkerClient>
        get() = withStateLock { clientsById.values.filter { it.isConnected } }

    val currentTarget: BackendTarget?
        get() = ServiceWorkerExtension.currentBackendTarget()

    val supportsMultipleWindows: Boolean
        get() = host?.supportsMultipleWindows ?: ServiceWorkerExtension.supportsMultipleWindows

    val supportsFragmentLaunch: Boolean
        get() = host?.supportsFragmentLaunch ?: ServiceWorkerExtension.supportsFragmentLaunch

    val supportsDialogFragments: Boolean
        get() = host?.supportsDialogFragments ?: ServiceWorkerExtension.supportsDialogFragments

    val supportsSystemTray: Boolean
        get() = host?.supportsSystemTray ?: ServiceWorkerExtension.supportsSystemTray

    val supportsOngoingNotification: Boolean
        get() = host?.supportsOngoingNotification ?: ServiceWorkerExtension.supportsOngoingNotification

    val supportsAutostart: Boolean
        get() = host?.supportsAutostart ?: ServiceWorkerExtension.supportsAutostart

    fun canOpenAdditionalWindow(fragmentClass: KClass<out Fragment>): Boolean =
        supportsMultipleWindows &&
            supportsFragmentLaunch &&
            (!FragmentRegistry.isDialogFragment(fragmentClass) || supportsDialogFragments) &&
            FragmentRegistry.supportsMultipleWindows(fragmentClass)

    inline fun <reified T : Fragment> canOpenAdditionalWindow(): Boolean =
        canOpenAdditionalWindow(T::class)

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
     * Invoked when a UI [Fragment] reports itself mounted and ready for fragment-targeted IPC.
     */
    open fun onFragmentConnected(handle: ServiceWorkerFragmentHandle) {}

    /**
     * Invoked when a previously mounted UI [Fragment] unmounts or its client disconnects.
     */
    open fun onFragmentDisconnected(handle: ServiceWorkerFragmentHandle) {}

    /**
     * Invoked when the desktop main app executable is launched directly without an explicit
     * window, fragment, or route argument while this [ServiceWorker] is already running.
     *
     * Returning `false` (the default) tells the newly launched UI process to exit immediately while
     * keeping this background worker running; returning `true` allows the UI process to open or
     * activate its main window.
     */
    open fun onMainAppLaunchedWhileRunning(): Boolean = false

    /**
     * Launches a coroutine scoped to the lifetime of this [ServiceWorker] (automatically cancelled on stop).
     */
    protected fun launch(block: suspend CoroutineScope.() -> Unit): Job =
        workerScope.launch(block = block)

    /**
     * Updates the ongoing foreground notification title (throws [ServiceWorkerOngoingNotificationUnsupportedException] where unsupported).
     */
    fun setNotificationTitle(title: String) {
        notificationTitle = title
    }

    /**
     * Updates the ongoing foreground notification body text (throws [ServiceWorkerOngoingNotificationUnsupportedException] where unsupported).
     */
    fun setNotificationText(text: String) {
        notificationText = text
    }

    /**
     * Updates the ongoing foreground notification subtext (throws [ServiceWorkerOngoingNotificationUnsupportedException] where unsupported).
     */
    fun setNotificationSubText(subText: String?) {
        notificationSubText = subText
    }

    /**
     * Updates the ongoing foreground notification small icon (throws [ServiceWorkerOngoingNotificationUnsupportedException] where unsupported).
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
        requireOngoingNotificationSupport()
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
        requireOngoingNotificationSupport()
        notificationActions.clear()
        notificationActions.addAll(actions)
        syncNotificationIfAttached()
    }

    fun getEffectiveNotificationActions(): List<ServiceWorkerNotificationAction> =
        notificationActions.toList()

    /**
     * Sets the desktop system tray / menu bar icon path or resource URI
     * (throws [SystemTrayUnsupportedException] on backends without tray support).
     */
    fun setTrayIcon(iconPathOrUrl: String?) {
        requireSystemTraySupport()
        trayIconPathOrUrl = iconPathOrUrl
        trayIconResource = null
        syncTrayIfAttached()
    }

    /**
     * Sets the desktop system tray / menu bar icon resource ID
     * (throws [SystemTrayUnsupportedException] on backends without tray support).
     */
    fun setTrayIcon(icon: ResourceID) {
        requireSystemTraySupport()
        trayIconResource = icon
        trayIconPathOrUrl = icon.toResourceUri()
        syncTrayIfAttached()
    }

    /**
     * Sets an explicit macOS-specific system tray / menu bar icon path or resource URI
     * (falling back to template monochrome silhouette rendering of [setTrayIcon] when unset).
     */
    fun setMacosTrayIcon(iconPathOrUrl: String?) {
        requireSystemTraySupport()
        macosTrayIconPathOrUrl = iconPathOrUrl
        syncTrayIfAttached()
    }

    /**
     * Sets an explicit macOS-specific system tray / menu bar icon resource ID.
     */
    fun setMacosTrayIcon(icon: ResourceID) {
        requireSystemTraySupport()
        macosTrayIconPathOrUrl = icon.toResourceUri()
        syncTrayIfAttached()
    }

    /**
     * Sets the desktop tray tooltip text (throws [SystemTrayUnsupportedException] on backends without tray support).
     */
    fun setTrayTooltip(tooltip: String?) {
        requireSystemTraySupport()
        trayTooltip = tooltip
        syncTrayIfAttached()
    }

    /**
     * Sets the desktop tray badge text where supported (throws [SystemTrayUnsupportedException] on backends without tray support).
     */
    fun setTrayBadgeText(badgeText: String?) {
        requireSystemTraySupport()
        trayBadgeText = badgeText
        syncTrayIfAttached()
    }

    /**
     * Sets the desktop tray context menu (throws [SystemTrayUnsupportedException] on backends without tray support).
     */
    fun setTrayMenu(menu: ContextMenu?) {
        requireSystemTraySupport()
        trayMenu = menu
        syncTrayIfAttached()
    }

    fun getEffectiveTrayMenu(): ContextMenu? = trayMenu

    /**
     * Registers the primary click listener for the desktop system tray icon
     * (throws [SystemTrayUnsupportedException] on backends without tray support).
     */
    fun setTrayClickListener(listener: (() -> Unit)?) {
        requireSystemTraySupport()
        trayClickListener = listener
        syncTrayIfAttached()
    }

    /**
     * Opens or focuses the main application UI window (launching the main executable on Desktop,
     * main Activity on Android, or window/tab on Web).
     */
    fun openMainWindow(route: String? = null) {
        val activeHost = host
        if (activeHost != null) {
            activeHost.openMainWindow(route)
        } else {
            ServiceWorkerExtension.openMainWindowFromHost(route)
        }
    }

    /**
     * Requests closing the main UI window while keeping this background [ServiceWorker] running.
     */
    fun closeMainWindow() {
        val activeHost = host
        if (activeHost != null) {
            activeHost.closeMainWindow()
        } else {
            ServiceWorkerExtension.closeMainWindowFromHost()
        }
    }

    /**
     * Validates capability & fragment registration rules and starts or focuses the UI [Fragment]
     * identified by [fragmentClass] across platforms.
     */
    fun <F : Fragment> startFragment(
        fragmentClass: KClass<F>,
        args: FragmentBundle? = null,
        openAdditionalWindow: Boolean = false,
        clearTask: Boolean = false,
        bringToFront: Boolean = true
    ) {
        val request = validateAndBuildLaunchRequest(
            fragmentClass = fragmentClass,
            args = args,
            openAdditionalWindow = openAdditionalWindow,
            clearTask = clearTask,
            bringToFront = bringToFront
        )
        val activeHost = host
            ?: throw ServiceWorkerIpcException("ServiceWorker host is not attached; cannot start fragment '${request.fragmentId}'.")
        activeHost.requestLaunchFragment(request)
    }

    inline fun <reified F : Fragment> startFragment(
        args: FragmentBundle? = null,
        openAdditionalWindow: Boolean = false,
        clearTask: Boolean = false,
        bringToFront: Boolean = true
    ) {
        startFragment(
            fragmentClass = F::class,
            args = args,
            openAdditionalWindow = openAdditionalWindow,
            clearTask = clearTask,
            bringToFront = bringToFront
        )
    }

    fun <F : Fragment> startFragment(
        fragmentClass: KClass<F>,
        firstArg: Pair<String, Any?>,
        vararg extraArgs: Pair<String, Any?>,
        openAdditionalWindow: Boolean = false,
        clearTask: Boolean = false,
        bringToFront: Boolean = true
    ) {
        startFragment(
            fragmentClass = fragmentClass,
            args = FragmentBundle.of(firstArg, *extraArgs),
            openAdditionalWindow = openAdditionalWindow,
            clearTask = clearTask,
            bringToFront = bringToFront
        )
    }

    inline fun <reified F : Fragment> startFragment(
        firstArg: Pair<String, Any?>,
        vararg extraArgs: Pair<String, Any?>,
        openAdditionalWindow: Boolean = false,
        clearTask: Boolean = false,
        bringToFront: Boolean = true
    ) {
        startFragment(
            fragmentClass = F::class,
            firstArg = firstArg,
            extraArgs = extraArgs,
            openAdditionalWindow = openAdditionalWindow,
            clearTask = clearTask,
            bringToFront = bringToFront
        )
    }

    /**
     * Starts [fragmentClass] (or focuses an existing instance when [openAdditionalWindow] is `false`)
     * and suspends until its UI [ServiceWorkerFragmentHandle] reports mounted.
     */
    suspend fun <F : Fragment> startFragmentAndAwait(
        fragmentClass: KClass<F>,
        args: FragmentBundle? = null,
        openAdditionalWindow: Boolean = false,
        clearTask: Boolean = false,
        bringToFront: Boolean = true,
        timeoutMs: Long = 10_000L
    ): ServiceWorkerFragmentHandle {
        val request = validateAndBuildLaunchRequest(
            fragmentClass = fragmentClass,
            args = args,
            openAdditionalWindow = openAdditionalWindow,
            clearTask = clearTask,
            bringToFront = bringToFront
        )
        val fragmentId = request.fragmentId
        if (!openAdditionalWindow) {
            val existing = findActiveHandle(fragmentClass, fragmentId)
            if (existing != null) {
                val activeHost = host
                    ?: throw ServiceWorkerIpcException("ServiceWorker host is not attached; cannot start fragment '$fragmentId'.")
                activeHost.requestLaunchFragment(request)
                return existing
            }
        }
        val waiter = CompletableDeferred<ServiceWorkerFragmentHandle>()
        withStateLock {
            pendingMountWaitersByFragment.getOrPut(fragmentId) { mutableListOf() }.add(waiter)
        }
        try {
            val activeHost = host
                ?: throw ServiceWorkerIpcException("ServiceWorker host is not attached; cannot start fragment '$fragmentId'.")
            activeHost.requestLaunchFragment(request)
            return withTimeout(timeoutMs) { waiter.await() }
        } catch (t: TimeoutCancellationException) {
            throw ServiceWorkerIpcException("Timed out waiting for fragment '$fragmentId' to mount.", t)
        } finally {
            withStateLock {
                pendingMountWaitersByFragment[fragmentId]?.remove(waiter)
                if (pendingMountWaitersByFragment[fragmentId]?.isEmpty() == true) {
                    pendingMountWaitersByFragment.remove(fragmentId)
                }
            }
        }
    }

    suspend inline fun <reified F : Fragment> startFragmentAndAwait(
        args: FragmentBundle? = null,
        openAdditionalWindow: Boolean = false,
        clearTask: Boolean = false,
        bringToFront: Boolean = true,
        timeoutMs: Long = 10_000L
    ): ServiceWorkerFragmentHandle = startFragmentAndAwait(
        fragmentClass = F::class,
        args = args,
        openAdditionalWindow = openAdditionalWindow,
        clearTask = clearTask,
        bringToFront = bringToFront,
        timeoutMs = timeoutMs
    )

    /**
     * Requests closing any open UI windows hosting [fragmentClass].
     */
    fun <F : Fragment> closeFragment(fragmentClass: KClass<F>) {
        val fragmentId = FragmentRegistry.resolveFragmentId(fragmentClass)
        if (!supportsFragmentLaunch) {
            throw ServiceWorkerFragmentLaunchUnsupportedException(fragmentId, resolvedBackendName())
        }
        val activeHost = host
            ?: throw ServiceWorkerIpcException("ServiceWorker host is not attached; cannot close fragment '$fragmentId'.")
        val evicted = withStateLock {
            val keysToRemove = pendingOutboundByFragment.keys.filter { matchesFragmentId(it, fragmentId, fragmentClass) }
            val items = mutableListOf<PendingOutboundItem>()
            for (k in keysToRemove) {
                pendingOutboundByFragment.remove(k)?.queue?.let { items.addAll(it) }
            }
            items
        }
        for (item in evicted) {
            item.onFailed?.invoke("Fragment '$fragmentId' closed before pending request could be sent.")
        }
        activeHost.requestCloseFragment(fragmentId, instanceId = null)
    }

    inline fun <reified F : Fragment> closeFragment() {
        closeFragment(F::class)
    }

    /**
     * Returns `true` if at least one mounted [ServiceWorkerFragmentHandle] for [fragmentClass] is active.
     */
    fun <F : Fragment> isFragmentActive(fragmentClass: KClass<F>): Boolean =
        activeFragments(fragmentClass).isNotEmpty()

    inline fun <reified F : Fragment> isFragmentActive(): Boolean =
        isFragmentActive(F::class)

    /**
     * Returns a snapshot of all currently mounted [ServiceWorkerFragmentHandle]s matching [fragmentClass].
     */
    fun <F : Fragment> activeFragments(fragmentClass: KClass<F>): List<ServiceWorkerFragmentHandle> {
        val fragmentId = FragmentRegistry.resolveFragmentId(fragmentClass)
        return withStateLock {
            fragmentHandles.values.filter { handle ->
                handle.client.isConnected && matchesFragmentId(handle.fragmentId, fragmentId, fragmentClass)
            }
        }
    }

    inline fun <reified F : Fragment> activeFragments(): List<ServiceWorkerFragmentHandle> =
        activeFragments(F::class)

    /**
     * Sends a typed one-way [message] to [fragmentClass]. When no mounted handle exists and
     * [launchIfNeeded] is `true`, queues the message in strict per-fragment FIFO order, triggers
     * fragment launch once, and flushes all queued envelopes immediately upon `fragmentMounted`.
     */
    suspend fun <F : Fragment, M : Any> sendToFragment(
        fragmentClass: KClass<F>,
        message: M,
        launchIfNeeded: Boolean = true,
        launchArgs: FragmentBundle? = null
    ) {
        val fragmentId = FragmentRegistry.resolveFragmentId(fragmentClass)
        val (typeId, payloadJson) = ServiceWorkerCodecRegistry.encodePayload(message)
        val env = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_MESSAGE,
            correlationId = 0L,
            clientId = "",
            typeId = typeId,
            payloadJson = payloadJson,
            targetFragmentId = fragmentId
        )

        val mutex = getFragmentSendMutex(fragmentId)
        var launchRequest: ServiceWorkerFragmentLaunchRequest? = null
        mutex.withLock {
            val activeHandle = findActiveHandle(fragmentClass, fragmentId)
            if (activeHandle != null) {
                flushPendingForFragmentLocked(fragmentId, fragmentClass, activeHandle.client)
                val targetEnv = env.copy(clientId = activeHandle.client.clientId)
                val currentHost = host ?: throw ServiceWorkerIpcException("ServiceWorker host not attached.")
                if (!currentHost.sendToClient(activeHandle.client.clientId, targetEnv)) {
                    throw ServiceWorkerIpcException("Failed to send message '$typeId' to fragment '$fragmentId'.")
                }
                return@withLock
            }

            if (!launchIfNeeded) {
                throw ServiceWorkerIpcException("No active fragment handle mounted for '$fragmentId' and launchIfNeeded is false.")
            }
            val validatedRequest = validateAndBuildLaunchRequest(
                fragmentClass = fragmentClass,
                args = launchArgs,
                openAdditionalWindow = false,
                clearTask = false,
                bringToFront = true
            )
            if (enqueuePendingOutbound(fragmentId = fragmentId, item = PendingOutboundItem(envelope = env))) {
                launchRequest = validatedRequest
            }
        }

        val pendingRequest = launchRequest
        if (pendingRequest != null) {
            val currentHost = host
                ?: throw ServiceWorkerIpcException("ServiceWorker host not attached; cannot start fragment '$fragmentId'.")
            currentHost.requestLaunchFragment(pendingRequest)
        }
    }

    suspend inline fun <reified F : Fragment> sendToFragment(
        message: Any,
        launchIfNeeded: Boolean = true,
        launchArgs: FragmentBundle? = null
    ) {
        sendToFragment(F::class, message, launchIfNeeded, launchArgs)
    }

    /**
     * Sends a typed [payload] request to [fragmentClass] and awaits a response of [responseClass].
     * When no mounted handle exists and [launchIfNeeded] is `true`, queues the request in strict
     * per-fragment FIFO order and starts the fragment once.
     */
    suspend fun <F : Fragment, Req : Any, Res : Any> requestFromFragment(
        fragmentClass: KClass<F>,
        payload: Req,
        responseClass: KClass<Res>,
        timeoutMs: Long = 10_000L,
        launchIfNeeded: Boolean = true,
        launchArgs: FragmentBundle? = null
    ): Res {
        val fragmentId = FragmentRegistry.resolveFragmentId(fragmentClass)
        val (typeId, payloadJson) = ServiceWorkerCodecRegistry.encodePayload(payload)
        val corrId = nextFragmentCorrSeq.fetchAndAdd(1L)
        val env = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_REQUEST,
            correlationId = corrId,
            clientId = "",
            typeId = typeId,
            payloadJson = payloadJson,
            targetFragmentId = fragmentId
        )
        val deferred = CompletableDeferred<ServiceWorkerEnvelope>()
        val pendingItem = PendingOutboundItem(
            envelope = env,
            responseDeferred = deferred,
            onFailed = { reason ->
                if (!deferred.isCompleted) {
                    deferred.complete(
                        ServiceWorkerEnvelope(
                            kind = ServiceWorkerEnvelope.KIND_RESPONSE_ERR,
                            correlationId = corrId,
                            error = reason
                        )
                    )
                }
            }
        )

        var dispatchedToClient: ServiceWorkerClient? = null
        var launchRequest: ServiceWorkerFragmentLaunchRequest? = null
        val mutex = getFragmentSendMutex(fragmentId)
        mutex.withLock {
            val activeHandle = findActiveHandle(fragmentClass, fragmentId)
            if (activeHandle != null) {
                flushPendingForFragmentLocked(fragmentId, fragmentClass, activeHandle.client)
                val targetClient = activeHandle.client
                val currentHost = host ?: throw ServiceWorkerIpcException("ServiceWorker host not attached.")
                if (!targetClient.registerPendingDeferred(corrId, deferred)) {
                    throw ServiceWorkerIpcException("Client '${targetClient.clientId}' disconnected.")
                }
                val delivered = currentHost.sendToClient(
                    targetClient.clientId,
                    env.copy(clientId = targetClient.clientId)
                )
                if (!delivered) {
                    targetClient.removePendingDeferred(corrId)
                    throw ServiceWorkerIpcException("Failed to deliver request '$typeId' to fragment '$fragmentId'.")
                }
                dispatchedToClient = targetClient
            } else {
                if (!launchIfNeeded) {
                    throw ServiceWorkerIpcException(
                        "No active fragment handle mounted for '$fragmentId' and launchIfNeeded is false."
                    )
                }
                val validatedRequest = validateAndBuildLaunchRequest(
                    fragmentClass = fragmentClass,
                    args = launchArgs,
                    openAdditionalWindow = false,
                    clearTask = false,
                    bringToFront = true
                )
                if (enqueuePendingOutbound(fragmentId = fragmentId, item = pendingItem)) {
                    launchRequest = validatedRequest
                }
            }
        }

        val pendingRequest = launchRequest
        if (pendingRequest != null) {
            val currentHost = host
                ?: throw ServiceWorkerIpcException("ServiceWorker host not attached; cannot start fragment '$fragmentId'.")
            currentHost.requestLaunchFragment(pendingRequest)
        }

        try {
            val reply = withTimeout(timeoutMs) { deferred.await() }
            if (reply.kind == ServiceWorkerEnvelope.KIND_RESPONSE_ERR) {
                throw ServiceWorkerIpcException(reply.error.ifEmpty { "Fragment request '$typeId' failed." })
            }
            return ServiceWorkerCodecRegistry.decodeAs(reply.typeId, reply.payloadJson, responseClass)
        } catch (t: TimeoutCancellationException) {
            throw ServiceWorkerIpcException("Timed out waiting for response from fragment '$fragmentId'.", t)
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            removePendingOutboundItem(fragmentId, pendingItem)
            dispatchedToClient?.removePendingDeferred(corrId)
            withStateLock {
                fragmentHandles.values.forEach { it.client.removePendingDeferred(corrId) }
            }
        }
    }

    suspend inline fun <reified F : Fragment, reified Res : Any> requestFromFragment(
        payload: Any,
        timeoutMs: Long = 10_000L,
        launchIfNeeded: Boolean = true,
        launchArgs: FragmentBundle? = null
    ): Res = requestFromFragment(F::class, payload, Res::class, timeoutMs, launchIfNeeded, launchArgs)

    suspend inline fun <F : Fragment, reified Res : Any> requestFromFragment(
        fragmentClass: KClass<F>,
        payload: Any,
        timeoutMs: Long = 10_000L,
        launchIfNeeded: Boolean = true,
        launchArgs: FragmentBundle? = null
    ): Res = requestFromFragment(fragmentClass, payload, Res::class, timeoutMs, launchIfNeeded, launchArgs)

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
        if (supportsAutostart) {
            host?.isAutostartEnabled() ?: ServiceWorkerExtension.isAutostartEnabled()
        } else {
            false
        }

    /**
     * Enables or disables OS login/boot autostart registration at runtime.
     */
    fun setAutostartEnabled(enabled: Boolean): Boolean {
        requireAutostartSupport()
        val activeHost = host
        if (activeHost != null) {
            val applied = activeHost.setAutostartEnabled(enabled)
            if (!applied) {
                val backend = resolvedBackendName()
                throw ServiceWorkerAutostartUnsupportedException(
                    backendName = backend,
                    message = "Failed to ${if (enabled) "enable" else "disable"} ServiceWorker autostart on backend '$backend'."
                )
            }
            return true
        }
        return ServiceWorkerExtension.setAutostartEnabled(enabled)
    }

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
        withStateLock { messageHandlers.add(entry) }
        return ServiceWorkerDisposableHandle {
            withStateLock { messageHandlers.remove(entry) }
        }
    }

    inline fun <reified T : Any> onMessage(
        noinline handler: suspend (client: ServiceWorkerClient, message: T) -> Unit
    ): Disposable = onMessage(T::class, handler)

    /**
     * Registers a one-way message handler scoped to messages arriving from [fragmentClass].
     */
    @Suppress("UNCHECKED_CAST")
    fun <F : Fragment, T : Any> onFragmentMessage(
        fragmentClass: KClass<F>,
        messageClass: KClass<T>,
        handler: suspend (ServiceWorkerFragmentHandle, T) -> Unit
    ): ServiceWorkerDisposableHandle {
        val entry = WorkerFragmentMessageHandler(
            fragmentClass = fragmentClass,
            fragmentId = FragmentRegistry.resolveFragmentId(fragmentClass),
            typeId = ServiceWorkerCodecRegistry.resolveTypeId(messageClass),
            payloadClass = messageClass,
            handler = { handle, msg -> handler(handle, msg as T) }
        )
        withStateLock { fragmentMessageHandlers.add(entry) }
        return ServiceWorkerDisposableHandle {
            withStateLock { fragmentMessageHandlers.remove(entry) }
        }
    }

    inline fun <reified F : Fragment, reified T : Any> onFragmentMessage(
        noinline handler: suspend (ServiceWorkerFragmentHandle, T) -> Unit
    ): ServiceWorkerDisposableHandle = onFragmentMessage(F::class, T::class, handler)

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
        withStateLock { requestHandlers.add(entry) }
        return ServiceWorkerDisposableHandle {
            withStateLock { requestHandlers.remove(entry) }
        }
    }

    inline fun <reified T : Any> onRequest(
        noinline handler: suspend (client: ServiceWorkerClient, request: T, reply: ServiceWorkerReply) -> Unit
    ): Disposable = onRequest(T::class, handler)

    /**
     * Registers a request handler scoped to requests arriving from [fragmentClass], replying via [ServiceWorkerReply].
     */
    @Suppress("UNCHECKED_CAST")
    fun <F : Fragment, Req : Any> onFragmentRequest(
        fragmentClass: KClass<F>,
        requestClass: KClass<Req>,
        handler: suspend (ServiceWorkerFragmentHandle, Req, ServiceWorkerReply) -> Unit
    ): ServiceWorkerDisposableHandle {
        val entry = WorkerFragmentRequestHandler(
            fragmentClass = fragmentClass,
            fragmentId = FragmentRegistry.resolveFragmentId(fragmentClass),
            typeId = ServiceWorkerCodecRegistry.resolveTypeId(requestClass),
            payloadClass = requestClass,
            handler = { handle, req, reply -> handler(handle, req as Req, reply) }
        )
        withStateLock { fragmentRequestHandlers.add(entry) }
        return ServiceWorkerDisposableHandle {
            withStateLock { fragmentRequestHandlers.remove(entry) }
        }
    }

    inline fun <reified F : Fragment, reified Req : Any> onFragmentRequest(
        noinline handler: suspend (ServiceWorkerFragmentHandle, Req, ServiceWorkerReply) -> Unit
    ): ServiceWorkerDisposableHandle = onFragmentRequest(F::class, Req::class, handler)

    /**
     * Registers a suspending request handler scoped to [fragmentClass] returning [Res] directly.
     */
    @Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
    fun <F : Fragment, Req : Any, Res : Any> onFragmentRequest(
        fragmentClass: KClass<F>,
        requestClass: KClass<Req>,
        responseClass: KClass<Res>,
        handler: suspend (ServiceWorkerFragmentHandle, Req) -> Res
    ): ServiceWorkerDisposableHandle {
        val entry = WorkerFragmentRequestHandler(
            fragmentClass = fragmentClass,
            fragmentId = FragmentRegistry.resolveFragmentId(fragmentClass),
            typeId = ServiceWorkerCodecRegistry.resolveTypeId(requestClass),
            payloadClass = requestClass,
            handler = { handle, req, reply ->
                try {
                    reply.send(handler(handle, req as Req))
                } catch (t: Throwable) {
                    if (!reply.isCompleted) {
                        reply.fail(t.message ?: t::class.simpleName ?: "Fragment request error")
                    }
                }
            }
        )
        withStateLock { fragmentRequestHandlers.add(entry) }
        return ServiceWorkerDisposableHandle {
            withStateLock { fragmentRequestHandlers.remove(entry) }
        }
    }

    inline fun <reified F : Fragment, reified Req : Any, reified Res : Any> onFragmentRequest(
        noinline handler: suspend (ServiceWorkerFragmentHandle, Req) -> Res
    ): ServiceWorkerDisposableHandle = onFragmentRequest(F::class, Req::class, Res::class, handler)

    /**
     * Broadcasts [message] to all connected UI clients.
     */
    suspend fun broadcast(message: Any): Int {
        val currentHost = host
            ?: throw ServiceWorkerIpcException("ServiceWorker host is not attached; cannot broadcast message.")
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
        if (runtimeHost.supportsOngoingNotification && _notificationTitle == "GenericUI" && options.appName.isNotBlank()) {
            _notificationTitle = options.appName
        }
        if (runtimeHost.supportsSystemTray) {
            if (trayTooltip == null && options.appName.isNotBlank()) {
                trayTooltip = options.appName
            }
            if (trayIconPathOrUrl == null && !options.trayIcon.isNullOrBlank()) {
                trayIconPathOrUrl = options.trayIcon
            }
            if (macosTrayIconPathOrUrl == null && !options.macosTrayIcon.isNullOrBlank()) {
                macosTrayIconPathOrUrl = options.macosTrayIcon
            }
        }
        host = runtimeHost
        if (!workerJob.isActive) {
            workerJob = SupervisorJob()
            workerScope = CoroutineScope(workerJob + Dispatchers.Default)
        }
        isRunning = true
        onStart()
        if (runtimeHost.supportsOngoingNotification) {
            syncNotificationIfAttached()
        }
        if (runtimeHost.supportsSystemTray) {
            syncTrayIfAttached()
        }
    }

    internal fun performStop() {
        if (!isRunning) return
        isRunning = false
        val (clientsSnapshot, handlesSnapshot, pendingItemsSnapshot, waitersSnapshot) = withStateLock {
            val clients = clientsById.values.toList()
            clientsById.clear()
            val handles = fragmentHandles.values.toList()
            fragmentHandles.clear()
            val pendingItems = pendingOutboundByFragment.values.flatMap { it.queue }
            pendingOutboundByFragment.clear()
            val waiters = pendingMountWaitersByFragment.values.flatten()
            pendingMountWaitersByFragment.clear()
            Quad(clients, handles, pendingItems, waiters)
        }
        try {
            onStop()
        } finally {
            for (client in clientsSnapshot) {
                client.failAllPending("ServiceWorker stopped.")
            }
            for (handle in handlesSnapshot) {
                onFragmentDisconnected(handle)
            }
            for (item in pendingItemsSnapshot) {
                item.onFailed?.invoke("ServiceWorker stopped.")
            }
            for (waiter in waitersSnapshot) {
                waiter.cancel()
            }
            workerScope.cancel()
        }
    }

    internal fun dispatchClientConnected(clientId: String): ServiceWorkerClient {
        val currentHost = host ?: throw ServiceWorkerIpcException("ServiceWorker host not attached.")
        var newlyCreated = false
        val client = withStateLock {
            val existing = clientsById[clientId]
            if (existing != null && existing.isConnected) {
                existing
            } else {
                newlyCreated = true
                ServiceWorkerClient(clientId, currentHost).also { clientsById[clientId] = it }
            }
        }
        if (newlyCreated) {
            onClientConnected(client)
        }
        return client
    }

    internal fun dispatchClientDisconnected(clientId: String) {
        val (client, removedHandles) = withStateLock {
            val c = clientsById.remove(clientId)
            val removed = mutableListOf<ServiceWorkerFragmentHandle>()
            val iterator = fragmentHandles.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key.clientId == clientId) {
                    removed.add(entry.value)
                    iterator.remove()
                }
            }
            c to removed
        }
        if (client == null && removedHandles.isEmpty()) return
        client?.failAllPending("Client disconnected")
        for (handle in removedHandles) {
            onFragmentDisconnected(handle)
        }
        if (client != null) {
            onClientDisconnected(client)
        }
    }

    internal fun dispatchIncomingEnvelope(clientId: String, envelope: ServiceWorkerEnvelope) {
        val currentHost = host ?: return
        val client = withStateLock { clientsById[clientId] } ?: dispatchClientConnected(clientId)
        when (envelope.kind) {
            ServiceWorkerEnvelope.KIND_MESSAGE -> {
                if (envelope.typeId == ServiceWorkerEnvelope.CONTROL_FRAGMENT_MOUNTED) {
                    handleFragmentMountedControl(client, envelope.payloadJson)
                    return
                }
                if (envelope.typeId == ServiceWorkerEnvelope.CONTROL_FRAGMENT_UNMOUNTED) {
                    handleFragmentUnmountedControl(client, envelope.payloadJson)
                    return
                }
                val sourceFragId = envelope.sourceFragmentId?.takeIf { it.isNotBlank() }
                val matchingFragHandlers = if (sourceFragId != null) {
                    withStateLock {
                        fragmentMessageHandlers.filter { entry ->
                            val typeMatches = entry.typeId == envelope.typeId ||
                                ServiceWorkerCodecRegistry.resolveTypeId(entry.payloadClass) == envelope.typeId
                            typeMatches && matchesFragmentId(sourceFragId, entry.fragmentId, entry.fragmentClass)
                        }
                    }
                } else {
                    emptyList()
                }
                val matchingGlobalHandlers = withStateLock {
                    messageHandlers.filter {
                        it.typeId == envelope.typeId ||
                            ServiceWorkerCodecRegistry.resolveTypeId(it.payloadClass) == envelope.typeId
                    }
                }
                if (matchingFragHandlers.isNotEmpty() || matchingGlobalHandlers.isNotEmpty()) {
                    val fragHandle = if (matchingFragHandlers.isNotEmpty() && sourceFragId != null) {
                        resolveHandleForIncoming(client, sourceFragId)
                    } else {
                        null
                    }
                    workerScope.launch {
                        if (fragHandle != null) {
                            for (entry in matchingFragHandlers) {
                                val decoded = ServiceWorkerCodecRegistry.decodeAs(
                                    envelope.typeId,
                                    envelope.payloadJson,
                                    entry.payloadClass
                                )
                                entry.handler(fragHandle, decoded)
                            }
                        }
                        for (entry in matchingGlobalHandlers) {
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
            ServiceWorkerEnvelope.KIND_REQUEST -> {
                val sourceFragId = envelope.sourceFragmentId?.takeIf { it.isNotBlank() }
                val fragEntry = if (sourceFragId != null) {
                    withStateLock {
                        fragmentRequestHandlers.firstOrNull { entry ->
                            val typeMatches = entry.typeId == envelope.typeId ||
                                ServiceWorkerCodecRegistry.resolveTypeId(entry.payloadClass) == envelope.typeId
                            typeMatches && matchesFragmentId(sourceFragId, entry.fragmentId, entry.fragmentClass)
                        }
                    }
                } else {
                    null
                }
                val globalEntry = if (fragEntry == null) {
                    withStateLock {
                        requestHandlers.firstOrNull {
                            it.typeId == envelope.typeId ||
                                ServiceWorkerCodecRegistry.resolveTypeId(it.payloadClass) == envelope.typeId
                        }
                    }
                } else {
                    null
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
                                    payloadJson = respJson,
                                    targetFragmentId = envelope.sourceFragmentId,
                                    sourceFragmentId = envelope.targetFragmentId
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
                                    error = errMsg,
                                    targetFragmentId = envelope.sourceFragmentId,
                                    sourceFragmentId = envelope.targetFragmentId
                                )
                            )
                        }
                    }
                )
                if (fragEntry == null && globalEntry == null) {
                    reply.fail("No @MainServiceWorker handler registered for request type '${envelope.typeId}'.")
                    return
                }
                val fragHandle = if (fragEntry != null && sourceFragId != null) {
                    resolveHandleForIncoming(client, sourceFragId)
                } else {
                    null
                }
                workerScope.launch {
                    try {
                        if (fragEntry != null && fragHandle != null) {
                            val decoded = ServiceWorkerCodecRegistry.decodeAs(
                                envelope.typeId,
                                envelope.payloadJson,
                                fragEntry.payloadClass
                            )
                            fragEntry.handler(fragHandle, decoded, reply)
                        } else if (globalEntry != null) {
                            val decoded = ServiceWorkerCodecRegistry.decodeAs(
                                envelope.typeId,
                                envelope.payloadJson,
                                globalEntry.payloadClass
                            )
                            globalEntry.handler(client, decoded, reply)
                        }
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
        try {
            action.onClick()
        } finally {
            if (action.triggersDefaultStop) {
                stopSelf()
            }
        }
        return true
    }

    private fun handleFragmentMountedControl(client: ServiceWorkerClient, payloadJson: String) {
        val (fragmentId, rawInstanceId, route) = ServiceWorkerFragmentLaunchRequest.decodeMountControlPayload(payloadJson)
            ?: return
        val instanceId = rawInstanceId.ifEmpty { "default" }
        val key = FragmentHandleKey(client.clientId, fragmentId, instanceId)
        val mountedClass = FragmentRegistry.resolveFragmentClass(fragmentId)
            ?: route?.let { FragmentRegistry.resolveFragmentClass(it) }

        var newlyRegisteredHandle: ServiceWorkerFragmentHandle? = null
        val waitersToComplete = mutableListOf<CompletableDeferred<ServiceWorkerFragmentHandle>>()
        val handle = withStateLock {
            val existing = fragmentHandles[key]
            val resolvedHandle = if (existing != null && existing.client === client) {
                existing
            } else {
                ServiceWorkerFragmentHandle(
                    client = client,
                    fragmentId = fragmentId,
                    instanceId = instanceId,
                    route = route
                ).also {
                    fragmentHandles[key] = it
                    newlyRegisteredHandle = it
                }
            }

            val waiterKeys = pendingMountWaitersByFragment.keys.filter { keyFragId ->
                matchesFragmentId(keyFragId, fragmentId, mountedClass)
            }
            for (k in waiterKeys) {
                pendingMountWaitersByFragment.remove(k)?.let { waitersToComplete.addAll(it) }
            }
            resolvedHandle
        }

        for (waiter in waitersToComplete) {
            waiter.complete(handle)
        }
        if (newlyRegisteredHandle != null) {
            onFragmentConnected(handle)
        }

        workerScope.launch {
            val mutex = getFragmentSendMutex(fragmentId)
            mutex.withLock {
                flushPendingForFragmentLocked(fragmentId, mountedClass, client)
            }
        }
    }

    private fun handleFragmentUnmountedControl(client: ServiceWorkerClient, payloadJson: String) {
        val (fragmentId, rawInstanceId, _) = ServiceWorkerFragmentLaunchRequest.decodeMountControlPayload(payloadJson)
            ?: return
        val instanceId = rawInstanceId.takeIf { it.isNotEmpty() }
        val targetClass = FragmentRegistry.resolveFragmentClass(fragmentId)
        val removedHandles = withStateLock {
            val removed = mutableListOf<ServiceWorkerFragmentHandle>()
            val iterator = fragmentHandles.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val k = entry.key
                if (k.clientId == client.clientId &&
                    matchesFragmentId(k.fragmentId, fragmentId, targetClass) &&
                    (instanceId == null || k.instanceId == instanceId)
                ) {
                    removed.add(entry.value)
                    iterator.remove()
                }
            }
            removed
        }
        for (handle in removedHandles) {
            onFragmentDisconnected(handle)
        }
    }

    private suspend fun flushPendingForFragmentLocked(
        fragmentId: String,
        fragmentClass: KClass<out Fragment>?,
        client: ServiceWorkerClient
    ) {
        val currentHost = host ?: return
        while (true) {
            val itemsToFlush = withStateLock {
                val matchingKeys = pendingOutboundByFragment.keys.filter { keyFragId ->
                    matchesFragmentId(keyFragId, fragmentId, fragmentClass)
                }
                val batch = mutableListOf<PendingOutboundItem>()
                for (k in matchingKeys) {
                    val state = pendingOutboundByFragment.remove(k) ?: continue
                    batch.addAll(state.queue)
                }
                batch
            }
            if (itemsToFlush.isEmpty()) break

            for (item in itemsToFlush) {
                val env = item.envelope.copy(clientId = client.clientId)
                if (env.kind == ServiceWorkerEnvelope.KIND_REQUEST && item.responseDeferred != null) {
                    if (!client.registerPendingDeferred(env.correlationId, item.responseDeferred)) {
                        item.onFailed?.invoke("Client '${client.clientId}' disconnected.")
                        continue
                    }
                    val delivered = currentHost.sendToClient(client.clientId, env)
                    if (!delivered) {
                        client.removePendingDeferred(env.correlationId)
                        item.onFailed?.invoke("Failed to deliver queued request '${env.typeId}' to '${client.clientId}'.")
                    }
                } else {
                    currentHost.sendToClient(client.clientId, env)
                }
            }
        }
    }

    private fun enqueuePendingOutbound(
        fragmentId: String,
        item: PendingOutboundItem
    ): Boolean {
        var evictedItem: PendingOutboundItem? = null
        val triggerLaunch = withStateLock {
            val state = pendingOutboundByFragment.getOrPut(fragmentId) { FragmentOutboundQueueState() }
            if (state.queue.size >= MAX_PENDING_OUTBOUND_PER_FRAGMENT) {
                evictedItem = state.queue.removeFirst()
            }
            state.queue.addLast(item)
            val shouldTrigger = !state.launchTriggered
            state.launchTriggered = true
            shouldTrigger
        }
        evictedItem?.onFailed?.invoke("Pending outbound queue for '$fragmentId' exceeded capacity ($MAX_PENDING_OUTBOUND_PER_FRAGMENT).")
        return triggerLaunch
    }

    private fun removePendingOutboundItem(fragmentId: String, item: PendingOutboundItem) {
        withStateLock {
            pendingOutboundByFragment[fragmentId]?.queue?.remove(item)
        }
    }

    private fun findActiveHandle(
        fragmentClass: KClass<out Fragment>,
        fragmentId: String
    ): ServiceWorkerFragmentHandle? = withStateLock {
        fragmentHandles.values.firstOrNull { handle ->
            handle.client.isConnected && matchesFragmentId(handle.fragmentId, fragmentId, fragmentClass)
        }
    }

    private fun resolveHandleForIncoming(
        client: ServiceWorkerClient,
        sourceFragmentId: String
    ): ServiceWorkerFragmentHandle {
        val targetClass = FragmentRegistry.resolveFragmentClass(sourceFragmentId)
        val existing = withStateLock {
            fragmentHandles.values.firstOrNull { handle ->
                handle.client.clientId == client.clientId &&
                    matchesFragmentId(handle.fragmentId, sourceFragmentId, targetClass)
            }
        }
        return existing ?: ServiceWorkerFragmentHandle(
            client = client,
            fragmentId = sourceFragmentId,
            instanceId = "",
            route = targetClass?.let { FragmentRegistry.resolveRoute(it) }
        )
    }

    private fun matchesFragmentId(
        candidateId: String,
        expectedId: String,
        expectedClass: KClass<out Fragment>?
    ): Boolean {
        if (candidateId == expectedId) return true
        if (expectedClass != null) {
            val resolvedCandidate = FragmentRegistry.resolveFragmentClass(candidateId)
            if (resolvedCandidate == expectedClass) return true
        }
        return false
    }

    private fun getFragmentSendMutex(fragmentId: String): Mutex = withStateLock {
        fragmentSendMutexes.getOrPut(fragmentId) { Mutex() }
    }

    private fun <F : Fragment> validateAndBuildLaunchRequest(
        fragmentClass: KClass<F>,
        args: FragmentBundle?,
        openAdditionalWindow: Boolean,
        clearTask: Boolean,
        bringToFront: Boolean
    ): ServiceWorkerFragmentLaunchRequest {
        val backend = resolvedBackendName()
        val fragmentId = FragmentRegistry.resolveFragmentId(fragmentClass)
        if (!supportsFragmentLaunch) {
            throw ServiceWorkerFragmentLaunchUnsupportedException(
                fragmentId = fragmentId,
                backendName = backend
            )
        }
        val isRegistered = FragmentRegistry.getMetadata(fragmentClass) != null ||
            FragmentRegistry.isRegistered(fragmentClass)
        if (!isRegistered) {
            throw FragmentNotRegisteredException(fragmentId)
        }
        if (openAdditionalWindow) {
            if (!supportsMultipleWindows) {
                throw MultipleWindowsUnsupportedException(backend)
            }
            if (!FragmentRegistry.supportsMultipleWindows(fragmentClass)) {
                throw FragmentMultipleWindowsUnsupportedException(fragmentId)
            }
        }
        if (FragmentRegistry.isDialogFragment(fragmentClass) && !supportsDialogFragments) {
            throw UnsupportedBackendCapabilityException(
                capability = "dialogWindows",
                backendName = backend,
                message = "Dialog fragments are not supported on backend '$backend'."
            )
        }
        return ServiceWorkerFragmentLaunchRequest(
            fragmentId = fragmentId,
            route = FragmentRegistry.resolveRoute(fragmentClass),
            arguments = args,
            openAdditionalWindow = openAdditionalWindow,
            clearTask = clearTask,
            bringToFront = bringToFront
        )
    }

    private fun resolvedBackendName(): String =
        host?.backendName ?: ServiceWorkerExtension.currentBackendName()

    private fun requireOngoingNotificationSupport() {
        if (!supportsOngoingNotification) {
            throw ServiceWorkerOngoingNotificationUnsupportedException(resolvedBackendName())
        }
    }

    private fun requireSystemTraySupport() {
        if (!supportsSystemTray) {
            throw SystemTrayUnsupportedException(resolvedBackendName())
        }
    }

    private fun requireAutostartSupport() {
        if (!supportsAutostart) {
            throw ServiceWorkerAutostartUnsupportedException(resolvedBackendName())
        }
    }

    private fun syncNotificationIfAttached() {
        val activeHost = host ?: return
        if (!activeHost.supportsOngoingNotification) return
        activeHost.updateNotification(
            title = notificationTitle,
            text = notificationText,
            subText = notificationSubText,
            icon = notificationIcon,
            actions = notificationActions.toList()
        )
    }

    private fun syncTrayIfAttached() {
        val activeHost = host ?: return
        if (!activeHost.supportsSystemTray) return
        activeHost.updateTray(
            iconPathOrUrl = trayIconPathOrUrl,
            iconResource = trayIconResource,
            tooltip = trayTooltip,
            badgeText = trayBadgeText,
            menu = trayMenu,
            onClick = trayClickListener,
            macosIconPathOrUrl = macosTrayIconPathOrUrl
        )
    }

    private inline fun <T> withStateLock(block: () -> T): T {
        while (!stateLock.compareAndSet(expectedValue = false, newValue = true)) {
            // Brief spin around in-memory worker state maps.
        }
        return try {
            block()
        } finally {
            stateLock.store(false)
        }
    }

    private data class Quad<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    companion object {
        private const val MAX_PENDING_OUTBOUND_PER_FRAGMENT: Int = 256
    }
}
