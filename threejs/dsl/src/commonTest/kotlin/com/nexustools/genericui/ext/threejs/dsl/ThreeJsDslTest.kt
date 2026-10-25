package com.nexustools.genericui.ext.threejs.dsl

import com.nexustools.genericui.ext.dsl.buildDiv
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreeJsDslTest {

    @Test
    fun testThreeJsDsl() {
        val standaloneScene = scene {
            background(0x112233)
            perspectiveCamera(fov = 60f) {
                position.set(0f, 2f, 6f)
            }
            boxMesh(width = 2f, height = 2f, depth = 2f)
            ambientLight(intensity = 0.5f)
            directionalLight(intensity = 1.0f, x = 2f, y = 5f, z = 3f)
        }
        assertEquals(4, standaloneScene.children.size)

        val root = buildDiv {
            threeJsGlCanvas(width = 640, height = 480, id = "three-gl") {
                sphereMesh(radius = 1.5f)
            }
            threeJsVkCanvas(width = 640, height = 480, id = "three-vk") {
                boxMesh(width = 1f, height = 1f, depth = 1f)
            }
        }
        assertEquals(2, root.children.size)
    }
}
