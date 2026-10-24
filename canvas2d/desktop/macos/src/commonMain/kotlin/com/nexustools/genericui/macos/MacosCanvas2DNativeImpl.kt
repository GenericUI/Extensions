package com.nexustools.genericui.macos

import com.nexustools.genericui.desktop.canvas.DesktopCanvas2DNativeImpl

/**
 * macOS CoreGraphics (`CGContextRef` + CoreText `CTLineRef`) implementation of
 * [com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl] synchronized with `CADisplayLink` / `CVDisplayLink`.
 */
class MacosCanvas2DNativeImpl : DesktopCanvas2DNativeImpl<MacosViewHandle>(
    nativeWidget = MacosViewHandle("GenericUICoreGraphicsView"),
    nativeApiName = "CoreGraphics (CGContextRef) + CoreText"
)
