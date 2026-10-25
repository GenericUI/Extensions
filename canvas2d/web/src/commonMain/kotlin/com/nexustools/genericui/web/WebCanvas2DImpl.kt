package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.ext.imageprocessing.web.WebCanvasImageBuffer
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

/**
 * Web `<canvas>` 2D implementation ([Canvas2DNativeImpl]) whose [Canvas2DContext] operates
 * **directly** on the underlying `CanvasRenderingContext2D` (`canvas.getContext("2d")`) taken from
 * this component's `<canvas>` HTML element ([nativeWidget]).
 */
class WebCanvas2DImpl : WebAbstractCanvasImpl<Canvas2DContext>("canvas-2d"), Canvas2DNativeImpl<WebDomElement> {
    private val context = WebCanvas2DContextImpl(
        width = getSurfaceWidth(),
        height = getSurfaceHeight(),
        existingCanvasElement = nativeWidget,
        initialAntialias = true
    )

    val imageBuffer: WebCanvasImageBuffer
        get() = context.imageBuffer

    override fun getRenderingContext(): Canvas2DContext {
        val w = getSurfaceWidth()
        val h = getSurfaceHeight()
        if (context.width != w || context.height != h) {
            context.resizeSurface(w, h)
        }
        return context
    }

    override fun captureFrameToRgba(): ByteArray =
        context.getImageDataRgba(0, 0, context.width, context.height)
}
