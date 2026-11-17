package com.nexustools.genericui.ext.engine.animation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * Periodic wave and damped-oscillation math curve evaluated at normalized progress `0f..1f`.
 */
class WaveMathCurve(
    val frequencyHz: Float = 2f,
    val amplitude: Float = 1f,
    val phaseOffsetRad: Float = 0f,
    val decay: Float = 0f,
    val shape: WaveShape = WaveShape.SINE
) : AnimationCurve {

    override fun sample(rawProgress: Float): Float {
        val p = rawProgress.coerceIn(0f, 1f)
        val angle = (2.0 * PI * frequencyHz.toDouble() * p.toDouble()) + phaseOffsetRad.toDouble()
        val envelope = if (decay > 0f) exp(-decay.toDouble() * p.toDouble()) else 1.0
        val wave = when (shape) {
            WaveShape.SINE -> sin(angle)
            WaveShape.COSINE -> cos(angle)
            WaveShape.TRIANGLE -> {
                val cycles = (frequencyHz.toDouble() * p.toDouble()) + (phaseOffsetRad.toDouble() / (2.0 * PI))
                val frac = cycles - floor(cycles)
                1.0 - 4.0 * abs(frac - 0.5)
            }
            WaveShape.DAMPED_SINE -> sin(angle) * exp(-maxOf(decay.toDouble(), 2.5) * p.toDouble())
        }
        return (amplitude.toDouble() * wave * envelope).toFloat()
    }
}
