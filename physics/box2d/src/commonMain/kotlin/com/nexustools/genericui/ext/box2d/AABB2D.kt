package com.nexustools.genericui.ext.box2d

/**
 * 2D Axis-Aligned Bounding Box (`b2AABB`).
 */
data class AABB2D(
    val lowerBound: Vec2 = Vec2(),
    val upperBound: Vec2 = Vec2()
) {
    fun isValid(): Boolean {
        val dx = upperBound.x - lowerBound.x
        val dy = upperBound.y - lowerBound.y
        return dx >= 0f && dy >= 0f && !dx.isNaN() && !dy.isNaN()
    }

    fun getCenter(): Vec2 = Vec2(
        0.5f * (lowerBound.x + upperBound.x),
        0.5f * (lowerBound.y + upperBound.y)
    )

    fun getExtents(): Vec2 = Vec2(
        0.5f * (upperBound.x - lowerBound.x),
        0.5f * (upperBound.y - lowerBound.y)
    )

    fun getPerimeter(): Float {
        val wx = upperBound.x - lowerBound.x
        val wy = upperBound.y - lowerBound.y
        return 2f * (wx + wy)
    }

    fun combine(aabb: AABB2D): AABB2D {
        lowerBound.x = minOf(lowerBound.x, aabb.lowerBound.x)
        lowerBound.y = minOf(lowerBound.y, aabb.lowerBound.y)
        upperBound.x = maxOf(upperBound.x, aabb.upperBound.x)
        upperBound.y = maxOf(upperBound.y, aabb.upperBound.y)
        return this
    }

    fun contains(aabb: AABB2D): Boolean =
        lowerBound.x <= aabb.lowerBound.x &&
            lowerBound.y <= aabb.lowerBound.y &&
            aabb.upperBound.x <= upperBound.x &&
            aabb.upperBound.y <= upperBound.y

    fun overlaps(other: AABB2D): Boolean =
        !(other.lowerBound.x > upperBound.x ||
            other.upperBound.x < lowerBound.x ||
            other.lowerBound.y > upperBound.y ||
            other.upperBound.y < lowerBound.y)
}
