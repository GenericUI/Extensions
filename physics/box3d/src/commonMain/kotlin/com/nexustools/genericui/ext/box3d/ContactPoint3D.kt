package com.nexustools.genericui.ext.box3d

/**
 * Individual 3D contact point inside a [ContactManifold3D].
 */
data class ContactPoint3D(
    val worldPointA: Vec3 = Vec3(),
    val worldPointB: Vec3 = Vec3(),
    val penetrationDepth: Float = 0f,
    var normalImpulse: Float = 0f,
    var tangentImpulse1: Float = 0f,
    var tangentImpulse2: Float = 0f
) {
    val midpoint: Vec3
        get() = (worldPointA + worldPointB) * 0.5f
}
