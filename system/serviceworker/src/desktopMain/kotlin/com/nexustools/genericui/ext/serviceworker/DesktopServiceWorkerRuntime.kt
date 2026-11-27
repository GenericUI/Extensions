package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.ipc.IpcClient

/**
 * Shared Desktop [ServiceWorkerPlatformRuntime] across Windows, macOS, GTK, KDE, and Swing.
 */
internal class DesktopServiceWorkerRuntime(
    private val defaultBackendTarget: BackendTarget
) : ServiceWorkerPlatformRuntime {

    override val isSupported: Boolean = true
    override val supportsMultipleWindows: Boolean = true
    override val supportsFragmentLaunch: Boolean = true
    override val supportsDialogFragments: Boolean = true
    override val supportsSystemTray: Boolean
        get() = GenericUI.activeFactory?.supportsSystemTray ?: true
    override val supportsOngoingNotification: Boolean = false
    override val supportsAutostart: Boolean = true

    private var currentOptions: ServiceWorkerOptions = ServiceWorkerOptions()
    private val channelClient = DesktopServiceWorkerChannelClient()
    private var activeHost: DesktopServiceWorkerHost? = null
    private var activeWorkerInstance: ServiceWorker? = null
    private var heldMainAppLockKey: String? = null

    override fun configure(options: ServiceWorkerOptions) {
        currentOptions = options
    }

    override fun isClientConnected(): Boolean = channelClient.isConnected

    override fun activeWorker(): ServiceWorker? = activeWorkerInstance

    override fun currentBackendTarget(): BackendTarget =
        GenericUI.activeFactory?.backendTarget ?: defaultBackendTarget

    override fun prepareDesktopMainLaunch(
        args: Array<String>,
        options: ServiceWorkerOptions,
        workerBinaryBaseName: String
    ): Boolean {
        currentOptions = options
        val explicitWindowFlag = args.any {
            it == "--open-window" ||
                it == "--settings-only" ||
                it.startsWith("--fragment=") ||
                it.startsWith("--route=")
        }
        val shouldOpenWindow = options.openWindowOnLaunch || explicitWindowFlag
        if (!shouldOpenWindow) {
            if (options.hasWorker) {
                val endpointPath = resolveEndpointFilePath(options.appId)
                val existing = DesktopEndpointDescriptor.parse(DesktopOsHelpers.readTextFileOrNull(endpointPath))
                val shouldOpenFromRunningWorker = existing?.let { queryShouldOpenMainWindowControl(it) }
                if (shouldOpenFromRunningWorker == null) {
                    ensureWorkerProcessRunning(options, workerBinaryBaseName)
                    return false
                }
                if (!shouldOpenFromRunningWorker) {
                    return false
                }
            } else {
                return false
            }
        }

        val appLockKey = mainAppLockKey(options.appId)
        val appLockPath = resolveRuntimeFilePath(options.appId, "mainapp.lock")
        if (!DesktopOsHelpers.acquireSingleInstanceLock(appLockKey, appLockPath)) {
            if (options.hasWorker) {
                ensureWorkerProcessRunning(options, workerBinaryBaseName)
            }
            val fragmentLaunchReq = parseFragmentLaunchRequestFromArgs(args)
            if (fragmentLaunchReq != null) {
                sendLaunchFragmentControl(options.appId, fragmentLaunchReq)
            } else {
                val route = parseRouteArg(args)
                sendActivateMainWindowControl(options.appId, route)
            }
            return false
        }

        heldMainAppLockKey = appLockKey
        DesktopOsHelpers.registerShutdownHook {
            releaseMainAppLock()
        }
        if (options.hasWorker) {
            ensureWorkerProcessRunning(options, workerBinaryBaseName)
        }
        return true
    }

    override fun runDesktopWorkerMain(
        args: Array<String>,
        options: ServiceWorkerOptions,
        workerFactory: () -> ServiceWorker,
        backendInit: () -> Unit,
        eventLoopRunner: () -> Unit,
        eventLoopStopper: () -> Unit,
        mainBinaryBaseName: String
    ): Int {
        currentOptions = options
        val runtimeDir = DesktopOsHelpers.runtimeDataDirectory(options.appId)
        DesktopOsHelpers.ensureDirectoryExists(runtimeDir)
        val sep = if (runtimeDir.contains('\\')) '\\' else '/'
        val lockPath = "${runtimeDir.trimEnd('/', '\\')}${sep}serviceworker.lock"
        val endpointPath = "${runtimeDir.trimEnd('/', '\\')}${sep}serviceworker-endpoint.json"
        val lockKey = workerLockKey(options.appId)

        if (!DesktopOsHelpers.acquireSingleInstanceLock(lockKey, lockPath)) {
            return 0
        }

        backendInit()
        val worker = workerFactory()
        val host = DesktopServiceWorkerHost(
            options = options,
            worker = worker,
            endpointFilePath = endpointPath,
            mainBinaryBaseName = mainBinaryBaseName,
            swingMainClassFqn = deriveMainClassFromWorkerClass(options.swingWorkerMainClass),
            stopEventLoop = eventLoopStopper,
            lockRelease = { DesktopOsHelpers.releaseSingleInstanceLock(lockKey) }
        )
        activeWorkerInstance = worker
        activeHost = host
        if (!host.start()) {
            host.shutdown()
            activeWorkerInstance = null
            activeHost = null
            return 1
        }
        host.initializeDefaultTrayIfSupported()
        worker.attachAndStart(host, options)
        try {
            eventLoopRunner()
        } finally {
            worker.performStop()
            host.shutdown()
            activeWorkerInstance = null
            activeHost = null
        }
        return 0
    }

    override suspend fun connectClient(
        options: ServiceWorkerOptions,
        onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
        onConnectionChanged: (Boolean) -> Unit
    ): Boolean {
        currentOptions = options
        val endpointFile = resolveEndpointFilePath(options.appId)
        var descriptor = DesktopEndpointDescriptor.parse(DesktopOsHelpers.readTextFileOrNull(endpointFile))
        if (descriptor != null && channelClient.connectToEndpoint(descriptor, onIncomingEnvelope, onConnectionChanged)) {
            return true
        }
        if (descriptor != null) {
            // Stale endpoint file from dead process; remove before spawning fresh worker
            DesktopOsHelpers.deleteFileIfExists(endpointFile)
        }
        if (options.hasWorker) {
            ensureWorkerProcessRunning(options, options.resolvedWorkerBinaryBaseName())
            descriptor = DesktopEndpointDescriptor.parse(DesktopOsHelpers.readTextFileOrNull(endpointFile))
            if (descriptor != null && channelClient.connectToEndpoint(descriptor, onIncomingEnvelope, onConnectionChanged)) {
                return true
            }
        }
        onConnectionChanged(false)
        return false
    }

    override suspend fun disconnectClient() {
        channelClient.disconnect()
    }

    override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean =
        channelClient.sendEnvelope(envelope)

    override fun ensureBackgroundStarted(
        options: ServiceWorkerOptions,
        workerFactory: (() -> ServiceWorker)?
    ): Boolean {
        currentOptions = options
        if (activeHost != null) return true
        return ensureWorkerProcessRunning(options, options.resolvedWorkerBinaryBaseName())
    }

    override fun isAutostartEnabled(options: ServiceWorkerOptions): Boolean =
        DesktopOsHelpers.isAutostartEnabled(options.appId, options.appName)

    override fun setAutostartEnabled(options: ServiceWorkerOptions, enabled: Boolean): Boolean {
        val cmd = DesktopOsHelpers.resolveSiblingBinaryCommand(
            siblingBaseName = options.resolvedWorkerBinaryBaseName(),
            jvmMainClassFqn = options.swingWorkerMainClass
        )
        val exePath = cmd?.first ?: DesktopOsHelpers.currentExecutablePath() ?: return false
        val args = cmd?.second ?: emptyList()
        return DesktopOsHelpers.setAutostartEnabled(
            appId = options.appId,
            appName = options.appName,
            targetExecutablePath = exePath,
            args = args,
            enabled = enabled
        )
    }

    override fun openMainWindowFromHost(options: ServiceWorkerOptions, route: String?) {
        activeHost?.openMainWindow(route)
    }

    override fun closeMainWindowFromHost(options: ServiceWorkerOptions) {
        activeHost?.closeMainWindow()
    }

    override fun onMainAppShutdown() {
        releaseMainAppLock()
    }

    override fun resetForTests() {
        channelClient.disconnect()
        activeWorkerInstance?.performStop()
        activeHost?.shutdown()
        activeWorkerInstance = null
        activeHost = null
        releaseMainAppLock()
        DesktopOsHelpers.releaseSingleInstanceLock()
        currentOptions = ServiceWorkerOptions()
    }

    private fun releaseMainAppLock() {
        val key = heldMainAppLockKey ?: return
        heldMainAppLockKey = null
        DesktopOsHelpers.releaseSingleInstanceLock(key)
    }

    private fun queryShouldOpenMainWindowControl(descriptor: DesktopEndpointDescriptor): Boolean? {
        val conn = IpcClient.connect(descriptor.toChannelAddress(), timeoutMs = 1_500) ?: return null
        return try {
            val clientId = "${DESKTOP_CONTROL_CLIENT_PREFIX}query-${DesktopOsHelpers.currentProcessId()}"
            val authEnv = ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_AUTH,
                clientId = clientId,
                payload = PreferenceElement.ofString(descriptor.token)
            )
            if (!conn.sendPacket(DesktopIpcPacketMapper.toIpcPacket(authEnv))) {
                return null
            }
            val authReplyPacket = conn.receivePacket(timeoutMs = 1_500)
            val authReply = authReplyPacket?.let { DesktopIpcPacketMapper.fromIpcPacket(it) }
            if (authReply == null || authReply.kind != ServiceWorkerEnvelope.KIND_AUTH_OK) {
                return null
            }
            val queryEnv = ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                clientId = clientId,
                typeId = ServiceWorkerEnvelope.CONTROL_QUERY_SHOULD_OPEN_MAIN_WINDOW,
                payload = PreferenceElement.Null
            )
            if (!conn.sendPacket(DesktopIpcPacketMapper.toIpcPacket(queryEnv))) {
                return null
            }
            val replyPacket = conn.receivePacket(timeoutMs = 1_500)
            val replyEnv = replyPacket?.let { DesktopIpcPacketMapper.fromIpcPacket(it) }
            conn.sendPacket(
                DesktopIpcPacketMapper.toIpcPacket(
                    ServiceWorkerEnvelope(
                        kind = ServiceWorkerEnvelope.KIND_BYE,
                        clientId = clientId
                    )
                )
            )
            if (replyEnv == null ||
                replyEnv.kind != ServiceWorkerEnvelope.KIND_MESSAGE ||
                replyEnv.typeId != ServiceWorkerEnvelope.CONTROL_QUERY_SHOULD_OPEN_MAIN_WINDOW
            ) {
                return null
            }
            replyEnv.payload.asBoolean(false)
        } finally {
            conn.close()
        }
    }

    private fun sendActivateMainWindowControl(appId: String, route: String): Boolean =
        sendControlFrame(
            appId = appId,
            typeId = ServiceWorkerEnvelope.CONTROL_REQUEST_ACTIVATE_MAIN_WINDOW,
            payload = PreferenceElement.ofString(route)
        )

    private fun sendLaunchFragmentControl(
        appId: String,
        request: ServiceWorkerFragmentLaunchRequest
    ): Boolean = sendControlFrame(
        appId = appId,
        typeId = ServiceWorkerEnvelope.CONTROL_REQUEST_LAUNCH_FRAGMENT,
        payload = request.toControlPayloadElement()
    )

    private fun sendControlFrame(appId: String, typeId: String, payload: PreferenceElement): Boolean {
        val endpointPath = resolveEndpointFilePath(appId)
        val descriptor = DesktopEndpointDescriptor.parse(DesktopOsHelpers.readTextFileOrNull(endpointPath))
            ?: return false
        val conn = IpcClient.connect(descriptor.toChannelAddress(), timeoutMs = 4_000) ?: return false
        return try {
            val clientId = "$DESKTOP_CONTROL_CLIENT_PREFIX${DesktopOsHelpers.currentProcessId()}"
            val authEnv = ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_AUTH,
                clientId = clientId,
                payload = PreferenceElement.ofString(descriptor.token)
            )
            if (!conn.sendPacket(DesktopIpcPacketMapper.toIpcPacket(authEnv))) {
                return false
            }
            val authReplyPacket = conn.receivePacket(timeoutMs = 3_000)
            val authReply = authReplyPacket?.let { DesktopIpcPacketMapper.fromIpcPacket(it) }
            if (authReply == null || authReply.kind != ServiceWorkerEnvelope.KIND_AUTH_OK) {
                return false
            }
            val controlEnv = ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                clientId = clientId,
                typeId = typeId,
                payload = payload
            )
            val sent = conn.sendPacket(DesktopIpcPacketMapper.toIpcPacket(controlEnv))
            if (sent) {
                conn.sendPacket(
                    DesktopIpcPacketMapper.toIpcPacket(
                        ServiceWorkerEnvelope(
                            kind = ServiceWorkerEnvelope.KIND_BYE,
                            clientId = clientId
                        )
                    )
                )
            }
            sent
        } finally {
            conn.close()
        }
    }

    private fun parseFragmentLaunchRequestFromArgs(args: Array<String>): ServiceWorkerFragmentLaunchRequest? {
        val hasFragmentArg = args.any {
            it.startsWith("--fragment=") ||
                it.startsWith("--fragment-args-b64=") ||
                it.startsWith("--open-additional-window=") ||
                it.startsWith("--clear-task=")
        }
        if (!hasFragmentArg) return null
        val bundle = try {
            FragmentBundle.fromCommandLineArgs(args)
        } catch (e: IllegalArgumentException) {
            throw ServiceWorkerIpcException("Malformed desktop fragment launch arguments: ${e.message}", e)
        }
        val fragmentId = bundle.getString(FragmentBundle.KEY_LAUNCH_FRAGMENT_ID)?.trim().orEmpty()
        val route = bundle.getString(FragmentBundle.KEY_LAUNCH_ROUTE)?.trim()?.takeIf { it.isNotEmpty() }
        if (fragmentId.isEmpty() && route == null) return null
        val openAdditionalWindow = bundle.getBoolean(FragmentBundle.KEY_LAUNCH_OPEN_ADDITIONAL_WINDOW, false)
        val clearTask = args.any { it.equals("--clear-task=true", ignoreCase = true) || it == "--clear-task" }
        return ServiceWorkerFragmentLaunchRequest(
            fragmentId = fragmentId.ifEmpty { route.orEmpty() },
            route = route,
            arguments = bundle,
            openAdditionalWindow = openAdditionalWindow,
            clearTask = clearTask,
            bringToFront = true
        )
    }

    private fun ensureWorkerProcessRunning(
        options: ServiceWorkerOptions,
        workerBinaryBaseName: String
    ): Boolean {
        val endpointPath = resolveEndpointFilePath(options.appId)
        val existing = DesktopEndpointDescriptor.parse(DesktopOsHelpers.readTextFileOrNull(endpointPath))
        if (existing != null && probeEndpointAlive(existing)) {
            return true
        }
        if (existing != null) {
            DesktopOsHelpers.deleteFileIfExists(endpointPath)
        }
        if (DesktopOsHelpers.tryWakeRegisteredBackgroundService(options.appId)) {
            if (waitForLiveEndpoint(endpointPath, timeoutMs = 1_500L)) {
                return true
            }
        }
        val cmd = DesktopOsHelpers.resolveSiblingBinaryCommand(
            siblingBaseName = workerBinaryBaseName,
            jvmMainClassFqn = options.swingWorkerMainClass
        ) ?: return false
        if (!DesktopOsHelpers.spawnDetachedProcess(cmd.first, cmd.second, null)) {
            return false
        }
        return waitForLiveEndpoint(endpointPath, timeoutMs = 3_000L)
    }

    private fun waitForLiveEndpoint(endpointPath: String, timeoutMs: Long): Boolean {
        var waited = 0L
        while (waited < timeoutMs) {
            val desc = DesktopEndpointDescriptor.parse(DesktopOsHelpers.readTextFileOrNull(endpointPath))
            if (desc != null && probeEndpointAlive(desc)) {
                return true
            }
            DesktopOsHelpers.sleepMillis(50L)
            waited += 50L
        }
        return false
    }

    private fun probeEndpointAlive(descriptor: DesktopEndpointDescriptor): Boolean {
        val probe = IpcClient.connect(descriptor.toChannelAddress(), timeoutMs = 400) ?: return false
        return try {
            val clientId = "${DESKTOP_CONTROL_CLIENT_PREFIX}probe-${DesktopOsHelpers.currentProcessId()}"
            val authEnv = ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_AUTH,
                clientId = clientId,
                payload = PreferenceElement.ofString(descriptor.token)
            )
            if (!probe.sendPacket(DesktopIpcPacketMapper.toIpcPacket(authEnv))) {
                return false
            }
            val replyPacket = probe.receivePacket(timeoutMs = 400)
            val replyEnv = replyPacket?.let { DesktopIpcPacketMapper.fromIpcPacket(it) }
            val authOk = replyEnv != null && replyEnv.kind == ServiceWorkerEnvelope.KIND_AUTH_OK
            if (authOk) {
                probe.sendPacket(
                    DesktopIpcPacketMapper.toIpcPacket(
                        ServiceWorkerEnvelope(
                            kind = ServiceWorkerEnvelope.KIND_BYE,
                            clientId = clientId
                        )
                    )
                )
            }
            authOk
        } finally {
            probe.close()
        }
    }

    private fun resolveEndpointFilePath(appId: String): String =
        resolveRuntimeFilePath(appId, "serviceworker-endpoint.json")

    private fun resolveRuntimeFilePath(appId: String, fileName: String): String {
        val dir = DesktopOsHelpers.runtimeDataDirectory(appId)
        DesktopOsHelpers.ensureDirectoryExists(dir)
        val sep = if (dir.contains('\\')) '\\' else '/'
        return "${dir.trimEnd('/', '\\')}$sep$fileName"
    }

    private fun sanitizedAppId(appId: String): String =
        appId.trim().ifEmpty { "com.nexustools.genericui.app" }.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun workerLockKey(appId: String): String =
        "GenericUI-ServiceWorker-${sanitizedAppId(appId)}"

    private fun mainAppLockKey(appId: String): String =
        "GenericUI-MainApp-${sanitizedAppId(appId)}"

    private fun parseRouteArg(args: Array<String>): String =
        args.firstOrNull { it.startsWith("--route=") }?.substringAfter("--route=").orEmpty()

    private fun deriveMainClassFromWorkerClass(workerMainClass: String): String {
        if (workerMainClass.isBlank()) return ""
        val pkg = workerMainClass.substringBeforeLast('.', "")
        return if (pkg.isEmpty()) "GeneratedSwingMain" else "$pkg.GeneratedSwingMain"
    }
}
