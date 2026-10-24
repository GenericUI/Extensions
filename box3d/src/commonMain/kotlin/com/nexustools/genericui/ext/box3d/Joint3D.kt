package com.nexustools.genericui.ext.box3d

/**
 * Base class for 3D constraints connecting two [RigidBody3D] instances.
 */
abstract class Joint3D(
    val type: JointType3D,
    val bodyA: RigidBody3D,
    val bodyB: RigidBody3D,
    val localAnchorA: Vec3 = Vec3(),
    val localAnchorB: Vec3 = Vec3(),
    var collideConnected: Boolean = false
) {
    var userData: Any? = null

    val worldAnchorA: Vec3
        get() = bodyA.transform.transformPoint(localAnchorA)

    val worldAnchorB: Vec3
        get() = bodyB.transform.transformPoint(localAnchorB)

    internal abstract fun solveVelocityConstraints(dt: Float)
    internal open fun solvePositionConstraints(): Boolean = true
}
