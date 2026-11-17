package com.nexustools.genericui.ext.threejs.lights

import com.nexustools.genericui.ext.threejs.math.Color

/**
 * Omnidirectional point light source (`THREE.PointLight`) with distance attenuation.
 */
class PointLight(
    color: Color = Color(0xffffff),
    intensity: Float = 1f,
    var distance: Float = 0f,
    var decay: Float = 2f
) : Light(color, intensity) {
    override val type: String = "PointLight"
}
