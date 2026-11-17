package com.nexustools.genericui.ext.threejs.objects

import com.nexustools.genericui.ext.threejs.core.Object3D

/**
 * Logical grouping node in the 3D scene graph (`THREE.Group`).
 */
class Group : Object3D() {
    override val type: String = "Group"
}
