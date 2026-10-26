package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.component.media.AbstractCanvas
import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.ext.canvas2d.overlay.Canvas2DOverlayHost
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.image.ImageBuffer

/**
 * Extended 2D vector and pixel surface in `:extensions:canvas2d` extending [Canvas2D] (and [AbstractCanvas]).
 *
 * Uses `:extensions:imageprocessing`'s universal [com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer]
 * surface (`SystemGpuImageBuffer` on System targets, `WebImageBuffer` on Web) as its backend
 * and enables double buffering (`doubleBuffer = true`) and antialiasing (`antialias = true`) by default.
 */
open class Canvas2DExtSurface(
    width: Int = 800,
    height: Int = 600,
    doubleBuffer: Boolean = true,
    initialAntialias: Boolean = true
) : Canvas2D(
    width = width,
    height = height,
    doubleBuffer = doubleBuffer,
    initialAntialias = initialAntialias
) {

    /**
     * Draws an [ImageBuffer] directly onto this 2D canvas surface at `(dx, dy)` using `:extensions:imageprocessing`.
     */
    fun drawImage(buffer: ImageBuffer, dx: Float = 0f, dy: Float = 0f) {
        val ctx = resolveRenderingContext() ?: return
        Canvas2DExtension.drawImage(ctx, buffer, dx, dy)
    }

    /**
     * Extracts the current 2D canvas framebuffer as an [ImageBuffer] via `:extensions:imageprocessing`.
     */
    fun toImageBuffer(): ImageBuffer {
        val ctx = resolveRenderingContext()
            ?: return ImageBuffer(surfaceWidth.coerceAtLeast(1), surfaceHeight.coerceAtLeast(1))
        return Canvas2DExtension.extractImageBuffer(ctx, 0, 0, ctx.width, ctx.height)
    }

    /**
     * Returns `true` when this surface is backed by an attached platform `Canvas2DNativeImpl`.
     */
    fun hasNativeRenderingContext(): Boolean = typedCanvasNative()?.getRenderingContext() != null

    /**
     * Synchronizes both the logical [widthPx]/[heightPx] and the underlying [imageBuffer] surface dimensions.
     */
    fun syncSurfaceSize(width: Int, height: Int) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        if (widthPx != safeW || heightPx != safeH) {
            widthPx = safeW
            heightPx = safeH
        }
        val ctx = resolveRenderingContext()
        if (ctx != null && (ctx.width != safeW || ctx.height != safeH)) {
            ctx.resizeSurface(safeW, safeH)
        }
    }

    /**
     * Wraps this [Canvas2DExtSurface] in a [Canvas2DOverlayHost] with the given [overlay] attached.
     */
    fun attachOverlay(overlay: AbstractOverlay): Canvas2DOverlayHost =
        Canvas2DOverlayHost(canvas = this, initialOverlay = overlay)

    /**
     * Wraps this [Canvas2DExtSurface] in a [Canvas2DOverlayHost] with an optional [overlay] (`null` by default).
     */
    fun withOverlay(overlay: AbstractOverlay? = null): Canvas2DOverlayHost =
        Canvas2DOverlayHost(canvas = this, initialOverlay = overlay)

    public override fun resolveRenderingContext(): ImageBuffer2DContext? =
        super.resolveRenderingContext()
}
