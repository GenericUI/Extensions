package com.nexustools.genericui.ext.svg

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * SVG 2D affine transform operations and 3x2 matrix composition/inversion helpers.
 */
sealed class SvgTransform {
    abstract fun toMatrix(): AffineMatrix
    abstract fun toSvgString(): String

    data class Translate(val tx: Float, val ty: Float = 0f) : SvgTransform() {
        override fun toMatrix(): AffineMatrix = AffineMatrix(1f, 0f, 0f, 1f, tx, ty)
        override fun toSvgString(): String = if (abs(ty) < 1e-6f) {
            "translate(${SvgPoint.formatSvgNumber(tx)})"
        } else {
            "translate(${SvgPoint.formatSvgNumber(tx)}, ${SvgPoint.formatSvgNumber(ty)})"
        }
    }

    data class Scale(val sx: Float, val sy: Float = sx) : SvgTransform() {
        override fun toMatrix(): AffineMatrix = AffineMatrix(sx, 0f, 0f, sy, 0f, 0f)
        override fun toSvgString(): String = if (abs(sx - sy) < 1e-6f) {
            "scale(${SvgPoint.formatSvgNumber(sx)})"
        } else {
            "scale(${SvgPoint.formatSvgNumber(sx)}, ${SvgPoint.formatSvgNumber(sy)})"
        }
    }

    data class Rotate(val angleDeg: Float, val cx: Float = 0f, val cy: Float = 0f) : SvgTransform() {
        override fun toMatrix(): AffineMatrix {
            val rad = angleDeg * (PI.toFloat() / 180f)
            val cosA = cos(rad)
            val sinA = sin(rad)
            val rot = AffineMatrix(cosA, sinA, -sinA, cosA, 0f, 0f)
            if (abs(cx) < 1e-6f && abs(cy) < 1e-6f) {
                return rot
            }
            return AffineMatrix(1f, 0f, 0f, 1f, cx, cy)
                .multiply(rot)
                .multiply(AffineMatrix(1f, 0f, 0f, 1f, -cx, -cy))
        }

        override fun toSvgString(): String = if (abs(cx) < 1e-6f && abs(cy) < 1e-6f) {
            "rotate(${SvgPoint.formatSvgNumber(angleDeg)})"
        } else {
            "rotate(${SvgPoint.formatSvgNumber(angleDeg)}, ${SvgPoint.formatSvgNumber(cx)}, ${SvgPoint.formatSvgNumber(cy)})"
        }
    }

    data class SkewX(val angleDeg: Float) : SvgTransform() {
        override fun toMatrix(): AffineMatrix {
            val t = tan(angleDeg * (PI.toFloat() / 180f))
            return AffineMatrix(1f, 0f, t, 1f, 0f, 0f)
        }

        override fun toSvgString(): String = "skewX(${SvgPoint.formatSvgNumber(angleDeg)})"
    }

    data class SkewY(val angleDeg: Float) : SvgTransform() {
        override fun toMatrix(): AffineMatrix {
            val t = tan(angleDeg * (PI.toFloat() / 180f))
            return AffineMatrix(1f, t, 0f, 1f, 0f, 0f)
        }

        override fun toSvgString(): String = "skewY(${SvgPoint.formatSvgNumber(angleDeg)})"
    }

    data class Matrix(
        val a: Float,
        val b: Float,
        val c: Float,
        val d: Float,
        val e: Float,
        val f: Float
    ) : SvgTransform() {
        override fun toMatrix(): AffineMatrix = AffineMatrix(a, b, c, d, e, f)
        override fun toSvgString(): String =
            "matrix(${SvgPoint.formatSvgNumber(a)}, ${SvgPoint.formatSvgNumber(b)}, " +
                "${SvgPoint.formatSvgNumber(c)}, ${SvgPoint.formatSvgNumber(d)}, " +
                "${SvgPoint.formatSvgNumber(e)}, ${SvgPoint.formatSvgNumber(f)})"
    }

