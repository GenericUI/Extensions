package com.nexustools.genericui.ext.media.overlay

import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.ext.media.VideoPlayerExt
import com.nexustools.genericui.ext.overlay.AbstractOverlay
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
        val player = VideoPlayerExt()
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
        assertEquals(host.id, player.fullscreenTargetElementId)

        // Initial state synchronized
        assertEquals(10.0, host.state.positionSec)
        assertEquals(120.0, host.state.durationSec)
        assertEquals("Hello from subtitle cue!", host.state.subtitleCueText)
        assertEquals(2, host.state.audioTracks.size)
        assertEquals(1, host.state.subtitleTracks.size)

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

        // Audio and subtitle track selection
        host.actions.onSelectAudioTrack?.invoke("ja")
        assertTrue(host.state.audioTracks.first { it.id == "ja" }.selected)

        host.actions.onSelectSubtitleTrack?.invoke(null)
        assertEquals("", host.state.subtitleCueText)

        host.actions.onSelectSubtitleTrack?.invoke("sub-0")
        assertEquals("Hello from subtitle cue!", host.state.subtitleCueText)

        // Buffered ranges and buffering state
        player.setBufferingState(true)
        assertTrue(host.state.isLoading)
        player.setBufferingState(false)
        assertFalse(host.state.isLoading)

        player.updateBufferedRanges(listOf(0.0 to 45.0))
        assertEquals(1, host.state.bufferedRanges.size)
        assertEquals(45.0, host.state.bufferedEndSec)

        // Fullscreen toggle
        host.actions.onToggleFullscreen?.invoke()
        assertTrue(player.isFullscreen)
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
}
