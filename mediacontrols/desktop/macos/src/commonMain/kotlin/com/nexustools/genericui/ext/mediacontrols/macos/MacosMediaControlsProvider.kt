package com.nexustools.genericui.ext.mediacontrols.macos

import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaControlsSession
import com.nexustools.genericui.window.IWindow

/**
 * Provider registering [MacosMediaControlsImpl] with [MediaControlsRegistry] on macOS.
 */
object MacosMediaControlsProvider {
    fun register() {
        MediaControlsRegistry.factory = { MacosMediaControlsImpl() }
    }

    fun createSession(window: IWindow? = null): MediaControlsSession {
        register()
        return MediaControlsSession(window = window, nativeImpl = MacosMediaControlsImpl())
    }
}
