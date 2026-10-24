package com.nexustools.genericui.macos

import com.nexustools.genericui.desktop.system.DesktopMediaSessionManagerNativeImpl

/**
 * macOS MediaPlayer framework (`MPNowPlayingInfoCenter` & `MPRemoteCommandCenter`) implementation
 * of [com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl].
 */
class MacosMediaSessionManagerNativeImpl : DesktopMediaSessionManagerNativeImpl(
    backendToolkit = "macOS MPNowPlayingInfoCenter / MPRemoteCommandCenter"
)
