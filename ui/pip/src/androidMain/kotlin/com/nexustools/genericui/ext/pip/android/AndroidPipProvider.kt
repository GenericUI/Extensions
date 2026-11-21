package com.nexustools.genericui.ext.pip.android

import com.nexustools.genericui.ext.pip.PipRegistry

/**
 * Platform registration entry point for Android Picture-in-Picture support.
 */
object AndroidPipProvider {

    @Volatile
    private var registered: Boolean = false

    fun register() {
        synchronized(this) {
            if (registered && PipRegistry.hasRegisteredFactory()) {
                return
            }
            PipRegistry.registerBackendFactory { AndroidPipBackend() }
            registered = true
        }
    }
}
