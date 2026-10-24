package com.nexustools.genericui.windows

import com.nexustools.genericui.desktop.canvas.DesktopCanvas2DNativeImpl

/**
 * Windows Direct2D (`ID2D1Factory1` / `ID2D1DeviceContext` + DirectWrite `IDWriteFactory`)
 * implementation of [com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl] synchronized with `DwmFlush()` VBlank.
 */
class WindowsCanvas2DNativeImpl : DesktopCanvas2DNativeImpl<Win32HwndHandle>(
    nativeWidget = Win32HwndHandle("GenericUIDirect2DCanvasW"),
    nativeApiName = "Direct2D 1.1 + DirectWrite"
)
