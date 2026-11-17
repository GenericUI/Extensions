package com.nexustools.genericui.ext.threejs.core

import com.nexustools.genericui.ext.threejs.math.Box3
import com.nexustools.genericui.ext.threejs.math.MathUtils
import com.nexustools.genericui.ext.threejs.math.Matrix4
import com.nexustools.genericui.ext.threejs.math.Sphere
import com.nexustools.genericui.ext.threejs.math.Vector3

/**
 * GPU-friendly mesh geometry container (`THREE.BufferGeometry`) storing named [BufferAttribute]s
 * (`position`, `normal`, `uv`, `color`, `skinIndex`, `skinWeight`) and optional triangle `indices`.
 */
open class BufferGeometry : EventDispatcher() {
    val id: Long = MathUtils.nextObjectId()
    val uuid: String = MathUtils.generateUUID()
    var name: String = ""
    open val type: String = "BufferGeometry"

    val attributes: MutableMap<String, BufferAttribute> = LinkedHashMap()
    var index: IntArray? = null

    var boundingBox: Box3? = null
    var boundingSphere: Sphere? = null

    fun setAttribute(name: String, attribute: BufferAttribute): BufferGeometry {
        attributes[name] = attribute
        return this
    }

    fun getAttribute(name: String): BufferAttribute? = attributes[name]

    fun deleteAttribute(name: String): BufferGeometry {
        attributes.remove(name)
        return this
    }

    fun setIndex(indices: IntArray?): BufferGeometry {
        this.index = indices
        return this
    }

    val vertexCount: Int
        get() = attributes["position"]?.count ?: 0

    fun computeVertexNormals() {
        val posAttr = attributes["position"] ?: return
        val normals = FloatArray(posAttr.array.size)
        val pA = Vector3()
        val pB = Vector3()
        val pC = Vector3()
        val cb = Vector3()
        val ab = Vector3()

        val idx = index
        if (idx != null) {
            var i = 0
            while (i + 2 < idx.size) {
                val vA = idx[i]
                val vB = idx[i + 1]
                val vC = idx[i + 2]
                posAttr.getVector3(vA, pA)
                posAttr.getVector3(vB, pB)
                posAttr.getVector3(vC, pC)

                cb.subVectors(pC, pB)
                ab.subVectors(pA, pB)
                cb.cross(ab)

                for (v in intArrayOf(vA, vB, vC)) {
                    normals[v * 3] += cb.x
                    normals[v * 3 + 1] += cb.y
                    normals[v * 3 + 2] += cb.z
                }
                i += 3
            }
        } else {
            var i = 0
            while (i + 2 < posAttr.count) {
                posAttr.getVector3(i, pA)
                posAttr.getVector3(i + 1, pB)
                posAttr.getVector3(i + 2, pC)

                cb.subVectors(pC, pB)
                ab.subVectors(pA, pB)
                cb.cross(ab)

                for (v in i..(i + 2)) {
                    normals[v * 3] += cb.x
                    normals[v * 3 + 1] += cb.y
                    normals[v * 3 + 2] += cb.z
                }
                i += 3
            }
        }

        val normalAttr = BufferAttribute(normals, 3)
        val n = Vector3()
        for (i in 0 until normalAttr.count) {
            normalAttr.getVector3(i, n).normalize()
            normalAttr.setXYZ(i, n.x, n.y, n.z)
        }
        setAttribute("normal", normalAttr)
    }

    fun computeBoundingBox() {
        val box = boundingBox ?: Box3().also { boundingBox = it }
        box.makeEmpty()
        val pos = attributes["position"] ?: return
        val v = Vector3()
        for (i in 0 until pos.count) {
            box.expandByPoint(pos.getVector3(i, v))
        }
    }

    fun computeBoundingSphere() {
        if (boundingBox == null) computeBoundingBox()
        val box = boundingBox ?: return
        val sphere = boundingSphere ?: Sphere().also { boundingSphere = it }
        box.getCenter(sphere.center)
        val pos = attributes["position"] ?: return
        var maxRadiusSq = 0f
        val v = Vector3()
        for (i in 0 until pos.count) {
            val dSq = sphere.center.distanceToSquared(pos.getVector3(i, v))
            if (dSq > maxRadiusSq) maxRadiusSq = dSq
        }
        sphere.radius = kotlin.math.sqrt(maxRadiusSq)
    }

    fun applyMatrix4(matrix: Matrix4): BufferGeometry {
        val pos = attributes["position"]
        if (pos != null) {
            val v = Vector3()
            for (i in 0 until pos.count) {
                pos.getVector3(i, v).applyMatrix4(matrix)
                pos.setXYZ(i, v.x, v.y, v.z)
            }
            pos.needsUpdate = true
        }
        computeBoundingBox()
        computeBoundingSphere()
        return this
    }

    fun dispose() {
        dispatchEvent("dispose")
    }
}
