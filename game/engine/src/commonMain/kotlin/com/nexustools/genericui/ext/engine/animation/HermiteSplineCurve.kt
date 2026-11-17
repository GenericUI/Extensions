package com.nexustools.genericui.ext.engine.animation

/**
 * Piecewise cubic Hermite / Catmull-Rom spline curve evaluated over [keyframes].
 */
class HermiteSplineCurve(
    keyframes: List<HermiteKeyframe>,
    val useCatmullRomAutoTangents: Boolean = false
) : AnimationCurve {
    val keyframes: List<HermiteKeyframe> = keyframes.sortedBy { it.time }

    override fun sample(rawProgress: Float): Float {
        if (this.keyframes.isEmpty()) return rawProgress.coerceIn(0f, 1f)
        if (this.keyframes.size == 1) return this.keyframes[0].value

        val t = rawProgress.coerceIn(this.keyframes.first().time, this.keyframes.last().time)
        if (t <= this.keyframes.first().time) return this.keyframes.first().value
        if (t >= this.keyframes.last().time) return this.keyframes.last().value

        var idx = 0
        while (idx + 1 < this.keyframes.size && this.keyframes[idx + 1].time < t) {
            idx++
        }
        val k0 = this.keyframes[idx]
        val k1 = this.keyframes[(idx + 1).coerceAtMost(this.keyframes.lastIndex)]
        val dt = k1.time - k0.time
        if (dt <= 1e-6f) return k1.value

        val u = ((t - k0.time) / dt).coerceIn(0f, 1f)
        val u2 = u * u
        val u3 = u2 * u

        val m0: Float
        val m1: Float
        if (useCatmullRomAutoTangents) {
            val prev = this.keyframes[(idx - 1).coerceAtLeast(0)]
            val next = this.keyframes[(idx + 2).coerceAtMost(this.keyframes.lastIndex)]
            val span0 = (k1.time - prev.time).coerceAtLeast(1e-5f)
            val span1 = (next.time - k0.time).coerceAtLeast(1e-5f)
            m0 = (k1.value - prev.value) / span0 * dt
            m1 = (next.value - k0.value) / span1 * dt
        } else {
            m0 = k0.outTangent * dt
            m1 = k1.inTangent * dt
        }

        val h00 = 2f * u3 - 3f * u2 + 1f
        val h10 = u3 - 2f * u2 + u
        val h01 = -2f * u3 + 3f * u2
        val h11 = u3 - u2
        return h00 * k0.value + h10 * m0 + h01 * k1.value + h11 * m1
    }
}
