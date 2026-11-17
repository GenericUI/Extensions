package com.nexustools.genericui.ext.threejs.dsl

import com.nexustools.genericui.ext.canvas3d.Canvas3DRegistry
import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.gpu.GpuAccelerationPolicy
import com.nexustools.genericui.ext.gpu.GpuBackendSupport
import com.nexustools.genericui.ext.gpu.GpuBackendType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreeJsDslTest {

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
            threeJsCanvas(
                width = 640,
                height = 480,
                backend = GpuBackendType.OPENGL_ES,
                acceleration = GpuAccelerationPolicy.HARDWARE_PREFERRED,
                id = "three-gl"
            ) {
                sphereMesh(radius = 1.5f)
            }
            threeJsCanvas(
                width = 640,
                height = 480,
                backend = GpuBackendType.VULKAN,
                acceleration = GpuAccelerationPolicy.HARDWARE_PREFERRED,
                id = "three-vk"
            ) {
                boxMesh(width = 1f, height = 1f, depth = 1f)
            }
        }
        assertEquals(2, root.children.size)
    }
}
