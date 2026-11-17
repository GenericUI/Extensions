package com.nexustools.genericui.ext.box3d

import kotlin.math.sqrt

/**
 * 3D physics vector (`x`, `y`, `z`).
 */
data class Vec3(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    fun set(x: Float, y: Float, z: Float): Vec3 {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    fun copy(other: Vec3): Vec3 {
        this.x = other.x
        this.y = other.y
        this.z = other.z
        return this
    }

    fun clone(): Vec3 = Vec3(x, y, z)

    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vec3 = Vec3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float): Vec3 = if (scalar != 0f) Vec3(x / scalar, y / scalar, z / scalar) else Vec3()
    operator fun unaryMinus(): Vec3 = Vec3(-x, -y, -z)

    fun addLocal(other: Vec3): Vec3 {
        x += other.x
        y += other.y
        z += other.z
        return this
    }

    fun subLocal(other: Vec3): Vec3 {
        x -= other.x
        y -= other.y
        z -= other.z
        return this
    }

    fun mulLocal(scalar: Float): Vec3 {
        x *= scalar
        y *= scalar
        z *= scalar
        return this
    }

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun lengthSq(): Float = x * x + y * y + z * z

    fun length(): Float = sqrt(lengthSq())

    fun normalize(): Vec3 {
        val len = length()
        if (len > 1e-6f) {
            x /= len
            y /= len
            z /= len
        }
        return this
    }

    fun normalized(): Vec3 = clone().normalize()

    fun distanceTo(other: Vec3): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        val ZERO: Vec3 get() = Vec3(0f, 0f, 0f)
        val UNIT_X: Vec3 get() = Vec3(1f, 0f, 0f)
        val UNIT_Y: Vec3 get() = Vec3(0f, 1f, 0f)
        val UNIT_Z: Vec3 get() = Vec3(0f, 0f, 1f)
    }
}
