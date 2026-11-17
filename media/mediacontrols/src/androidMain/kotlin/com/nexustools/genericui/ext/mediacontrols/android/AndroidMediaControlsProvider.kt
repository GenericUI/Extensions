package com.nexustools.genericui.ext.mediacontrols.android

import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaControlsSession
import com.nexustools.genericui.window.IWindow

/**
 * Provider registering [AndroidMediaControlsImpl] (`android.media.session.MediaSession`) with
 * [MediaControlsRegistry] on Android.
 */
object AndroidMediaControlsProvider {
    fun supportsTarget(target: BackendTarget): Boolean = target == BackendTarget.Android

    fun register() {
        MediaControlsRegistry.factory = { AndroidMediaControlsImpl() }
    }

    fun createSession(window: IWindow? = null): MediaControlsSession {
        register()
        return MediaControlsSession(window = window, nativeImpl = AndroidMediaControlsImpl())
    }
}
