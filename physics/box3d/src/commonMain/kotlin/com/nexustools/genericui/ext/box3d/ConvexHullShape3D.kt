package com.nexustools.genericui.ext.box3d

/**
 * 3D Convex Hull collision shape defined by a cloud of 3D vertices.
 */
class ConvexHullShape3D(
    val points: List<Vec3>
) : ColliderShape3D(ShapeType3D.CONVEX_HULL) {

    override fun computeAABB(worldTransform: Transform3D): AABB3D {
        if (points.isEmpty()) {
            return AABB3D(worldTransform.position.clone(), worldTransform.position.clone())
        }
        val min = Vec3(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        val max = Vec3(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        for (pt in points) {
            val w = worldTransform.transformPoint(pt)
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
        val localAabb = computeAABB(Transform3D())
        val ext = localAabb.extents
        return BoxShape3D(ext).computeMassProperties(density)
    }

    override fun supportPoint(worldTransform: Transform3D, direction: Vec3): Vec3 {
        if (points.isEmpty()) return worldTransform.position.clone()
        val localDir = worldTransform.inverseTransformVector(direction)
        var best = points[0]
        var bestDot = best.dot(localDir)
        for (i in 1 until points.size) {
            val d = points[i].dot(localDir)
            if (d > bestDot) {
                bestDot = d
                best = points[i]
            }
        }
        return worldTransform.transformPoint(best)
    }
}
