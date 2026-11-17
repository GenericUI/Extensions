package com.nexustools.genericui.ext.box3d

/**
 * 3D Hookean spring-damper joint connecting two rigid body anchors with `restLength`, `stiffness`, and `damping`.
 */
class SpringJoint3D(
    bodyA: RigidBody3D,
    bodyB: RigidBody3D,
    localAnchorA: Vec3 = Vec3(),
    localAnchorB: Vec3 = Vec3(),
    var restLength: Float = 1f,
    var stiffness: Float = 40f,
    var damping: Float = 4f
) : Joint3D(JointType3D.SPRING, bodyA, bodyB, localAnchorA, localAnchorB) {

    override fun solveVelocityConstraints(dt: Float) {
        if (dt <= 0f) return
        val pA = worldAnchorA
        val pB = worldAnchorB
        val delta = pB - pA
        val currentLen = delta.length()
        if (currentLen < 1e-6f) return
        val dir = delta / currentLen
        val stretch = currentLen - restLength
        val relVel = (bodyB.linearVelocity - bodyA.linearVelocity).dot(dir)
        val forceMag = stiffness * stretch + damping * relVel
        val impulse = dir * (forceMag * dt)
        bodyA.applyImpulseAtPoint(impulse, pA)
        bodyB.applyImpulseAtPoint(-impulse, pB)
    }
}
