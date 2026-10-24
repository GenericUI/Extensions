package com.nexustools.genericui.ext.box3d

/**
 * 3D collider instance bound to a parent [RigidBody3D].
 */
class Collider3D(
    val body: RigidBody3D,
    desc: ColliderDesc3D
) {
    val shape: ColliderShape3D = desc.shape
    val localTransform: Transform3D = desc.localTransform.clone()
    var density: Float = desc.density
    var friction: Float = desc.friction
    var restitution: Float = desc.restitution
    var isSensor: Boolean = desc.isSensor
    var collisionGroups: Int = desc.collisionGroups
    var solverGroups: Int = desc.solverGroups
    var userData: Any? = desc.userData

    fun worldTransform(): Transform3D {
        val worldPos = body.transform.transformPoint(localTransform.position)
        val worldRot = (body.transform.rotation * localTransform.rotation).normalize()
        return Transform3D(worldPos, worldRot)
    }

    fun computeWorldAABB(): AABB3D = shape.computeAABB(worldTransform())
}
