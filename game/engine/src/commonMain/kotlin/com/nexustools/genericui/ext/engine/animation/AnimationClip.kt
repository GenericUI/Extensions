package com.nexustools.genericui.ext.engine.animation

import kotlin.math.floor

/**
 * Base VBlank-evaluated animation clip providing drift-free timestamp progress computation,
 * wrap-mode handling, and pause/resume tracking.
 */
abstract class AnimationClip(
    durationSeconds: Float,
    val curve: AnimationCurve = EasingFunction.LINEAR,
    val wrapMode: AnimationWrapMode = AnimationWrapMode.ONCE,
    val onComplete: (() -> Unit)? = null
) {
    val durationSeconds: Float = durationSeconds.coerceAtLeast(0.0001f)

    var startTimestampMs: Double? = null
        internal set

    var isPaused: Boolean = false
        private set

    var isFinished: Boolean = false
        protected set

    var lastRawProgress: Float = 0f
        private set

    var lastShapedProgress: Float = 0f
        private set

    private var pausedAtTimestampMs: Double? = null
    private var totalPausedMs: Double = 0.0

    fun pause(currentTimestampMs: Double? = null) {
        if (isPaused || isFinished) return
        isPaused = true
        pausedAtTimestampMs = currentTimestampMs
    }

    fun resume(currentTimestampMs: Double? = null) {
        if (!isPaused) return
        val pausedAt = pausedAtTimestampMs
        if (pausedAt != null && currentTimestampMs != null && currentTimestampMs > pausedAt) {
            totalPausedMs += (currentTimestampMs - pausedAt)
        }
        pausedAtTimestampMs = null
        isPaused = false
    }

    fun resetPlayback(newStartTimestampMs: Double? = null) {
        startTimestampMs = newStartTimestampMs
        pausedAtTimestampMs = null
        totalPausedMs = 0.0
        isPaused = false
        isFinished = false
        lastRawProgress = 0f
        lastShapedProgress = 0f
    }

    /**
     * Evaluates this clip directly against the hardware VBlank [vblankTimestampMs] with zero
     * accumulated frame-delta drift.
     */
    fun evaluateAtVBlank(vblankTimestampMs: Double) {
        if (isPaused || isFinished) return
        val clipStartMs = startTimestampMs ?: vblankTimestampMs.also { startTimestampMs = it }
        val durationMs = durationSeconds.toDouble() * 1000.0
        val elapsedMs = (vblankTimestampMs - clipStartMs - totalPausedMs).coerceAtLeast(0.0)

        var completedNow = false
        val rawProgress: Float = when (wrapMode) {
            AnimationWrapMode.ONCE -> {
                if (elapsedMs >= durationMs) {
                    completedNow = true
                }
                ((vblankTimestampMs - clipStartMs - totalPausedMs) / durationMs).coerceIn(0.0, 1.0).toFloat()
            }
            AnimationWrapMode.CLAMP_FOREVER -> {
                ((vblankTimestampMs - clipStartMs - totalPausedMs) / durationMs).coerceIn(0.0, 1.0).toFloat()
            }
            AnimationWrapMode.LOOP -> {
                val cycles = elapsedMs / durationMs
                (cycles - floor(cycles)).toFloat().coerceIn(0f, 1f)
            }
            AnimationWrapMode.PING_PONG -> {
                val cycles = elapsedMs / durationMs
                val cycleIndex = floor(cycles).toLong()
                val frac = (cycles - cycleIndex).toFloat().coerceIn(0f, 1f)
                if ((cycleIndex and 1L) == 1L) 1f - frac else frac
            }
        }

        val shaped = curve.sample(rawProgress)
        lastRawProgress = rawProgress
        lastShapedProgress = shaped
        applyShapedProgress(shaped, rawProgress)

        if (completedNow) {
            isFinished = true
            onComplete?.invoke()
        }
    }

    protected abstract fun applyShapedProgress(shapedProgress: Float, rawProgress: Float)
}
