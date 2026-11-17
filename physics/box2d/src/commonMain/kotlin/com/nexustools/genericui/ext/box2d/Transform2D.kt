package com.nexustools.genericui.ext.box2d

/**
 * 2D rigid transform (`b2Transform`) combining translation [p] and rotation [q].
 */
data class Transform2D(
    val p: Vec2 = Vec2(),
    val q: Rot = Rot()
) {
    fun setIdentity(): Transform2D {
        p.setZero()
        q.setIdentity()
        return this
    }

    fun set(position: Vec2, angleRadians: Float): Transform2D {
        p.copy(position)
        q.set(angleRadians)
        return this
    }

    fun mul(v: Vec2): Vec2 = Vec2(
        (q.c * v.x - q.s * v.y) + p.x,
        (q.s * v.x + q.c * v.y) + p.y
    )

    fun mulTrans(v: Vec2): Vec2 {
        val px = v.x - p.x
        val py = v.y - p.y
        return Vec2(
            q.c * px + q.s * py,
            -q.s * px + q.c * py
        )
    }
}
