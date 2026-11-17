package com.nexustools.genericui.ext.engine.math

import kotlin.math.sqrt

/**
 * Three-dimensional single-precision floating-point vector for 3D positions, directions, and scales.
 */
data class Vec3f(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    operator fun plus(other: Vec3f): Vec3f = Vec3f(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vec3f): Vec3f = Vec3f(x - other.x, y - other.y, z - other.z)

    operator fun times(scalar: Float): Vec3f = Vec3f(x * scalar, y * scalar, z * scalar)

    operator fun div(scalar: Float): Vec3f =
        if (scalar != 0f) Vec3f(x / scalar, y / scalar, z / scalar) else Vec3f(0f, 0f, 0f)

    operator fun unaryMinus(): Vec3f = Vec3f(-x, -y, -z)

    fun set(x: Float, y: Float, z: Float): Vec3f {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    fun copyFrom(other: Vec3f): Vec3f = set(other.x, other.y, other.z)

    fun copy(): Vec3f = Vec3f(x, y, z)

    fun dot(other: Vec3f): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3f): Vec3f = Vec3f(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun lengthSquared(): Float = x * x + y * y + z * z

    fun length(): Float = sqrt(lengthSquared())

    fun distanceTo(other: Vec3f): Float {
        val dx = other.x - x
        val dy = other.y - y
        val dz = other.z - z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun normalized(): Vec3f {
        val len = length()
        return if (len > 1e-6f) Vec3f(x / len, y / len, z / len) else Vec3f(0f, 0f, 0f)
    }

    fun normalizeLocal(): Vec3f {
        val len = length()
        if (len > 1e-6f) {
            x /= len
            y /= len
            z /= len
        } else {
            x = 0f
            y = 0f
            z = 0f
        }
        return this
    }

    fun lerp(target: Vec3f, t: Float): Vec3f = Vec3f(
        x + (target.x - x) * t,
        y + (target.y - y) * t,
        z + (target.z - z) * t
    )

    companion object {
        val ZERO: Vec3f get() = Vec3f(0f, 0f, 0f)
        val ONE: Vec3f get() = Vec3f(1f, 1f, 1f)
        val UNIT_X: Vec3f get() = Vec3f(1f, 0f, 0f)
        val UNIT_Y: Vec3f get() = Vec3f(0f, 1f, 0f)
        val UNIT_Z: Vec3f get() = Vec3f(0f, 0f, 1f)
    }
}
