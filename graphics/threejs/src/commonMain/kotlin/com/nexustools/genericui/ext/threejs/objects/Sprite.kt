package com.nexustools.genericui.ext.threejs.objects

import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.materials.Material
import com.nexustools.genericui.ext.threejs.materials.MeshBasicMaterial
import com.nexustools.genericui.ext.threejs.math.Vector2

/**
 * Camera-facing 2D billboard quad in 3D space (`THREE.Sprite`).
 */
class Sprite(
    var material: Material = MeshBasicMaterial()
) : Object3D() {
    override val type: String = "Sprite"
    val center: Vector2 = Vector2(0.5f, 0.5f)
}
