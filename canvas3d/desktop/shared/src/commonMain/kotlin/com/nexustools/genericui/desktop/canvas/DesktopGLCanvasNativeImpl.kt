package com.nexustools.genericui.desktop.canvas

import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.image.codec.LibJpegCodec
import com.nexustools.genericui.nativeimpl.media.GLCanvasNativeImpl

/**
 * Shared desktop base implementation of [GLCanvasNativeImpl] backed by [DesktopGLContextImpl]
 * (Google ANGLE `libEGL.dll` + `libGLESv2.dll` on Windows, Metal-backed ANGLE on macOS,
 * `GtkGLArea` on GTK4, and `QOpenGLWidget` ES 3.0 on Qt6).
 */
open class DesktopGLCanvasNativeImpl<out W : DesktopNativeWidget>(
    nativeWidget: W,
    rendererName: String = "ANGLE (Desktop OpenGL ES 3.0)",
    isAngle: Boolean = true
) : DesktopBaseNativeImpl<W>(nativeWidget, "gl-canvas"), GLCanvasNativeImpl<W> {

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
        vblankCallback = if (active) onVBlankFrame else null
    }

    override fun requestSingleRender() {
        vblankCallback?.invoke(null)
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
