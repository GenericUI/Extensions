package com.nexustools.genericui.ext.threejs.math

import kotlin.math.sqrt

/**
 * 3D geometric ray (`THREE.Ray`) defined by an [origin] and unit [direction].
 */
data class Ray(
    val origin: Vector3 = Vector3(),
    val direction: Vector3 = Vector3(0f, 0f, -1f)
) {
    fun set(origin: Vector3, direction: Vector3): Ray {
        this.origin.copy(origin)
        this.direction.copy(direction)
        return this
    }

    fun copy(ray: Ray): Ray = set(ray.origin, ray.direction)

    fun clone(): Ray = Ray(origin.clone(), direction.clone())

    fun at(t: Float, target: Vector3 = Vector3()): Vector3 =
        target.copy(origin).addScaledVector(direction, t)

    fun intersectSphere(sphere: Sphere, target: Vector3 = Vector3()): Vector3? {
        val v1 = Vector3().subVectors(sphere.center, origin)
        val tca = v1.dot(direction)
        val d2 = v1.dot(v1) - tca * tca
        val radius2 = sphere.radius * sphere.radius
        if (d2 > radius2) return null

        val thc = sqrt(radius2 - d2)
        val t0 = tca - thc
        val t1 = tca + thc
        if (t1 < 0f) return null
        return if (t0 < 0f) at(t1, target) else at(t0, target)
    }

    fun intersectPlane(plane: Plane, target: Vector3 = Vector3()): Vector3? {
        val denominator = plane.normal.dot(direction)
        if (denominator == 0f) {
            if (plane.distanceToPoint(origin) == 0f) {
                return target.copy(origin)
            }
            return null
        }
        val t = -(origin.dot(plane.normal) + plane.constant) / denominator
        return if (t >= 0f) at(t, target) else null
    }

    fun intersectBox(box: Box3, target: Vector3 = Vector3()): Vector3? {
        val invdirx = 1f / direction.x
        val invdiry = 1f / direction.y
        val invdirz = 1f / direction.z

        var tmin: Float
        var tmax: Float

        if (invdirx >= 0f) {
            tmin = (box.min.x - origin.x) * invdirx
            tmax = (box.max.x - origin.x) * invdirx
        } else {
            tmin = (box.max.x - origin.x) * invdirx
            tmax = (box.min.x - origin.x) * invdirx
        }

        val tymin: Float
        val tymax: Float
        if (invdiry >= 0f) {
            tymin = (box.min.y - origin.y) * invdiry
            tymax = (box.max.y - origin.y) * invdiry
        } else {
            tymin = (box.max.y - origin.y) * invdiry
            tymax = (box.min.y - origin.y) * invdiry
        }

        if ((tmin > tymax) || (tymin > tmax)) return null
        if (tymin > tmin || tmin.isNaN()) tmin = tymin
        if (tymax < tmax || tmax.isNaN()) tmax = tymax

        val tzmin: Float
        val tzmax: Float
        if (invdirz >= 0f) {
            tzmin = (box.min.z - origin.z) * invdirz
            tzmax = (box.max.z - origin.z) * invdirz
        } else {
            tzmin = (box.max.z - origin.z) * invdirz
            tzmax = (box.min.z - origin.z) * invdirz
        }

        if ((tmin > tzmax) || (tzmin > tmax)) return null
        if (tzmin > tmin || tmin.isNaN()) tmin = tzmin
        if (tzmax < tmax || tmax.isNaN()) tmax = tzmax

        if (tmax < 0f) return null
        return at(if (tmin >= 0f) tmin else tmax, target)
    }

    fun intersectTriangle(
        a: Vector3,
        b: Vector3,
        c: Vector3,
        backfaceCulling: Boolean = false,
        target: Vector3 = Vector3()
    ): Vector3? {
        val edge1 = Vector3().subVectors(b, a)
        val edge2 = Vector3().subVectors(c, a)
        val normal = Vector3().crossVectors(edge1, edge2)

        var DdN = direction.dot(normal)
        val sign: Float
        if (DdN > 0f) {
            if (backfaceCulling) return null
            sign = 1f
        } else if (DdN < 0f) {
            sign = -1f
            DdN = -DdN
        } else {
            return null
        }

        val diff = Vector3().subVectors(origin, a)
        val DdQxE2 = sign * direction.dot(Vector3().crossVectors(diff, edge2))
        if (DdQxE2 < 0f) return null

        val DdE1xQ = sign * direction.dot(Vector3().crossVectors(edge1, diff))
        if (DdE1xQ < 0f) return null
        if (DdQxE2 + DdE1xQ > DdN) return null

        val QdN = -sign * diff.dot(normal)
        if (QdN < 0f) return null

        return at(QdN / DdN, target)
    }

    fun applyMatrix4(matrix4: Matrix4): Ray {
        origin.applyMatrix4(matrix4)
        direction.transformDirection(matrix4)
        return this
    }
}
