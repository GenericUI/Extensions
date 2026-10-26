package com.nexustools.genericui.ext.mediacontrols

/**
 * Registry that supplies the active platform's [MediaControlsNativeImpl] factory, falling back to
 * [DefaultNoOpMediaControlsNativeImpl] when no platform provider has been registered.
 */
object MediaControlsRegistry {
    var factory: (() -> MediaControlsNativeImpl)? = null

    fun createNativeImpl(): MediaControlsNativeImpl =
        factory?.invoke() ?: DefaultNoOpMediaControlsNativeImpl()
}
