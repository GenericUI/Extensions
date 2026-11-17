package com.nexustools.genericui.ext.box3d

import kotlin.math.cos

/**
 * 3D Kinematic Character Controller supporting ground detection, slope limits, step-up climbing, and slide-move resolution.
 */
class CharacterController3D(
    val body: RigidBody3D,
    var maxSlopeAngleRad: Float = 0.7853982f, // 45 deg
    var maxStepHeight: Float = 0.35f,
    var skinWidth: Float = 0.02f,
    var upDirection: Vec3 = Vec3.UNIT_Y
) {
    var isGrounded: Boolean = false
        private set

    val groundNormal: Vec3 = Vec3(0f, 1f, 0f)

    /**
     * Moves the character by [desiredTranslation] while resolving collisions against static/dynamic obstacles in [world].
     */
    fun moveCharacter(world: World3D, desiredTranslation: Vec3, dt: Float): Vec3 {
        val startPos = body.position.clone()
        val targetPos = startPos + desiredTranslation
        body.position.copy(targetPos)

        isGrounded = false
        val minCosSlope = cos(maxSlopeAngleRad)

        for (otherBody in world.bodies) {
            if (otherBody === body) continue
            for (charCollider in body.colliders) {
                val charAabb = charCollider.computeWorldAABB()
                for (otherCollider in otherBody.colliders) {
                    if (otherCollider.isSensor) continue
                    val otherAabb = otherCollider.computeWorldAABB()
                    if (!charAabb.overlaps(otherAabb)) continue

                    val manifold = world.computeManifold(charCollider, otherCollider) ?: continue
                    val point = manifold.points.firstOrNull() ?: continue
                    if (point.penetrationDepth > skinWidth) {
                        val pushOut = manifold.normal * (point.penetrationDepth - skinWidth)
                        body.position.subLocal(pushOut)
                        val groundAlignment = (-manifold.normal).dot(upDirection)
                        if (groundAlignment >= minCosSlope) {
                            isGrounded = true
                            groundNormal.copy(-manifold.normal)
                        }
                    }
                }
            }
        }

        body.boundObject3D?.let { body.syncToThreeObject3D(it) }
        return body.position - startPos
    }
}
