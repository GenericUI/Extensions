@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.webview.desktop.ipc

import kotlin.concurrent.atomics.AtomicReference

/**
 * Coordinates marshaling desktop WebView host callbacks onto the active UI toolkit thread
 * while running inline when no interactive UI event loop is running (for example in headless tests).
 */
expect object WebViewHostUiBridge {
    fun isUiLoopActive(): Boolean
    fun isOnUiThread(): Boolean
    fun ensureUiThreadBoundIfActive()
    fun postToUi(requireUiLoop: Boolean = false, forceAsync: Boolean = false, block: () -> Unit)
}

internal object WebViewHostUiBridgeTestHooks {
    val uiLoopActiveOverride = AtomicReference<Boolean?>(null)
    val onUiThreadOverride = AtomicReference<Boolean?>(null)
    val postToUiOverride = AtomicReference<((() -> Unit) -> Unit)?>(null)

    fun reset() {
        uiLoopActiveOverride.store(null)
        onUiThreadOverride.store(null)
        postToUiOverride.store(null)
    }
}
