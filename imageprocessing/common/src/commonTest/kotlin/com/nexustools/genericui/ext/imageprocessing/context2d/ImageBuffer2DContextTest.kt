package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.style.RgbaColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageBuffer2DContextTest {

    @Test
    fun testImageBuffer2DContextFullHtml5ParityAndDirtyTracking() {
        val buffer = DefaultCpuImageBuffer(width = 160, height = 120, doubleBuffer = true)
        var dirtyNotificationCount = 0
        buffer.addDirtyListener { _, _, _, _, _ ->
            dirtyNotificationCount++
        }

        val ctx: ImageBuffer2DContext = buffer.getContext()
        assertEquals(160, ctx.width)
        assertEquals(120, ctx.height)

        // 1. State stack & 2D affine matrix transforms
        ctx.save()
        ctx.translate(10f, 15f)
        ctx.scale(2f, 2f)
        val matrix = ctx.getTransform()
        assertEquals(2f, matrix.a)
        assertEquals(2f, matrix.d)
        assertEquals(10f, matrix.e)
        assertEquals(15f, matrix.f)
        ctx.restore()
        assertTrue(ctx.getTransform().isIdentity)

        // 2. Gradients (Linear, Radial, Conic) & Patterns
        val linear = ctx.createLinearGradient(0f, 0f, 100f, 0f).apply {
            addColorStop(0f, RgbaColor(255, 0, 0, 255))
            addColorStop(1f, RgbaColor(0, 0, 255, 255))
        }
        ctx.setFillGradient(linear)
        ctx.fillRect(0f, 0f, 100f, 20f)
        assertTrue(buffer.isDirty)
        assertTrue(dirtyNotificationCount > 0)

        val conic = ctx.createConicGradient(0f, 50f, 50f).apply {
            addColorStop(0f, RgbaColor.RED)
            addColorStop(0.5f, RgbaColor.GREEN)
            addColorStop(1f, RgbaColor.RED)
        }
        assertEquals(255, conic.sampleAt(80f, 50f).r)

        // 3. ImageBufferPath2D & hit testing (isPointInPath, isPointInStroke)
        val path = ImageBufferPath2D().apply {
            roundRect(20f, 30f, 60f, 40f, 8f)
        }
        assertTrue(ctx.isPointInPath(path, 50f, 50f, ImageBufferFillRule.NONZERO))
        assertFalse(ctx.isPointInPath(path, 5f, 5f, ImageBufferFillRule.NONZERO))
        ctx.lineWidth = 4f
        assertTrue(ctx.isPointInStroke(path, 20f, 50f))

        // 4. ScalableVectorFontEngine & ImageBufferTextMetrics
        ctx.setFont(
            fontFamily = "sans-serif",
            fontSizePx = 24f,
            fontWeight = 700,
            fontStyle = ImageBufferFontStyle.ITALIC,
            fontStretch = ImageBufferFontStretch.EXPANDED,
            fontVariantCaps = ImageBufferFontVariantCaps.SMALL_CAPS
        )
        val metrics = ctx.measureText("GenericUI 2D")
        assertTrue(metrics.width > 40f)
        assertTrue(metrics.fontBoundingBoxAscent > 0f)
        assertTrue(metrics.fontBoundingBoxDescent > 0f)

        ctx.setFillColor(RgbaColor.WHITE)
        ctx.fillText("GenericUI", 12f, 90f)

        // 5. All 26 Porter-Duff & W3C composite operations
        assertEquals(26, ImageBufferCompositeOperation.entries.size)
        for (op in ImageBufferCompositeOperation.entries) {
            val blended = op.blend(RgbaColor(200, 100, 50, 180), RgbaColor(40, 120, 220, 200))
            assertTrue(blended.r in 0..255)
            assertTrue(blended.g in 0..255)
            assertTrue(blended.b in 0..255)
            assertTrue(blended.a in 0..255)
        }

        // 6. Double-buffer flush clears dirty state
        buffer.swapBuffers()
        assertFalse(buffer.isDirty)
        assertEquals(1L, buffer.swapCount)
    }
}
