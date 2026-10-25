package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

/**
 * Dedicated 2D vector and pixel drawing canvas extending [AbstractCanvas], backed by
 * `:extensions:imageprocessing`'s [ImageBuffer] (supporting [doubleBuffer] on System targets
 * and an HTML5 `<canvas>` element on Web).
 *
 * Provides [getContext] (`canvas2D.getContext()`) which returns the [Canvas2DContext] from
 * the backing [ImageBuffer] (`imageBuffer.getContext()`).
 */
open class Canvas2D(
    width: Int = 800,
    height: Int = 600,
    val doubleBuffer: Boolean = false,
    initialAntialias: Boolean = true
) : AbstractCanvas<Canvas2DContext>() {

    private var backingImageBuffer: ImageBuffer? = null
    private var fallbackContextAdapter: Canvas2DContext? = null

    /**
     * Smooth edge antialiasing and bilinear texture filtering (`true` by default).
     */
    var antialias: Boolean = initialAntialias
        set(value) {
            field = value
            super.resolveRenderingContext()?.antialias = value
            backingImageBuffer?.getContext()?.antialias = value
        }

    /**
     * The `:extensions:imageprocessing` [ImageBuffer] surface backing this [Canvas2D].
     */
    val imageBuffer: ImageBuffer
        get() {
            super.resolveRenderingContext()?.imageBuffer?.let { return it }
            val safeW = (widthPx ?: 800).coerceAtLeast(1)
            val safeH = (heightPx ?: 600).coerceAtLeast(1)
            val existing = backingImageBuffer
            if (existing != null) {
                if (existing.width != safeW || existing.height != safeH) {
                    existing.resize(safeW, safeH)
                }
                return existing
            }
            return Canvas2DExtension.createImageBuffer(
                width = safeW,
                height = safeH,
                doubleBuffer = doubleBuffer,
                antialias = antialias
            ).also { backingImageBuffer = it }
        }

    init {
        this.widthPx = width
        this.heightPx = height
    }

    /**
     * Fetches the [Canvas2DContext] backed by [imageBuffer]'s `getContext()`.
     */
    fun getContext(): Canvas2DContext =
        resolveRenderingContext() ?: Canvas2DExtension.wrapImageBufferContext(imageBuffer)

    /**
     * Flushes / swaps the underlying [imageBuffer] surface.
     */
    fun swapBuffers() {
        imageBuffer.swapBuffers()
    }

    override fun resolveRenderingContext(): Canvas2DContext? {
        super.resolveRenderingContext()?.let {
            it.antialias = antialias
            return it
        }
        val buf = imageBuffer
        val existing = fallbackContextAdapter
        if (existing != null && existing.imageBuffer === buf) {
            existing.antialias = antialias
            return existing
        }
        return Canvas2DExtension.wrapImageBufferContext(buf).also {
            it.antialias = antialias
            fallbackContextAdapter = it
        }
    }

    override fun createNativeImpl(factory: NativeFactory): Canvas2DNativeImpl<*> =
        Canvas2DExtension.createCanvas2DNative(
            width = (widthPx ?: 800).coerceAtLeast(1),
            height = (heightPx ?: 600).coerceAtLeast(1)
        )
}
