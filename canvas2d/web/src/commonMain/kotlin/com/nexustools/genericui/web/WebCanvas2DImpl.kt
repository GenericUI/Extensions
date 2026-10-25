package com.nexustools.genericui.web

import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.WebImageBuffer
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

/**
 * Web `<canvas>` 2D implementation ([Canvas2DNativeImpl]) whose [ImageBuffer2DContext] operates
 * **directly** on the browser's native 2D rendering context via [WebImageBuffer] without
 * dirty-tracking or manual double-buffer swap overhead.
 */
class WebCanvas2DImpl : WebAbstractCanvasImpl<ImageBuffer2DContext>("canvas-2d"), Canvas2DNativeImpl<WebDomElement> {
    val imageBuffer: WebImageBuffer = WebImageBuffer(
        width = getSurfaceWidth().coerceAtLeast(1),
        height = getSurfaceHeight().coerceAtLeast(1),
        doubleBuffer = false,
        initialAntialias = true,
        existingSurfaceElement = nativeWidget
    )

    override fun getRenderingContext(): ImageBuffer2DContext {
        val w = getSurfaceWidth().coerceAtLeast(1)
        val h = getSurfaceHeight().coerceAtLeast(1)
        if (imageBuffer.width != w || imageBuffer.height != h) {
            imageBuffer.resize(w, h)
        }
        return imageBuffer.getContext()
    }

    override fun captureFrameToRgba(): ByteArray =
        imageBuffer.readPixelsRgba(0, 0, imageBuffer.width, imageBuffer.height)
}
