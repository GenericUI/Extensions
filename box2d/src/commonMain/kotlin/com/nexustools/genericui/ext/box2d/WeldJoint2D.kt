package com.nexustools.genericui.ext.box2d

/**
 * 2D rigid weld joint locking relative position and orientation between [bodyA] and [bodyB] (`b2WeldJoint`).
 */
class WeldJoint2D(
    bodyA: Body2D,
    bodyB: Body2D,
    val localAnchorA: Vec2 = Vec2(),
    val localAnchorB: Vec2 = Vec2(),
    val referenceAngle: Float = bodyB.angle - bodyA.angle,
    var stiffness: Float = 0f,
    var damping: Float = 0f,
    collideConnected: Boolean = false
) : Joint2D(JointType2D.WELD, bodyA, bodyB, collideConnected) {

    override fun getAnchorA(): Vec2 = bodyA.getWorldPoint(localAnchorA)

    override fun getAnchorB(): Vec2 = bodyB.getWorldPoint(localAnchorB)

    override fun solveVelocityConstraints(dt: Float) {
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum > 0f) {
            val dv = bodyB.linearVelocity - bodyA.linearVelocity
            val imp = dv * (-1f / invMassSum)
            bodyA.linearVelocity.addScaledLocal(imp, -bodyA.invMass)
            bodyB.linearVelocity.addScaledLocal(imp, bodyB.invMass)
        }
        val invInertiaSum = bodyA.invInertia + bodyB.invInertia
        if (invInertiaSum > 0f) {
            val dw = bodyB.angularVelocity - bodyA.angularVelocity
            val rotImp = -dw / invInertiaSum
            bodyA.angularVelocity -= bodyA.invInertia * rotImp
            bodyB.angularVelocity += bodyB.invInertia * rotImp
        }
    }

    override fun solvePositionConstraints(): Boolean {
        val pErr = getAnchorB() - getAnchorA()
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum > 0f) {
            val corr = pErr * (0.5f / invMassSum)
            bodyA.transform.p.addScaledLocal(corr, bodyA.invMass)
            bodyB.transform.p.addScaledLocal(corr, -bodyB.invMass)
        }
        val angleErr = (bodyB.angle - bodyA.angle) - referenceAngle
        val invInertiaSum = bodyA.invInertia + bodyB.invInertia
        if (invInertiaSum > 0f) {
            bodyA.setTransform(bodyA.position, bodyA.angle + angleErr * (bodyA.invInertia / invInertiaSum) * 0.5f)
            bodyB.setTransform(bodyB.position, bodyB.angle - angleErr * (bodyB.invInertia / invInertiaSum) * 0.5f)
        }
        return pErr.length() <= 0.005f && kotlin.math.abs(angleErr) <= 0.01f
    }
}
