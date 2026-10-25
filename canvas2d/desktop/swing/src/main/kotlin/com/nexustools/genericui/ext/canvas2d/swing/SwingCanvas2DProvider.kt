package com.nexustools.genericui.ext.canvas2d.swing

import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer
import com.nexustools.genericui.swing.SwingCanvas2DImpl

/**
 * Registers JVM/Swing 2D rendering contexts ([SystemGpuImageBuffer] -> [ImageBuffer2DContext])
 * and [SwingCanvas2DImpl] into [Canvas2DExtension].
 */
object SwingCanvas2DProvider {
    fun install() {
        SystemGpuImageBuffer.installFactory(preferVulkan = true)
        Canvas2DExtension.registerContextFactory { width, height ->
            createSwingCanvas2DContext(width, height)
        }
        Canvas2DExtension.registerCanvas2DFactory { width, height ->
            SwingCanvas2DImpl(width, height)
        }
    }

    fun createSwingCanvas2DContext(width: Int = 800, height: Int = 600): ImageBuffer2DContext =
        SystemGpuImageBuffer(
            width = width,
            height = height,
            doubleBuffer = true,
            initialAntialias = true
        ).getContext()
}
