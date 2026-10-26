package com.nexustools.genericui.ext.canvas2d.desktop

import com.nexustools.genericui.desktop.canvas.DesktopCanvas2DNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension
import com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer

/**
 * Registers `:desktop`'s [DesktopCanvas2DNativeImpl] and [SystemGpuImageBuffer] into [Canvas2DExtension].
 */
object DesktopCanvas2DProvider {
    fun install(
        nativeApiName: String = "Desktop2D",
        enableVulkan: Boolean = true,
        @Suppress("UNUSED_PARAMETER") enableAngleOpenGlEs: Boolean = true
    ) {
        SystemGpuImageBuffer.installFactory(preferVulkan = enableVulkan)

        Canvas2DExtension.registerContextFactory { width, height ->
            SystemGpuImageBuffer(
                width = width,
                height = height,
                doubleBuffer = true,
                initialAntialias = true,
                preferVulkan = enableVulkan
            ).getContext()
        }
        Canvas2DExtension.registerCanvas2DFactory { width, height ->
            DesktopCanvas2DNativeImpl(
                nativeWidget = DesktopNativeWidget(nativeApiName, "canvas-2d"),
                nativeApiName = nativeApiName,
                initialWidth = width,
                initialHeight = height,
                preferVulkan = enableVulkan
            )
        }
    }
}
