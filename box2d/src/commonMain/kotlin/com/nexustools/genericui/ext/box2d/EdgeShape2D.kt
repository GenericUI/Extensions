package com.nexustools.genericui.ext.box2d

/**
 * 2D two-sided line segment collision shape (`b2EdgeShape`).
 */
class EdgeShape2D(
    val vertex1: Vec2 = Vec2(),
    val vertex2: Vec2 = Vec2()
) : Shape2D(ShapeType2D.EDGE, 0.01f) {

    fun setTwoSided(v1: Vec2, v2: Vec2): EdgeShape2D {
        vertex1.copy(v1)
        vertex2.copy(v2)
        return this
    }

    override fun clone(): EdgeShape2D = EdgeShape2D(vertex1.clone(), vertex2.clone())

    override fun testPoint(xf: Transform2D, p: Vec2): Boolean = false

    override fun rayCast(output: RayCastOutput2D, input: RayCastInput2D, transform: Transform2D): Boolean {
        val p1 = transform.mulTrans(input.p1)
        val p2 = transform.mulTrans(input.p2)
        val d = p2 - p1
        val v1 = vertex1
        val v2 = vertex2
        val e = v2 - v1
        val normal = Vec2(e.y, -e.x)
        normal.normalize()

        val numerator = normal.dot(v1 - p1)
        val denominator = normal.dot(d)
        if (denominator == 0f) return false

        val t = numerator / denominator
        if (t < 0f || input.maxFraction < t) return false

        val q = p1 + d * t
        val r = v2 - v1
        val rr = r.dot(r)
        if (rr == 0f) return false
        val s = (q - v1).dot(r) / rr
        if (s < 0f || 1f < s) return false

        output.fraction = t
        if (numerator > 0f) {
            output.normal.copy(transform.q.mulVec(-normal))
        } else {
            output.normal.copy(transform.q.mulVec(normal))
        }
        return true
    }

    override fun computeAABB(aabb: AABB2D, xf: Transform2D) {
        val v1 = xf.mul(vertex1)
        val v2 = xf.mul(vertex2)
        aabb.lowerBound.set(minOf(v1.x, v2.x) - radius, minOf(v1.y, v2.y) - radius)
        aabb.upperBound.set(maxOf(v1.x, v2.x) + radius, maxOf(v1.y, v2.y) + radius)
    }

    override fun computeMass(density: Float): MassData2D =
        MassData2D(0f, (vertex1 + vertex2) * 0.5f, 0f)
}
