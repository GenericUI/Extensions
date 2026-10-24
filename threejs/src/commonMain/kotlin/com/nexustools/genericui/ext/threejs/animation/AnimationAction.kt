package com.nexustools.genericui.ext.threejs.animation

import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.math.Quaternion

/**
 * Scheduled playback controller for an [AnimationClip] on an [AnimationMixer] (`THREE.AnimationAction`).
 */
class AnimationAction(
    val mixer: AnimationMixer,
    val clip: AnimationClip,
    val localRoot: Object3D = mixer.root
) {
    var time: Float = 0f
    var timeScale: Float = 1f
    var weight: Float = 1f
    var loop: Boolean = true
    var paused: Boolean = false
    var enabled: Boolean = true
    private var running: Boolean = false

    fun play(): AnimationAction {
        running = true
        return this
    }

    fun stop(): AnimationAction {
        running = false
        time = 0f
        return this
    }

    fun reset(): AnimationAction {
        paused = false
        time = 0f
        return this
    }

    fun isRunning(): Boolean = running && !paused && enabled && timeScale != 0f

    internal fun update(deltaSeconds: Float) {
        if (!isRunning()) return
        time += deltaSeconds * timeScale
        if (clip.duration > 0f) {
            if (loop) {
                time %= clip.duration
                if (time < 0f) time += clip.duration
            } else if (time >= clip.duration) {
                time = clip.duration
                running = false
            }
        }
        applyTracksAt(time)
    }

    private fun applyTracksAt(sampleTime: Float) {
        for (track in clip.tracks) {
            val values = track.evaluateAt(sampleTime)
            when {
                track.name.endsWith(".position") && values.size >= 3 -> {
                    localRoot.position.set(
                        localRoot.position.x * (1f - weight) + values[0] * weight,
                        localRoot.position.y * (1f - weight) + values[1] * weight,
                        localRoot.position.z * (1f - weight) + values[2] * weight
                    )
                }
                track.name.endsWith(".scale") && values.size >= 3 -> {
                    localRoot.scale.set(
                        localRoot.scale.x * (1f - weight) + values[0] * weight,
                        localRoot.scale.y * (1f - weight) + values[1] * weight,
                        localRoot.scale.z * (1f - weight) + values[2] * weight
                    )
                }
                track.name.endsWith(".quaternion") && values.size >= 4 -> {
                    val targetQ = Quaternion(values[0], values[1], values[2], values[3]).normalize()
                    localRoot.quaternion.slerp(targetQ, weight)
                    localRoot.syncEulerFromQuaternion()
                }
                track.name.endsWith(".rotation") && values.size >= 3 -> {
                    localRoot.rotation.set(values[0], values[1], values[2])
                    localRoot.syncQuaternionFromEuler()
                }
            }
        }
    }
}
