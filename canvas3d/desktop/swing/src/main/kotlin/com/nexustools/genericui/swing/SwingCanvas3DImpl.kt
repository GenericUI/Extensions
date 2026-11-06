package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLReadFormat
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Java Swing Vulkan / OpenGL ES (`LWJGL` runtime-detected via [SwingVKContextImpl] / [SwingGLContextImpl])
 * implementation of [Canvas3DNativeImpl].
 */
class SwingCanvas3DImpl(
    override val config: Canvas3DConfig = Canvas3DConfig(),
    override val activeBackend: GpuBackendType = GpuBackendType.VULKAN,
    override val activeAcceleration: GpuAcceleration = GpuAcceleration.HARDWARE,
    initialWidth: Int = 800,
    initialHeight: Int = 600
) : SwingAbstractCanvasImpl<GpuRenderContext>(JPanel()), Canvas3DNativeImpl<JComponent> {

    private var surfaceW: Int = initialWidth.coerceAtLeast(1)
    private var surfaceH: Int = initialHeight.coerceAtLeast(1)

    private val renderContext: GpuRenderContext = when (activeBackend) {
        GpuBackendType.VULKAN -> SwingVKContextImpl(
            config = config.vulkan,
            initialWidth = surfaceW,
            initialHeight = surfaceH,
            acceleration = activeAcceleration
        )
        GpuBackendType.OPENGL_ES -> SwingGLContextImpl(
            config = config.gl,
            initialWidth = surfaceW,
            initialHeight = surfaceH,
            acceleration = activeAcceleration
        )
    }

    override val vkContext: VKContext?
        get() = renderContext as? VKContext

    override val glContext: GLContext?
        get() = renderContext as? GLContext

    private var createdListener: ((GpuRenderContext, Int, Int) -> Unit)? = null
    private var resizedListener: ((GpuRenderContext, Int, Int) -> Unit)? = null

    override fun getRenderingContext(): GpuRenderContext = renderContext

    override fun setOnSurfaceCreated(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        createdListener = listener
        listener?.invoke(renderContext, getSurfaceWidth(), getSurfaceHeight())
    }

    override fun setOnSurfaceResized(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        resizedListener = listener
    }

    override fun captureFrameToRgba(): ByteArray = when (val ctx = renderContext) {
        is GLContext -> ctx.readPixels(0, 0, getSurfaceWidth(), getSurfaceHeight(), GLReadFormat.RGBA8)
        is VKContext -> ctx.readPixels(0, 0, getSurfaceWidth(), getSurfaceHeight())
        else -> super.captureFrameToRgba()
    }
}
