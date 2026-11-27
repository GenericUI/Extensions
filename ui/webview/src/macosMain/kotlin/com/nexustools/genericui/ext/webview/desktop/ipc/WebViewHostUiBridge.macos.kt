@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.webview.desktop.ipc

import com.nexustools.genericui.macos.MacosApplicationNativeImpl
import com.nexustools.genericui.threading.GenericUiThreading
import platform.Foundation.NSThread

/**
 * macOS AppKit UI-thread marshaling implementation of [WebViewHostUiBridge].
 */
actual object WebViewHostUiBridge {
    actual fun isUiLoopActive(): Boolean {
        WebViewHostUiBridgeTestHooks.uiLoopActiveOverride.load()?.let { return it }
        return MacosApplicationNativeImpl.interactiveMacosGuiEnabled
    }

    actual fun isOnUiThread(): Boolean {
        WebViewHostUiBridgeTestHooks.onUiThreadOverride.load()?.let { return it }
        if (!isUiLoopActive()) return true
        return NSThread.isMainThread
    }

    actual fun ensureUiThreadBoundIfActive() {
        // Cocoa main thread affinity is bound to NSThread.isMainThread.
    }

    actual fun postToUi(requireUiLoop: Boolean, forceAsync: Boolean, block: () -> Unit) {
        val active = requireUiLoop || isUiLoopActive()
        if (!active) {
            block()
            return
        }
        val testPoster = WebViewHostUiBridgeTestHooks.postToUiOverride.load()
        if (testPoster != null) {
            if (!forceAsync && isOnUiThread()) {
                block()
            } else {
                testPoster.invoke(block)
            }
            return
        }
        if (!forceAsync && isOnUiThread()) {
            block()
            return
        }
        GenericUiThreading.runOnUi(block)
    }
}
