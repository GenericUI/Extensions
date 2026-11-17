package com.nexustools.genericui.ext.mediacontrols.swing

import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaControlsSession
import com.nexustools.genericui.window.IWindow

/**
 * Provider registering [SwingMediaControlsImpl] with [MediaControlsRegistry] on JVM Swing.
 */
object SwingMediaControlsProvider {
    fun register() {
        MediaControlsRegistry.factory = { SwingMediaControlsImpl() }
    }

    fun createSession(window: IWindow? = null): MediaControlsSession {
        register()
        return MediaControlsSession(window = window, nativeImpl = SwingMediaControlsImpl())
    }
}
