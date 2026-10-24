package com.nexustools.genericui.ext.box3d

import kotlin.math.floor

/**
 * 3D Terrain Heightfield collision shape defined by a grid of `rows` x `cols` height values.
 */
class HeightfieldShape3D(
    val rows: Int,
    val cols: Int,
    val heights: FloatArray,
    val scale: Vec3 = Vec3(1f, 1f, 1f)
) : ColliderShape3D(ShapeType3D.HEIGHTFIELD) {

    fun heightAtGrid(row: Int, col: Int): Float {
        val r = row.coerceIn(0, rows - 1)
        val c = col.coerceIn(0, cols - 1)
        return heights[r * cols + c] * scale.y
    }

    fun sampleHeightAtLocal(x: Float, z: Float): Float {
        val halfWidth = (cols - 1) * scale.x * 0.5f
        val halfDepth = (rows - 1) * scale.z * 0.5f
        val gx = ((x + halfWidth) / scale.x).coerceIn(0f, (cols - 1).toFloat())
        val gz = ((z + halfDepth) / scale.z).coerceIn(0f, (rows - 1).toFloat())
        val c0 = floor(gx).toInt().coerceIn(0, cols - 1)
        val r0 = floor(gz).toInt().coerceIn(0, rows - 1)
        return heightAtGrid(r0, c0)
    }

    override fun computeAABB(worldTransform: Transform3D): AABB3D {
        var minH = 0f
        var maxH = 0f
        if (heights.isNotEmpty()) {
            minH = heights.min() * scale.y
            maxH = heights.max() * scale.y
        }
        val hx = (cols - 1) * scale.x * 0.5f
        val hz = (rows - 1) * scale.z * 0.5f
        val p = worldTransform.position
        return AABB3D(
            Vec3(p.x - hx, p.y + minH, p.z - hz),
            Vec3(p.x + hx, p.y + maxH, p.z + hz)
        )
    }

    override fun computeMassProperties(density: Float): MassProperties3D =
        MassProperties3D(0f, Vec3(), Vec3())

    override fun supportPoint(worldTransform: Transform3D, direction: Vec3): Vec3 {
        val aabb = computeAABB(worldTransform)
        return Vec3(
            if (direction.x >= 0f) aabb.max.x else aabb.min.x,
            if (direction.y >= 0f) aabb.max.y else aabb.min.y,
            if (direction.z >= 0f) aabb.max.z else aabb.min.z
        )
    }
}
