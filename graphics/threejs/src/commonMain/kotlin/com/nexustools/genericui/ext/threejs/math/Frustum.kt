package com.nexustools.genericui.ext.threejs.math

/**
 * 6-plane camera view frustum (`THREE.Frustum`) for view-frustum culling.
 */
class Frustum(
    p0: Plane = Plane(),
    p1: Plane = Plane(),
    p2: Plane = Plane(),
    p3: Plane = Plane(),
    p4: Plane = Plane(),
    p5: Plane = Plane()
) {
    val planes: Array<Plane> = arrayOf(p0, p1, p2, p3, p4, p5)

    fun setFromProjectionMatrix(m: Matrix4): Frustum {
        val me = m.elements
        val me0 = me[0]; val me1 = me[1]; val me2 = me[2]; val me3 = me[3]
        val me4 = me[4]; val me5 = me[5]; val me6 = me[6]; val me7 = me[7]
        val me8 = me[8]; val me9 = me[9]; val me10 = me[10]; val me11 = me[11]
        val me12 = me[12]; val me13 = me[13]; val me14 = me[14]; val me15 = me[15]

        planes[0].setComponents(me3 - me0, me7 - me4, me11 - me8, me15 - me12).normalize()
        planes[1].setComponents(me3 + me0, me7 + me4, me11 + me8, me15 + me12).normalize()
        planes[2].setComponents(me3 + me1, me7 + me5, me11 + me9, me15 + me13).normalize()
        planes[3].setComponents(me3 - me1, me7 - me5, me11 - me9, me15 - me13).normalize()
        planes[4].setComponents(me3 - me2, me7 - me6, me11 - me10, me15 - me14).normalize()
        planes[5].setComponents(me3 + me2, me7 + me6, me11 + me10, me15 + me14).normalize()
        return this
    }

    fun intersectsSphere(sphere: Sphere): Boolean {
        val center = sphere.center
        val negRadius = -sphere.radius
        for (plane in planes) {
            val distance = plane.distanceToPoint(center)
            if (distance < negRadius) return false
        }
        return true
    }

    fun intersectsBox(box: Box3): Boolean {
        val p = Vector3()
        for (plane in planes) {
            p.x = if (plane.normal.x > 0f) box.max.x else box.min.x
            p.y = if (plane.normal.y > 0f) box.max.y else box.min.y
            p.z = if (plane.normal.z > 0f) box.max.z else box.min.z
            if (plane.distanceToPoint(p) < 0f) return false
        }
        return true
    }

    fun containsPoint(point: Vector3): Boolean {
        for (plane in planes) {
            if (plane.distanceToPoint(point) < 0f) return false
        }
        return true
    }
}
