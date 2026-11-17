package com.nexustools.genericui.ext.mediacontrols

/**
 * Capabilities reported by a platform's [MediaControlsNativeImpl].
 */
data class MediaControlsCapabilities(
    val supportsSystemTransportControls: Boolean = false,
    val supportsTaskbarThumbnailButtons: Boolean = false,
    val supportsKeyboardMediaKeyHook: Boolean = false
)
