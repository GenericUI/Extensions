package com.nexustools.genericui.ext.pip.desktop

import com.nexustools.genericui.ext.pip.PipRegistry

/**
 * Registers the native desktop floating-window [DesktopFloatingPipBackend].
 */
object DesktopPipProvider {
    fun register() {
        PipRegistry.registerBackendFactory { DesktopFloatingPipBackend() }
    }
}
