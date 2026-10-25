package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.transition.CssTimingFunction
import com.nexustools.genericui.style.transition.CssTransform2D
import com.nexustools.genericui.style.transition.CssTransitionSpec
import kotlin.math.roundToInt

/**
 * Per-node CSS transition and keyframe animation engine for [AlienWidgetNode].
 *
 * Supports:
 * - Default Bootstrap 5.3 `.15s ease-in-out` interactive transitions on `background-color`, `border-color`,
 *   `color`, `box-shadow`, and `opacity` when hovering, focusing, or pressing controls.
 * - Custom `transition:` specs (`all`, `background-color`, `color`, `border-color`, `border-radius`,
 *   `opacity`, `transform`, `width`, `height`, `left`, `top`) with cubic-Bezier timing curves.
 * - Continuous `@keyframes` animations (`spin`, `pulse`, `fade`) for `Spinner`, `ProgressBar` animated stripes,
 *   and custom animated elements.
 */
class AlienTransitionAnimator {

    private class ActivePropertyTransition(
        val property: String,
        val durationMs: Float,
        val delayMs: Float,
        val timing: CssTimingFunction,
        var elapsedMs: Float = 0f,
        val onComplete: (() -> Unit)? = null
    ) {
        val isComplete: Boolean
            get() = elapsedMs >= delayMs + durationMs

        fun progress(): Float {
            if (elapsedMs <= delayMs) return 0f
            val activeElapsed = (elapsedMs - delayMs).coerceIn(0f, durationMs)
            val rawT = if (durationMs <= 0f) 1f else (activeElapsed / durationMs).coerceIn(0f, 1f)
            return timing.evaluate(rawT)
        }
    }

    private var configuredSpecs: List<CssTransitionSpec> = emptyList()
    private val activeTransitions = linkedMapOf<String, ActivePropertyTransition>()

    private var previousTargetStyle: AlienResolvedVisualStyle? = null
    private var startStyleSnapshot: AlienResolvedVisualStyle? = null
    private var currentInterpolatedStyle: AlienResolvedVisualStyle? = null

    var continuousAnimationPhaseRad: Float = 0f
        private set

    val isAnimating: Boolean
        get() = activeTransitions.isNotEmpty()

    fun configureTransitions(specs: List<CssTransitionSpec>) {
        configuredSpecs = specs
    }

    fun startTransition(
        property: String,
        spec: CssTransitionSpec,
        onComplete: (() -> Unit)? = null
    ) {
        val norm = property.trim().lowercase()
        if (currentInterpolatedStyle != null) {
            startStyleSnapshot = currentInterpolatedStyle
        }
        activeTransitions[norm] = ActivePropertyTransition(
            property = norm,
            durationMs = spec.durationMs.coerceAtLeast(1f),
            delayMs = spec.delayMs.coerceAtLeast(0f),
            timing = spec.timingFunction,
            elapsedMs = 0f,
            onComplete = onComplete
        )
    }

