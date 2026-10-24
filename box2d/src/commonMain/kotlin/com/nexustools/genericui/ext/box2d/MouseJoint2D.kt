package com.nexustools.genericui.ext.box2d

/**
 * Interactive pointer/mouse spring constraint pulling a point on [bodyB] toward [target] (`b2MouseJoint`).
 */
class MouseJoint2D(
    bodyA: Body2D,
    bodyB: Body2D,
    targetPoint: Vec2 = bodyB.position.clone(),
    var maxForce: Float = 1000f,
    var stiffness: Float = 25f,
    var damping: Float = 5f
) : Joint2D(JointType2D.MOUSE, bodyA, bodyB, false) {

    val target: Vec2 = targetPoint.clone()
    val localAnchorB: Vec2 = bodyB.getLocalPoint(targetPoint)

    fun setTarget(newTarget: Vec2) {
        target.copy(newTarget)
        bodyB.isAwake = true
    }

    override fun getAnchorA(): Vec2 = target.clone()

    override fun getAnchorB(): Vec2 = bodyB.getWorldPoint(localAnchorB)

    override fun solveVelocityConstraints(dt: Float) {
        if (dt <= 0f || bodyB.invMass <= 0f) return
        val anchorB = getAnchorB()
        val c = anchorB - target
        val v = bodyB.linearVelocity
        val force = (c * (-stiffness)) - (v * damping)
        val maxImpulse = maxForce * dt
        var impulse = force * dt
        val mag = impulse.length()
        if (mag > maxImpulse && mag > 0f) {
            impulse *= (maxImpulse / mag)
        }
        bodyB.applyLinearImpulse(impulse, anchorB, true)
    }

    override fun solvePositionConstraints(): Boolean = true
}
