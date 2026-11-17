package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.system.ScreenWakeLockNativeImpl
import platform.Foundation.NSThread
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS implementation of [ScreenWakeLockNativeImpl] backed by `UIApplication.sharedApplication.idleTimerDisabled`
 * on the main UIKit thread.
 */
class IosScreenWakeLockImpl : ScreenWakeLockNativeImpl {

    @kotlin.concurrent.Volatile
    override var isDisposed: Boolean = false
        private set

    @kotlin.concurrent.Volatile
    private var heldState: Boolean = false

    override val isWakeLockHeld: Boolean
        get() = !isDisposed && heldState

    override fun setWakeLockEnabled(enabled: Boolean, reason: String) {
        if (isDisposed && enabled) return
        val targetEnabled = !isDisposed && enabled
        heldState = targetEnabled
        val applyOnMain = {
            runCatching {
                UIApplication.sharedApplication.setIdleTimerDisabled(targetEnabled)
            }
            Unit
        }
        if (NSThread.isMainThread) {
            applyOnMain()
        } else {
            dispatch_async(dispatch_get_main_queue()) {
                applyOnMain()
            }
        }
    }

    override fun dispose() {
        if (!isDisposed) {
            isDisposed = true
            setWakeLockEnabled(false, "GenericUI Media Playback")
            heldState = false
        }
    }
}
