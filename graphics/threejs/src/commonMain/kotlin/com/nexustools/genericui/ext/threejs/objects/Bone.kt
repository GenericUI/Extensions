package com.nexustools.genericui.ext.threejs.objects

import com.nexustools.genericui.ext.threejs.core.Object3D

/**
 * Skeletal joint node (`THREE.Bone`) used within a [Skeleton].
 */
class Bone : Object3D() {
    override val type: String = "Bone"
}
