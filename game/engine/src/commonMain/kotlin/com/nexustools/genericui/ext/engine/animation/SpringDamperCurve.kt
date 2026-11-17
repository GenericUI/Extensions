package com.nexustools.genericui.ext.engine.animation

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Analytic damped harmonic oscillator curve supporting underdamped ([dampingRatio] < 1),
 * critically damped ([dampingRatio] == 1), and overdamped ([dampingRatio] > 1) spring physics
 * normalized cleanly over `[0.0f..1.0f]`.
 */
class SpringDamperCurve(
    stiffness: Float = 180f,
    dampingRatio: Float = 0.7f,
    val initialVelocity: Float = 0f,
    settlingDurationSeconds: Float = 1.0f
) : AnimationCurve {
    val stiffness: Float = stiffness.coerceAtLeast(0.1f)
    val dampingRatio: Float = dampingRatio.coerceAtLeast(0.01f)
    val settlingDurationSeconds: Float = settlingDurationSeconds.coerceAtLeast(0.01f)

    override fun sample(rawProgress: Float): Float {
        val p = rawProgress.coerceIn(0f, 1f)
        if (p <= 0f) return 0f
        if (p >= 1f) return 1f
        val endRaw = evaluateUnnormalized(settlingDurationSeconds.toDouble())
        val raw = evaluateUnnormalized(p.toDouble() * settlingDurationSeconds.toDouble())
        return (raw + p.toDouble() * (1.0 - endRaw)).toFloat()
    }

    private fun evaluateUnnormalized(t: Double): Double {
        val wn = sqrt(stiffness.toDouble())
        val zeta = dampingRatio.toDouble()
        val v0 = initialVelocity.toDouble()
        val error = when {
            zeta < 1.0 - 1e-4 -> {
                val wd = wn * sqrt(1.0 - zeta * zeta)
                val env = exp(-zeta * wn * t)
                val b = (zeta * wn - v0) / wd
                env * (cos(wd * t) + b * sin(wd * t))
            }
            zeta <= 1.0 + 1e-4 -> {
                exp(-wn * t) * (1.0 + (wn - v0) * t)
            }
            else -> {
                val disc = sqrt(zeta * zeta - 1.0)
                val r1 = -wn * (zeta - disc)
                val r2 = -wn * (zeta + disc)
                val denom = (r1 - r2).let { if (kotlin.math.abs(it) < 1e-6) 1e-6 else it }
                val c2 = (r1 + v0) / denom
                val c1 = 1.0 - c2
                c1 * exp(r1 * t) + c2 * exp(r2 * t)
            }
        }
        return 1.0 - error
    }
}
