package com.nexustools.genericui.ext.box3d

/**
 * Mass, local center of mass, and principal rotational inertia for a 3D collider or rigid body.
 */
data class MassProperties3D(
    val mass: Float = 1f,
    val centerOfMass: Vec3 = Vec3(),
    val principalInertia: Vec3 = Vec3(1f, 1f, 1f)
) {
    val invMass: Float
        get() = if (mass > 1e-6f) 1f / mass else 0f

    val invPrincipalInertia: Vec3
        get() = Vec3(
            if (principalInertia.x > 1e-6f) 1f / principalInertia.x else 0f,
            if (principalInertia.y > 1e-6f) 1f / principalInertia.y else 0f,
            if (principalInertia.z > 1e-6f) 1f / principalInertia.z else 0f
        )
}
