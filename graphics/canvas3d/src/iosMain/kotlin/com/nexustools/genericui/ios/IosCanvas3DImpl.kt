@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLReadFormat
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.desktop.canvas.DesktopGLContextImpl
import com.nexustools.genericui.desktop.canvas.DesktopVKContextImpl
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

/**
 * iOS & iPadOS `MTKView` (`MoltenVK` / `MetalANGLE`) + `CADisplayLink` implementation of [Canvas3DNativeImpl],
 * presenting swapped 3D color attachments onto a UIKit [UIImageView] layer via [UikitViewHandle].
 */
class IosCanvas3DImpl(
    override val config: Canvas3DConfig = Canvas3DConfig(),
    override val activeBackend: GpuBackendType = GpuBackendType.VULKAN,
    override val activeAcceleration: GpuAcceleration = GpuAcceleration.HARDWARE,
    initialWidth: Int = 800,
    initialHeight: Int = 600
) : IosAbstractCanvasImpl<GpuRenderContext>(
    backingView = UIImageView(
        frame = CGRectMake(0.0, 0.0, initialWidth.coerceAtLeast(1).toDouble(), initialHeight.coerceAtLeast(1).toDouble())
    ).apply {
        contentMode = UIViewContentMode.UIViewContentModeScaleToFill
        clipsToBounds = true
    },
    classPrefix = "canvas-3d",
    initialSurfaceWidth = initialWidth.coerceAtLeast(1),
    initialSurfaceHeight = initialHeight.coerceAtLeast(1)
), Canvas3DNativeImpl<UikitViewHandle> {

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
    private var onBeforeFramePresentListener: ((Long, Double) -> Unit)? = null
    private var presentedFrameCounter: Long = 0L

    override fun setPreferredSize(size: Size?) {
        super.setPreferredSize(size)
        if (size != null && size.width > 0 && size.height > 0) {
            val prevW = getSurfaceWidth()
            val prevH = getSurfaceHeight()
            val nextW = size.width.coerceAtLeast(1)
            val nextH = size.height.coerceAtLeast(1)
            initialSurfaceWidth = nextW
            initialSurfaceHeight = nextH
            when (val ctx = renderContext) {
                is DesktopGLContextImpl -> ctx.resizeSurface(nextW, nextH)
                is DesktopVKContextImpl -> ctx.resizeSwapchain(nextW, nextH)
                else -> Unit
            }
            if (prevW != nextW || prevH != nextH) {
                onSurfaceResizedListener?.invoke(renderContext, nextW, nextH)
            }
        }
    }

    override fun getRenderingContext(): GpuRenderContext = renderContext

    override fun setOnSurfaceCreated(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        onSurfaceCreatedListener = listener
        listener?.invoke(renderContext, getSurfaceWidth(), getSurfaceHeight())
    }

    override fun setOnSurfaceResized(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        onSurfaceResizedListener = listener
    }

    override fun setOnBeforeFramePresentListener(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {
        onBeforeFramePresentListener = listener
    }

    override fun onAfterVBlankFrame() {
        presentedFrameCounter++
        onBeforeFramePresentListener?.invoke(presentedFrameCounter, lastDeltaMs)
        val w = getSurfaceWidth().coerceAtLeast(1)
        val h = getSurfaceHeight().coerceAtLeast(1)
        val rgba = captureFrameToRgba()
        if (rgba.size >= w * h * 4) {
            val frameBuffer = ImageBuffer(
                width = w,
                height = h,
                pixelFormat = PixelFormat.RGBA_8888,
                bytes = rgba
            )
            UikitGeometryAndColorUtil.imageBufferToUiImage(frameBuffer)?.let { uiImg ->
                (uiView as? UIImageView)?.image = uiImg
            }
        }
    }

    override fun captureFrameToRgba(): ByteArray = when (val ctx = renderContext) {
        is GLContext -> ctx.readPixels(0, 0, getSurfaceWidth(), getSurfaceHeight(), GLReadFormat.RGBA8)
        is VKContext -> ctx.readPixels(0, 0, getSurfaceWidth(), getSurfaceHeight())
        else -> super.captureFrameToRgba()
    }

    override fun dispose() {
        onSurfaceCreatedListener = null
        onSurfaceResizedListener = null
        onBeforeFramePresentListener = null
        super.dispose()
    }
}
