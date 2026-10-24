package com.nexustools.genericui.ext.box2d

/**
 * 2D slider / prismatic joint allowing 1-DOF linear translation along [localAxisA] (`b2PrismaticJoint`).
 */
class PrismaticJoint2D(
    bodyA: Body2D,
    bodyB: Body2D,
    val localAnchorA: Vec2 = Vec2(),
    val localAnchorB: Vec2 = Vec2(),
    val localAxisA: Vec2 = Vec2(1f, 0f).apply { normalize() },
    val referenceAngle: Float = bodyB.angle - bodyA.angle,
    var enableLimit: Boolean = false,
    var lowerTranslation: Float = 0f,
    var upperTranslation: Float = 0f,
    var enableMotor: Boolean = false,
    var motorSpeed: Float = 0f,
    var maxMotorForce: Float = 0f,
    collideConnected: Boolean = false
) : Joint2D(JointType2D.PRISMATIC, bodyA, bodyB, collideConnected) {

    override fun getAnchorA(): Vec2 = bodyA.getWorldPoint(localAnchorA)

    override fun getAnchorB(): Vec2 = bodyB.getWorldPoint(localAnchorB)

    fun getJointTranslation(): Float {
        val pA = getAnchorA()
        val pB = getAnchorB()
        val d = pB - pA
        val axis = bodyA.getWorldVector(localAxisA)
        return d.dot(axis)
    }

    fun getJointSpeed(): Float {
        val axis = bodyA.getWorldVector(localAxisA)
        return (bodyB.linearVelocity - bodyA.linearVelocity).dot(axis)
    }

    override fun solveVelocityConstraints(dt: Float) {
        val axis = bodyA.getWorldVector(localAxisA)
        val perp = Vec2(-axis.y, axis.x)
        val relVel = bodyB.linearVelocity - bodyA.linearVelocity
        val perpSpeed = relVel.dot(perp)
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum > 0f) {
            val perpImpulse = perp * (-perpSpeed / invMassSum)
            bodyA.linearVelocity.addScaledLocal(perpImpulse, -bodyA.invMass)
            bodyB.linearVelocity.addScaledLocal(perpImpulse, bodyB.invMass)

            if (enableMotor && maxMotorForce > 0f) {
                val axisSpeed = relVel.dot(axis) - motorSpeed
                val maxImp = maxMotorForce * dt
                val motorImp = (-axisSpeed / invMassSum).coerceIn(-maxImp, maxImp)
                val impulseVec = axis * motorImp
                bodyA.linearVelocity.addScaledLocal(impulseVec, -bodyA.invMass)
                bodyB.linearVelocity.addScaledLocal(impulseVec, bodyB.invMass)
            }
        }
    }

    override fun solvePositionConstraints(): Boolean {
        val axis = bodyA.getWorldVector(localAxisA)
        val perp = Vec2(-axis.y, axis.x)
        val d = getAnchorB() - getAnchorA()
        val perpErr = d.dot(perp)
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum > 0f) {
            val corr = perp * (perpErr * 0.5f / invMassSum)
            bodyA.transform.p.addScaledLocal(corr, bodyA.invMass)
            bodyB.transform.p.addScaledLocal(corr, -bodyB.invMass)
            if (enableLimit) {
                val trans = d.dot(axis)
                if (trans < lowerTranslation) {
                    val lCorr = axis * ((trans - lowerTranslation) / invMassSum)
                    bodyA.transform.p.addScaledLocal(lCorr, bodyA.invMass)
                    bodyB.transform.p.addScaledLocal(lCorr, -bodyB.invMass)
                } else if (trans > upperTranslation) {
                    val uCorr = axis * ((trans - upperTranslation) / invMassSum)
                    bodyA.transform.p.addScaledLocal(uCorr, bodyA.invMass)
                    bodyB.transform.p.addScaledLocal(uCorr, -bodyB.invMass)
                }
            }
        }
        return kotlin.math.abs(perpErr) <= 0.005f
    }
}
