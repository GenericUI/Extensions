package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.component.media.AbstractCanvas
import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.image.ImageBuffer

/**
 * Extended 2D vector and pixel surface in `:extensions:canvas2d` extending [Canvas2D] (and [AbstractCanvas]).
 *
 * Provides direct [drawImage] and [toImageBuffer] integration with `:common`'s [ImageBuffer] and
 * guarantees a valid [Canvas2DContext] during [registerVBlankRender] callbacks.
 */
open class Canvas2DExtSurface(
    width: Int = 800,
    height: Int = 600
) : Canvas2D(width, height) {

    private var fallbackContext: Canvas2DContext? = null

    /**
     * Draws a `:common` [ImageBuffer] directly onto this 2D canvas surface at `(dx, dy)`.
     */
    fun drawImage(buffer: ImageBuffer, dx: Float = 0f, dy: Float = 0f) {
        val ctx = resolveRenderingContext() ?: return
        Canvas2DExtension.drawImage(ctx, buffer, dx, dy)
    }

    /**
     * Extracts the current 2D canvas framebuffer as an [ImageBuffer].
     */
    fun toImageBuffer(): ImageBuffer {
        val ctx = resolveRenderingContext()
            ?: return ImageBuffer(surfaceWidth.coerceAtLeast(1), surfaceHeight.coerceAtLeast(1))
        return Canvas2DExtension.extractImageBuffer(ctx, 0, 0, ctx.width, ctx.height)
    }

    public override fun resolveRenderingContext(): Canvas2DContext? {
        super.resolveRenderingContext()?.let { return it }
        if (fallbackContext == null) {
            fallbackContext = Canvas2DExtension.createContext(
                width = (widthPx ?: 800).coerceAtLeast(1),
                height = (heightPx ?: 600).coerceAtLeast(1)
            )
        }
        return fallbackContext
    }
}
