package com.nexustools.genericui.ext.box3d

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

/**
 * 3D Capsule collision shape aligned along the local Y axis with `halfHeight` and hemisphere `radius`.
 */
class CapsuleShape3D(
    var halfHeight: Float = 0.5f,
    var radius: Float = 0.25f
) : ColliderShape3D(ShapeType3D.CAPSULE) {

    override fun computeAABB(worldTransform: Transform3D): AABB3D {
        val top = worldTransform.transformPoint(Vec3(0f, halfHeight, 0f))
        val bottom = worldTransform.transformPoint(Vec3(0f, -halfHeight, 0f))
        return AABB3D(
            Vec3(
                min(top.x, bottom.x) - radius,
                min(top.y, bottom.y) - radius,
                min(top.z, bottom.z) - radius
            ),
            Vec3(
                max(top.x, bottom.x) + radius,
                max(top.y, bottom.y) + radius,
                max(top.z, bottom.z) + radius
            )
        )
    }

    override fun computeMassProperties(density: Float): MassProperties3D {
        val cylHeight = halfHeight * 2f
        val cylVolume = (PI * radius * radius * cylHeight).toFloat()
        val sphereVolume = (4.0 / 3.0 * PI * radius * radius * radius).toFloat()
        val mass = density * (cylVolume + sphereVolume)
        val totalHeight = cylHeight + 2f * radius
        val ixx = mass * (3f * radius * radius + totalHeight * totalHeight) / 12f
        val iyy = 0.5f * mass * radius * radius
        return MassProperties3D(
            mass = mass,
            centerOfMass = Vec3(),
            principalInertia = Vec3(ixx, iyy, ixx)
        )
    }

    override fun supportPoint(worldTransform: Transform3D, direction: Vec3): Vec3 {
        val localDir = worldTransform.inverseTransformVector(direction)
        val capCenter = Vec3(0f, if (localDir.y >= 0f) halfHeight else -halfHeight, 0f)
        val n = direction.normalized()
        return worldTransform.transformPoint(capCenter) + n * radius
    }
}
