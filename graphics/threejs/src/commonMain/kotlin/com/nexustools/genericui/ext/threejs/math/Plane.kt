package com.nexustools.genericui.ext.threejs.math

/**
 * Infinite 3D plane (`THREE.Plane`) in Hessian normal form (`normal.dot(p) + constant = 0`).
 */
data class Plane(
    val normal: Vector3 = Vector3(1f, 0f, 0f),
    var constant: Float = 0f
) {
    fun set(normal: Vector3, constant: Float): Plane {
        this.normal.copy(normal)
        this.constant = constant
        return this
    }

    fun setComponents(x: Float, y: Float, z: Float, w: Float): Plane {
        normal.set(x, y, z)
        constant = w
        return this
    }

    fun setFromNormalAndCoplanarPoint(normal: Vector3, point: Vector3): Plane {
        this.normal.copy(normal)
        this.constant = -point.dot(this.normal)
        return this
    }

    fun copy(plane: Plane): Plane = set(plane.normal, plane.constant)

    fun clone(): Plane = Plane(normal.clone(), constant)

    fun normalize(): Plane {
        val len = normal.length()
        if (len > 0f) {
            val invLen = 1f / len
            normal.multiplyScalar(invLen)
            constant *= invLen
        }
        return this
    }

    fun distanceToPoint(point: Vector3): Float =
        normal.dot(point) + constant

    fun distanceToSphere(sphere: Sphere): Float =
        distanceToPoint(sphere.center) - sphere.radius

    fun projectPoint(point: Vector3, target: Vector3 = Vector3()): Vector3 =
        target.copy(point).addScaledVector(normal, -distanceToPoint(point))
}
