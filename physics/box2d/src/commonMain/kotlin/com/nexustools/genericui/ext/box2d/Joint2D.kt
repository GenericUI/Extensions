package com.nexustools.genericui.ext.box2d

/**
 * Abstract base class for 2D physics constraints connecting [bodyA] and [bodyB] (`b2Joint`).
 */
abstract class Joint2D(
    val type: JointType2D,
    val bodyA: Body2D,
    val bodyB: Body2D,
    val collideConnected: Boolean = false,
    var userData: Any? = null
) {
    abstract fun getAnchorA(): Vec2
    abstract fun getAnchorB(): Vec2

    internal abstract fun solveVelocityConstraints(dt: Float)
    internal abstract fun solvePositionConstraints(): Boolean
}
