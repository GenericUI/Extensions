package com.nexustools.genericui.ext.mediacontrols

/**
 * Resolves the built-in [MediaControlsNativeImpl] factory for the active platform so
 * [MediaControlsSession] works automatically out of the box even before generated launcher code runs.
 */
internal expect fun defaultPlatformMediaControlsFactory(): (() -> MediaControlsNativeImpl)?
