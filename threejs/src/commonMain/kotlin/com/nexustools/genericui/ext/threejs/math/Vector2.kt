package com.nexustools.genericui.ext.threejs.math

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2D vector (`THREE.Vector2`).
 */
data class Vector2(
    var x: Float = 0f,
    var y: Float = 0f
) {
    fun set(x: Float, y: Float): Vector2 {
        this.x = x
        this.y = y
        return this
    }

    fun setScalar(scalar: Float): Vector2 = set(scalar, scalar)

    fun copy(v: Vector2): Vector2 = set(v.x, v.y)

    fun clone(): Vector2 = Vector2(x, y)

    fun add(v: Vector2): Vector2 {
        x += v.x
        y += v.y
        return this
    }

    fun addVectors(a: Vector2, b: Vector2): Vector2 = set(a.x + b.x, a.y + b.y)

    fun sub(v: Vector2): Vector2 {
        x -= v.x
        y -= v.y
        return this
    }

    fun subVectors(a: Vector2, b: Vector2): Vector2 = set(a.x - b.x, a.y - b.y)

    fun multiplyScalar(scalar: Float): Vector2 {
        x *= scalar
        y *= scalar
        return this
    }

    fun divideScalar(scalar: Float): Vector2 =
        if (scalar != 0f) multiplyScalar(1f / scalar) else set(0f, 0f)

    fun dot(v: Vector2): Float = x * v.x + y * v.y

    fun cross(v: Vector2): Float = x * v.y - y * v.x

    fun lengthSq(): Float = x * x + y * y

    fun length(): Float = sqrt(lengthSq())

    fun normalize(): Vector2 = divideScalar(length().let { if (it == 0f) 1f else it })

    fun angle(): Float = atan2(-y, -x) + kotlin.math.PI.toFloat()

    fun distanceToSquared(v: Vector2): Float {
        val dx = x - v.x
        val dy = y - v.y
        return dx * dx + dy * dy
    }

    fun distanceTo(v: Vector2): Float = sqrt(distanceToSquared(v))

    fun lerp(v: Vector2, alpha: Float): Vector2 {
        x += (v.x - x) * alpha
        y += (v.y - y) * alpha
        return this
    }

    fun rotateAround(center: Vector2, angle: Float): Vector2 {
        val c = cos(angle)
        val s = sin(angle)
        val dx = x - center.x
        val dy = y - center.y
        x = dx * c - dy * s + center.x
        y = dx * s + dy * c + center.y
        return this
    }
}
