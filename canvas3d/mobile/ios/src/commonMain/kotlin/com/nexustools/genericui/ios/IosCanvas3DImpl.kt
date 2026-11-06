package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLReadFormat
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl

/**
 * iOS `MTKView` (`MoltenVK` / `MetalANGLE`) + `CADisplayLink` implementation of [Canvas3DNativeImpl].
 */
class IosCanvas3DImpl(
    override val config: Canvas3DConfig = Canvas3DConfig(),
    override val activeBackend: GpuBackendType = GpuBackendType.VULKAN,
    override val activeAcceleration: GpuAcceleration = GpuAcceleration.HARDWARE,
    initialWidth: Int = 800,
    initialHeight: Int = 600
) : IosAbstractCanvasImpl<GpuRenderContext>(
    if (activeBackend == GpuBackendType.VULKAN) "MTKView" else "GLKView",
    "canvas-3d"
), Canvas3DNativeImpl<IosNativeView> {

    private val renderContext: GpuRenderContext = when (activeBackend) {
        GpuBackendType.VULKAN -> IosVKContextImpl(
            config = config.vulkan,
            initialWidth = initialWidth.coerceAtLeast(1),
            initialHeight = initialHeight.coerceAtLeast(1),
            acceleration = activeAcceleration
        )
        GpuBackendType.OPENGL_ES -> IosGLContextImpl(
            config = config.gl,
            initialWidth = initialWidth.coerceAtLeast(1),
            initialHeight = initialHeight.coerceAtLeast(1),
            acceleration = activeAcceleration
        )
    }

    override val vkContext: VKContext?
        get() = renderContext as? VKContext

    override val glContext: GLContext?
        get() = renderContext as? GLContext

    private var onSurfaceCreatedListener: ((GpuRenderContext, Int, Int) -> Unit)? = null
    private var onSurfaceResizedListener: ((GpuRenderContext, Int, Int) -> Unit)? = null

    override fun getRenderingContext(): GpuRenderContext = renderContext

    override fun setOnSurfaceCreated(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        onSurfaceCreatedListener = listener
        listener?.invoke(renderContext, getSurfaceWidth(), getSurfaceHeight())
    }

    override fun setOnSurfaceResized(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        onSurfaceResizedListener = listener
    }

    override fun captureFrameToRgba(): ByteArray = when (val ctx = renderContext) {
        is GLContext -> ctx.readPixels(0, 0, getSurfaceWidth(), getSurfaceHeight(), GLReadFormat.RGBA8)
        is VKContext -> ctx.readPixels(0, 0, getSurfaceWidth(), getSurfaceHeight())
        else -> super.captureFrameToRgba()
    }
}
