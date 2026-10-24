package com.nexustools.genericui.ext.threejs.math

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * 3D vector (`THREE.Vector3`).
 */
data class Vector3(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    fun set(x: Float, y: Float, z: Float): Vector3 {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    fun setScalar(scalar: Float): Vector3 = set(scalar, scalar, scalar)

    fun copy(v: Vector3): Vector3 = set(v.x, v.y, v.z)

    fun clone(): Vector3 = Vector3(x, y, z)

    fun add(v: Vector3): Vector3 {
        x += v.x
        y += v.y
        z += v.z
        return this
    }

    fun addScalar(s: Float): Vector3 {
        x += s
        y += s
        z += s
        return this
    }

    fun addVectors(a: Vector3, b: Vector3): Vector3 =
        set(a.x + b.x, a.y + b.y, a.z + b.z)

    fun addScaledVector(v: Vector3, s: Float): Vector3 {
        x += v.x * s
        y += v.y * s
        z += v.z * s
        return this
    }

    fun sub(v: Vector3): Vector3 {
        x -= v.x
        y -= v.y
        z -= v.z
        return this
    }

    fun subVectors(a: Vector3, b: Vector3): Vector3 =
        set(a.x - b.x, a.y - b.y, a.z - b.z)

    fun multiply(v: Vector3): Vector3 {
        x *= v.x
        y *= v.y
        z *= v.z
        return this
    }

    fun multiplyScalar(scalar: Float): Vector3 {
        x *= scalar
        y *= scalar
        z *= scalar
        return this
    }

    fun divideScalar(scalar: Float): Vector3 =
        if (scalar != 0f) multiplyScalar(1f / scalar) else set(0f, 0f, 0f)

    fun min(v: Vector3): Vector3 {
        x = minOf(x, v.x)
        y = minOf(y, v.y)
        z = minOf(z, v.z)
        return this
    }

    fun max(v: Vector3): Vector3 {
        x = maxOf(x, v.x)
        y = maxOf(y, v.y)
        z = maxOf(z, v.z)
        return this
    }

    fun clamp(min: Vector3, max: Vector3): Vector3 {
        x = x.coerceIn(min.x, max.x)
        y = y.coerceIn(min.y, max.y)
        z = z.coerceIn(min.z, max.z)
        return this
    }

    fun negate(): Vector3 = multiplyScalar(-1f)

    fun dot(v: Vector3): Float = x * v.x + y * v.y + z * v.z

    fun lengthSq(): Float = x * x + y * y + z * z

    fun length(): Float = sqrt(lengthSq())

    fun normalize(): Vector3 {
        val len = length()
        return if (len > 0f) divideScalar(len) else set(0f, 0f, 0f)
    }

    fun setLength(length: Float): Vector3 = normalize().multiplyScalar(length)

    fun lerp(v: Vector3, alpha: Float): Vector3 {
        x += (v.x - x) * alpha
        y += (v.y - y) * alpha
        z += (v.z - z) * alpha
        return this
    }

    fun lerpVectors(v1: Vector3, v2: Vector3, alpha: Float): Vector3 =
        set(
            v1.x + (v2.x - v1.x) * alpha,
            v1.y + (v2.y - v1.y) * alpha,
            v1.z + (v2.z - v1.z) * alpha
        )

    fun cross(v: Vector3): Vector3 = crossVectors(this, v)

    fun crossVectors(a: Vector3, b: Vector3): Vector3 {
        val ax = a.x
        val ay = a.y
        val az = a.z
        val bx = b.x
        val by = b.y
        val bz = b.z
        x = ay * bz - az * by
        y = az * bx - ax * bz
        z = ax * by - ay * bx
        return this
    }

    fun angleTo(v: Vector3): Float {
        val denominator = sqrt(lengthSq() * v.lengthSq())
        if (denominator == 0f) return (kotlin.math.PI / 2.0).toFloat()
        val theta = dot(v) / denominator
        return acos(MathUtils.clamp(theta, -1f, 1f))
    }

    fun distanceToSquared(v: Vector3): Float {
        val dx = x - v.x
        val dy = y - v.y
        val dz = z - v.z
        return dx * dx + dy * dy + dz * dz
    }

    fun distanceTo(v: Vector3): Float = sqrt(distanceToSquared(v))

    fun applyMatrix3(m: Matrix3): Vector3 {
        val vx = x
        val vy = y
        val vz = z
        val e = m.elements
        x = e[0] * vx + e[3] * vy + e[6] * vz
        y = e[1] * vx + e[4] * vy + e[7] * vz
        z = e[2] * vx + e[5] * vy + e[8] * vz
        return this
    }

    fun applyMatrix4(m: Matrix4): Vector3 {
        val vx = x
        val vy = y
        val vz = z
        val e = m.elements
        val w = 1f / (e[3] * vx + e[7] * vy + e[11] * vz + e[15]).let { if (it == 0f) 1f else it }
        x = (e[0] * vx + e[4] * vy + e[8] * vz + e[12]) * w
        y = (e[1] * vx + e[5] * vy + e[9] * vz + e[13]) * w
        z = (e[2] * vx + e[6] * vy + e[10] * vz + e[14]) * w
        return this
    }

    fun transformDirection(m: Matrix4): Vector3 {
        val vx = x
        val vy = y
        val vz = z
        val e = m.elements
        x = e[0] * vx + e[4] * vy + e[8] * vz
        y = e[1] * vx + e[5] * vy + e[9] * vz
        z = e[2] * vx + e[6] * vy + e[10] * vz
        return normalize()
    }

    fun applyQuaternion(q: Quaternion): Vector3 {
        val vx = x
        val vy = y
        val vz = z
        val qx = q.x
        val qy = q.y
        val qz = q.z
        val qw = q.w

        val tx = 2f * (qy * vz - qz * vy)
        val ty = 2f * (qz * vx - qx * vz)
        val tz = 2f * (qx * vy - qy * vx)

        x = vx + qw * tx + qy * tz - qz * ty
        y = vy + qw * ty + qz * tx - qx * tz
        z = vz + qw * tz + qx * ty - qy * tx
        return this
    }

    fun setFromMatrixPosition(m: Matrix4): Vector3 {
        val e = m.elements
        return set(e[12], e[13], e[14])
    }
}
