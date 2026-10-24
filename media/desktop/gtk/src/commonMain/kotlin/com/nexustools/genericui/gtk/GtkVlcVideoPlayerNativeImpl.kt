package com.nexustools.genericui.gtk

import com.nexustools.genericui.ext.media.desktop.libvlc.DesktopLibVlcVideoPlayerImpl

/**
 * GTK 4 `libvlc` (`libvlc.so`) video player implementation calling into `:extensions:media:desktop:shared`
 * ([DesktopLibVlcVideoPlayerImpl]) and binding its surface handle via `libvlc_media_player_set_xwindow`.
 */
class GtkVlcVideoPlayerNativeImpl : DesktopLibVlcVideoPlayerImpl<GtkWidgetHandle>(
    nativeWidget = GtkWidgetHandle("GtkBox_LibVlcVideoHost"),
    platformName = "gtk"
) {
    init {
        bindXWindow(nativeWidget.nativePointer.toUInt())
    }
}
