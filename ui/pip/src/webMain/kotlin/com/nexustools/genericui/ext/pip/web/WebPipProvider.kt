package com.nexustools.genericui.ext.pip.web

import com.nexustools.genericui.ext.pip.PipRegistry

/**
 * Registers the Web Picture-in-Picture backend.
 */
object WebPipProvider {
    fun register() {
        PipRegistry.registerBackendFactory { WebPipBackend() }
    }
}
