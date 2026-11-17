package com.nexustools.genericui.ext.canvas2d.web

import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension
import com.nexustools.genericui.ext.canvas2d.ScrollCanvas2D
import com.nexustools.genericui.web.WebScrollCanvas2DImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebScrollCanvas2DTest {

    @Test
    fun testWebScrollCanvas2DDomStructureAndSimulatedScrolling() {
        WebCanvas2DProvider.install()

        val impl = Canvas2DExtension.createScrollCanvas2DNative(
            viewportWidth = 500,
            viewportHeight = 350,
            contentWidth = 3000,
            contentHeight = 2000
        ) as WebScrollCanvas2DImpl

        assertTrue(impl.isWebScrollContainer)
        val rootEl = impl.nativeWidget
        assertEquals("genericui:scrollcanvas2d", rootEl.tagName)
        assertEquals("relative", rootEl.style["position"])
        assertEquals("auto", rootEl.style["overflow"])

        assertEquals(2, rootEl.children.size)
        val sizerDiv = impl.contentSizerDiv
        val canvasEl = impl.canvasElement
        assertEquals("div", sizerDiv.tagName)
        assertEquals("3000px", sizerDiv.style["width"])
        assertEquals("2000px", sizerDiv.style["height"])

        assertEquals("canvas", canvasEl.tagName)
        assertEquals("absolute", canvasEl.style["position"])
        assertEquals("0", canvasEl.style["left"])
        assertEquals("0", canvasEl.style["top"])
        assertEquals("0", canvasEl.style["width"])
        assertEquals("0", canvasEl.style["height"])
        assertEquals("500", canvasEl.getAttribute("width"))
        assertEquals("350", canvasEl.getAttribute("height"))

        // Bind to ScrollCanvas2D and verify bidirectional scroll synchronization
        val scrollCanvas = ScrollCanvas2D(
            viewportWidth = 500,
            viewportHeight = 350,
            contentWidth = 3000,
            contentHeight = 2000
        )
        scrollCanvas.attachScrollNativeImpl(impl)

        var notifiedX = 0
        var notifiedY = 0
        scrollCanvas.onScroll { sx, sy, _, _, _, _ ->
            notifiedX = sx
            notifiedY = sy
        }

        // Simulate browser DOM scroll event
        rootEl.scrollLeft = 420.0
        rootEl.scrollTop = 680.0
        rootEl.dispatchEvent("scroll")

        assertEquals(420, scrollCanvas.scrollX)
        assertEquals(680, scrollCanvas.scrollY)
        assertEquals(420, notifiedX)
        assertEquals(680, notifiedY)

        // Programmatic scrollTo updates DOM scrollLeft / scrollTop
        scrollCanvas.scrollTo(800, 950)
        assertEquals(800.0, rootEl.scrollLeft)
        assertEquals(950.0, rootEl.scrollTop)
    }
}
