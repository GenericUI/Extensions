package com.nexustools.genericui.ext.threejs.objects

import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.materials.Material
import com.nexustools.genericui.ext.threejs.materials.MeshBasicMaterial
import com.nexustools.genericui.ext.threejs.math.Color
import com.nexustools.genericui.ext.threejs.math.Matrix4

/**
 * Hardware-instanced 3D mesh (`THREE.InstancedMesh`) rendering [count] instances in a single draw call.
 */
class InstancedMesh(
    geometry: BufferGeometry,
    material: Material = MeshBasicMaterial(),
    var count: Int = 1
) : Mesh(geometry, material) {
    override val type: String = "InstancedMesh"

    val instanceMatrix: FloatArray = FloatArray(count.coerceAtLeast(1) * 16).apply {
        for (i in 0 until count.coerceAtLeast(1)) {
            val base = i * 16
            this[base] = 1f
            this[base + 5] = 1f
            this[base + 10] = 1f
            this[base + 15] = 1f
        }
    }
    var instanceColor: FloatArray? = null

    fun setMatrixAt(index: Int, matrix: Matrix4) {
        if (index !in 0 until count) return
        matrix.elements.copyInto(instanceMatrix, destinationOffset = index * 16)
    }

    fun getMatrixAt(index: Int, target: Matrix4 = Matrix4()): Matrix4 {
        if (index !in 0 until count) return target.identity()
        instanceMatrix.copyInto(target.elements, 0, index * 16, index * 16 + 16)
        return target
    }

    fun setColorAt(index: Int, color: Color) {
        if (index !in 0 until count) return
        val colors = instanceColor ?: FloatArray(count * 3) { 1f }.also { instanceColor = it }
        val base = index * 3
        colors[base] = color.r
        colors[base + 1] = color.g
        colors[base + 2] = color.b
    }
}
