package com.nexustools.genericui.ext.mediacontrols

/**
 * Event delivered to a [MediaControlListener] when the user interacts with OS transport controls,
 * taskbar thumbnail preview buttons, or hardware multimedia keys.
 */
data class MediaControlEvent(
    val action: MediaControlAction,
    val seekPositionMs: Long? = null,
    val source: MediaControlEventSource = MediaControlEventSource.SYSTEM_TRANSPORT
)
