package com.nexustools.genericui.ext.box2d

/**
 * Convex 2D polygon / box collision shape (`b2PolygonShape`).
 */
class PolygonShape2D : Shape2D(ShapeType2D.POLYGON, 0.01f) {
    val vertices: MutableList<Vec2> = mutableListOf()
    val normals: MutableList<Vec2> = mutableListOf()
    val centroid: Vec2 = Vec2()

    fun setAsBox(halfWidth: Float, halfHeight: Float): PolygonShape2D {
        vertices.clear()
        normals.clear()
        vertices.add(Vec2(-halfWidth, -halfHeight))
        vertices.add(Vec2(halfWidth, -halfHeight))
        vertices.add(Vec2(halfWidth, halfHeight))
        vertices.add(Vec2(-halfWidth, halfHeight))

        normals.add(Vec2(0f, -1f))
        normals.add(Vec2(1f, 0f))
        normals.add(Vec2(0f, 1f))
        normals.add(Vec2(-1f, 0f))
        centroid.setZero()
        return this
    }

    fun setAsBox(halfWidth: Float, halfHeight: Float, center: Vec2, angleRadians: Float): PolygonShape2D {
        setAsBox(halfWidth, halfHeight)
        val xf = Transform2D(center, Rot(angleRadians))
        centroid.copy(center)
        for (i in vertices.indices) {
            vertices[i] = xf.mul(vertices[i])
            normals[i] = xf.q.mulVec(normals[i])
        }
        return this
    }

    fun set(points: List<Vec2>): PolygonShape2D {
        vertices.clear()
        normals.clear()
        vertices.addAll(points.map { it.clone() })
        val n = vertices.size
        for (i in 0 until n) {
            val i2 = if (i + 1 < n) i + 1 else 0
            val edge = vertices[i2] - vertices[i]
            val normal = Vec2.cross(edge, 1f)
            normal.normalize()
            normals.add(normal)
        }
        var cx = 0f
        var cy = 0f
        if (n > 0) {
            for (v in vertices) {
                cx += v.x
                cy += v.y
            }
            centroid.set(cx / n, cy / n)
        }
        return this
    }

    override fun clone(): PolygonShape2D {
        val copy = PolygonShape2D()
        copy.vertices.addAll(vertices.map { it.clone() })
        copy.normals.addAll(normals.map { it.clone() })
        copy.centroid.copy(centroid)
        return copy
    }

    override fun testPoint(xf: Transform2D, p: Vec2): Boolean {
        val pLocal = xf.mulTrans(p)
        for (i in vertices.indices) {
            val dot = normals[i].dot(pLocal - vertices[i])
            if (dot > 0f) return false
        }
        return true
    }

    override fun rayCast(output: RayCastOutput2D, input: RayCastInput2D, transform: Transform2D): Boolean {
        val p1 = transform.mulTrans(input.p1)
        val p2 = transform.mulTrans(input.p2)
        val d = p2 - p1
        var lower = 0f
        var upper = input.maxFraction
        var index = -1

        for (i in vertices.indices) {
            val numerator = normals[i].dot(vertices[i] - p1)
            val denominator = normals[i].dot(d)
            if (denominator == 0f) {
                if (numerator < 0f) return false
            } else {
                if (denominator < 0f && numerator < lower * denominator) {
                    lower = numerator / denominator
                    index = i
                } else if (denominator > 0f && numerator < upper * denominator) {
                    upper = numerator / denominator
                }
            }
            if (upper < lower) return false
        }

        if (index >= 0) {
            output.fraction = lower
            output.normal.copy(transform.q.mulVec(normals[index]))
            return true
        }
        return false
    }

    override fun computeAABB(aabb: AABB2D, xf: Transform2D) {
        if (vertices.isEmpty()) {
            aabb.lowerBound.copy(xf.p)
            aabb.upperBound.copy(xf.p)
            return
        }
        val first = xf.mul(vertices[0])
        val lower = first.clone()
        val upper = first.clone()
        for (i in 1 until vertices.size) {
            val v = xf.mul(vertices[i])
            lower.x = minOf(lower.x, v.x)
            lower.y = minOf(lower.y, v.y)
            upper.x = maxOf(upper.x, v.x)
            upper.y = maxOf(upper.y, v.y)
        }
        aabb.lowerBound.set(lower.x - radius, lower.y - radius)
        aabb.upperBound.set(upper.x + radius, upper.y + radius)
    }

    override fun computeMass(density: Float): MassData2D {
        if (vertices.size < 3) return MassData2D(0f, centroid.clone(), 0f)
        var area = 0f
        var I = 0f
        val s = vertices[0]
        val kInv3 = 1f / 3f

        for (i in vertices.indices) {
            val e1 = vertices[i] - s
            val e2 = vertices[(i + 1) % vertices.size] - s
            val D = Vec2.cross(e1, e2)
            val triangleArea = 0.5f * D
            area += triangleArea
            val intx2 = e1.x * e1.x + e2.x * e1.x + e2.x * e2.x
            val inty2 = e1.y * e1.y + e2.y * e1.y + e2.y * e2.y
            I += (0.25f * kInv3 * D) * (intx2 + inty2)
        }

        val totalMass = density * kotlin.math.abs(area)
        val totalInertia = density * kotlin.math.abs(I)
        return MassData2D(mass = totalMass, center = centroid.clone(), inertia = totalInertia)
    }
}
