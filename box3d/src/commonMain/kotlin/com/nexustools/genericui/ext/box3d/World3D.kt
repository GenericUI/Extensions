package com.nexustools.genericui.ext.box3d

import com.nexustools.genericui.ext.threejs.core.Object3D
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 3D Physics World managing rigid bodies, colliders, broadphase/narrowphase collision manifolds,
 * sequential impulse constraints, raycasting, and automatic Three.js [Object3D] synchronization.
 */
class World3D(
    val gravity: Vec3 = Vec3(0f, -9.81f, 0f)
) {
    private var nextBodyId = 1
    private val _bodies = mutableListOf<RigidBody3D>()
    val bodies: List<RigidBody3D> get() = _bodies

    private val _joints = mutableListOf<Joint3D>()
    val joints: List<Joint3D> get() = _joints

    private val _manifolds = mutableListOf<ContactManifold3D>()
    val contactManifolds: List<ContactManifold3D> get() = _manifolds

    private val previousCollisionPairs = mutableSetOf<Pair<Collider3D, Collider3D>>()
    var collisionListener: CollisionEventListener3D? = null

    var velocityIterations: Int = 8
    var positionIterations: Int = 3

    fun createRigidBody(desc: RigidBodyDesc3D, boundObject3D: Object3D? = null): RigidBody3D {
        val body = RigidBody3D(nextBodyId++, desc)
        if (boundObject3D != null) {
            body.boundObject3D = boundObject3D
            body.syncToThreeObject3D(boundObject3D)
        }
        _bodies.add(body)
        return body
    }

    fun removeRigidBody(body: RigidBody3D) {
        _bodies.remove(body)
        _joints.removeAll { it.bodyA === body || it.bodyB === body }
    }

    fun addJoint(joint: Joint3D): Joint3D {
        _joints.add(joint)
        return joint
    }

    fun removeJoint(joint: Joint3D) {
        _joints.remove(joint)
    }

    /**
     * Advances the 3D physics world by [dtSeconds] and synchronizes all bound Three.js [Object3D] instances.
     */
    fun step(dtSeconds: Float) {
        if (dtSeconds <= 0f) return

        // 1. Integrate forces -> velocities
        for (b in _bodies) {
            if (b.type != BodyType3D.DYNAMIC || !b.isAwake) continue

            val gx = gravity.x * b.gravityScale
            val gy = gravity.y * b.gravityScale
            val gz = gravity.z * b.gravityScale

            if (!b.lockTranslationsX) b.linearVelocity.x += (gx + b.forceAccumulator.x * b.invMass) * dtSeconds
            if (!b.lockTranslationsY) b.linearVelocity.y += (gy + b.forceAccumulator.y * b.invMass) * dtSeconds
            if (!b.lockTranslationsZ) b.linearVelocity.z += (gz + b.forceAccumulator.z * b.invMass) * dtSeconds

            if (!b.lockRotationsX) b.angularVelocity.x += (b.torqueAccumulator.x * b.invPrincipalInertia.x) * dtSeconds
            if (!b.lockRotationsY) b.angularVelocity.y += (b.torqueAccumulator.y * b.invPrincipalInertia.y) * dtSeconds
            if (!b.lockRotationsZ) b.angularVelocity.z += (b.torqueAccumulator.z * b.invPrincipalInertia.z) * dtSeconds

            val linDamp = (1f - dtSeconds * b.linearDamping).coerceIn(0f, 1f)
            val angDamp = (1f - dtSeconds * b.angularDamping).coerceIn(0f, 1f)
            b.linearVelocity.mulLocal(linDamp)
            b.angularVelocity.mulLocal(angDamp)
        }

        // 2. Detect collisions (broadphase AABB + narrowphase manifold generation)
        detectCollisions()

        // 3. Solve velocity constraints
        repeat(velocityIterations) {
            for (joint in _joints) {
                joint.solveVelocityConstraints(dtSeconds)
            }
            for (manifold in _manifolds) {
                solveManifoldVelocity(manifold)
            }
        }

        // 4. Integrate velocities -> positions & rotations
        for (b in _bodies) {
            if (b.type == BodyType3D.STATIC || !b.isAwake) continue
            if (!b.lockTranslationsX) b.transform.position.x += b.linearVelocity.x * dtSeconds
            if (!b.lockTranslationsY) b.transform.position.y += b.linearVelocity.y * dtSeconds
            if (!b.lockTranslationsZ) b.transform.position.z += b.linearVelocity.z * dtSeconds
            b.transform.rotation.integrateAngularVelocity(b.angularVelocity, dtSeconds)
            b.forceAccumulator.set(0f, 0f, 0f)
            b.torqueAccumulator.set(0f, 0f, 0f)
        }

        // 5. Solve position constraints (Baumgarte / pseudo-velocity positional correction)
        repeat(positionIterations) {
            for (joint in _joints) {
                joint.solvePositionConstraints()
            }
            for (manifold in _manifolds) {
                solveManifoldPosition(manifold)
            }
        }

        // 6. Synchronize bound Three.js Object3D instances
        for (b in _bodies) {
            b.boundObject3D?.let { b.syncToThreeObject3D(it) }
        }
    }

    private fun detectCollisions() {
        _manifolds.clear()
        val currentPairs = mutableSetOf<Pair<Collider3D, Collider3D>>()

        for (i in 0 until _bodies.size) {
            val bodyA = _bodies[i]
            for (j in i + 1 until _bodies.size) {
                val bodyB = _bodies[j]
                if (bodyA.type == BodyType3D.STATIC && bodyB.type == BodyType3D.STATIC) continue

                for (colA in bodyA.colliders) {
                    val aabbA = colA.computeWorldAABB()
                    for (colB in bodyB.colliders) {
                        if ((colA.collisionGroups and colB.solverGroups) == 0 &&
                            (colB.collisionGroups and colA.solverGroups) == 0
                        ) continue

                        val aabbB = colB.computeWorldAABB()
                        if (!aabbA.overlaps(aabbB)) continue

                        val manifold = computeManifold(colA, colB) ?: continue
                        val pair = Pair(colA, colB)
                        currentPairs.add(pair)

                        if (colA.isSensor || colB.isSensor) {
                            if (pair !in previousCollisionPairs) {
                                val sensor = if (colA.isSensor) colA else colB
                                val other = if (colA.isSensor) colB else colA
                                collisionListener?.onSensorEnter(sensor, other)
                            }
                        } else {
                            _manifolds.add(manifold)
                            if (pair !in previousCollisionPairs) {
                                collisionListener?.onCollisionEnter(manifold)
                            } else {
                                collisionListener?.onCollisionStay(manifold)
                            }
                        }
                    }
                }
            }
        }

        for (oldPair in previousCollisionPairs) {
            if (oldPair !in currentPairs) {
                val (colA, colB) = oldPair
                if (colA.isSensor || colB.isSensor) {
                    val sensor = if (colA.isSensor) colA else colB
                    val other = if (colA.isSensor) colB else colA
                    collisionListener?.onSensorExit(sensor, other)
                } else {
                    collisionListener?.onCollisionExit(colA, colB)
                }
            }
        }

        previousCollisionPairs.clear()
        previousCollisionPairs.addAll(currentPairs)
    }

    internal fun computeManifold(colA: Collider3D, colB: Collider3D): ContactManifold3D? {
        val tfA = colA.worldTransform()
        val tfB = colB.worldTransform()
        val sA = colA.shape
        val sB = colB.shape

        if (sA is SphereShape3D && sB is SphereShape3D) {
            val delta = tfB.position - tfA.position
            val dist = delta.length()
            val radSum = sA.radius + sB.radius
            if (dist >= radSum) return null
            val normal = if (dist > 1e-6f) delta / dist else Vec3.UNIT_Y
            val depth = radSum - dist
            val ptA = tfA.position + normal * sA.radius
            val ptB = tfB.position - normal * sB.radius
            return ContactManifold3D(colA, colB, normal, listOf(ContactPoint3D(ptA, ptB, depth)))
        }

        if (sA is SphereShape3D && sB is BoxShape3D) {
            return collideSphereBox(colA, sA, tfA, colB, sB, tfB, flip = false)
        }
        if (sA is BoxShape3D && sB is SphereShape3D) {
            return collideSphereBox(colB, sB, tfB, colA, sA, tfA, flip = true)
        }

        // General SAT / AABB fallback for Box-Box, Capsule, Cylinder, Hull, Mesh, Heightfield
        val aabbA = sA.computeAABB(tfA)
        val aabbB = sB.computeAABB(tfB)
        val ox = min(aabbA.max.x, aabbB.max.x) - max(aabbA.min.x, aabbB.min.x)
        val oy = min(aabbA.max.y, aabbB.max.y) - max(aabbA.min.y, aabbB.min.y)
        val oz = min(aabbA.max.z, aabbB.max.z) - max(aabbA.min.z, aabbB.min.z)
        if (ox <= 0f || oy <= 0f || oz <= 0f) return null

        val cA = aabbA.center
        val cB = aabbB.center
        val normal: Vec3
        val depth: Float
        when {
            ox <= oy && ox <= oz -> {
                depth = ox
                normal = Vec3(if (cB.x >= cA.x) 1f else -1f, 0f, 0f)
            }
            oy <= ox && oy <= oz -> {
                depth = oy
                normal = Vec3(0f, if (cB.y >= cA.y) 1f else -1f, 0f)
            }
            else -> {
                depth = oz
                normal = Vec3(0f, 0f, if (cB.z >= cA.z) 1f else -1f)
            }
        }
        val mid = (cA + cB) * 0.5f
        val pt = ContactPoint3D(
            worldPointA = mid + normal * (depth * 0.5f),
            worldPointB = mid - normal * (depth * 0.5f),
            penetrationDepth = depth
        )
        return ContactManifold3D(colA, colB, normal, listOf(pt))
    }

    private fun collideSphereBox(
        sphereCol: Collider3D,
        sphere: SphereShape3D,
        tfSphere: Transform3D,
        boxCol: Collider3D,
        box: BoxShape3D,
        tfBox: Transform3D,
        flip: Boolean
    ): ContactManifold3D? {
        val localCenter = tfBox.inverseTransformPoint(tfSphere.position)
        val hx = box.halfExtents.x
        val hy = box.halfExtents.y
        val hz = box.halfExtents.z
        val closestLocal = Vec3(
            localCenter.x.coerceIn(-hx, hx),
            localCenter.y.coerceIn(-hy, hy),
            localCenter.z.coerceIn(-hz, hz)
        )
        val closestWorld = tfBox.transformPoint(closestLocal)
        val delta = closestWorld - tfSphere.position
        val distSq = delta.lengthSq()
        if (distSq >= sphere.radius * sphere.radius) return null

        val dist = sqrt(distSq)
        val normalFromSphereToBox = if (dist > 1e-6f) delta / dist else Vec3(0f, -1f, 0f)
        val depth = sphere.radius - dist
        val ptSphere = tfSphere.position + normalFromSphereToBox * sphere.radius
        val ptBox = closestWorld

        return if (!flip) {
            ContactManifold3D(
                colliderA = sphereCol,
                colliderB = boxCol,
                normal = normalFromSphereToBox,
                points = listOf(ContactPoint3D(ptSphere, ptBox, depth))
            )
        } else {
            ContactManifold3D(
                colliderA = boxCol,
                colliderB = sphereCol,
                normal = -normalFromSphereToBox,
                points = listOf(ContactPoint3D(ptBox, ptSphere, depth))
            )
        }
    }

    private fun solveManifoldVelocity(manifold: ContactManifold3D) {
        val bodyA = manifold.colliderA.body
        val bodyB = manifold.colliderB.body
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum <= 0f) return

        val n = manifold.normal
        val restitution = max(manifold.colliderA.restitution, manifold.colliderB.restitution)
        val friction = sqrt(manifold.colliderA.friction * manifold.colliderB.friction)

        for (cp in manifold.points) {
            val relVel = bodyB.linearVelocity - bodyA.linearVelocity
            val vn = relVel.dot(n)
            if (vn > 0f) continue

            val jn = -(1f + restitution) * vn / invMassSum
            cp.normalImpulse = max(0f, cp.normalImpulse + jn)
            val normalImpulseVec = n * jn
            bodyA.applyLinearImpulse(-normalImpulseVec, wake = false)
            bodyB.applyLinearImpulse(normalImpulseVec, wake = false)

            // Tangential friction impulse
            val tangentVel = relVel - n * vn
            val tangentSpeed = tangentVel.length()
            if (tangentSpeed > 1e-6f) {
                val t = tangentVel / tangentSpeed
                val jtMax = friction * abs(jn)
                val jt = (-tangentSpeed / invMassSum).coerceIn(-jtMax, jtMax)
                val frictionImpulse = t * jt
                bodyA.applyLinearImpulse(-frictionImpulse, wake = false)
                bodyB.applyLinearImpulse(frictionImpulse, wake = false)
            }
        }
    }

    private fun solveManifoldPosition(manifold: ContactManifold3D) {
        val bodyA = manifold.colliderA.body
        val bodyB = manifold.colliderB.body
        val invMassSum = bodyA.invMass + bodyB.invMass
        if (invMassSum <= 0f) return

        val slop = 0.005f
        val percent = 0.35f
        for (cp in manifold.points) {
            val corrMag = max(cp.penetrationDepth - slop, 0f) / invMassSum * percent
            val correction = manifold.normal * corrMag
            if (bodyA.type == BodyType3D.DYNAMIC) {
                bodyA.transform.position.subLocal(correction * bodyA.invMass)
            }
            if (bodyB.type == BodyType3D.DYNAMIC) {
                bodyB.transform.position.addLocal(correction * bodyB.invMass)
            }
        }
    }

    /**
     * Casts a [Ray3D] through the 3D world and returns the closest [RaycastHit3D] if any collider is hit.
     */
    fun raycastClosest(ray: Ray3D): RaycastHit3D? {
        var bestHit: RaycastHit3D? = null
        val dir = ray.direction.normalized()
        for (b in _bodies) {
            for (c in b.colliders) {
                val aabb = c.computeWorldAABB()
                val hit = intersectRayAABB(ray.origin, dir, ray.maxDistance, aabb, c) ?: continue
                if (bestHit == null || hit.distance < bestHit.distance) {
                    bestHit = hit
                }
            }
        }
        return bestHit
    }

    private fun intersectRayAABB(
        origin: Vec3,
        dir: Vec3,
        maxDist: Float,
        aabb: AABB3D,
        collider: Collider3D
    ): RaycastHit3D? {
        var tMin = 0f
        var tMax = maxDist
        var normal = Vec3(0f, 1f, 0f)

        val axes = arrayOf(
            Triple(origin.x, dir.x, Pair(aabb.min.x, aabb.max.x)),
            Triple(origin.y, dir.y, Pair(aabb.min.y, aabb.max.y)),
            Triple(origin.z, dir.z, Pair(aabb.min.z, aabb.max.z))
        )

        for (axisIdx in 0..2) {
            val (o, d, bounds) = axes[axisIdx]
            if (abs(d) < 1e-6f) {
                if (o < bounds.first || o > bounds.second) return null
            } else {
                val invD = 1f / d
                var t1 = (bounds.first - o) * invD
                var t2 = (bounds.second - o) * invD
                var sign = -1f
                if (t1 > t2) {
                    val tmp = t1; t1 = t2; t2 = tmp
                    sign = 1f
                }
                if (t1 > tMin) {
                    tMin = t1
                    normal = when (axisIdx) {
                        0 -> Vec3(sign, 0f, 0f)
                        1 -> Vec3(0f, sign, 0f)
                        else -> Vec3(0f, 0f, sign)
                    }
                }
                tMax = min(tMax, t2)
                if (tMin > tMax) return null
            }
        }
        return RaycastHit3D(
            collider = collider,
            point = origin + dir * tMin,
            normal = normal,
            distance = tMin
        )
    }
}
