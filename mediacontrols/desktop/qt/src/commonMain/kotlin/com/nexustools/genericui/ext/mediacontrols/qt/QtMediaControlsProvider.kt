package com.nexustools.genericui.ext.mediacontrols.qt

import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaControlsSession
import com.nexustools.genericui.window.IWindow

/**
 * Provider registering [QtMediaControlsImpl] with [MediaControlsRegistry] on Qt.
 */
object QtMediaControlsProvider {
    fun register() {
        MediaControlsRegistry.factory = { QtMediaControlsImpl() }
    }

    fun createSession(window: IWindow? = null): MediaControlsSession {
        register()
        return MediaControlsSession(window = window, nativeImpl = QtMediaControlsImpl())
    }
}
