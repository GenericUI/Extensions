package com.nexustools.genericui.ext.threejs.objects

import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.materials.LineBasicMaterial
import com.nexustools.genericui.ext.threejs.materials.Material

/**
 * Continuous 3D polyline strip (`THREE.Line`).
 */
open class Line(
    var geometry: BufferGeometry = BufferGeometry(),
    var material: Material = LineBasicMaterial()
) : Object3D() {
    override val type: String = "Line"
}
