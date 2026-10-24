package com.nexustools.genericui.ext.threejs.math

/**
 * 3D Axis-Aligned Bounding Box (`THREE.Box3`).
 */
data class Box3(
    val min: Vector3 = Vector3(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    val max: Vector3 = Vector3(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
) {
    fun set(min: Vector3, max: Vector3): Box3 {
        this.min.copy(min)
        this.max.copy(max)
        return this
    }

    fun makeEmpty(): Box3 {
        min.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        max.set(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        return this
    }

    fun isEmpty(): Boolean = (max.x < min.x) || (max.y < min.y) || (max.z < min.z)

    fun copy(box: Box3): Box3 = set(box.min, box.max)

    fun clone(): Box3 = Box3(min.clone(), max.clone())

    fun getCenter(target: Vector3 = Vector3()): Vector3 =
        if (isEmpty()) target.set(0f, 0f, 0f) else target.addVectors(min, max).multiplyScalar(0.5f)

    fun getSize(target: Vector3 = Vector3()): Vector3 =
        if (isEmpty()) target.set(0f, 0f, 0f) else target.subVectors(max, min)

    fun expandByPoint(point: Vector3): Box3 {
        min.min(point)
        max.max(point)
        return this
    }

    fun expandByScalar(scalar: Float): Box3 {
        min.addScalar(-scalar)
        max.addScalar(scalar)
        return this
    }

    fun containsPoint(point: Vector3): Boolean =
        point.x in min.x..max.x && point.y in min.y..max.y && point.z in min.z..max.z

    fun intersectsBox(box: Box3): Boolean =
        box.max.x >= min.x && box.min.x <= max.x &&
            box.max.y >= min.y && box.min.y <= max.y &&
            box.max.z >= min.z && box.min.z <= max.z

    fun union(box: Box3): Box3 {
        min.min(box.min)
        max.max(box.max)
        return this
    }

    fun applyMatrix4(matrix: Matrix4): Box3 {
        if (isEmpty()) return this
        val points = arrayOf(
            Vector3(min.x, min.y, min.z),
            Vector3(min.x, min.y, max.z),
            Vector3(min.x, max.y, min.z),
            Vector3(min.x, max.y, max.z),
            Vector3(max.x, min.y, min.z),
            Vector3(max.x, min.y, max.z),
            Vector3(max.x, max.y, min.z),
            Vector3(max.x, max.y, max.z)
        )
        makeEmpty()
        for (p in points) {
            expandByPoint(p.applyMatrix4(matrix))
        }
        return this
    }
}
