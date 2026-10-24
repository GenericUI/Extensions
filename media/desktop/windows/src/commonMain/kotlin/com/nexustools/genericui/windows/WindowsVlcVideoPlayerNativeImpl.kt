package com.nexustools.genericui.windows

import com.nexustools.genericui.ext.media.desktop.libvlc.DesktopLibVlcVideoPlayerImpl

/**
 * Windows `libvlc` (`libvlc.dll`) video player implementation calling into `:extensions:media:desktop:shared`
 * ([DesktopLibVlcVideoPlayerImpl]) and binding its Win32 `HWND` via `libvlc_media_player_set_hwnd`.
 */
class WindowsVlcVideoPlayerNativeImpl : DesktopLibVlcVideoPlayerImpl<Win32HwndHandle>(
    nativeWidget = Win32HwndHandle("GenericUILibVlcVideoHostW"),
    platformName = "windows"
) {
    init {
        bindWin32Hwnd(nativeWidget.nativePointer)
    }
}
