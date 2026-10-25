package com.nexustools.genericui.ios

import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

/**
 * iOS `UIView` + `CADisplayLink` implementation of [Canvas2DNativeImpl] backed by a
 * double-buffered [SystemGpuImageBuffer] (`doubleBuffer = true`).
 */
class IosCanvas2DImpl : IosAbstractCanvasImpl<ImageBuffer2DContext>("UIView", "canvas-2d"), Canvas2DNativeImpl<IosNativeView> {
    val imageBuffer: SystemGpuImageBuffer = SystemGpuImageBuffer(
        width = getSurfaceWidth().coerceAtLeast(1),
        height = getSurfaceHeight().coerceAtLeast(1),
        doubleBuffer = true,
        initialAntialias = true
    )

    override fun getRenderingContext(): ImageBuffer2DContext {
        val w = getSurfaceWidth().coerceAtLeast(1)
        val h = getSurfaceHeight().coerceAtLeast(1)
        if (imageBuffer.width != w || imageBuffer.height != h) {
            imageBuffer.resize(w, h)
        }
        return imageBuffer.getContext()
    }
}
