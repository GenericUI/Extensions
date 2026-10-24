package com.nexustools.genericui.swing

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageEncodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.nativeimpl.media.AbstractCanvasNativeImpl
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Shared Swing base implementation for [SwingCanvas2DImpl], [SwingGLCanvasImpl], and [SwingVKCanvasImpl].
 */
abstract class SwingAbstractCanvasImpl<C>(
    panel: JPanel = JPanel()
) : SwingBaseNativeImpl(panel, "canvas"), AbstractCanvasNativeImpl<JComponent, C> {
    override val nativeWidget: JPanel = super.nativeWidget as JPanel

    protected var vblankActive: Boolean = false
    protected var vblankCallback: ((Double?) -> Unit)? = null
    protected var passThroughFlag: Boolean = false

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        vblankActive = active
        vblankCallback = if (active) onVBlankFrame else null
    }

    override fun requestSingleRender() {
        val nowMs = System.nanoTime() / 1_000_000.0
        vblankCallback?.invoke(nowMs)
        nativeWidget.repaint()
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        passThroughFlag = passThrough
    }

    override fun getSurfaceWidth(): Int = getBounds().width.coerceAtLeast(1)
    override fun getSurfaceHeight(): Int = getBounds().height.coerceAtLeast(1)
    override fun getDevicePixelRatio(): Float = 1.0f

    override fun captureFrameToRgba(): ByteArray =
        ByteArray((getSurfaceWidth() * getSurfaceHeight() * 4).coerceAtLeast(4))

    override fun captureFrameToJpeg(quality: Int): ByteArray {
        val buf = ImageBuffer(getSurfaceWidth(), getSurfaceHeight())
        return ImageLoader.encode(buf, ImageEncodeOptions(format = ImageFormat.JPEG, quality = quality))
    }
}
