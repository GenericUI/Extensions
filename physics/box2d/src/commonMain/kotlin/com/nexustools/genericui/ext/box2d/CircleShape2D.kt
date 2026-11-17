package com.nexustools.genericui.ext.box2d

import kotlin.math.PI
import kotlin.math.sqrt

/**
 * 2D circle collision shape (`b2CircleShape`).
 */
class CircleShape2D(
    radius: Float = 0.5f,
    val p: Vec2 = Vec2()
) : Shape2D(ShapeType2D.CIRCLE, radius) {
    val localPosition: Vec2 get() = p


    override fun clone(): CircleShape2D = CircleShape2D(radius, p.clone())

    override fun testPoint(xf: Transform2D, p: Vec2): Boolean {
        val center = xf.mul(this.p)
        val d = p - center
        return d.dot(d) <= radius * radius
    }

    override fun rayCast(output: RayCastOutput2D, input: RayCastInput2D, transform: Transform2D): Boolean {
        val position = transform.mul(p)
        val s = input.p1 - position
        val b = s.dot(s) - radius * radius
        val r = input.p2 - input.p1
        val c = s.dot(r)
        val rr = r.dot(r)
        val sigma = c * c - rr * b
        if (sigma < 0f || rr < 1e-7f) return false
        val a = -(c + sqrt(sigma))
        if (a in 0f..(input.maxFraction * rr)) {
            val frac = a / rr
            output.fraction = frac
            val norm = s + r * frac
            norm.normalize()
            output.normal.copy(norm)
            return true
        }
        return false
    }

    override fun computeAABB(aabb: AABB2D, xf: Transform2D) {
        val center = xf.mul(p)
        aabb.lowerBound.set(center.x - radius, center.y - radius)
        aabb.upperBound.set(center.x + radius, center.y + radius)
    }

    override fun computeMass(density: Float): MassData2D {
        val mass = density * PI.toFloat() * radius * radius
        val inertia = mass * (0.5f * radius * radius + p.dot(p))
        return MassData2D(mass = mass, center = p.clone(), inertia = inertia)
    }
}
