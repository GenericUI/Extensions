package com.nexustools.genericui.desktop.canvas

import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLReadFormat
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopInteractiveSurfaceSpec
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.ext.canvas3d.OffscreenCanvas3D
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.desktop.DesktopGpuDynamicLoader
import com.nexustools.genericui.ext.gpu.desktop.DesktopGpuProvider
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.image.codec.LibJpegCodec
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl
import kotlin.math.min

/**
 * Shared desktop base implementation of [Canvas3DNativeImpl] backed by [DesktopVKContextImpl] /
 * [DesktopGLContextImpl] and `:extensions:canvas3d`'s hardware-accelerated double-buffered
 * [OffscreenCanvas3D], presenting rendered frames via [DesktopInteractiveSurfaceSpec].
 */
open class DesktopCanvas3DNativeImpl<out W : DesktopNativeWidget>(
    nativeWidget: W,
    override val config: Canvas3DConfig = Canvas3DConfig(),
    override val activeBackend: GpuBackendType = GpuBackendType.VULKAN,
    override val activeAcceleration: GpuAcceleration = GpuAcceleration.HARDWARE,
    initialWidth: Int = 800,
    initialHeight: Int = 600
) : DesktopBaseNativeImpl<W>(nativeWidget, "canvas-3d"), Canvas3DNativeImpl<W> {

    protected var surfaceW: Int = initialWidth.coerceAtLeast(1)
    protected var surfaceH: Int = initialHeight.coerceAtLeast(1)

    val offscreenCanvas3D: OffscreenCanvas3D = OffscreenCanvas3D(
        width = surfaceW,
        height = surfaceH,
        preferVulkan = activeBackend == GpuBackendType.VULKAN,
        config = config,
        gpuContext = DesktopGpuProvider.acquireDesktopContext(preferVulkan = activeBackend == GpuBackendType.VULKAN)
    )

    protected val renderContext: GpuRenderContext = when (activeBackend) {
        GpuBackendType.VULKAN -> DesktopVKContextImpl(
            config = config.vulkan,
            initialWidth = surfaceW,
            initialHeight = surfaceH,
            contextInfo = DesktopGpuDynamicLoader.queryVulkanContextInfo(
                acceleration = activeAcceleration,
                apiVersion = config.vulkan.apiVersion
            )
        )
        GpuBackendType.OPENGL_ES -> DesktopGLContextImpl(
            config = config.gl,
            initialWidth = surfaceW,
            initialHeight = surfaceH,
            contextInfo = DesktopGpuDynamicLoader.queryAngleContextInfo(
                acceleration = activeAcceleration,
                version = config.gl.version
            )
        )
    }

    override val vkContext: VKContext?
        get() = renderContext as? VKContext

    override val glContext: GLContext?
        get() = renderContext as? GLContext

    protected var vblankActive: Boolean = false
    protected var vblankCallback: ((Double?) -> Unit)? = null
    protected var onSurfaceCreatedListener: ((GpuRenderContext, Int, Int) -> Unit)? = null
    protected var onSurfaceResizedListener: ((GpuRenderContext, Int, Int) -> Unit)? = null
    private var onBeforeFramePresentListener: ((Long, Double) -> Unit)? = null

    init {
        nativeWidget.attributes["offscreenCanvas3D"] = offscreenCanvas3D
        DesktopInteractiveSurfaceSpec(
            surfaceWidth = { offscreenCanvas3D.width },
            surfaceHeight = { offscreenCanvas3D.height },
            swapCount = { offscreenCanvas3D.swapCount },
            captureBgra = { offscreenCanvas3D.presentFrontBufferBgra() },
            captureRgba = { offscreenCanvas3D.presentFrontBufferRgba() },
            resizeSurface = { w, h ->
                if (w > 0 && h > 0 && (w != surfaceW || h != surfaceH)) {
                    surfaceW = w
                    surfaceH = h
                    offscreenCanvas3D.resize(w, h)
                    when (val ctx = renderContext) {
                        is DesktopGLContextImpl -> ctx.resizeSurface(w, h)
                        is DesktopVKContextImpl -> ctx.resizeSwapchain(w, h)
                        else -> {}
                    }
                    onSurfaceResizedListener?.invoke(renderContext, w, h)
                }
            },
            preferHardwareStretchBlit = true,
            suppressDefaultBorder = true,
            onBeforePresentFrame = { sc, ts ->
                onBeforeFramePresentListener?.invoke(sc, ts ?: 0.0)
            }
        ).attachTo(nativeWidget)
    }

    override fun getRenderingContext(): GpuRenderContext = renderContext

    override fun setOnSurfaceCreated(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        onSurfaceCreatedListener = listener
        listener?.invoke(renderContext, surfaceW, surfaceH)
    }

    override fun setOnSurfaceResized(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        onSurfaceResizedListener = listener
    }

    override fun setOnBeforeFramePresentListener(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {
        onBeforeFramePresentListener = listener
    }

    private fun presentRenderedContextToOffscreenSurface(timestampMs: Double? = null) {
        val rgba = when (val ctx = renderContext) {
            is GLContext -> ctx.readPixels(0, 0, surfaceW, surfaceH, GLReadFormat.RGBA8)
            is VKContext -> ctx.readPixels(0, 0, surfaceW, surfaceH)
            else -> ByteArray(0)
        }
        val dst = offscreenCanvas3D.surface.backBuffer.bytes
        val copyLen = min(rgba.size, dst.size)
        if (copyLen > 0) {
            rgba.copyInto(dst, 0, 0, copyLen)
            offscreenCanvas3D.surface.markBackBufferDirty()
        }
        val nextSwap = offscreenCanvas3D.swapCount + 1L
        onBeforeFramePresentListener?.invoke(nextSwap, timestampMs ?: 0.0)
        nativeWidget.interactiveSurfaceSpec?.dispatchBeforePresentFrame(nextSwap, timestampMs)
        offscreenCanvas3D.swapBuffers()
    }

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        vblankActive = active
        val doubleBufferedCallback: (Double?) -> Unit = { ts ->
            onVBlankFrame(ts)
            presentRenderedContextToOffscreenSurface(ts)
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
        presentRenderedContextToOffscreenSurface(null)
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        nativeWidget.attributes["pointerEventsPassThrough"] = passThrough
    }

    override fun getSurfaceWidth(): Int = surfaceW

    override fun getSurfaceHeight(): Int = surfaceH

    override fun getDevicePixelRatio(): Float = 1.0f

    override fun captureFrameToRgba(): ByteArray = when (val ctx = renderContext) {
        is GLContext -> ctx.readPixels(0, 0, surfaceW, surfaceH, GLReadFormat.RGBA8)
        is VKContext -> ctx.readPixels(0, 0, surfaceW, surfaceH)
        else -> offscreenCanvas3D.presentFrontBufferRgba()
    }

    override fun captureFrameToJpeg(quality: Int): ByteArray {
        val rgba = captureFrameToRgba()
        val buf = ImageBuffer(surfaceW, surfaceH, PixelFormat.RGBA_8888, bytes = rgba)
        return LibJpegCodec.encode(buf, quality)
    }
}
