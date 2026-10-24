package com.nexustools.genericui.ext.threejs.math

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * Three.js-compatible mathematical utility functions (`THREE.MathUtils`).
 */
object MathUtils {
    const val DEG2RAD: Float = (PI / 180.0).toFloat()
    const val RAD2DEG: Float = (180.0 / PI).toFloat()

    private var nextIdCounter: Long = 1L

    fun generateUUID(): String {
        val chars = "0123456789abcdef"
        val sb = StringBuilder(36)
        for (i in 0 until 36) {
            when (i) {
                8, 13, 18, 23 -> sb.append('-')
                14 -> sb.append('4')
                19 -> sb.append(chars[(Random.nextInt(16) and 0x3) or 0x8])
                else -> sb.append(chars[Random.nextInt(16)])
            }
        }
        return sb.toString()
    }

    fun nextObjectId(): Long = nextIdCounter++

    fun clamp(value: Float, min: Float, max: Float): Float =
        value.coerceIn(min, max)

    fun euclideanModulo(n: Float, m: Float): Float =
        ((n % m) + m) % m

    fun mapLinear(x: Float, a1: Float, a2: Float, b1: Float, b2: Float): Float =
        b1 + (x - a1) * (b2 - b1) / (a2 - a1)

    fun inverseLerp(x: Float, y: Float, value: Float): Float =
        if (x != y) (value - x) / (y - x) else 0f

    fun lerp(x: Float, y: Float, t: Float): Float =
        (1f - t) * x + t * y

    fun damp(x: Float, y: Float, lambda: Float, dt: Float): Float =
        lerp(x, y, 1f - kotlin.math.exp(-lambda * dt))

    fun smoothstep(x: Float, min: Float, max: Float): Float {
        if (x <= min) return 0f
        if (x >= max) return 1f
        val v = (x - min) / (max - min)
        return v * v * (3f - 2f * v)
    }

    fun smootherstep(x: Float, min: Float, max: Float): Float {
        if (x <= min) return 0f
        if (x >= max) return 1f
        val v = (x - min) / (max - min)
        return v * v * v * (v * (v * 6f - 15f) + 10f)
    }

    fun degToRad(degrees: Float): Float = degrees * DEG2RAD

    fun radToDeg(radians: Float): Float = radians * RAD2DEG

    fun isPowerOfTwo(value: Int): Boolean =
        value > 0 && (value and (value - 1)) == 0

    fun ceilPowerOfTwo(value: Float): Int =
        2.0.pow(ceil(ln(value.toDouble()) / ln(2.0))).toInt()

    fun floorPowerOfTwo(value: Float): Int =
        2.0.pow(floor(ln(value.toDouble()) / ln(2.0))).toInt()
}
