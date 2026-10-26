package com.nexustools.genericui.ext.canvas3d.dsl

import com.nexustools.genericui.ext.canvas3d.GLCanvasExt
import com.nexustools.genericui.ext.canvas3d.ScrollCanvas3D
import com.nexustools.genericui.ext.canvas3d.ScrollGLCanvas
import com.nexustools.genericui.ext.canvas3d.ScrollVKCanvas
import com.nexustools.genericui.ext.canvas3d.VKCanvasExt
import com.nexustools.genericui.ext.canvas3d.overlay.Canvas3DOverlayHost
import com.nexustools.genericui.ext.canvas3d.overlay.ScrollCanvas3DOverlayHost
import com.nexustools.genericui.ext.dsl.buildDiv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Canvas3DDslTest {

    @Test
    fun testCanvas3DAndScrollCanvas3DDsl() {
        var lastScrollX = 0
        var lastScrollY = 0
        val root = buildDiv {
            glCanvasExt(width = 800, height = 600, id = "gl3d")
            vkCanvasExt(width = 800, height = 600, id = "vk3d")
            scrollCanvas3D(
                viewportWidth = 640,
                viewportHeight = 480,
                contentWidth = 2560,
                contentHeight = 1920,
                id = "scroll3d"
            )
            scrollGlCanvas(
                viewportWidth = 640,
                viewportHeight = 480,
                contentWidth = 2560,
                contentHeight = 1920,
                id = "scrollGl",
                onScroll = { sx, sy, _, _, _, _ ->
                    lastScrollX = sx
                    lastScrollY = sy
                }
            )
            scrollVkCanvas(
                viewportWidth = 640,
                viewportHeight = 480,
                contentWidth = 2560,
                contentHeight = 1920,
                id = "scrollVk"
            )
        }
        assertEquals(5, root.children.size)
        val gl = root.children[0] as GLCanvasExt
        val vk = root.children[1] as VKCanvasExt
        val scroll3d = root.children[2] as ScrollCanvas3D
        val scrollGl = root.children[3] as ScrollGLCanvas
        val scrollVk = root.children[4] as ScrollVKCanvas
        assertEquals("gl3d", gl.id)
        assertEquals("vk3d", vk.id)
        assertEquals("scroll3d", scroll3d.id)
        assertEquals("scrollGl", scrollGl.id)
        assertEquals("scrollVk", scrollVk.id)

        scrollGl.scrollTo(400, 600)
        assertEquals(400, lastScrollX)
        assertEquals(600, lastScrollY)
    }

    @Test
    fun testCanvas3DOverlayHostDslBuilders() {
        val root = buildDiv {
            glCanvasOverlayHost(width = 800, height = 600, id = "glHost")
            vkCanvasOverlayHost(width = 800, height = 600, id = "vkHost")
            scrollGlCanvasOverlayHost(
                viewportWidth = 640,
                viewportHeight = 480,
                contentWidth = 2560,
                contentHeight = 1920,
                id = "scrollGlHost"
            )
            scrollVkCanvasOverlayHost(
                viewportWidth = 640,
                viewportHeight = 480,
                contentWidth = 2560,
                contentHeight = 1920,
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
