package com.nexustools.genericui.ext.threejs.materials

import com.nexustools.genericui.ext.threejs.math.Color

/**
 * Advanced PBR material with clearcoat, transmission, sheen, and IOR (`THREE.MeshPhysicalMaterial`).
 */
class MeshPhysicalMaterial(
    color: Color = Color(0xffffff),
    roughness: Float = 0.5f,
    metalness: Float = 0f,
    var clearcoat: Float = 0f,
    var clearcoatRoughness: Float = 0f,
    var ior: Float = 1.5f,
    var transmission: Float = 0f,
    var thickness: Float = 0f,
    var sheen: Float = 0f,
    val sheenColor: Color = Color(0x000000)
) : MeshStandardMaterial(color = color, roughness = roughness, metalness = metalness) {
    override val type: String = "MeshPhysicalMaterial"
}
