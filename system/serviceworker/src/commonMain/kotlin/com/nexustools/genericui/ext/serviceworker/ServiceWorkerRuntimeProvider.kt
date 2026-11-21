package com.nexustools.genericui.ext.serviceworker

/**
 * Multiplatform factory selecting the concrete [ServiceWorkerPlatformRuntime] per target leaf.
 */
internal expect object ServiceWorkerRuntimeProvider {
    fun createRuntime(): ServiceWorkerPlatformRuntime
}
