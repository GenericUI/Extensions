package com.nexustools.genericui.ext.svg

import com.nexustools.genericui.style.color.RgbaColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SvgRasterizerAndHitTestTest {

    @Test
    fun renderShapesEvenOddHoleAndClipPath() {
        val svg = Svg(viewBox = SvgViewBox(0f, 0f, 100f, 100f)).apply {
            defs.addChild(
                SvgClipPath(id = "left-half").apply {
                    addChild(SvgRect(x = 0f, y = 0f, width = 50f, height = 100f))
                }
            )
            addChild(
                SvgRect(x = 0f, y = 0f, width = 100f, height = 100f).apply {
                    id = "clipped-rect"
                    fill = SvgPaint.SolidColor(RgbaColor(255, 0, 0, 255))
                    clipPathId = "left-half"
                }
            )
            addChild(
                SvgPath("M 55 10 H 95 V 90 H 55 Z M 65 30 V 70 H 85 V 30 Z").apply {
                    id = "donut"
                    fill = SvgPaint.SolidColor(RgbaColor(0, 0, 255, 255))
                    fillRule = SvgFillRule.EVENODD
                }
            )
        }

        val buffer = svg.renderToImageBuffer(100, 100)
        assertEquals(100, buffer.width)
        assertEquals(100, buffer.height)

        // Clipped red rect: inside left clip half -> opaque red; outside left half -> transparent
        val insideClip = buffer.getPixel(25, 50)
        assertTrue(insideClip.r > 240 && insideClip.g < 15 && insideClip.b < 15 && insideClip.a > 240)

        val clippedOut = buffer.getPixel(52, 50)
        assertEquals(0, clippedOut.a)

        // EvenOdd donut on right half: outer ring is blue, interior hole center (75, 50) is transparent
        val ring = buffer.getPixel(60, 50)
        assertTrue(ring.b > 240 && ring.r < 15 && ring.a > 240)

        val hole = buffer.getPixel(75, 50)
        assertEquals(0, hole.a)

        // Hit-testing respects clip bounds & evenodd hole!
        assertSame(svg.findById("clipped-rect"), svg.hitTest(25f, 50f, 100f, 100f))
        assertNull(svg.hitTest(52f, 50f, 100f, 100f))
        assertSame(svg.findById("donut"), svg.hitTest(60f, 50f, 100f, 100f))
        assertNull(svg.hitTest(75f, 50f, 100f, 100f))
    }

    @Test
    fun renderLinearAndRadialGradientsAndDashedStroke() {
        val svg = Svg(viewBox = SvgViewBox(0f, 0f, 100f, 100f)).apply {
            defs.addChild(
                SvgLinearGradient(id = "lin", x1 = 0f, y1 = 0f, x2 = 1f, y2 = 0f).apply {
                    addStop(0f, RgbaColor(255, 0, 0, 255))
                    addStop(1f, RgbaColor(0, 0, 255, 255))
                }
            )
            defs.addChild(
                SvgRadialGradient(id = "rad", cx = 0.5f, cy = 0.5f, r = 0.5f).apply {
                    addStop(0f, RgbaColor(255, 255, 255, 255))
                    addStop(1f, RgbaColor(0, 128, 0, 255))
                }
            )
            addChild(
                SvgRect(x = 0f, y = 0f, width = 100f, height = 40f).apply {
                    fill = SvgPaint.GradientRef("lin")
                }
            )
            addChild(
                SvgCircle(cx = 50f, cy = 70f, r = 20f).apply {
                    fill = SvgPaint.GradientRef("rad")
                }
            )
            addChild(
                SvgLine(x1 = 10f, y1 = 95f, x2 = 90f, y2 = 95f).apply {
                    stroke = SvgPaint.SolidColor(RgbaColor(0, 255, 0, 255))
                    strokeWidth = 4f
                    strokeDasharray = listOf(10f, 10f)
                }
            )
        }

        val buffer = svg.renderToImageBuffer(100, 100)
        val leftGrad = buffer.getPixel(5, 20)
        val midGrad = buffer.getPixel(50, 20)
        val rightGrad = buffer.getPixel(95, 20)
        assertTrue(leftGrad.r > 210 && leftGrad.b < 45)
        assertTrue(midGrad.r in 90..165 && midGrad.b in 90..165)
        assertTrue(rightGrad.b > 210 && rightGrad.r < 45)

        val radCenter = buffer.getPixel(50, 70)
        val radOuter = buffer.getPixel(68, 70)
        assertTrue(radCenter.r > 200 && radCenter.g > 200 && radCenter.b > 200)
        assertTrue(radOuter.g > 90 && radOuter.r < 80)

        // First dash [10..20] painted green, first gap [20..30] transparent
        val onDash = buffer.getPixel(15, 95)
        val inGap = buffer.getPixel(25, 95)
        assertTrue(onDash.g > 200 && onDash.a > 200)
        assertEquals(0, inGap.a)
    }

    @Test
    fun renderTransformedGroupTextAndViewportLetterboxHitTest() {
        val svg = Svg(viewBox = SvgViewBox(0f, 0f, 100f, 100f)).apply {
            addChild(
                SvgGroup().apply {
                    id = "rot-group"
                    transformList.add(SvgTransform.Translate(50f, 50f))
                    transformList.add(SvgTransform.Rotate(90f))
                    // Local (0..20, 0..10) rotated +90deg around origin -> (-10..0, 0..20)
                    // Translated by (50, 50) -> (40..50, 50..70)
                    addChild(
                        SvgRect(x = 0f, y = 0f, width = 20f, height = 10f).apply {
                            id = "rotated-rect"
                            fill = SvgPaint.SolidColor(RgbaColor(255, 165, 0, 255))
                        }
                    )
                }
            )
            addChild(
                SvgText(x = 10f, y = 30f, text = "HI", fontSize = 20f).apply {
                    id = "greeting"
                    fill = SvgPaint.SolidColor(RgbaColor(255, 255, 255, 255))
                }
            )
        }

        // Render in 200x100 viewport (letterboxed X: 50..150 maps to 0..100 viewBox)
        val buffer = svg.renderToImageBuffer(200, 100)

        // Transformed rect at viewBox (45, 60) -> viewport (50 + 45 = 95, 60)
        val rectPixel = buffer.getPixel(95, 60)
        assertTrue(rectPixel.r > 230 && rectPixel.g in 140..190 && rectPixel.a > 230)

        // Builtin stroke text "HI" around viewBox x=10..35, y=10..30 -> viewport x=60..85, y=10..30
        var litTextPixels = 0
        for (py in 10..32) {
            for (px in 58..90) {
                if (buffer.getPixel(px, py).a > 50) litTextPixels++
            }
        }
        assertTrue(litTextPixels > 20, "Expected visible built-in glyph strokes, found $litTextPixels pixels")

        // Hit-test inverse transforms through letterboxed viewport + group transform!
        val hit = svg.hitTest(viewportX = 95f, viewportY = 60f, viewportWidth = 200f, viewportHeight = 100f)
        assertSame(svg.findById("rotated-rect"), hit)
        assertNull(svg.hitTest(viewportX = 10f, viewportY = 10f, viewportWidth = 200f, viewportHeight = 100f))
    }
}
