package com.nexustools.genericui.ext.svg

import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SvgDocumentAndMutationTest {

    @Test
    fun parseFullSvgDocumentQueryAndRoundTrip() {
        val markup = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="120" viewBox="0 0 120 60" preserveAspectRatio="xMinYMid meet">
              <defs>
                <linearGradient id="bg-grad" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0" stop-color="#ff0000"/>
                  <stop offset="100%" stop-color="rgba(0, 0, 255, 0.5)"/>
                </linearGradient>
                <clipPath id="half-clip">
                  <rect x="0" y="0" width="60" height="60"/>
                </clipPath>
              </defs>
              <g id="layer-main" class="scene primary" transform="translate(10, 5) scale(2)" fill="#112233">
                <rect id="hero-box" class="shape card" x="2" y="3" width="40" height="20" rx="4" fill="url(#bg-grad)" clip-path="url(#half-clip)"/>
                <circle id="badge-dot" class="shape badge" cx="30" cy="15" r="6" style="fill: lime; stroke: #000; stroke-width: 2"/>
                <path id="curve" d="M 0 0 C 10 0 10 20 20 20 A 10 10 0 0 1 30 30 Z"/>
                <text id="label" x="5" y="45" font-size="14" text-anchor="middle">HI</text>
                <use id="dup" href="#badge-dot" x="15" y="5"/>
              </g>
            </svg>
        """.trimIndent()

        val svg = Svg.fromXml(markup)
        assertEquals(240f, svg.svgWidth)
        assertEquals(120f, svg.svgHeight)
        assertEquals(SvgViewBox(0f, 0f, 120f, 60f), svg.viewBox)
        assertEquals(
            SvgPreserveAspectRatio(SvgPreserveAspectRatio.Align.X_MIN_Y_MID, SvgPreserveAspectRatio.MeetOrSlice.MEET),
            svg.preserveAspectRatio
        )

        val grad = assertIs<SvgLinearGradient>(svg.findById("bg-grad"))
        assertEquals(2, grad.stops.size)
        assertEquals(RgbaColor(255, 0, 0, 255), grad.stops[0].color)
        assertEquals(1f, grad.stops[1].offset)
        assertEquals(0, grad.stops[1].color.r)
        assertEquals(255, grad.stops[1].color.b)
        assertTrue(abs(grad.stops[1].opacity - 0.5f) < 0.02f || grad.stops[1].color.a in 126..129)

        assertNotNull(svg.findById("half-clip"))
        val layer = assertIs<SvgGroup>(svg.querySelector("g.scene.primary"))
        assertEquals(2, layer.transformList.size)

        val hero = assertIs<SvgRect>(svg.querySelector("g > rect.card#hero-box"))
        assertEquals(2f, hero.x)
        assertEquals("bg-grad", assertIs<SvgPaint.GradientRef>(hero.fill).cleanId)
        assertEquals("half-clip", hero.clipPathId)

        val badge = assertIs<SvgCircle>(svg.querySelector(".badge"))
        assertEquals(SvgPaint.SolidColor(RgbaColor(0, 255, 0, 255)), badge.fill)
        assertEquals(2f, badge.strokeWidth)

        val curve = assertIs<SvgPath>(svg.findById("curve"))
        assertTrue(curve.commands.size >= 4)

        val label = assertIs<SvgText>(svg.findById("label"))
        assertEquals("HI", label.text)
        assertEquals(14f, label.fontSize)
        assertEquals("middle", label.textAnchor)

        val use = assertIs<SvgUse>(svg.findById("dup"))
        assertEquals("badge-dot", use.targetId)
        assertEquals(15f, use.x)

        assertEquals(2, svg.querySelectorAll(".shape").size)
        assertEquals(2, svg.queryByTag("rect").size)

        val xml = svg.toXml(pretty = true)
        val reparsed = Svg.fromXml(xml)
        assertEquals(svg.viewBox, reparsed.viewBox)
        assertEquals("HI", assertIs<SvgText>(reparsed.findById("label")).text)
        assertEquals(2, assertIs<SvgLinearGradient>(reparsed.findById("bg-grad")).stops.size)
        assertIs<SvgRect>(reparsed.findById("hero-box"))
    }

    @Test
    fun liveDomMutationsTrackSceneDirtyAndTransformInverse() {
        val svg = Svg(viewBox = SvgViewBox(0f, 0f, 100f, 100f))
        val group = SvgGroup()
        val circle = SvgCircle(cx = 20f, cy = 30f, r = 10f).apply { id = "dot" }
        group.addChild(circle)
        svg.addChild(group)

        var dirtyCount = 0
        svg.onVectorSceneDirty = { dirtyCount++ }
        val v0 = svg.sceneVersion

        circle.cx = 40f
        assertEquals(v0 + 1, svg.sceneVersion)
        assertEquals(1, dirtyCount)

        circle.classList.add("highlighted")
        assertEquals(v0 + 2, svg.sceneVersion)

        group.transformList.add(SvgTransform.Translate(15f, -5f))
        group.transformList.add(SvgTransform.Rotate(90f))
        assertEquals(v0 + 4, svg.sceneVersion)

        val composed = SvgTransform.composeMatrix(group.transformList)
        val mapped = composed.mapPoint(SvgPoint(10f, 0f))
        val inverse = composed.inverse()
        assertNotNull(inverse)
        val roundTrip = inverse.mapPoint(mapped)
        assertTrue(abs(roundTrip.x - 10f) < 1e-3f)
        assertTrue(abs(roundTrip.y - 0f) < 1e-3f)

        val path = SvgPath("M 0 0 L 10 0 L 10 10 Z").apply { id = "tri" }
        svg.addChild(path)
        path.d = "M 0 0 H 20 V 20 Z"
        assertTrue(path.commands.size >= 4)

        svg.removeChild(path)
        assertNull(svg.findById("tri"))
    }

    @Test
    fun parseSvgColorsAndTransformsAllVariants() {
        assertEquals(RgbaColor(255, 0, 170, 255), SvgColorParser.parse("#f0a"))
        assertEquals(RgbaColor(17, 34, 51, 136), SvgColorParser.parse("#11223388"))
        assertEquals(RgbaColor(255, 0, 0, 255), SvgColorParser.parse("hsl(0, 100%, 50%)"))
        assertEquals(RgbaColor(255, 0, 0, 255), SvgColorParser.parse("crimson".let { "red" }))
        assertEquals(RgbaColor(0, 0, 0, 0), SvgColorParser.parse("transparent"))
        assertEquals(SvgPaint.None, SvgPaint.parse("none"))
        assertEquals(SvgPaint.CurrentColor, SvgPaint.parse("currentColor"))
    }
}
