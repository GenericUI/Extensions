package com.nexustools.genericui.ext.threejs.scenes

import com.nexustools.genericui.ext.threejs.math.Color

/**
 * Exponential-squared distance fog (`THREE.FogExp2`).
 */
class FogExp2(
    color: Color = Color(0xffffff),
    var density: Float = 0.00025f
) : Fog(color, 1f, 1000f) {

    override fun clone(): FogExp2 = FogExp2(color.clone(), density)
}
