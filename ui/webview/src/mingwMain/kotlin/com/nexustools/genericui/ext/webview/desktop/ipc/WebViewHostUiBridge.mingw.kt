@file:OptIn(
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class
)

package com.nexustools.genericui.ext.webview.desktop.ipc

import com.nexustools.genericui.threading.GenericUiThreading
import com.nexustools.genericui.windows.WindowsApplicationNativeImpl
import com.nexustools.genericui.windows.WindowsUiThreadDispatcherNativeImpl
import platform.windows.GetCurrentThreadId

/**
 * Windows Win32 UI-thread marshaling implementation of [WebViewHostUiBridge].
 */
actual object WebViewHostUiBridge {
    actual fun isUiLoopActive(): Boolean {
        WebViewHostUiBridgeTestHooks.uiLoopActiveOverride.load()?.let { return it }
        return WindowsApplicationNativeImpl.interactiveWin32GuiEnabled
    }

    actual fun isOnUiThread(): Boolean {
        WebViewHostUiBridgeTestHooks.onUiThreadOverride.load()?.let { return it }
        if (!isUiLoopActive()) return true
        val registeredTid = WindowsUiThreadDispatcherNativeImpl.getRegisteredUiThreadId()
        if (registeredTid != 0) {
            return GetCurrentThreadId().toInt() == registeredTid
        }
        return GenericUiThreading.isOnUiThread()
    }

    actual fun ensureUiThreadBoundIfActive() {
        if (WindowsApplicationNativeImpl.interactiveWin32GuiEnabled) {
            WindowsUiThreadDispatcherNativeImpl.attachCurrentThreadAsUiThreadIfUnset()
        }
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
        val dispatcher = WindowsUiThreadDispatcherNativeImpl.activeDispatcher
        if (dispatcher != null) {
            dispatcher.dispatchToUi(block)
        } else {
            GenericUiThreading.runOnUi(block)
        }
    }
}
