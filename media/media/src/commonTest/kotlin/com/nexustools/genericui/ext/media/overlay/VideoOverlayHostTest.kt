package com.nexustools.genericui.ext.media.overlay

import com.nexustools.genericui.component.media.ExpandingVolumeSlider
import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SeekBar
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.enums.media.MediaPreloadMode
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssLength
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VideoOverlayHostTest {

    @Test
    fun testVideoOverlayHostAndBinderSynchronization() {
        UiTimerScheduler.clearAllTimers()
        val player = VideoPlayer()
        player.setSource("https://example.com/movie.mp4", seekOffsetSeconds = 10.0, durationOverrideSeconds = 120.0)
        player.setAudioTracks(
            listOf(
                MediaTrackInfo(id = "en", label = "English", language = "en", isSelected = true),
                MediaTrackInfo(id = "ja", label = "Japanese", language = "ja", isSelected = false)
            )
        )
        player.addExternalSubtitleTrack("English CC", "en", "https://example.com/en.vtt")
        player.loadSubtitleContent(
            """
            WEBVTT

            1
            00:00:05.000 --> 00:00:15.000
            Hello from subtitle cue!

            2
            00:00:20.000 --> 00:00:30.000
            Second subtitle cue
            """.trimIndent()
        )

        val overlay = object : AbstractOverlay("test-video-overlay") {}
        val host = player.attachVideoOverlay(overlay)

        assertSame(overlay, host.activeOverlay)
        assertSame(player, host.contentComponent)
        assertNotNull(host.id)
        assertSame(host, player.attachedOverlayHost)

        // Initial state synchronized
        assertEquals(10.0, host.state.positionSec)
        assertEquals(120.0, host.state.durationSec)
        assertEquals("Hello from subtitle cue!", host.state.subtitleCueText)
        assertEquals(2, host.state.audioTracks.size)
        assertEquals(1, host.state.subtitleTracks.size)
        assertEquals("en", player.getSelectedAudioTrackId())

        // Play action via OverlayActions starts 1000ms polling timer
        host.actions.onPlay?.invoke()
        assertTrue(player.isPlaying)
        assertTrue(host.state.isPlaying)
        assertTrue(host.binder.isPollingActive)

        // Seek and skip actions
        host.actions.onSeekToSec?.invoke(25.0)
        assertEquals(25.0, player.currentTimeSeconds)
        assertEquals(25.0, host.state.positionSec)
        assertEquals("Second subtitle cue", host.state.subtitleCueText)

        host.actions.onSkipDeltaSec?.invoke(-20.0)
        assertEquals(5.0, player.currentTimeSeconds)
        assertEquals("Hello from subtitle cue!", host.state.subtitleCueText)

        // Volume, mute, and speed actions
        host.actions.onVolumeChange?.invoke(65)
        assertEquals(65, host.state.volumePercent)
        host.actions.onMuteToggle?.invoke()
        assertTrue(player.isMuted)
        assertTrue(host.state.isMuted)

        host.actions.onSpeedChange?.invoke(1.5)
        assertEquals(1.5, host.state.playbackSpeed)

        // Audio and subtitle track selection (by ID and by 0-based index)
        assertFalse(player.supportsAudioTrackSelection())
        assertTrue(player.selectAudioTrackByIndex(1))
        assertEquals("ja", player.getSelectedAudioTrackId())
        assertTrue(host.state.audioTracks.first { it.id == "ja" }.selected)

        host.actions.onSelectSubtitleTrack?.invoke(null)
        assertEquals(null, player.getSelectedSubtitleTrackId())
        assertEquals("", host.state.subtitleCueText)

        assertTrue(player.selectSubtitleTrackByIndex(0))
        assertEquals("sub-0", player.getSelectedSubtitleTrackId())
        assertEquals("Hello from subtitle cue!", host.state.subtitleCueText)

        // Buffered ranges and buffering state
        player.setBufferingState(true)
        assertTrue(host.state.isLoading)
        player.setBufferingState(false)
        assertFalse(host.state.isLoading)

        player.updateBufferedRanges(listOf(0.0 to 45.0))
        assertEquals(1, host.state.bufferedRanges.size)
        assertEquals(45.0, host.state.bufferedEndSec)

        // Fullscreen toggle targets host and updates both player and host fullscreen state
        host.actions.onToggleFullscreen?.invoke()
        assertTrue(player.isFullscreen())
        assertTrue(host.isFullscreen())
        assertTrue(host.state.isFullscreen)

        // Picture-in-Picture mode hides/disables overlay
        host.setPictureInPictureMode(true)
        assertTrue(host.isInPictureInPictureMode)
        assertTrue(overlay.isInPictureInPictureMode)
        assertFalse(host.overlayRoot.isVisible)

        host.setPictureInPictureMode(false)
        assertFalse(host.isInPictureInPictureMode)
        assertTrue(host.overlayRoot.isVisible)

        // Pause stops 1000ms polling timer
        host.actions.onPause?.invoke()
        assertFalse(player.isPlaying)
        assertFalse(host.state.isPlaying)
        assertFalse(host.binder.isPollingActive)

        host.dispose()
    }

    @Test
    fun testSeekBarAndVideoPlayerPreloadingDefaults() {
        val player = VideoPlayer()
        assertEquals(MediaPreloadMode.AUTO, player.preloadMode)
        assertTrue(player.adaptivePreloadBufferingEnabled)

        val seekBar = SeekBar(value = 15.0, min = 0.0, max = 100.0, step = 0.5)
        assertEquals("genericui:seekbar", seekBar.htmlTag)
        assertEquals(0.0, seekBar.min)
        assertEquals(100.0, seekBar.max)
        assertEquals(0.5, seekBar.step)
        assertEquals(15.0, seekBar.value)
        assertEquals(0.15f, seekBar.playedFraction, 0.001f)
        assertEquals(0.0, seekBar.bufferedValue)
        assertEquals(0.0f, seekBar.bufferedFraction, 0.001f)
        assertEquals(RgbaColor(255, 255, 255, 128), seekBar.bufferedColor)

        seekBar.bufferedValue = 60.0
        assertEquals(60.0, seekBar.bufferedValue)
        assertEquals(listOf(0.0 to 60.0), seekBar.bufferedRanges)
        assertEquals(0.60f, seekBar.bufferedFraction, 0.001f)

        seekBar.bufferedRanges = listOf(0.0 to 30.0, 40.0 to 80.0)
        assertEquals(80.0, seekBar.bufferedValue)
        assertEquals(0.80f, seekBar.bufferedFraction, 0.001f)

        // Theme accent color resolution
        val prevApp = Application.current
        try {
            val testApp = Application(initialThemeColor = "#ff6600")
            testApp.setThemeColor("#ff6600")
            assertEquals(RgbaColor(255, 102, 0, 255), seekBar.resolveEffectiveAccentColor())
            assertEquals(RgbaColor(255, 102, 0, 255), seekBar.resolveEffectiveThumbColor())

            seekBar.accentColor = RgbaColor(0, 200, 100, 255)
            assertEquals(RgbaColor(0, 200, 100, 255), seekBar.resolveEffectiveAccentColor())
        } finally {
            Application.current = prevApp
        }

        // Bidirectional binding with VideoPlayer
        player.setSource("https://example.com/episode.mp4", seekOffsetSeconds = 20.0, durationOverrideSeconds = 200.0)
        player.updateBufferedRanges(listOf(0.0 to 90.0))
        assertEquals(90.0, player.bufferedEndSeconds)

        val binding = seekBar.bindTo(player)
        assertEquals(200.0, seekBar.max)
        assertEquals(20.0, seekBar.value)
        assertEquals(90.0, seekBar.bufferedValue)
        assertEquals(0.10f, seekBar.playedFraction, 0.001f)
        assertEquals(0.45f, seekBar.bufferedFraction, 0.001f)

        player.seekTo(50.0)
        assertEquals(50.0, seekBar.value)
        assertEquals(0.25f, seekBar.playedFraction, 0.001f)

        seekBar.value = 110.0
        assertEquals(110.0, player.currentTimeSeconds)

        binding.dispose()
    }

    @Test
    fun testExpandingVolumeSliderAnimationAndDimensions() {
        val vol = ExpandingVolumeSlider(value = 100.0, muted = false, expandedSliderWidthPx = 92)
        assertFalse(vol.isExpanded)
        assertEquals(0f, vol.expansionProgress, 0.001f)
        assertEquals(0, vol.currentSliderWidthPx)
        assertEquals(CssLength.ZERO, vol.sliderContainer.style.width)
        assertEquals(CssLength.px(92), vol.slider.style.width)
        assertEquals(0f, vol.slider.style.flexShrink)
        assertTrue(vol.sliderContainer.style.transitions?.any { it.property == "width" } == true)

        // Immediate non-animated expand/collapse check
        vol.setExpanded(true, animate = false)
        assertTrue(vol.isExpanded)
        assertEquals(1f, vol.expansionProgress, 0.001f)
        assertEquals(92, vol.currentSliderWidthPx)
        assertEquals(CssLength.px(92), vol.sliderContainer.style.width)
        assertEquals("92", vol.getAttribute("data-animated-slider-width-px"))

        vol.setExpanded(false, animate = false)
        assertFalse(vol.isExpanded)
        assertEquals(0f, vol.expansionProgress, 0.001f)
        assertEquals(0, vol.currentSliderWidthPx)
        assertEquals(CssLength.ZERO, vol.sliderContainer.style.width)

        // Animated expand starts immediately with non-zero progress step
        vol.isExpanded = true
        assertTrue(vol.isExpanded)
        assertTrue(vol.expansionProgress > 0f)
        assertTrue(vol.currentSliderWidthPx > 0)
        vol.dispose()
    }
}
