package com.nexustools.genericui.ext.box2d

/**
 * Surface normal and intersection fraction produced by a 2D raycast (`b2RayCastOutput`).
 */
data class RayCastOutput2D(
    val normal: Vec2 = Vec2(),
    var fraction: Float = 0f
)
