package com.nexustools.genericui.ext.threejs.scenes

import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.materials.Material
import com.nexustools.genericui.ext.threejs.math.Color
import com.nexustools.genericui.ext.threejs.textures.Texture

/**
 * Root 3D scene graph container (`THREE.Scene`).
 */
class Scene : Object3D() {
    override val type: String = "Scene"

    var background: Color? = Color(0x0f172a)
    var backgroundTexture: Texture? = null
    var environment: Texture? = null
    var fog: Fog? = null
    var overrideMaterial: Material? = null
    var backgroundBlurriness: Float = 0f
    var backgroundIntensity: Float = 1f
    var autoUpdate: Boolean = true
}
