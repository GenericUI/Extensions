package com.nexustools.genericui.ext.threejs.textures

/**
 * Raw pixel-buffer texture (`THREE.DataTexture`).
 */
class DataTexture(
    val data: ByteArray,
    width: Int,
    height: Int
) : Texture(width = width, height = height, rgbaBytes = data) {
    init {
        generateMipmaps = false
        flipY = false
        needsUpdate = true
    }
}
