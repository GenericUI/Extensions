package com.nexustools.genericui.swing

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageEncodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.nativeimpl.media.AbstractCanvasNativeImpl
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

/**
 * Shared Swing base implementation for `SwingCanvas2DImpl`, `SwingGLCanvasImpl`, and `SwingVKCanvasImpl`.
 */
abstract class SwingAbstractCanvasImpl<C>(
    panel: JPanel = JPanel()
) : SwingBaseNativeImpl(panel, "canvas"), AbstractCanvasNativeImpl<JComponent, C> {
    override val nativeWidget: JPanel get() = super.nativeWidget as JPanel

    protected var vblankActive: Boolean = false
    protected var vblankCallback: ((Double?) -> Unit)? = null
    protected var passThroughFlag: Boolean = false
    private var vblankTimer: Timer? = null

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        vblankActive = active
        vblankCallback = if (active) onVBlankFrame else null
        vblankTimer?.stop()
        vblankTimer = null
        if (active) {
            vblankTimer = Timer(16) {
                if (vblankActive) {
                    val nowMs = System.nanoTime() / 1_000_000.0
                    vblankCallback?.invoke(nowMs)
                    onAfterVBlankFrame()
                    nativeWidget.repaint()
                }
            }.apply {
                isRepeats = true
                start()
            }
        }
    }

    protected open fun onAfterVBlankFrame() {}

    override fun requestSingleRender() {
        val nowMs = System.nanoTime() / 1_000_000.0
        vblankCallback?.invoke(nowMs)
        onAfterVBlankFrame()
        nativeWidget.repaint()
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        passThroughFlag = passThrough
    }

    override fun getSurfaceWidth(): Int =
        nativeWidget.width.takeIf { it > 0 } ?: getBounds().width.coerceAtLeast(1)

    override fun getSurfaceHeight(): Int =
        nativeWidget.height.takeIf { it > 0 } ?: getBounds().height.coerceAtLeast(1)

    override fun getDevicePixelRatio(): Float = 1.0f

    override fun captureFrameToRgba(): ByteArray {
        val w = getSurfaceWidth().coerceAtLeast(1)
        val h = getSurfaceHeight().coerceAtLeast(1)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            if (nativeWidget.width != w || nativeWidget.height != h) {
                nativeWidget.setSize(w, h)
                nativeWidget.doLayout()
            }
            nativeWidget.printAll(g)
        } finally {
            g.dispose()
        }
        val argb = IntArray(w * h)
        img.getRGB(0, 0, w, h, argb, 0, w)
        val rgba = ByteArray(w * h * 4)
        var i = 0
        var j = 0
        while (i < argb.size) {
            val px = argb[i++]
            rgba[j++] = ((px ushr 16) and 0xFF).toByte()
            rgba[j++] = ((px ushr 8) and 0xFF).toByte()
            rgba[j++] = (px and 0xFF).toByte()
            rgba[j++] = ((px ushr 24) and 0xFF).toByte()
        }
        return rgba
    }

    override fun captureFrameToJpeg(quality: Int): ByteArray {
        val w = getSurfaceWidth().coerceAtLeast(1)
        val h = getSurfaceHeight().coerceAtLeast(1)
        val rgba = captureFrameToRgba()
        val buf = ImageBuffer(width = w, height = h, pixelFormat = PixelFormat.RGBA_8888, bytes = rgba)
        return ImageLoader.encode(buf, ImageEncodeOptions(format = ImageFormat.JPEG, quality = quality))
    }

    override fun dispose() {
        vblankActive = false
        vblankCallback = null
        vblankTimer?.stop()
        vblankTimer = null
        super.dispose()
    }
}
