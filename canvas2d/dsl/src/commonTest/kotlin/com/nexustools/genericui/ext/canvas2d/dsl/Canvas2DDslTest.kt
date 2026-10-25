package com.nexustools.genericui.ext.canvas2d.dsl

import com.nexustools.genericui.ext.canvas2d.Canvas2DExtSurface
import com.nexustools.genericui.ext.canvas2d.CurveEditorExt
import com.nexustools.genericui.ext.canvas2d.ScrollCanvas2D
import com.nexustools.genericui.ext.dsl.buildDiv
import kotlin.test.Test
import kotlin.test.assertEquals

class Canvas2DDslTest {

    @Test
    fun testCanvas2DAndCurveEditorAndScrollCanvas2DDsl() {
        var lastScrollX = 0
        var lastScrollY = 0
        val root = buildDiv {
            canvas2DExt(width = 640, height = 480, id = "c2d")
            curveEditorExt(width = 200, height = 200, id = "curve")
            scrollCanvas2D(
                viewportWidth = 500,
                viewportHeight = 400,
                contentWidth = 2000,
                contentHeight = 1600,
                id = "scroll2d",
                onScroll = { sx, sy, _, _, _, _ ->
                    lastScrollX = sx
                    lastScrollY = sy
                }
            )
        }
        assertEquals(3, root.children.size)
        val c2d = root.children[0] as Canvas2DExtSurface
        val curve = root.children[1] as CurveEditorExt
        val scroll2d = root.children[2] as ScrollCanvas2D
        assertEquals("c2d", c2d.id)
        assertEquals("curve", curve.id)
        assertEquals("scroll2d", scroll2d.id)
        assertEquals(500, scroll2d.viewportWidth)
        assertEquals(400, scroll2d.viewportHeight)
        assertEquals(2000, scroll2d.contentWidth)
        assertEquals(1600, scroll2d.contentHeight)

        scroll2d.scrollTo(250, 350)
        assertEquals(250, lastScrollX)
        assertEquals(350, lastScrollY)
    }
}
