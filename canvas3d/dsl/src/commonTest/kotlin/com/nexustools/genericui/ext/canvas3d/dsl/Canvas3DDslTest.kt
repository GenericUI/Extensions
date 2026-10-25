package com.nexustools.genericui.ext.canvas3d.dsl

import com.nexustools.genericui.ext.canvas3d.GLCanvasExt
import com.nexustools.genericui.ext.canvas3d.VKCanvasExt
import com.nexustools.genericui.ext.dsl.buildDiv
import kotlin.test.Test
import kotlin.test.assertEquals

class Canvas3DDslTest {

    @Test
    fun testCanvas3DDsl() {
        val root = buildDiv {
            glCanvasExt(width = 800, height = 600, id = "gl3d")
            vkCanvasExt(width = 800, height = 600, id = "vk3d")
        }
        assertEquals(2, root.children.size)
        val gl = root.children[0] as GLCanvasExt
        val vk = root.children[1] as VKCanvasExt
        assertEquals("gl3d", gl.id)
        assertEquals("vk3d", vk.id)
    }
}
