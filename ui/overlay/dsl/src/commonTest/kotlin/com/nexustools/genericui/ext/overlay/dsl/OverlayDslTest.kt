package com.nexustools.genericui.ext.overlay.dsl

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayerSizingMode
import com.nexustools.genericui.ext.overlay.Overlay
import com.nexustools.genericui.ext.overlay.OverlayHostBox
import com.nexustools.genericui.ext.overlay.OverlayState
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OverlayDslTest {

    @Test
    fun testPureOverlayAndOverlayHostBoxDslBuilders() {
        val target = Container("gui-target")
        val fillChild = Container("gui-fill-layer")
        val floatChild = Container("gui-float-layer")

        val standaloneOverlay = buildOverlay(
            state = OverlayState(title = "Overlay Title", durationSec = 60.0),
            themeColorHex = "#38bdf8"
        ) {
            fillLayer(fillChild, zIndex = 1, passThroughPointerEvents = true)
            floatingLayer(
                component = floatChild,
                alignment = LayerAlignment.TOP_END,
                offsetXPx = -16,
                offsetYPx = 16,
                zIndex = 4
            )
        }

        assertEquals("Overlay Title", standaloneOverlay.state.title)
        assertEquals("#38bdf8", standaloneOverlay.themeColorHex)
        assertEquals(RgbaColor.TRANSPARENT, standaloneOverlay.style.backgroundColor)
        assertEquals(2, standaloneOverlay.elements.size)
        assertEquals(LayerSizingMode.FILL, standaloneOverlay.getLayerConstraint(fillChild)?.sizingMode)
        assertEquals(LayerSizingMode.FLOAT, standaloneOverlay.getLayerConstraint(floatChild)?.sizingMode)
        assertEquals(LayerAlignment.TOP_END, standaloneOverlay.getLayerConstraint(floatChild)?.alignment)

        val root = Container("root")
        val emptyHost = root.overlayHostBox(
            content = target,
            id = "emptyHost"
        )
        val boundHost = root.overlayHostBox(
            content = Container("gui-video"),
            initialOverlay = standaloneOverlay,
            themeColorHex = "#38bdf8",
            id = "boundHost"
        )
        val inlineOverlay = root.overlay(id = "inlineOverlay")

        assertEquals(3, root.children.size)
        assertNull(emptyHost.overlay)
        assertSame(target, emptyHost.targetComponent)
        assertSame(standaloneOverlay, boundHost.overlay)
        assertEquals("#38bdf8", boundHost.themeColorHex)
        assertTrue(inlineOverlay.elements.isEmpty())
        assertEquals(RgbaColor.TRANSPARENT, inlineOverlay.style.backgroundColor)

        boundHost.dispose()
        emptyHost.dispose()
        inlineOverlay.dispose()
    }
}
