package com.nexustools.genericui.ext.canvas2d.web

import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension
import com.nexustools.genericui.ext.imageprocessing.context2d.WebImageBuffer
import com.nexustools.genericui.web.WebCanvas2DImpl
import com.nexustools.genericui.web.WebScrollCanvas2DImpl

/**
 * Registers `:web`'s 2D context ([WebImageBuffer] -> `ImageBuffer2DContext`),
 * [WebCanvas2DImpl], and [WebScrollCanvas2DImpl] into [Canvas2DExtension].
 */
object WebCanvas2DProvider {
    fun install() {
        WebImageBuffer.installFactory()
        Canvas2DExtension.registerContextFactory { width, height ->
            WebImageBuffer(width = width, height = height).getContext()
        }
        Canvas2DExtension.registerCanvas2DFactory { _, _ ->
            WebCanvas2DImpl()
        }
        Canvas2DExtension.registerScrollCanvas2DFactory { viewportWidth, viewportHeight, contentWidth, contentHeight ->
            WebScrollCanvas2DImpl(
                initialViewportWidth = viewportWidth,
                initialViewportHeight = viewportHeight,
                initialContentWidth = contentWidth,
                initialContentHeight = contentHeight
            )
        }
    }
}
