package com.nexustools.genericui.android

import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

/**
 * Android hardware-accelerated `android.view.View` implementation of [Canvas2DNativeImpl]
 * backed by a double-buffered [SystemGpuImageBuffer] (`doubleBuffer = true`).
 */
class AndroidCanvas2DImpl : AndroidAbstractCanvasImpl<ImageBuffer2DContext>("android.view.View"), Canvas2DNativeImpl<AndroidViewHandle> {
    val imageBuffer: SystemGpuImageBuffer = SystemGpuImageBuffer(
        width = surfaceW,
        height = surfaceH,
        doubleBuffer = true,
        initialAntialias = true
    )

    override fun getRenderingContext(): ImageBuffer2DContext {
        val w = getSurfaceWidth()
        val h = getSurfaceHeight()
        if (imageBuffer.width != w || imageBuffer.height != h) {
            imageBuffer.resize(w, h)
        }
        return imageBuffer.getContext()
    }
}
