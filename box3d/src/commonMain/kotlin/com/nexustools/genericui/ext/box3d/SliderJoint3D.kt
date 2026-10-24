package com.nexustools.genericui.ext.box3d

/**
 * 3D Slider (prismatic) joint allowing 1DOF linear translation along a shared axis.
 */
class SliderJoint3D(
    bodyA: RigidBody3D,
    bodyB: RigidBody3D,
    localAnchorA: Vec3 = Vec3(),
    localAnchorB: Vec3 = Vec3(),
    val localAxisA: Vec3 = Vec3.UNIT_X,
    var enableLimits: Boolean = false,
    var minDistance: Float = -1f,
    var maxDistance: Float = 1f,
    var enableMotor: Boolean = false,
    var targetLinearVelocity: Float = 0f,
    var maxMotorForce: Float = 20f
) : Joint3D(JointType3D.SLIDER, bodyA, bodyB, localAnchorA, localAnchorB) {

    override fun solveVelocityConstraints(dt: Float) {
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum <= 0f) return
        val axis = bodyA.transform.transformVector(localAxisA).normalized()
        val relVel = bodyB.linearVelocity - bodyA.linearVelocity
        val axialSpeed = relVel.dot(axis)
        val perpVel = relVel - axis * axialSpeed
        val perpImpulse = perpVel * (-0.8f / invMassSum)
        bodyA.applyLinearImpulse(-perpImpulse, wake = false)
        bodyB.applyLinearImpulse(perpImpulse, wake = false)

        if (enableMotor && dt > 0f) {
            val force = ((targetLinearVelocity - axialSpeed) / invMassSum).coerceIn(-maxMotorForce, maxMotorForce)
            val motorImpulse = axis * (force * dt)
            bodyA.applyLinearImpulse(-motorImpulse)
            bodyB.applyLinearImpulse(motorImpulse)
        }
    }
}
