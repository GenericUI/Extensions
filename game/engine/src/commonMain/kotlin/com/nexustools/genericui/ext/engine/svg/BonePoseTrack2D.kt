package com.nexustools.genericui.ext.engine.svg

import com.nexustools.genericui.ext.engine.animation.AnimationCurve
import com.nexustools.genericui.ext.engine.animation.EasingFunction

/**
 * Single bone rotation track within a [SkeletalAnimationClip2D], supporting both timed keyframes
 * interpolated via an [AnimationCurve] AND direct normalized `0.0f..1.0f` progress lambda curves.
 */
class BonePoseTrack2D(
    val boneName: String,
    val timesNormalized: FloatArray = FloatArray(0),
    val anglesRad: FloatArray = FloatArray(0),
    val curve: AnimationCurve = EasingFunction.LINEAR,
    val lambdaAngleEvaluator: ((normalizedProgress: Float) -> Float)? = null
) {
    fun sampleAngleRad(normalizedProgress: Float): Float {
        val p = normalizedProgress.coerceIn(0f, 1f)
        val lambda = lambdaAngleEvaluator
        if (lambda != null) {
            return lambda(curve.sample(p))
        }
        if (timesNormalized.isEmpty() || anglesRad.isEmpty()) return 0f
        if (p <= timesNormalized.first() || timesNormalized.size == 1) return anglesRad.first()
        if (p >= timesNormalized.last()) return anglesRad.last()

        var idx = 0
        while (idx + 1 < timesNormalized.size && timesNormalized[idx + 1] < p) {
            idx++
        }
        val t0 = timesNormalized[idx]
        val t1 = timesNormalized[(idx + 1).coerceAtMost(timesNormalized.lastIndex)]
        val span = t1 - t0
        val localAlpha = if (span > 1e-6f) ((p - t0) / span).coerceIn(0f, 1f) else 0f
        val shaped = curve.sample(localAlpha)
        val a0 = anglesRad[idx]
        val a1 = anglesRad[(idx + 1).coerceAtMost(anglesRad.lastIndex)]
        return a0 + (a1 - a0) * shaped
    }

    companion object {
        fun fromLambda(
            boneName: String,
            curve: AnimationCurve = EasingFunction.LINEAR,
            evaluator: (progress: Float) -> Float
        ): BonePoseTrack2D = BonePoseTrack2D(
            boneName = boneName,
            curve = curve,
            lambdaAngleEvaluator = evaluator
        )
    }
}
