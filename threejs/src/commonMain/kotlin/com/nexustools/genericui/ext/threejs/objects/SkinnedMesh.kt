package com.nexustools.genericui.ext.threejs.objects

import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.materials.Material
import com.nexustools.genericui.ext.threejs.materials.MeshBasicMaterial
import com.nexustools.genericui.ext.threejs.math.Matrix4

/**
 * Skeletal-animated mesh (`THREE.SkinnedMesh`) deformed by a [Skeleton].
 */
class SkinnedMesh(
    geometry: BufferGeometry = BufferGeometry(),
    material: Material = MeshBasicMaterial()
) : Mesh(geometry, material) {
    override val type: String = "SkinnedMesh"

    var bindMode: String = "attached"
    val bindMatrix: Matrix4 = Matrix4()
    val bindMatrixInverse: Matrix4 = Matrix4()
    var skeleton: Skeleton? = null

    fun bind(skeleton: Skeleton, bindMatrix: Matrix4? = null) {
        this.skeleton = skeleton
        if (bindMatrix != null) {
            this.bindMatrix.copy(bindMatrix)
        } else {
            updateMatrixWorld(true)
            this.bindMatrix.copy(matrixWorld)
        }
        this.bindMatrixInverse.copy(this.bindMatrix).invert()
    }

    fun pose() {
        skeleton?.calculateInverses()
    }
}
