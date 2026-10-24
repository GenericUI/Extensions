package com.nexustools.genericui.ext.box3d

/**
 * 3D ray defined by `origin`, normalized `direction`, and `maxDistance`.
 */
data class Ray3D(
    val origin: Vec3 = Vec3(),
    val direction: Vec3 = Vec3(0f, 0f, -1f),
    val maxDistance: Float = 1000f
) {
    fun pointAt(t: Float): Vec3 = origin + direction * t
}
