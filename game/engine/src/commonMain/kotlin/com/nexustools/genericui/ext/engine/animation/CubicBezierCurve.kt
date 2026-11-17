package com.nexustools.genericui.ext.engine.animation

import kotlin.math.abs

/**
 * Analytic CSS-style cubic Bezier animation curve (`(0,0) -> (x1, y1) -> (x2, y2) -> (1,1)`)
 * solved via Newton-Raphson iterations with a binary bisection fallback for steep slopes.
 */
class CubicBezierCurve(
    x1: Float,
    val y1: Float,
    x2: Float,
    val y2: Float
) : AnimationCurve {
    val x1: Float = x1.coerceIn(0f, 1f)
    val x2: Float = x2.coerceIn(0f, 1f)

    override fun sample(rawProgress: Float): Float {
        val targetX = rawProgress.coerceIn(0f, 1f)
        if (targetX <= 0f) return 0f
        if (targetX >= 1f) return 1f
        val t = solveCurveX(targetX.toDouble())
        return sampleCurveY(t).toFloat()
    }

    private fun sampleCurveX(t: Double): Double {
        val inv = 1.0 - t
        return 3.0 * inv * inv * t * x1 + 3.0 * inv * t * t * x2 + t * t * t
    }

    private fun sampleCurveY(t: Double): Double {
        val inv = 1.0 - t
        return 3.0 * inv * inv * t * y1 + 3.0 * inv * t * t * y2 + t * t * t
    }

    private fun sampleDerivativeX(t: Double): Double {
        val inv = 1.0 - t
        return 3.0 * inv * inv * x1 + 6.0 * inv * t * (x2 - x1) + 3.0 * t * t * (1.0 - x2)
    }

    private fun solveCurveX(x: Double): Double {
        var t2 = x
        for (i in 0 until 8) {
            val xErr = sampleCurveX(t2) - x
            if (abs(xErr) < 1e-6) return t2
            val d2 = sampleDerivativeX(t2)
            if (abs(d2) < 1e-6) break
            t2 = (t2 - xErr / d2).coerceIn(0.0, 1.0)
        }
        var lo = 0.0
        var hi = 1.0
        var t = x.coerceIn(0.0, 1.0)
        for (i in 0 until 24) {
            val xEst = sampleCurveX(t)
            if (abs(x - xEst) < 1e-6) return t
            if (x > xEst) lo = t else hi = t
            t = 0.5 * (lo + hi)
        }
        return t
    }

    companion object {
        val EASE: CubicBezierCurve = CubicBezierCurve(0.25f, 0.1f, 0.25f, 1f)
        val EASE_IN: CubicBezierCurve = CubicBezierCurve(0.42f, 0f, 1f, 1f)
        val EASE_OUT: CubicBezierCurve = CubicBezierCurve(0f, 0f, 0.58f, 1f)
        val EASE_IN_OUT: CubicBezierCurve = CubicBezierCurve(0.42f, 0f, 0.58f, 1f)
    }
}
