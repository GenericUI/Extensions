package com.nexustools.genericui.ext.threejs.renderers

import com.nexustools.genericui.ext.threejs.core.EventDispatcher
import com.nexustools.genericui.ext.threejs.math.MathUtils
import com.nexustools.genericui.ext.threejs.math.Vector4
import com.nexustools.genericui.ext.threejs.textures.Texture

/**
 * Offscreen framebuffer render target (`THREE.WebGLRenderTarget`).
 */
class WebGLRenderTarget(
    var width: Int = 512,
    var height: Int = 512,
    var depthBuffer: Boolean = true,
    var stencilBuffer: Boolean = false,
    var samples: Int = 0
) : EventDispatcher() {

    val id: Long = MathUtils.nextObjectId()
    val texture: Texture = Texture(width = width, height = height)
    val viewport: Vector4 = Vector4(0f, 0f, width.toFloat(), height.toFloat())
    val scissor: Vector4 = Vector4(0f, 0f, width.toFloat(), height.toFloat())
    var scissorTest: Boolean = false

    fun setSize(width: Int, height: Int) {
        if (this.width != width || this.height != height) {
            this.width = width
            this.height = height
            this.texture.width = width
            this.texture.height = height
            this.texture.needsUpdate = true
            viewport.set(0f, 0f, width.toFloat(), height.toFloat())
            scissor.set(0f, 0f, width.toFloat(), height.toFloat())
            dispatchEvent("dispose")
        }
    }

    fun dispose() {
        dispatchEvent("dispose")
    }
}
