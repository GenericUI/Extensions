package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.system.SystemTrayIcon
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.threading.GenericUiThreading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Worker-side desktop host owning the loopback `127.0.0.1:0` [DesktopLoopbackTcpServer],
 * atomic `serviceworker-endpoint.json` discovery file, token authentication, [SystemTrayIcon],
 * and main-window / autostart / shutdown coordination.
 */
internal class DesktopServiceWorkerHost(
    private val options: ServiceWorkerOptions,
    private val worker: ServiceWorker,
    private val endpointFilePath: String,
    private val mainBinaryBaseName: String,
    private val swingMainClassFqn: String,
    private val stopEventLoop: () -> Unit,
    private val lockRelease: () -> Unit,
    private val endpointOverrideForTests: ((DesktopEndpointDescriptor) -> Unit)? = null
) : ServiceWorkerRuntimeHost {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = DesktopLoopbackTcpServer()
    private val sessionsById = linkedMapOf<String, DesktopTcpConnection>()
    private val authToken: String = DesktopOsHelpers.generateRandomHexToken(32)
    private var nextClientSeq: Int = 1

    @Volatile
    private var stopped: Boolean = false

    private var managedTray: SystemTrayIcon? = null
    private var trayInitialized: Boolean = false

    val boundPort: Int
        get() = server.port

    val token: String
        get() = authToken

    fun start(): Boolean {
        val started = server.start { conn ->
            handleAcceptedConnection(conn)
        }
        if (!started || server.port <= 0) {
            return false
        }
        val descriptor = DesktopEndpointDescriptor(
            port = server.port,
            token = authToken,
            pid = DesktopOsHelpers.currentProcessId(),
            appId = options.appId
        )
        val parentDir = endpointFilePath.substringBeforeLast('/', "").ifEmpty {
            endpointFilePath.substringBeforeLast('\\', "")
        }
        if (parentDir.isNotEmpty()) {
            DesktopOsHelpers.ensureDirectoryExists(parentDir)
        }
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
        if (GenericUI.activeFactory?.supportsSystemTray != true) return
        trayInitialized = true
        val initialIcon = options.trayIcon?.takeIf { it.isNotBlank() }
        val tray = SystemTrayIcon(
            iconPathOrUrl = initialIcon,
            tooltip = options.appName.ifBlank { null }
        )
        tray.onClick = { openMainWindow(null) }
        managedTray = tray
    }

    override fun updateNotification(
        title: String,
        text: String,
        subText: String?,
        icon: ResourceID?,
        actions: List<ServiceWorkerNotificationAction>
    ) {
        // Ongoing notifications are Android-specific; desktop uses SystemTrayIcon.
    }

    override fun updateTray(
        iconPathOrUrl: String?,
        iconResource: ResourceID?,
        tooltip: String?,
        badgeText: String?,
        menu: ContextMenu?,
        onClick: (() -> Unit)?
    ) {
        if (GenericUI.activeFactory?.supportsSystemTray != true) return
        val runUpdate = {
            if (!trayInitialized) {
                initializeDefaultTrayIfSupported()
            }
            val tray = managedTray
            if (tray != null) {
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
                if (menu != null) {
                    tray.contextMenu = menu
                }
                tray.onClick = onClick ?: { openMainWindow(null) }
            }
        }
        if (GenericUI.activeFactory != null) {
            GenericUiThreading.runOnUi(runUpdate)
        }
    }

    override fun openMainWindow(route: String?) {
        val cmd = DesktopOsHelpers.resolveSiblingBinaryCommand(mainBinaryBaseName, swingMainClassFqn) ?: return
        val args = buildList {
            addAll(cmd.second)
            add("--open-window")
            if (!route.isNullOrBlank()) {
                add("--route=$route")
            }
        }
        DesktopOsHelpers.spawnDetachedProcess(cmd.first, args, null)
    }

    override fun closeMainWindow() {
        val env = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_MESSAGE,
            typeId = "__genericui.sw.control.closeMainWindow",
            payloadJson = "null"
        )
        val encoded = ServiceWorkerFrameCodec.encodeBinaryBody(env)
        for (conn in sessionsById.values.toList()) {
            conn.sendBody(encoded)
        }
    }

    override fun stopSelf() {
        shutdown()
        stopEventLoop()
    }

    override fun exitApplication(exitCode: Int) {
        val exitEnv = ServiceWorkerEnvelope(
            kind = ServiceWorkerEnvelope.KIND_MESSAGE,
            typeId = "__genericui.sw.control.exitApp",
            payloadJson = exitCode.toString()
        )
        val body = ServiceWorkerFrameCodec.encodeBinaryBody(exitEnv)
        for (conn in sessionsById.values.toList()) {
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
        val conn = sessionsById[clientId] ?: return false
        if (conn.isClosed) {
            sessionsById.remove(clientId)
            return false
        }
        val body = ServiceWorkerFrameCodec.encodeBinaryBody(envelope)
        return conn.sendBody(body)
    }

    override suspend fun broadcastEnvelope(envelope: ServiceWorkerEnvelope): Int {
        val body = ServiceWorkerFrameCodec.encodeBinaryBody(envelope)
        var delivered = 0
        for ((cid, conn) in sessionsById.entries.toList()) {
            if (!conn.isClosed && conn.sendBody(body)) {
                delivered++
            } else {
                sessionsById.remove(cid)
            }
        }
        return delivered
    }

    override fun disconnectClient(clientId: String) {
        val conn = sessionsById.remove(clientId)
        conn?.close()
    }

    fun shutdown() {
        if (stopped) return
        stopped = true
        val byeBody = ServiceWorkerFrameCodec.encodeBinaryBody(
            ServiceWorkerEnvelope(kind = ServiceWorkerEnvelope.KIND_BYE)
        )
        for (conn in sessionsById.values.toList()) {
            runCatching { conn.sendBody(byeBody) }
            runCatching { conn.close() }
        }
        sessionsById.clear()
        runCatching { managedTray?.dispose() }
        managedTray = null
        runCatching { server.close() }
        DesktopOsHelpers.deleteFileIfExists(endpointFilePath)
        lockRelease()
        ioScope.cancel()
    }

    private fun handleAcceptedConnection(conn: DesktopTcpConnection) {
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
                assignedClientId = cid
                sessionsById[cid] = conn
                val okBody = ServiceWorkerFrameCodec.encodeBinaryBody(
                    ServiceWorkerEnvelope(
                        kind = ServiceWorkerEnvelope.KIND_AUTH_OK,
                        clientId = cid
                    )
                )
                if (!conn.sendBody(okBody)) {
                    sessionsById.remove(cid)
                    conn.close()
                    return@launch
                }
                worker.dispatchClientConnected(cid)

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
                    worker.dispatchIncomingEnvelope(cid, env)
                    if (env.kind == ServiceWorkerEnvelope.KIND_BYE) {
                        break
                    }
                }
            } finally {
                val cid = assignedClientId
                if (cid != null) {
                    sessionsById.remove(cid)
                    worker.dispatchClientDisconnected(cid)
                }
                conn.close()
            }
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
