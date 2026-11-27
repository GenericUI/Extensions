package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.core.ApplicationLifecycleExtensionHook
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.core.GenericUiExtensionRegistry
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.system.BackendTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass

/**
 * Central coordinator for the `:extensions:system:serviceworker` extension module.
 *
 * Exposes capability flags ([isSupported], [supportsMultipleWindows], [supportsFragmentLaunch],
 * [supportsDialogFragments], [supportsSystemTray], [supportsOngoingNotification],
 * [supportsAutostart]), client-to-worker IPC [channel], generated launcher registration hooks,
 * and platform startup bridges without introducing any core-to-extension dependencies.
 */
object ServiceWorkerExtension : ApplicationLifecycleExtensionHook {

    private var runtimeOverride: ServiceWorkerPlatformRuntime? = null
    private var defaultRuntime: ServiceWorkerPlatformRuntime? = null
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val openMainWindowListeners = mutableListOf<(String?) -> Unit>()

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
     * `true` when the active platform supports opening multiple concurrent UI windows from a worker.
     */
    val supportsMultipleWindows: Boolean
        get() = currentRuntime.supportsMultipleWindows

    /**
     * `true` when the active platform/context allows a worker to start UI fragments.
     */
    val supportsFragmentLaunch: Boolean
        get() = currentRuntime.supportsFragmentLaunch

    /**
     * `true` when the active platform supports launching `@AppFragment(dialog = true)` fragments.
     */
    val supportsDialogFragments: Boolean
        get() = currentRuntime.supportsDialogFragments

    /**
     * `true` on Desktop targets supporting OS system tray / menu bar icons; `false` on mobile and Web.
     */
    val supportsSystemTray: Boolean
        get() = currentRuntime.supportsSystemTray

    /**
     * `true` on platforms supporting an ongoing foreground service notification (Android).
     */
    val supportsOngoingNotification: Boolean
        get() = currentRuntime.supportsOngoingNotification

    /**
     * `true` on Desktop (Windows, macOS, Linux, Swing) and Android; `false` on iOS, iPadOS, and Web.
     */
    val supportsAutostart: Boolean
        get() = currentRuntime.supportsAutostart

    /**
     * Returns `true` when both the active backend and [fragmentClass]'s `@MainAppFragment` /
     * `@AppFragment` metadata allow opening an additional concurrent window.
     */
    fun canOpenAdditionalWindow(fragmentClass: KClass<out Fragment>): Boolean =
        supportsMultipleWindows &&
            supportsFragmentLaunch &&
            (!FragmentRegistry.isDialogFragment(fragmentClass) || supportsDialogFragments) &&
            FragmentRegistry.supportsMultipleWindows(fragmentClass)

    inline fun <reified T : Fragment> canOpenAdditionalWindow(): Boolean =
        canOpenAdditionalWindow(T::class)

    internal fun currentBackendName(): String =
        currentRuntime.currentBackendTarget()?.displayName ?: "Unknown"

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
     * ensuring the singleton background `<baseName>-sw` process is active.
     */
    fun prepareDesktopMainLaunch(
        args: Array<String>,
        workerBinaryBaseName: String = options.resolvedWorkerBinaryBaseName()
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
     * Entrypoint invoked by the generated secondary Desktop `<baseName>-sw` executable (and Swing
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
        if (!isSupported) {
            throw ServiceWorkerUnsupportedException(currentBackendName())
        }
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
        if (!isSupported) {
            throw ServiceWorkerUnsupportedException(currentBackendName())
        }
        return currentRuntime.ensureBackgroundStarted(options, registeredWorkerFactory)
    }

    fun activeWorker(): ServiceWorker? =
        if (isSupported) currentRuntime.activeWorker() else null

    fun currentBackendTarget(): BackendTarget? =
        currentRuntime.currentBackendTarget()

    fun isAutostartEnabled(): Boolean =
        if (supportsAutostart) currentRuntime.isAutostartEnabled(options) else false

    fun setAutostartEnabled(enabled: Boolean): Boolean {
        if (!supportsAutostart) {
            throw ServiceWorkerAutostartUnsupportedException(currentBackendName())
        }
        val applied = currentRuntime.setAutostartEnabled(options, enabled)
        if (!applied) {
            val backend = currentBackendName()
            throw ServiceWorkerAutostartUnsupportedException(
                backendName = backend,
                message = "Failed to ${if (enabled) "enable" else "disable"} ServiceWorker autostart on backend '$backend'."
            )
        }
        return true
    }

    internal fun resolvedWorkerFactory(): (() -> ServiceWorker)? =
        if (isSupported) registeredWorkerFactory else null

    internal fun openMainWindowFromHost(route: String?) {
        currentRuntime.openMainWindowFromHost(options, route)
    }

    internal fun closeMainWindowFromHost() {
        currentRuntime.closeMainWindowFromHost(options)
    }

    /**
     * Registers a UI-side listener notified whenever the background Service Worker activates
     * or routes to the running main window (via `__genericui.sw.control.openMainWindow`).
     */
    fun onOpenMainWindowRequested(listener: (route: String?) -> Unit): Disposable {
        openMainWindowListeners.add(listener)
        return ServiceWorkerDisposableHandle { openMainWindowListeners.remove(listener) }
    }

    internal fun dispatchOpenMainWindowRequested(route: String?) {
        for (listener in openMainWindowListeners.toList()) {
            listener(route)
        }
    }

    override fun onBeforeAppPrepare(descriptor: GenericUiAppDescriptor) {
        val merged = options.copy(
            appId = descriptor.appId.ifBlank { options.appId },
            appName = descriptor.appName.ifBlank { options.appName }
        )
        configure(merged)
    }

    override fun onAppPrepared(app: IApplication, descriptor: GenericUiAppDescriptor) {
        if (app is Application) {
            ServiceWorkerUiWindowCoordinator.onAppPrepared(app)
        }
        if (!isSupported || !options.hasWorker && registeredWorkerFactory == null) return
        ensureStarted()
        lifecycleScope.launch {
            channel.connect()
        }
    }

    override fun onAppEventLoopStarted(app: Application, factory: NativeFactory) {
        ServiceWorkerUiWindowCoordinator.onAppEventLoopStarted(app, factory)
    }

    override fun hasOpenFragments(app: Application): Boolean =
        ServiceWorkerUiWindowCoordinator.hasOpenCoordinatorFragments(app)

    override fun onAppShutdown(app: IApplication?) {
        ServiceWorkerUiWindowCoordinator.onAppShutdown(app)
        if (!isSupported) return
        currentRuntime.onMainAppShutdown()
        lifecycleScope.launch {
            channel.disconnect()
        }
    }

    internal fun installRuntimeOverrideForTests(override: ServiceWorkerPlatformRuntime?) {
        runtimeOverride = override
        defaultRuntime = null
    }

    fun resetForTests() {
        lifecycleHookRegistered = false
        registeredWorkerFactory = null
        options = ServiceWorkerOptions()
        openMainWindowListeners.clear()
        ServiceWorkerUiWindowCoordinator.resetForTests()
        ServiceWorkerFragmentBinding.clearAllForTests()
        channel.reset()
        currentRuntime.resetForTests()
        runtimeOverride = null
        defaultRuntime = null
    }

    private fun ensureLifecycleHookRegistered() {
        if (lifecycleHookRegistered && this in GenericUiExtensionRegistry.registeredLifecycleHooks()) return
        lifecycleHookRegistered = true
        GenericUiExtensionRegistry.registerLifecycleHook(this)
    }
}
