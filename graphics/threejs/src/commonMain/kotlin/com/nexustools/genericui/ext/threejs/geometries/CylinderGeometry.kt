package com.nexustools.genericui.ext.threejs.geometries

import com.nexustools.genericui.ext.threejs.core.BufferAttribute
import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cylindrical / frustum mesh geometry (`THREE.CylinderGeometry`).
 */
open class CylinderGeometry(
    val radiusTop: Float = 1f,
    val radiusBottom: Float = 1f,
    val height: Float = 1f,
    val radialSegments: Int = 32,
    val heightSegments: Int = 1,
    val openEnded: Boolean = false,
    val thetaStart: Float = 0f,
    val thetaLength: Float = (PI * 2.0).toFloat()
) : BufferGeometry() {
    override val type: String = "CylinderGeometry"

    init {
        val rs = radialSegments.coerceAtLeast(3)
        val hs = heightSegments.coerceAtLeast(1)
        val posList = ArrayList<Float>()
        val normList = ArrayList<Float>()
        val uvList = ArrayList<Float>()
        val idxList = ArrayList<Int>()

        val halfHeight = height * 0.5f
        val slope = (radiusBottom - radiusTop) / height
        val n = Vector3()

        for (y in 0..hs) {
            val v = y.toFloat() / hs
            val currY = halfHeight - v * height
            val radius = v * (radiusBottom - radiusTop) + radiusTop
            for (x in 0..rs) {
                val u = x.toFloat() / rs
                val theta = u * thetaLength + thetaStart
                val sinT = sin(theta)
                val cosT = cos(theta)

                posList.add(radius * sinT)
                posList.add(currY)
                posList.add(radius * cosT)

                n.set(sinT, slope, cosT).normalize()
                normList.add(n.x)
                normList.add(n.y)
                normList.add(n.z)

                uvList.add(u)
                uvList.add(1f - v)
            }
        }

        for (x in 0 until rs) {
            for (y in 0 until hs) {
                val a = y * (rs + 1) + x
                val b = (y + 1) * (rs + 1) + x
                val c = (y + 1) * (rs + 1) + (x + 1)
                val d = y * (rs + 1) + (x + 1)
                idxList.add(a); idxList.add(b); idxList.add(d)
                idxList.add(b); idxList.add(c); idxList.add(d)
            }
        }

        setAttribute("position", BufferAttribute(posList.toFloatArray(), 3))
        setAttribute("normal", BufferAttribute(normList.toFloatArray(), 3))
        setAttribute("uv", BufferAttribute(uvList.toFloatArray(), 2))
        setIndex(idxList.toIntArray())
        computeBoundingBox()
        computeBoundingSphere()
    }
}
