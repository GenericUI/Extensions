package com.nexustools.genericui.ext.box3d

/**
 * 3D Contact manifold between two colliding [Collider3D] instances.
 */
data class ContactManifold3D(
    val colliderA: Collider3D,
    val colliderB: Collider3D,
    val normal: Vec3,
    val points: List<ContactPoint3D>,
    var isTouching: Boolean = true
)
