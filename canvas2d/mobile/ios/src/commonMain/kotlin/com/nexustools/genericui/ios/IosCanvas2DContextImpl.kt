package com.nexustools.genericui.ios

import com.nexustools.genericui.ext.canvas2d.system.SystemAcceleratedCanvas2DContext

/**
 * iOS hardware-accelerated implementation of [com.nexustools.genericui.component.media.Canvas2DContext]
 * backed directly by `:extensions:imageprocessing:sharedSystem`'s
 * [com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer] and `imageBuffer.getContext()`.
 */
class IosCanvas2DContextImpl(
    width: Int = 390,
    height: Int = 600,
    doubleBuffer: Boolean = false
) : SystemAcceleratedCanvas2DContext(
    width = width,
    height = height,
    doubleBuffer = doubleBuffer,
    initialAntialias = true,
    preferVulkan = true
)
