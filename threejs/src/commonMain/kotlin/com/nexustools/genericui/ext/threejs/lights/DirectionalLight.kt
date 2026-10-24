package com.nexustools.genericui.ext.threejs.lights

import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.math.Color

/**
 * Parallel-ray directional light (`THREE.DirectionalLight`) such as sunlight.
 */
class DirectionalLight(
    color: Color = Color(0xffffff),
    intensity: Float = 1f
) : Light(color, intensity) {
    override val type: String = "DirectionalLight"

    var target: Object3D = Object3D()

    init {
        position.set(0f, 1f, 0f)
        updateMatrix()
    }
}
