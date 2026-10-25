package com.nexustools.genericui.ext.canvas2d.web

import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension
import com.nexustools.genericui.ext.imageprocessing.web.WebCanvasImageBuffer
import com.nexustools.genericui.web.WebCanvas2DContextImpl
import com.nexustools.genericui.web.WebCanvas2DImpl

/**
 * Registers `:web`'s HTML5 `<canvas>` 2D context ([WebCanvas2DContextImpl] backed by
 * `:extensions:imageprocessing:web`'s [WebCanvasImageBuffer]) and [WebCanvas2DImpl] into [Canvas2DExtension].
 */
object WebCanvas2DProvider {
    fun install() {
        WebCanvasImageBuffer.installFactory()
        Canvas2DExtension.registerContextFactory { width, height ->
            WebCanvas2DContextImpl(width = width, height = height)
        }
        Canvas2DExtension.registerCanvas2DFactory { _, _ ->
            WebCanvas2DImpl()
        }
    }
}
