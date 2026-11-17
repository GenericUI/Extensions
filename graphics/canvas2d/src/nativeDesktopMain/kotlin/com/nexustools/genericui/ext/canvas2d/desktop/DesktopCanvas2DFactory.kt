package com.nexustools.genericui.ext.canvas2d.desktop

import com.nexustools.genericui.desktop.canvas.DesktopCanvas2DNativeImpl

internal expect fun createPlatformDesktopCanvas2DNativeImpl(
    initialWidth: Int,
    initialHeight: Int,
    preferVulkan: Boolean
): DesktopCanvas2DNativeImpl<*>
