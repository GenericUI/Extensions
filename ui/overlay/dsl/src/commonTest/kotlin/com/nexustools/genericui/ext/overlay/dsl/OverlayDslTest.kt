package com.nexustools.genericui.ext.overlay.dsl

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayerSizingMode
import com.nexustools.genericui.ext.overlay.Overlay
import com.nexustools.genericui.ext.overlay.OverlayExtension
import com.nexustools.genericui.ext.overlay.OverlayHostBox
import com.nexustools.genericui.ext.overlay.OverlayState
import com.nexustools.genericui.ext.overlay.OverlayWindowTitleBarConfig
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

        standaloneOverlay.applyStyleSheet(OverlayExtension.defaultStyleSheet())
        assertEquals("Overlay Title", standaloneOverlay.state.title)
        assertEquals("#38bdf8", standaloneOverlay.themeColorHex)
        assertEquals(RgbaColor.TRANSPARENT, standaloneOverlay.computedStyle?.backgroundColor)
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
        inlineOverlay.applyStyleSheet(OverlayExtension.defaultStyleSheet())

        assertEquals(3, root.children.size)
        assertNull(emptyHost.overlay)
        assertSame(target, emptyHost.targetComponent)
        assertSame(standaloneOverlay, boundHost.overlay)
        assertEquals("#38bdf8", boundHost.themeColorHex)
        assertTrue(inlineOverlay.elements.isEmpty())
        assertEquals(RgbaColor.TRANSPARENT, inlineOverlay.computedStyle?.backgroundColor)

        boundHost.dispose()
        emptyHost.dispose()
        inlineOverlay.dispose()

        val titleBarHost = root.overlayHostBox(
            content = Container("gui-titlebar-target"),
            windowTitleBarEnabled = true,
            windowTitleBarConfig = OverlayWindowTitleBarConfig(
                enabled = true,
                heightPx = 36,
                title = "DSL Window Title"
            ),
            id = "titleBarHost"
        )
        assertTrue(titleBarHost.windowTitleBarEnabled)
        assertTrue(titleBarHost.windowTitleBar.config.enabled)
        assertEquals(36, titleBarHost.windowTitleBarConfig.heightPx)
        assertEquals("DSL Window Title", titleBarHost.windowTitleBarConfig.title)
        assertTrue(titleBarHost.overlayRoot.children.firstOrNull() != null)
        val titledOverlay = root.overlay(windowTitleBarEnabled = true, id = "titledOverlay")
        assertTrue(titledOverlay.windowTitleBarEnabled)
        titleBarHost.dispose()
        titledOverlay.dispose()
    }
}
