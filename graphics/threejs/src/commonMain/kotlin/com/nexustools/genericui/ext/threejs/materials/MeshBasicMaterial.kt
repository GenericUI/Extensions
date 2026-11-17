package com.nexustools.genericui.ext.threejs.materials

import com.nexustools.genericui.ext.threejs.math.Color
import com.nexustools.genericui.ext.threejs.textures.Texture

/**
 * Unlit surface material (`THREE.MeshBasicMaterial`).
 */
open class MeshBasicMaterial(
    val color: Color = Color(0xffffff),
    var map: Texture? = null,
    wireframe: Boolean = false
) : Material() {
    override val type: String = "MeshBasicMaterial"

    init {
        this.wireframe = wireframe
    }
}
