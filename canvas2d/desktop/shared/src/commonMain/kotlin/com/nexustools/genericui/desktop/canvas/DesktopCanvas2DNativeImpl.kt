package com.nexustools.genericui.desktop.canvas

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.image.codec.LibJpegCodec
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

/**
 * Shared desktop base implementation of [Canvas2DNativeImpl] backed by [DesktopCanvas2DContextImpl]
 * and [LibJpegCodec] frame capture.
 */
open class DesktopCanvas2DNativeImpl<out W : DesktopNativeWidget>(
    nativeWidget: W,
    nativeApiName: String = "Desktop2D"
) : DesktopBaseNativeImpl<W>(nativeWidget, "canvas-2d"), Canvas2DNativeImpl<W> {

    protected val contextImpl: DesktopCanvas2DContextImpl = DesktopCanvas2DContextImpl(800, 600, nativeApiName)
    protected var vblankActive: Boolean = false
    protected var vblankCallback: ((Double?) -> Unit)? = null
    protected var passThroughEvents: Boolean = false
    protected var devicePixelRatioVal: Float = 1.0f

    override fun getRenderingContext(): Canvas2DContext = contextImpl

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        vblankActive = active
        vblankCallback = if (active) onVBlankFrame else null
    }

    override fun requestSingleRender() {
        vblankCallback?.invoke(null)
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        passThroughEvents = passThrough
        nativeWidget.attributes["pointerEventsPassThrough"] = passThrough
    }

    override fun getSurfaceWidth(): Int = contextImpl.width

    override fun getSurfaceHeight(): Int = contextImpl.height

    override fun getDevicePixelRatio(): Float = devicePixelRatioVal

    override fun captureFrameToRgba(): ByteArray =
        contextImpl.getImageDataRgba(0, 0, contextImpl.width, contextImpl.height)

    override fun captureFrameToJpeg(quality: Int): ByteArray {
        val rgba = captureFrameToRgba()
        val buf = ImageBuffer(contextImpl.width, contextImpl.height, PixelFormat.RGBA_8888, bytes = rgba)
        return LibJpegCodec.encode(buf, quality)
    }
}
