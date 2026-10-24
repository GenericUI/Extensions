package com.nexustools.genericui.ext.box2d

/**
 * Ray segment query input for 2D raycasting (`b2RayCastInput`).
 */
data class RayCastInput2D(
    val p1: Vec2 = Vec2(),
    val p2: Vec2 = Vec2(),
    var maxFraction: Float = 1f
)
