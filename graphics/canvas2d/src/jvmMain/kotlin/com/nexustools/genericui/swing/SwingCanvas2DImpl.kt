package com.nexustools.genericui.swing

import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Swing (`JPanel`) implementation of [Canvas2DNativeImpl] backed by a double-buffered
 * [SystemGpuImageBuffer] (`doubleBuffer = true`) and its [ImageBuffer2DContext], painting
 * the front framebuffer directly inside Swing's `paintComponent`.
 */
class SwingCanvas2DImpl(
    initialWidth: Int = 800,
    initialHeight: Int = 600
) : SwingAbstractCanvasImpl<ImageBuffer2DContext>(SwingCanvas2DPanel()), Canvas2DNativeImpl<JComponent> {

    private class SwingCanvas2DPanel : JPanel() {
        var framebufferProvider: (() -> ByteArray)? = null
        var widthProvider: (() -> Int)? = null
        var heightProvider: (() -> Int)? = null
        private var backingImage: BufferedImage? = null
        private var argbScratch: IntArray = IntArray(0)

        init {
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val w = (widthProvider?.invoke() ?: width).coerceAtLeast(1)
            val h = (heightProvider?.invoke() ?: height).coerceAtLeast(1)
            val rgba = framebufferProvider?.invoke() ?: return
            val pixelCount = w * h
            if (rgba.size < pixelCount * 4) return

            if (argbScratch.size != pixelCount) {
                argbScratch = IntArray(pixelCount)
            }
            val argb = argbScratch
            var srcIdx = 0
            for (i in 0 until pixelCount) {
                val r = rgba[srcIdx].toInt() and 0xFF
                val gCh = rgba[srcIdx + 1].toInt() and 0xFF
                val b = rgba[srcIdx + 2].toInt() and 0xFF
                val a = rgba[srcIdx + 3].toInt() and 0xFF
                argb[i] = (a shl 24) or (r shl 16) or (gCh shl 8) or b
                srcIdx += 4
            }
            var img = backingImage
            if (img == null || img.width != w || img.height != h) {
                img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                backingImage = img
            }
            img.setRGB(0, 0, w, h, argb, 0, w)
            g.drawImage(img, 0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1), null)
        }
    }

    val imageBuffer: SystemGpuImageBuffer = SystemGpuImageBuffer(
        width = initialWidth.coerceAtLeast(1),
        height = initialHeight.coerceAtLeast(1),
        doubleBuffer = true,
        initialAntialias = true
    )

    private val context2D: ImageBuffer2DContext
        get() = imageBuffer.getContext()

    private var onSurfaceResizedListener: ((Int, Int) -> Unit)? = null
    private var onBeforeFramePresentListener: ((Long, Double) -> Unit)? = null
    private var presentedFrameCounter: Long = 0L

    init {
        val panel = nativeWidget as SwingCanvas2DPanel
        panel.framebufferProvider = { context2D.presentFrontBufferRgba() }
        panel.widthProvider = { context2D.width }
        panel.heightProvider = { context2D.height }
    }

    override fun setPreferredSize(size: Size?) {
        super.setPreferredSize(size)
        if (size != null && size.width > 0 && size.height > 0) {
            val prevW = context2D.width
            val prevH = context2D.height
            val nextW = size.width.coerceAtLeast(1)
            val nextH = size.height.coerceAtLeast(1)
            if (imageBuffer.width != nextW || imageBuffer.height != nextH) {
                imageBuffer.resize(nextW, nextH)
            }
            if (prevW != nextW || prevH != nextH) {
                onSurfaceResizedListener?.invoke(nextW, nextH)
            }
        }
    }

    override fun setOnSurfaceResizedListener(listener: ((Int, Int) -> Unit)?) {
        onSurfaceResizedListener = listener
    }

    override fun setOnBeforeFramePresentListener(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {
        onBeforeFramePresentListener = listener
    }

    override fun getSurfaceWidth(): Int = context2D.width
    override fun getSurfaceHeight(): Int = context2D.height

    override fun getRenderingContext(): ImageBuffer2DContext = context2D

    override fun onAfterVBlankFrame() {
        presentedFrameCounter++
        val nowMs = System.nanoTime() / 1_000_000.0
        onBeforeFramePresentListener?.invoke(presentedFrameCounter, nowMs)
        context2D.swapBuffers()
    }

    override fun captureFrameToRgba(): ByteArray =
        context2D.presentFrontBufferRgba()
}
