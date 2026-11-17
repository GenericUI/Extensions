package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLReadFormat
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.web.WebGpuProvider
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl

/**
 * Web `<canvas>` WebGPU (`WebVKContextImpl`) and WebGL2 (`WebGLContextImpl`) implementation
 * of [Canvas3DNativeImpl] with single-flight `requestAnimationFrame` VBlank loop and high-resolution `deltaMs` tracking.
 */
class WebCanvas3DImpl(
    override val config: Canvas3DConfig = Canvas3DConfig(),
    override val activeBackend: GpuBackendType = GpuBackendType.VULKAN,
    override val activeAcceleration: GpuAcceleration = GpuAcceleration.HARDWARE,
    initialWidth: Int = 800,
    initialHeight: Int = 600
) : WebAbstractCanvasImpl<GpuRenderContext>("canvas-3d"), Canvas3DNativeImpl<WebDomElement> {

    private var surfaceW: Int = initialWidth.coerceAtLeast(1)
    private var surfaceH: Int = initialHeight.coerceAtLeast(1)

    private val useWebGl2: Boolean =
        activeBackend == GpuBackendType.OPENGL_ES ||
            (WebBrowserDomBridge.isBrowserEnvironment && !WebGpuProvider.webGpuAvailable && WebGl2BrowserBridge.isWebGl2Supported())

    private val renderContext: GpuRenderContext = if (useWebGl2) {
        WebGLContextImpl(
            canvasElement = nativeWidget,
            config = config.gl,
            initialWidth = surfaceW,
            initialHeight = surfaceH,
            acceleration = activeAcceleration
        )
    } else {
        WebVKContextImpl(
            canvasElement = nativeWidget,
            config = config.vulkan,
            initialWidth = surfaceW,
            initialHeight = surfaceH,
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
        nativeWidget.setAttribute("width", surfaceW.toString())
        nativeWidget.setAttribute("height", surfaceH.toString())
        nativeWidget.clientWidth = surfaceW
        nativeWidget.clientHeight = surfaceH
        nativeWidget.setAttribute(
            "data-context",
            if (useWebGl2) "webgl2" else "webgpu"
        )
        nativeWidget.setAttribute("data-backend", activeBackend.name)
        nativeWidget.setAttribute("data-acceleration", activeAcceleration.name)
    }

    override fun getRenderingContext(): GpuRenderContext = renderContext

    override fun getSurfaceWidth(): Int = surfaceW

    override fun getSurfaceHeight(): Int = surfaceH

    override fun resizeSurface(width: Int, height: Int) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val changed = safeW != surfaceW || safeH != surfaceH
        surfaceW = safeW
        surfaceH = safeH
        super.resizeSurface(safeW, safeH)
        when (val ctx = renderContext) {
            is WebGLContextImpl -> ctx.resizeWebSurface(safeW, safeH)
            is WebVKContextImpl -> ctx.resizeSwapchain(safeW, safeH)
            else -> {}
        }
        if (changed) {
            onSurfaceResizedListener?.invoke(renderContext, safeW, safeH)
        }
    }

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

    override fun dispose() {
        (renderContext as? WebGLContextImpl)?.destroyContext()
        super.dispose()
    }
}
