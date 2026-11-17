package com.nexustools.genericui.ext.box2d

import kotlin.math.sqrt

/**
 * Active collision pair between [fixtureA] and [fixtureB] (`b2Contact`).
 */
class Contact2D(
    val fixtureA: Fixture2D,
    val fixtureB: Fixture2D,
    val manifold: Manifold2D = Manifold2D()
) {
    var isTouching: Boolean = true
    var isEnabled: Boolean = true
    var friction: Float = sqrt(fixtureA.friction * fixtureB.friction)
    var restitution: Float = maxOf(fixtureA.restitution, fixtureB.restitution)
    var tangentSpeed: Float = 0f
}
