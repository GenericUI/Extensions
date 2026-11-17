package com.nexustools.genericui.ext.threejs.cameras

/**
 * Multi-viewport camera array (`THREE.ArrayCamera`) for split-screen and stereo rendering.
 */
class ArrayCamera(
    val cameras: MutableList<PerspectiveCamera> = mutableListOf()
) : PerspectiveCamera() {
    override val type: String = "ArrayCamera"
}
