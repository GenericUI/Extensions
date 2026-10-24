package com.nexustools.genericui.ext.threejs.geometries

import com.nexustools.genericui.ext.threejs.core.BufferAttribute
import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Torus / donut mesh geometry (`THREE.TorusGeometry`).
 */
class TorusGeometry(
    val radius: Float = 1f,
    val tube: Float = 0.4f,
    val radialSegments: Int = 16,
    val tubularSegments: Int = 48,
    val arc: Float = (PI * 2.0).toFloat()
) : BufferGeometry() {
    override val type: String = "TorusGeometry"

    init {
        val rs = radialSegments.coerceAtLeast(3)
        val ts = tubularSegments.coerceAtLeast(3)
        val vertexCount = (rs + 1) * (ts + 1)
        val positions = FloatArray(vertexCount * 3)
        val normals = FloatArray(vertexCount * 3)
        val uvs = FloatArray(vertexCount * 2)
        val indices = IntArray(rs * ts * 6)

        val center = Vector3()
        val vertex = Vector3()
        val normal = Vector3()

        var vIdx = 0
        for (j in 0..rs) {
            for (i in 0..ts) {
                val u = (i.toFloat() / ts) * arc
                val v = (j.toFloat() / rs) * (PI * 2.0).toFloat()

                vertex.x = (radius + tube * cos(v)) * cos(u)
                vertex.y = (radius + tube * cos(v)) * sin(u)
                vertex.z = tube * sin(v)

                positions[vIdx * 3] = vertex.x
                positions[vIdx * 3 + 1] = vertex.y
                positions[vIdx * 3 + 2] = vertex.z

                center.x = radius * cos(u)
                center.y = radius * sin(u)
                center.z = 0f
                normal.subVectors(vertex, center).normalize()

                normals[vIdx * 3] = normal.x
                normals[vIdx * 3 + 1] = normal.y
                normals[vIdx * 3 + 2] = normal.z

                uvs[vIdx * 2] = i.toFloat() / ts
                uvs[vIdx * 2 + 1] = j.toFloat() / rs
                vIdx++
            }
        }

        var iIdx = 0
        for (j in 1..rs) {
            for (i in 1..ts) {
                val a = (ts + 1) * j + i - 1
                val b = (ts + 1) * (j - 1) + i - 1
                val c = (ts + 1) * (j - 1) + i
                val d = (ts + 1) * j + i

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
