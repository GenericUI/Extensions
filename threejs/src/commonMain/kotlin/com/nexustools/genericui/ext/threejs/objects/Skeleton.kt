package com.nexustools.genericui.ext.threejs.objects

import com.nexustools.genericui.ext.threejs.math.Matrix4

/**
 * Hierarchical bone rig (`THREE.Skeleton`) computing skinning bone matrices for a [SkinnedMesh].
 */
class Skeleton(
    val bones: List<Bone> = emptyList(),
    boneInverses: List<Matrix4>? = null
) {
    val boneInverses: List<Matrix4> = boneInverses ?: bones.map { Matrix4() }
    val boneMatrices: FloatArray = FloatArray(bones.size * 16)

    init {
        if (boneInverses == null) {
            calculateInverses()
        }
    }

    fun calculateInverses() {
        for (i in bones.indices) {
            bones[i].updateMatrixWorld(true)
            boneInverses[i].copy(bones[i].matrixWorld).invert()
        }
    }

    fun update() {
        val offsetMatrix = Matrix4()
        for (i in bones.indices) {
            val matrix = bones[i].matrixWorld
            offsetMatrix.multiplyMatrices(matrix, boneInverses[i])
            offsetMatrix.elements.copyInto(boneMatrices, destinationOffset = i * 16)
        }
    }

    fun getBoneByName(name: String): Bone? =
        bones.firstOrNull { it.name == name }
}
