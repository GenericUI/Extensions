package com.nexustools.genericui.ext.box3d.dsl

import com.nexustools.genericui.ext.canvas3d.Canvas3DRegistry
import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.gpu.GpuBackendSupport
import com.nexustools.genericui.ext.gpu.GpuBackendType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class Box3DDslTest {

    @BeforeTest
    fun setUp() {
        Canvas3DRegistry.overrideSupportForTesting(
            GpuBackendType.OPENGL_ES,
            GpuBackendSupport(
                backend = GpuBackendType.OPENGL_ES,
                hardwareAvailable = true,
                softwareAvailable = true
            )
        )
        Canvas3DRegistry.overrideSupportForTesting(
            GpuBackendType.VULKAN,
            GpuBackendSupport(
                backend = GpuBackendType.VULKAN,
                hardwareAvailable = true,
                softwareAvailable = true
            )
        )
    }

    @AfterTest
    fun tearDown() {
        Canvas3DRegistry.resetForTesting()
    }

    @Test
    fun testBox3DDsl() {
        val w = world3D {
            staticRigidBody(x = 0f, y = -2f, z = 0f) {
                boxCollider(hx = 10f, hy = 0.5f, hz = 10f, density = 0f)
            }
            dynamicRigidBody(x = 0f, y = 5f, z = 0f) {
                sphereCollider(radius = 0.5f, density = 1f)
            }
        }
        assertEquals(2, w.bodies.size)

        val root = buildDiv {
            box3dDebugCanvas(world = w, width = 800, height = 600, id = "b3d-canvas")
        }
        assertEquals(1, root.children.size)
    }
}
