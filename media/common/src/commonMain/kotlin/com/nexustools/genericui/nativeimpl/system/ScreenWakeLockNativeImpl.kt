package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.core.Disposable

/**
 * Platform native implementation that inhibits display dimming/power-off, screensaver activation,
 * and OS idle sleep while one or more media players are actively playing with
 * [com.nexustools.genericui.component.media.Player.keepScreenOn] enabled.
 */
interface ScreenWakeLockNativeImpl : Disposable {
    val isWakeLockHeld: Boolean
    fun setWakeLockEnabled(enabled: Boolean, reason: String = "GenericUI Media Playback")
}
