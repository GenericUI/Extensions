package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.ext.threejs.lights.DirectionalLight
import com.nexustools.genericui.ext.threejs.math.Color

/**
 * Parallel-ray directional light module bound to a [SceneEntity] transform.
 */
class DirectionalLight3DModule(
    color: Color = Color(0xffffff),
    intensity: Float = 1f
) : Node3DBindingModule(object3D = DirectionalLight(color = color, intensity = intensity)) {

    val light: DirectionalLight
        get() = object3D as DirectionalLight
}
