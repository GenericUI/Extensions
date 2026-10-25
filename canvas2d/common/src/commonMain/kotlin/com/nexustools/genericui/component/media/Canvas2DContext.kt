package com.nexustools.genericui.component.media

import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer
import com.nexustools.genericui.style.RgbaColor

/**
 * Immediate-mode 2D vector and pixel rendering context for [Canvas2D].
 *
 * - On **System targets** (`:extensions:canvas2d:sharedSystem`, `:desktop`, `:mobile`), backed by
 *   `:extensions:imageprocessing:sharedSystem`'s `SystemGpuImageBuffer` and
 *   `GpuAcceleratedContext2DBackend` (unbuffered Vulkan `#version 450` and OpenGL ES `#version 300 es`
 *   shader-accelerated surface with `antialias = true` by default).
 * - On the **Web target** (`:extensions:canvas2d:web`), backed by `:extensions:imageprocessing:web`'s
 *   `WebCanvasImageBuffer` and `WebHtmlCanvasContext2DBackend`, operating directly on the underlying
 *   `CanvasRenderingContext2D` taken from the `<canvas>` HTML element.
 */
interface Canvas2DContext {
    val width: Int
    val height: Int

    /**
     * Enables or disables smooth edge antialiasing and bilinear texture filtering (`true` by default).
     */
    var antialias: Boolean
        get() = true
        set(_) {}

    /**
     * The underlying `:extensions:imageprocessing` [ImageBuffer] surface that this context operates on directly,
     * if backed by an [ImageBuffer].
     */
    val imageBuffer: ImageBuffer?
        get() = null

    /**
     * Resizes the underlying surface in-place.
     */
    fun resizeSurface(newWidth: Int, newHeight: Int) {}

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
     * Accelerated antialiased brush, pencil, airbrush, or eraser stroke between `(x0, y0)` and `(x1, y1)`.
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
    ) {
        save()
        setGlobalAlpha(opacity.coerceIn(0f, 1f))
        setGlobalCompositeOperation(if (erase) "destination-out" else "source-over")
        setStrokeColor(color)
        setLineWidth((radius * 2f).coerceAtLeast(1f))
        setLineCap("round")
        setLineJoin("round")
        beginPath()
        moveTo(x0, y0)
        lineTo(x1, y1)
        stroke()
        restore()
    }

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
    ) {
        save()
        setFillColor(startColor)
        fillRect(rectX, rectY, rectW, rectH)
        restore()
    }

    /**
     * Accelerated flood fill starting at `(seedX, seedY)`.
     */
    fun floodFill(
        seedX: Int,
        seedY: Int,
        fillColor: RgbaColor,
        tolerance: Int = 24
    ) {
        val buf = imageBuffer
        if (buf != null) {
            buf.context2D.floodFill(seedX, seedY, fillColor, tolerance)
        }
    }
}
