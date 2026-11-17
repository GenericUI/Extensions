package com.nexustools.genericui.ext.engine.animation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Complete set of 31 standard parametric easing functions (`LINEAR` plus `QUAD`, `CUBIC`,
 * `QUART`, `QUINT`, `SINE`, `EXPO`, `CIRC`, `BACK`, `ELASTIC`, and `BOUNCE` in `IN`, `OUT`,
 * and `IN_OUT` variants).
 */
enum class EasingFunction : AnimationCurve {
    LINEAR,
    QUAD_IN,
    QUAD_OUT,
    QUAD_IN_OUT,
    CUBIC_IN,
    CUBIC_OUT,
    CUBIC_IN_OUT,
    QUART_IN,
    QUART_OUT,
    QUART_IN_OUT,
    QUINT_IN,
    QUINT_OUT,
    QUINT_IN_OUT,
    SINE_IN,
    SINE_OUT,
    SINE_IN_OUT,
    EXPO_IN,
    EXPO_OUT,
    EXPO_IN_OUT,
    CIRC_IN,
    CIRC_OUT,
    CIRC_IN_OUT,
    BACK_IN,
    BACK_OUT,
    BACK_IN_OUT,
    ELASTIC_IN,
    ELASTIC_OUT,
    ELASTIC_IN_OUT,
    BOUNCE_IN,
    BOUNCE_OUT,
    BOUNCE_IN_OUT;

    override fun sample(rawProgress: Float): Float {
        val t = rawProgress.coerceIn(0f, 1f)
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val d = t.toDouble()
        return when (this) {
            LINEAR -> t
            QUAD_IN -> (d * d).toFloat()
            QUAD_OUT -> (1.0 - (1.0 - d) * (1.0 - d)).toFloat()
            QUAD_IN_OUT -> if (d < 0.5) (2.0 * d * d).toFloat() else (1.0 - (-2.0 * d + 2.0).pow(2) * 0.5).toFloat()

            CUBIC_IN -> (d * d * d).toFloat()
            CUBIC_OUT -> (1.0 - (1.0 - d).pow(3)).toFloat()
            CUBIC_IN_OUT -> if (d < 0.5) (4.0 * d * d * d).toFloat() else (1.0 - (-2.0 * d + 2.0).pow(3) * 0.5).toFloat()

            QUART_IN -> (d * d * d * d).toFloat()
            QUART_OUT -> (1.0 - (1.0 - d).pow(4)).toFloat()
            QUART_IN_OUT -> if (d < 0.5) (8.0 * d * d * d * d).toFloat() else (1.0 - (-2.0 * d + 2.0).pow(4) * 0.5).toFloat()

            QUINT_IN -> (d * d * d * d * d).toFloat()
            QUINT_OUT -> (1.0 - (1.0 - d).pow(5)).toFloat()
            QUINT_IN_OUT -> if (d < 0.5) (16.0 * d * d * d * d * d).toFloat() else (1.0 - (-2.0 * d + 2.0).pow(5) * 0.5).toFloat()

            SINE_IN -> (1.0 - cos((d * PI) * 0.5)).toFloat()
            SINE_OUT -> sin((d * PI) * 0.5).toFloat()
            SINE_IN_OUT -> (-(cos(PI * d) - 1.0) * 0.5).toFloat()

            EXPO_IN -> 2.0.pow(10.0 * d - 10.0).toFloat()
            EXPO_OUT -> (1.0 - 2.0.pow(-10.0 * d)).toFloat()
            EXPO_IN_OUT -> if (d < 0.5) {
                (2.0.pow(20.0 * d - 10.0) * 0.5).toFloat()
            } else {
                ((2.0 - 2.0.pow(-20.0 * d + 10.0)) * 0.5).toFloat()
            }

            CIRC_IN -> (1.0 - sqrt((1.0 - d * d).coerceAtLeast(0.0))).toFloat()
            CIRC_OUT -> sqrt((1.0 - (d - 1.0).pow(2)).coerceAtLeast(0.0)).toFloat()
            CIRC_IN_OUT -> if (d < 0.5) {
                ((1.0 - sqrt((1.0 - (2.0 * d).pow(2)).coerceAtLeast(0.0))) * 0.5).toFloat()
            } else {
                ((sqrt((1.0 - (-2.0 * d + 2.0).pow(2)).coerceAtLeast(0.0)) + 1.0) * 0.5).toFloat()
            }

            BACK_IN -> {
                val c1 = 1.70158
                val c3 = c1 + 1.0
                (c3 * d * d * d - c1 * d * d).toFloat()
            }
            BACK_OUT -> {
                val c1 = 1.70158
                val c3 = c1 + 1.0
                val u = d - 1.0
                (1.0 + c3 * u * u * u + c1 * u * u).toFloat()
            }
            BACK_IN_OUT -> {
                val c1 = 1.70158
                val c2 = c1 * 1.525
                if (d < 0.5) {
                    (((2.0 * d).pow(2) * ((c2 + 1.0) * 2.0 * d - c2)) * 0.5).toFloat()
                } else {
                    (((2.0 * d - 2.0).pow(2) * ((c2 + 1.0) * (d * 2.0 - 2.0) + c2) + 2.0) * 0.5).toFloat()
                }
            }

            ELASTIC_IN -> {
                val c4 = (2.0 * PI) / 3.0
                (-(2.0.pow(10.0 * d - 10.0)) * sin((d * 10.0 - 10.75) * c4)).toFloat()
            }
            ELASTIC_OUT -> {
                val c4 = (2.0 * PI) / 3.0
                (2.0.pow(-10.0 * d) * sin((d * 10.0 - 0.75) * c4) + 1.0).toFloat()
            }
            ELASTIC_IN_OUT -> {
                val c5 = (2.0 * PI) / 4.5
                if (d < 0.5) {
                    (-(2.0.pow(20.0 * d - 10.0) * sin((20.0 * d - 11.125) * c5)) * 0.5).toFloat()
                } else {
                    ((2.0.pow(-20.0 * d + 10.0) * sin((20.0 * d - 11.125) * c5)) * 0.5 + 1.0).toFloat()
                }
            }

            BOUNCE_IN -> (1.0 - bounceOutDouble(1.0 - d)).toFloat()
            BOUNCE_OUT -> bounceOutDouble(d).toFloat()
            BOUNCE_IN_OUT -> if (d < 0.5) {
                ((1.0 - bounceOutDouble(1.0 - 2.0 * d)) * 0.5).toFloat()
            } else {
                ((1.0 + bounceOutDouble(2.0 * d - 1.0)) * 0.5).toFloat()
            }
        }
    }

    private fun bounceOutDouble(x: Double): Double {
        val n1 = 7.5625
        val d1 = 2.75
        return when {
            x < 1.0 / d1 -> n1 * x * x
            x < 2.0 / d1 -> {
                val u = x - 1.5 / d1
                n1 * u * u + 0.75
            }
            x < 2.5 / d1 -> {
                val u = x - 2.25 / d1
                n1 * u * u + 0.9375
            }
            else -> {
                val u = x - 2.625 / d1
                n1 * u * u + 0.984375
            }
        }
    }
}
