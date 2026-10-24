package com.nexustools.genericui.ext.threejs.cameras

/**
 * Perspective projection camera (`THREE.PerspectiveCamera`).
 */
open class PerspectiveCamera(
    var fov: Float = 50f,
    var aspect: Float = 1f,
    var near: Float = 0.1f,
    var far: Float = 2000f
) : Camera() {
    override val type: String = "PerspectiveCamera"

    var zoom: Float = 1f
    var focus: Float = 10f
    var filmGauge: Float = 35f
    var filmOffset: Float = 0f

    init {
        updateProjectionMatrix()
    }

    override fun updateProjectionMatrix() {
        val effectiveFov = if (zoom != 1f && zoom > 0f) fov / zoom else fov
        projectionMatrix.makePerspectiveFov(effectiveFov, aspect.coerceAtLeast(0.001f), near, far)
        super.updateProjectionMatrix()
    }
}