    /**
     * Evaluates or initiates smooth transitions toward [targetStyle] and returns the effective
     * [AlienResolvedVisualStyle] for the current frame.
     */
    fun resolveVisualStyleForFrame(
        targetStyle: AlienResolvedVisualStyle,
        enableDefaultBootstrapHoverTransition: Boolean = true
    ): AlienResolvedVisualStyle {
        val prev = previousTargetStyle
        if (prev == null) {
            previousTargetStyle = targetStyle
            startStyleSnapshot = targetStyle
            currentInterpolatedStyle = targetStyle
            return targetStyle
        }

        if (prev != targetStyle) {
            val fromSnap = currentInterpolatedStyle ?: prev
            startStyleSnapshot = fromSnap
            previousTargetStyle = targetStyle

            val defaultSpec = if (enableDefaultBootstrapHoverTransition) {
                CssTransitionSpec(
                    property = "all",
                    durationMs = 150f,
                    timingFunction = CssTimingFunction.EASE_IN_OUT,
                    delayMs = 0f
                )
            } else {
                null
            }

            if (fromSnap.backgroundColor != targetStyle.backgroundColor) {
                findSpec("background-color", defaultSpec)?.let { startTransition("background-color", it) }
            }
            if (fromSnap.textColor != targetStyle.textColor) {
                findSpec("color", defaultSpec)?.let { startTransition("color", it) }
            }
            if (fromSnap.borderColor != targetStyle.borderColor) {
                findSpec("border-color", defaultSpec)?.let { startTransition("border-color", it) }
            }
            if (fromSnap.borderRadiusPx != targetStyle.borderRadiusPx) {
                findSpec("border-radius", null)?.let { startTransition("border-radius", it) }
            }
            if (fromSnap.opacity != targetStyle.opacity) {
                findSpec("opacity", defaultSpec)?.let { startTransition("opacity", it) }
            }
            if (fromSnap.transform2D != targetStyle.transform2D) {
                findSpec("transform", null)?.let { startTransition("transform", it) }
            }
            if (fromSnap.focusRingSpreadPx != targetStyle.focusRingSpreadPx) {
                findSpec("box-shadow", defaultSpec)?.let { startTransition("box-shadow", it) }
            }
        }

        if (activeTransitions.isEmpty()) {
            currentInterpolatedStyle = targetStyle
            return targetStyle
        }

        val start = startStyleSnapshot ?: targetStyle
        val bgT = progressFor("background-color")
        val textT = progressFor("color")
        val borderT = progressFor("border-color")
        val radiusT = progressFor("border-radius")
        val opacityT = progressFor("opacity")
        val transformT = progressFor("transform")
        val shadowT = progressFor("box-shadow")

        val resolved = targetStyle.copy(
            backgroundColor = if (bgT < 1f) lerpColor(start.backgroundColor, targetStyle.backgroundColor, bgT) else targetStyle.backgroundColor,
            textColor = if (textT < 1f) lerpColor(start.textColor, targetStyle.textColor, textT) else targetStyle.textColor,
            borderColor = if (borderT < 1f) lerpColor(start.borderColor, targetStyle.borderColor, borderT) else targetStyle.borderColor,
            borderRadiusPx = if (radiusT < 1f) lerpFloat(start.borderRadiusPx, targetStyle.borderRadiusPx, radiusT) else targetStyle.borderRadiusPx,
            opacity = if (opacityT < 1f) lerpFloat(start.opacity, targetStyle.opacity, opacityT) else targetStyle.opacity,
            transform2D = if (transformT < 1f) start.transform2D.interpolateTo(targetStyle.transform2D, transformT) else targetStyle.transform2D,
            focusRingSpreadPx = if (shadowT < 1f) lerpFloat(start.focusRingSpreadPx, targetStyle.focusRingSpreadPx, shadowT) else targetStyle.focusRingSpreadPx
        )
        currentInterpolatedStyle = resolved
        return resolved
    }

    /**
     * Advances active transitions and continuous animations by [deltaMs] milliseconds.
     * Returns `true` if any visual property changed and requires a dirty region repaint.
     */
    fun step(deltaMs: Float, hasContinuousAnimation: Boolean = false): Boolean {
        val safeDelta = deltaMs.coerceAtLeast(0f)
        var changed = false

        if (hasContinuousAnimation && safeDelta > 0f) {
            continuousAnimationPhaseRad = (continuousAnimationPhaseRad + (safeDelta / 750f) * 6.2831855f) % 6.2831855f
            changed = true
        }

        if (activeTransitions.isEmpty()) return changed

        val completedCallbacks = mutableListOf<() -> Unit>()
        val iterator = activeTransitions.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            entry.elapsedMs += safeDelta
            changed = true
            if (entry.isComplete) {
                entry.onComplete?.let { completedCallbacks.add(it) }
                iterator.remove()
            }
        }

        for (cb in completedCallbacks) {
            cb()
        }
        return changed
    }

    private fun findSpec(property: String, fallback: CssTransitionSpec?): CssTransitionSpec? =
        CssTransitionSpec.findMatching(configuredSpecs, property) ?: fallback

    private fun progressFor(property: String): Float {
        val direct = activeTransitions[property] ?: activeTransitions["all"] ?: return 1f
        return direct.progress()
    }

    companion object {
        fun lerpFloat(a: Float, b: Float, t: Float): Float =
            a + (b - a) * t.coerceIn(0f, 1f)

        fun lerpColor(from: RgbaColor, to: RgbaColor, t: Float): RgbaColor {
            val clamped = t.coerceIn(0f, 1f)
            return RgbaColor(
                r = (from.r + (to.r - from.r) * clamped).roundToInt().coerceIn(0, 255),
                g = (from.g + (to.g - from.g) * clamped).roundToInt().coerceIn(0, 255),
                b = (from.b + (to.b - from.b) * clamped).roundToInt().coerceIn(0, 255),
                a = (from.a + (to.a - from.a) * clamped).roundToInt().coerceIn(0, 255)
            )
        }
    }
}
