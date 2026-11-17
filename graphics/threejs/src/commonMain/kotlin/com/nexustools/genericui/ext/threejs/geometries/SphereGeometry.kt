package com.nexustools.genericui.ext.threejs.geometries

import com.nexustools.genericui.ext.threejs.core.BufferAttribute
import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * UV sphere geometry (`THREE.SphereGeometry`).
 */
class SphereGeometry(
    val radius: Float = 1f,
    val widthSegments: Int = 32,
    val heightSegments: Int = 16,
    val phiStart: Float = 0f,
    val phiLength: Float = (PI * 2.0).toFloat(),
    val thetaStart: Float = 0f,
    val thetaLength: Float = PI.toFloat()
) : BufferGeometry() {
    override val type: String = "SphereGeometry"

    init {
        val ws = widthSegments.coerceAtLeast(3)
        val hs = heightSegments.coerceAtLeast(2)
        val vertexCount = (ws + 1) * (hs + 1)
        val positions = FloatArray(vertexCount * 3)
        val normals = FloatArray(vertexCount * 3)
        val uvs = FloatArray(vertexCount * 2)
        val indexList = ArrayList<Int>(ws * hs * 6)

        var vIdx = 0
        for (iy in 0..hs) {
            val v = iy.toFloat() / hs
            val theta = thetaStart + v * thetaLength
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)

            for (ix in 0..ws) {
                val u = ix.toFloat() / ws
                val phi = phiStart + u * phiLength
                val nx = -cos(phi) * sinTheta
                val ny = cosTheta
                val nz = sin(phi) * sinTheta

                positions[vIdx * 3] = radius * nx
                positions[vIdx * 3 + 1] = radius * ny
                positions[vIdx * 3 + 2] = radius * nz

                normals[vIdx * 3] = nx
                normals[vIdx * 3 + 1] = ny
                normals[vIdx * 3 + 2] = nz

                uvs[vIdx * 2] = u
                uvs[vIdx * 2 + 1] = 1f - v
                vIdx++
            }
        }

        for (iy in 0 until hs) {
            for (ix in 0 until ws) {
                val a = iy * (ws + 1) + (ix + 1)
                val b = iy * (ws + 1) + ix
                val c = (iy + 1) * (ws + 1) + ix
                val d = (iy + 1) * (ws + 1) + (ix + 1)

                if (iy != 0 || thetaStart > 0f) {
                    indexList.add(a); indexList.add(b); indexList.add(d)
                }
                if (iy != hs - 1 || (thetaStart + thetaLength) < PI.toFloat()) {
                    indexList.add(b); indexList.add(c); indexList.add(d)
                }
            }
        }

        setAttribute("position", BufferAttribute(positions, 3))
        setAttribute("normal", BufferAttribute(normals, 3))
        setAttribute("uv", BufferAttribute(uvs, 2))
        setIndex(indexList.toIntArray())
        computeBoundingBox()
        computeBoundingSphere()
    }
}