    /**
     * 3x2 affine matrix `[a c e; b d f; 0 0 1]` supporting composition, point transform, and inversion.
     */
    data class AffineMatrix(
        val a: Float = 1f,
        val b: Float = 0f,
        val c: Float = 0f,
        val d: Float = 1f,
        val e: Float = 0f,
        val f: Float = 0f
    ) {
        fun mapX(x: Float, y: Float): Float = a * x + c * y + e
        fun mapY(x: Float, y: Float): Float = b * x + d * y + f
        fun mapPoint(p: SvgPoint): SvgPoint = SvgPoint(mapX(p.x, p.y), mapY(p.x, p.y))

        fun multiply(other: AffineMatrix): AffineMatrix = AffineMatrix(
            a = a * other.a + c * other.b,
            b = b * other.a + d * other.b,
            c = a * other.c + c * other.d,
            d = b * other.c + d * other.d,
            e = a * other.e + c * other.f + e,
            f = b * other.e + d * other.f + f
        )

        fun inverse(): AffineMatrix? {
            val det = a * d - b * c
            if (abs(det) < 1e-10f) return null
            val invDet = 1f / det
            return AffineMatrix(
                a = d * invDet,
                b = -b * invDet,
                c = -c * invDet,
                d = a * invDet,
                e = (c * f - d * e) * invDet,
                f = (b * e - a * f) * invDet
            )
        }

        fun maxAxisScale(): Float {
            val sx = kotlin.math.hypot(a.toDouble(), b.toDouble()).toFloat()
            val sy = kotlin.math.hypot(c.toDouble(), d.toDouble()).toFloat()
            return kotlin.math.max(sx, sy)
        }

        companion object {
            val IDENTITY = AffineMatrix(1f, 0f, 0f, 1f, 0f, 0f)
        }
    }

    companion object {
        fun translate(tx: Float, ty: Float = 0f): SvgTransform = Translate(tx, ty)
        fun scale(sx: Float, sy: Float = sx): SvgTransform = Scale(sx, sy)
        fun rotate(angleDeg: Float, cx: Float = 0f, cy: Float = 0f): SvgTransform = Rotate(angleDeg, cx, cy)
        fun skewX(angleDeg: Float): SvgTransform = SkewX(angleDeg)
        fun skewY(angleDeg: Float): SvgTransform = SkewY(angleDeg)
        fun matrix(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float): SvgTransform =
            Matrix(a, b, c, d, e, f)

        fun composeMatrix(transforms: List<SvgTransform>): AffineMatrix {
            var mat = AffineMatrix.IDENTITY
            for (t in transforms) {
                mat = mat.multiply(t.toMatrix())
            }
            return mat
        }

        fun formatTransformList(transforms: List<SvgTransform>): String =
            transforms.joinToString(" ") { it.toSvgString() }

        fun parseTransformList(raw: String?): List<SvgTransform> {
            if (raw.isNullOrBlank()) return emptyList()
            val out = ArrayList<SvgTransform>()
            var idx = 0
            val len = raw.length
            while (idx < len) {
                while (idx < len && (raw[idx].isWhitespace() || raw[idx] == ',')) {
                    idx++
                }
                if (idx >= len) break
                val nameStart = idx
                while (idx < len && raw[idx].isLetter()) {
                    idx++
                }
                val fnName = raw.substring(nameStart, idx).trim().lowercase()
                while (idx < len && raw[idx].isWhitespace()) {
                    idx++
                }
                if (idx >= len || raw[idx] != '(') {
                    idx++
                    continue
                }
                val openParen = idx
                val closeParen = raw.indexOf(')', openParen + 1)
                if (closeParen < 0) break
                val argsStr = raw.substring(openParen + 1, closeParen)
                val nums = SvgPoint.parseNumberList(argsStr)
                when (fnName) {
                    "translate" -> if (nums.isNotEmpty()) {
                        out.add(Translate(nums[0], nums.getOrElse(1) { 0f }))
                    }
                    "scale" -> if (nums.isNotEmpty()) {
                        out.add(Scale(nums[0], nums.getOrElse(1) { nums[0] }))
                    }
                    "rotate" -> if (nums.isNotEmpty()) {
                        out.add(Rotate(nums[0], nums.getOrElse(1) { 0f }, nums.getOrElse(2) { 0f }))
                    }
                    "skewx" -> if (nums.isNotEmpty()) {
                        out.add(SkewX(nums[0]))
                    }
                    "skewy" -> if (nums.isNotEmpty()) {
                        out.add(SkewY(nums[0]))
                    }
                    "matrix" -> if (nums.size >= 6) {
                        out.add(Matrix(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5]))
                    }
                }
                idx = closeParen + 1
            }
            return out
        }
    }
}
