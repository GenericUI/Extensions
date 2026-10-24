package com.nexustools.genericui.ext.threejs.objects

import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.materials.LineBasicMaterial
import com.nexustools.genericui.ext.threejs.materials.Material

/**
 * Disconnected pairs of vertices rendered as line segments (`THREE.LineSegments`).
 */
class LineSegments(
    geometry: BufferGeometry = BufferGeometry(),
    material: Material = LineBasicMaterial()
) : Line(geometry, material) {
    override val type: String = "LineSegments"
}
