package com.nexustools.genericui.ext.canvas2d.mobile

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension

/**
 * Registers mobile (`android.graphics.Canvas` / iOS `CGContext`) 2D rendering contexts into [Canvas2DExtension].
 */
object MobileCanvas2DProvider {
    fun install() {
        val defaultContext = Canvas2DExtension.createContext(800, 600)
        Canvas2DExtension.registerContextFactory { width, height ->
            if (width == defaultContext.width && height == defaultContext.height) {
                defaultContext
            } else {
                createMobileCanvas2DContext(width, height)
            }
        }
    }

    fun createMobileCanvas2DContext(width: Int, height: Int): Canvas2DContext =
        Canvas2DExtension.createContext(width, height)
}
