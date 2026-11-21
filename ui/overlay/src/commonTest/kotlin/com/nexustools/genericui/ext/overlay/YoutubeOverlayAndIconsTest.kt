package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.overlay.res.R
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class YoutubeOverlayAndIconsTest {

    @BeforeTest
    fun setUp() {
        UiTimerScheduler.clearAllTimers()
    }

    @AfterTest
    fun tearDown() {
        UiTimerScheduler.clearAllTimers()
    }

    @Test
    fun testAll9VectorIconsRegisteredInScopedRAndPureBezierZeroArc() {
        val expectedMapping = mapOf(
            OverlayIconType.PLAY to R.drawable.ic_play,
            OverlayIconType.PAUSE to R.drawable.ic_pause,
            OverlayIconType.REWIND to R.drawable.ic_rewind,
            OverlayIconType.FAST_FORWARD to R.drawable.ic_fast_forward,
            OverlayIconType.STOP to R.drawable.ic_stop,
            OverlayIconType.NEXT to R.drawable.ic_next,
            OverlayIconType.PREVIOUS to R.drawable.ic_previous,
            OverlayIconType.FULLSCREEN_ENTER to R.drawable.ic_fullscreen_enter,
            OverlayIconType.FULLSCREEN_EXIT to R.drawable.ic_fullscreen_exit
        )
        assertEquals(9, OverlayIconType.entries.size)
        val allowedPathCommands = setOf('M', 'L', 'H', 'V', 'C', 'Q', 'Z')
        val pathAttrRegex = Regex("""d\s*=\s*"([^"]+)"""")

        for (type in OverlayIconType.entries) {
            val resId = OverlayIcons.resourceFor(type)
            assertEquals(expectedMapping[type], resId)
            assertEquals("gui/ext/ui/overlay/drawable/${resId.name}.svg", resId.canonicalRelativePath)
            assertEquals("gui_ext_ui_overlay_drawable_${resId.name}", resId.flatName)
            assertEquals("genericui-ext-overlay", resId.nativeLibraryGroupId)

            val drawable = Resources.getDrawable(resId)
            assertNotNull(drawable, "DrawableResource missing for $type ($resId)")
            assertEquals("svg", drawable.format)
            assertEquals(24f, drawable.viewBoxWidth)
            assertEquals(24f, drawable.viewBoxHeight)

            val svg = drawable.svgSource
            assertNotNull(svg, "svgSource missing for $type")
            assertFalse(svg.contains("<circle", ignoreCase = true), "Unexpected <circle> in ${resId.name}")
            assertFalse(svg.contains("<rect", ignoreCase = true), "Unexpected <rect> in ${resId.name}")
            assertFalse(svg.contains("<ellipse", ignoreCase = true), "Unexpected <ellipse> in ${resId.name}")
            assertFalse(svg.contains("<polygon", ignoreCase = true), "Unexpected <polygon> in ${resId.name}")
            assertFalse(svg.contains("<polyline", ignoreCase = true), "Unexpected <polyline> in ${resId.name}")
            assertFalse(svg.contains("<line", ignoreCase = true), "Unexpected <line> in ${resId.name}")

            val matches = pathAttrRegex.findAll(svg).toList()
            assertTrue(matches.isNotEmpty(), "Missing <path d=\"...\"> in ${resId.name}")
            for (match in matches) {
                val pathData = match.groupValues[1]
                for (ch in pathData) {
                    if (ch.isLetter()) {
                        assertTrue(
                            ch in allowedPathCommands,
                            "Disallowed SVG path command '$ch' in ${resId.name}: $pathData"
                        )
                    }
                }
            }
        }

        val enterSvg = Resources.getDrawable(R.drawable.ic_fullscreen_enter)?.svgSource.orEmpty()
        val exitSvg = Resources.getDrawable(R.drawable.ic_fullscreen_exit)?.svgSource.orEmpty()
        assertNotEquals(enterSvg, exitSvg)
    }

    @Test
    fun testOverlayControlButtonsZeroDefaultTooltipsAndVariants() {
        val frostedFilter = CssFilter(blur = CssLength.px(16f), saturate = 1.2f)
        val buttons = listOf(
            OverlayControlButtons.play(),
            OverlayControlButtons.pause(),
            OverlayControlButtons.playPause(),
            OverlayControlButtons.rewind(),
            OverlayControlButtons.fastForward(),
            OverlayControlButtons.stop(),
            OverlayControlButtons.next(),
            OverlayControlButtons.previous(),
            OverlayControlButtons.fullscreenEnter(),
            OverlayControlButtons.fullscreenExit(),
            OverlayControlButtons.fullscreenToggle()
        )
        for (btn in buttons) {
            assertNull(btn.tooltipText, "Overlay button must never define a default tooltipText")
            assertEquals(40, btn.widthPx)
            assertEquals(40, btn.heightPx)
        }

        val centerPrimary = OverlayControlButton(
            iconType = OverlayIconType.PLAY,
            variant = OverlayButtonVariant.CENTER_PRIMARY,
            backdropFilter = frostedFilter
        )
        assertNull(centerPrimary.tooltipText)
        assertEquals(64, centerPrimary.widthPx)
        assertEquals(64, centerPrimary.heightPx)
        assertEquals("primary-center-action", centerPrimary.getAttribute("data-overlay-role"))
        assertEquals(frostedFilter, centerPrimary.backdropFilter)
        assertEquals(RgbaColor(20, 24, 32, 97), centerPrimary.backgroundColor)
        assertEquals(RgbaColor(255, 255, 255, 56), centerPrimary.borderColor)

        val centerSecondary = OverlayControlButton(
            iconType = OverlayIconType.REWIND,
            variant = OverlayButtonVariant.CENTER_SECONDARY
        )
        assertNull(centerSecondary.tooltipText)
        assertEquals(56, centerSecondary.widthPx)
        assertEquals(56, centerSecondary.heightPx)
        assertNull(centerSecondary.backdropFilter)

        val innerPillBtn = OverlayControlButton(
            iconType = OverlayIconType.NEXT,
            variant = OverlayButtonVariant.INNER_PILL,
            tooltipText = "Explicit Tooltip"
        )
        assertEquals("Explicit Tooltip", innerPillBtn.tooltipText)
        assertEquals(32, innerPillBtn.widthPx)
        assertEquals(32, innerPillBtn.heightPx)
        assertEquals(0f, innerPillBtn.borderWidthPx)
        assertEquals(CssBorderStyle.NONE, innerPillBtn.borderStyle)
        assertEquals(RgbaColor.TRANSPARENT, innerPillBtn.backgroundColor)

        val controlsPill = OverlayControlButtons.createControlsPill()
        assertEquals("controls-right", controlsPill.getAttribute("data-overlay-role"))
        assertEquals(40, controlsPill.heightPx)

        val timePill = OverlayControlButtons.createTimePill("1:23 / 4:56")
        assertEquals("time-readout", timePill.getAttribute("data-overlay-role"))
        assertEquals(40, timePill.heightPx)

        val badge4k = OverlayControlButtons.createOutlinedBadge("4K", id = "badge-4k")
        assertNull(badge4k.tooltipText)
        assertEquals("4K", badge4k.text)
        assertEquals("outlined-feature-badge", badge4k.getAttribute("data-overlay-role"))
    }

    @Test
    fun testPlayPauseAndFullscreenButtonStateSwitching() {
        var playPauseClicks = 0
        val playPauseBtn = OverlayPlayPauseButton(isPlaying = false) {
            playPauseClicks++
        }
        assertNull(playPauseBtn.tooltipText)
        assertEquals(R.drawable.ic_play, playPauseBtn.iconRes)
        playPauseBtn.isPlaying = true
        assertEquals(R.drawable.ic_pause, playPauseBtn.iconRes)
        playPauseBtn.triggerClick()
        assertEquals(1, playPauseClicks)

        val toggleEvents = mutableListOf<Boolean>()
        val fullscreenBtn = OverlayFullscreenButton(isFullscreen = false) { next ->
            toggleEvents.add(next)
        }
        assertNull(fullscreenBtn.tooltipText)
        assertEquals(R.drawable.ic_fullscreen_enter, fullscreenBtn.iconRes)
        assertFalse(fullscreenBtn.isFullscreen)

        fullscreenBtn.triggerClick()
        assertTrue(fullscreenBtn.isFullscreen)
        assertEquals(R.drawable.ic_fullscreen_exit, fullscreenBtn.iconRes)
        assertEquals(listOf(true), toggleEvents)

        fullscreenBtn.triggerClick()
        assertFalse(fullscreenBtn.isFullscreen)
        assertEquals(R.drawable.ic_fullscreen_enter, fullscreenBtn.iconRes)
        assertEquals(listOf(true, false), toggleEvents)

        fullscreenBtn.isFullscreen = true
        assertEquals(R.drawable.ic_fullscreen_exit, fullscreenBtn.iconRes)
        assertEquals(listOf(true, false), toggleEvents)
    }

    @Test
    fun testYoutubePlayerOverlayLayersAutoHideCaptionsCursorPopoversAndTimelinePlacement() {
        val overlay = YoutubePlayerOverlay(
            config = YoutubeOverlayConfig(
                showPrevious = true,
                showStop = true,
                showRewind = true,
                showFastForward = true
            )
        )
        val cursorEvents = mutableListOf<Boolean>()
        overlay.onCursorVisibilityChanged = { cursorEvents.add(it) }

        val builtInButtons = listOf(
            overlay.previousButton,
            overlay.playPauseButton,
            overlay.stopButton,
            overlay.rewindButton,
            overlay.fastForwardButton,
            overlay.nextButton,
            overlay.fullscreenButton,
            overlay.centerPreviousButton,
            overlay.centerRewindButton,
            overlay.centerPlayPauseButton,
            overlay.centerStopButton,
            overlay.centerFastForwardButton,
            overlay.centerNextButton
        )
        for (btn in builtInButtons) {
            assertNull(btn.tooltipText, "Built-in button ${btn.id} must have null tooltipText")
        }

        // Layer structure
        val hudConstraint = overlay.getLayerConstraint(overlay.hudControlsContainer)
        val subConstraint = overlay.getLayerConstraint(overlay.subtitleBanner)
        assertEquals(10, hudConstraint?.zIndex)
        assertEquals(15, subConstraint?.zIndex)
        assertEquals(LayerAlignment.BOTTOM_CENTER, subConstraint?.alignment)
        assertTrue(subConstraint?.passThroughPointerEvents == true)
        assertEquals("controls-layer", overlay.hudControlsContainer.getAttribute("data-overlay-role"))
        assertEquals("caption-banner", overlay.subtitleBanner.getAttribute("data-overlay-role"))
        assertEquals("top-bar", overlay.topBar.getAttribute("data-overlay-role"))
        assertEquals("center-zone", overlay.centerZone.getAttribute("data-overlay-role"))
        assertEquals("bottom-bar", overlay.bottomBar.getAttribute("data-overlay-role"))
        assertEquals("seek-container", overlay.timelineContainer.getAttribute("data-overlay-role"))
        assertEquals("hover-bubble", overlay.seekHoverBubble.getAttribute("data-overlay-role"))
        assertEquals("controls-left", overlay.leftControlsRow.getAttribute("data-overlay-role"))
        assertEquals("controls-right", overlay.rightControlsRow.getAttribute("data-overlay-role"))
        assertEquals("time-readout", overlay.timePillContainer.getAttribute("data-overlay-role"))
        assertSame(overlay.hudControlsContainer, overlay.autoHideController.targetComponent)

        // Persistent captions survive HUD auto-hide while outer overlay stays hit-testable and cursor hides
        overlay.setSubtitleCueText("Subtitles stay visible")
        assertTrue(overlay.subtitleBanner.isVisible)
        assertEquals("Subtitles stay visible", overlay.subtitleBanner.text)

        overlay.hideHud(instant = true)
        assertFalse(overlay.isHudVisible)
        assertEquals(0.0f, overlay.hudControlsContainer.opacity)
        assertEquals(CssPointerEvents.NONE, overlay.hudControlsContainer.style.pointerEvents)
        assertEquals(1.0f, overlay.opacity)
        assertTrue(overlay.pointerEventsEnabled)
        assertTrue(overlay.subtitleBanner.isVisible)
        assertFalse(overlay.isCursorVisible)
        assertEquals("none", overlay.cursor)
        assertTrue(overlay.hasClass("hud-cursor-hidden"))
        assertEquals(listOf(false), cursorEvents)

        overlay.notifyUserActivity()
        assertTrue(overlay.isHudVisible)
        assertEquals(1.0f, overlay.hudControlsContainer.opacity)
        assertEquals(CssPointerEvents.AUTO, overlay.hudControlsContainer.style.pointerEvents)
        assertTrue(overlay.isCursorVisible)
        assertEquals("default", overlay.cursor)
        assertFalse(overlay.hasClass("hud-cursor-hidden"))
        assertEquals(listOf(false, true), cursorEvents)

        // Popover management pins autoHideController
        val settingsPanel = Container("settings-popover")
        overlay.registerPopoverLayer("settings", settingsPanel)
        assertFalse(settingsPanel.isVisible)
        assertFalse(overlay.autoHideController.isPinned)

        overlay.setPopoverOpen("settings", true)
        assertTrue(settingsPanel.isVisible)
        assertTrue(overlay.isPopoverOpen("settings"))
        assertTrue(overlay.autoHideController.isPinned)
        assertEquals("true", overlay.getAttribute("popupOpen"))
        overlay.hideHud(instant = true)
        assertTrue(overlay.isHudVisible, "HUD must remain visible while a popover is open")

        overlay.closeAllPopovers()
        assertFalse(settingsPanel.isVisible)
        assertFalse(overlay.isAnyPopoverOpen)
        assertFalse(overlay.autoHideController.isPinned)
        assertEquals("false", overlay.getAttribute("popupOpen"))

        // Hover bubble clamping [2%..98%]
        overlay.showTimelineHover(0.0, "0:00")
        assertTrue(overlay.seekHoverBubble.isVisible)
        assertEquals("0:00", overlay.seekHoverBubble.text)
        assertEquals("2.0", overlay.seekHoverBubble.getAttribute("layerLeftPercent"))

        overlay.showTimelineHover(1.0, "10:00")
        assertEquals("10:00", overlay.seekHoverBubble.text)
        assertEquals("98.0", overlay.seekHoverBubble.getAttribute("layerLeftPercent"))

        overlay.showTimelineHover(0.5, "5:00")
        assertEquals("50.0", overlay.seekHoverBubble.getAttribute("layerLeftPercent"))

        overlay.hideTimelineHover()
        assertFalse(overlay.seekHoverBubble.isVisible)

        // Timeline placement ordering
        overlay.setTimelinePlacement(YoutubeTimelinePlacement.ABOVE_CONTROLS)
        assertTrue(
            overlay.bottomBar.children.indexOf(overlay.timelineContainer) <
                overlay.bottomBar.children.indexOf(overlay.controlsRow)
        )
        overlay.setTimelinePlacement(YoutubeTimelinePlacement.BELOW_CONTROLS)
        assertTrue(
            overlay.bottomBar.children.indexOf(overlay.controlsRow) <
                overlay.bottomBar.children.indexOf(overlay.timelineContainer)
        )

        // State synchronization
        overlay.updateState(
            OverlayState(
                title = "Episode 1",
                subtitle = "1080p",
                statusText = "Buffering...",
                isPlaying = true,
                isLoading = true,
                positionSec = 65.0,
                durationSec = 600.0,
                isFullscreen = true,
                subtitleCueText = "Updated cue"
            )
        )
        assertEquals("Episode 1", overlay.titleLabel.text)
        assertEquals("1080p", overlay.subtitleLabel.text)
        assertEquals("Buffering...", overlay.statusLabel.text)
        assertTrue(overlay.loadingSpinner.isVisible)
        assertFalse(overlay.centerPlayPauseButton.isVisible)
        assertEquals(R.drawable.ic_pause, overlay.playPauseButton.iconRes)
        assertEquals(R.drawable.ic_fullscreen_exit, overlay.fullscreenButton.iconRes)
        assertEquals("1:05 / 10:00", overlay.timeReadoutLabel.text)
        assertEquals("Updated cue", overlay.subtitleBanner.text)

        overlay.dispose()
    }

    @Test
    fun testHybridOverlayDifferenceControllerSmallMobilePortraitVsMediumLarge() {
        val overlay = YoutubePlayerOverlay()
        val modeHistory = mutableListOf<HybridOverlayLayoutMode>()
        val controller = HybridOverlayDifferenceController(
            overlay = overlay,
            initialMode = HybridOverlayLayoutMode.MEDIUM_LARGE,
            onLayoutModeChanged = { modeHistory.add(it) }
        )

        // Initial MEDIUM_LARGE state
        assertEquals(HybridOverlayLayoutMode.MEDIUM_LARGE, controller.currentMode)
        assertFalse(controller.isCompactMode)
        assertEquals(YoutubeTimelinePlacement.ABOVE_CONTROLS, overlay.timelinePlacement)
        assertTrue(overlay.playPauseButton.isVisible)
        assertFalse(overlay.centerTransportRow.isVisible)
        assertNull(overlay.centerPlayPauseButton.backdropFilter)

        // Switch to SMALL_MOBILE_PORTRAIT on mobile portrait
        controller.updateForSize(
            widthPx = 390,
            heightPx = 844,
            densityScale = 1.0f,
            backendTarget = BackendTarget.Android
        )
        assertEquals(HybridOverlayLayoutMode.SMALL_MOBILE_PORTRAIT, controller.currentMode)
        assertTrue(controller.isCompactMode)
        assertEquals(YoutubeTimelinePlacement.BELOW_CONTROLS, overlay.timelinePlacement)
        assertTrue(overlay.centerTransportRow.isVisible)
        assertTrue(overlay.centerRewindButton.isVisible)
        assertTrue(overlay.centerPlayPauseButton.isVisible)
        assertTrue(overlay.centerFastForwardButton.isVisible)
        assertEquals(56, overlay.centerRewindButton.widthPx)
        assertEquals(64, overlay.centerPlayPauseButton.widthPx)
        assertEquals(56, overlay.centerFastForwardButton.widthPx)

        val expectedBackdrop = CssFilter(blur = CssLength.px(16f), saturate = 1.2f)
        val expectedFill = RgbaColor(20, 24, 32, 97)
        val expectedBorder = RgbaColor(255, 255, 255, 56)
        for (centerBtn in listOf(
            overlay.centerRewindButton,
            overlay.centerPlayPauseButton,
            overlay.centerFastForwardButton
        )) {
            assertEquals(expectedBackdrop, centerBtn.backdropFilter)
            assertEquals(expectedFill, centerBtn.backgroundColor)
            assertEquals(expectedBorder, centerBtn.borderColor)
        }
        assertFalse(overlay.playPauseButton.isVisible, "Duplicate bottom play/pause should hide in compact mode")
        assertFalse(overlay.rewindButton.isVisible)
        assertFalse(overlay.fastForwardButton.isVisible)

        // Resize via overlay.notifyTargetResize back to wide desktop/web
        overlay.notifyTargetResize(1280, 720)
        assertEquals(HybridOverlayLayoutMode.MEDIUM_LARGE, controller.currentMode)
        assertEquals(YoutubeTimelinePlacement.ABOVE_CONTROLS, overlay.timelinePlacement)
        assertTrue(overlay.playPauseButton.isVisible)
        assertFalse(overlay.centerTransportRow.isVisible)
        assertNull(overlay.centerRewindButton.backdropFilter)
        assertNull(overlay.centerPlayPauseButton.backdropFilter)
        assertNull(overlay.centerFastForwardButton.backdropFilter)
        assertEquals(
            listOf(
                HybridOverlayLayoutMode.MEDIUM_LARGE,
                HybridOverlayLayoutMode.SMALL_MOBILE_PORTRAIT,
                HybridOverlayLayoutMode.MEDIUM_LARGE
            ),
            modeHistory
        )

        // Resize to narrow web/desktop portrait
        overlay.notifyTargetResize(480, 720)
        assertEquals(HybridOverlayLayoutMode.SMALL_MOBILE_PORTRAIT, controller.currentMode)
        assertEquals(YoutubeTimelinePlacement.BELOW_CONTROLS, overlay.timelinePlacement)

        overlay.dispose()
    }
}
