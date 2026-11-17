package com.nexustools.genericui.ext.box3d

/**
 * 3D Triangle Mesh static/kinematic collision shape defined by `vertices` and triangle `indices`.
 */
class TriMeshShape3D(
    val vertices: List<Vec3>,
    val indices: IntArray
) : ColliderShape3D(ShapeType3D.TRI_MESH) {

    override fun computeAABB(worldTransform: Transform3D): AABB3D {
        if (vertices.isEmpty()) {
            return AABB3D(worldTransform.position.clone(), worldTransform.position.clone())
        }
        val min = Vec3(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        val max = Vec3(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        for (v in vertices) {
            val w = worldTransform.transformPoint(v)
            if (w.x < min.x) min.x = w.x
            if (w.y < min.y) min.y = w.y
            if (w.z < min.z) min.z = w.z
            if (w.x > max.x) max.x = w.x
            if (w.y > max.y) max.y = w.y
            if (w.z > max.z) max.z = w.z
        }
        return AABB3D(min, max)
    }

    override fun computeMassProperties(density: Float): MassProperties3D =
        MassProperties3D(0f, Vec3(), Vec3())

    override fun supportPoint(worldTransform: Transform3D, direction: Vec3): Vec3 {
        if (vertices.isEmpty()) return worldTransform.position.clone()
        val localDir = worldTransform.inverseTransformVector(direction)
        var best = vertices[0]
        var bestDot = best.dot(localDir)
        for (i in 1 until vertices.size) {
            val d = vertices[i].dot(localDir)
            if (d > bestDot) {
                bestDot = d
                best = vertices[i]
            }
        }
        return worldTransform.transformPoint(best)
    }
}
