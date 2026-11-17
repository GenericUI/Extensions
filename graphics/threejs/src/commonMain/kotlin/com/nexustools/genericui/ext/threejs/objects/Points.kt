package com.nexustools.genericui.ext.threejs.objects

import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.materials.Material
import com.nexustools.genericui.ext.threejs.materials.PointsMaterial

/**
 * Point cloud / particle system node (`THREE.Points`).
 */
class Points(
    var geometry: BufferGeometry = BufferGeometry(),
    var material: Material = PointsMaterial()
) : Object3D() {
    override val type: String = "Points"
}
