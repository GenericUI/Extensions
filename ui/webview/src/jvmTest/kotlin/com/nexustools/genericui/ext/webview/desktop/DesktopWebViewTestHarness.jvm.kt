package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.swing.SwingAlienNjsWebViewImpl
import com.nexustools.genericui.ext.webview.swing.SwingWebViewExtensionProvider
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl
import java.io.File

actual object DesktopWebViewTestHarness {
    actual fun installProvider() {
        SwingWebViewExtensionProvider.install()
    }

    actual fun currentProcessId(): Long = ProcessHandle.current().pid()

    actual fun resolveWebViewSuiteDirectory(): String {
        val candidates = ArrayList<File>()
        val resUrl = Thread.currentThread().contextClassLoader?.getResource("webview-suite/index.html")
        if (resUrl != null && resUrl.protocol == "file") {
            val file = File(resUrl.toURI()).parentFile
            if (file != null) candidates.add(file)
        }
        candidates.add(File("src/desktopCommonTest/resources/webview-suite"))
        candidates.add(File("extensions/ui/webview/src/desktopCommonTest/resources/webview-suite"))
        candidates.add(File("../src/desktopCommonTest/resources/webview-suite"))
        for (dir in candidates) {
            if (dir.isDirectory && File(dir, "index.html").isFile) {
                return dir.canonicalPath.replace('\\', '/')
            }
        }
        error("Could not locate webview-suite test resources in JVM test environment (cwd=${File(".").absolutePath})")
    }

    actual fun createDirectWebViewImpl(config: WebViewConfig): WebViewNativeImpl<*> =
        SwingAlienNjsWebViewImpl(config)

    actual fun terminateWorkerProcessForCrashRecovery(impl: WebViewNativeImpl<*>): Long? =
        (impl as SwingAlienNjsWebViewImpl).workerSession.terminateWorkerProcessForCrashRecovery()

    actual fun waitForWorkerRestartRecovery(
        impl: WebViewNativeImpl<*>,
        expectedRestartCount: Int,
        timeoutMs: Int
    ): Boolean = (impl as SwingAlienNjsWebViewImpl).workerSession.waitForRestartRecovery(expectedRestartCount, timeoutMs)

    actual fun workerCrashCount(impl: WebViewNativeImpl<*>): Int =
        (impl as SwingAlienNjsWebViewImpl).workerSession.workerCrashCount

    actual fun workerRestartCount(impl: WebViewNativeImpl<*>): Int =
        (impl as SwingAlienNjsWebViewImpl).workerSession.workerRestartCount
}
