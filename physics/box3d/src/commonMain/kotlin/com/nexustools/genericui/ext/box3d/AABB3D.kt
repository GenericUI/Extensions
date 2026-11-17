package com.nexustools.genericui.ext.box3d

import kotlin.math.max
import kotlin.math.min

/**
 * 3D Axis-Aligned Bounding Box (`min` and `max` corners) for broadphase collision detection.
 */
data class AABB3D(
    val min: Vec3 = Vec3(0f, 0f, 0f),
    val max: Vec3 = Vec3(0f, 0f, 0f)
) {
    val center: Vec3
        get() = Vec3(
            (min.x + max.x) * 0.5f,
            (min.y + max.y) * 0.5f,
            (min.z + max.z) * 0.5f
        )

    val extents: Vec3
        get() = Vec3(
            (max.x - min.x) * 0.5f,
            (max.y - min.y) * 0.5f,
            (max.z - min.z) * 0.5f
        )

    fun overlaps(other: AABB3D): Boolean {
        if (max.x < other.min.x || min.x > other.max.x) return false
        if (max.y < other.min.y || min.y > other.max.y) return false
        if (max.z < other.min.z || min.z > other.max.z) return false
        return true
    }

    fun contains(point: Vec3): Boolean {
        return point.x in min.x..max.x &&
            point.y in min.y..max.y &&
            point.z in min.z..max.z
    }

    fun combine(other: AABB3D): AABB3D = AABB3D(
        Vec3(min(min.x, other.min.x), min(min.y, other.min.y), min(min.z, other.min.z)),
        Vec3(max(max.x, other.max.x), max(max.y, other.max.y), max(max.z, other.max.z))
    )

    fun expandByScalar(margin: Float): AABB3D = AABB3D(
        Vec3(min.x - margin, min.y - margin, min.z - margin),
        Vec3(max.x + margin, max.y + margin, max.z + margin)
    )
}
