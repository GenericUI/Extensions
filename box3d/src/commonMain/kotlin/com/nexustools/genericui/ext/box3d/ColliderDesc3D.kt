package com.nexustools.genericui.ext.box3d

/**
 * Descriptor used to attach a [Collider3D] onto a [RigidBody3D].
 */
data class ColliderDesc3D(
    val shape: ColliderShape3D,
    var localTransform: Transform3D = Transform3D(),
    var density: Float = 1f,
    var friction: Float = 0.5f,
    var restitution: Float = 0.1f,
    var isSensor: Boolean = false,
    var collisionGroups: Int = 0x0001,
    var solverGroups: Int = 0xFFFF,
    var userData: Any? = null
)
