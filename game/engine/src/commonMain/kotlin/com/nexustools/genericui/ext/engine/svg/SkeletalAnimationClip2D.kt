package com.nexustools.genericui.ext.engine.svg

import com.nexustools.genericui.ext.engine.animation.AnimationWrapMode

/**
 * Named 2D skeletal animation clip driving a collection of [BonePoseTrack2D] channels.
 */
class SkeletalAnimationClip2D(
    val name: String,
    durationSeconds: Float = 1f,
    val wrapMode: AnimationWrapMode = AnimationWrapMode.LOOP,
    val tracks: List<BonePoseTrack2D> = emptyList()
) {
    val durationSeconds: Float = durationSeconds.coerceAtLeast(0.001f)
}
