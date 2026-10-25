package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.style.RgbaColor

/**
 * Shared 2D context backend interface in `:extensions:imageprocessing:common` that operates
 * directly on an `:extensions:imageprocessing` [ImageBuffer] surface.
 *
 * - Implemented on **System targets** (`:extensions:imageprocessing:sharedSystem`) by
 *   `GpuAcceleratedContext2DBackend` working directly on an unbuffered GL/Vulkan [ImageBuffer]
 *   (`SystemGpuImageBuffer`) using hardware-accelerated **Vulkan (`#version 450`)** and
 *   **OpenGL ES (`#version 300 es`)** shaders for all drawing, antialiasing (`antialias = true`
 *   by default), brush/gradient/flood-fill kernels, and fast pixel fetching.
 * - Implemented on the **Web target** (`:extensions:imageprocessing:web`) by
 *   `WebHtmlCanvasContext2DBackend` working directly on top of the HTML5 `<canvas>` 2D rendering
 *   context (`CanvasRenderingContext2D`) backing `WebCanvasImageBuffer`.
 * - Used by `:extensions:canvas2d` across all targets to execute accelerated 2D rendering,
 *   raster tool processing, and pixel fetching (`getImageDataRgba`, `presentFrontBufferRgba`,
 *   `presentFrontBufferBgra`, `toImageBuffer`).
 */
interface ImageProcessingContext2DBackend {
    /**
     * The underlying `:extensions:imageprocessing` [ImageBuffer] (unbuffered Vulkan/GLES surface on
     * System targets, or HTML5 `<canvas>` surface on Web) that this 2D context works on directly.
     */
    val imageBuffer: ImageBuffer

    val width: Int
    val height: Int

    /**
     * Enables or disables smooth edge antialiasing and bilinear texture filtering (`true` by default).
     */
    var antialias: Boolean

    /**
     * Number of completed direct surface flushes/presentations.
     */
    val swapCount: Long

    fun resize(newWidth: Int, newHeight: Int)
    fun swapBuffers()

    fun save()
    fun restore()
    fun scale(sx: Float, sy: Float)
    fun rotate(angleRadians: Float)
    fun translate(dx: Float, dy: Float)
    fun setTransform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float)
    fun resetTransform()

    fun setGlobalAlpha(alpha: Float)
    fun setGlobalCompositeOperation(mode: String)

    fun setFillColor(color: RgbaColor)
    fun setStrokeColor(color: RgbaColor)
    fun setLineWidth(width: Float)
    fun setLineCap(cap: String)
    fun setLineJoin(join: String)
    fun setLineDash(segments: FloatArray, offset: Float = 0f)

    fun clearRect(x: Float, y: Float, width: Float, height: Float)
    fun fillRect(x: Float, y: Float, width: Float, height: Float)
    fun strokeRect(x: Float, y: Float, width: Float, height: Float)
    fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float)

    fun beginPath()
    fun closePath()
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float)
    fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float)
    fun arc(x: Float, y: Float, radius: Float, startAngle: Float, endAngle: Float, counterClockwise: Boolean = false)
    fun fill()
    fun stroke()
    fun clip()

    fun setFont(fontFamily: String, fontSizePx: Float, fontWeight: Int = 400, italic: Boolean = false)
    fun fillText(text: String, x: Float, y: Float)
    fun strokeText(text: String, x: Float, y: Float)
    fun measureTextWidth(text: String): Float

    fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int = 0, dy: Int = 0)
    fun getImageDataRgba(x: Int, y: Int, width: Int, height: Int): ByteArray

    /**
     * Draws a [com.nexustools.genericui.image.ImageBuffer] at `(dx, dy)` onto this 2D context surface.
     */
    fun drawImageBuffer(buffer: com.nexustools.genericui.image.ImageBuffer, dx: Float = 0f, dy: Float = 0f)

    /**
     * Returns the direct RGBA bytes of [imageBuffer] without full-frame reallocation.
     */
    fun presentFrontBufferRgba(): ByteArray

    /**
     * Returns the direct BGRA bytes of [imageBuffer] for zero-copy Win32/GDI/Direct2D/Qt presentation.
     */
    fun presentFrontBufferBgra(): ByteArray

    /**
     * Returns the current contents of [imageBuffer] as a [com.nexustools.genericui.image.ImageBuffer].
     */
    fun toImageBuffer(): com.nexustools.genericui.image.ImageBuffer

    /**
     * Accelerated capsule-interpolated antialiased brush, pencil, airbrush, or eraser stroke
     * between `(x0, y0)` and `(x1, y1)`.
     */
    fun stampBrushStroke(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float,
        hardness: Float = 0.85f,
        color: RgbaColor,
        opacity: Float = 1.0f,
        erase: Boolean = false
    )

    /**
     * Accelerated linear gradient fill between `(x0, y0)` ([startColor]) and `(x1, y1)` ([endColor]).
     */
    fun fillLinearGradient(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        startColor: RgbaColor,
        endColor: RgbaColor,
        rectX: Float = 0f,
        rectY: Float = 0f,
        rectW: Float = width.toFloat(),
        rectH: Float = height.toFloat()
    )

    /**
     * Accelerated scanline flood fill starting at `(seedX, seedY)`.
     */
    fun floodFill(
        seedX: Int,
        seedY: Int,
        fillColor: RgbaColor,
        tolerance: Int = 24
    )
}
