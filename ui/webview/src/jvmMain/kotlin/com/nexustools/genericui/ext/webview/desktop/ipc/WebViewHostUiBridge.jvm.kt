@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.webview.desktop.ipc

import com.nexustools.genericui.swing.SwingApplicationImpl
import java.awt.GraphicsEnvironment
import javax.swing.SwingUtilities

/**
 * JVM Swing UI-thread marshaling implementation of [WebViewHostUiBridge].
 */
actual object WebViewHostUiBridge {
    actual fun isUiLoopActive(): Boolean {
        WebViewHostUiBridgeTestHooks.uiLoopActiveOverride.load()?.let { return it }
        if (GraphicsEnvironment.isHeadless()) return false
        return SwingApplicationImpl.interactiveSwingGuiEnabled || SwingUtilities.isEventDispatchThread()
    }

    actual fun isOnUiThread(): Boolean {
        WebViewHostUiBridgeTestHooks.onUiThreadOverride.load()?.let { return it }
        return if (!isUiLoopActive()) true else SwingUtilities.isEventDispatchThread()
    }

    actual fun ensureUiThreadBoundIfActive() {
        // Swing Event Dispatch Thread affinity is owned by AWT/Swing directly.
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
        if (GraphicsEnvironment.isHeadless()) {
            block()
            return
        }
        if (!forceAsync && SwingUtilities.isEventDispatchThread()) {
            block()
            return
        }
        SwingUtilities.invokeLater(block)
    }
}
