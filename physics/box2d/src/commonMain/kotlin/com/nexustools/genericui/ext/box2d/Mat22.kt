package com.nexustools.genericui.ext.box2d

/**
 * 2x2 column-major matrix (`b2Mat22`).
 */
data class Mat22(
    val ex: Vec2 = Vec2(1f, 0f),
    val ey: Vec2 = Vec2(0f, 1f)
) {
    fun setIdentity(): Mat22 {
        ex.set(1f, 0f)
        ey.set(0f, 1f)
        return this
    }

    fun setZero(): Mat22 {
        ex.setZero()
        ey.setZero()
        return this
    }

    fun getInverse(): Mat22 {
        val a = ex.x
        val b = ey.x
        val c = ex.y
        val d = ey.y
        var det = a * d - b * c
        if (det != 0f) {
            det = 1f / det
        }
        return Mat22(
            Vec2(det * d, -det * c),
            Vec2(-det * b, det * a)
        )
    }

    fun solve(b: Vec2): Vec2 {
        val a11 = ex.x
        val a12 = ey.x
        val a21 = ex.y
        val a22 = ey.y
        var det = a11 * a22 - a12 * a21
        if (det != 0f) {
            det = 1f / det
        }
        return Vec2(
            det * (a22 * b.x - a12 * b.y),
            det * (a11 * b.y - a21 * b.x)
        )
    }

    fun mul(v: Vec2): Vec2 = Vec2(
        ex.x * v.x + ey.x * v.y,
        ex.y * v.x + ey.y * v.y
    )
}
