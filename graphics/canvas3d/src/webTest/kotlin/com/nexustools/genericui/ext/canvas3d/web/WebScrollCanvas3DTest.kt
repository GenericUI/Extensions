package com.nexustools.genericui.ext.canvas3d.web

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.GLClearBuffer
import com.nexustools.genericui.ext.canvas3d.Canvas3DExtension
import com.nexustools.genericui.ext.canvas3d.ScrollCanvas3D
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.web.WebCanvas3DImpl
import com.nexustools.genericui.web.WebGLContextImpl
import com.nexustools.genericui.web.WebScrollCanvas3DImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
            backend = GpuBackendType.OPENGL_ES
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

        val scrollCanvas = ScrollCanvas3D(
            viewportWidth = 640,
            viewportHeight = 480,
            contentWidth = 4000,
            contentHeight = 3000,
            backend = GpuBackendType.OPENGL_ES
        )
        scrollCanvas.attachScrollNativeImpl(impl)

        var notifiedX = 0
        var notifiedY = 0
        scrollCanvas.onScroll { sx, sy, _, _, _, _ ->
            notifiedX = sx
            notifiedY = sy
        }

        rootEl.scrollLeft = 512.0
        rootEl.scrollTop = 768.0
        rootEl.dispatchEvent("scroll")

        assertEquals(512, scrollCanvas.scrollX)
        assertEquals(768, scrollCanvas.scrollY)
        assertEquals(512, notifiedX)
        assertEquals(768, notifiedY)

        scrollCanvas.scrollTo(1024, 1536)
        assertEquals(1024.0, rootEl.scrollLeft)
        assertEquals(1536.0, rootEl.scrollTop)
    }

    @Test
    fun testWebCanvas3DProviderAndWebGl2ContextCreation() {
        WebCanvas3DProvider.install(enableWebGpu = false, enableWebGl2 = true)

        assertFalse(Canvas3D.detectSupport(GpuBackendType.VULKAN).hardwareAvailable)
        assertTrue(Canvas3D.detectSupport(GpuBackendType.OPENGL_ES).hardwareAvailable)

        val glContext = WebCanvas3DProvider.createContext(
            backend = GpuBackendType.OPENGL_ES,
            width = 512,
            height = 384
        )
        assertTrue(glContext is WebGLContextImpl)

        val canvasImpl = WebCanvas3DImpl(
            activeBackend = GpuBackendType.OPENGL_ES,
            initialWidth = 512,
            initialHeight = 384
        )
        assertNotNull(canvasImpl.glContext)
        assertEquals("webgl2", canvasImpl.nativeWidget.getAttribute("data-context"))

        var resizedW = 0
        var resizedH = 0
        canvasImpl.setOnSurfaceResized { _, w, h ->
            resizedW = w
            resizedH = h
        }

        var renderFrames = 0
        canvasImpl.setVBlankLoopActive(true) {
            renderFrames++
            canvasImpl.glContext?.clearColor(0.1f, 0.2f, 0.3f, 1.0f)
            canvasImpl.glContext?.clear(setOf(GLClearBuffer.COLOR))
        }

        canvasImpl.nativeWidget.clientWidth = 1024
        canvasImpl.nativeWidget.clientHeight = 576
        canvasImpl.stepRequestAnimationFrame(16.0)

        assertEquals(1, renderFrames)
        assertEquals(1024, resizedW)
        assertEquals(576, resizedH)
        assertEquals(1024, canvasImpl.getSurfaceWidth())
        assertEquals(576, canvasImpl.getSurfaceHeight())

        canvasImpl.dispose()
        WebCanvas3DProvider.install()
    }
}
