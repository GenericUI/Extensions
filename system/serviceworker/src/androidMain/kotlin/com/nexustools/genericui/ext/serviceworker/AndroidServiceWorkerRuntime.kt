package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.enums.system.BackendTarget

/**
 * Android [ServiceWorkerPlatformRuntime] delegating foreground service lifecycle, ongoing
 * notifications, notification action intents, and local Binder IPC to
 * [AndroidServiceWorkerController].
 */
internal class AndroidServiceWorkerRuntime : ServiceWorkerPlatformRuntime {

    override val isSupported: Boolean = true
    override val supportsMultipleWindows: Boolean = false
    override val supportsFragmentLaunch: Boolean = true
    override val supportsDialogFragments: Boolean = true
    override val supportsSystemTray: Boolean = false
    override val supportsOngoingNotification: Boolean = true
    override val supportsAutostart: Boolean = true

    override fun configure(options: ServiceWorkerOptions) {
        AndroidServiceWorkerController.updateOptions(options)
        if (options.hasWorker || ServiceWorkerExtension.hasRegisteredWorkerFactory()) {
            AndroidServiceWorkerController.installLifecycleObserverIfNeeded()
        }
    }

    override fun isClientConnected(): Boolean =
        AndroidServiceWorkerController.isInProcessClientConnected()

    override suspend fun connectClient(
        options: ServiceWorkerOptions,
        onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
        onConnectionChanged: (Boolean) -> Unit
    ): Boolean {
        AndroidServiceWorkerController.updateOptions(options)
        return AndroidServiceWorkerController.connectInProcessClient(
            listener = onIncomingEnvelope,
            connectionChanged = onConnectionChanged
        )
    }

    override suspend fun disconnectClient() {
        AndroidServiceWorkerController.disconnectInProcessClient()
    }

    override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean =
        AndroidServiceWorkerController.dispatchFromClient(envelope)

    override fun ensureBackgroundStarted(
        options: ServiceWorkerOptions,
        workerFactory: (() -> ServiceWorker)?
    ): Boolean {
        AndroidServiceWorkerController.updateOptions(options)
        AndroidServiceWorkerController.installLifecycleObserverIfNeeded()
        return AndroidServiceWorkerController.ensureServiceStartedAndBound(null)
    }

    override fun isAutostartEnabled(options: ServiceWorkerOptions): Boolean =
        AndroidServiceWorkerController.isAutostartEnabled()

    override fun setAutostartEnabled(options: ServiceWorkerOptions, enabled: Boolean): Boolean =
        AndroidServiceWorkerController.setAutostartEnabled(enabled)

    override fun openMainWindowFromHost(options: ServiceWorkerOptions, route: String?) {
        AndroidServiceWorkerController.openMainWindow(route)
    }

    override fun closeMainWindowFromHost(options: ServiceWorkerOptions) {
        AndroidServiceWorkerController.closeMainWindow()
    }

    override fun activeWorker(): ServiceWorker? =
        AndroidServiceWorkerController.activeWorker()

    override fun currentBackendTarget(): BackendTarget = BackendTarget.Android

    override fun resetForTests() {
        AndroidServiceWorkerController.resetForTests()
    }
}
