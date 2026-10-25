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
 * (which delegates to `:extensions:canvas2d`'s [com.nexustools.genericui.ext.canvas2d.OffscreenCanvas2D]
 * and `:extensions:gpu`'s [com.nexustools.genericui.ext.gpu.GpuDoubleBufferedSurface])
 * and [LibJpegCodec] frame capture.
 */
open class DesktopCanvas2DNativeImpl<out W : DesktopNativeWidget>(
    nativeWidget: W,
    nativeApiName: String = "Desktop2D",
    initialWidth: Int = 800,
    initialHeight: Int = 600
) : DesktopBaseNativeImpl<W>(nativeWidget, "canvas-2d"), Canvas2DNativeImpl<W> {

    protected val contextImpl: DesktopCanvas2DContextImpl = DesktopCanvas2DContextImpl(
        width = initialWidth,
        height = initialHeight,
        nativeApiName = nativeApiName
    )
    protected var vblankActive: Boolean = false
    protected var vblankCallback: ((Double?) -> Unit)? = null
    protected var passThroughEvents: Boolean = false
    protected var devicePixelRatioVal: Float = 1.0f

    init {
        nativeWidget.attributes["canvas2DNativeImpl"] = this
        nativeWidget.attributes["canvas2DContext"] = contextImpl
        nativeWidget.attributes["offscreenCanvas2D"] = contextImpl.offscreenCanvas
        nativeWidget.attributes["captureRgba"] = { contextImpl.presentFrontBufferRgba() }
        nativeWidget.attributes["captureBgra"] = { contextImpl.presentFrontBufferBgra() }
        nativeWidget.attributes["surfaceWidth"] = { contextImpl.width }
        nativeWidget.attributes["surfaceHeight"] = { contextImpl.height }
        nativeWidget.attributes["resizeSurface"] = { w: Int, h: Int -> contextImpl.resizeSurface(w, h) }
    }

    val renderingContextImpl: DesktopCanvas2DContextImpl
        get() = contextImpl

    fun isVBlankLoopActive(): Boolean = vblankActive

    fun dispatchVBlank(timestampMs: Double?) {
        val cb = vblankCallback ?: return
        cb(timestampMs)
        contextImpl.swapBuffers()
    }

    override fun getRenderingContext(): Canvas2DContext = contextImpl

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        vblankActive = active
        val doubleBufferedCallback: (Double?) -> Unit = { ts ->
            onVBlankFrame(ts)
            contextImpl.swapBuffers()
        }
        vblankCallback = if (active) doubleBufferedCallback else null
        if (active) {
            nativeWidget.attributes["vblankDispatch"] = doubleBufferedCallback
        } else {
            nativeWidget.attributes.remove("vblankDispatch")
        }
    }

    override fun requestSingleRender() {
        vblankCallback?.invoke(null)
        contextImpl.swapBuffers()
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
