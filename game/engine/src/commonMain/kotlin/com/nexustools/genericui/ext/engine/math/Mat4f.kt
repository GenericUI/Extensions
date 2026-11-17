package com.nexustools.genericui.ext.engine.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan

/**
 * Column-major 4x4 floating-point transformation matrix.
 */
class Mat4f(
    val elements: FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )
) {
    init {
        require(elements.size == 16) { "Mat4f requires exactly 16 elements, got ${elements.size}" }
    }

    fun setIdentity(): Mat4f {
        elements[0] = 1f; elements[4] = 0f; elements[8] = 0f; elements[12] = 0f
        elements[1] = 0f; elements[5] = 1f; elements[9] = 0f; elements[13] = 0f
        elements[2] = 0f; elements[6] = 0f; elements[10] = 1f; elements[14] = 0f
        elements[3] = 0f; elements[7] = 0f; elements[11] = 0f; elements[15] = 1f
        return this
    }

    fun copyFrom(other: Mat4f): Mat4f {
        other.elements.copyInto(elements)
        return this
    }

    fun copy(): Mat4f = Mat4f(elements.copyOf())

    operator fun times(rhs: Mat4f): Mat4f = multiply(rhs)

    fun multiply(rhs: Mat4f): Mat4f {
        val a = elements
        val b = rhs.elements
        val out = FloatArray(16)
        for (col in 0 until 4) {
            val b0 = b[col * 4]
            val b1 = b[col * 4 + 1]
            val b2 = b[col * 4 + 2]
            val b3 = b[col * 4 + 3]
            out[col * 4] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3
            out[col * 4 + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3
            out[col * 4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3
            out[col * 4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3
        }
        return Mat4f(out)
    }

    fun transformPoint(p: Vec3f): Vec3f {
        val e = elements
        val w = e[3] * p.x + e[7] * p.y + e[11] * p.z + e[15]
        val invW = if (abs(w) > 1e-6f) 1f / w else 1f
        return Vec3f(
            (e[0] * p.x + e[4] * p.y + e[8] * p.z + e[12]) * invW,
            (e[1] * p.x + e[5] * p.y + e[9] * p.z + e[13]) * invW,
            (e[2] * p.x + e[6] * p.y + e[10] * p.z + e[14]) * invW
        )
    }

    fun transformDirection(d: Vec3f): Vec3f {
        val e = elements
        return Vec3f(
            e[0] * d.x + e[4] * d.y + e[8] * d.z,
            e[1] * d.x + e[5] * d.y + e[9] * d.z,
            e[2] * d.x + e[6] * d.y + e[10] * d.z
        )
    }

    fun inverted(): Mat4f? {
        val m = elements
        val inv = FloatArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]

        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]

        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]

        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]

        val det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (abs(det) <= 1e-8f) return null
        val invDet = 1f / det
        for (i in 0 until 16) {
            inv[i] *= invDet
        }
        return Mat4f(inv)
    }

    companion object {
        val IDENTITY: Mat4f get() = Mat4f()

        fun translation(x: Float, y: Float, z: Float): Mat4f = Mat4f(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                x, y, z, 1f
            )
        )

        fun scale(sx: Float, sy: Float, sz: Float): Mat4f = Mat4f(
            floatArrayOf(
                sx, 0f, 0f, 0f,
                0f, sy, 0f, 0f,
                0f, 0f, sz, 0f,
                0f, 0f, 0f, 1f
            )
        )

        fun compose(position: Vec3f, rotation: Quat4f, scale: Vec3f): Mat4f {
            val q = rotation.normalized()
            val x = q.x; val y = q.y; val z = q.z; val w = q.w
            val x2 = x + x; val y2 = y + y; val z2 = z + z
            val xx = x * x2; val xy = x * y2; val xz = x * z2
            val yy = y * y2; val yz = y * z2; val zz = z * z2
            val wx = w * x2; val wy = w * y2; val wz = w * z2
            val sx = scale.x; val sy = scale.y; val sz = scale.z
            return Mat4f(
                floatArrayOf(
                    (1f - (yy + zz)) * sx, (xy + wz) * sx, (xz - wy) * sx, 0f,
                    (xy - wz) * sy, (1f - (xx + zz)) * sy, (yz + wx) * sy, 0f,
                    (xz + wy) * sz, (yz - wx) * sz, (1f - (xx + yy)) * sz, 0f,
                    position.x, position.y, position.z, 1f
                )
            )
        }

        fun perspectiveFov(fovDegrees: Float, aspect: Float, near: Float, far: Float): Mat4f {
            val f = 1f / tan((fovDegrees * (PI / 180.0)).toFloat() * 0.5f)
            val nf = 1f / (near - far)
            return Mat4f(
                floatArrayOf(
                    f / aspect.coerceAtLeast(1e-4f), 0f, 0f, 0f,
                    0f, f, 0f, 0f,
                    0f, 0f, (far + near) * nf, -1f,
                    0f, 0f, 2f * far * near * nf, 0f
                )
            )
        }

        fun lookAt(eye: Vec3f, target: Vec3f, worldUp: Vec3f = Vec3f.UNIT_Y): Mat4f {
            val zAxis = (eye - target).normalized()
            val xAxis = worldUp.cross(zAxis).normalized()
            val yAxis = zAxis.cross(xAxis)
            return Mat4f(
                floatArrayOf(
                    xAxis.x, yAxis.x, zAxis.x, 0f,
                    xAxis.y, yAxis.y, zAxis.y, 0f,
                    xAxis.z, yAxis.z, zAxis.z, 0f,
                    -xAxis.dot(eye), -yAxis.dot(eye), -zAxis.dot(eye), 1f
                )
            )
        }
    }
}
