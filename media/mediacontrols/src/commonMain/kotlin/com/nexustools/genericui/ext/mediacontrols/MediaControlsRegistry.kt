package com.nexustools.genericui.ext.mediacontrols

/**
 * Registry that supplies the active platform's [MediaControlsNativeImpl] factory.
 *
 * Fails fast with [IllegalStateException] when no platform provider has been installed.
 */
object MediaControlsRegistry {
    var factory: (() -> MediaControlsNativeImpl)? = defaultPlatformMediaControlsFactory()

    fun resetToPlatformDefault() {
        factory = defaultPlatformMediaControlsFactory()
    }

    fun createNativeImpl(): MediaControlsNativeImpl =
        factory?.invoke()
            ?: throw IllegalStateException("No MediaControlsNativeImpl factory installed for the active platform.")
}
