package com.nexustools.genericui.ext.threejs.lights

import com.nexustools.genericui.ext.threejs.math.Color

/**
 * Omnidirectional ambient scene light (`THREE.AmbientLight`).
 */
class AmbientLight(
    color: Color = Color(0xffffff),
    intensity: Float = 1f
) : Light(color, intensity) {
    override val type: String = "AmbientLight"
}
