package com.nexustools.genericui.ext.engine.animation

/**
 * VBlank-synchronized lambda animation clip delivering `0.0f..1.0f` progress shaped by [curve]
 * on every VBlank frame directly on the rendering thread.
 */
class LambdaAnimationClip(
    durationSeconds: Float,
    curve: AnimationCurve = EasingFunction.LINEAR,
    wrapMode: AnimationWrapMode = AnimationWrapMode.ONCE,
    onComplete: (() -> Unit)? = null,
    val onProgress: (progress: Float) -> Unit
) : AnimationClip(
    durationSeconds = durationSeconds,
    curve = curve,
    wrapMode = wrapMode,
    onComplete = onComplete
) {
    override fun applyShapedProgress(shapedProgress: Float, rawProgress: Float) {
        onProgress(shapedProgress)
    }
}
