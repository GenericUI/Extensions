package com.nexustools.genericui.ext.box3d

import kotlin.math.PI

/**
 * 3D Sphere collision shape defined by `radius`.
 */
class SphereShape3D(
    var radius: Float = 0.5f
) : ColliderShape3D(ShapeType3D.SPHERE) {

    override fun computeAABB(worldTransform: Transform3D): AABB3D {
        val p = worldTransform.position
        return AABB3D(
            Vec3(p.x - radius, p.y - radius, p.z - radius),
            Vec3(p.x + radius, p.y + radius, p.z + radius)
        )
    }

    override fun computeMassProperties(density: Float): MassProperties3D {
        val volume = (4.0 / 3.0 * PI * radius * radius * radius).toFloat()
        val mass = density * volume
        val inertia = 0.4f * mass * radius * radius
        return MassProperties3D(
            mass = mass,
            centerOfMass = Vec3(),
            principalInertia = Vec3(inertia, inertia, inertia)
        )
    }

    override fun supportPoint(worldTransform: Transform3D, direction: Vec3): Vec3 {
        val n = direction.normalized()
        return worldTransform.position + n * radius
    }
}
