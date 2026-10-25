package com.nexustools.genericui.ext.canvas3d

import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.gpu.GLContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScrollCanvas3DTest {

    @Test
    fun testScrollGLCanvasAndScrollVKCanvasViewportOnlySwapchainAndSystemScrollbars() {
        val scrollGl = ScrollGLCanvas(
            viewportWidth = 640,
            viewportHeight = 480,
            contentWidth = 4096,
            contentHeight = 3072
        )

        // The physical 3D canvas swapchain is strictly viewport-sized (640x480), NOT 4096x3072!
        assertEquals(640, scrollGl.viewportWidth)
        assertEquals(480, scrollGl.viewportHeight)
        assertEquals(4096, scrollGl.contentWidth)
        assertEquals(3072, scrollGl.contentHeight)
        assertEquals(640, scrollGl.glCanvas.widthPx)
        assertEquals(480, scrollGl.glCanvas.heightPx)
        assertEquals(3456, scrollGl.maxScrollX)
        assertEquals(2592, scrollGl.maxScrollY)

        // On System targets, ScrollGLCanvas contains the viewport 3D canvas + right & bottom scrollbar components
        assertEquals(3, scrollGl.children.size)
        assertSame(scrollGl.glCanvas, scrollGl.children[0])
        assertSame(scrollGl.rightScrollBar, scrollGl.children[1])
        assertSame(scrollGl.bottomScrollBar, scrollGl.children[2])
        assertTrue(scrollGl.rightScrollBar.hasClass("scroll-canvas-scrollbar-right"))
        assertTrue(scrollGl.bottomScrollBar.hasClass("scroll-canvas-scrollbar-bottom"))

        var lastGlScrollX = -1
        var lastGlScrollY = -1
        var glRenderCount = 0
        scrollGl.onScroll { sx, sy, vw, vh, cw, ch ->
            lastGlScrollX = sx
            lastGlScrollY = sy
            assertEquals(640, vw)
            assertEquals(480, vh)
            assertEquals(4096, cw)
            assertEquals(3072, ch)
            val gl = scrollGl.getContext()
            assertNotNull(gl)
            gl.viewport(0, 0, vw, vh)
            gl.clear(GLContext.GL_COLOR_BUFFER_BIT)
            glRenderCount++
        }

        scrollGl.scrollTo(500, 700)
        assertEquals(500, scrollGl.scrollX)
        assertEquals(700, scrollGl.scrollY)
        assertEquals(500, lastGlScrollX)
        assertEquals(700, lastGlScrollY)
        assertEquals(500.0, scrollGl.bottomScrollBar.value)
        assertEquals(700.0, scrollGl.rightScrollBar.value)
        assertEquals(1, glRenderCount)

        // Move right & bottom scrollbars
        scrollGl.rightScrollBar.onValueChanged?.invoke(1200.0)
        scrollGl.bottomScrollBar.onValueChanged?.invoke(1600.0)
        assertEquals(1600, scrollGl.scrollX)
        assertEquals(1200, scrollGl.scrollY)
        assertEquals(3, glRenderCount)

        // Wheel event on 3D canvas
        scrollGl.handleWheelEvent(WheelEvent(x = 50f, y = 50f, deltaX = -100f, deltaY = 80f))
        assertEquals(1500, scrollGl.scrollX)
        assertEquals(1280, scrollGl.scrollY)
        assertEquals(4, glRenderCount)

        // Verify ScrollVKCanvas as well
        val scrollVk = ScrollVKCanvas(
            viewportWidth = 800,
            viewportHeight = 600,
            contentWidth = 3200,
            contentHeight = 2400
        )
        assertSame(scrollVk.vkCanvas, scrollVk.children[0])
        assertSame(scrollVk.rightScrollBar, scrollVk.children[1])
        assertSame(scrollVk.bottomScrollBar, scrollVk.children[2])
        assertNotNull(scrollVk.getContext())
    }
}
