package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.core.AbstractComponent

/**
 * Global registry supplying the active platform [PipBackend] factory.
 *
 * When queried or asked to create a backend without an explicit override, invokes
 * [PlatformPipBootstrap.ensureDefaultRegistered] lazily so default platform support
 * initializes automatically.
 */
object PipRegistry {

    private var factory: (() -> PipBackend)? = null
    private var cachedProbeBackend: PipBackend? = null
    private var bootstrapAttempted: Boolean = false

    fun registerBackendFactory(factory: () -> PipBackend) {
        this.factory = factory
        this.cachedProbeBackend = null
        this.bootstrapAttempted = true
    }

    fun hasRegisteredFactory(): Boolean = factory != null

    fun createBackend(): PipBackend? {
        ensureBootstrappedIfNeeded()
        return factory?.invoke()
    }

    fun capabilities(): PipCapabilities =
        probeBackend()?.capabilities ?: PipCapabilities.UNSUPPORTED

    fun supportsComponent(component: AbstractComponent): Boolean =
        probeBackend()?.supportsComponent(component) == true

    fun resetForTest() {
        factory = null
        cachedProbeBackend = null
        bootstrapAttempted = false
    }

    private fun ensureBootstrappedIfNeeded() {
        PipExtension.ensureStyleSheetRegistered()
        if (factory == null && !bootstrapAttempted) {
            bootstrapAttempted = true
            PlatformPipBootstrap.ensureDefaultRegistered()
        }
    }

    private fun probeBackend(): PipBackend? {
        ensureBootstrappedIfNeeded()
        val currentFactory = factory ?: return null
        val existing = cachedProbeBackend
        if (existing != null) return existing
        return currentFactory.invoke().also { created ->
            cachedProbeBackend = created
        }
    }
}
