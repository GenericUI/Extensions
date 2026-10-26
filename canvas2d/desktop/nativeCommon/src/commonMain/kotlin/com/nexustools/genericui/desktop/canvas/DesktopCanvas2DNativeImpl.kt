package com.nexustools.genericui.desktop.canvas

import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.ext.imageprocessing.context2d.GpuAcceleratedContext2DBackend
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer
import com.nexustools.genericui.image.codec.LibJpegCodec
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

/**
 * Shared desktop base implementation of [Canvas2DNativeImpl] backed directly by a double-buffered
 * `:extensions:imageprocessing:sharedSystem` [SystemGpuImageBuffer] (`doubleBuffer = true`) and
 * its [GpuAcceleratedContext2DBackend] ([ImageBuffer2DContext]).
 */
open class DesktopCanvas2DNativeImpl<out W : DesktopNativeWidget>(
    nativeWidget: W,
    val nativeApiName: String = "Desktop2D",
    initialWidth: Int = 800,
    initialHeight: Int = 600,
    preferVulkan: Boolean = true
) : DesktopBaseNativeImpl<W>(nativeWidget, "canvas-2d"), Canvas2DNativeImpl<W> {

    /**
     * Double-buffered GL/Vulkan [SystemGpuImageBuffer] surface backing this desktop canvas.
     */
    val imageBuffer: SystemGpuImageBuffer = SystemGpuImageBuffer(
        width = initialWidth,
        height = initialHeight,
        doubleBuffer = true,
        initialAntialias = true,
        preferVulkan = preferVulkan
    )

    protected val contextImpl: GpuAcceleratedContext2DBackend
        get() = imageBuffer.context2D

    protected var vblankActive: Boolean = false
    protected var vblankCallback: ((Double?) -> Unit)? = null
    protected var passThroughEvents: Boolean = false
    protected var devicePixelRatioVal: Float = 1.0f
    var onSurfaceResized: ((Int, Int) -> Unit)? = null

    init {
        nativeWidget.attributes["canvas2DNativeImpl"] = this
        nativeWidget.attributes["canvas2DContext"] = contextImpl
        nativeWidget.attributes["imageBuffer"] = imageBuffer
        nativeWidget.attributes["captureRgba"] = { contextImpl.presentFrontBufferRgba() }
        nativeWidget.attributes["captureBgra"] = { contextImpl.presentFrontBufferBgra() }
        nativeWidget.attributes["frameBufferRgba"] = { contextImpl.presentFrontBufferRgba() }
        nativeWidget.attributes["frameBufferBgra"] = { contextImpl.presentFrontBufferBgra() }
        nativeWidget.attributes["swapBuffers"] = { contextImpl.swapBuffers() }
        nativeWidget.attributes["swapCount"] = { contextImpl.swapCount }
        nativeWidget.attributes["surfaceWidth"] = { contextImpl.width }
        nativeWidget.attributes["surfaceHeight"] = { contextImpl.height }
        nativeWidget.attributes["resizeSurface"] = { w: Int, h: Int ->
            contextImpl.resizeSurface(w, h)
            onSurfaceResized?.invoke(w, h)
            @Suppress("UNCHECKED_CAST")
            val attrHook = nativeWidget.attributes["onSurfaceResized"] as? ((Int, Int) -> Unit)
            attrHook?.invoke(w, h)
        }
    }

    override fun setOnSurfaceResizedListener(listener: ((Int, Int) -> Unit)?) {
        onSurfaceResized = listener
    }

    val renderingContextImpl: GpuAcceleratedContext2DBackend
        get() = contextImpl

    fun isVBlankLoopActive(): Boolean = vblankActive

    fun dispatchVBlank(timestampMs: Double?) {
        val cb = vblankCallback ?: return
        cb(timestampMs)
        contextImpl.swapBuffers()
    }

    override fun getRenderingContext(): ImageBuffer2DContext = contextImpl

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
        LibJpegCodec.encode(imageBuffer, quality)
}
