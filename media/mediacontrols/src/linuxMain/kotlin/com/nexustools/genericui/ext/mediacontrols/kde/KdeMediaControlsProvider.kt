package com.nexustools.genericui.ext.mediacontrols.kde

import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaControlsSession
import com.nexustools.genericui.window.IWindow

/**
 * Provider registering [KdeMediaControlsImpl] with [MediaControlsRegistry] on Linux KDE.
 */
object KdeMediaControlsProvider {
    fun register() {
        MediaControlsRegistry.factory = { KdeMediaControlsImpl() }
    }

    fun createSession(window: IWindow? = null): MediaControlsSession {
        register()
        return MediaControlsSession(window = window, nativeImpl = KdeMediaControlsImpl())
    }
}
