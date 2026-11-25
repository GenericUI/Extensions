@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.system.SystemTrayIcon
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.exception.SystemTrayUnsupportedException
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.threading.GenericUiThreading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal const val DESKTOP_CONTROL_CLIENT_PREFIX: String = "__genericui.sw.control."
private const val PENDING_MAIN_SPAWN_GRACE_MS: Long = 5_000L

/**
 * Worker-side desktop host owning the native IPC [DesktopIpcServer],
 * atomic `serviceworker-endpoint.json` discovery file, token authentication, [SystemTrayIcon],
 * and main-window / fragment-window / autostart / shutdown coordination.
 */
internal class DesktopServiceWorkerHost(
    private val options: ServiceWorkerOptions,
    private val worker: ServiceWorker,
    private val endpointFilePath: String,
    private val mainBinaryBaseName: String,
    private val swingMainClassFqn: String,
    private val stopEventLoop: () -> Unit,
    private val lockRelease: () -> Unit,
    private val endpointOverrideForTests: ((DesktopEndpointDescriptor) -> Unit)? = null,
    private val spawnLauncher: (String, List<String>, String?) -> Boolean = { exe, args, dir ->
        DesktopOsHelpers.spawnDetachedProcess(exe, args, dir)
    },
    private val siblingCommandResolver: (String, String) -> Pair<String, List<String>>? = { base, mainCls ->
        DesktopOsHelpers.resolveSiblingBinaryCommand(base, mainCls)
    }
) : ServiceWorkerRuntimeHost {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = DesktopIpcServer()
    private val sessionsById = linkedMapOf<String, DesktopIpcConnection>()
    private val mainWindowStateLock = AtomicBoolean(false)
    private var pendingSpawnDeadline: TimeMark? = null
    private var pendingOpenRoute: String? = null
    private var hasPendingOpenRoute: Boolean = false
    private val pendingFragmentLaunches = mutableListOf<ServiceWorkerFragmentLaunchRequest>()
    private val authToken: String = DesktopOsHelpers.generateRandomHexToken(32)
    private var nextClientSeq: Int = 1

    @Volatile
    private var stopped: Boolean = false

    private var managedTray: SystemTrayIcon? = null
    private var trayInitialized: Boolean = false
    internal var traySupportedForTests: Boolean? = null

    override val backendName: String
        get() = GenericUI.activeFactory?.backendTarget?.displayName ?: "Desktop"

    override val supportsMultipleWindows: Boolean = true
    override val supportsFragmentLaunch: Boolean = true
    override val supportsDialogFragments: Boolean = true
    override val supportsSystemTray: Boolean
        get() = traySupportedForTests ?: (GenericUI.activeFactory?.supportsSystemTray ?: true)
    override val supportsOngoingNotification: Boolean = false
    override val supportsAutostart: Boolean = true

    internal val currentTrayForTests: SystemTrayIcon?
        get() = managedTray

    val boundTransport: String
        get() = server.transportKind

    val boundAddress: String
        get() = server.endpointAddress

    val token: String
        get() = authToken

    fun start(): Boolean {
        val parentDir = endpointFilePath.substringBeforeLast('/', "").ifEmpty {
            endpointFilePath.substringBeforeLast('\\', "")
        }
        if (parentDir.isNotEmpty()) {
            DesktopOsHelpers.ensureDirectoryExists(parentDir)
        }
        val runtimeDir = parentDir.ifEmpty { DesktopOsHelpers.runtimeDataDirectory(options.appId) }
        val started = server.start(
            appId = options.appId,
            runtimeDir = runtimeDir,
            onClientConnected = { handleAcceptedConnection(it) }
        )
        if (!started || server.transportKind.isBlank() || server.endpointAddress.isBlank()) {
            return false
        }
        val descriptor = DesktopEndpointDescriptor(
            transport = server.transportKind,
            address = server.endpointAddress,
            token = authToken,
            pid = DesktopOsHelpers.currentProcessId(),
            appId = options.appId
        )
        DesktopOsHelpers.atomicWriteTextFile(
            path = endpointFilePath,
            content = descriptor.toJson(),
            ownerOnlyPermissions = true
        )
        endpointOverrideForTests?.invoke(descriptor)
        return true
    }

    fun initializeDefaultTrayIfSupported() {
        if (trayInitialized) return
        val supported = traySupportedForTests ?: (GenericUI.activeFactory?.supportsSystemTray == true)
        if (!supported) return
        trayInitialized = true
        val initialIcon = options.trayIcon?.takeIf { it.isNotBlank() }
        val initialMacosIcon = options.macosTrayIcon?.takeIf { it.isNotBlank() }
        val tray = SystemTrayIcon(
            iconPathOrUrl = initialIcon,
            tooltip = options.appName.ifBlank { null },
            macosIconPathOrUrl = initialMacosIcon
        )
        tray.onClick = if (worker.getEffectiveTrayMenu() != null) null else ({ openMainWindow(null) })
        managedTray = tray
    }

    override fun updateNotification(
        title: String,
        text: String,
        subText: String?,
        icon: ResourceID?,
        actions: List<ServiceWorkerNotificationAction>
    ) {
        throw ServiceWorkerOngoingNotificationUnsupportedException(backendName)
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
        if (!supportsSystemTray) {
            throw SystemTrayUnsupportedException(backendName)
        }
        val runUpdate = {
            if (!trayInitialized) {
                initializeDefaultTrayIfSupported()
            }
            val tray = managedTray
            if (tray != null) {
                val effectiveMacosIcon = macosIconPathOrUrl?.takeIf { it.isNotBlank() }
                    ?: options.macosTrayIcon?.takeIf { it.isNotBlank() }
                tray.macosIconPathOrUrl = effectiveMacosIcon
                val effectiveIcon = iconResource?.toResourceUri()
                    ?: iconPathOrUrl?.takeIf { it.isNotBlank() }
                    ?: options.trayIcon?.takeIf { it.isNotBlank() }
                if (effectiveIcon != null) {
                    tray.iconPathOrUrl = effectiveIcon
                }
                if (tooltip != null) {
                    tray.tooltip = tooltip
                }
                tray.badgeText = badgeText
                val effectiveMenu = menu ?: tray.contextMenu
                if (menu != null) {
                    tray.contextMenu = menu
                }
                tray.onClick = when {
                    onClick != null -> onClick
                    effectiveMenu != null -> null
                    else -> {
                        { openMainWindow(null) }
                    }
                }
            }
        }
        if (GenericUI.activeFactory != null) {
            GenericUiThreading.runOnUi(runUpdate)
        } else if (traySupportedForTests == true) {
            runUpdate()
        }
    }

    override fun openMainWindow(route: String?) {
        val openEnvBody = ServiceWorkerFrameCodec.encodeBinaryBody(
            ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                typeId = ServiceWorkerEnvelope.CONTROL_OPEN_MAIN_WINDOW,
                payloadJson = route.orEmpty()
            )
        )

        val activeConnections = collectActiveConnections()
        if (activeConnections.isNotEmpty()) {
            var delivered = false
            for (conn in activeConnections) {
                if (conn.sendBody(openEnvBody)) {
                    delivered = true
                }
            }
            if (delivered) {
                withMainWindowLock {
                    pendingSpawnDeadline = null
                    pendingOpenRoute = null
                    hasPendingOpenRoute = false
                }
                return
            }
        }

        var shouldSpawn = false
        withMainWindowLock {
            val deadline = pendingSpawnDeadline
            if (deadline != null && !deadline.hasPassedNow()) {
                pendingOpenRoute = route?.takeIf { it.isNotBlank() } ?: pendingOpenRoute
                hasPendingOpenRoute = true
                return@withMainWindowLock
            }

            pendingSpawnDeadline = TimeSource.Monotonic.markNow() + PENDING_MAIN_SPAWN_GRACE_MS.milliseconds
            pendingOpenRoute = null
            hasPendingOpenRoute = false
            shouldSpawn = true
        }

        if (!shouldSpawn) return

        val cmd = siblingCommandResolver(mainBinaryBaseName, swingMainClassFqn)
        if (cmd == null) {
            withMainWindowLock {
                pendingSpawnDeadline = null
            }
            return
        }
        val args = buildList {
            addAll(cmd.second)
            add("--open-window")
            if (!route.isNullOrBlank()) {
                add("--route=$route")
            }
        }
        if (!spawnLauncher(cmd.first, args, null)) {
            withMainWindowLock {
                pendingSpawnDeadline = null
            }
        }
    }

    override fun closeMainWindow() {
        val env = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_MESSAGE,
            typeId = ServiceWorkerEnvelope.CONTROL_CLOSE_MAIN_WINDOW,
            payloadJson = "null"
        )
        val encoded = ServiceWorkerFrameCodec.encodeBinaryBody(env)
        for (conn in withMainWindowLock { sessionsById.values.toList() }) {
            conn.sendBody(encoded)
        }
    }

    override fun requestLaunchFragment(request: ServiceWorkerFragmentLaunchRequest) {
        val launchBody = ServiceWorkerFrameCodec.encodeBinaryBody(
            ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                typeId = ServiceWorkerEnvelope.CONTROL_LAUNCH_FRAGMENT,
                payloadJson = request.toControlPayloadJson(),
                targetFragmentId = request.fragmentId
            )
        )

        val activeConnections = collectActiveConnections()
        if (activeConnections.isNotEmpty()) {
            var delivered = false
            for (conn in activeConnections) {
                if (conn.sendBody(launchBody)) {
                    delivered = true
                }
            }
            if (delivered) {
                withMainWindowLock {
                    pendingSpawnDeadline = null
                    pendingOpenRoute = null
                    hasPendingOpenRoute = false
                }
                return
            }
        }

        var shouldSpawn = false
        withMainWindowLock {
            val deadline = pendingSpawnDeadline
            if (deadline != null && !deadline.hasPassedNow()) {
                pendingFragmentLaunches.add(request)
                return@withMainWindowLock
            }
            pendingSpawnDeadline = TimeSource.Monotonic.markNow() + PENDING_MAIN_SPAWN_GRACE_MS.milliseconds
            shouldSpawn = true
        }

        if (!shouldSpawn) return

        val cmd = siblingCommandResolver(mainBinaryBaseName, swingMainClassFqn)
        if (cmd == null) {
            withMainWindowLock {
                pendingSpawnDeadline = null
            }
            return
        }
        val args = buildList {
            addAll(cmd.second)
            add("--open-window")
            if (request.fragmentId.isNotBlank()) {
                add("--fragment=${request.fragmentId}")
            }
            val route = request.route?.takeIf { it.isNotBlank() }
            if (route != null) {
                add("--route=$route")
            }
            add("--open-additional-window=${request.openAdditionalWindow}")
            if (request.clearTask) {
                add("--clear-task=true")
            }
            val fragArgs = request.arguments
            if (fragArgs != null && fragArgs.keys().isNotEmpty()) {
                add("--fragment-args-b64=${fragArgs.toBase64Url()}")
            }
        }
        if (!spawnLauncher(cmd.first, args, null)) {
            withMainWindowLock {
                pendingSpawnDeadline = null
            }
        }
    }

    override fun requestCloseFragment(fragmentId: String, instanceId: String?) {
        withMainWindowLock {
            pendingFragmentLaunches.removeAll { it.fragmentId == fragmentId }
        }
        val closeBody = ServiceWorkerFrameCodec.encodeBinaryBody(
            ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                typeId = ServiceWorkerEnvelope.CONTROL_CLOSE_FRAGMENT,
                payloadJson = ServiceWorkerFragmentLaunchRequest.encodeCloseControlPayload(fragmentId, instanceId),
                targetFragmentId = fragmentId
            )
        )
        for (conn in collectActiveConnections()) {
            conn.sendBody(closeBody)
        }
    }

    override fun stopSelf() {
        shutdown()
        stopEventLoop()
    }

    override fun exitApplication(exitCode: Int) {
        val exitEnv = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_MESSAGE,
            typeId = ServiceWorkerEnvelope.CONTROL_EXIT_APP,
            payloadJson = exitCode.toString()
        )
        val body = ServiceWorkerFrameCodec.encodeBinaryBody(exitEnv)
        for (conn in withMainWindowLock { sessionsById.values.toList() }) {
            conn.sendBody(body)
        }
        stopSelf()
    }

    override fun isAutostartEnabled(): Boolean =
        DesktopOsHelpers.isAutostartEnabled(options.appId, options.appName)

    override fun setAutostartEnabled(enabled: Boolean): Boolean {
        val exePath = DesktopOsHelpers.currentExecutablePath() ?: return false
        return DesktopOsHelpers.setAutostartEnabled(
            appId = options.appId,
            appName = options.appName,
            targetExecutablePath = exePath,
            args = emptyList(),
            enabled = enabled
        )
    }

    override suspend fun sendToClient(clientId: String, envelope: ServiceWorkerEnvelope): Boolean {
        val conn = withMainWindowLock { sessionsById[clientId] } ?: return false
        if (conn.isClosed) {
            withMainWindowLock { sessionsById.remove(clientId) }
            return false
        }
        val body = ServiceWorkerFrameCodec.encodeBinaryBody(envelope)
        return conn.sendBody(body)
    }

    override suspend fun broadcastEnvelope(envelope: ServiceWorkerEnvelope): Int {
        val body = ServiceWorkerFrameCodec.encodeBinaryBody(envelope)
        var delivered = 0
        for ((cid, conn) in withMainWindowLock { sessionsById.entries.toList() }) {
            if (!conn.isClosed && conn.sendBody(body)) {
                delivered++
            } else {
                withMainWindowLock { sessionsById.remove(cid) }
            }
        }
        return delivered
    }

    override fun disconnectClient(clientId: String) {
        val conn = withMainWindowLock { sessionsById.remove(clientId) }
        conn?.close()
    }

    fun shutdown() {
        if (stopped) return
        stopped = true
        val byeBody = ServiceWorkerFrameCodec.encodeBinaryBody(
            ServiceWorkerEnvelope(kind = ServiceWorkerEnvelope.KIND_BYE)
        )
        val conns = withMainWindowLock {
            pendingFragmentLaunches.clear()
            val list = sessionsById.values.toList()
            sessionsById.clear()
            list
        }
        for (conn in conns) {
            if (!conn.isClosed) {
                conn.sendBody(byeBody)
                conn.close()
            }
        }
        managedTray?.dispose()
        managedTray = null
        server.close()
        DesktopOsHelpers.deleteFileIfExists(endpointFilePath)
        lockRelease()
        ioScope.cancel()
    }

    private fun collectActiveConnections(): List<DesktopIpcConnection> = withMainWindowLock {
        val list = mutableListOf<DesktopIpcConnection>()
        for ((cid, conn) in sessionsById.entries.toList()) {
            if (conn.isClosed) {
                sessionsById.remove(cid)
            } else {
                list.add(conn)
            }
        }
        list
    }

    private fun handleAcceptedConnection(conn: DesktopIpcConnection) {
        ioScope.launch {
            var assignedClientId: String? = null
            try {
                val authBytes = conn.receiveBody(timeoutMs = 3_000)
                val authEnv = authBytes?.let { ServiceWorkerFrameCodec.decodeBinaryBody(it) }
                if (authEnv == null ||
                    authEnv.kind != ServiceWorkerEnvelope.KIND_AUTH ||
                    !constantTimeEquals(authEnv.payloadJson, authToken)
                ) {
                    val errBody = ServiceWorkerFrameCodec.encodeBinaryBody(
                        ServiceWorkerEnvelope(
                            kind = ServiceWorkerEnvelope.KIND_AUTH_ERR,
                            error = "Invalid or missing ServiceWorker IPC authentication token."
                        )
                    )
                    conn.sendBody(errBody)
                    conn.close()
                    return@launch
                }

                val cid = authEnv.clientId.trim().ifEmpty { "client-${nextClientSeq++}" }
                val isControlClient = cid.startsWith(DESKTOP_CONTROL_CLIENT_PREFIX)
                if (!isControlClient) {
                    assignedClientId = cid
                    withMainWindowLock {
                        sessionsById[cid] = conn
                    }
                }
                val okBody = ServiceWorkerFrameCodec.encodeBinaryBody(
                    ServiceWorkerEnvelope(
                        kind = ServiceWorkerEnvelope.KIND_AUTH_OK,
                        clientId = cid
                    )
                )
                if (!conn.sendBody(okBody)) {
                    if (!isControlClient) {
                        withMainWindowLock {
                            sessionsById.remove(cid)
                        }
                    }
                    conn.close()
                    return@launch
                }
                if (!isControlClient) {
                    worker.dispatchClientConnected(cid)
                    var routeToDeliver: String? = null
                    var deliverQueuedOpen = false
                    var queuedFragmentLaunches: List<ServiceWorkerFragmentLaunchRequest> = emptyList()
                    withMainWindowLock {
                        pendingSpawnDeadline = null
                        if (hasPendingOpenRoute) {
                            deliverQueuedOpen = true
                            routeToDeliver = pendingOpenRoute
                            hasPendingOpenRoute = false
                            pendingOpenRoute = null
                        }
                        if (pendingFragmentLaunches.isNotEmpty()) {
                            queuedFragmentLaunches = pendingFragmentLaunches.toList()
                            pendingFragmentLaunches.clear()
                        }
                    }
                    if (deliverQueuedOpen) {
                        conn.sendBody(
                            ServiceWorkerFrameCodec.encodeBinaryBody(
                                ServiceWorkerEnvelope(
                                    kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                                    typeId = ServiceWorkerEnvelope.CONTROL_OPEN_MAIN_WINDOW,
                                    payloadJson = routeToDeliver.orEmpty()
                                )
                            )
                        )
                    }
                    for (launchReq in queuedFragmentLaunches) {
                        conn.sendBody(
                            ServiceWorkerFrameCodec.encodeBinaryBody(
                                ServiceWorkerEnvelope(
                                    kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                                    typeId = ServiceWorkerEnvelope.CONTROL_LAUNCH_FRAGMENT,
                                    payloadJson = launchReq.toControlPayloadJson(),
                                    targetFragmentId = launchReq.fragmentId
                                )
                            )
                        )
                    }
                }

                while (!stopped && !conn.isClosed) {
                    val frameBytes = conn.receiveBody(timeoutMs = 250) ?: continue
                    val env = ServiceWorkerFrameCodec.decodeBinaryBody(frameBytes) ?: continue
                    if (env.kind == ServiceWorkerEnvelope.KIND_PING) {
                        conn.sendBody(
                            ServiceWorkerFrameCodec.encodeBinaryBody(
                                ServiceWorkerEnvelope(kind = ServiceWorkerEnvelope.KIND_PING, correlationId = env.correlationId)
                            )
                        )
                        continue
                    }
                    if (env.kind == ServiceWorkerEnvelope.KIND_MESSAGE &&
                        env.typeId == ServiceWorkerEnvelope.CONTROL_REQUEST_ACTIVATE_MAIN_WINDOW
                    ) {
                        val route = env.payloadJson.trim()
                            .removeSurrounding("\"")
                            .takeIf { it.isNotEmpty() && it != "null" }
                        openMainWindow(route)
                        continue
                    }
                    if (env.kind == ServiceWorkerEnvelope.KIND_MESSAGE &&
                        env.typeId == ServiceWorkerEnvelope.CONTROL_QUERY_SHOULD_OPEN_MAIN_WINDOW
                    ) {
                        val shouldOpen = worker.onMainAppLaunchedWhileRunning()
                        conn.sendBody(
                            ServiceWorkerFrameCodec.encodeBinaryBody(
                                ServiceWorkerEnvelope(
                                    kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                                    correlationId = env.correlationId,
                                    clientId = cid,
                                    typeId = ServiceWorkerEnvelope.CONTROL_QUERY_SHOULD_OPEN_MAIN_WINDOW,
                                    payloadJson = if (shouldOpen) "true" else "false"
                                )
                            )
                        )
                        continue
                    }
                    if (env.kind == ServiceWorkerEnvelope.KIND_MESSAGE &&
                        env.typeId == ServiceWorkerEnvelope.CONTROL_REQUEST_LAUNCH_FRAGMENT
                    ) {
                        val launchReq = ServiceWorkerFragmentLaunchRequest.fromControlPayloadJson(env.payloadJson)
                        if (launchReq != null) {
                            requestLaunchFragment(launchReq)
                        }
                        continue
                    }
                    if (!isControlClient) {
                        worker.dispatchIncomingEnvelope(cid, env)
                    }
                    if (env.kind == ServiceWorkerEnvelope.KIND_BYE) {
                        break
                    }
                }
            } finally {
                val cid = assignedClientId
                if (cid != null) {
                    withMainWindowLock {
                        sessionsById.remove(cid)
                    }
                    worker.dispatchClientDisconnected(cid)
                }
                conn.close()
            }
        }
    }

    private inline fun <T> withMainWindowLock(block: () -> T): T {
        while (!mainWindowStateLock.compareAndSet(expectedValue = false, newValue = true)) {
            // Brief spin around microsecond in-memory map/state updates.
        }
        return try {
            block()
        } finally {
            mainWindowStateLock.store(false)
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val cleanA = a.removeSurrounding("\"")
        if (cleanA.length != b.length) return false
        var diff = 0
        for (i in cleanA.indices) {
            diff = diff or (cleanA[i].code xor b[i].code)
        }
        return diff == 0
    }
}
