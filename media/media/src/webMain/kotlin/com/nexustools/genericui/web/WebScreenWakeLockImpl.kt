package com.nexustools.genericui.web

import com.nexustools.genericui.nativeimpl.system.ScreenWakeLockNativeImpl

/**
 * Web implementation of [ScreenWakeLockNativeImpl] backed by the W3C Screen Wake Lock API
 * (`navigator.wakeLock.request("screen")`).
 */
class WebScreenWakeLockImpl : ScreenWakeLockNativeImpl {

    override var isDisposed: Boolean = false
        private set

    override val isWakeLockHeld: Boolean
        get() = !isDisposed && WebWakeLockBridge.isWakeLockHeld

    override fun setWakeLockEnabled(enabled: Boolean, reason: String) {
        if (isDisposed && enabled) return
        WebWakeLockBridge.setWakeLockEnabled(!isDisposed && enabled, reason)
    }

    override fun dispose() {
        if (!isDisposed) {
            isDisposed = true
            WebWakeLockBridge.setWakeLockEnabled(false, "GenericUI Media Playback")
        }
    }
}
