package com.nexustools.genericui.ext.threejs.geometries

import com.nexustools.genericui.ext.threejs.core.BufferAttribute
import com.nexustools.genericui.ext.threejs.core.BufferGeometry

/**
 * 3D rectangular prism geometry (`THREE.BoxGeometry`) with 24 vertices (positions, normals, UVs)
 * and 36 triangle indices.
 */
class BoxGeometry(
    val width: Float = 1f,
    val height: Float = 1f,
    val depth: Float = 1f,
    val widthSegments: Int = 1,
    val heightSegments: Int = 1,
    val depthSegments: Int = 1
) : BufferGeometry() {
    override val type: String = "BoxGeometry"

    init {
        val hx = width * 0.5f
        val hy = height * 0.5f
        val hz = depth * 0.5f

        val positions = floatArrayOf(
            // +Z front
            -hx, -hy, hz, hx, -hy, hz, hx, hy, hz, -hx, hy, hz,
            // -Z back
            hx, -hy, -hz, -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz,
            // +Y top
            -hx, hy, hz, hx, hy, hz, hx, hy, -hz, -hx, hy, -hz,
            // -Y bottom
            -hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz,
            // +X right
            hx, -hy, hz, hx, -hy, -hz, hx, hy, -hz, hx, hy, hz,
            // -X left
            -hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz
        )

        val normals = floatArrayOf(
            0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f,
            0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f,
            0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f,
            0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f,
            1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f,
            -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f
        )

        val uvs = FloatArray(48)
        for (face in 0 until 6) {
            val base = face * 8
            uvs[base] = 0f; uvs[base + 1] = 0f
            uvs[base + 2] = 1f; uvs[base + 3] = 0f
            uvs[base + 4] = 1f; uvs[base + 5] = 1f
            uvs[base + 6] = 0f; uvs[base + 7] = 1f
        }

        val indices = IntArray(36)
        for (face in 0 until 6) {
            val v = face * 4
            val idx = face * 6
            indices[idx] = v
            indices[idx + 1] = v + 1
            indices[idx + 2] = v + 2
            indices[idx + 3] = v
            indices[idx + 4] = v + 2
            indices[idx + 5] = v + 3
        }

        setAttribute("position", BufferAttribute(positions, 3))
        setAttribute("normal", BufferAttribute(normals, 3))
        setAttribute("uv", BufferAttribute(uvs, 2))
        setIndex(indices)
        computeBoundingBox()
        computeBoundingSphere()
    }
}
