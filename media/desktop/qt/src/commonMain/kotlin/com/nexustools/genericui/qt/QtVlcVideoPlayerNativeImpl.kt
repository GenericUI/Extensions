package com.nexustools.genericui.qt

import com.nexustools.genericui.ext.media.desktop.libvlc.DesktopLibVlcVideoPlayerImpl

/**
 * Qt 6 `libvlc` video player implementation calling into `:extensions:media:desktop:shared`
 * ([DesktopLibVlcVideoPlayerImpl]) and binding its `QWidget::winId()` surface handle to `libvlc`.
 */
class QtVlcVideoPlayerNativeImpl : DesktopLibVlcVideoPlayerImpl<QtWidgetHandle>(
    nativeWidget = QtWidgetHandle("QWidget_LibVlcVideoHost"),
    platformName = "qt"
) {
    init {
        bindXWindow(nativeWidget.nativePointer.toUInt())
    }
}
