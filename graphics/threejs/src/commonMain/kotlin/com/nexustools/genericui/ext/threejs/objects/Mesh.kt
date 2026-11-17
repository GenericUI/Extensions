package com.nexustools.genericui.ext.threejs.objects

import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.materials.Material
import com.nexustools.genericui.ext.threejs.materials.MeshBasicMaterial

/**
 * Renderable triangular 3D mesh (`THREE.Mesh`) combining a [BufferGeometry] and a [Material].
 */
open class Mesh(
    var geometry: BufferGeometry = BufferGeometry(),
    var material: Material = MeshBasicMaterial()
) : Object3D() {
    override val type: String = "Mesh"
}
