package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageProcessingContext2DBackend
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Extension registry and [ImageBuffer] bridge for `:extensions:canvas2d`,
 * relying strictly on `:extensions:imageprocessing`'s universal [ImageBuffer] and
 * [ImageBuffer.getContext] (`SystemGpuImageBuffer` on System targets, and
 * `WebCanvasImageBuffer` backed by an HTML5 `<canvas>` on Web).
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

    /**
     * Creates an `:extensions:imageprocessing` [ImageBuffer] for offscreen 2D rendering or
     * as a `Canvas2D` backend. Call `imageBuffer.getContext()` to fetch its 2D context.
     */
    fun createImageBuffer(
        width: Int = 800,
        height: Int = 600,
        doubleBuffer: Boolean = false,
        antialias: Boolean = true
    ): ImageBuffer = ImageBuffer.create(
        width = width,
        height = height,
        doubleBuffer = doubleBuffer,
        antialias = antialias
    )

    fun wrapImageBufferContext(imageBuffer: ImageBuffer): Canvas2DContext =
        ImageBufferCanvas2DContext(imageBuffer)

    fun createContext(width: Int = 800, height: Int = 600, doubleBuffer: Boolean = false): Canvas2DContext {
        contextFactory?.let { return it(width, height) }
        return ImageBufferCanvas2DContext(
            createImageBuffer(width = width, height = height, doubleBuffer = doubleBuffer, antialias = true)
        )
    }

    /**
     * Draws an [com.nexustools.genericui.image.ImageBuffer] onto [context] at `(dx, dy)` using
     * `:extensions:imageprocessing`'s accelerated 2D context backend.
     */
    fun drawImage(
        context: Canvas2DContext,
        buffer: com.nexustools.genericui.image.ImageBuffer,
        dx: Float = 0f,
        dy: Float = 0f
    ) {
        if (context is ImageProcessingContext2DBackend) {
            context.drawImageBuffer(buffer, dx, dy)
            return
        }
        val surface = context.imageBuffer
        if (surface != null) {
            surface.getContext().drawImageBuffer(buffer, dx, dy)
            return
        }
        val rgba = buffer.asRgba8888()
        context.putImageDataRgba(
            rgbaBytes = rgba.bytes,
            width = rgba.width,
            height = rgba.height,
            dx = dx.roundToInt(),
            dy = dy.roundToInt()
        )
    }

    /**
     * Fetches a rectangular region of [context] into a `:common` [com.nexustools.genericui.image.ImageBuffer]
     * via `:extensions:imageprocessing`.
     */
    fun extractImageBuffer(
        context: Canvas2DContext,
        x: Int = 0,
        y: Int = 0,
        width: Int = context.width,
        height: Int = context.height
    ): com.nexustools.genericui.image.ImageBuffer {
        val safeW = max(1, width)
        val safeH = max(1, height)
        val surface = context.imageBuffer
        if (surface != null && x == 0 && y == 0 && safeW == surface.width && safeH == surface.height) {
            return surface.toImageBuffer()
        }
        val rgbaBytes = surface?.readPixelsRgba(x, y, safeW, safeH)
            ?: context.getImageDataRgba(x, y, safeW, safeH)
        return com.nexustools.genericui.image.ImageBuffer(
            width = safeW,
            height = safeH,
            pixelFormat = PixelFormat.RGBA_8888,
            bytes = if (rgbaBytes.size == safeW * safeH * 4) rgbaBytes else ByteArray(safeW * safeH * 4)
        )
    }
}
