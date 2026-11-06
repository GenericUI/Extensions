package com.nexustools.genericui.android

import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLReadFormat
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl

/**
 * Android `android.view.SurfaceView` / `android.opengl.GLSurfaceView` + `EGL14` / `VK_KHR_android_surface`
 * implementation of [Canvas3DNativeImpl].
 */
class AndroidCanvas3DImpl(
    override val config: Canvas3DConfig = Canvas3DConfig(),
    override val activeBackend: GpuBackendType = GpuBackendType.VULKAN,
    override val activeAcceleration: GpuAcceleration = GpuAcceleration.HARDWARE,
    initialWidth: Int = 800,
    initialHeight: Int = 600
) : AndroidAbstractCanvasImpl<GpuRenderContext>(
    if (activeBackend == GpuBackendType.VULKAN) "android.view.SurfaceView" else "android.opengl.GLSurfaceView"
), Canvas3DNativeImpl<AndroidViewHandle> {

    private val renderContext: GpuRenderContext = when (activeBackend) {
        GpuBackendType.VULKAN -> AndroidVKContextImpl(
            config = config.vulkan,
            initialWidth = initialWidth.coerceAtLeast(1),
            initialHeight = initialHeight.coerceAtLeast(1),
            acceleration = activeAcceleration
        )
        GpuBackendType.OPENGL_ES -> AndroidGLContextImpl(
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

    init {
        surfaceW = initialWidth.coerceAtLeast(1)
        surfaceH = initialHeight.coerceAtLeast(1)
    }

    override fun getRenderingContext(): GpuRenderContext = renderContext

    override fun setOnSurfaceCreated(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        onSurfaceCreatedListener = listener
        listener?.invoke(renderContext, getSurfaceWidth(), getSurfaceHeight())
    }

    override fun setOnSurfaceResized(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        onSurfaceResizedListener = listener
        listener?.invoke(renderContext, getSurfaceWidth(), getSurfaceHeight())
    }

    override fun captureFrameToRgba(): ByteArray = when (val ctx = renderContext) {
        is GLContext -> ctx.readPixels(0, 0, getSurfaceWidth(), getSurfaceHeight(), GLReadFormat.RGBA8)
        is VKContext -> ctx.readPixels(0, 0, getSurfaceWidth(), getSurfaceHeight())
        else -> super.captureFrameToRgba()
    }
}
