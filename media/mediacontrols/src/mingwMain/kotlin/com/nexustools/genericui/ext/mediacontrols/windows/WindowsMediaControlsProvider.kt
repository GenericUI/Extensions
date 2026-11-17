package com.nexustools.genericui.ext.mediacontrols.windows

import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaControlsSession
import com.nexustools.genericui.window.IWindow

/**
 * Provider registering [WindowsMediaControlsImpl] with [MediaControlsRegistry] on Windows (`mingwX64`).
 */
object WindowsMediaControlsProvider {
    fun register() {
        MediaControlsRegistry.factory = { WindowsMediaControlsImpl() }
    }

    fun createSession(window: IWindow? = null): MediaControlsSession {
        register()
        return MediaControlsSession(window = window, nativeImpl = WindowsMediaControlsImpl())
    }
}
