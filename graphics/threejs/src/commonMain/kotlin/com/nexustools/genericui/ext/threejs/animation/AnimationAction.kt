package com.nexustools.genericui.ext.threejs.animation

import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.math.Quaternion

/**
 * Scheduled playback controller for an [AnimationClip] on an [AnimationMixer] (`THREE.AnimationAction`),
 * resolving track targets by node or bone name in [localRoot]'s hierarchy.
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
        var anyUpdated = false
        for (track in clip.tracks) {
            val dotIdx = track.name.lastIndexOf('.')
            val targetKey = if (dotIdx > 0) track.name.substring(0, dotIdx) else ""
            val propName = if (dotIdx >= 0) track.name.substring(dotIdx + 1) else track.name
            val targetObj = if (targetKey.isEmpty() || targetKey == "root") {
                localRoot
            } else {
                localRoot.getObjectByName(targetKey) ?: localRoot
            }

            val values = track.evaluateAt(sampleTime)
            when {
                (propName == "position" || propName == "translation") && values.size >= 3 -> {
                    targetObj.position.set(
                        targetObj.position.x * (1f - weight) + values[0] * weight,
                        targetObj.position.y * (1f - weight) + values[1] * weight,
                        targetObj.position.z * (1f - weight) + values[2] * weight
                    )
                    anyUpdated = true
                }
                propName == "scale" && values.size >= 3 -> {
                    targetObj.scale.set(
                        targetObj.scale.x * (1f - weight) + values[0] * weight,
                        targetObj.scale.y * (1f - weight) + values[1] * weight,
                        targetObj.scale.z * (1f - weight) + values[2] * weight
                    )
                    anyUpdated = true
                }
                propName == "quaternion" && values.size >= 4 -> {
                    val targetQ = Quaternion(values[0], values[1], values[2], values[3]).normalize()
                    targetObj.quaternion.slerp(targetQ, weight)
                    targetObj.syncEulerFromQuaternion()
                    anyUpdated = true
                }
                propName == "rotation" && values.size >= 4 -> {
                    val targetQ = Quaternion(values[0], values[1], values[2], values[3]).normalize()
                    targetObj.quaternion.slerp(targetQ, weight)
                    targetObj.syncEulerFromQuaternion()
                    anyUpdated = true
                }
                propName == "rotation" && values.size >= 3 -> {
                    targetObj.rotation.set(values[0], values[1], values[2])
                    targetObj.syncQuaternionFromEuler()
                    anyUpdated = true
                }
            }
        }
        if (anyUpdated) {
            localRoot.updateMatrixWorld(true)
        }
    }
}
