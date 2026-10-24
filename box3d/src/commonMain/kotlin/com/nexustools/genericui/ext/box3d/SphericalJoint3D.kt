package com.nexustools.genericui.ext.box3d

/**
 * 3D Spherical (ball-and-socket) joint constraining two anchor points to coincide while allowing 3DOF rotation.
 */
class SphericalJoint3D(
    bodyA: RigidBody3D,
    bodyB: RigidBody3D,
    localAnchorA: Vec3 = Vec3(),
    localAnchorB: Vec3 = Vec3()
) : Joint3D(JointType3D.SPHERICAL, bodyA, bodyB, localAnchorA, localAnchorB) {

    override fun solveVelocityConstraints(dt: Float) {
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum <= 0f) return
        val rA = worldAnchorA - bodyA.transform.position
        val rB = worldAnchorB - bodyB.transform.position
        val vA = bodyA.linearVelocity + bodyA.angularVelocity.cross(rA)
        val vB = bodyB.linearVelocity + bodyB.angularVelocity.cross(rB)
        val relVel = vB - vA
        val impulse = relVel * (-0.6f / invMassSum)
        bodyA.applyImpulseAtPoint(-impulse, worldAnchorA, wake = false)
        bodyB.applyImpulseAtPoint(impulse, worldAnchorB, wake = false)
    }

    override fun solvePositionConstraints(): Boolean {
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum <= 0f) return true
        val err = worldAnchorB - worldAnchorA
        val correction = err * (0.25f / invMassSum)
        if (bodyA.type == BodyType3D.DYNAMIC) bodyA.transform.position.addLocal(correction * bodyA.invMass)
        if (bodyB.type == BodyType3D.DYNAMIC) bodyB.transform.position.subLocal(correction * bodyB.invMass)
        return err.lengthSq() < 1e-4f
    }
}
