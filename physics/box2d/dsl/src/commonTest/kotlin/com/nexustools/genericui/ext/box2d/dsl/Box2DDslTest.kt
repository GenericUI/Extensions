package com.nexustools.genericui.ext.box2d.dsl

import com.nexustools.genericui.ext.dsl.buildDiv
import kotlin.test.Test
import kotlin.test.assertEquals

class Box2DDslTest {

    @Test
    fun testBox2DDsl() {
        val w = world2D {
            staticBody(x = 0f, y = -5f) {
                boxFixture(halfWidth = 10f, halfHeight = 0.5f, density = 0f)
            }
            dynamicBody(x = 0f, y = 4f) {
                circleFixture(radius = 0.5f, density = 1f, restitution = 0.6f)
            }
        }
        assertEquals(2, w.bodies.size)

        val root = buildDiv {
            box2dDebugCanvas(world = w, width = 800, height = 600, id = "b2d-canvas")
        }
        assertEquals(1, root.children.size)
    }
}
