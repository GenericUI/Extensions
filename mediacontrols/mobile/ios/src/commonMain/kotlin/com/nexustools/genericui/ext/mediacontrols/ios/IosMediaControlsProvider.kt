package com.nexustools.genericui.ext.mediacontrols.ios

import com.nexustools.genericui.ext.mediacontrols.DefaultNoOpMediaControlsNativeImpl
import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaControlsSession
import com.nexustools.genericui.window.IWindow

/**
 * Lightweight no-op provider for `:extensions:mediacontrols:mobile:ios` using
 * [DefaultNoOpMediaControlsNativeImpl].
 */
object IosMediaControlsProvider {
    fun register() {
        MediaControlsRegistry.factory = { DefaultNoOpMediaControlsNativeImpl() }
    }

    fun createSession(window: IWindow? = null): MediaControlsSession {
        register()
        return MediaControlsSession(window = window, nativeImpl = DefaultNoOpMediaControlsNativeImpl())
    }
}
