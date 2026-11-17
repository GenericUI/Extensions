package com.nexustools.genericui.ext.threejs.cameras

/**
 * Orthographic projection camera (`THREE.OrthographicCamera`).
 */
class OrthographicCamera(
    var left: Float = -1f,
    var right: Float = 1f,
    var top: Float = 1f,
    var bottom: Float = -1f,
    var near: Float = 0.1f,
    var far: Float = 2000f
) : Camera() {
    override val type: String = "OrthographicCamera"

    var zoom: Float = 1f

    init {
        updateProjectionMatrix()
    }

    override fun updateProjectionMatrix() {
        val z = zoom.coerceAtLeast(0.0001f)
        val dx = (right - left) / (2f * z)
        val dy = (top - bottom) / (2f * z)
        val cx = (right + left) / 2f
        val cy = (top + bottom) / 2f
        projectionMatrix.makeOrthographic(cx - dx, cx + dx, cy + dy, cy - dy, near, far)
        super.updateProjectionMatrix()
    }
}
