package com.nexustools.genericui.ext.box2d

/**
 * 2D connected chain of edge segments (`b2ChainShape`) for static terrain and boundaries.
 */
class ChainShape2D(
    points: List<Vec2> = emptyList(),
    val isLoop: Boolean = false
) : Shape2D(ShapeType2D.CHAIN, 0.01f) {

    val vertices: MutableList<Vec2> = points.map { it.clone() }.toMutableList()

    fun createLoop(points: List<Vec2>): ChainShape2D {
        vertices.clear()
        vertices.addAll(points.map { it.clone() })
        if (points.isNotEmpty()) {
            vertices.add(points.first().clone())
        }
        return this
    }

    fun createChain(points: List<Vec2>): ChainShape2D {
        vertices.clear()
        vertices.addAll(points.map { it.clone() })
        return this
    }

    fun getChildEdge(index: Int): EdgeShape2D {
        val v1 = vertices[index]
        val v2 = vertices[(index + 1) % vertices.size]
        return EdgeShape2D(v1.clone(), v2.clone())
    }

    override fun clone(): ChainShape2D = ChainShape2D(vertices, isLoop)

    override fun testPoint(xf: Transform2D, p: Vec2): Boolean = false

    override fun rayCast(output: RayCastOutput2D, input: RayCastInput2D, transform: Transform2D): Boolean {
        for (i in 0 until vertices.size - 1) {
            val edge = EdgeShape2D(vertices[i], vertices[i + 1])
            if (edge.rayCast(output, input, transform)) return true
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
        aabb.lowerBound.copy(lower)
        aabb.upperBound.copy(upper)
    }

    override fun computeMass(density: Float): MassData2D = MassData2D(0f, Vec2(), 0f)
}
