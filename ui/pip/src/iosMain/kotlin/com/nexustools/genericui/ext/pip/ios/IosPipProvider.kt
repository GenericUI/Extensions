package com.nexustools.genericui.ext.pip.ios

import com.nexustools.genericui.ext.pip.PipRegistry
import kotlin.concurrent.Volatile

/**
 * Platform registration entry point for iOS & iPadOS Picture-in-Picture support.
 */
object IosPipProvider {

    @Volatile
    private var registered: Boolean = false

    fun register() {
        if (registered && PipRegistry.hasRegisteredFactory()) {
            return
        }
        PipRegistry.registerBackendFactory { IosPipBackend() }
        registered = true
    }
}
