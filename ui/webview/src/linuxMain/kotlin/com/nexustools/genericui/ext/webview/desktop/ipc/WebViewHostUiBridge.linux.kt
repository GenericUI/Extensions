@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.webview.desktop.ipc

import com.nexustools.genericui.gtk.GtkApplicationNativeImpl
import com.nexustools.genericui.kde.KdeApplicationNativeImpl
import com.nexustools.genericui.threading.GenericUiThreading

/**
 * Linux GTK/KDE UI-thread marshaling implementation of [WebViewHostUiBridge].
 */
actual object WebViewHostUiBridge {
    actual fun isUiLoopActive(): Boolean {
        WebViewHostUiBridgeTestHooks.uiLoopActiveOverride.load()?.let { return it }
        return GtkApplicationNativeImpl.interactiveGtkGuiEnabled ||
            KdeApplicationNativeImpl.interactiveKdeGuiEnabled
    }

    actual fun isOnUiThread(): Boolean {
        WebViewHostUiBridgeTestHooks.onUiThreadOverride.load()?.let { return it }
        if (!isUiLoopActive()) return true
        return GenericUiThreading.isOnUiThread()
    }

    actual fun ensureUiThreadBoundIfActive() {
        // GTK/KDE UI dispatcher captures main thread during initialization.
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
