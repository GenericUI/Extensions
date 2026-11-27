package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.desktop.ipc.NativeWorkerPlatformSupport
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl

actual object DesktopWebViewTestHarness {
    actual fun installProvider() {
        DesktopWebViewExtensionProvider.install()
    }

    actual fun currentProcessId(): Long = NativeWorkerPlatformSupport.currentPid()

    actual fun resolveWebViewSuiteDirectory(): String {
        val candidates = ArrayList<String>()
        val cwd = NativeWorkerPlatformSupport.resolveCurrentWorkingDirectory()?.replace('\\', '/')?.trimEnd('/')
        if (!cwd.isNullOrEmpty()) {
            candidates.add("$cwd/src/desktopCommonTest/resources/webview-suite")
            candidates.add("$cwd/extensions/ui/webview/src/desktopCommonTest/resources/webview-suite")
        }
        val exeDir = NativeWorkerPlatformSupport.resolveExecutableDirectory()?.replace('\\', '/')?.trimEnd('/')
        if (!exeDir.isNullOrEmpty()) {
            var cur: String = exeDir
            for (depth in 0 until 8) {
                candidates.add("$cur/src/desktopCommonTest/resources/webview-suite")
                candidates.add("$cur/extensions/ui/webview/src/desktopCommonTest/resources/webview-suite")
                val slash = cur.lastIndexOf('/')
                if (slash <= 0) break
                cur = cur.substring(0, slash)
            }
        }
        candidates.add("D:/IdeaProjects/GenericUI/extensions/ui/webview/src/desktopCommonTest/resources/webview-suite")

        for (dir in candidates) {
            if (NativeWorkerPlatformSupport.fileExists("$dir/index.html")) {
                return dir
            }
        }
        error("Could not locate webview-suite test resources in Native test environment (cwd=$cwd, exeDir=$exeDir)")
    }

    actual fun createDirectWebViewImpl(config: WebViewConfig): WebViewNativeImpl<*> =
        DesktopAlienNjsWebViewImpl(config)

    actual fun terminateWorkerProcessForCrashRecovery(impl: WebViewNativeImpl<*>): Long? =
        (impl as DesktopAlienNjsWebViewImpl).workerSession.terminateWorkerProcessForCrashRecovery()

    actual fun waitForWorkerRestartRecovery(
        impl: WebViewNativeImpl<*>,
        expectedRestartCount: Int,
        timeoutMs: Int
    ): Boolean = (impl as DesktopAlienNjsWebViewImpl).workerSession.waitForRestartRecovery(expectedRestartCount, timeoutMs)

    actual fun workerCrashCount(impl: WebViewNativeImpl<*>): Int =
        (impl as DesktopAlienNjsWebViewImpl).workerSession.workerCrashCount

    actual fun workerRestartCount(impl: WebViewNativeImpl<*>): Int =
        (impl as DesktopAlienNjsWebViewImpl).workerSession.workerRestartCount
}
