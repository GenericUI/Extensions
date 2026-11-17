package com.nexustools.genericui.ext.threejs.textures

import com.nexustools.genericui.ext.threejs.core.EventDispatcher
import com.nexustools.genericui.ext.threejs.math.MathUtils
import com.nexustools.genericui.ext.threejs.math.Vector2
import com.nexustools.genericui.image.ImageBuffer

/**
 * 2D GPU texture resource (`THREE.Texture`).
 */
open class Texture(
    var width: Int = 1,
    var height: Int = 1,
    var rgbaBytes: ByteArray? = null,
    var image: ImageBuffer? = null
) : EventDispatcher() {
    val id: Long = MathUtils.nextObjectId()
    val uuid: String = MathUtils.generateUUID()
    var name: String = ""

    val offset: Vector2 = Vector2(0f, 0f)
    val repeat: Vector2 = Vector2(1f, 1f)
    val center: Vector2 = Vector2(0f, 0f)
    var rotation: Float = 0f

    var generateMipmaps: Boolean = true
    var flipY: Boolean = true
    var version: Int = 0
        private set

    var needsUpdate: Boolean = true
        set(value) {
            field = value
            if (value) version++
        }

    fun dispose() {
        dispatchEvent("dispose")
    }
}
