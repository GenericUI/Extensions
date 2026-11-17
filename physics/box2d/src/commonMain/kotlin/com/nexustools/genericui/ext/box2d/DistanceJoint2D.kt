package com.nexustools.genericui.ext.box2d

/**
 * 2D distance / spring-damper constraint between two anchor points (`b2DistanceJoint`).
 */
class DistanceJoint2D(
    bodyA: Body2D,
    bodyB: Body2D,
    val localAnchorA: Vec2 = Vec2(),
    val localAnchorB: Vec2 = Vec2(),
    var length: Float = Vec2.distance(bodyA.getWorldPoint(localAnchorA), bodyB.getWorldPoint(localAnchorB)),
    var stiffness: Float = 0f,
    var damping: Float = 0f,
    collideConnected: Boolean = false
) : Joint2D(JointType2D.DISTANCE, bodyA, bodyB, collideConnected) {

    override fun getAnchorA(): Vec2 = bodyA.getWorldPoint(localAnchorA)

    override fun getAnchorB(): Vec2 = bodyB.getWorldPoint(localAnchorB)

    override fun solveVelocityConstraints(dt: Float) {
        val pA = getAnchorA()
        val pB = getAnchorB()
        val u = pB - pA
        val currentLen = u.normalize()
        if (currentLen < 1e-6f) return

        val vA = bodyA.linearVelocity
        val vB = bodyB.linearVelocity
        val relVel = (vB - vA).dot(u)
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum <= 0f) return

        val impulseMag = if (stiffness > 0f) {
            val c = currentLen - length
            -(dt * (stiffness * c + damping * relVel)) / (1f + dt * damping * invMassSum)
        } else {
            -relVel / invMassSum
        }

        val impulse = u * impulseMag
        bodyA.linearVelocity.addScaledLocal(impulse, -bodyA.invMass)
        bodyB.linearVelocity.addScaledLocal(impulse, bodyB.invMass)
    }

    override fun solvePositionConstraints(): Boolean {
        if (stiffness > 0f) return true
        val pA = getAnchorA()
        val pB = getAnchorB()
        val u = pB - pA
        val currentLen = u.normalize()
        val c = currentLen - length
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum > 0f) {
            val impulse = u * (-c * 0.5f / invMassSum)
            bodyA.transform.p.addScaledLocal(impulse, -bodyA.invMass)
            bodyB.transform.p.addScaledLocal(impulse, bodyB.invMass)
        }
        return kotlin.math.abs(c) < 0.005f
    }
}
