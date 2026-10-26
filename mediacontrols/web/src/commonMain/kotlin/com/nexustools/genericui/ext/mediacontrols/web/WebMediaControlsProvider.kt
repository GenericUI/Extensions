package com.nexustools.genericui.ext.mediacontrols.web

import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaControlsSession
import com.nexustools.genericui.window.IWindow

/**
 * Provider registering [WebMediaControlsImpl] with [MediaControlsRegistry] on Web (`js` / `wasmJs`).
 */
object WebMediaControlsProvider {
    fun register() {
        MediaControlsRegistry.factory = { WebMediaControlsImpl() }
    }

    fun createSession(window: IWindow? = null): MediaControlsSession {
        register()
        return MediaControlsSession(window = window, nativeImpl = WebMediaControlsImpl())
    }
}
