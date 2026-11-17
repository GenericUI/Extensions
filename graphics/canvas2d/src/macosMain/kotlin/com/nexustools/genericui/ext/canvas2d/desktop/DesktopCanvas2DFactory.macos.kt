package com.nexustools.genericui.ext.canvas2d.desktop

import com.nexustools.genericui.desktop.canvas.DesktopCanvas2DNativeImpl
import com.nexustools.genericui.macos.MacosCanvas2DNativeImpl

internal actual fun createPlatformDesktopCanvas2DNativeImpl(
    initialWidth: Int,
    initialHeight: Int,
    preferVulkan: Boolean
): DesktopCanvas2DNativeImpl<*> =
    MacosCanvas2DNativeImpl(
        initialWidth = initialWidth,
        initialHeight = initialHeight,
        preferVulkan = preferVulkan
    )
