package com.nexustools.genericui.ext.box2d

/**
 * 2D pin / hinge joint constraining two bodies to share a common world anchor point (`b2RevoluteJoint`).
 */
class RevoluteJoint2D(
    bodyA: Body2D,
    bodyB: Body2D,
    val localAnchorA: Vec2 = Vec2(),
    val localAnchorB: Vec2 = Vec2(),
    val referenceAngle: Float = bodyB.angle - bodyA.angle,
    var enableLimit: Boolean = false,
    var lowerAngle: Float = 0f,
    var upperAngle: Float = 0f,
    var enableMotor: Boolean = false,
    var motorSpeed: Float = 0f,
    var maxMotorTorque: Float = 0f,
    collideConnected: Boolean = false
) : Joint2D(JointType2D.REVOLUTE, bodyA, bodyB, collideConnected) {

    override fun getAnchorA(): Vec2 = bodyA.getWorldPoint(localAnchorA)

    override fun getAnchorB(): Vec2 = bodyB.getWorldPoint(localAnchorB)

    fun getJointAngle(): Float = (bodyB.angle - bodyA.angle) - referenceAngle

    fun getJointSpeed(): Float = bodyB.angularVelocity - bodyA.angularVelocity

    override fun solveVelocityConstraints(dt: Float) {
        if (enableMotor && maxMotorTorque > 0f) {
            val dw = getJointSpeed() - motorSpeed
            val effI = bodyA.invInertia + bodyB.invInertia
            if (effI > 0f) {
                val maxImpulse = maxMotorTorque * dt
                val impulse = (-dw / effI).coerceIn(-maxImpulse, maxImpulse)
                bodyA.angularVelocity -= bodyA.invInertia * impulse
                bodyB.angularVelocity += bodyB.invInertia * impulse
            }
        }

        val rA = bodyA.getWorldVector(localAnchorA - bodyA.localCenter)
        val rB = bodyB.getWorldVector(localAnchorB - bodyB.localCenter)
        val vA = bodyA.linearVelocity + Vec2.cross(bodyA.angularVelocity, rA)
        val vB = bodyB.linearVelocity + Vec2.cross(bodyB.angularVelocity, rB)
        val cdot = vB - vA
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum > 0f) {
            val impulse = cdot * (-1f / invMassSum)
            bodyA.linearVelocity.addScaledLocal(impulse, -bodyA.invMass)
            bodyB.linearVelocity.addScaledLocal(impulse, bodyB.invMass)
        }
    }

    override fun solvePositionConstraints(): Boolean {
        val pA = getAnchorA()
        val pB = getAnchorB()
        val error = pB - pA
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum > 0f && error.lengthSquared() > 1e-6f) {
            val correction = error * (0.5f / invMassSum)
            bodyA.transform.p.addScaledLocal(correction, bodyA.invMass)
            bodyB.transform.p.addScaledLocal(correction, -bodyB.invMass)
        }
        if (enableLimit) {
            val angle = getJointAngle()
            val effI = bodyA.invInertia + bodyB.invInertia
            if (effI > 0f) {
                if (angle < lowerAngle) {
                    val dAngle = lowerAngle - angle
                    bodyA.setTransform(bodyA.position, bodyA.angle - dAngle * (bodyA.invInertia / effI))
                    bodyB.setTransform(bodyB.position, bodyB.angle + dAngle * (bodyB.invInertia / effI))
                } else if (angle > upperAngle) {
                    val dAngle = angle - upperAngle
                    bodyA.setTransform(bodyA.position, bodyA.angle + dAngle * (bodyA.invInertia / effI))
                    bodyB.setTransform(bodyB.position, bodyB.angle - dAngle * (bodyB.invInertia / effI))
                }
            }
        }
        return error.length() <= 0.005f
    }
}
