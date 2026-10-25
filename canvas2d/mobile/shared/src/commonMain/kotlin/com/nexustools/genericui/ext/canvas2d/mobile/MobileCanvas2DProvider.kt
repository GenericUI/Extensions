package com.nexustools.genericui.ext.canvas2d.mobile

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension
import com.nexustools.genericui.ext.canvas2d.system.SystemAcceleratedCanvas2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer

/**
 * Registers mobile hardware-accelerated 2D rendering contexts ([SystemAcceleratedCanvas2DContext]
 * backed by `:extensions:imageprocessing:sharedSystem`'s [SystemGpuImageBuffer]) into [Canvas2DExtension].
 */
object MobileCanvas2DProvider {
    fun install() {
        SystemGpuImageBuffer.installFactory(preferVulkan = true)
        Canvas2DExtension.registerContextFactory { width, height ->
            createMobileCanvas2DContext(width, height)
        }
    }

    fun createMobileCanvas2DContext(width: Int, height: Int): Canvas2DContext =
        SystemAcceleratedCanvas2DContext(
            width = width,
            height = height,
            doubleBuffer = false,
            initialAntialias = true,
            preferVulkan = true
        )
}
