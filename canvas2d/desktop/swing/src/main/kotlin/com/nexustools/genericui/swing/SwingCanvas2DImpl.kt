package com.nexustools.genericui.swing

import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Swing (`JPanel`) implementation of [Canvas2DNativeImpl] backed by a double-buffered
 * [SystemGpuImageBuffer] (`doubleBuffer = true`) and its [ImageBuffer2DContext].
 */
class SwingCanvas2DImpl(
    initialWidth: Int = 800,
    initialHeight: Int = 600
) : SwingAbstractCanvasImpl<ImageBuffer2DContext>(JPanel()), Canvas2DNativeImpl<JComponent> {

    val imageBuffer: SystemGpuImageBuffer = SystemGpuImageBuffer(
        width = initialWidth,
        height = initialHeight,
        doubleBuffer = true,
        initialAntialias = true
    )

    private val context2D: ImageBuffer2DContext
        get() = imageBuffer.getContext()

    override fun getRenderingContext(): ImageBuffer2DContext = context2D

    override fun captureFrameToRgba(): ByteArray =
        context2D.presentFrontBufferRgba()
}
