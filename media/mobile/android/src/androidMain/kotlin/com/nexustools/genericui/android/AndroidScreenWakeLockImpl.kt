package com.nexustools.genericui.android

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.PowerManager
import android.view.WindowManager
import com.nexustools.genericui.nativeimpl.system.ScreenWakeLockNativeImpl

/**
 * Android implementation of [ScreenWakeLockNativeImpl] using `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`
 * on the active foreground [Activity] window (plus best-effort `PowerManager.PARTIAL_WAKE_LOCK` for background audio).
 */
class AndroidScreenWakeLockImpl : ScreenWakeLockNativeImpl {

    private val lock = Any()

    @Volatile
    override var isDisposed: Boolean = false
        private set

    @Volatile
    private var heldState: Boolean = false

    private var cpuWakeLock: PowerManager.WakeLock? = null

    override val isWakeLockHeld: Boolean
        get() = !isDisposed && heldState

    private fun unwrapActivity(context: Context?): Activity? {
        var current: Context? = context
        var depth = 0
        while (current != null && depth < 16) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
            depth++
        }
        return null
    }

    override fun setWakeLockEnabled(enabled: Boolean, reason: String) {
        synchronized(lock) {
            if (isDisposed && enabled) return
            val targetEnabled = !isDisposed && enabled
            heldState = targetEnabled

            val ctx = AndroidViewHandle.activeContext
            val activity = unwrapActivity(ctx)
            AndroidViewHandle.runOnMainThread {
                runCatching {
                    val window = activity?.window
                    if (window != null) {
                        if (targetEnabled) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }
            }

            if (targetEnabled) {
                if (cpuWakeLock == null && ctx != null) {
                    cpuWakeLock = runCatching {
                        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                        pm?.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            "GenericUI:${reason.ifBlank { "MediaPlayback" }}"
                        )?.apply {
                            setReferenceCounted(false)
                        }
                    }.getOrNull()
                }
                runCatching {
                    if (cpuWakeLock?.isHeld == false) {
                        cpuWakeLock?.acquire()
                    }
                }
            } else {
                runCatching {
                    if (cpuWakeLock?.isHeld == true) {
                        cpuWakeLock?.release()
                    }
                }
            }
        }
    }

    override fun dispose() {
        synchronized(lock) {
            if (isDisposed) return
            setWakeLockEnabled(false, "GenericUI Media Playback")
            isDisposed = true
            cpuWakeLock = null
        }
    }
}
