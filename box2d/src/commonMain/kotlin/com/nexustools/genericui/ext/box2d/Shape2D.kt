package com.nexustools.genericui.ext.box2d

/**
 * Abstract 2D collision shape (`b2Shape`).
 */
abstract class Shape2D(
    val type: ShapeType2D,
    var radius: Float = 0.01f
) {
    abstract fun clone(): Shape2D
    abstract fun testPoint(xf: Transform2D, p: Vec2): Boolean
    abstract fun rayCast(output: RayCastOutput2D, input: RayCastInput2D, transform: Transform2D): Boolean
    abstract fun computeAABB(aabb: AABB2D, xf: Transform2D)
    abstract fun computeMass(density: Float): MassData2D
}
