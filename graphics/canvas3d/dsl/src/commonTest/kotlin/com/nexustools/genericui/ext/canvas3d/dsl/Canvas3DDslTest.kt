package com.nexustools.genericui.ext.canvas3d.dsl

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.ext.canvas3d.Canvas3DRegistry
import com.nexustools.genericui.ext.canvas3d.ScrollCanvas3D
import com.nexustools.genericui.ext.canvas3d.overlay.Canvas3DOverlayHost
import com.nexustools.genericui.ext.canvas3d.overlay.ScrollCanvas3DOverlayHost
import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.gpu.GpuAccelerationPolicy
import com.nexustools.genericui.ext.gpu.GpuBackendSupport
import com.nexustools.genericui.ext.gpu.GpuBackendType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Canvas3DDslTest {

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
    fun testCanvas3DAndScrollCanvas3DDsl() {
        var lastScrollX = 0
        var lastScrollY = 0
        val root = buildDiv {
            canvas3D(
                backend = GpuBackendType.OPENGL_ES,
                acceleration = GpuAccelerationPolicy.HARDWARE_PREFERRED,
                width = 800,
                height = 600,
                id = "gl3d"
            )
            canvas3D(
                backend = GpuBackendType.VULKAN,
                acceleration = GpuAccelerationPolicy.HARDWARE_PREFERRED,
                width = 800,
                height = 600,
                id = "vk3d"
            )
            scrollCanvas3D(
                viewportWidth = 640,
                viewportHeight = 480,
                contentWidth = 2560,
                contentHeight = 1920,
                backend = GpuBackendType.OPENGL_ES,
                acceleration = GpuAccelerationPolicy.HARDWARE_PREFERRED,
                id = "scrollGl",
                onScroll = { sx, sy, _, _, _, _ ->
                    lastScrollX = sx
                    lastScrollY = sy
                }
            )
            scrollCanvas3D(
                viewportWidth = 640,
                viewportHeight = 480,
                contentWidth = 2560,
                contentHeight = 1920,
                backend = GpuBackendType.VULKAN,
                acceleration = GpuAccelerationPolicy.HARDWARE_PREFERRED,
                id = "scrollVk"
            )
        }
        assertEquals(4, root.children.size)
        val gl = root.children[0] as Canvas3D
        val vk = root.children[1] as Canvas3D
        val scrollGl = root.children[2] as ScrollCanvas3D
        val scrollVk = root.children[3] as ScrollCanvas3D
        assertEquals("gl3d", gl.id)
        assertEquals("vk3d", vk.id)
        assertEquals("scrollGl", scrollGl.id)
        assertEquals("scrollVk", scrollVk.id)

        scrollGl.scrollTo(400, 600)
        assertEquals(400, lastScrollX)
        assertEquals(600, lastScrollY)
    }

    @Test
    fun testCanvas3DOverlayHostDslBuilders() {
        val root = buildDiv {
            canvas3DOverlayHost(
                backend = GpuBackendType.OPENGL_ES,
                acceleration = GpuAccelerationPolicy.HARDWARE_PREFERRED,
                width = 800,
                height = 600,
                id = "glHost"
            )
            canvas3DOverlayHost(
                backend = GpuBackendType.VULKAN,
                acceleration = GpuAccelerationPolicy.HARDWARE_PREFERRED,
                width = 800,
                height = 600,
                id = "vkHost"
            )
            scrollCanvas3DOverlayHost(
                viewportWidth = 640,
                viewportHeight = 480,
                contentWidth = 2560,
                contentHeight = 1920,
                backend = GpuBackendType.OPENGL_ES,
                acceleration = GpuAccelerationPolicy.HARDWARE_PREFERRED,
                id = "scrollGlHost"
            )
            scrollCanvas3DOverlayHost(
                viewportWidth = 640,
                viewportHeight = 480,
                contentWidth = 2560,
                contentHeight = 1920,
                backend = GpuBackendType.VULKAN,
                acceleration = GpuAccelerationPolicy.HARDWARE_PREFERRED,
                id = "scrollVkHost"
            )
        }
        assertEquals(4, root.children.size)
        val glHost = root.children[0] as Canvas3DOverlayHost
        val vkHost = root.children[1] as Canvas3DOverlayHost
        val scrollGlHost = root.children[2] as ScrollCanvas3DOverlayHost
        val scrollVkHost = root.children[3] as ScrollCanvas3DOverlayHost
        assertNull(glHost.activeOverlay)
        assertNull(vkHost.activeOverlay)
        assertNull(scrollGlHost.activeOverlay)
        assertNull(scrollVkHost.activeOverlay)
        assertEquals(640, scrollGlHost.overlayRoot.widthPx)
        assertEquals(480, scrollGlHost.overlayRoot.heightPx)
        assertEquals(640, scrollVkHost.overlayRoot.widthPx)
        assertEquals(480, scrollVkHost.overlayRoot.heightPx)
    }
}
