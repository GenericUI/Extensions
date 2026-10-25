package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl
import com.nexustools.genericui.nativeimpl.media.ScrollCanvas2DNativeImpl
import kotlin.math.max

/**
 * Extension registry and [ImageBuffer] / [ImageBuffer2DContext] bridge for `:extensions:canvas2d`,
 * relying strictly on `:extensions:imageprocessing`'s universal [ImageBuffer] and
 * [ImageBuffer.getContext] (`SystemGpuImageBuffer` on System targets, and
 * `WebImageBuffer` on Web).
 */
object Canvas2DExtension {
    private var contextFactory: ((Int, Int) -> ImageBuffer2DContext)? = null
    private var canvas2dNativeFactory: ((Int, Int) -> Canvas2DNativeImpl<*>)? = null
    private var scrollCanvas2dNativeFactory: ((Int, Int, Int, Int) -> ScrollCanvas2DNativeImpl<*>)? = null

    fun registerContextFactory(factory: (width: Int, height: Int) -> ImageBuffer2DContext) {
        contextFactory = factory
    }

    fun registerCanvas2DFactory(factory: (width: Int, height: Int) -> Canvas2DNativeImpl<*>) {
        canvas2dNativeFactory = factory
    }

    fun registerScrollCanvas2DFactory(
        factory: (viewportWidth: Int, viewportHeight: Int, contentWidth: Int, contentHeight: Int) -> ScrollCanvas2DNativeImpl<*>
    ) {
        scrollCanvas2dNativeFactory = factory
    }

    fun createCanvas2DNative(width: Int = 800, height: Int = 600): Canvas2DNativeImpl<*> =
        canvas2dNativeFactory?.invoke(width, height)
            ?: throw IllegalStateException("No Canvas2DNativeImpl provider registered in Canvas2DExtension")

    fun createScrollCanvas2DNativeOrNull(
        viewportWidth: Int = 800,
        viewportHeight: Int = 600,
        contentWidth: Int = viewportWidth,
        contentHeight: Int = viewportHeight
    ): ScrollCanvas2DNativeImpl<*>? =
        scrollCanvas2dNativeFactory?.invoke(viewportWidth, viewportHeight, contentWidth, contentHeight)

    fun createScrollCanvas2DNative(
        viewportWidth: Int = 800,
        viewportHeight: Int = 600,
        contentWidth: Int = viewportWidth,
        contentHeight: Int = viewportHeight
    ): ScrollCanvas2DNativeImpl<*> =
        scrollCanvas2dNativeFactory?.invoke(viewportWidth, viewportHeight, contentWidth, contentHeight)
            ?: SystemScrollCanvas2DNativeImpl(
                initialViewportWidth = viewportWidth,
                initialViewportHeight = viewportHeight,
                initialContentWidth = contentWidth,
                initialContentHeight = contentHeight
            )

    /**
     * Creates an `:extensions:imageprocessing` [ImageBuffer] for offscreen 2D rendering or
     * as a `Canvas2D` backend. Call `imageBuffer.getContext()` to fetch its [ImageBuffer2DContext].
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

    fun wrapImageBufferContext(imageBuffer: ImageBuffer): ImageBuffer2DContext =
        imageBuffer.getContext()

    fun createContext(width: Int = 800, height: Int = 600, doubleBuffer: Boolean = false): ImageBuffer2DContext {
        contextFactory?.let { return it(width, height) }
        return createImageBuffer(
            width = width,
            height = height,
            doubleBuffer = doubleBuffer,
            antialias = true
        ).getContext()
    }

    /**
     * Draws an [com.nexustools.genericui.image.ImageBuffer] onto [context] at `(dx, dy)` using
     * `:extensions:imageprocessing`'s accelerated [ImageBuffer2DContext].
     */
    fun drawImage(
        context: ImageBuffer2DContext,
        buffer: com.nexustools.genericui.image.ImageBuffer,
        dx: Float = 0f,
        dy: Float = 0f
    ) {
        context.drawImage(buffer, dx, dy)
    }

    /**
     * Fetches a rectangular region of [context] into a `:common` [com.nexustools.genericui.image.ImageBuffer]
     * via `:extensions:imageprocessing`.
     */
    fun extractImageBuffer(
        context: ImageBuffer2DContext,
        x: Int = 0,
        y: Int = 0,
        width: Int = context.width,
        height: Int = context.height
    ): com.nexustools.genericui.image.ImageBuffer {
        val safeW = max(1, width)
        val safeH = max(1, height)
        val surface = context.imageBuffer
        if (x == 0 && y == 0 && safeW == surface.width && safeH == surface.height) {
            return surface.toImageBuffer()
        }
        val rgbaBytes = surface.readPixelsRgba(x, y, safeW, safeH)
        return com.nexustools.genericui.image.ImageBuffer(
            width = safeW,
            height = safeH,
            pixelFormat = PixelFormat.RGBA_8888,
            bytes = if (rgbaBytes.size == safeW * safeH * 4) rgbaBytes else ByteArray(safeW * safeH * 4)
        )
    }
}
