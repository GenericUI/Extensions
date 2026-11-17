package com.nexustools.genericui.ext.engine.animation

import com.nexustools.genericui.ext.engine.math.Vec2f
import com.nexustools.genericui.ext.engine.math.Vec3f
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Typed VBlank-synchronized property interpolation clip between [startValue] and [endValue].
 */
class PropertyTweenAnimation<T>(
    durationSeconds: Float,
    val startValue: T,
    val endValue: T,
    val interpolator: (start: T, end: T, progress: Float) -> T,
    curve: AnimationCurve = EasingFunction.LINEAR,
    wrapMode: AnimationWrapMode = AnimationWrapMode.ONCE,
    onComplete: (() -> Unit)? = null,
    val setter: (T) -> Unit
) : AnimationClip(
    durationSeconds = durationSeconds,
    curve = curve,
    wrapMode = wrapMode,
    onComplete = onComplete
) {
    override fun applyShapedProgress(shapedProgress: Float, rawProgress: Float) {
        setter(interpolator(startValue, endValue, shapedProgress))
    }

    companion object {
        fun ofFloat(
            durationSeconds: Float,
            start: Float,
            end: Float,
            curve: AnimationCurve = EasingFunction.LINEAR,
            wrapMode: AnimationWrapMode = AnimationWrapMode.ONCE,
            onComplete: (() -> Unit)? = null,
            setter: (Float) -> Unit
        ): PropertyTweenAnimation<Float> = PropertyTweenAnimation(
            durationSeconds = durationSeconds,
            startValue = start,
            endValue = end,
            interpolator = { a, b, t -> a + (b - a) * t },
            curve = curve,
            wrapMode = wrapMode,
            onComplete = onComplete,
            setter = setter
        )

        fun ofVec2f(
            durationSeconds: Float,
            start: Vec2f,
            end: Vec2f,
            curve: AnimationCurve = EasingFunction.LINEAR,
            wrapMode: AnimationWrapMode = AnimationWrapMode.ONCE,
            onComplete: (() -> Unit)? = null,
            setter: (Vec2f) -> Unit
        ): PropertyTweenAnimation<Vec2f> = PropertyTweenAnimation(
            durationSeconds = durationSeconds,
            startValue = start.copy(),
            endValue = end.copy(),
            interpolator = { a, b, t -> a.lerp(b, t) },
            curve = curve,
            wrapMode = wrapMode,
            onComplete = onComplete,
            setter = setter
        )

        fun ofVec3f(
            durationSeconds: Float,
            start: Vec3f,
            end: Vec3f,
            curve: AnimationCurve = EasingFunction.LINEAR,
            wrapMode: AnimationWrapMode = AnimationWrapMode.ONCE,
            onComplete: (() -> Unit)? = null,
            setter: (Vec3f) -> Unit
        ): PropertyTweenAnimation<Vec3f> = PropertyTweenAnimation(
            durationSeconds = durationSeconds,
            startValue = start.copy(),
            endValue = end.copy(),
            interpolator = { a, b, t -> a.lerp(b, t) },
            curve = curve,
            wrapMode = wrapMode,
            onComplete = onComplete,
            setter = setter
        )

        fun ofRgbaColor(
            durationSeconds: Float,
            start: RgbaColor,
            end: RgbaColor,
            curve: AnimationCurve = EasingFunction.LINEAR,
            wrapMode: AnimationWrapMode = AnimationWrapMode.ONCE,
            onComplete: (() -> Unit)? = null,
            setter: (RgbaColor) -> Unit
        ): PropertyTweenAnimation<RgbaColor> = PropertyTweenAnimation(
            durationSeconds = durationSeconds,
            startValue = start,
            endValue = end,
            interpolator = { a, b, t ->
                val clamped = t.coerceIn(0f, 1f)
                RgbaColor(
                    r = (a.r + (b.r - a.r) * clamped + 0.5f).toInt().coerceIn(0, 255),
                    g = (a.g + (b.g - a.g) * clamped + 0.5f).toInt().coerceIn(0, 255),
                    b = (a.b + (b.b - a.b) * clamped + 0.5f).toInt().coerceIn(0, 255),
                    a = (a.a + (b.a - a.a) * clamped + 0.5f).toInt().coerceIn(0, 255)
                )
            },
            curve = curve,
            wrapMode = wrapMode,
            onComplete = onComplete,
            setter = setter
        )
    }
}
