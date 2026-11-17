package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WebImageBuffer2DContextTest {

    @Test
    fun testWebImageBufferSurfacePeerToDataUrlAndPixelSync() {
        val buffer = WebImageBuffer(width = 120, height = 80, initialAntialias = true)
        assertEquals("canvas", buffer.surfaceElement.tagName)
        assertEquals("120", buffer.surfaceElement.getAttribute("width"))
        assertEquals("80", buffer.surfaceElement.getAttribute("height"))
        assertEquals("true", buffer.surfaceElement.getAttribute("data-context-2d"))
        assertEquals("true", buffer.surfaceElement.getAttribute("data-antialias"))

        val peer = buffer.nativeSurfacePeer
        assertNotNull(peer)
        if (!WebImageBuffer2DBridge.isBrowserCanvasSupported) {
            assertSame(buffer.surfaceElement, peer)
        }

        val dataUrl = buffer.toDataUrl("image/webp")
        assertEquals("image/webp", buffer.surfaceElement.getAttribute("data-last-export-mime"))
        if (!WebImageBuffer2DBridge.isBrowserCanvasSupported) {
            assertNull(dataUrl)
        } else {
            assertNotNull(dataUrl)
            assertTrue(dataUrl.startsWith("data:"))
        }

        val prevVersion = buffer.modificationVersion
        buffer.setPixel(10, 15, RgbaColor(200, 100, 50, 255))
        assertTrue(buffer.modificationVersion > prevVersion)
        val px = buffer.getPixel(10, 15)
        assertEquals(200, px.r)
        assertEquals(100, px.g)
        assertEquals(50, px.b)
        assertEquals(255, px.a)

        val copied = buffer.copy()
        assertEquals(120, copied.width)
        assertEquals(80, copied.height)
        assertEquals(200, copied.getPixel(10, 15).r)

        val bgraBuf = WebImageBuffer(width = 16, height = 16, pixelFormat = PixelFormat.BGRA_8888)
        bgraBuf.setPixel(2, 3, RgbaColor(10, 20, 30, 255))
        val convertedRgba = bgraBuf.toRgba8888()
        assertEquals(PixelFormat.RGBA_8888, convertedRgba.pixelFormat)
        assertEquals(10, convertedRgba.getPixel(2, 3).r)
        assertEquals(20, convertedRgba.getPixel(2, 3).g)
        assertEquals(30, convertedRgba.getPixel(2, 3).b)
    }

    @Test
    fun testWebImageBuffer2DContextDrawingOperations() {
        val buffer = WebImageBuffer(width = 160, height = 120)
        val ctx = buffer.getContext()
        val el = buffer.surfaceElement

        // 1. fillRect
        ctx.setFillColor(RgbaColor(255, 64, 32, 255))
        ctx.fillRect(10f, 10f, 40f, 30f)
        assertEquals("fillRect", el.getAttribute("data-last-op"))
        assertEquals("rgba(255,64,32,1)", el.getAttribute("data-fill-style"))
        val filledPixel = buffer.getPixel(25, 25)
        assertEquals(255, filledPixel.r)
        assertEquals(64, filledPixel.g)
        assertEquals(32, filledPixel.b)

        // 2. strokeRect
        ctx.setStrokeColor(RgbaColor(0, 180, 255, 255))
        ctx.lineWidth = 4f
        ctx.lineCap = ImageBufferLineCap.ROUND
        ctx.lineJoin = ImageBufferLineJoin.BEVEL
        ctx.strokeRect(60f, 10f, 30f, 30f)
        assertEquals("strokeRect", el.getAttribute("data-last-op"))
        assertEquals("rgba(0,180,255,1)", el.getAttribute("data-stroke-style"))
        assertEquals("4", el.getAttribute("data-line-width")?.substringBefore('.'))
        val strokedPixel = buffer.getPixel(60, 25)
        assertEquals(180, strokedPixel.g)
        assertEquals(255, strokedPixel.b)

        // 3. fillText, strokeText & measureText
        ctx.setFont(fontFamily = "monospace", fontSizePx = 18f, fontWeight = 700, italic = true)
        ctx.textAlign = ImageBufferTextAlign.LEFT
        ctx.textBaseline = ImageBufferTextBaseline.TOP
        val metrics = ctx.measureText("Canvas2D")
        assertTrue(metrics.width > 0f)
        assertEquals("Canvas2D", el.getAttribute("data-last-measure-text"))
        assertEquals("italic 700 18px monospace", el.getAttribute("data-font")?.replace(".0px", "px"))

        ctx.setFillColor(RgbaColor.WHITE)
        ctx.fillText("Hello Web", 12f, 60f)
        assertEquals("fillText", el.getAttribute("data-last-op"))
        assertEquals("Hello Web", el.getAttribute("data-last-text"))

        ctx.setStrokeColor(RgbaColor.GREEN)
        ctx.strokeText("Outline", 12f, 85f, 120f)
        assertEquals("strokeText", el.getAttribute("data-last-op"))
        assertEquals("Outline", el.getAttribute("data-last-text"))

        // 4. drawImage with both WebImageBuffer (nativeSurfacePeer) and common ImageBuffer (RGBA bytes)
        val srcWebBuffer = WebImageBuffer(width = 20, height = 20)
        srcWebBuffer.fill(RgbaColor(12, 220, 90, 255))
        ctx.drawImage(srcWebBuffer, 100f, 10f)
        assertEquals("drawImage", el.getAttribute("data-last-op"))
        val drawnWebPx = buffer.getPixel(110, 20)
        assertEquals(12, drawnWebPx.r)
        assertEquals(220, drawnWebPx.g)
        assertEquals(90, drawnWebPx.b)

        val srcCpuBuffer = com.nexustools.genericui.image.ImageBuffer(width = 16, height = 16).apply {
            fill(RgbaColor(210, 40, 190, 255))
        }
        ctx.drawImage(srcCpuBuffer, 100f, 50f, 24f, 24f)
        assertEquals("drawImage", el.getAttribute("data-last-op"))
        val drawnCpuPx = buffer.getPixel(112, 62)
        assertEquals(210, drawnCpuPx.r)
        assertEquals(40, drawnCpuPx.g)
        assertEquals(190, drawnCpuPx.b)

        // 5. clearRect & resize preservation
        ctx.clearRect(10f, 10f, 40f, 30f)
        assertEquals("clearRect", el.getAttribute("data-last-op"))
        assertEquals(0, buffer.getPixel(25, 25).a)

        buffer.resize(200, 150)
        assertEquals(200, buffer.width)
        assertEquals(150, buffer.height)
        assertEquals("200", el.getAttribute("width"))
        assertEquals("150", el.getAttribute("height"))
        assertEquals(220, buffer.getPixel(110, 20).g)
    }
}
