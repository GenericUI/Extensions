package com.nexustools.genericui.ext.threejs.math

/**
 * 2D Axis-Aligned Bounding Box (`THREE.Box2`).
 */
data class Box2(
    val min: Vector2 = Vector2(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    val max: Vector2 = Vector2(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
) {
    fun set(min: Vector2, max: Vector2): Box2 {
        this.min.copy(min)
        this.max.copy(max)
        return this
    }

    fun makeEmpty(): Box2 {
        min.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        max.set(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        return this
    }

    fun isEmpty(): Boolean = (max.x < min.x) || (max.y < min.y)

    fun getCenter(target: Vector2 = Vector2()): Vector2 =
        if (isEmpty()) target.set(0f, 0f) else target.addVectors(min, max).multiplyScalar(0.5f)

    fun getSize(target: Vector2 = Vector2()): Vector2 =
        if (isEmpty()) target.set(0f, 0f) else target.subVectors(max, min)

    fun expandByPoint(point: Vector2): Box2 {
        min.x = minOf(min.x, point.x)
        min.y = minOf(min.y, point.y)
        max.x = maxOf(max.x, point.x)
        max.y = maxOf(max.y, point.y)
        return this
    }

    fun containsPoint(point: Vector2): Boolean =
        point.x in min.x..max.x && point.y in min.y..max.y

    fun intersectsBox(box: Box2): Boolean =
        box.max.x >= min.x && box.min.x <= max.x && box.max.y >= min.y && box.min.y <= max.y
}
