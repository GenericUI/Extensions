package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Extension registry and [ImageBuffer] drawing bridge for `:extensions:canvas2d`.
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

    fun createContext(width: Int = 800, height: Int = 600): Canvas2DContext {
        contextFactory?.let { return it(width, height) }
        return createFallbackContext(width, height)
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

    private fun createFallbackContext(initialWidth: Int, initialHeight: Int): Canvas2DContext = object : Canvas2DContext {
        override val width: Int = max(1, initialWidth)
        override val height: Int = max(1, initialHeight)
        private val pixels = ByteArray(width * height * 4)

        override fun save() {}
        override fun restore() {}
        override fun scale(sx: Float, sy: Float) {}
        override fun rotate(angleRadians: Float) {}
        override fun translate(dx: Float, dy: Float) {}
        override fun setTransform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {}
        override fun resetTransform() {}
        override fun setGlobalAlpha(alpha: Float) {}
        override fun setGlobalCompositeOperation(mode: String) {}
        override fun setFillColor(color: RgbaColor) {}
        override fun setStrokeColor(color: RgbaColor) {}
        override fun setLineWidth(width: Float) {}
        override fun setLineCap(cap: String) {}
        override fun setLineJoin(join: String) {}
        override fun setLineDash(segments: FloatArray, offset: Float) {}
        override fun clearRect(x: Float, y: Float, width: Float, height: Float) {
            pixels.fill(0)
        }
        override fun fillRect(x: Float, y: Float, width: Float, height: Float) {}
        override fun strokeRect(x: Float, y: Float, width: Float, height: Float) {}
        override fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {}
        override fun beginPath() {}
        override fun closePath() {}
        override fun moveTo(x: Float, y: Float) {}
        override fun lineTo(x: Float, y: Float) {}
        override fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float) {}
        override fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float) {}
        override fun arc(x: Float, y: Float, radius: Float, startAngle: Float, endAngle: Float, counterClockwise: Boolean) {}
        override fun fill() {}
        override fun stroke() {}
        override fun clip() {}
        override fun setFont(fontFamily: String, fontSizePx: Float, fontWeight: Int, italic: Boolean) {}
        override fun fillText(text: String, x: Float, y: Float) {}
        override fun strokeText(text: String, x: Float, y: Float) {}
        override fun measureTextWidth(text: String): Float = text.length * 7f

        override fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) {
            for (row in 0 until height) {
                val dstY = dy + row
                if (dstY !in 0 until this.height) continue
                for (col in 0 until width) {
                    val dstX = dx + col
                    if (dstX !in 0 until this.width) continue
                    val sIdx = (row * width + col) * 4
                    val dIdx = (dstY * this.width + dstX) * 4
                    if (sIdx + 3 < rgbaBytes.size && dIdx + 3 < pixels.size) {
                        pixels[dIdx] = rgbaBytes[sIdx]
                        pixels[dIdx + 1] = rgbaBytes[sIdx + 1]
                        pixels[dIdx + 2] = rgbaBytes[sIdx + 2]
                        pixels[dIdx + 3] = rgbaBytes[sIdx + 3]
                    }
                }
            }
        }

        override fun getImageDataRgba(x: Int, y: Int, width: Int, height: Int): ByteArray {
            val out = ByteArray(max(0, width) * max(0, height) * 4)
            for (row in 0 until height) {
                val srcY = y + row
                if (srcY !in 0 until this.height) continue
                for (col in 0 until width) {
                    val srcX = x + col
                    if (srcX !in 0 until this.width) continue
                    val sIdx = (srcY * this.width + srcX) * 4
                    val dIdx = (row * width + col) * 4
                    if (sIdx + 3 < pixels.size && dIdx + 3 < out.size) {
                        out[dIdx] = pixels[sIdx]
                        out[dIdx + 1] = pixels[sIdx + 1]
                        out[dIdx + 2] = pixels[sIdx + 2]
                        out[dIdx + 3] = pixels[sIdx + 3]
                    }
                }
            }
            return out
        }
    }
}
