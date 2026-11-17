package com.nexustools.genericui.ext.threejs.lights

import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.math.Color

/**
 * Abstract base class for scene light sources (`THREE.Light`).
 */
open class Light(
    val color: Color = Color(0xffffff),
    var intensity: Float = 1f
) : Object3D() {
    override val type: String = "Light"
}
