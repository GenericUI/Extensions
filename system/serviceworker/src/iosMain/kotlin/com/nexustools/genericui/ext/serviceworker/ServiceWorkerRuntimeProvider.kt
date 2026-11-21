package com.nexustools.genericui.ext.serviceworker

internal actual object ServiceWorkerRuntimeProvider {
    actual fun createRuntime(): ServiceWorkerPlatformRuntime = AppleMobileServiceWorkerRuntime()
}
