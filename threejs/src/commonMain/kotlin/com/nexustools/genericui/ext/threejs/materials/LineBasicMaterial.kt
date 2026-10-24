package com.nexustools.genericui.ext.threejs.materials

import com.nexustools.genericui.ext.threejs.math.Color

/**
 * Material for rendering wireframe/polyline segments (`THREE.LineBasicMaterial`).
 */
class LineBasicMaterial(
    val color: Color = Color(0xffffff),
    var linewidth: Float = 1f
) : Material() {
    override val type: String = "LineBasicMaterial"
}
