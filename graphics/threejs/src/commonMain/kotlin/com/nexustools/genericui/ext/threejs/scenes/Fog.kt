package com.nexustools.genericui.ext.threejs.scenes

import com.nexustools.genericui.ext.threejs.math.Color

/**
 * Linear distance fog (`THREE.Fog`).
 */
open class Fog(
    val color: Color = Color(0xffffff),
    var near: Float = 1f,
    var far: Float = 1000f
) {
    var name: String = ""

    open fun clone(): Fog = Fog(color.clone(), near, far)
}
