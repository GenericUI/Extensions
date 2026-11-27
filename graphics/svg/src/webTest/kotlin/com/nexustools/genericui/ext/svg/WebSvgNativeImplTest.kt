package com.nexustools.genericui.ext.svg

import com.nexustools.genericui.component.htmlcompat.Div
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.svg.web.WebSvgDomBridge
import com.nexustools.genericui.ext.svg.web.WebSvgNativeImpl
import com.nexustools.genericui.ext.svg.web.WebSvgProvider
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.web.WebNativeFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WebSvgNativeImplTest {

    private class TestWebHostRoot(private val factory: NativeFactory) : Div() {
        override fun resolveActiveFactory(): NativeFactory? = factory
    }

    @AfterTest
    fun tearDown() {
        SvgExtension.resetProviderForTests()
    }

    @Test
    fun webSvgSyncsDomHierarchyMutationsAndEvents() {
        WebSvgProvider.install()
        val svg = Svg(
            viewBox = SvgViewBox(0f, 0f, 120f, 80f),
            svgWidth = 120f,
            svgHeight = 80f
        ).apply {
            id = "root-svg-host"
        }

        var groupClicks = 0
        var rectClicks = 0
        var entered = 0
        var left = 0

        svg.defs.addChild(
            SvgLinearGradient(id = "sky").apply {
                addStop(0f, RgbaColor(0, 120, 255, 255))
                addStop(1f, RgbaColor(255, 255, 255, 255))
            }
        )
        val group = SvgGroup().apply {
            id = "card-group"
            onClick = { groupClicks++ }
        }
        val rect = SvgRect(x = 10f, y = 10f, width = 60f, height = 40f).apply {
            id = "hero-rect"
            fill = SvgPaint.GradientRef("sky")
            onClick = { rectClicks++ }
            onPointerEnter = { entered++ }
            onPointerLeave = { left++ }
        }
        val label = SvgText(x = 20f, y = 65f, text = "OK", fontSize = 18f).apply {
            id = "status-text"
        }
        group.addChild(rect)
        svg.addChild(group)
        svg.addChild(label)

        val host = TestWebHostRoot(WebNativeFactory())
        host.addChild(svg)

        val impl = assertIs<WebSvgNativeImpl>(svg.ensureNativeImpl())
        assertEquals("genericui:svg", impl.nativeWidget.tagName)
        assertSame(rect, impl.hitTestViewport(30f, 25f))

        if (!WebSvgDomBridge.isBrowserEnvironment()) {
            assertEquals("svg", impl.svgRootElement.tagName)
            assertEquals("0 0 120 80", impl.svgRootElement.getAttribute("viewBox"))

            val domRect = assertNotNull(impl.findSvgDomById("hero-rect"))
            assertEquals("url(#sky)", domRect.getAttribute("fill"))
            assertEquals("OK", impl.findSvgDomById("status-text")?.textContent)

            // Mutate SVG scene graph and verify live sync onto DOM nodes
            rect.width = 90f
            label.text = "LIVE"
            assertEquals("90", impl.findSvgDomById("hero-rect")?.getAttribute("width"))
            assertEquals("LIVE", impl.findSvgDomById("status-text")?.textContent)

            val updatedRectDom = assertNotNull(impl.findSvgDomById("hero-rect"))
            updatedRectDom.dispatchEvent("mouseenter")
            updatedRectDom.click()
            updatedRectDom.dispatchEvent("mouseleave")

            assertEquals(1, entered)
            assertEquals(1, left)
            assertEquals(1, rectClicks)
            assertEquals(1, groupClicks)

            val html = impl.nativeWidget.toOuterHtml()
            assertTrue(html.contains("<svg"))
            assertTrue(html.contains("linearGradient"))
            assertTrue(html.contains("LIVE"))
        } else {
            assertNotNull(impl.nativeWidget.nativeBrowserPeer)
            rect.width = 90f
            assertSame(rect, impl.hitTestViewport(85f, 25f))
        }
    }
}
