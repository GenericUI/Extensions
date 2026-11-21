package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.ext.pip.desktop.DesktopFloatingPipBackend

internal actual object PlatformPipBootstrap {
    actual fun ensureDefaultRegistered() {
        if (!PipRegistry.hasRegisteredFactory()) {
            PipRegistry.registerBackendFactory { DesktopFloatingPipBackend() }
        }
    }
}
