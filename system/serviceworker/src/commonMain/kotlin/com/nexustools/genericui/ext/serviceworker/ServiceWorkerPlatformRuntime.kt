package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.enums.system.BackendTarget

/**
 * Platform abstraction bridging [ServiceWorkerExtension], [ServiceWorkerChannel], and native
 * OS background worker / IPC lifecycle on each target backend.
 */
internal interface ServiceWorkerPlatformRuntime {
    val isSupported: Boolean
    val isAutostartSupported: Boolean

    fun configure(options: ServiceWorkerOptions)

    fun isClientConnected(): Boolean

    suspend fun connectClient(
        options: ServiceWorkerOptions,
        onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
        onConnectionChanged: (Boolean) -> Unit
    ): Boolean

    suspend fun disconnectClient()

    suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean

    fun prepareDesktopMainLaunch(
        args: Array<String>,
        options: ServiceWorkerOptions,
        workerBinaryBaseName: String
    ): Boolean = true

    fun runDesktopWorkerMain(
        args: Array<String>,
        options: ServiceWorkerOptions,
        workerFactory: () -> ServiceWorker,
        backendInit: () -> Unit,
        eventLoopRunner: () -> Unit,
        eventLoopStopper: () -> Unit,
        mainBinaryBaseName: String
    ): Int = 0

    fun ensureBackgroundStarted(
        options: ServiceWorkerOptions,
        workerFactory: (() -> ServiceWorker)?
    ): Boolean = isSupported

    fun isAutostartEnabled(options: ServiceWorkerOptions): Boolean = false

    fun setAutostartEnabled(options: ServiceWorkerOptions, enabled: Boolean): Boolean = false

    fun openMainWindowFromHost(options: ServiceWorkerOptions, route: String?) {}

    fun closeMainWindowFromHost(options: ServiceWorkerOptions) {}

    fun currentBackendTarget(): BackendTarget? = null

    fun activeWorker(): ServiceWorker? = null

    fun resetForTests() {}
}
