package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Extension registry and [ImageBuffer] drawing bridge for `:extensions:canvas2d`,
 * backed by hardware-accelerated [OffscreenCanvas2D] (`:extensions:gpu` Vulkan primary, OpenGL ES / ANGLE fallback).
 */
object Canvas2DExtension {
    private var contextFactory: ((Int, Int) -> Canvas2DContext)? = null
    private var canvas2dNativeFactory: ((Int, Int) -> Canvas2DNativeImpl<*>)? = null

    fun registerContextFactory(factory: (width: Int, height: Int) -> Canvas2DContext) {
        contextFactory = factory
    }

    fun registerCanvas2DFactory(factory: (width: Int, height: Int) -> Canvas2DNativeImpl<*>) {
        canvas2dNativeFactory = factory
    }

    fun createCanvas2DNative(width: Int = 800, height: Int = 600): Canvas2DNativeImpl<*> =
        canvas2dNativeFactory?.invoke(width, height)
            ?: throw IllegalStateException("No Canvas2DNativeImpl provider registered in Canvas2DExtension")

    fun createOffscreenCanvas2D(
        width: Int = 800,
        height: Int = 600,
        preferVulkan: Boolean = true
    ): OffscreenCanvas2D = OffscreenCanvas2D(
        width = width,
        height = height,
        gpuContext = GpuDevice.acquireOffscreenContext(preferVulkan = preferVulkan)
    )

    fun createContext(width: Int = 800, height: Int = 600): Canvas2DContext {
        contextFactory?.let { return it(width, height) }
        return createOffscreenCanvas2D(width, height, preferVulkan = true)
    }

    /**
     * Draws an [ImageBuffer] onto [context] at `(dx, dy)`.
     */
    fun drawImage(
        context: Canvas2DContext,
        buffer: ImageBuffer,
        dx: Float = 0f,
        dy: Float = 0f
    ) {
        val rgba = buffer.toRgba8888()
        context.putImageDataRgba(
            rgbaBytes = rgba.bytes,
            width = rgba.width,
            height = rgba.height,
            dx = dx.roundToInt(),
            dy = dy.roundToInt()
        )
    }

    /**
     * Reads a rectangular region of [context] into a `:common` [ImageBuffer].
     */
    fun extractImageBuffer(
        context: Canvas2DContext,
        x: Int = 0,
        y: Int = 0,
        width: Int = context.width,
        height: Int = context.height
    ): ImageBuffer {
        val safeW = max(1, width)
        val safeH = max(1, height)
        val rgbaBytes = context.getImageDataRgba(x, y, safeW, safeH)
        return ImageBuffer(
            width = safeW,
            height = safeH,
            pixelFormat = PixelFormat.RGBA_8888,
            bytes = if (rgbaBytes.size == safeW * safeH * 4) rgbaBytes else ByteArray(safeW * safeH * 4)
        )
    }
}
