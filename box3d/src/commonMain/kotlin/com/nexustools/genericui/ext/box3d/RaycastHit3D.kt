package com.nexustools.genericui.ext.box3d

/**
 * Result of a 3D raycast intersection against a [Collider3D] in [World3D].
 */
data class RaycastHit3D(
    val collider: Collider3D,
    val point: Vec3,
    val normal: Vec3,
    val distance: Float
)
