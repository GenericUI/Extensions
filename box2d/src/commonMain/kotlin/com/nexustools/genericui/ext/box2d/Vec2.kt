package com.nexustools.genericui.ext.box2d

import kotlin.math.sqrt

/**
 * 2D column vector (`b2Vec2`) for Box2D rigid-body physics calculations.
 */
data class Vec2(
    var x: Float = 0f,
    var y: Float = 0f
) {
    fun setZero(): Vec2 {
        x = 0f
        y = 0f
        return this
    }

    fun set(x: Float, y: Float): Vec2 {
        this.x = x
        this.y = y
        return this
    }

    fun copy(v: Vec2): Vec2 = set(v.x, v.y)

    fun clone(): Vec2 = Vec2(x, y)

    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

    operator fun times(scalar: Float): Vec2 = Vec2(x * scalar, y * scalar)

    operator fun unaryMinus(): Vec2 = Vec2(-x, -y)

    fun addLocal(v: Vec2): Vec2 {
        x += v.x
        y += v.y
        return this
    }

    fun addScaledLocal(v: Vec2, scale: Float): Vec2 {
        x += v.x * scale
        y += v.y * scale
        return this
    }

    fun subLocal(v: Vec2): Vec2 {
        x -= v.x
        y -= v.y
        return this
    }

    fun mulLocal(scalar: Float): Vec2 {
        x *= scalar
        y *= scalar
        return this
    }

    fun lengthSquared(): Float = x * x + y * y

    fun length(): Float = sqrt(lengthSquared())

    fun normalize(): Float {
        val len = length()
        if (len < 1e-7f) return 0f
        val inv = 1f / len
        x *= inv
        y *= inv
        return len
    }

    fun dot(b: Vec2): Float = x * b.x + y * b.y

    fun cross(b: Vec2): Float = x * b.y - y * b.x

    companion object {
        fun dot(a: Vec2, b: Vec2): Float = a.x * b.x + a.y * b.y
        fun cross(a: Vec2, b: Vec2): Float = a.x * b.y - a.y * b.x
        fun cross(a: Vec2, s: Float): Vec2 = Vec2(s * a.y, -s * a.x)
        fun cross(s: Float, a: Vec2): Vec2 = Vec2(-s * a.y, s * a.x)
        fun distanceSquared(a: Vec2, b: Vec2): Float {
            val dx = a.x - b.x
            val dy = a.y - b.y
            return dx * dx + dy * dy
        }
        fun distance(a: Vec2, b: Vec2): Float = sqrt(distanceSquared(a, b))
    }
}
