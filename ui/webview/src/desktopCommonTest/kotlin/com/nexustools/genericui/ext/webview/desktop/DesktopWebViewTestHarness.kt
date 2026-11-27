package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl

/**
 * Platform test harness bridging JVM Swing and Kotlin/Native Desktop test targets for common
 * isolated-worker compliance suites.
 */
expect object DesktopWebViewTestHarness {
    fun installProvider()
    fun currentProcessId(): Long
    fun resolveWebViewSuiteDirectory(): String
    fun createDirectWebViewImpl(config: WebViewConfig): WebViewNativeImpl<*>
    fun terminateWorkerProcessForCrashRecovery(impl: WebViewNativeImpl<*>): Long?
    fun waitForWorkerRestartRecovery(
        impl: WebViewNativeImpl<*>,
        expectedRestartCount: Int,
        timeoutMs: Int = 15_000
    ): Boolean
    fun workerCrashCount(impl: WebViewNativeImpl<*>): Int
    fun workerRestartCount(impl: WebViewNativeImpl<*>): Int
}
