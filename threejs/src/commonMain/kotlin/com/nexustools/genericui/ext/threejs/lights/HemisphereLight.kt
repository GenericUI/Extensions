package com.nexustools.genericui.ext.threejs.lights

import com.nexustools.genericui.ext.threejs.math.Color

/**
 * Sky-to-ground gradient hemisphere light (`THREE.HemisphereLight`).
 */
class HemisphereLight(
    skyColor: Color = Color(0xffffff),
    val groundColor: Color = Color(0x444444),
    intensity: Float = 1f
) : Light(skyColor, intensity) {
    override val type: String = "HemisphereLight"

    init {
        position.set(0f, 1f, 0f)
        updateMatrix()
    }
}
