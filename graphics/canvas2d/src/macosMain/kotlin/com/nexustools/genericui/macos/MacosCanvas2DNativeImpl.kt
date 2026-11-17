package com.nexustools.genericui.macos

import com.nexustools.genericui.desktop.canvas.DesktopCanvas2DNativeImpl

/**
 * macOS CoreGraphics (`CGContextRef` + CoreText `CTLineRef`) implementation of
 * [com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl] synchronized with `CADisplayLink` / `CVDisplayLink`.
 */
class MacosCanvas2DNativeImpl(
    initialWidth: Int = 800,
    initialHeight: Int = 600,
    preferVulkan: Boolean = true
) : DesktopCanvas2DNativeImpl<MacosViewHandle>(
    nativeWidget = MacosViewHandle("GenericUICoreGraphicsView"),
    nativeApiName = "CoreGraphics (CGContextRef) + CoreText",
    initialWidth = initialWidth,
    initialHeight = initialHeight,
    preferVulkan = preferVulkan
)
