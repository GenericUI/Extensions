package com.nexustools.genericui.ext.threejs.materials

import com.nexustools.genericui.ext.threejs.math.Color
import com.nexustools.genericui.ext.threejs.textures.Texture

/**
 * Diffuse Gouraud/Lambertian lit material (`THREE.MeshLambertMaterial`).
 */
open class MeshLambertMaterial(
    val color: Color = Color(0xffffff),
    val emissive: Color = Color(0x000000),
    var emissiveIntensity: Float = 1f,
    var map: Texture? = null
) : Material() {
    override val type: String = "MeshLambertMaterial"
}
