package com.nexustools.genericui.ext.imageprocessing.context2d

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Strongly-typed 2D affine transformation matrix `(a, b, c, d, e, f)` for [ImageBuffer2DContext]:
 * - `x' = a * x + c * y + e`
 * - `y' = b * x + d * y + f`
 */
data class ImageBufferMatrix2D(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val e: Float = 0f,
    val f: Float = 0f
) {
    val isIdentity: Boolean
        get() = abs(a - 1f) <= 1e-6f &&
            abs(b) <= 1e-6f &&
            abs(c) <= 1e-6f &&
            abs(d - 1f) <= 1e-6f &&
            abs(e) <= 1e-6f &&
            abs(f) <= 1e-6f

    /**
     * Post-multiplies this matrix by [other] (`this * other`).
     */
    fun multiply(other: ImageBufferMatrix2D): ImageBufferMatrix2D =
        ImageBufferMatrix2D(
            a = a * other.a + c * other.b,
            b = b * other.a + d * other.b,
            c = a * other.c + c * other.d,
            d = b * other.c + d * other.d,
            e = a * other.e + c * other.f + e,
            f = b * other.e + d * other.f + f
        )

    fun translate(tx: Float, ty: Float): ImageBufferMatrix2D =
        multiply(ImageBufferMatrix2D(1f, 0f, 0f, 1f, tx, ty))

    fun scale(sx: Float, sy: Float): ImageBufferMatrix2D =
        multiply(ImageBufferMatrix2D(sx, 0f, 0f, sy, 0f, 0f))

    fun rotate(angleRadians: Float): ImageBufferMatrix2D {
        val cosA = cos(angleRadians)
        val sinA = sin(angleRadians)
        return multiply(ImageBufferMatrix2D(cosA, sinA, -sinA, cosA, 0f, 0f))
    }

    fun skewX(angleRadians: Float): ImageBufferMatrix2D =
        multiply(ImageBufferMatrix2D(1f, 0f, tan(angleRadians), 1f, 0f, 0f))

    fun skewY(angleRadians: Float): ImageBufferMatrix2D =
        multiply(ImageBufferMatrix2D(1f, tan(angleRadians), 0f, 1f, 0f, 0f))

    /**
     * Returns the inverse of this affine matrix, or `null` if the determinant is zero.
     */
    fun invert(): ImageBufferMatrix2D? {
        val det = a * d - b * c
        if (abs(det) <= 1e-8f) return null
        val invDet = 1f / det
        return ImageBufferMatrix2D(
            a = d * invDet,
            b = -b * invDet,
            c = -c * invDet,
            d = a * invDet,
            e = (c * f - d * e) * invDet,
            f = (b * e - a * f) * invDet
        )
    }

    fun transformX(x: Float, y: Float): Float = a * x + c * y + e

    fun transformY(x: Float, y: Float): Float = b * x + d * y + f

    fun transformPoint(x: Float, y: Float): Pair<Float, Float> =
        transformX(x, y) to transformY(x, y)

    companion object {
        val IDENTITY: ImageBufferMatrix2D = ImageBufferMatrix2D()
    }
}
