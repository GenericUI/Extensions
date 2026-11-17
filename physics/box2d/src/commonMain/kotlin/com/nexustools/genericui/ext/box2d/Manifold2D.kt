package com.nexustools.genericui.ext.box2d

/**
 * Contact manifold describing collision geometry between two [Fixture2D]s (`b2Manifold`).
 */
data class Manifold2D(
    val normal: Vec2 = Vec2(),
    val points: MutableList<Vec2> = mutableListOf(),
    var penetrationDepth: Float = 0f
) {
    val pointCount: Int
        get() = points.size
}
