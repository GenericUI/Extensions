package com.nexustools.genericui.desktop.canvas

import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.ext.canvas3d.OffscreenCanvas3D
import com.nexustools.genericui.ext.gpu.desktop.DesktopGpuProvider
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.image.codec.LibJpegCodec
import com.nexustools.genericui.nativeimpl.media.GLCanvasNativeImpl

/**
 * Shared desktop base implementation of [GLCanvasNativeImpl] backed by [DesktopGLContextImpl]
 * and `:extensions:canvas3d`'s hardware-accelerated double-buffered [OffscreenCanvas3D]
 * (Google ANGLE `libEGL.dll` + `libGLESv2.dll` on Windows, Metal-backed ANGLE on macOS,
 * `GtkGLArea` on GTK4, and `QOpenGLWidget` ES 3.0 on Qt6).
 */
open class DesktopGLCanvasNativeImpl<out W : DesktopNativeWidget>(
    nativeWidget: W,
    rendererName: String = "ANGLE (Desktop OpenGL ES 3.0)",
    isAngle: Boolean = true
) : DesktopBaseNativeImpl<W>(nativeWidget, "gl-canvas"), GLCanvasNativeImpl<W> {

    val offscreenCanvas3D: OffscreenCanvas3D = OffscreenCanvas3D(
        width = 800,
        height = 600,
        preferVulkan = false,
        gpuContext = DesktopGpuProvider.acquireDesktopContext(preferVulkan = false)
    )

    protected val glContext: DesktopGLContextImpl = DesktopGLContextImpl(
        rendererString = rendererName,
        isAngleBackend = isAngle
    )
    protected var surfaceW: Int = 800
    protected var surfaceH: Int = 600
    protected var vblankActive: Boolean = false
    protected var vblankCallback: ((Double?) -> Unit)? = null
    protected var onSurfaceCreatedListener: ((GLContext, Int, Int) -> Unit)? = null
    protected var onSurfaceResizedListener: ((GLContext, Int, Int) -> Unit)? = null

    init {
        nativeWidget.attributes["offscreenCanvas3D"] = offscreenCanvas3D
        nativeWidget.attributes["captureRgba"] = { offscreenCanvas3D.presentFrontBufferRgba() }
        nativeWidget.attributes["captureBgra"] = { offscreenCanvas3D.presentFrontBufferBgra() }
    }

    override fun getRenderingContext(): GLContext = glContext

    override fun setOnSurfaceCreated(listener: ((GLContext, Int, Int) -> Unit)?) {
        onSurfaceCreatedListener = listener
        listener?.invoke(glContext, surfaceW, surfaceH)
    }

    override fun setOnSurfaceResized(listener: ((GLContext, Int, Int) -> Unit)?) {
        onSurfaceResizedListener = listener
    }

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        vblankActive = active
        val doubleBufferedCallback: (Double?) -> Unit = { ts ->
            onVBlankFrame(ts)
            offscreenCanvas3D.swapBuffers()
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
        offscreenCanvas3D.swapBuffers()
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        nativeWidget.attributes["pointerEventsPassThrough"] = passThrough
    }

    override fun getSurfaceWidth(): Int = surfaceW

    override fun getSurfaceHeight(): Int = surfaceH

    override fun getDevicePixelRatio(): Float = 1.0f

    override fun captureFrameToRgba(): ByteArray =
        glContext.readPixelsRgba(0, 0, surfaceW, surfaceH)

    override fun captureFrameToJpeg(quality: Int): ByteArray {
        val rgba = captureFrameToRgba()
        val buf = ImageBuffer(surfaceW, surfaceH, PixelFormat.RGBA_8888, bytes = rgba)
        return LibJpegCodec.encode(buf, quality)
    }
}
