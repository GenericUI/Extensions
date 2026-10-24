package com.nexustools.genericui.ext.box3d

import kotlin.math.PI
import kotlin.math.sqrt

/**
 * 3D Cylinder collision shape aligned along the local Y axis with `halfHeight` and `radius`.
 */
class CylinderShape3D(
    var halfHeight: Float = 0.5f,
    var radius: Float = 0.5f
) : ColliderShape3D(ShapeType3D.CYLINDER) {

    override fun computeAABB(worldTransform: Transform3D): AABB3D {
        val box = BoxShape3D(radius, halfHeight, radius)
        return box.computeAABB(worldTransform)
    }

    override fun computeMassProperties(density: Float): MassProperties3D {
        val height = halfHeight * 2f
        val volume = (PI * radius * radius * height).toFloat()
        val mass = density * volume
        val ixz = mass * (3f * radius * radius + height * height) / 12f
        val iy = 0.5f * mass * radius * radius
        return MassProperties3D(
            mass = mass,
            centerOfMass = Vec3(),
            principalInertia = Vec3(ixz, iy, ixz)
        )
    }

    override fun supportPoint(worldTransform: Transform3D, direction: Vec3): Vec3 {
        val localDir = worldTransform.inverseTransformVector(direction)
        val xzLen = sqrt(localDir.x * localDir.x + localDir.z * localDir.z)
        val sx = if (xzLen > 1e-6f) (localDir.x / xzLen) * radius else 0f
        val sz = if (xzLen > 1e-6f) (localDir.z / xzLen) * radius else 0f
        val sy = if (localDir.y >= 0f) halfHeight else -halfHeight
        return worldTransform.transformPoint(Vec3(sx, sy, sz))
    }
}
