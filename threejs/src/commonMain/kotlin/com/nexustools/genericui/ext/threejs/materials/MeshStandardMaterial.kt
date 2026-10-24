package com.nexustools.genericui.ext.threejs.materials

import com.nexustools.genericui.ext.threejs.math.Color
import com.nexustools.genericui.ext.threejs.textures.Texture

/**
 * Physically Based Rendering (PBR) metallic-roughness material (`THREE.MeshStandardMaterial`).
 */
open class MeshStandardMaterial(
    val color: Color = Color(0xffffff),
    var roughness: Float = 1f,
    var metalness: Float = 0f,
    val emissive: Color = Color(0x000000),
    var emissiveIntensity: Float = 1f,
    var map: Texture? = null,
    var normalMap: Texture? = null,
    var roughnessMap: Texture? = null,
    var metalnessMap: Texture? = null,
    var aoMap: Texture? = null,
    var envMapIntensity: Float = 1f,
    var flatShading: Boolean = false
) : Material() {
    override val type: String = "MeshStandardMaterial"
}
