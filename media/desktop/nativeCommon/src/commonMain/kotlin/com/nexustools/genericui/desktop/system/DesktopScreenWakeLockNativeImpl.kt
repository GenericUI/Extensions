package com.nexustools.genericui.desktop.system

import com.nexustools.genericui.ext.media.desktop.wakelock.DesktopScreenWakePlatformBridge
import com.nexustools.genericui.nativeimpl.system.ScreenWakeLockNativeImpl

/**
 * Shared native desktop implementation of [ScreenWakeLockNativeImpl] across macOS (`IOKit` + `NSProcessInfo`),
 * Windows (`PowerCreateRequest`/`PowerSetRequest` + `SetThreadExecutionState`), and Linux (`org.freedesktop.ScreenSaver`,
 * `org.gnome.SessionManager`, and X11 `XScreenSaverSuspend`).
 */
open class DesktopScreenWakeLockNativeImpl : ScreenWakeLockNativeImpl {

    override var isDisposed: Boolean = false
        protected set

    override val isWakeLockHeld: Boolean
        get() = !isDisposed && DesktopScreenWakePlatformBridge.isHeld

    override fun setWakeLockEnabled(enabled: Boolean, reason: String) {
        if (isDisposed && enabled) return
        DesktopScreenWakePlatformBridge.setWakeLockEnabled(enabled, reason)
    }

    override fun dispose() {
        if (!isDisposed) {
            isDisposed = true
            DesktopScreenWakePlatformBridge.setWakeLockEnabled(false, "GenericUI Media Playback")
        }
    }
}
