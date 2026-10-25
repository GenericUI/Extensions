package com.nexustools.genericui.desktop.canvas

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer
import com.nexustools.genericui.image.codec.LibJpegCodec
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

/**
 * Shared desktop base implementation of [Canvas2DNativeImpl] backed directly by
 * `:extensions:imageprocessing:sharedSystem`'s [SystemGpuImageBuffer] (`contextImpl.imageBuffer`)
 * and `GpuAcceleratedContext2DBackend` (`contextImpl.backend`).
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

    /**
     * Direct access to the unbuffered GL/Vulkan [SystemGpuImageBuffer] surface backing this canvas.
     */
    val imageBuffer: SystemGpuImageBuffer
        get() = contextImpl.imageBuffer

    init {
        nativeWidget.attributes["canvas2DNativeImpl"] = this
        nativeWidget.attributes["canvas2DContext"] = contextImpl
        nativeWidget.attributes["imageBuffer"] = contextImpl.imageBuffer
        nativeWidget.attributes["captureRgba"] = { contextImpl.presentFrontBufferRgba() }
        nativeWidget.attributes["captureBgra"] = { contextImpl.presentFrontBufferBgra() }
        nativeWidget.attributes["frameBufferRgba"] = { contextImpl.presentFrontBufferRgba() }
        nativeWidget.attributes["frameBufferBgra"] = { contextImpl.presentFrontBufferBgra() }
        nativeWidget.attributes["swapBuffers"] = { contextImpl.swapBuffers() }
        nativeWidget.attributes["swapCount"] = { contextImpl.swapCount }
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
        val directFlushCallback: (Double?) -> Unit = { ts ->
            onVBlankFrame(ts)
            contextImpl.swapBuffers()
        }
        vblankCallback = if (active) directFlushCallback else null
        if (active) {
            nativeWidget.attributes["vblankDispatch"] = directFlushCallback
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
        contextImpl.presentFrontBufferRgba()

    override fun captureFrameToJpeg(quality: Int): ByteArray =
        LibJpegCodec.encode(contextImpl.imageBuffer, quality)
}
