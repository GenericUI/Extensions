package com.nexustools.genericui.ext.box3d

/**
 * 3D Hinge (revolute) joint allowing rotation around a single shared axis, with optional limits and motor.
 */
class HingeJoint3D(
    bodyA: RigidBody3D,
    bodyB: RigidBody3D,
    localAnchorA: Vec3 = Vec3(),
    localAnchorB: Vec3 = Vec3(),
    val localAxisA: Vec3 = Vec3.UNIT_Y,
    val localAxisB: Vec3 = Vec3.UNIT_Y,
    var enableLimits: Boolean = false,
    var minAngleRad: Float = 0f,
    var maxAngleRad: Float = 0f,
    var enableMotor: Boolean = false,
    var motorSpeedRadPerSec: Float = 0f,
    var maxMotorTorque: Float = 10f
) : Joint3D(JointType3D.HINGE, bodyA, bodyB, localAnchorA, localAnchorB) {

    override fun solveVelocityConstraints(dt: Float) {
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum <= 0f) return

        if (enableMotor && dt > 0f) {
            val worldAxis = bodyA.transform.transformVector(localAxisA).normalized()
            val relAngVel = (bodyB.angularVelocity - bodyA.angularVelocity).dot(worldAxis)
            val torqueMag = ((motorSpeedRadPerSec - relAngVel) * 0.5f).coerceIn(-maxMotorTorque, maxMotorTorque)
            val motorImpulse = worldAxis * (torqueMag * dt)
            bodyA.applyAngularImpulse(-motorImpulse)
            bodyB.applyAngularImpulse(motorImpulse)
        }

        val errVel = bodyB.linearVelocity - bodyA.linearVelocity
        val impulse = errVel * (-0.5f / invMassSum)
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
