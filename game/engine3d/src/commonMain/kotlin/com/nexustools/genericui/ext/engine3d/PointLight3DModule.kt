package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.ext.threejs.lights.PointLight
import com.nexustools.genericui.ext.threejs.math.Color

/**
 * Omnidirectional attenuated point light module bound to a [SceneEntity] transform.
 */
class PointLight3DModule(
    color: Color = Color(0xffffff),
    intensity: Float = 1f,
    distance: Float = 0f,
    decay: Float = 2f
) : Node3DBindingModule(
    object3D = PointLight(
        color = color,
        intensity = intensity,
        distance = distance,
        decay = decay
    )
) {
    val light: PointLight
        get() = object3D as PointLight
}
