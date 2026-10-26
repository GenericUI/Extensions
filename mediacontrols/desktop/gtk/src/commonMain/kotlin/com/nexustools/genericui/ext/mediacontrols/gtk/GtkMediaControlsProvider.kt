package com.nexustools.genericui.ext.mediacontrols.gtk

import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaControlsSession
import com.nexustools.genericui.window.IWindow

/**
 * Provider registering [GtkMediaControlsImpl] with [MediaControlsRegistry] on Linux GTK.
 */
object GtkMediaControlsProvider {
    fun register() {
        MediaControlsRegistry.factory = { GtkMediaControlsImpl() }
    }

    fun createSession(window: IWindow? = null): MediaControlsSession {
        register()
        return MediaControlsSession(window = window, nativeImpl = GtkMediaControlsImpl())
    }
}
