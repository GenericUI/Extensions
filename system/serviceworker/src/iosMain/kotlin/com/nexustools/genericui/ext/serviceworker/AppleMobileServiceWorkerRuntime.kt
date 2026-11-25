package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.system.BackendTarget

/**
 * Honest iOS / iPadOS [ServiceWorkerPlatformRuntime]: background Service Workers are unsupported
 * by OS constraints (`isSupported == false`, all ServiceWorker capabilities `false`), and
 * `@MainServiceWorker` is never instantiated or simulated.
 */
internal class AppleMobileServiceWorkerRuntime : ServiceWorkerPlatformRuntime {

    override val isSupported: Boolean = false
    override val supportsMultipleWindows: Boolean = false
    override val supportsFragmentLaunch: Boolean = false
    override val supportsDialogFragments: Boolean = false
    override val supportsSystemTray: Boolean = false
    override val supportsOngoingNotification: Boolean = false
    override val supportsAutostart: Boolean = false

    override fun configure(options: ServiceWorkerOptions) {
        // Unsupported on iOS / iPadOS; retain metadata only without activating anything.
    }

    override fun isClientConnected(): Boolean = false

    override suspend fun connectClient(
        options: ServiceWorkerOptions,
        onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
        onConnectionChanged: (Boolean) -> Unit
    ): Boolean {
        onConnectionChanged(false)
        return false
    }

    override suspend fun disconnectClient() {}

    override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean = false

    override fun ensureBackgroundStarted(
        options: ServiceWorkerOptions,
        workerFactory: (() -> ServiceWorker)?
    ): Boolean = false

    override fun setAutostartEnabled(options: ServiceWorkerOptions, enabled: Boolean): Boolean {
        throw ServiceWorkerAutostartUnsupportedException(currentBackendTarget().displayName)
    }

    override fun openMainWindowFromHost(options: ServiceWorkerOptions, route: String?) {
        throw ServiceWorkerUnsupportedException(currentBackendTarget().displayName)
    }

    override fun closeMainWindowFromHost(options: ServiceWorkerOptions) {
        throw ServiceWorkerUnsupportedException(currentBackendTarget().displayName)
    }

    override fun currentBackendTarget(): BackendTarget =
        GenericUI.activeFactory?.backendTarget ?: BackendTarget.IOS
}
