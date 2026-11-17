package com.nexustools.genericui.ext.threejs.lights

import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.math.Color
import kotlin.math.PI

/**
 * Cone-shaped spotlight (`THREE.SpotLight`) with inner/outer penumbra falloff.
 */
class SpotLight(
    color: Color = Color(0xffffff),
    intensity: Float = 1f,
    var distance: Float = 0f,
    var angle: Float = (PI / 3.0).toFloat(),
    var penumbra: Float = 0f,
    var decay: Float = 2f
) : Light(color, intensity) {
    override val type: String = "SpotLight"

    var target: Object3D = Object3D()

    init {
        position.set(0f, 1f, 0f)
        updateMatrix()
    }
}
