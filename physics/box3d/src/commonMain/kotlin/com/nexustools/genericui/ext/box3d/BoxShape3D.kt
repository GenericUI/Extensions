package com.nexustools.genericui.ext.box3d

import kotlin.math.abs

/**
 * 3D Box collision shape defined by `halfExtents` (`hx`, `hy`, `hz`).
 */
class BoxShape3D(
    val halfExtents: Vec3 = Vec3(0.5f, 0.5f, 0.5f)
) : ColliderShape3D(ShapeType3D.BOX) {

    constructor(hx: Float, hy: Float, hz: Float) : this(Vec3(hx, hy, hz))

    override fun computeAABB(worldTransform: Transform3D): AABB3D {
        val corners = arrayOf(
            Vec3(-halfExtents.x, -halfExtents.y, -halfExtents.z),
            Vec3(halfExtents.x, -halfExtents.y, -halfExtents.z),
            Vec3(-halfExtents.x, halfExtents.y, -halfExtents.z),
            Vec3(halfExtents.x, halfExtents.y, -halfExtents.z),
            Vec3(-halfExtents.x, -halfExtents.y, halfExtents.z),
            Vec3(halfExtents.x, -halfExtents.y, halfExtents.z),
            Vec3(-halfExtents.x, halfExtents.y, halfExtents.z),
            Vec3(halfExtents.x, halfExtents.y, halfExtents.z)
        )
        val min = Vec3(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        val max = Vec3(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        for (c in corners) {
            val w = worldTransform.transformPoint(c)
            if (w.x < min.x) min.x = w.x
            if (w.y < min.y) min.y = w.y
            if (w.z < min.z) min.z = w.z
            if (w.x > max.x) max.x = w.x
            if (w.y > max.y) max.y = w.y
            if (w.z > max.z) max.z = w.z
        }
        return AABB3D(min, max)
    }

    override fun computeMassProperties(density: Float): MassProperties3D {
        val wx = halfExtents.x * 2f
        val wy = halfExtents.y * 2f
        val wz = halfExtents.z * 2f
        val volume = wx * wy * wz
        val mass = density * volume
        val factor = mass / 12f
        return MassProperties3D(
            mass = mass,
            centerOfMass = Vec3(),
            principalInertia = Vec3(
                factor * (wy * wy + wz * wz),
                factor * (wx * wx + wz * wz),
                factor * (wx * wx + wy * wy)
            )
        )
    }

    override fun supportPoint(worldTransform: Transform3D, direction: Vec3): Vec3 {
        val localDir = worldTransform.inverseTransformVector(direction)
        val localSupport = Vec3(
            if (localDir.x >= 0f) halfExtents.x else -halfExtents.x,
            if (localDir.y >= 0f) halfExtents.y else -halfExtents.y,
            if (localDir.z >= 0f) halfExtents.z else -halfExtents.z
        )
        return worldTransform.transformPoint(localSupport)
    }
}
