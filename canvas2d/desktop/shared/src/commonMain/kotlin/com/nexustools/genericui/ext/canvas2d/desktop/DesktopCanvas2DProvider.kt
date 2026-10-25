package com.nexustools.genericui.ext.canvas2d.desktop

import com.nexustools.genericui.desktop.canvas.DesktopCanvas2DContextImpl
import com.nexustools.genericui.desktop.canvas.DesktopCanvas2DNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension
import com.nexustools.genericui.ext.gpu.desktop.DesktopGpuProvider

/**
 * Registers `:desktop`'s [DesktopCanvas2DContextImpl] and [DesktopCanvas2DNativeImpl] into [Canvas2DExtension]
 * alongside [DesktopGpuProvider] (Vulkan 1.3 primary, OpenGL ES 3.0 / Google ANGLE fallback).
 */
object DesktopCanvas2DProvider {
    fun install(
        nativeApiName: String = "Desktop2D",
        enableVulkan: Boolean = true,
        enableAngleOpenGlEs: Boolean = true
    ) {
        DesktopGpuProvider.install(enableVulkan = enableVulkan, enableAngleOpenGlEs = enableAngleOpenGlEs)

        Canvas2DExtension.registerContextFactory { width, height ->
            DesktopCanvas2DContextImpl(width = width, height = height, nativeApiName = nativeApiName)
        }
        Canvas2DExtension.registerCanvas2DFactory { width, height ->
            DesktopCanvas2DNativeImpl(
                nativeWidget = DesktopNativeWidget(nativeApiName, "canvas-2d"),
                nativeApiName = nativeApiName,
                initialWidth = width,
                initialHeight = height
            )
        }
    }
}
