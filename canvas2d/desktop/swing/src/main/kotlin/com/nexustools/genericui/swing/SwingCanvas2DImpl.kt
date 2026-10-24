package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Java2D (`JPanel.paintComponent(Graphics)` + `BufferedImage`) implementation of [Canvas2DNativeImpl].
 */
class SwingCanvas2DImpl : SwingAbstractCanvasImpl<Canvas2DContext>(JPanel()), Canvas2DNativeImpl<JComponent> {
    private val context2D = SwingCanvas2DContextImpl(800, 600)

    override fun getRenderingContext(): Canvas2DContext = context2D

    override fun captureFrameToRgba(): ByteArray =
        context2D.getImageDataRgba(0, 0, context2D.width, context2D.height)
}
