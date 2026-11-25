package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.css.CssCursor
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
        Animator.cancelAllAnimations()
        UiTimerScheduler.clearAllTimers()
    }

    @AfterTest
    fun tearDown() {
        Animator.cancelAllAnimations()
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
        val sheet = OverlayExtension.defaultStyleSheet()
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
            btn.applyStyleSheet(sheet)
            assertNull(btn.tooltipText, "Overlay button must never define a default tooltipText")
            assertTrue(btn.hasClass("player-btn"))
            assertTrue(btn.hasClass("player-circle-btn"))
            assertNull(btn.style.width)
            assertNull(btn.style.backgroundColor)
            assertEquals("40px", btn.computedStyle?.width)
            assertEquals("40px", btn.computedStyle?.height)
        }

        val centerPrimary = OverlayControlButton(
            iconType = OverlayIconType.PLAY,
            variant = OverlayButtonVariant.CENTER_PRIMARY,
            backdropFilter = frostedFilter
        ).apply { applyStyleSheet(sheet) }
        assertNull(centerPrimary.tooltipText)
        assertTrue(centerPrimary.hasClass("player-btn-center-primary"))
        assertTrue(centerPrimary.hasClass("player-btn-frosted"))
        assertTrue(centerPrimary.isFrosted)
        assertEquals("64px", centerPrimary.computedStyle?.width)
        assertEquals("64px", centerPrimary.computedStyle?.height)
        assertEquals("primary-center-action", centerPrimary.getAttribute("data-overlay-role"))
        assertEquals(frostedFilter, centerPrimary.computedStyle?.backdropFilter)
        assertEquals(RgbaColor(20, 24, 32, 97), centerPrimary.computedStyle?.backgroundColor)
        assertEquals(RgbaColor(255, 255, 255, 56), centerPrimary.computedStyle?.borderColor)

        val centerSecondary = OverlayControlButton(
            iconType = OverlayIconType.REWIND,
            variant = OverlayButtonVariant.CENTER_SECONDARY
        ).apply { applyStyleSheet(sheet) }
        assertNull(centerSecondary.tooltipText)
        assertTrue(centerSecondary.hasClass("player-btn-center-secondary"))
        assertFalse(centerSecondary.isFrosted)
        assertEquals("56px", centerSecondary.computedStyle?.width)
        assertEquals("56px", centerSecondary.computedStyle?.height)
        assertNull(centerSecondary.computedStyle?.backdropFilter)

        val innerPillBtn = OverlayControlButton(
            iconType = OverlayIconType.NEXT,
            variant = OverlayButtonVariant.INNER_PILL,
            tooltipText = "Explicit Tooltip"
        ).apply { applyStyleSheet(sheet) }
        assertEquals("Explicit Tooltip", innerPillBtn.tooltipText)
        assertTrue(innerPillBtn.hasClass("player-pill-inner-btn"))
        assertEquals("32px", innerPillBtn.computedStyle?.width)
        assertEquals("32px", innerPillBtn.computedStyle?.height)
        assertEquals(0f, innerPillBtn.computedStyle?.borderWidthPx)
        assertEquals("none", innerPillBtn.computedStyle?.borderStyle)
        assertEquals(RgbaColor.TRANSPARENT, innerPillBtn.computedStyle?.backgroundColor)

        val controlsPill = OverlayControlButtons.createControlsPill().apply { applyStyleSheet(sheet) }
        assertEquals("controls-right", controlsPill.getAttribute("data-overlay-role"))
        assertTrue(controlsPill.hasClass("player-controls-pill"))
        assertEquals("40px", controlsPill.computedStyle?.height)

        val timePill = OverlayControlButtons.createTimePill("1:23 / 4:56").apply { applyStyleSheet(sheet) }
        assertEquals("time-readout", timePill.getAttribute("data-overlay-role"))
        assertTrue(timePill.hasClass("player-time-pill"))
        assertEquals("40px", timePill.computedStyle?.height)

        val badge4k = OverlayControlButtons.createOutlinedBadge("4K", id = "badge-4k").apply { applyStyleSheet(sheet) }
        assertNull(badge4k.tooltipText)
        assertEquals("4K", badge4k.text)
        assertTrue(badge4k.hasClass("overlay-active-feature-badge"))
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
        val host = OverlayHostBox(
            content = Container("player-stage"),
            initialOverlay = overlay
        )
        val sheet = OverlayExtension.defaultStyleSheet()
        host.applyStyleSheet(sheet)
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
        val subConstraint = overlay.getEffectiveLayerConstraint(overlay.subtitleBanner)
        assertEquals(10, hudConstraint?.zIndex)
        assertEquals(15, subConstraint?.zIndex)
        assertEquals(LayerAlignment.BOTTOM_CENTER, subConstraint?.alignment)
        assertTrue(subConstraint?.passThroughPointerEvents == true)
        assertSame(host.persistentOverlayRoot, overlay.subtitleBanner.parent)
        assertSame(host.persistentOverlayRoot, overlay.loadingSpinner.parent)
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
        assertSame(overlay, overlay.autoHideController.targetComponent)
        assertTrue(overlay.hasClass("overlay-fading-root"))

        // Persistent captions survive HUD auto-hide in persistentOverlayRoot while fading overlay uses .hidden
        overlay.setSubtitleCueText("Subtitles stay visible")
        assertTrue(overlay.subtitleBanner.isVisible)
        assertEquals("Subtitles stay visible", overlay.subtitleBannerLabel.text)
        assertEquals("caption-banner", overlay.subtitleBanner.getAttribute("data-overlay-role"))
        assertNull(overlay.subtitleBanner.style.backgroundColor)
        assertEquals(RgbaColor(0, 0, 0, 148), overlay.subtitleBanner.computedStyle?.backgroundColor)
        assertEquals(CssFilter.blur(CssLength.px(12f)), overlay.subtitleBanner.computedStyle?.backdropFilter)
        assertEquals(8f, overlay.subtitleBanner.computedStyle?.borderRadiusPx)
        assertEquals(false, overlay.subtitleBanner.computedStyle?.pointerEventsEnabled)

        overlay.hideHud(instant = true)
        assertFalse(overlay.isHudVisible)
        assertTrue(overlay.hasClass(AutoHideController.HIDDEN_CLASS))
        assertTrue(overlay.hasClass(AutoHideController.INSTANT_FADE_CLASS))
        assertEquals(0.0f, overlay.computedStyle?.opacity ?: -1f, 0.001f)
        assertEquals(false, overlay.computedStyle?.pointerEventsEnabled)
        assertEquals("none", overlay.computedStyle?.cursor)
        assertFalse(host.persistentOverlayRoot.hasClass(AutoHideController.HIDDEN_CLASS))
        assertEquals(1.0f, host.persistentOverlayRoot.computedStyle?.opacity ?: -1f, 0.001f)
        assertTrue(overlay.subtitleBanner.isVisible)
        assertFalse(overlay.isCursorVisible)
        assertTrue(overlay.hasClass("hud-cursor-hidden"))
        assertEquals(listOf(false), cursorEvents)

        overlay.notifyUserActivity()
        assertTrue(overlay.isHudVisible)
        assertFalse(overlay.hasClass(AutoHideController.HIDDEN_CLASS))
        assertEquals(1.0f, overlay.computedStyle?.opacity ?: -1f, 0.001f)
        assertEquals(true, overlay.computedStyle?.pointerEventsEnabled)
        assertTrue(overlay.isCursorVisible)
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
        assertEquals("0:00", overlay.seekHoverLabel.text)
        assertEquals("hover-bubble", overlay.seekHoverBubble.getAttribute("data-overlay-role"))
        assertNull(overlay.seekHoverBubble.style.backgroundColor)
        assertEquals(RgbaColor(15, 18, 26, 171), overlay.seekHoverBubble.computedStyle?.backgroundColor)
        assertEquals(CssFilter.blur(CssLength.px(10f)), overlay.seekHoverBubble.computedStyle?.backdropFilter)
        assertEquals(1f, overlay.seekHoverBubble.computedStyle?.borderWidthPx)
        assertEquals(8f, overlay.seekHoverBubble.computedStyle?.borderRadiusPx)
        assertEquals(false, overlay.seekHoverBubble.computedStyle?.pointerEventsEnabled)
        assertEquals("2.0", overlay.seekHoverBubble.getAttribute("layerLeftPercent"))

        overlay.showTimelineHover(1.0, "10:00")
        assertEquals("10:00", overlay.seekHoverLabel.text)
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
        assertEquals("Updated cue", overlay.subtitleBannerLabel.text)

        host.dispose()
    }

    @Test
    fun testHybridOverlayDifferenceControllerSmallMobilePortraitVsMediumLarge() {
        val overlay = YoutubePlayerOverlay()
        val sheet = OverlayExtension.defaultStyleSheet()
        overlay.applyStyleSheet(sheet)
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
        assertFalse(overlay.centerPlayPauseButton.isFrosted)
        assertNull(overlay.centerPlayPauseButton.computedStyle?.backdropFilter)

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
        assertEquals("56px", overlay.centerRewindButton.computedStyle?.width)
        assertEquals("64px", overlay.centerPlayPauseButton.computedStyle?.width)
        assertEquals("56px", overlay.centerFastForwardButton.computedStyle?.width)

        val expectedBackdrop = CssFilter(blur = CssLength.px(16f), saturate = 1.2f)
        val expectedFill = RgbaColor(20, 24, 32, 97)
        val expectedBorder = RgbaColor(255, 255, 255, 56)
        for (centerBtn in listOf(
            overlay.centerRewindButton,
            overlay.centerPlayPauseButton,
            overlay.centerFastForwardButton
        )) {
            assertTrue(centerBtn.isFrosted)
            assertTrue(centerBtn.hasClass("player-btn-frosted"))
            assertEquals(expectedBackdrop, centerBtn.computedStyle?.backdropFilter)
            assertEquals(expectedFill, centerBtn.computedStyle?.backgroundColor)
            assertEquals(expectedBorder, centerBtn.computedStyle?.borderTopColor)
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
        assertFalse(overlay.centerRewindButton.isFrosted)
        assertFalse(overlay.centerPlayPauseButton.isFrosted)
        assertFalse(overlay.centerFastForwardButton.isFrosted)
        assertNull(overlay.centerRewindButton.computedStyle?.backdropFilter)
        assertNull(overlay.centerPlayPauseButton.computedStyle?.backdropFilter)
        assertNull(overlay.centerFastForwardButton.computedStyle?.backdropFilter)
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

    @Test
    fun testOverlayFullscreenButton4ArrowSwitchingDrivenByOverlayStateAndWindowFullscreenCallbacks() {
        val enterSvg = Resources.getDrawable(R.drawable.ic_fullscreen_enter)?.svgSource.orEmpty()
        val exitSvg = Resources.getDrawable(R.drawable.ic_fullscreen_exit)?.svgSource.orEmpty()
        assertTrue(enterSvg.contains("M4 4H9.5"), "ic_fullscreen_enter must depict 4 outward diagonal corner arrows")
        assertTrue(exitSvg.contains("M3.5 4.9L4.9 3.5"), "ic_fullscreen_exit must depict 4 inward diagonal corner arrows")

        val sheet = OverlayExtension.defaultStyleSheet()
        val customBtnToggles = mutableListOf<Boolean>()
        val customFullscreenBtn = OverlayFullscreenButton(
            isFullscreen = false,
            variant = OverlayButtonVariant.CIRCLE_PILL
        ) { next ->
            customBtnToggles.add(next)
        }.apply {
            addClass("app-custom-fullscreen-btn")
        }

        val customOverlay = object : AbstractOverlay(tagName = "custom-fullscreen-overlay") {}
        customOverlay.addElement(customFullscreenBtn, LayerConstraint.float(alignment = LayerAlignment.BOTTOM_END))

        val ytOverlay = YoutubePlayerOverlay()
        val host = OverlayHostBox(
            content = Container("video-stage").apply {
                widthPx = 1280
                heightPx = 720
            },
            initialOverlay = customOverlay
        )
        host.applyStyleSheet(sheet)

        assertTrue(customFullscreenBtn.hasClass("gui-overlay-control-btn"))
        assertTrue(customFullscreenBtn.hasClass("app-custom-fullscreen-btn"))
        assertEquals("40px", customFullscreenBtn.computedStyle?.width)
        assertEquals("40px", customFullscreenBtn.computedStyle?.height)
        assertEquals(R.drawable.ic_fullscreen_enter, customFullscreenBtn.iconRes)
        assertEquals(OverlayIconType.FULLSCREEN_ENTER, customFullscreenBtn.iconType)
        assertEquals("false", customFullscreenBtn.getAttribute("data-fullscreen"))
        assertEquals("false", customFullscreenBtn.getAttribute("aria-pressed"))

        // Overlay state updates switch every descendant OverlayFullscreenButton without invoking onFullscreenToggle
        customOverlay.updateState(OverlayState(isFullscreen = true))
        assertTrue(customFullscreenBtn.isFullscreen)
        assertEquals(R.drawable.ic_fullscreen_exit, customFullscreenBtn.iconRes)
        assertEquals(OverlayIconType.FULLSCREEN_EXIT, customFullscreenBtn.iconType)
        assertEquals("true", customFullscreenBtn.getAttribute("data-fullscreen"))
        assertEquals("true", customFullscreenBtn.getAttribute("aria-pressed"))
        assertTrue(customBtnToggles.isEmpty())

        customOverlay.updateState(OverlayState(isFullscreen = false))
        assertFalse(customFullscreenBtn.isFullscreen)
        assertEquals(R.drawable.ic_fullscreen_enter, customFullscreenBtn.iconRes)
        assertEquals(OverlayIconType.FULLSCREEN_ENTER, customFullscreenBtn.iconType)
        assertTrue(customBtnToggles.isEmpty())

        // Attached Window fullscreen transitions drive AbstractOverlay.syncFullscreenButtons via onFullscreenChanged
        val window = Window("Fullscreen Sync Window")
        window.setContent(host)
        window.setFullscreen(true)
        assertTrue(customOverlay.state.isFullscreen)
        assertTrue(customFullscreenBtn.isFullscreen)
        assertEquals(R.drawable.ic_fullscreen_exit, customFullscreenBtn.iconRes)
        assertTrue(customBtnToggles.isEmpty())

        window.setFullscreen(false)
        assertFalse(customOverlay.state.isFullscreen)
        assertFalse(customFullscreenBtn.isFullscreen)
        assertEquals(R.drawable.ic_fullscreen_enter, customFullscreenBtn.iconRes)
        assertTrue(customBtnToggles.isEmpty())

        // Attaching YoutubePlayerOverlay while window is already fullscreen syncs its fullscreenButton immediately
        val ytToggles = mutableListOf<Boolean>()
        ytOverlay.onFullscreenToggle = { ytToggles.add(it) }
        window.setFullscreen(true)
        host.attachOverlay(ytOverlay)
        assertTrue(ytOverlay.state.isFullscreen)
        assertTrue(ytOverlay.fullscreenButton.isFullscreen)
        assertEquals(R.drawable.ic_fullscreen_exit, ytOverlay.fullscreenButton.iconRes)
        assertTrue(ytToggles.isEmpty())

        window.setFullscreen(false)
        assertFalse(ytOverlay.state.isFullscreen)
        assertFalse(ytOverlay.fullscreenButton.isFullscreen)
        assertEquals(R.drawable.ic_fullscreen_enter, ytOverlay.fullscreenButton.iconRes)
        assertTrue(ytToggles.isEmpty())

        window.dispose()
        host.dispose()
    }
}
