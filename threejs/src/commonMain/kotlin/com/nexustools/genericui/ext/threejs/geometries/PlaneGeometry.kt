package com.nexustools.genericui.ext.threejs.geometries

import com.nexustools.genericui.ext.threejs.core.BufferAttribute
import com.nexustools.genericui.ext.threejs.core.BufferGeometry

/**
 * 2D subdivided plane geometry in the XY plane (`THREE.PlaneGeometry`).
 */
class PlaneGeometry(
    val width: Float = 1f,
    val height: Float = 1f,
    val widthSegments: Int = 1,
    val heightSegments: Int = 1
) : BufferGeometry() {
    override val type: String = "PlaneGeometry"

    init {
        val gridX = widthSegments.coerceAtLeast(1)
        val gridY = heightSegments.coerceAtLeast(1)
        val gridX1 = gridX + 1
        val gridY1 = gridY + 1

        val widthHalf = width * 0.5f
        val heightHalf = height * 0.5f
        val segmentWidth = width / gridX
        val segmentHeight = height / gridY

        val vertexCount = gridX1 * gridY1
        val positions = FloatArray(vertexCount * 3)
        val normals = FloatArray(vertexCount * 3)
        val uvs = FloatArray(vertexCount * 2)
        val indices = IntArray(gridX * gridY * 6)

        var vIdx = 0
        for (iy in 0 until gridY1) {
            val y = iy * segmentHeight - heightHalf
            for (ix in 0 until gridX1) {
                val x = ix * segmentWidth - widthHalf
                positions[vIdx * 3] = x
                positions[vIdx * 3 + 1] = -y
                positions[vIdx * 3 + 2] = 0f

                normals[vIdx * 3] = 0f
                normals[vIdx * 3 + 1] = 0f
                normals[vIdx * 3 + 2] = 1f

                uvs[vIdx * 2] = ix.toFloat() / gridX
                uvs[vIdx * 2 + 1] = 1f - (iy.toFloat() / gridY)
                vIdx++
            }
        }

        var iIdx = 0
        for (iy in 0 until gridY) {
            for (ix in 0 until gridX) {
                val a = ix + gridX1 * iy
                val b = ix + gridX1 * (iy + 1)
                val c = (ix + 1) + gridX1 * (iy + 1)
                val d = (ix + 1) + gridX1 * iy

                indices[iIdx++] = a; indices[iIdx++] = b; indices[iIdx++] = d
                indices[iIdx++] = b; indices[iIdx++] = c; indices[iIdx++] = d
            }
        }

        setAttribute("position", BufferAttribute(positions, 3))
        setAttribute("normal", BufferAttribute(normals, 3))
        setAttribute("uv", BufferAttribute(uvs, 2))
        setIndex(indices)
        computeBoundingBox()
        computeBoundingSphere()
    }
}
