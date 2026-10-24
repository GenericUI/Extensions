package com.nexustools.genericui.ext.threejs.textures

/**
 * 6-face environment cubemap texture (`THREE.CubeTexture`).
 */
class CubeTexture(
    val faces: List<Texture> = emptyList()
) : Texture() {
    init {
        flipY = false
    }
}
