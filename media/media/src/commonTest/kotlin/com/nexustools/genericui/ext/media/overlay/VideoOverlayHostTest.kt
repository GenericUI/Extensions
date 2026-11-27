package com.nexustools.genericui.ext.media.overlay

import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.form.styled.StyledSlider
import com.nexustools.genericui.component.media.ExpandingVolumeSlider
import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SeekBar
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.enums.media.MediaPreloadMode
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.style.css.CssProperty
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VideoOverlayHostTest {

    @BeforeTest
    @AfterTest
    fun resetSchedulers() {
        Animator.cancelAllAnimations()
        UiTimerScheduler.clearAllTimers()
    }

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
        assertFalse(com.nexustools.genericui.ext.media.MediaExtension.supportsAudioTrackSelection())
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
        assertIs<StyledSlider>(seekBar)
        assertIs<Composite>(seekBar)
        assertFalse((seekBar as Any) is AbstractContainer)
        assertEquals("genericui:seekbar", seekBar.htmlTag)
        assertTrue(seekBar.hasClass("seek-bar"))
        assertTrue(seekBar.hasClass("styled-slider"))
        assertSame(seekBar.internalPreloadSegment(), seekBar.mediaCompositeChildren().last())
        assertEquals(0.0, seekBar.min)
        assertEquals(100.0, seekBar.max)
        assertEquals(0.5, seekBar.step)
        assertEquals(15.0, seekBar.value)
        assertEquals(0.15f, seekBar.playedFraction, 0.001f)
        assertEquals(0.0, seekBar.bufferedValue)
        assertEquals(0.0f, seekBar.bufferedFraction, 0.001f)
        assertEquals("0%", seekBar.internalPreloadSegment().style.getWidth()?.toCssString())

        seekBar.bufferedValue = 60.0
        assertEquals(60.0, seekBar.bufferedValue)
        assertEquals(listOf(0.0 to 60.0), seekBar.bufferedRanges)
        assertEquals(0.60f, seekBar.bufferedFraction, 0.001f)
        assertEquals("60%", seekBar.internalPreloadSegment().style.getWidth()?.toCssString())

        seekBar.bufferedRanges = listOf(0.0 to 30.0, 40.0 to 80.0)
        assertEquals(80.0, seekBar.bufferedValue)
        assertEquals(0.80f, seekBar.bufferedFraction, 0.001f)
        assertEquals("80%", seekBar.internalPreloadSegment().style.getWidth()?.toCssString())

        // Zero static/theme inline style overrides on SeekBar
        assertTrue(seekBar.style.isEmpty())
        assertTrue(seekBar.mediaTrackButton().style.isEmpty())
        assertFalse(seekBar.mediaProgressSegment().style.hasProperty(CssProperty.BACKGROUND_COLOR))
        assertFalse(seekBar.internalPreloadSegment().style.hasProperty(CssProperty.BACKGROUND_COLOR))
        assertEquals("15%", seekBar.mediaProgressSegment().style.getWidth()?.toCssString())
        assertEquals("15%", seekBar.mediaKnobButton().style.getLeft()?.toCssString())

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
        assertEquals("45%", seekBar.internalPreloadSegment().style.getWidth()?.toCssString())

        player.seekTo(50.0)
        assertEquals(50.0, seekBar.value)
        assertEquals(0.25f, seekBar.playedFraction, 0.001f)

        seekBar.value = 110.0
        assertEquals(110.0, player.currentTimeSeconds)

        // SeekBar applies MediaExtension structural z-index ordering (track=1 < preload=2 < progress=3 < knob=4)
        // and inherits StyledSlider theme colors (no hardcoded background-color overriding StyledSlider's theme).
        val mediaCssForSeek = MediaExtension.defaultStyleSheet()
        seekBar.applyStyleSheet(mediaCssForSeek)
        assertEquals(1, seekBar.mediaTrackButton().computedStyle?.zIndex)
        assertEquals(2, seekBar.internalPreloadSegment().computedStyle?.zIndex)
        assertEquals(3, seekBar.mediaProgressSegment().computedStyle?.zIndex)
        assertEquals(4, seekBar.mediaKnobButton().computedStyle?.zIndex)

        binding.dispose()
    }

    @Test
    fun testExpandingVolumeSliderAnimationAndDimensions() {
        val vol = ExpandingVolumeSlider(value = 100.0, muted = false, expandedSliderWidthPx = 92)
        assertIs<Composite>(vol)
        assertFalse((vol as Any) is AbstractContainer)
        val innerSlider = vol.internalVolumeSlider()
        assertIs<StyledSlider>(innerSlider)
        assertFalse((innerSlider as Any) is SeekBar)
        assertTrue(vol.style.isEmpty())
        assertTrue(vol.internalMuteButton().style.isEmpty())

        val mediaCss = MediaExtension.defaultStyleSheet()
        vol.applyStyleSheet(mediaCss)

        assertFalse(vol.isExpanded)
        assertEquals(0f, vol.expansionProgress, 0.001f)
        assertEquals(0, vol.currentSliderWidthPx)
        assertEquals("0px", innerSlider.computedStyle?.width)
        assertEquals(0f, innerSlider.computedStyle?.opacity)
        assertEquals("hidden", innerSlider.computedStyle?.overflow)
        assertEquals(false, innerSlider.computedStyle?.pointerEventsEnabled)
        assertTrue(mediaCss.keyframes.containsKey("volumeSliderExpand"))
        assertFalse(mediaCss.keyframes.containsKey("volumeSliderCollapse"))
        assertTrue(innerSlider.computedStyle?.transition?.contains("width") == true)
        assertTrue(innerSlider.computedStyle?.animations.isNullOrEmpty() && innerSlider.computedStyle?.animation.isNullOrBlank())

        // Immediate non-animated expand/collapse check via semantic class + stylesheet cascade
        vol.setExpanded(true, animate = false)
        assertTrue(vol.isExpanded)
        assertTrue(vol.hasClass(ExpandingVolumeSlider.EXPANDED_CLASS))
        assertEquals(1f, vol.expansionProgress, 0.001f)
        assertEquals(92, vol.currentSliderWidthPx)
        assertEquals("92px", innerSlider.computedStyle?.width)
        assertEquals(1f, innerSlider.computedStyle?.opacity)
        assertEquals("visible", innerSlider.computedStyle?.overflow)
        assertEquals(true, innerSlider.computedStyle?.pointerEventsEnabled)
        assertTrue(
            innerSlider.computedStyle?.animations?.any { it.name == "volumeSliderExpand" } == true ||
                innerSlider.computedStyle?.animation?.contains("volumeSliderExpand") == true
        )
        assertEquals("92", vol.getAttribute("data-animated-slider-width-px"))

        vol.setExpanded(false, animate = false)
        assertFalse(vol.isExpanded)
        assertFalse(vol.hasClass(ExpandingVolumeSlider.EXPANDED_CLASS))
        assertEquals(0f, vol.expansionProgress, 0.001f)
        assertEquals(0, vol.currentSliderWidthPx)
        assertEquals("0px", innerSlider.computedStyle?.width)
        assertEquals(0f, innerSlider.computedStyle?.opacity)
        assertEquals("hidden", innerSlider.computedStyle?.overflow)
        assertTrue(innerSlider.computedStyle?.transition?.contains("width") == true)

        // alwaysExpanded clears overflow clipping on inner slider
        vol.alwaysExpanded = true
        assertEquals("visible", innerSlider.computedStyle?.overflow)
        vol.alwaysExpanded = false
        assertEquals("hidden", innerSlider.computedStyle?.overflow)

        // Pure CSS :hover interaction expands without toggling EXPANDED_CLASS and collapses cleanly via transition
        vol.dispatchPointerEnter()
        assertTrue(vol.isHovered)
        assertTrue(vol.isExpanded)
        assertFalse(vol.hasClass(ExpandingVolumeSlider.EXPANDED_CLASS))
        assertEquals("92px", innerSlider.computedStyle?.width)
        assertEquals("visible", innerSlider.computedStyle?.overflow)
        assertTrue(
            innerSlider.computedStyle?.animations?.any { it.name == "volumeSliderExpand" } == true ||
                innerSlider.computedStyle?.animation?.contains("volumeSliderExpand") == true
        )
        vol.dispatchPointerLeave()
        assertFalse(vol.isHovered)
        assertFalse(vol.isExpanded)
        assertFalse(vol.hasClass(ExpandingVolumeSlider.EXPANDED_CLASS))
        assertEquals("0px", innerSlider.computedStyle?.width)
        assertEquals("hidden", innerSlider.computedStyle?.overflow)
        assertTrue(innerSlider.computedStyle?.transition?.contains("width") == true)

        vol.beginScrub()
        assertTrue(vol.isScrubbing)
        assertTrue(vol.isExpanded)
        assertTrue(vol.hasClass(ExpandingVolumeSlider.SCRUBBING_CLASS))
        vol.endScrub()
        assertFalse(vol.isScrubbing)
        assertFalse(vol.isExpanded)
        assertFalse(vol.hasClass(ExpandingVolumeSlider.SCRUBBING_CLASS))
        vol.dispose()
    }

    @Test
    fun testSeekBarAestheticSkinningOptInAndBaselineAttributes() {
        val seekBar = SeekBar(value = 25.0, min = 0.0, max = 100.0)
        assertTrue(seekBar.acceptsAestheticSkinComponentStyles)
        assertTrue(seekBar.style.isEmpty())
        assertNull(seekBar.getAttribute("data-surface-contrast"))
        assertNull(seekBar.getAttribute("data-track-color"))
        assertNull(seekBar.getAttribute("data-buffered-color"))
        assertEquals(25.0, seekBar.getAttribute("data-value")?.toDoubleOrNull() ?: -1.0, 0.001)
        assertEquals(0.25, seekBar.getAttribute("data-played-fraction")?.toDoubleOrNull() ?: -1.0, 0.001)
        assertEquals(0.0, seekBar.getAttribute("data-buffered-fraction")?.toDoubleOrNull() ?: -1.0, 0.001)
    }
}
