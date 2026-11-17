package com.nexustools.genericui.ext.box3d

/**
 * 3x3 matrix for 3D rotational inertia tensors and basis transformations.
 */
class Mat33(
    var m00: Float = 1f, var m01: Float = 0f, var m02: Float = 0f,
    var m10: Float = 0f, var m11: Float = 1f, var m12: Float = 0f,
    var m20: Float = 0f, var m21: Float = 0f, var m22: Float = 1f
) {
    fun setDiagonal(x: Float, y: Float, z: Float): Mat33 {
        m00 = x; m01 = 0f; m02 = 0f
        m10 = 0f; m11 = y; m12 = 0f
        m20 = 0f; m21 = 0f; m22 = z
        return this
    }

    fun mulVec(v: Vec3): Vec3 = Vec3(
        m00 * v.x + m01 * v.y + m02 * v.z,
        m10 * v.x + m11 * v.y + m12 * v.z,
        m20 * v.x + m21 * v.y + m22 * v.z
    )

    fun clone(): Mat33 = Mat33(
        m00, m01, m02,
        m10, m11, m12,
        m20, m21, m22
    )

    companion object {
        fun diagonal(x: Float, y: Float, z: Float): Mat33 = Mat33().setDiagonal(x, y, z)
    }
}
