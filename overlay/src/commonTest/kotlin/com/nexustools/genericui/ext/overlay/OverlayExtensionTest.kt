package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.layout.LayerSizingMode
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.event.KeyEvent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OverlayExtensionTest {

    @BeforeTest
    fun setUp() {
        UiTimerScheduler.clearAllTimers()
        OverlayExtension.activeApplication = null
        OverlayExtension.defaultThemeColorHex = "#0d6efd"
    }

    @AfterTest
    fun tearDown() {
        UiTimerScheduler.clearAllTimers()
        OverlayExtension.activeApplication = null
        OverlayExtension.defaultThemeColorHex = "#0d6efd"
    }

    @Test
    fun testOverlayTimeFormatterMinutesAndForcedHoursAndUnknown() {
        assertEquals("0:05", OverlayTimeFormatter.formatPosition(5.0, 120.0))
        assertEquals("2:00", OverlayTimeFormatter.formatDuration(5.0, 120.0))
        assertEquals("0:05 / 2:00", OverlayTimeFormatter.formatPositionAndDuration(5.0, 120.0))
        assertEquals("--:--", OverlayTimeFormatter.formatDuration(5.0, 0.0))
        assertEquals("0:05:00", OverlayTimeFormatter.formatPosition(300.0, 3600.0))
        assertEquals("1:00:00", OverlayTimeFormatter.formatDuration(300.0, 3600.0))
    }

    @Test
    fun testOverlayExtensionPureOverlayAndHostBoxAreTransparentWithNoDefaultOverlay() {
        val target = Container("gui-target").apply {
            widthPx = 1280
            heightPx = 720
        }
        val host = OverlayHostBox(content = target)
        assertNull(host.overlay)
        assertNull(host.activeOverlay)
        assertSame(target, host.targetComponent)
        assertSame(target, host.contentComponent)
        assertEquals("transparent", host.getStyleProperty("background"))
        assertEquals("transparent", host.getStyleProperty("background-color"))
        assertEquals("transparent", host.overlayRoot.getStyleProperty("background"))
        assertEquals(1280, host.overlayRoot.widthPx)
        assertEquals(720, host.overlayRoot.heightPx)

        val overlay = OverlayExtension.createOverlay(tagName = "test-overlay", host = host)
        assertEquals("transparent", overlay.getStyleProperty("background"))
        assertEquals("transparent", overlay.getStyleProperty("background-color"))
        assertSame(overlay, host.overlay)
        assertSame(overlay, host.activeOverlay)
        assertEquals(1280, overlay.targetWidthPx)
        assertEquals(720, overlay.targetHeightPx)
        assertEquals(1280, overlay.widthPx)
        assertEquals(720, overlay.heightPx)

        host.detachOverlay()
        assertNull(host.overlay)
        assertNull(host.activeOverlay)
        host.dispose()
    }

    @Test
    fun testOverlayFillAndFloatingLayersAndRenderResizeSynchronization() {
        val overlay = Overlay()
        val fillChild = Container("gui-fill-child")
        val floatChild = Container("gui-float-child")

        val fillElement = overlay.addElement(
            component = fillChild,
            constraint = LayerConstraint.fill(zIndex = 2, passThroughPointerEvents = true)
        )
        var lastRenderFrameTs = -1L
        var lastResizeW = -1
        var lastResizeH = -1

        val floatElement = object : OverlayElement(
            elementId = "floating-bubble",
            component = floatChild,
            constraint = LayerConstraint.float(
                alignment = LayerAlignment.BOTTOM_START,
                offsetXPx = 24,
                offsetYPx = -36,
                zIndex = 5
            )
        ) {
            override fun onTargetRenderFrame(frameTimestampMs: Long, targetWidthPx: Int, targetHeightPx: Int) {
                lastRenderFrameTs = frameTimestampMs
            }

            override fun onTargetResize(targetWidthPx: Int, targetHeightPx: Int) {
                lastResizeW = targetWidthPx
                lastResizeH = targetHeightPx
            }
        }
        overlay.addElement(floatElement)

        assertEquals(2, overlay.elements.size)
        assertSame(fillElement, overlay.elements[0])
        assertSame(floatElement, overlay.getElement("floating-bubble"))
        assertEquals(LayerSizingMode.FILL, overlay.getLayerConstraint(fillChild)?.sizingMode)
        assertEquals(LayerSizingMode.FLOAT, overlay.getLayerConstraint(floatChild)?.sizingMode)
        assertEquals(LayerAlignment.BOTTOM_START, overlay.getLayerConstraint(floatChild)?.alignment)

        val host = OverlayHostBox(initialOverlay = overlay)
        host.syncWithTargetResize(1920, 1080)
        assertEquals(1920, overlay.targetWidthPx)
        assertEquals(1080, overlay.targetHeightPx)
        assertEquals(1920, lastResizeW)
        assertEquals(1080, lastResizeH)

        host.syncWithTargetRender(frameTimestampMs = 42_000L)
        assertEquals(42_000L, overlay.lastFrameTimestampMs)
        assertEquals(42_000L, lastRenderFrameTs)

        assertTrue(overlay.removeElement("floating-bubble"))
        assertEquals(1, overlay.elements.size)
        assertNull(overlay.getElement("floating-bubble"))
        host.dispose()
    }

    @Test
    fun testAutoHideAndKeyboardShortcutRouting() {
        val overlay = Overlay()
        var playPauseCount = 0
        var fullscreenCount = 0
        var muteCount = 0
        var lastSeekSec = -1.0

        overlay.updateState(
            OverlayState(
                title = "Demo Video",
                isPlaying = true,
                positionSec = 30.0,
                durationSec = 120.0,
                volumePercent = 80
            )
        )
        overlay.updateActions(
            OverlayActions(
                onPlayPause = { playPauseCount++ },
                onToggleFullscreen = { fullscreenCount++ },
                onMuteToggle = { muteCount++ },
                onSeekToSec = { lastSeekSec = it }
            )
        )

        assertTrue(overlay.inputRouter.handleKeyDown(KeyEvent(key = "Space"), overlay.state, overlay.actions))
        assertEquals(1, playPauseCount)
        assertTrue(overlay.inputRouter.handleKeyDown(KeyEvent(key = "f"), overlay.state, overlay.actions))
        assertEquals(1, fullscreenCount)
        assertTrue(overlay.inputRouter.handleKeyDown(KeyEvent(key = "m"), overlay.state, overlay.actions))
        assertEquals(1, muteCount)
        assertTrue(overlay.inputRouter.handleKeyDown(KeyEvent(key = "ArrowRight"), overlay.state, overlay.actions))
        assertEquals(40.0, lastSeekSec)

        overlay.autoHideController.setPinned("scrubbing", true)
        assertTrue(overlay.autoHideController.isPinned)
        overlay.autoHideController.hideOverlay()
        assertTrue(overlay.autoHideController.isOverlayVisible)

        overlay.autoHideController.setPinned("scrubbing", false)
        assertFalse(overlay.autoHideController.isPinned)
        overlay.autoHideController.hideOverlay(instant = true)
        assertFalse(overlay.autoHideController.isOverlayVisible)

        overlay.dispose()
    }
}
