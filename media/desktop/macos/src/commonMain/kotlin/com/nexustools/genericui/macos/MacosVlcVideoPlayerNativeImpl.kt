package com.nexustools.genericui.macos

import com.nexustools.genericui.ext.media.desktop.libvlc.DesktopLibVlcVideoPlayerImpl

/**
 * macOS `libvlc` (`libvlc.dylib`) video player implementation calling into `:extensions:media:desktop:shared`
 * ([DesktopLibVlcVideoPlayerImpl]) and binding its `NSView` handle via `libvlc_media_player_set_nsobject`.
 */
class MacosVlcVideoPlayerNativeImpl : DesktopLibVlcVideoPlayerImpl<MacosViewHandle>(
    nativeWidget = MacosViewHandle("GenericUILibVlcVideoView"),
    platformName = "macos"
) {
    init {
        bindMacosNsView(nativeWidget.nativePointer)
    }
}
