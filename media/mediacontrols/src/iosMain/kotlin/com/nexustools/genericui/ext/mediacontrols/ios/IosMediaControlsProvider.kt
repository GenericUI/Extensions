package com.nexustools.genericui.ext.mediacontrols.ios

import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaControlsSession
import com.nexustools.genericui.window.IWindow

/**
 * Provider registering [IosMediaControlsImpl] (`MPNowPlayingInfoCenter` + `MPRemoteCommandCenter`)
 * with [MediaControlsRegistry] for both [BackendTarget.IOS] and [BackendTarget.IPadOS].
 */
object IosMediaControlsProvider {
    fun supportsTarget(target: BackendTarget): Boolean = target.isIosFamily

    fun register() {
        MediaControlsRegistry.factory = { IosMediaControlsImpl() }
    }

    fun createSession(window: IWindow? = null): MediaControlsSession {
        register()
        return MediaControlsSession(window = window, nativeImpl = IosMediaControlsImpl())
    }
}
