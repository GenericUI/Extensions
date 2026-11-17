package com.nexustools.genericui.ext.threejs.materials

import com.nexustools.genericui.ext.threejs.math.Color
import com.nexustools.genericui.ext.threejs.textures.Texture

/**
 * Material for rendering point clouds and particle systems (`THREE.PointsMaterial`).
 */
class PointsMaterial(
    val color: Color = Color(0xffffff),
    var size: Float = 1f,
    var sizeAttenuation: Boolean = true,
    var map: Texture? = null
) : Material() {
    override val type: String = "PointsMaterial"
}
