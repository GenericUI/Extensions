package com.nexustools.genericui.ext.overlay.dsl

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.overlay.MobileOverlayPreset
import com.nexustools.genericui.ext.overlay.OverlayBadgeVariant
import com.nexustools.genericui.ext.overlay.WebSystemOverlayPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class OverlayDslTest {

    @Test
    fun testOverlayDslBuilders() {
        val root = Container("root")
        val hostBox = root.overlayHostBox(themeColorHex = "#3388FF") {
            val preset = webSystemOverlayPreset {
                addBottomPillButton("custom-pill", "Action", OverlayBadgeVariant.ACCENT) {}
            }
            assertIs<WebSystemOverlayPreset>(preset)
        }
        assertEquals("#3388FF", hostBox.themeColorHex)
        assertNotNull(hostBox.activeOverlay)

        val mobile = hostBox.defaultOverlayPreset(BackendTarget.Android)
        assertIs<MobileOverlayPreset>(mobile)

        hostBox.dispose()
    }
}
