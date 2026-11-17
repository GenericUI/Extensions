package com.nexustools.genericui.ext.threejs.materials

import com.nexustools.genericui.ext.threejs.math.Color
import com.nexustools.genericui.ext.threejs.textures.Texture

/**
 * Blinn-Phong specular lit material (`THREE.MeshPhongMaterial`).
 */
open class MeshPhongMaterial(
    val color: Color = Color(0xffffff),
    val specular: Color = Color(0x111111),
    var shininess: Float = 30f,
    val emissive: Color = Color(0x000000),
    var emissiveIntensity: Float = 1f,
    var map: Texture? = null,
    var normalMap: Texture? = null
) : Material() {
    override val type: String = "MeshPhongMaterial"
}
