package com.nexustools.genericui.ext.box3d

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D orientation quaternion (`x`, `y`, `z`, `w`) for rigid body physics.
 */
data class Quat3D(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var w: Float = 1f
) {
    fun set(x: Float, y: Float, z: Float, w: Float): Quat3D {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
        return this
    }

    fun copy(other: Quat3D): Quat3D {
        this.x = other.x
        this.y = other.y
        this.z = other.z
        this.w = other.w
        return this
    }

    fun clone(): Quat3D = Quat3D(x, y, z, w)

    fun setFromAxisAngle(axis: Vec3, angleRad: Float): Quat3D {
        val half = angleRad * 0.5f
        val s = sin(half)
        val n = axis.normalized()
        x = n.x * s
        y = n.y * s
        z = n.z * s
        w = cos(half)
        return normalize()
    }

    fun normalize(): Quat3D {
        val len = sqrt(x * x + y * y + z * z + w * w)
        if (len > 1e-6f) {
            x /= len
            y /= len
            z /= len
            w /= len
        } else {
            x = 0f
            y = 0f
            z = 0f
            w = 1f
        }
        return this
    }

    fun conjugate(): Quat3D = Quat3D(-x, -y, -z, w)

    operator fun times(other: Quat3D): Quat3D = Quat3D(
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w,
        w * other.w - x * other.x - y * other.y - z * other.z
    )

    fun rotateVec3(v: Vec3): Vec3 {
        val qx = x
        val qy = y
        val qz = z
        val qw = w

        val ix = qw * v.x + qy * v.z - qz * v.y
        val iy = qw * v.y + qz * v.x - qx * v.z
        val iz = qw * v.z + qx * v.y - qy * v.x
        val iw = -qx * v.x - qy * v.y - qz * v.z

        return Vec3(
            ix * qw + iw * -qx + iy * -qz - iz * -qy,
            iy * qw + iw * -qy + iz * -qx - ix * -qz,
            iz * qw + iw * -qz + ix * -qy - iy * -qx
        )
    }

    fun inverseRotateVec3(v: Vec3): Vec3 = conjugate().rotateVec3(v)

    fun integrateAngularVelocity(omega: Vec3, dt: Float): Quat3D {
        val halfDt = 0.5f * dt
        val dq = Quat3D(omega.x * halfDt, omega.y * halfDt, omega.z * halfDt, 0f) * this
        x += dq.x
        y += dq.y
        z += dq.z
        w += dq.w
        return normalize()
    }

    companion object {
        val IDENTITY: Quat3D get() = Quat3D(0f, 0f, 0f, 1f)
    }
}
