package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.core.ApplicationLifecycleExtensionHook
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.core.GenericUiExtensionRegistry
import com.nexustools.genericui.enums.system.BackendTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Central coordinator for the `:extensions:system:serviceworker` extension module.
 *
 * Exposes capability flags ([isSupported], [isAutostartSupported]), client-to-worker IPC
 * [channel], generated launcher registration hooks, and platform startup bridges without introducing
 * any core-to-extension dependencies.
 */
object ServiceWorkerExtension : ApplicationLifecycleExtensionHook {

    private var runtimeOverride: ServiceWorkerPlatformRuntime? = null
    private var defaultRuntime: ServiceWorkerPlatformRuntime? = null
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var lifecycleHookRegistered: Boolean = false

    @Volatile
    private var registeredWorkerFactory: (() -> ServiceWorker)? = null

    var options: ServiceWorkerOptions = ServiceWorkerOptions()
        private set

    private val currentRuntime: ServiceWorkerPlatformRuntime
        get() = runtimeOverride ?: defaultRuntime ?: ServiceWorkerRuntimeProvider.createRuntime().also {
            defaultRuntime = it
        }

    /**
     * `true` on Desktop, Android, and Web; `false` on iOS and iPadOS.
     */
    val isSupported: Boolean
        get() = currentRuntime.isSupported

    /**
     * `true` on Desktop (Windows, macOS, Linux, Swing) and Android; `false` on iOS, iPadOS, and Web.
     */
    val isAutostartSupported: Boolean
        get() = currentRuntime.isAutostartSupported

    /**
     * Typed client-to-worker IPC channel connected automatically to the active `@MainServiceWorker`.
     */
    val channel: ServiceWorkerChannel = ServiceWorkerChannel(
        runtimeResolver = { currentRuntime },
        optionsResolver = { options }
    )

    /**
     * Applies extension options propagated from Gradle (`serviceWorker { ... }`) and registers
     * the lifecycle hook on [GenericUiExtensionRegistry].
     */
    fun install(configuredOptions: ServiceWorkerOptions = ServiceWorkerOptions()) {
        configure(configuredOptions)
        ensureLifecycleHookRegistered()
    }

    fun configure(configuredOptions: ServiceWorkerOptions) {
        options = configuredOptions
        currentRuntime.configure(configuredOptions)
    }

    /**
     * Registers the generated `@MainServiceWorker` factory (no-op on iOS / iPadOS, where workers
     * MUST NEVER be instantiated or simulated).
     */
    fun registerWorkerFactory(factory: () -> ServiceWorker) {
        if (!isSupported) {
            return
        }
        registeredWorkerFactory = factory
        ensureLifecycleHookRegistered()
    }

    fun hasRegisteredWorkerFactory(): Boolean =
        isSupported && registeredWorkerFactory != null

    /**
     * Invoked by generated Desktop main launchers before creating/showing any GUI window.
     * Returns `true` if the GUI window should proceed to launch, or `false` when
     * `openWindowOnLaunch == false` and `--open-window`/`--settings-only` were omitted after
     * ensuring the singleton background `serviceworker` process is active.
     */
    fun prepareDesktopMainLaunch(
        args: Array<String>,
        workerBinaryBaseName: String = "serviceworker"
    ): Boolean {
        if (!isSupported) return true
        ensureLifecycleHookRegistered()
        return currentRuntime.prepareDesktopMainLaunch(
            args = args,
            options = options,
            workerBinaryBaseName = workerBinaryBaseName
        )
    }

    /**
     * Entrypoint invoked by the generated secondary Desktop `serviceworker` executable (and Swing
     * `GeneratedSwingServiceWorkerMain`). Runs the singleton lock, loopback IPC server, system tray,
     * `@MainServiceWorker`, and headless OS event loop.
     */
    fun runDesktopWorkerMain(
        args: Array<String>,
        backendInit: () -> Unit,
        eventLoopRunner: () -> Unit,
        eventLoopStopper: () -> Unit,
        workerFactory: () -> ServiceWorker,
        mainBinaryBaseName: String = options.baseName
    ): Int {
        if (!isSupported) return 0
        registeredWorkerFactory = workerFactory
        return currentRuntime.runDesktopWorkerMain(
            args = args,
            options = options,
            workerFactory = workerFactory,
            backendInit = backendInit,
            eventLoopRunner = eventLoopRunner,
            eventLoopStopper = eventLoopStopper,
            mainBinaryBaseName = mainBinaryBaseName
        )
    }

    fun ensureStarted(): Boolean {
        if (!isSupported) return false
        return currentRuntime.ensureBackgroundStarted(options, registeredWorkerFactory)
    }

    fun activeWorker(): ServiceWorker? =
        if (isSupported) currentRuntime.activeWorker() else null

    fun currentBackendTarget(): BackendTarget? =
        currentRuntime.currentBackendTarget()

    fun isAutostartEnabled(): Boolean =
        if (isAutostartSupported) currentRuntime.isAutostartEnabled(options) else false

    fun setAutostartEnabled(enabled: Boolean): Boolean =
        if (isAutostartSupported) currentRuntime.setAutostartEnabled(options, enabled) else false

    internal fun resolvedWorkerFactory(): (() -> ServiceWorker)? =
        if (isSupported) registeredWorkerFactory else null

    internal fun openMainWindowFromHost(route: String?) {
        currentRuntime.openMainWindowFromHost(options, route)
    }

    internal fun closeMainWindowFromHost() {
        currentRuntime.closeMainWindowFromHost(options)
    }

    override fun onBeforeAppPrepare(descriptor: GenericUiAppDescriptor) {
        val merged = options.copy(
            appId = descriptor.appId.ifBlank { options.appId },
            appName = descriptor.appName.ifBlank { options.appName }
        )
        configure(merged)
    }

    override fun onAppPrepared(app: IApplication, descriptor: GenericUiAppDescriptor) {
        if (!isSupported || !options.hasWorker && registeredWorkerFactory == null) return
        ensureStarted()
        lifecycleScope.launch {
            runCatching { channel.connect() }
        }
    }

    override fun onAppShutdown(app: IApplication?) {
        if (!isSupported) return
        lifecycleScope.launch {
            runCatching { channel.disconnect() }
        }
    }

    internal fun installRuntimeOverrideForTests(override: ServiceWorkerPlatformRuntime?) {
        runtimeOverride = override
        defaultRuntime = null
    }

    fun resetForTests() {
        registeredWorkerFactory = null
        options = ServiceWorkerOptions()
        channel.reset()
        currentRuntime.resetForTests()
        runtimeOverride = null
        defaultRuntime = null
    }

    private fun ensureLifecycleHookRegistered() {
        if (lifecycleHookRegistered) return
        lifecycleHookRegistered = true
        GenericUiExtensionRegistry.registerLifecycleHook(this)
    }
}
