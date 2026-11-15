package com.nexustools.genericui.ext.media.desktop.wakelock

internal expect object DesktopScreenWakePlatformBridge {
    val isHeld: Boolean
    fun setWakeLockEnabled(enabled: Boolean, reason: String)
}
