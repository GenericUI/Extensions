package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.event.KeyEvent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverlayExtensionTest {

    @BeforeTest
    fun setUp() {
        UiTimerScheduler.clearAllTimers()
        OverlayExtension.activeApplication = null
        OverlayExtension.defaultThemeColorHex = "#0d6efd"
        OverlayExtension.resetBuiltInPresets()
    }

    @AfterTest
    fun tearDown() {
        UiTimerScheduler.clearAllTimers()
        OverlayExtension.activeApplication = null
        OverlayExtension.defaultThemeColorHex = "#0d6efd"
    }

    @Test
    fun testOverlayTimeFormatterMinutesAndForcedHoursAndUnknown() {
        // Under 1 hour -> M:SS
        assertEquals("0:05", OverlayTimeFormatter.formatPosition(5.0, 120.0))
        assertEquals("2:00", OverlayTimeFormatter.formatDuration(5.0, 120.0))
        assertEquals("0:05 / 2:00", OverlayTimeFormatter.formatPositionAndDuration(5.0, 120.0))

        // Duration >= 3600s -> both position and duration forced to H:MM:SS
        assertEquals("0:05:09", OverlayTimeFormatter.formatPosition(309.0, 3600.0))
        assertEquals("1:00:00", OverlayTimeFormatter.formatDuration(309.0, 3600.0))
        assertEquals("0:05:09 / 1:00:00", OverlayTimeFormatter.formatPositionAndDuration(309.0, 3600.0))

        // Position >= 3600s -> forced H:MM:SS
        assertEquals("1:02:03", OverlayTimeFormatter.formatPosition(3723.0, 4000.0))
        assertEquals("1:06:40", OverlayTimeFormatter.formatDuration(3723.0, 4000.0))

        // Unknown or zero duration -> --:--
        assertEquals("0:42", OverlayTimeFormatter.formatPosition(42.0, 0.0))
        assertEquals("--:--", OverlayTimeFormatter.formatDuration(42.0, 0.0))
        assertEquals("0:42 / --:--", OverlayTimeFormatter.formatPositionAndDuration(42.0, 0.0))
        assertEquals("--:--", OverlayTimeFormatter.formatDuration(0.0, Double.NaN))
    }

    @Test
    fun testAutoHideControllerTimeoutPinningPointerLeaveTapAndPiP() {
        val target = Container("overlay-target")
        val controller = AutoHideController(
            spec = AutoHideSpec(
                timeoutMs = 3500,
                fadeDurationMs = 200,
                hideImmediatelyOnPointerLeave = true,
                instantHideOnTap = true,
                hideCursorWhenHidden = true
            ),
            targetComponent = target
        )

        // Show overlay starts 3500ms timer
        controller.showOverlay(scheduleHide = true)
        assertTrue(controller.isOverlayVisible)
        assertEquals(1.0f, controller.currentOpacity)

        UiTimerScheduler.advanceTimeBy(3499)
        assertTrue(controller.isOverlayVisible)

        UiTimerScheduler.advanceTimeBy(1)
        assertFalse(controller.isOverlayVisible)
        assertEquals(0.0f, controller.currentOpacity)
        assertEquals("none", target.cursor)

        // Pin reason keeps overlay visible and prevents timeout from hiding it
        controller.setPinned("popover", true)
        assertTrue(controller.isPinned)
        assertTrue(controller.isOverlayVisible)

        UiTimerScheduler.advanceTimeBy(10000)
        assertTrue(controller.isOverlayVisible)

        // Pointer leave is ignored while pinned
        controller.notifyPointerLeave()
        assertTrue(controller.isOverlayVisible)

        // Unpinning restarts the 3500ms countdown
        controller.setPinned("popover", false)
        assertFalse(controller.isPinned)
        UiTimerScheduler.advanceTimeBy(3500)
        assertFalse(controller.isOverlayVisible)

        // Pointer leave hides immediately on System+Web when unpinned
        controller.notifyUserActivity()
        assertTrue(controller.isOverlayVisible)
        controller.notifyPointerLeave()
        assertFalse(controller.isOverlayVisible)

        // Tap toggle on Mobile
        controller.toggleVisibilityByTap()
        assertTrue(controller.isOverlayVisible)
        controller.toggleVisibilityByTap()
        assertFalse(controller.isOverlayVisible)

        // Picture-in-Picture mode immediately hides and disables the overlay even if pinned
        controller.setPinned("seeking", true)
        assertTrue(controller.isOverlayVisible)
        controller.setPictureInPictureMode(true)
        assertTrue(controller.isInPictureInPictureMode)
        assertFalse(controller.isOverlayVisible)
        assertFalse(target.isVisible)
        assertFalse(target.pointerEventsEnabled)

        // User activity is ignored while in PiP mode
        controller.notifyUserActivity()
        assertFalse(controller.isOverlayVisible)

        // Exiting PiP restores visibility
        controller.setPictureInPictureMode(false)
        assertFalse(controller.isInPictureInPictureMode)
        assertTrue(controller.isOverlayVisible)
        assertTrue(target.isVisible)

        controller.dispose()
    }

    @Test
    fun testTrackSeekBarAndSliderSeekBarDragPreviewClampingAndThemeColor() {
        OverlayExtension.defaultThemeColorHex = "#2A80FF"
        val autoHide = AutoHideController()

        var committedTrackSeek: Double? = null
        val trackSeekBar = TrackSeekBar(autoHideController = autoHide)
        assertEquals("#2A80FF", trackSeekBar.effectiveThemeColorHex)

        // Disabled when duration <= 0
        trackSeekBar.onStateChanged(
            OverlayState(positionSec = 0.0, durationSec = 0.0),
            OverlayActions(onSeekToSec = { committedTrackSeek = it })
        )
        assertFalse(trackSeekBar.isEnabled)

        // Enabled when duration > 0
        trackSeekBar.onStateChanged(
            OverlayState(
                positionSec = 10.0,
                durationSec = 100.0,
                bufferedRanges = listOf(OverlayBufferedRange(0.0, 50.0))
            ),
            OverlayActions(onSeekToSec = { committedTrackSeek = it })
        )
        assertTrue(trackSeekBar.isEnabled)
        assertEquals(0.5, trackSeekBar.bufferedFraction, 1e-4)

        // Drag-with-live-preview pins AutoHideController and does NOT commit until release
        trackSeekBar.beginScrubAtSeconds(30.0)
        assertTrue(trackSeekBar.isScrubbing)
        assertTrue(autoHide.activePinReasons.contains(TrackSeekBar.PIN_REASON_SEEKING))
        assertEquals("0:30", trackSeekBar.previewTimeText)
        assertNull(committedTrackSeek)

        // Clamping to [0, duration - 0.5]
        trackSeekBar.updateScrubAtSeconds(150.0)
        assertEquals(99.5, trackSeekBar.previewPositionSec, 1e-4)
        assertNull(committedTrackSeek)

        trackSeekBar.endScrub(commit = true)
        assertFalse(trackSeekBar.isScrubbing)
        assertFalse(autoHide.activePinReasons.contains(TrackSeekBar.PIN_REASON_SEEKING))
        assertNotNull(committedTrackSeek)
        assertEquals(99.5, committedTrackSeek!!, 1e-4)

        // Override themeColorHex property on TrackSeekBar
        trackSeekBar.themeColorHex = "#FF5500"
        assertEquals("#FF5500", trackSeekBar.effectiveThemeColorHex)

        // SliderSeekBar test
        var committedSliderSeek: Double? = null
        val sliderSeekBar = SliderSeekBar(autoHideController = autoHide)
        assertEquals("#2A80FF", sliderSeekBar.effectiveThemeColorHex)
        assertFalse(sliderSeekBar.isEnabled)

        sliderSeekBar.onStateChanged(
            OverlayState(positionSec = 20.0, durationSec = 200.0),
            OverlayActions(onSeekToSec = { committedSliderSeek = it })
        )
        assertTrue(sliderSeekBar.isEnabled)

        sliderSeekBar.beginScrub(60.0)
        assertTrue(sliderSeekBar.isScrubbing)
        assertTrue(autoHide.activePinReasons.contains(SliderSeekBar.PIN_REASON_SEEKING))
        assertEquals("1:00 / 3:20", sliderSeekBar.previewTimePairText)
        assertNull(committedSliderSeek)

        // Clamping near end of media to duration - 0.5 = 199.5
        sliderSeekBar.updateScrub(200.0)
        assertEquals(199.5, sliderSeekBar.previewPositionSec, 1e-4)
        sliderSeekBar.endScrub(commit = true)
        assertFalse(sliderSeekBar.isScrubbing)
        assertFalse(autoHide.activePinReasons.contains(SliderSeekBar.PIN_REASON_SEEKING))
        assertEquals(199.5, committedSliderSeek!!, 1e-4)

        trackSeekBar.dispose()
        sliderSeekBar.dispose()
        autoHide.dispose()
    }

    @Test
    fun testOverlayPresetResolverAndPresetsBehavior() {
        // Resolver maps Android & IOS -> MobileOverlayPreset, Desktop & Web -> WebSystemOverlayPreset
        assertIs<MobileOverlayPreset>(OverlayPresetResolver.resolveForBackend(BackendTarget.Android))
        assertIs<MobileOverlayPreset>(OverlayPresetResolver.resolveForBackend(BackendTarget.IOS))
        assertIs<WebSystemOverlayPreset>(OverlayPresetResolver.resolveForBackend(BackendTarget.Windows))
        assertIs<WebSystemOverlayPreset>(OverlayPresetResolver.resolveForBackend(BackendTarget.MacOS))
        assertIs<WebSystemOverlayPreset>(OverlayPresetResolver.resolveForBackend(BackendTarget.Gtk))
        assertIs<WebSystemOverlayPreset>(OverlayPresetResolver.resolveForBackend(BackendTarget.Qt))
        assertIs<WebSystemOverlayPreset>(OverlayPresetResolver.resolveForBackend(BackendTarget.Swing))
        assertIs<WebSystemOverlayPreset>(OverlayPresetResolver.resolveForBackend(BackendTarget.Web))

        // OverlayExtension.createDefaultPreset delegates to OverlayPresetResolver
        val hostBox = OverlayHostBox()
        val mobilePreset = OverlayExtension.createDefaultPreset(BackendTarget.Android, host = hostBox)
        assertIs<MobileOverlayPreset>(mobilePreset)
        assertEquals(mobilePreset, hostBox.activeOverlay)

        // Test MobileOverlayPreset slots and state propagation
        var skipDeltaCalled: Double? = null
        var badgeClickedId: String? = null
        hostBox.actions = OverlayActions(
            onSkipDeltaSec = { skipDeltaCalled = it },
            onBadgeClick = { badgeClickedId = it }
        )
        hostBox.updateState {
            it.copy(
                title = "Sample Title",
                subtitle = "Subtitle Info",
                statusText = "Buffering...",
                subtitleCueText = "Hello world",
                isLoading = true,
                positionSec = 90.0,
                durationSec = 3600.0,
                topBadges = listOf(OverlayBadgeSpec(id = "hd", text = "HD", variant = OverlayBadgeVariant.OUTLINED))
            )
        }

        assertEquals("Sample Title", mobilePreset.metadataHeader.titleLabel.text)
        assertEquals("Subtitle Info", mobilePreset.metadataHeader.subtitleLabel.text)
        assertTrue(mobilePreset.statusPill.isVisible)
        assertEquals("Buffering...", mobilePreset.statusPill.textLabel.text)
        assertTrue(mobilePreset.subtitleCueView.isVisible)
        assertEquals("Hello world", mobilePreset.subtitleCueView.cueLabel.text)
        assertTrue(mobilePreset.centerTransportCluster.loadingIndicator.isVisible)
        assertFalse(mobilePreset.centerTransportCluster.playPauseButton.isVisible)
        assertEquals("0:01:30 / 1:00:00", mobilePreset.timePill.text)

        mobilePreset.centerTransportCluster.skipForwardButton.triggerClick()
        assertEquals(30.0, skipDeltaCalled)

        // Switch hostBox to WebSystemOverlayPreset
        val webPreset = WebSystemOverlayPreset()
        hostBox.attachOverlay(webPreset)
        var playPauseCount = 0
        var fullscreenCount = 0
        hostBox.actions = OverlayActions(
            onPlayPause = { playPauseCount++ },
            onToggleFullscreen = { fullscreenCount++ },
            onNext = {},
            onBadgeClick = { badgeClickedId = it }
        )
        hostBox.updateState {
            it.copy(
                isLoading = false,
                belowHostBadges = listOf(
                    OverlayBadgeSpec(id = "codec", text = "AV1", variant = OverlayBadgeVariant.ACCENT, clickable = true)
                )
            )
        }

        assertTrue(webPreset.nextButton.isVisible)
        assertTrue(webPreset.belowHostBadgeStrip.isVisible)
        assertEquals(1, webPreset.belowHostBadgeStrip.badges.size)
        webPreset.belowHostBadgeStrip.badges.first().onClick?.invoke()
        assertEquals("codec", badgeClickedId)

        // Single-click play/pause is debounced by 250ms on WebSystemOverlayPreset
        webPreset.inputRouter.handlePointerClick(webPreset.state, webPreset.actions)
        UiTimerScheduler.advanceTimeBy(100)
        assertEquals(0, playPauseCount)
        UiTimerScheduler.advanceTimeBy(150)
        assertEquals(1, playPauseCount)

        // Double-click cancels pending single-click and toggles fullscreen immediately
        webPreset.inputRouter.handlePointerClick(webPreset.state, webPreset.actions)
        UiTimerScheduler.advanceTimeBy(100)
        webPreset.inputRouter.handlePointerDoubleClick(webPreset.state, webPreset.actions)
        UiTimerScheduler.advanceTimeBy(300)
        assertEquals(1, playPauseCount)
        assertEquals(1, fullscreenCount)

        // Keyboard shortcut Space triggers play/pause unless text input is focused
        assertTrue(webPreset.inputRouter.handleKeyDown(KeyEvent(key = " "), webPreset.state, webPreset.actions))
        assertEquals(2, playPauseCount)
        assertFalse(
            webPreset.inputRouter.handleKeyDown(
                KeyEvent(key = " "),
                webPreset.state,
                webPreset.actions,
                isTextInputFocused = true
            )
        )
        assertEquals(2, playPauseCount)

        // Popover and Curtain pin AutoHideController
        webPreset.popoverPanel.open()
        assertTrue(webPreset.autoHideController.activePinReasons.contains(OverlayPopoverPanel.PIN_REASON_POPOVER))
        webPreset.popoverPanel.close()
        assertFalse(webPreset.autoHideController.activePinReasons.contains(OverlayPopoverPanel.PIN_REASON_POPOVER))

        webPreset.showCurtain()
        assertTrue(webPreset.autoHideController.activePinReasons.contains(OverlayCurtain.PIN_REASON_CURTAIN))
        webPreset.hideCurtain()
        assertFalse(webPreset.autoHideController.activePinReasons.contains(OverlayCurtain.PIN_REASON_CURTAIN))

        hostBox.dispose()
        mobilePreset.dispose()
    }
}
