package com.nexustools.genericui.ext.engine.scene

/**
 * Fixed-timestep accumulator preventing spiral-of-death lag spikes via [maxDeltaSeconds] and
 * [maxFixedStepsPerTick], while computing [interpolationAlpha] for smooth render interpolation.
 */
class FixedStepAccumulator(
    fixedStepSeconds: Float = 1f / 60f,
    maxDeltaSeconds: Float = 0.1f,
    maxFixedStepsPerTick: Int = 8
) {
    var fixedStepSeconds: Float = fixedStepSeconds.coerceAtLeast(0.0001f)
        set(value) {
            field = value.coerceAtLeast(0.0001f)
        }

    var maxDeltaSeconds: Float = maxDeltaSeconds.coerceAtLeast(0.001f)
        set(value) {
            field = value.coerceAtLeast(0.001f)
        }

    var maxFixedStepsPerTick: Int = maxFixedStepsPerTick.coerceAtLeast(1)
        set(value) {
            field = value.coerceAtLeast(1)
        }

    var accumulatedSeconds: Float = 0f
        private set

    val interpolationAlpha: Float
        get() = (accumulatedSeconds / fixedStepSeconds).coerceIn(0f, 1f)

    fun accumulateAndStep(
        rawDeltaSeconds: Float,
        onStep: (fixedDeltaSeconds: Float) -> Unit
    ): Int {
        val clampedDelta = rawDeltaSeconds.coerceIn(0f, maxDeltaSeconds)
        accumulatedSeconds += clampedDelta
        var steps = 0
        while (accumulatedSeconds + 1e-6f >= fixedStepSeconds && steps < maxFixedStepsPerTick) {
            accumulatedSeconds = (accumulatedSeconds - fixedStepSeconds).coerceAtLeast(0f)
            onStep(fixedStepSeconds)
            steps++
        }
        if (steps >= maxFixedStepsPerTick && accumulatedSeconds > fixedStepSeconds) {
            accumulatedSeconds %= fixedStepSeconds
        }
        return steps
    }

    fun reset() {
        accumulatedSeconds = 0f
    }
}
