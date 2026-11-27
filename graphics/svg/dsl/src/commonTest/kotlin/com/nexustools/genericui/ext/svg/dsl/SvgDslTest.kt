package com.nexustools.genericui.ext.svg.dsl

import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.svg.*
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SvgDslTest {

    @Test
    fun buildStandaloneSvgWithDefsTransformsAndRender() {
        val vector = buildSvg(width = 100f, height = 100f, id = "logo") {
            viewBox(0f, 0f, 100f, 100f)
            defs {
                linearGradient(id = "grad", x1 = 0f, y1 = 0f, x2 = 1f, y2 = 0f) {
                    stop(0f, "#ff0000")
                    stop(1f, RgbaColor(0, 0, 255, 255))
                }
                clipPath(id = "clip-box") {
                    rect(x = 0f, y = 0f, width = 80f, height = 100f)
                }
            }
            rect(x = 0f, y = 0f, width = 100f, height = 100f, id = "backdrop", clipPathId = "clip-box") {
                fillGradient("grad")
            }
            g(id = "shapes", transform = "translate(10, 10)") {
                circle(cx = 20f, cy = 20f, r = 10f, id = "orb") {
                    fillColor(RgbaColor(0, 255, 0, 255))
                }
                polygon(points = "40,10 60,10 50,30", id = "tri") {
                    fill("#ffffff")
                }
                line(x1 = 0f, y1 = 50f, x2 = 60f, y2 = 50f) {
                    stroke("#ffff00", width = 3f)
                }
                text(x = 5f, y = 75f, content = "GO", fontSize = 16f) {
                    fill("#000000")
                }
            }
        }

        assertEquals(2, vector.defs.children.size)
        assertIs<SvgCircle>(vector.findById("orb"))
        assertIs<SvgPolygon>(vector.findById("tri"))

        val buf = vector.renderToImageBuffer(100, 100)
        // Left gradient clipped area vs clipped-out area at x = 90
        assertTrue(buf.getPixel(5, 95).r > 200)
        assertEquals(0, buf.getPixel(90, 95).a)

        // Green orb translated by (10, 10) from cx=20, cy=20 -> (30, 30)
        assertTrue(buf.getPixel(30, 30).g > 240)
        assertSame(vector.findById("orb"), vector.hitTest(30f, 30f, 100f, 100f))
        assertNull(vector.hitTest(90f, 95f, 100f, 100f))
    }

    @Test
    fun mountSvgInsideGenericUiContainerDsl() {
        val container = buildDiv(id = "root") {
            svg(viewBox = SvgViewBox(0f, 0f, 50f, 50f), id = "inline-icon", widthPx = 50, heightPx = 50) {
                path("M 5 5 L 45 5 L 45 45 Z", id = "wing") {
                    fill("red")
                }
            }
            svgFromXml("""<svg viewBox="0 0 20 20"><circle id="dot" cx="10" cy="10" r="5" fill="blue"/></svg>""") {
                val dot = findById("dot") as SvgCircle
                dot.r = 8f
            }
        }

        assertEquals(2, container.children.size)
        val first = assertIs<Svg>(container.children[0])
        assertEquals("inline-icon", first.id)
        assertEquals(50, first.widthPx)
        assertIs<SvgPath>(first.findById("wing"))

        val second = assertIs<Svg>(container.children[1])
        val dot = assertIs<SvgCircle>(second.findById("dot"))
        assertEquals(8f, dot.r)
    }
}
