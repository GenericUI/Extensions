package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.geometry.Rect
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * Animates a floating PiP window's [Rect] bounds between source target coordinates and
 * its corner-docked coordinates using [UiTimerScheduler].
 */
class PipWindowTransitionAnimator : Disposable {

    override var isDisposed: Boolean = false
        private set

    private var activeTimerId: Int = -1
    private var animationGeneration: Long = 0L

    val isAnimating: Boolean
        get() = activeTimerId > 0

    fun animateBounds(
        fromRect: Rect,
        toRect: Rect,
        durationMs: Int = DEFAULT_DURATION_MS,
        immediate: Boolean = false,
        onStep: (Rect) -> Unit,
        onComplete: (() -> Unit)? = null
    ) {
        cancel()
        if (isDisposed) return

        if (immediate || durationMs <= 0 || fromRect == toRect) {
            onStep(toRect)
            onComplete?.invoke()
            return
        }

        val token = animationGeneration
        val startMark = TimeSource.Monotonic.markNow()
        var tickCount = 0

        onStep(fromRect)
        activeTimerId = UiTimerScheduler.setInterval(FRAME_INTERVAL_MS) {
            if (isDisposed || token != animationGeneration) {
                clearActiveTimer()
                return@setInterval
            }
            tickCount += 1
            val wallElapsedMs = startMark.elapsedNow().inWholeMilliseconds.toFloat()
            val tickElapsedMs = tickCount * FRAME_INTERVAL_MS.toFloat()
            val elapsedMs = maxOf(wallElapsedMs, tickElapsedMs)
            val progress = (elapsedMs / durationMs.toFloat()).coerceIn(0f, 1f)
            val inv = 1f - progress
            val eased = 1f - (inv * inv * inv)

            val currentRect = if (progress >= 1f) {
                toRect
            } else {
                Rect(
                    x = lerpInt(fromRect.x, toRect.x, eased),
                    y = lerpInt(fromRect.y, toRect.y, eased),
                    width = lerpInt(fromRect.width, toRect.width, eased).coerceAtLeast(1),
                    height = lerpInt(fromRect.height, toRect.height, eased).coerceAtLeast(1)
                )
            }
            onStep(currentRect)

            if (progress >= 1f) {
                clearActiveTimer()
                onComplete?.invoke()
            }
        }
    }

    fun cancel() {
        animationGeneration += 1L
        clearActiveTimer()
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        cancel()
    }

    private fun clearActiveTimer() {
        val id = activeTimerId
        activeTimerId = -1
        if (id > 0) {
            UiTimerScheduler.clearTimer(id)
        }
    }

    private fun lerpInt(start: Int, end: Int, t: Float): Int =
        (start + (end - start) * t).roundToInt()

    companion object {
        const val DEFAULT_DURATION_MS: Int = 180
        const val FRAME_INTERVAL_MS: Int = 16
    }
}
