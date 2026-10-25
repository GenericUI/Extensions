package com.nexustools.genericui.component.media

import kotlin.time.TimeSource

/**
 * High-resolution monotonic frame timer used by [AbstractCanvas] (`Canvas2D`, `GLCanvas`, `VKCanvas`)
 * and `VBlankWatcher` to compute exact `deltaMs` and `totalElapsedMs` between VBlank render callbacks.
 *
 * When the platform supplies an explicit hardware/DOM VBlank timestamp (such as `DOMHighResTimeStamp`
 * from `requestAnimationFrame`, `CADisplayLink.timestamp`, or `Choreographer.FrameCallback` nanos),
 * [VBlankTimer] uses that timestamp; otherwise it falls back to [TimeSource.Monotonic].
 */
class VBlankTimer(
    var maxDeltaMsClamp: Double = 250.0
) {
    private val timeMark = TimeSource.Monotonic.markNow()
    private var startTimestampMs: Double? = null
    private var lastTimestampMs: Double? = null

    var frameNumber: Long = 0L
        private set

    var lastDeltaMs: Double = 0.0
        private set

    var totalElapsedMs: Double = 0.0
        private set

    var smoothedFps: Double = 60.0
        private set

    fun reset() {
        startTimestampMs = null
        lastTimestampMs = null
        frameNumber = 0L
        lastDeltaMs = 0.0
        totalElapsedMs = 0.0
    }

    /**
     * Advances the timer by one frame and returns `Pair(deltaMs, totalElapsedMs)`.
     */
    fun stepFrame(explicitTimestampMs: Double? = null): Pair<Double, Double> {
        val nowMs = explicitTimestampMs ?: timeMark.elapsedNow().inWholeNanoseconds / 1_000_000.0
        val start = startTimestampMs
        val prev = lastTimestampMs

        if (start == null || prev == null) {
            startTimestampMs = nowMs
            lastTimestampMs = nowMs
            lastDeltaMs = 0.0
            totalElapsedMs = 0.0
            frameNumber = 0L
            return 0.0 to 0.0
        }

        val rawDelta = (nowMs - prev).coerceAtLeast(0.0)
        val clampedDelta = if (maxDeltaMsClamp > 0.0) rawDelta.coerceAtMost(maxDeltaMsClamp) else rawDelta
        val elapsed = (nowMs - start).coerceAtLeast(0.0)

        lastTimestampMs = nowMs
        lastDeltaMs = clampedDelta
        totalElapsedMs = elapsed
        frameNumber += 1L

        if (clampedDelta > 0.001) {
            val instantFps = 1000.0 / clampedDelta
            smoothedFps = smoothedFps * 0.9 + instantFps * 0.1
        }

        return clampedDelta to elapsed
    }
}
