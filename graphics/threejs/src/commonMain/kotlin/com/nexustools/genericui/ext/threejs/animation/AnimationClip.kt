package com.nexustools.genericui.ext.threejs.animation

import com.nexustools.genericui.ext.threejs.math.MathUtils

/**
 * Reusable set of keyframe tracks representing a named animation sequence (`THREE.AnimationClip`).
 */
class AnimationClip(
    val name: String,
    duration: Float = -1f,
    val tracks: List<KeyframeTrack> = emptyList()
) {
    val uuid: String = MathUtils.generateUUID()
    val duration: Float = if (duration >= 0f) {
        duration
    } else {
        tracks.maxOfOrNull { it.times.maxOrNull() ?: 0f } ?: 0f
    }

    companion object {
        fun findByName(clips: List<AnimationClip>, name: String): AnimationClip? =
            clips.firstOrNull { it.name == name }
    }
}
