package com.nexustools.genericui.ext.engine.math

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Two-dimensional single-precision floating-point vector for 2D spatial transforms, physics
 * coordinates, and skeletal kinematics.
 */
data class Vec2f(
    var x: Float = 0f,
    var y: Float = 0f
) {
    operator fun plus(other: Vec2f): Vec2f = Vec2f(x + other.x, y + other.y)

    operator fun minus(other: Vec2f): Vec2f = Vec2f(x - other.x, y - other.y)

    operator fun times(scalar: Float): Vec2f = Vec2f(x * scalar, y * scalar)

    operator fun div(scalar: Float): Vec2f =
        if (scalar != 0f) Vec2f(x / scalar, y / scalar) else Vec2f(0f, 0f)

    operator fun unaryMinus(): Vec2f = Vec2f(-x, -y)

    fun set(x: Float, y: Float): Vec2f {
        this.x = x
        this.y = y
        return this
    }

    fun copyFrom(other: Vec2f): Vec2f = set(other.x, other.y)

    fun copy(): Vec2f = Vec2f(x, y)

    fun dot(other: Vec2f): Float = x * other.x + y * other.y

    fun cross(other: Vec2f): Float = x * other.y - y * other.x

    fun lengthSquared(): Float = x * x + y * y

    fun length(): Float = hypot(x, y)

    fun distanceTo(other: Vec2f): Float = hypot(other.x - x, other.y - y)

    fun normalized(): Vec2f {
        val len = sqrt(lengthSquared())
        return if (len > 1e-6f) Vec2f(x / len, y / len) else Vec2f(0f, 0f)
    }

    fun normalizeLocal(): Vec2f {
        val len = sqrt(lengthSquared())
        if (len > 1e-6f) {
            x /= len
            y /= len
        } else {
            x = 0f
            y = 0f
        }
        return this
    }

    fun angleRad(): Float = atan2(y, x)

    fun rotatedBy(radians: Float): Vec2f {
        val c = cos(radians)
        val s = sin(radians)
        return Vec2f(x * c - y * s, x * s + y * c)
    }

    fun lerp(target: Vec2f, t: Float): Vec2f = Vec2f(
        x + (target.x - x) * t,
        y + (target.y - y) * t
    )

    companion object {
        val ZERO: Vec2f get() = Vec2f(0f, 0f)
        val ONE: Vec2f get() = Vec2f(1f, 1f)
        val UNIT_X: Vec2f get() = Vec2f(1f, 0f)
        val UNIT_Y: Vec2f get() = Vec2f(0f, 1f)

        fun fromAngle(radians: Float, length: Float = 1f): Vec2f =
            Vec2f(cos(radians) * length, sin(radians) * length)
    }
}
