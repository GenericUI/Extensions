package com.nexustools.genericui.ext.engine.math

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Unit quaternion representing 3D orientation (`x, y, z, w`).
 */
data class Quat4f(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var w: Float = 1f
) {
    fun set(x: Float, y: Float, z: Float, w: Float): Quat4f {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
        return this
    }

    fun copyFrom(other: Quat4f): Quat4f = set(other.x, other.y, other.z, other.w)

    fun copy(): Quat4f = Quat4f(x, y, z, w)

    fun setIdentity(): Quat4f = set(0f, 0f, 0f, 1f)

    fun lengthSquared(): Float = x * x + y * y + z * z + w * w

    fun length(): Float = sqrt(lengthSquared())

    fun normalized(): Quat4f {
        val len = length()
        return if (len > 1e-6f) Quat4f(x / len, y / len, z / len, w / len) else Quat4f(0f, 0f, 0f, 1f)
    }

    fun normalizeLocal(): Quat4f {
        val len = length()
        if (len > 1e-6f) {
            val inv = 1f / len
            x *= inv
            y *= inv
            z *= inv
            w *= inv
        } else {
            setIdentity()
        }
        return this
    }

    fun conjugate(): Quat4f = Quat4f(-x, -y, -z, w)

    fun inverted(): Quat4f {
        val normSq = lengthSquared()
        return if (normSq > 1e-6f) {
            val inv = 1f / normSq
            Quat4f(-x * inv, -y * inv, -z * inv, w * inv)
        } else {
            Quat4f(0f, 0f, 0f, 1f)
        }
    }

    fun dot(other: Quat4f): Float = x * other.x + y * other.y + z * other.z + w * other.w

    operator fun times(rhs: Quat4f): Quat4f = Quat4f(
        x = w * rhs.x + x * rhs.w + y * rhs.z - z * rhs.y,
        y = w * rhs.y - x * rhs.z + y * rhs.w + z * rhs.x,
        z = w * rhs.z + x * rhs.y - y * rhs.x + z * rhs.w,
        w = w * rhs.w - x * rhs.x - y * rhs.y - z * rhs.z
    )

    fun rotateVec(v: Vec3f): Vec3f {
        val tx = 2f * (y * v.z - z * v.y)
        val ty = 2f * (z * v.x - x * v.z)
        val tz = 2f * (x * v.y - y * v.x)
        return Vec3f(
            v.x + w * tx + y * tz - z * ty,
            v.y + w * ty + z * tx - x * tz,
            v.z + w * tz + x * ty - y * tx
        )
    }

    fun toEulerRadians(): Vec3f {
        val sinrCosp = 2f * (w * x + y * z)
        val cosrCosp = 1f - 2f * (x * x + y * y)
        val rollX = atan2(sinrCosp, cosrCosp)

        val sinp = 2f * (w * y - z * x)
        val pitchY = if (kotlin.math.abs(sinp) >= 1f) {
            ((PI / 2.0) * if (sinp >= 0f) 1.0 else -1.0).toFloat()
        } else {
            asin(sinp)
        }

        val sinyCosp = 2f * (w * z + x * y)
        val cosyCosp = 1f - 2f * (y * y + z * z)
        val yawZ = atan2(sinyCosp, cosyCosp)
        return Vec3f(rollX, pitchY, yawZ)
    }

    fun slerp(target: Quat4f, t: Float): Quat4f {
        val clamped = t.coerceIn(0f, 1f)
        var tx = target.x
        var ty = target.y
        var tz = target.z
        var tw = target.w
        var cosHalfTheta = x * tx + y * ty + z * tz + w * tw
        if (cosHalfTheta < 0f) {
            tx = -tx
            ty = -ty
            tz = -tz
            tw = -tw
            cosHalfTheta = -cosHalfTheta
        }
        if (cosHalfTheta >= 0.9995f) {
            return Quat4f(
                x + (tx - x) * clamped,
                y + (ty - y) * clamped,
                z + (tz - z) * clamped,
                w + (tw - w) * clamped
            ).normalizeLocal()
        }
        val halfTheta = acos(cosHalfTheta.coerceIn(-1f, 1f))
        val sinHalfTheta = sqrt((1f - cosHalfTheta * cosHalfTheta).coerceAtLeast(1e-8f))
        val ratioA = sin((1f - clamped) * halfTheta) / sinHalfTheta
        val ratioB = sin(clamped * halfTheta) / sinHalfTheta
        return Quat4f(
            x * ratioA + tx * ratioB,
            y * ratioA + ty * ratioB,
            z * ratioA + tz * ratioB,
            w * ratioA + tw * ratioB
        ).normalizeLocal()
    }

    companion object {
        val IDENTITY: Quat4f get() = Quat4f(0f, 0f, 0f, 1f)

        fun fromAxisAngle(axis: Vec3f, angleRad: Float): Quat4f {
            val n = axis.normalized()
            val half = angleRad * 0.5f
            val s = sin(half)
            return Quat4f(n.x * s, n.y * s, n.z * s, cos(half)).normalizeLocal()
        }

        fun fromEulerRadians(xRad: Float, yRad: Float, zRad: Float): Quat4f {
            val hx = xRad * 0.5f
            val hy = yRad * 0.5f
            val hz = zRad * 0.5f
            val cx = cos(hx)
            val sx = sin(hx)
            val cy = cos(hy)
            val sy = sin(hy)
            val cz = cos(hz)
            val sz = sin(hz)
            return Quat4f(
                x = sx * cy * cz - cx * sy * sz,
                y = cx * sy * cz + sx * cy * sz,
                z = cx * cy * sz - sx * sy * cz,
                w = cx * cy * cz + sx * sy * sz
            ).normalizeLocal()
        }
    }
}
