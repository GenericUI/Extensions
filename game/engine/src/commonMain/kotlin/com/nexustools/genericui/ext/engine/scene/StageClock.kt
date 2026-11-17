package com.nexustools.genericui.ext.engine.scene

/**
 * Tracks simulation time, fixed-step cadence, frame counters, and time scaling for a [GameScene].
 */
class StageClock(
    fixedStepSeconds: Float = 1f / 60f
) {
    var totalElapsedSeconds: Double = 0.0
        private set

    var deltaSeconds: Float = 0f
        private set

    var fixedStepSeconds: Float = fixedStepSeconds.coerceAtLeast(0.0001f)
        internal set

    var tickCount: Long = 0L
        private set

    var fixedStepCount: Long = 0L
        private set

    var frameCount: Long = 0L
        private set

    var timeScale: Float = 1f
        set(value) {
            field = value.coerceAtLeast(0f)
        }

    var paused: Boolean = false

    internal fun advanceTick(scaledDeltaSeconds: Float, fixedStep: Float) {
        this.deltaSeconds = scaledDeltaSeconds
        this.fixedStepSeconds = fixedStep
        this.totalElapsedSeconds += scaledDeltaSeconds.toDouble()
        this.tickCount++
    }

    internal fun incrementFixedStep() {
        fixedStepCount++
    }

    internal fun recordRenderFrame(frameNumber: Long) {
        frameCount = frameNumber
    }

    fun reset() {
        totalElapsedSeconds = 0.0
        deltaSeconds = 0f
        tickCount = 0L
        fixedStepCount = 0L
        frameCount = 0L
    }
}
