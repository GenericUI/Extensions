package com.nexustools.genericui.ext.box2d

/**
 * Collision fixture binding a [Shape2D] and surface material properties to a [Body2D] (`b2Fixture`).
 */
class Fixture2D(
    val body: Body2D,
    def: FixtureDef2D
) {
    val shape: Shape2D = def.shape.clone()
    var density: Float = def.density
    var friction: Float = def.friction
    var restitution: Float = def.restitution
    var isSensor: Boolean = def.isSensor
    var categoryBits: Int = def.categoryBits
    var maskBits: Int = def.maskBits
    var groupIndex: Int = def.groupIndex
    var userData: Any? = def.userData

    val aabb: AABB2D = AABB2D()

    fun updateAABB() {
        shape.computeAABB(aabb, body.transform)
    }

    fun computeAABB(): AABB2D {
        updateAABB()
        return aabb
    }

    fun testPoint(p: Vec2): Boolean = shape.testPoint(body.transform, p)


    fun rayCast(output: RayCastOutput2D, input: RayCastInput2D): Boolean =
        shape.rayCast(output, input, body.transform)
}
