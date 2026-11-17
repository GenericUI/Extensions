package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.geometries.BoxGeometry
import com.nexustools.genericui.ext.threejs.materials.Material
import com.nexustools.genericui.ext.threejs.materials.MeshStandardMaterial
import com.nexustools.genericui.ext.threejs.objects.Mesh

/**
 * Renders a Three.js [Mesh] synchronized with the owning entity's spatial transform.
 */
class MeshRenderer3DModule(
    geometry: BufferGeometry = BoxGeometry(1f, 1f, 1f),
    material: Material = MeshStandardMaterial()
) : Node3DBindingModule(object3D = Mesh(geometry, material)) {

    val mesh: Mesh
        get() = object3D as Mesh
}
