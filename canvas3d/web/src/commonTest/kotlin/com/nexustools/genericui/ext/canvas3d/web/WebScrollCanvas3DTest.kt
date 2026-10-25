package com.nexustools.genericui.ext.canvas3d.web

import com.nexustools.genericui.ext.canvas3d.Canvas3DExtension
import com.nexustools.genericui.ext.canvas3d.ScrollGLCanvas
import com.nexustools.genericui.web.WebScrollCanvas3DImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebScrollCanvas3DTest {

    @Test
    fun testWebScrollCanvas3DDomStructureAndSimulatedScrolling() {
        WebCanvas3DProvider.install()

        val impl = Canvas3DExtension.createScrollCanvas3DNative(
            viewportWidth = 640,
            viewportHeight = 480,
            contentWidth = 4000,
            contentHeight = 3000,
            preferVulkan = false
        ) as WebScrollCanvas3DImpl

        assertTrue(impl.isWebScrollContainer)
        val rootEl = impl.nativeWidget
        assertEquals("genericui:scrollcanvas3d", rootEl.tagName)
        assertEquals("relative", rootEl.style["position"])
        assertEquals("auto", rootEl.style["overflow"])

        assertEquals(2, rootEl.children.size)
        val sizerDiv = impl.contentSizerDiv
        val canvasEl = impl.canvasElement
        assertEquals("div", sizerDiv.tagName)
        assertEquals("4000px", sizerDiv.style["width"])
        assertEquals("3000px", sizerDiv.style["height"])

        assertEquals("canvas", canvasEl.tagName)
        assertEquals("absolute", canvasEl.style["position"])
        assertEquals("0", canvasEl.style["left"])
        assertEquals("0", canvasEl.style["top"])
        assertEquals("0", canvasEl.style["width"])
        assertEquals("0", canvasEl.style["height"])
        assertEquals("640", canvasEl.getAttribute("width"))
        assertEquals("480", canvasEl.getAttribute("height"))

        val scrollGl = ScrollGLCanvas(
            viewportWidth = 640,
            viewportHeight = 480,
            contentWidth = 4000,
            contentHeight = 3000
        )
        scrollGl.attachScrollNativeImpl(impl)

        var notifiedX = 0
        var notifiedY = 0
        scrollGl.onScroll { sx, sy, _, _, _, _ ->
            notifiedX = sx
            notifiedY = sy
        }

        rootEl.scrollLeft = 512.0
        rootEl.scrollTop = 768.0
        rootEl.dispatchEvent("scroll")

        assertEquals(512, scrollGl.scrollX)
        assertEquals(768, scrollGl.scrollY)
        assertEquals(512, notifiedX)
        assertEquals(768, notifiedY)

        scrollGl.scrollTo(1024, 1536)
        assertEquals(1024.0, rootEl.scrollLeft)
        assertEquals(1536.0, rootEl.scrollTop)
    }
}
