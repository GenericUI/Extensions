package com.nexustools.genericui.web

/**
 * Internal bridge to the W3C Screen Wake Lock API (`navigator.wakeLock.request("screen")`),
 * including automatic re-acquisition on `document` `visibilitychange` while playback remains active.
 */
internal expect object WebWakeLockBridge {
    val isWakeLockHeld: Boolean
    fun setWakeLockEnabled(enabled: Boolean, reason: String)
}
