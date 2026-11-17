package com.nexustools.genericui.ext.canvas2d.desktop

import com.nexustools.genericui.desktop.canvas.DesktopCanvas2DNativeImpl
import com.nexustools.genericui.windows.WindowsCanvas2DNativeImpl

internal actual fun createPlatformDesktopCanvas2DNativeImpl(
    initialWidth: Int,
    initialHeight: Int,
    preferVulkan: Boolean
): DesktopCanvas2DNativeImpl<*> =
    WindowsCanvas2DNativeImpl(
        initialWidth = initialWidth,
        initialHeight = initialHeight,
        preferVulkan = preferVulkan
    )
