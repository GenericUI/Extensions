package com.nexustools.genericui.ext.box2d

import kotlin.math.sqrt

/**
 * Complete 2D rigid-body physics world (`b2World`) managing [Body2D]s, [Fixture2D]s, [Joint2D]s,
 * broadphase AABB overlap detection, narrowphase collision manifolds, sequential impulse constraint
 * solving, contact callbacks, and 2D raycasting.
 */
class World2D(
    val gravity: Vec2 = Vec2(0f, -9.81f),
    var allowSleeping: Boolean = true
) {
    private val _bodies = mutableListOf<Body2D>()
    val bodies: List<Body2D>
        get() = _bodies

    private val _joints = mutableListOf<Joint2D>()
    val joints: List<Joint2D>
        get() = _joints

    private val _contacts = mutableListOf<Contact2D>()
    val contacts: List<Contact2D>
        get() = _contacts

    var contactListener: ContactListener2D? = null

    fun createBody(def: BodyDef2D = BodyDef2D()): Body2D {
        val body = Body2D(def, this)
        _bodies.add(body)
        return body
    }

    fun destroyBody(body: Body2D) {
        _joints.removeAll { it.bodyA === body || it.bodyB === body }
        _contacts.removeAll { it.fixtureA.body === body || it.fixtureB.body === body }
        _bodies.remove(body)
    }

    fun createJoint(def: JointDef2D): Joint2D {
        val joint = when (def.type) {
            JointType2D.REVOLUTE -> RevoluteJoint2D(
                bodyA = def.bodyA,
                bodyB = def.bodyB,
                localAnchorA = def.localAnchorA.clone(),
                localAnchorB = def.localAnchorB.clone(),
                referenceAngle = def.referenceAngle,
                enableLimit = def.enableLimit,
                lowerAngle = def.lowerLimit,
                upperAngle = def.upperLimit,
                enableMotor = def.enableMotor,
                motorSpeed = def.motorSpeed,
                maxMotorTorque = def.maxMotorTorqueOrForce,
                collideConnected = def.collideConnected
            )
            JointType2D.PRISMATIC -> PrismaticJoint2D(
                bodyA = def.bodyA,
                bodyB = def.bodyB,
                localAnchorA = def.localAnchorA.clone(),
                localAnchorB = def.localAnchorB.clone(),
                localAxisA = def.localAxisA.clone(),
                referenceAngle = def.referenceAngle,
                enableLimit = def.enableLimit,
                lowerTranslation = def.lowerLimit,
                upperTranslation = def.upperLimit,
                enableMotor = def.enableMotor,
                motorSpeed = def.motorSpeed,
                maxMotorForce = def.maxMotorTorqueOrForce,
                collideConnected = def.collideConnected
            )
            JointType2D.DISTANCE -> DistanceJoint2D(
                bodyA = def.bodyA,
                bodyB = def.bodyB,
                localAnchorA = def.localAnchorA.clone(),
                localAnchorB = def.localAnchorB.clone(),
                length = def.length,
                stiffness = def.stiffness,
                damping = def.damping,
                collideConnected = def.collideConnected
            )
            JointType2D.WELD -> WeldJoint2D(
                bodyA = def.bodyA,
                bodyB = def.bodyB,
                localAnchorA = def.localAnchorA.clone(),
                localAnchorB = def.localAnchorB.clone(),
                referenceAngle = def.referenceAngle,
                stiffness = def.stiffness,
                damping = def.damping,
                collideConnected = def.collideConnected
            )
            JointType2D.MOUSE -> MouseJoint2D(
                bodyA = def.bodyA,
                bodyB = def.bodyB,
                targetPoint = def.bodyB.getWorldPoint(def.localAnchorB),
                maxForce = def.maxMotorTorqueOrForce.coerceAtLeast(100f),
                stiffness = def.stiffness.coerceAtLeast(5f),
                damping = def.damping.coerceAtLeast(1f)
            )
        }
        joint.userData = def.userData
        _joints.add(joint)
        return joint
    }

    fun addJoint(joint: Joint2D): Joint2D {
        _joints.add(joint)
        return joint
    }

    fun destroyJoint(joint: Joint2D) {
        _joints.remove(joint)
    }

    /**
     * Advances the 2D physics simulation by [timeStepSeconds] using [velocityIterations]
     * and [positionIterations] sequential impulse passes.
     */
    fun step(
        timeStepSeconds: Float,
        velocityIterations: Int = 8,
        positionIterations: Int = 3
    ) {
        val dt = timeStepSeconds.coerceIn(0f, 0.1f)
        if (dt <= 0f) return

        // 1. Integrate forces & gravity into velocities
        for (body in _bodies) {
            if (!body.isEnabled || !body.isAwake) continue
            if (body.type == BodyType2D.DYNAMIC) {
                body.linearVelocity.x += dt * (gravity.x * body.gravityScale + body.invMass * body.force.x)
                body.linearVelocity.y += dt * (gravity.y * body.gravityScale + body.invMass * body.force.y)
                body.angularVelocity += dt * body.invInertia * body.torque

                body.linearVelocity.mulLocal(1f / (1f + dt * body.linearDamping))
                body.angularVelocity *= 1f / (1f + dt * body.angularDamping)
            }
            for (fixture in body.fixtures) {
                fixture.updateAABB()
            }
        }

        // 2. Broadphase & narrowphase collision detection
        detectCollisions()

        // 3. Solve velocity constraints
        for (iter in 0 until velocityIterations.coerceAtLeast(1)) {
            for (joint in _joints) {
                joint.solveVelocityConstraints(dt)
            }
            for (contact in _contacts) {
                if (!contact.isEnabled || !contact.isTouching) continue
                if (contact.fixtureA.isSensor || contact.fixtureB.isSensor) continue
                solveContactVelocity(contact)
            }
        }

        // 4. Integrate velocities into positions
        for (body in _bodies) {
            if (!body.isEnabled || !body.isAwake || body.type == BodyType2D.STATIC) continue
            val newPos = body.position + (body.linearVelocity * dt)
            val newAngle = if (body.isFixedRotation) body.angle else body.angle + body.angularVelocity * dt
            body.setTransform(newPos, newAngle)
            for (fixture in body.fixtures) {
                fixture.updateAABB()
            }
        }

        // 5. Solve position constraints (Baumgarte / NGS positional correction)
        for (iter in 0 until positionIterations.coerceAtLeast(1)) {
            for (joint in _joints) {
                joint.solvePositionConstraints()
            }
            for (contact in _contacts) {
                if (!contact.isEnabled || !contact.isTouching) continue
                if (contact.fixtureA.isSensor || contact.fixtureB.isSensor) continue
                solveContactPosition(contact)
            }
        }

        // 6. Clear accumulators
        for (body in _bodies) {
            body.force.setZero()
            body.torque = 0f
        }
    }

    fun rayCast(
        point1: Vec2,
        point2: Vec2,
        callback: (fixture: Fixture2D, point: Vec2, normal: Vec2, fraction: Float) -> Float
    ) {
        val input = RayCastInput2D(point1.clone(), point2.clone(), 1f)
        val output = RayCastOutput2D()
        for (body in _bodies) {
            if (!body.isEnabled) continue
            for (fixture in body.fixtures) {
                if (fixture.rayCast(output, input)) {
                    val fraction = output.fraction
                    val hitPoint = point1 + (point2 - point1) * fraction
                    val nextMax = callback(fixture, hitPoint, output.normal.clone(), fraction)
                    if (nextMax <= 0f) return
                    input.maxFraction = nextMax
                }
            }
        }
    }

    fun queryAABB(aabb: AABB2D, callback: (Fixture2D) -> Boolean) {
        for (body in _bodies) {
            if (!body.isEnabled) continue
            for (fixture in body.fixtures) {
                fixture.updateAABB()
                if (fixture.aabb.overlaps(aabb)) {
                    if (!callback(fixture)) return
                }
            }
        }
    }

    private fun detectCollisions() {
        val newContacts = mutableListOf<Contact2D>()
        for (i in 0 until _bodies.size) {
            val bA = _bodies[i]
            if (!bA.isEnabled) continue
            for (j in i + 1 until _bodies.size) {
                val bB = _bodies[j]
                if (!bB.isEnabled) continue
                if (bA.type == BodyType2D.STATIC && bB.type == BodyType2D.STATIC) continue

                for (fA in bA.fixtures) {
                    for (fB in bB.fixtures) {
                        if (!shouldCollide(fA, fB)) continue
                        if (!fA.aabb.overlaps(fB.aabb)) continue
                        val manifold = computeManifold(fA, fB)
                        if (manifold != null) {
                            val contact = Contact2D(fA, fB, manifold)
                            newContacts.add(contact)
                            contactListener?.beginContact(contact)
                        }
                    }
                }
            }
        }
        _contacts.clear()
        _contacts.addAll(newContacts)
    }

    private fun shouldCollide(fA: Fixture2D, fB: Fixture2D): Boolean {
        if (fA.groupIndex == fB.groupIndex && fA.groupIndex != 0) {
            return fA.groupIndex > 0
        }
        return (fA.maskBits and fB.categoryBits) != 0 && (fA.categoryBits and fB.maskBits) != 0
    }

    private fun computeManifold(fA: Fixture2D, fB: Fixture2D): Manifold2D? {
        val sA = fA.shape
        val sB = fB.shape
        if (sA is CircleShape2D && sB is CircleShape2D) {
            val pA = fA.body.getWorldPoint(sA.p)
            val pB = fB.body.getWorldPoint(sB.p)
            val d = pB - pA
            val distSq = d.lengthSquared()
            val rSum = sA.radius + sB.radius
            if (distSq > rSum * rSum) return null
            val dist = sqrt(distSq)
            val normal = if (dist > 1e-6f) d * (1f / dist) else Vec2(0f, 1f)
            val contactPoint = pA + normal * (sA.radius - 0.5f * (rSum - dist))
            return Manifold2D(
                normal = normal,
                points = mutableListOf(contactPoint),
                penetrationDepth = rSum - dist
            )
        }

        // General AABB / SAT approximation for polygon & circle/edge combinations
        val aabbA = fA.aabb
        val aabbB = fB.aabb
        val cA = aabbA.getCenter()
        val cB = aabbB.getCenter()
        val extA = aabbA.getExtents()
        val extB = aabbB.getExtents()

        val dx = cB.x - cA.x
        val px = (extA.x + extB.x) - kotlin.math.abs(dx)
        if (px <= 0f) return null

        val dy = cB.y - cA.y
        val py = (extA.y + extB.y) - kotlin.math.abs(dy)
        if (py <= 0f) return null

        return if (px < py) {
            val sx = if (dx < 0f) -1f else 1f
            Manifold2D(
                normal = Vec2(sx, 0f),
                points = mutableListOf(Vec2(cA.x + extA.x * sx, cB.y)),
                penetrationDepth = px
            )
        } else {
            val sy = if (dy < 0f) -1f else 1f
            Manifold2D(
                normal = Vec2(0f, sy),
                points = mutableListOf(Vec2(cB.x, cA.y + extA.y * sy)),
                penetrationDepth = py
            )
        }
    }

    private fun solveContactVelocity(contact: Contact2D) {
        val bA = contact.fixtureA.body
        val bB = contact.fixtureB.body
        val invMassSum = bA.invMass + bB.invMass
        if (invMassSum <= 0f) return

        val normal = contact.manifold.normal
        val rv = bB.linearVelocity - bA.linearVelocity
        val velAlongNormal = rv.dot(normal)
        if (velAlongNormal > 0f) return

        val e = contact.restitution
        val j = (-(1f + e) * velAlongNormal) / invMassSum
        val impulse = normal * j
        bA.linearVelocity.addScaledLocal(impulse, -bA.invMass)
        bB.linearVelocity.addScaledLocal(impulse, bB.invMass)

        // Coulomb friction impulse
        val tangent = rv - (normal * rv.dot(normal))
        if (tangent.normalize() > 1e-6f) {
            val jt = -rv.dot(tangent) / invMassSum
            val maxFriction = j * contact.friction
            val frictionImpulse = tangent * jt.coerceIn(-maxFriction, maxFriction)
            bA.linearVelocity.addScaledLocal(frictionImpulse, -bA.invMass)
            bB.linearVelocity.addScaledLocal(frictionImpulse, bB.invMass)
        }

        contactListener?.postSolve(
            contact,
            ContactImpulse2D(
                normalImpulses = floatArrayOf(j, 0f),
                tangentImpulses = floatArrayOf(0f, 0f),
                count = 1
            )
        )
    }

    private fun solveContactPosition(contact: Contact2D) {
        val bA = contact.fixtureA.body
        val bB = contact.fixtureB.body
        val invMassSum = bA.invMass + bB.invMass
        if (invMassSum <= 0f) return

        val slop = 0.005f
        val percent = 0.4f
        val depth = (contact.manifold.penetrationDepth - slop).coerceAtLeast(0f)
        if (depth <= 0f) return

        val correction = contact.manifold.normal * ((depth / invMassSum) * percent)
        bA.setTransform(bA.position - correction * bA.invMass, bA.angle)
        bB.setTransform(bB.position + correction * bB.invMass, bB.angle)
    }
}
