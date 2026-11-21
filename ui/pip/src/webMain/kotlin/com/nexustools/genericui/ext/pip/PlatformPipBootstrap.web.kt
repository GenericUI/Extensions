package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.ext.pip.web.WebPipBackend

internal actual object PlatformPipBootstrap {
    actual fun ensureDefaultRegistered() {
        if (!PipRegistry.hasRegisteredFactory()) {
            PipRegistry.registerBackendFactory { WebPipBackend() }
        }
    }
}
