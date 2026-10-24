package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

/**
 * Web `<canvas>` 2D implementation ([Canvas2DNativeImpl]) with `requestAnimationFrame` VBlank loop
 * and high-resolution `deltaMs` tracking.
 */
class WebCanvas2DImpl : WebAbstractCanvasImpl<Canvas2DContext>("canvas-2d"), Canvas2DNativeImpl<WebDomElement> {
    private val context = WebCanvas2DContextImpl()

    override fun getRenderingContext(): Canvas2DContext {
        context.width = getSurfaceWidth()
        context.height = getSurfaceHeight()
        return context
    }
}
