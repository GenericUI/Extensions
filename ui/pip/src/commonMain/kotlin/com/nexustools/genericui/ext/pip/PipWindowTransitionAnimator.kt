package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.animation.AnimationHandle
import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.style.transition.CssTimingFunction
import kotlin.math.roundToInt

/**
 * Animates a floating PiP window's [Rect] bounds between source target coordinates and
 * its corner-docked coordinates using [Animator].
 */
class PipWindowTransitionAnimator : Disposable {

    override var isDisposed: Boolean = false
        private set

    private var activeHandle: AnimationHandle? = null

    val isAnimating: Boolean
        get() = activeHandle?.isRunning == true

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

        onStep(fromRect)
        activeHandle = Animator.animate(
            durationMs = durationMs,
            timingFunction = CssTimingFunction.LINEAR,
            onUpdate = { progress ->
                if (isDisposed) return@animate
                val p = progress.coerceIn(0f, 1f)
                val inv = 1f - p
                val eased = 1f - (inv * inv * inv)
                val currentRect = if (p >= 1f) {
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
            },
            onComplete = { cancelled ->
                activeHandle = null
                if (!cancelled && !isDisposed) {
                    onStep(toRect)
                    onComplete?.invoke()
                }
            }
        )
    }

    fun cancel() {
        activeHandle?.cancel()
        activeHandle = null
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        cancel()
    }

    private fun lerpInt(start: Int, end: Int, t: Float): Int =
        (start + (end - start) * t).roundToInt()

    companion object {
        const val DEFAULT_DURATION_MS: Int = 180
        const val FRAME_INTERVAL_MS: Int = 16
    }
}
