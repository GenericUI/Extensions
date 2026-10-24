package com.nexustools.genericui.ext.box3d

/**
 * 3D Fixed Joint locking relative translation and orientation between two rigid bodies.
 */
class FixedJoint3D(
    bodyA: RigidBody3D,
    bodyB: RigidBody3D,
    localAnchorA: Vec3 = Vec3(),
    localAnchorB: Vec3 = Vec3()
) : Joint3D(JointType3D.FIXED, bodyA, bodyB, localAnchorA, localAnchorB) {

    override fun solveVelocityConstraints(dt: Float) {
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum <= 0f) return
        val relVel = bodyB.linearVelocity - bodyA.linearVelocity
        val impulse = relVel * (-1f / invMassSum)
        bodyA.applyLinearImpulse(-impulse, wake = false)
        bodyB.applyLinearImpulse(impulse, wake = false)
    }

    override fun solvePositionConstraints(): Boolean {
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum <= 0f) return true
        val err = worldAnchorB - worldAnchorA
        val correction = err * (0.2f / invMassSum)
        if (bodyA.type == BodyType3D.DYNAMIC) bodyA.transform.position.addLocal(correction * bodyA.invMass)
        if (bodyB.type == BodyType3D.DYNAMIC) bodyB.transform.position.subLocal(correction * bodyB.invMass)
        return err.lengthSq() < 1e-4f
    }
}
