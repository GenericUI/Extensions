package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.style.RgbaColor
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Strongly-typed 2D pattern descriptor wrapping an [com.nexustools.genericui.image.ImageBuffer]
 * with an [ImageBufferPatternRepetition] mode and optional 2D pattern [transform].
 */
class ImageBufferPattern2D(
    val image: com.nexustools.genericui.image.ImageBuffer,
    val repetition: ImageBufferPatternRepetition = ImageBufferPatternRepetition.REPEAT
) {
    var transform: ImageBufferMatrix2D = ImageBufferMatrix2D.IDENTITY
        private set

    private var inverseTransform: ImageBufferMatrix2D = ImageBufferMatrix2D.IDENTITY
    private val rgbaImage: com.nexustools.genericui.image.ImageBuffer = image.asRgba8888()

    fun setTransform(matrix: ImageBufferMatrix2D) {
        transform = matrix
        inverseTransform = matrix.invert() ?: ImageBufferMatrix2D.IDENTITY
    }

    /**
     * Samples the pattern at pattern-space coordinates `(px, py)`.
     */
    fun sampleAt(px: Float, py: Float, smoothing: Boolean = true): RgbaColor {
        val w = rgbaImage.width
        val h = rgbaImage.height
        if (w <= 0 || h <= 0) return RgbaColor.TRANSPARENT

        val tx = inverseTransform.transformX(px, py)
        val ty = inverseTransform.transformY(px, py)

        val sx: Float
        val sy: Float

        when (repetition) {
            ImageBufferPatternRepetition.REPEAT -> {
                sx = positiveMod(tx, w.toFloat())
                sy = positiveMod(ty, h.toFloat())
            }
            ImageBufferPatternRepetition.REPEAT_X -> {
                if (ty < 0f || ty >= h.toFloat()) return RgbaColor.TRANSPARENT
                sx = positiveMod(tx, w.toFloat())
                sy = ty
            }
            ImageBufferPatternRepetition.REPEAT_Y -> {
                if (tx < 0f || tx >= w.toFloat()) return RgbaColor.TRANSPARENT
                sx = tx
                sy = positiveMod(ty, h.toFloat())
            }
            ImageBufferPatternRepetition.NO_REPEAT -> {
                if (tx < 0f || tx >= w.toFloat() || ty < 0f || ty >= h.toFloat()) {
                    return RgbaColor.TRANSPARENT
                }
                sx = tx
                sy = ty
            }
        }

        if (!smoothing) {
            val ix = sx.toInt().coerceIn(0, w - 1)
            val iy = sy.toInt().coerceIn(0, h - 1)
            return rgbaImage.getPixel(ix, iy)
        }

        val x0 = floor(sx - 0.5f).toInt()
        val y0 = floor(sy - 0.5f).toInt()
        val fx = (sx - 0.5f) - x0
        val fy = (sy - 0.5f) - y0

        val c00 = sampleWrappedPixel(x0, y0, w, h)
        val c10 = sampleWrappedPixel(x0 + 1, y0, w, h)
        val c01 = sampleWrappedPixel(x0, y0 + 1, w, h)
        val c11 = sampleWrappedPixel(x0 + 1, y0 + 1, w, h)

        val w00 = (1f - fx) * (1f - fy)
        val w10 = fx * (1f - fy)
        val w01 = (1f - fx) * fy
        val w11 = fx * fy

        return RgbaColor(
            r = (c00.r * w00 + c10.r * w10 + c01.r * w01 + c11.r * w11).roundToInt().coerceIn(0, 255),
            g = (c00.g * w00 + c10.g * w10 + c01.g * w01 + c11.g * w11).roundToInt().coerceIn(0, 255),
            b = (c00.b * w00 + c10.b * w10 + c01.b * w01 + c11.b * w11).roundToInt().coerceIn(0, 255),
            a = (c00.a * w00 + c10.a * w10 + c01.a * w01 + c11.a * w11).roundToInt().coerceIn(0, 255)
        )
    }

    private fun sampleWrappedPixel(ix: Int, iy: Int, w: Int, h: Int): RgbaColor {
        val wx = when (repetition) {
            ImageBufferPatternRepetition.REPEAT, ImageBufferPatternRepetition.REPEAT_X -> ((ix % w) + w) % w
            else -> ix.coerceIn(0, w - 1)
        }
        val wy = when (repetition) {
            ImageBufferPatternRepetition.REPEAT, ImageBufferPatternRepetition.REPEAT_Y -> ((iy % h) + h) % h
            else -> iy.coerceIn(0, h - 1)
        }
        return rgbaImage.getPixel(wx, wy)
    }

    private fun positiveMod(v: Float, mod: Float): Float {
        if (mod <= 0f) return 0f
        val r = v % mod
        return if (r < 0f) r + mod else r
    }
}
