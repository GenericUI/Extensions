package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScrollCanvas2DTest {

    @Test
    fun testViewportOnlyCanvasAndSystemScrollbarsSimulateScrolling() {
        val scrollCanvas = ScrollCanvas2D(
            viewportWidth = 400,
            viewportHeight = 300,
            contentWidth = 2400,
            contentHeight = 1800
        )

        // The physical framebuffer is strictly viewport-sized (400x300), NOT 2400x1800!
        assertEquals(400, scrollCanvas.viewportWidth)
        assertEquals(300, scrollCanvas.viewportHeight)
        assertEquals(2400, scrollCanvas.contentWidth)
        assertEquals(1800, scrollCanvas.contentHeight)
        assertEquals(400, scrollCanvas.imageBuffer.width)
        assertEquals(300, scrollCanvas.imageBuffer.height)
        assertEquals(2000, scrollCanvas.maxScrollX)
        assertEquals(1500, scrollCanvas.maxScrollY)

        // On System targets, ScrollCanvas2D contains the viewportCanvas + right & bottom scrollbar components
        assertEquals(3, scrollCanvas.children.size)
        assertSame(scrollCanvas.viewportCanvas, scrollCanvas.children[0])
        assertSame(scrollCanvas.rightScrollBar, scrollCanvas.children[1])
        assertSame(scrollCanvas.bottomScrollBar, scrollCanvas.children[2])
        assertTrue(scrollCanvas.rightScrollBar.hasClass("scroll-canvas-scrollbar-right"))
        assertTrue(scrollCanvas.bottomScrollBar.hasClass("scroll-canvas-scrollbar-bottom"))
        assertEquals(1500.0, scrollCanvas.rightScrollBar.max)
        assertEquals(2000.0, scrollCanvas.bottomScrollBar.max)

        var lastNotifiedX = -1
        var lastNotifiedY = -1
        var renderPassCount = 0
        scrollCanvas.onScroll { sx, sy, vw, vh, cw, ch ->
            lastNotifiedX = sx
            lastNotifiedY = sy
            assertEquals(400, vw)
            assertEquals(300, vh)
            assertEquals(2400, cw)
            assertEquals(1800, ch)

            // Re-render viewport canvas for simulated scroll offset
            val ctx = scrollCanvas.getContext()
            ctx.setFillColor(RgbaColor(32, 64, 128, 255))
            ctx.fillRect(0f, 0f, vw.toFloat(), vh.toFloat())
            renderPassCount++
        }

        // Scroll via scrollTo -> updates scrollbars and fires listener
        scrollCanvas.scrollTo(320, 480)
        assertEquals(320, scrollCanvas.scrollX)
        assertEquals(480, scrollCanvas.scrollY)
        assertEquals(320, lastNotifiedX)
        assertEquals(480, lastNotifiedY)
        assertEquals(320.0, scrollCanvas.bottomScrollBar.value)
        assertEquals(480.0, scrollCanvas.rightScrollBar.value)
        assertEquals(1, renderPassCount)

        // Move right scrollbar -> updates scrollY and fires listener
        scrollCanvas.rightScrollBar.onValueChanged?.invoke(900.0)
        assertEquals(320, scrollCanvas.scrollX)
        assertEquals(900, scrollCanvas.scrollY)
        assertEquals(900, lastNotifiedY)
        assertEquals(2, renderPassCount)

        // Move bottom scrollbar -> updates scrollX and fires listener
        scrollCanvas.bottomScrollBar.onValueChanged?.invoke(1150.0)
        assertEquals(1150, scrollCanvas.scrollX)
        assertEquals(900, scrollCanvas.scrollY)
        assertEquals(1150, lastNotifiedX)
        assertEquals(3, renderPassCount)

        // Wheel event on canvas -> adjusts scrollX / scrollY and synchronizes scrollbars
        scrollCanvas.handleWheelEvent(WheelEvent(x = 100f, y = 100f, deltaX = 50f, deltaY = 120f))
        assertEquals(1200, scrollCanvas.scrollX)
        assertEquals(1020, scrollCanvas.scrollY)
        assertEquals(1200.0, scrollCanvas.bottomScrollBar.value)
        assertEquals(1020.0, scrollCanvas.rightScrollBar.value)
        assertEquals(4, renderPassCount)

        // Clamping beyond bounds
        scrollCanvas.scrollTo(99999, 99999)
        assertEquals(2000, scrollCanvas.scrollX)
        assertEquals(1500, scrollCanvas.scrollY)
        assertEquals(400, scrollCanvas.imageBuffer.width)
        assertEquals(300, scrollCanvas.imageBuffer.height)
    }

    @Test
    fun testCanvas2DAndScrollCanvas2DOverlayHostsExcludeScrollbarsAndHaveNoDefaultOverlay() {
        val surface = Canvas2DExtSurface(width = 640, height = 360)
        val surfaceHost = surface.withOverlay()
        assertNull(surfaceHost.activeOverlay)
        assertSame(surface, surfaceHost.canvas)
        assertEquals(640, surfaceHost.overlayRoot.widthPx)
        assertEquals(360, surfaceHost.overlayRoot.heightPx)

        val customOverlay = object : AbstractOverlay("test-2d-overlay") {}
        val attachedSurfaceHost = surface.attachOverlay(customOverlay)
        assertSame(customOverlay, attachedSurfaceHost.activeOverlay)
        assertEquals(640, customOverlay.widthPx)
        assertEquals(360, customOverlay.heightPx)

        val scrollCanvas = ScrollCanvas2D(
            viewportWidth = 500,
            viewportHeight = 350,
            contentWidth = 2000,
            contentHeight = 1400
        )
        val scrollHost = scrollCanvas.withOverlay()
        assertNull(scrollHost.activeOverlay)
        assertFalse(scrollHost.overlayCoversScrollbars)
        assertEquals(500, scrollHost.overlayRoot.widthPx)
        assertEquals(350, scrollHost.overlayRoot.heightPx)
        assertEquals("true", scrollHost.overlayRoot.getAttribute("data-excludes-scrollbars"))

        val scrollOverlay = object : AbstractOverlay("scroll-2d-overlay") {}
        scrollHost.attachOverlay(scrollOverlay)
        assertSame(scrollOverlay, scrollHost.activeOverlay)
        assertEquals(500, scrollOverlay.widthPx)
        assertEquals(350, scrollOverlay.heightPx)

        scrollCanvas.setViewportSize(720, 480)
        assertEquals(720, scrollHost.overlayRoot.widthPx)
        assertEquals(480, scrollHost.overlayRoot.heightPx)
        assertEquals(720, scrollOverlay.widthPx)
        assertEquals(480, scrollOverlay.heightPx)
    }
}
