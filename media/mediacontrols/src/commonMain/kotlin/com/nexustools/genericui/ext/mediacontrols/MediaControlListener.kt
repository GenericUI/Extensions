package com.nexustools.genericui.ext.mediacontrols

/**
 * Listener notified when a [MediaControlEvent] is triggered by the system, taskbar preview toolbar,
 * or keyboard media key hook.
 */
fun interface MediaControlListener {
    fun onMediaControlEvent(event: MediaControlEvent)
}
