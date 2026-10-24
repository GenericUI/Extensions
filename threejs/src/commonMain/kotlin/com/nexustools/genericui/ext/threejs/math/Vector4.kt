package com.nexustools.genericui.ext.threejs.math

import kotlin.math.sqrt

/**
 * 4D vector (`THREE.Vector4`).
 */
data class Vector4(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var w: Float = 1f
) {
    fun set(x: Float, y: Float, z: Float, w: Float): Vector4 {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
        return this
    }

    fun copy(v: Vector4): Vector4 = set(v.x, v.y, v.z, v.w)

    fun clone(): Vector4 = Vector4(x, y, z, w)

    fun add(v: Vector4): Vector4 {
        x += v.x
        y += v.y
        z += v.z
        w += v.w
        return this
    }

    fun sub(v: Vector4): Vector4 {
        x -= v.x
        y -= v.y
        z -= v.z
        w -= v.w
        return this
    }

    fun multiplyScalar(scalar: Float): Vector4 {
        x *= scalar
        y *= scalar
        z *= scalar
        w *= scalar
        return this
    }

    fun dot(v: Vector4): Float = x * v.x + y * v.y + z * v.z + w * v.w

    fun lengthSq(): Float = dot(this)

    fun length(): Float = sqrt(lengthSq())

    fun normalize(): Vector4 {
        val len = length()
        return if (len > 0f) multiplyScalar(1f / len) else set(0f, 0f, 0f, 1f)
    }

    fun applyMatrix4(m: Matrix4): Vector4 {
        val vx = x
        val vy = y
        val vz = z
        val vw = w
        val e = m.elements
        x = e[0] * vx + e[4] * vy + e[8] * vz + e[12] * vw
        y = e[1] * vx + e[5] * vy + e[9] * vz + e[13] * vw
        z = e[2] * vx + e[6] * vy + e[10] * vz + e[14] * vw
        w = e[3] * vx + e[7] * vy + e[11] * vz + e[15] * vw
        return this
    }
}
