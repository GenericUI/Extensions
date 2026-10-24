package com.nexustools.genericui.ext.box2d

/**
 * Mass, center of mass, and rotational inertia computed for a [Shape2D] (`b2MassData`).
 */
data class MassData2D(
    var mass: Float = 0f,
    val center: Vec2 = Vec2(),
    var inertia: Float = 0f
)
