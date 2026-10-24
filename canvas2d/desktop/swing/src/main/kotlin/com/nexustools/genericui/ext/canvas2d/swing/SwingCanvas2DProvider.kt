package com.nexustools.genericui.ext.canvas2d.swing

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension
import com.nexustools.genericui.swing.SwingCanvas2DContextImpl
import com.nexustools.genericui.swing.SwingCanvas2DImpl

/**
 * Registers JVM/Swing (`Graphics2D` / `BufferedImage`) 2D rendering contexts and [SwingCanvas2DImpl] into [Canvas2DExtension].
 */
object SwingCanvas2DProvider {
    fun install() {
        Canvas2DExtension.registerContextFactory { width, height ->
            SwingCanvas2DContextImpl(width, height)
        }
        Canvas2DExtension.registerCanvas2DFactory { _, _ ->
            SwingCanvas2DImpl()
        }
    }

    fun createSwingCanvas2DContext(width: Int = 800, height: Int = 600): Canvas2DContext =
        SwingCanvas2DContextImpl(width, height)
}
