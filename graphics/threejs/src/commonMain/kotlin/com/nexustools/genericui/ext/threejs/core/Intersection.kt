package com.nexustools.genericui.ext.threejs.core

import com.nexustools.genericui.ext.threejs.math.Vector2
import com.nexustools.genericui.ext.threejs.math.Vector3

/**
 * Raycast hit descriptor (`THREE.Intersection`) returned by [Raycaster].
 */
data class Intersection(
    val distance: Float,
    val point: Vector3,
    val object3D: Object3D,
    val faceIndex: Int? = null,
    val uv: Vector2? = null,
    val normal: Vector3? = null,
    val instanceId: Int? = null
)
