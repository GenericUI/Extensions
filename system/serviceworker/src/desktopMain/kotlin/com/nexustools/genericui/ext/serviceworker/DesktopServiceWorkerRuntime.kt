package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.system.BackendTarget

/**
 * Shared Desktop [ServiceWorkerPlatformRuntime] across Windows, macOS, GTK, Qt, and Swing.
 */
internal class DesktopServiceWorkerRuntime(
    private val defaultBackendTarget: BackendTarget
) : ServiceWorkerPlatformRuntime {

    override val isSupported: Boolean = true

    override val isAutostartSupported: Boolean = true

    private var currentOptions: ServiceWorkerOptions = ServiceWorkerOptions()
    private val channelClient = DesktopServiceWorkerChannelClient()
    private var activeHost: DesktopServiceWorkerHost? = null
    private var activeWorkerInstance: ServiceWorker? = null

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
        if (options.hasWorker) {
            ensureWorkerProcessRunning(options, workerBinaryBaseName)
        }
        val explicitWindowFlag = args.any { it == "--open-window" || it == "--settings-only" }
        return options.openWindowOnLaunch || explicitWindowFlag
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
        val lockKey = "GenericUI-ServiceWorker-${options.appId.replace(Regex("[^A-Za-z0-9_.-]"), "_")}"

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
            lockRelease = { DesktopOsHelpers.releaseSingleInstanceLock() }
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
            ensureWorkerProcessRunning(options, "serviceworker")
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
        return ensureWorkerProcessRunning(options, "serviceworker")
    }

    override fun isAutostartEnabled(options: ServiceWorkerOptions): Boolean =
        DesktopOsHelpers.isAutostartEnabled(options.appId, options.appName)

    override fun setAutostartEnabled(options: ServiceWorkerOptions, enabled: Boolean): Boolean {
        val cmd = DesktopOsHelpers.resolveSiblingBinaryCommand(
            siblingBaseName = "serviceworker",
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

    override fun resetForTests() {
        channelClient.disconnect()
        activeWorkerInstance?.performStop()
        activeHost?.shutdown()
        activeWorkerInstance = null
        activeHost = null
        currentOptions = ServiceWorkerOptions()
    }

    private fun ensureWorkerProcessRunning(
        options: ServiceWorkerOptions,
        workerBinaryBaseName: String
    ): Boolean {
        val endpointPath = resolveEndpointFilePath(options.appId)
        val existing = DesktopEndpointDescriptor.parse(DesktopOsHelpers.readTextFileOrNull(endpointPath))
        if (existing != null) {
            val probe = DesktopLoopbackTcpClient.connect(existing.port, timeoutMs = 400)
            if (probe != null) {
                probe.close()
                return true
            }
            DesktopOsHelpers.deleteFileIfExists(endpointPath)
        }
        val cmd = DesktopOsHelpers.resolveSiblingBinaryCommand(
            siblingBaseName = workerBinaryBaseName,
            jvmMainClassFqn = options.swingWorkerMainClass
        ) ?: return false
        if (!DesktopOsHelpers.spawnDetachedProcess(cmd.first, cmd.second, null)) {
            return false
        }
        val deadline = 3_000L
        var waited = 0L
        while (waited < deadline) {
            val desc = DesktopEndpointDescriptor.parse(DesktopOsHelpers.readTextFileOrNull(endpointPath))
            if (desc != null) {
                return true
            }
            DesktopOsHelpers.sleepMillis(50L)
            waited += 50L
        }
        return false
    }

    private fun resolveEndpointFilePath(appId: String): String {
        val dir = DesktopOsHelpers.runtimeDataDirectory(appId)
        DesktopOsHelpers.ensureDirectoryExists(dir)
        val sep = if (dir.contains('\\')) '\\' else '/'
        return "${dir.trimEnd('/', '\\')}${sep}serviceworker-endpoint.json"
    }

    private fun deriveMainClassFromWorkerClass(workerMainClass: String): String {
        if (workerMainClass.isBlank()) return ""
        val pkg = workerMainClass.substringBeforeLast('.', "")
        return if (pkg.isEmpty()) "GeneratedSwingMain" else "$pkg.GeneratedSwingMain"
    }
}
