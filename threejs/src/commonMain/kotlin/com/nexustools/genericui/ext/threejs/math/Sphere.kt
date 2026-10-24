package com.nexustools.genericui.ext.threejs.math

/**
 * 3D bounding sphere (`THREE.Sphere`).
 */
data class Sphere(
    val center: Vector3 = Vector3(),
    var radius: Float = -1f
) {
    fun set(center: Vector3, radius: Float): Sphere {
        this.center.copy(center)
        this.radius = radius
        return this
    }

    fun copy(sphere: Sphere): Sphere = set(sphere.center, sphere.radius)

    fun clone(): Sphere = Sphere(center.clone(), radius)

    fun isEmpty(): Boolean = radius < 0f

    fun makeEmpty(): Sphere {
        center.set(0f, 0f, 0f)
        radius = -1f
        return this
    }

    fun containsPoint(point: Vector3): Boolean =
        point.distanceToSquared(center) <= radius * radius

    fun distanceToPoint(point: Vector3): Float =
        point.distanceTo(center) - radius

    fun intersectsSphere(sphere: Sphere): Boolean {
        val radiusSum = radius + sphere.radius
        return sphere.center.distanceToSquared(center) <= radiusSum * radiusSum
    }

    fun intersectsBox(box: Box3): Boolean {
        val clamped = Vector3(
            center.x.coerceIn(box.min.x, box.max.x),
            center.y.coerceIn(box.min.y, box.max.y),
            center.z.coerceIn(box.min.z, box.max.z)
        )
        return clamped.distanceToSquared(center) <= radius * radius
    }

    fun applyMatrix4(matrix: Matrix4): Sphere {
        center.applyMatrix4(matrix)
        radius *= matrix.getMaxScaleOnAxis()
        return this
    }
}
