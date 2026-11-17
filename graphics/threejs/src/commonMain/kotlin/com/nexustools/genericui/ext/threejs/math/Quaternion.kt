package com.nexustools.genericui.ext.threejs.math

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Unit quaternion for 3D rotations (`THREE.Quaternion`).
 */
data class Quaternion(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var w: Float = 1f
) {
    fun set(x: Float, y: Float, z: Float, w: Float): Quaternion {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
        return this
    }

    fun identity(): Quaternion = set(0f, 0f, 0f, 1f)

    fun copy(q: Quaternion): Quaternion = set(q.x, q.y, q.z, q.w)

    fun clone(): Quaternion = Quaternion(x, y, z, w)

    fun setFromEuler(euler: Euler): Quaternion {
        val c1 = cos(euler.x * 0.5f)
        val c2 = cos(euler.y * 0.5f)
        val c3 = cos(euler.z * 0.5f)
        val s1 = sin(euler.x * 0.5f)
        val s2 = sin(euler.y * 0.5f)
        val s3 = sin(euler.z * 0.5f)

        when (euler.order) {
            "XYZ" -> {
                x = s1 * c2 * c3 + c1 * s2 * s3
                y = c1 * s2 * c3 - s1 * c2 * s3
                z = c1 * c2 * s3 + s1 * s2 * c3
                w = c1 * c2 * c3 - s1 * s2 * s3
            }
            "YXZ" -> {
                x = s1 * c2 * c3 + c1 * s2 * s3
                y = c1 * s2 * c3 - s1 * c2 * s3
                z = c1 * c2 * s3 - s1 * s2 * c3
                w = c1 * c2 * c3 + s1 * s2 * s3
            }
            else -> {
                x = s1 * c2 * c3 + c1 * s2 * s3
                y = c1 * s2 * c3 - s1 * c2 * s3
                z = c1 * c2 * s3 + s1 * s2 * c3
                w = c1 * c2 * c3 - s1 * s2 * s3
            }
        }
        return this
    }

    fun setFromAxisAngle(axis: Vector3, angle: Float): Quaternion {
        val halfAngle = angle * 0.5f
        val s = sin(halfAngle)
        x = axis.x * s
        y = axis.y * s
        z = axis.z * s
        w = cos(halfAngle)
        return this
    }

    fun setFromRotationMatrix(m: Matrix4): Quaternion {
        val te = m.elements
        val m11 = te[0]; val m12 = te[4]; val m13 = te[8]
        val m21 = te[1]; val m22 = te[5]; val m23 = te[9]
        val m31 = te[2]; val m32 = te[6]; val m33 = te[10]
        val trace = m11 + m22 + m33

        if (trace > 0f) {
            val s = 0.5f / sqrt(trace + 1f)
            w = 0.25f / s
            x = (m32 - m23) * s
            y = (m13 - m31) * s
            z = (m21 - m12) * s
        } else if (m11 > m22 && m11 > m33) {
            val s = 2f * sqrt(1f + m11 - m22 - m33)
            w = (m32 - m23) / s
            x = 0.25f * s
            y = (m12 + m21) / s
            z = (m13 + m31) / s
        } else if (m22 > m33) {
            val s = 2f * sqrt(1f + m22 - m11 - m33)
            w = (m13 - m31) / s
            x = (m12 + m21) / s
            y = 0.25f * s
            z = (m23 + m32) / s
        } else {
            val s = 2f * sqrt(1f + m33 - m11 - m22)
            w = (m21 - m12) / s
            x = (m13 + m31) / s
            y = (m23 + m32) / s
            z = 0.25f * s
        }
        return this
    }

    fun invert(): Quaternion = conjugate()

    fun conjugate(): Quaternion {
        x = -x
        y = -y
        z = -z
        return this
    }

    fun dot(v: Quaternion): Float = x * v.x + y * v.y + z * v.z + w * v.w

    fun lengthSq(): Float = x * x + y * y + z * z + w * w

    fun length(): Float = sqrt(lengthSq())

    fun normalize(): Quaternion {
        var l = length()
        if (l == 0f) {
            x = 0f; y = 0f; z = 0f; w = 1f
        } else {
            l = 1f / l
            x *= l; y *= l; z *= l; w *= l
        }
        return this
    }

    fun multiply(q: Quaternion): Quaternion = multiplyQuaternions(this, q)

    fun premultiply(q: Quaternion): Quaternion = multiplyQuaternions(q, this)

    fun multiplyQuaternions(a: Quaternion, b: Quaternion): Quaternion {
        val qax = a.x; val qay = a.y; val qaz = a.z; val qaw = a.w
        val qbx = b.x; val qby = b.y; val qbz = b.z; val qbw = b.w

        x = qax * qbw + qaw * qbx + qay * qbz - qaz * qby
        y = qay * qbw + qaw * qby + qaz * qbx - qax * qbz
        z = qaz * qbw + qaw * qbz + qax * qby - qay * qbx
        w = qaw * qbw - qax * qbx - qay * qby - qaz * qbz
        return this
    }

    fun slerp(qb: Quaternion, t: Float): Quaternion {
        if (t == 0f) return this
        if (t == 1f) return copy(qb)

        val x0 = x; val y0 = y; val z0 = z; val w0 = w
        var cosHalfTheta = x0 * qb.x + y0 * qb.y + z0 * qb.z + w0 * qb.w

        if (cosHalfTheta < 0f) {
            x = -qb.x; y = -qb.y; z = -qb.z; w = -qb.w
            cosHalfTheta = -cosHalfTheta
        } else {
            copy(qb)
        }

        if (cosHalfTheta >= 1f) {
            x = x0; y = y0; z = z0; w = w0
            return this
        }

        val sqrSinHalfTheta = 1f - cosHalfTheta * cosHalfTheta
        if (sqrSinHalfTheta <= 1e-6f) {
            val s = 1f - t
            x = s * x0 + t * x
            y = s * y0 + t * y
            z = s * z0 + t * z
            w = s * w0 + t * w
            return normalize()
        }

        val sinHalfTheta = sqrt(sqrSinHalfTheta)
        val halfTheta = acos(cosHalfTheta)
        val ratioA = sin((1f - t) * halfTheta) / sinHalfTheta
        val ratioB = sin(t * halfTheta) / sinHalfTheta

        x = x0 * ratioA + x * ratioB
        y = y0 * ratioA + y * ratioB
        z = z0 * ratioA + z * ratioB
        w = w0 * ratioA + w * ratioB
        return this
    }
}
