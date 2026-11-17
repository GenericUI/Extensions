package com.nexustools.genericui.ext.box2d

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2D rotation represented by sine (`s`) and cosine (`c`) (`b2Rot`).
 */
data class Rot(
    var s: Float = 0f,
    var c: Float = 1f
) {
    constructor(angleRadians: Float) : this(sin(angleRadians), cos(angleRadians))

    fun set(angleRadians: Float): Rot {
        s = sin(angleRadians)
        c = cos(angleRadians)
        return this
    }

    fun setIdentity(): Rot {
        s = 0f
        c = 1f
        return this
    }

    fun getAngle(): Float = atan2(s, c)

    fun getXAxis(): Vec2 = Vec2(c, s)

    fun getYAxis(): Vec2 = Vec2(-s, c)

    fun mulVec(v: Vec2): Vec2 = Vec2(
        c * v.x - s * v.y,
        s * v.x + c * v.y
    )

    fun mulTransVec(v: Vec2): Vec2 = Vec2(
        c * v.x + s * v.y,
        -s * v.x + c * v.y
    )
}
