package com.nexustools.genericui.ext.threejs.animation

import com.nexustools.genericui.ext.threejs.core.EventDispatcher
import com.nexustools.genericui.ext.threejs.core.Object3D

/**
 * Scene animation player (`THREE.AnimationMixer`) driving one or more [AnimationAction]s.
 */
class AnimationMixer(
    val root: Object3D
) : EventDispatcher() {
    var time: Float = 0f
        private set
    var timeScale: Float = 1f

    private val actions = mutableListOf<AnimationAction>()

    fun clipAction(clip: AnimationClip, optionalRoot: Object3D = root): AnimationAction {
        val existing = actions.firstOrNull { it.clip === clip && it.localRoot === optionalRoot }
        if (existing != null) return existing
        val action = AnimationAction(this, clip, optionalRoot)
        actions.add(action)
        return action
    }

    fun stopAllAction(): AnimationMixer {
        for (action in actions) {
            action.stop()
        }
        return this
    }

    fun update(deltaSeconds: Float): AnimationMixer {
        val scaledDelta = deltaSeconds * timeScale
        time += scaledDelta
        for (action in actions) {
            action.update(scaledDelta)
        }
        return this
    }
}
