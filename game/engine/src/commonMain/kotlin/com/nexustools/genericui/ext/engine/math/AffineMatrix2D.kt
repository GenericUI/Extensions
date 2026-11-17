package com.nexustools.genericui.ext.engine.math

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compact 2D affine matrix (`x' = a*x + c*y + tx`, `y' = b*x + d*y + ty`).
 */
data class AffineMatrix2D(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val tx: Float = 0f,
    val ty: Float = 0f
) {
    operator fun times(rhs: AffineMatrix2D): AffineMatrix2D = multiply(rhs)

    fun multiply(rhs: AffineMatrix2D): AffineMatrix2D = AffineMatrix2D(
        a = a * rhs.a + c * rhs.b,
        b = b * rhs.a + d * rhs.b,
        c = a * rhs.c + c * rhs.d,
        d = b * rhs.c + d * rhs.d,
        tx = a * rhs.tx + c * rhs.ty + tx,
        ty = b * rhs.tx + d * rhs.ty + ty
    )

    fun transformX(x: Float, y: Float): Float = a * x + c * y + tx

    fun transformY(x: Float, y: Float): Float = b * x + d * y + ty

    fun transformPoint(point: Vec2f): Vec2f =
        Vec2f(transformX(point.x, point.y), transformY(point.x, point.y))

    fun inverted(): AffineMatrix2D? {
        val det = a * d - b * c
        if (abs(det) <= 1e-8f) return null
        val inv = 1f / det
        return AffineMatrix2D(
            a = d * inv,
            b = -b * inv,
            c = -c * inv,
            d = a * inv,
            tx = (c * ty - d * tx) * inv,
            ty = (b * tx - a * ty) * inv
        )
    }

    companion object {
        val IDENTITY: AffineMatrix2D = AffineMatrix2D()

        fun translation(x: Float, y: Float): AffineMatrix2D =
            AffineMatrix2D(tx = x, ty = y)

        fun rotation(radians: Float): AffineMatrix2D {
            val cosA = cos(radians)
            val sinA = sin(radians)
            return AffineMatrix2D(a = cosA, b = sinA, c = -sinA, d = cosA)
        }

        fun scale(sx: Float, sy: Float): AffineMatrix2D =
            AffineMatrix2D(a = sx, d = sy)

        fun fromTRS(
            x: Float,
            y: Float,
            rotationRad: Float = 0f,
            scaleX: Float = 1f,
            scaleY: Float = 1f
        ): AffineMatrix2D {
            val cosA = cos(rotationRad)
            val sinA = sin(rotationRad)
            return AffineMatrix2D(
                a = cosA * scaleX,
                b = sinA * scaleX,
                c = -sinA * scaleY,
                d = cosA * scaleY,
                tx = x,
                ty = y
            )
        }
    }
}
