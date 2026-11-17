package com.nexustools.genericui.ext.threejs.materials

import com.nexustools.genericui.ext.threejs.core.EventDispatcher
import com.nexustools.genericui.ext.threejs.math.MathUtils

/**
 * Abstract base class for surface and shader materials (`THREE.Material`).
 */
open class Material : EventDispatcher() {
    val id: Long = MathUtils.nextObjectId()
    val uuid: String = MathUtils.generateUUID()
    var name: String = ""
    open val type: String = "Material"

    var side: Side = Side.FRONT
    var blending: BlendingMode = BlendingMode.NORMAL
    var opacity: Float = 1f
    var transparent: Boolean = false
    var depthTest: Boolean = true
    var depthWrite: Boolean = true
    var wireframe: Boolean = false
    var vertexColors: Boolean = false
    var visible: Boolean = true
    var alphaTest: Float = 0f

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
