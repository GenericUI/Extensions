package com.nexustools.genericui.desktop.canvas

import com.nexustools.genericui.ext.canvas2d.system.SystemAcceleratedCanvas2DContext

/**
 * Desktop 2D immediate-mode vector and pixel rendering context implementing
 * [com.nexustools.genericui.component.media.Canvas2DContext] for `:windows`, `:macos`, `:gtk`, and `:qt`.
 *
 * Relies strictly on `:extensions:imageprocessing:sharedSystem`'s
 * [com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer] ([imageBuffer]) and
 * `imageBuffer.getContext()` via [SystemAcceleratedCanvas2DContext], with antialiasing enabled by default.
 */
class DesktopCanvas2DContextImpl(
    width: Int = 800,
    height: Int = 600,
    val nativeApiName: String = "Desktop2D",
    doubleBuffer: Boolean = false,
    preferVulkan: Boolean = true,
    initialAntialias: Boolean = true
) : SystemAcceleratedCanvas2DContext(
    width = width,
    height = height,
    doubleBuffer = doubleBuffer,
    initialAntialias = initialAntialias,
    preferVulkan = preferVulkan
)
