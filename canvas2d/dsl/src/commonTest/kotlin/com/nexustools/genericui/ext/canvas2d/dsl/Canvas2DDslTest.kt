package com.nexustools.genericui.ext.canvas2d.dsl

import com.nexustools.genericui.ext.canvas2d.Canvas2DExtSurface
import com.nexustools.genericui.ext.canvas2d.CurveEditorExt
import com.nexustools.genericui.ext.dsl.buildDiv
import kotlin.test.Test
import kotlin.test.assertEquals

class Canvas2DDslTest {

    @Test
    fun testCanvas2DAndCurveEditorDsl() {
        val root = buildDiv {
            canvas2DExt(width = 640, height = 480, id = "c2d")
            curveEditorExt(width = 200, height = 200, id = "curve")
        }
        assertEquals(2, root.children.size)
        val c2d = root.children[0] as Canvas2DExtSurface
        val curve = root.children[1] as CurveEditorExt
        assertEquals("c2d", c2d.id)
        assertEquals("curve", curve.id)
    }
}
