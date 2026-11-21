package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.enums.system.BackendTarget

internal actual object ServiceWorkerRuntimeProvider {
    actual fun createRuntime(): ServiceWorkerPlatformRuntime =
        DesktopServiceWorkerRuntime(BackendTarget.Windows)
}
