package com.nexustools.genericui.component.media

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.enums.media.MediaPreloadMode
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.enums.window.TitleBarControlsPlacement
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.overlay.DefaultWindowTitleBarOverlay
import com.nexustools.genericui.ext.overlay.OverlayExtension
import com.nexustools.genericui.ext.overlay.OverlayHostBox
import com.nexustools.genericui.ext.overlay.OverlayWindowTitleBarConfig
import com.nexustools.genericui.ext.overlay.YoutubePlayerOverlay
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
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

class VideoFragmentTest {

    @BeforeTest
    fun setUp() {
        UiTimerScheduler.clearAllTimers()
    }

    @AfterTest
    fun tearDown() {
        UiTimerScheduler.clearAllTimers()
    }

    private fun createTestWindow(
        title: String = "Video Fragment Window",
        capabilities: TitleCapabilities = TitleCapabilities(
            isTitleBarRequired = true,
            defaultControlsPlacement = TitleBarControlsPlacement.LEADING,
            prefersBlurredTranslucentOverlayTitleBar = true
        )
    ): Window = object : Window(title = title) {
        override val titleCapabilities: TitleCapabilities = capabilities
    }

    @Test
    fun testVideoFragmentImplementsPlayerContractAndTrackDelegation() {
        val fragment = VideoFragment(sourceUrl = "https://example.com/initial.mp4")
        val player: Player = fragment

        assertEquals("https://example.com/initial.mp4", player.sourceUrl)
        assertTrue(fragment.prefersUndecoratedWindow)
        assertIs<OverlayHostBox>(fragment.overlayHost)
        assertTrue(fragment.windowTitleBarEnabled)
        assertEquals("0", fragment.getAttribute("borderRadiusPx"))
        assertEquals("0", fragment.overlayHost.getAttribute("borderRadiusPx"))
        fragment.applyStyleSheet(MediaExtension.defaultStyleSheet())
        assertEquals(0f, fragment.borderRadiusPx)
        assertEquals(0f, fragment.overlayHost.borderRadiusPx)

        var playChanges = 0
        var lastPlaying: Boolean? = null
        var lastTimeUpdate: Double? = null
        var lastDuration: Double? = null
        var lastBuffering: Boolean? = null
        var lastEnded = false
        var lastError: String? = null

        player.onPlayingStateChanged = {
            playChanges++
            lastPlaying = it
        }
        player.onTimeUpdate = { lastTimeUpdate = it }
        player.onDurationChanged = { lastDuration = it }
        player.onBufferingChanged = { lastBuffering = it }
        player.onEnded = { lastEnded = true }
        player.onError = { lastError = it }

        player.setSource("https://example.com/episode.mp4", seekOffsetSeconds = 12.0, durationOverrideSeconds = 300.0)
        assertEquals("https://example.com/episode.mp4", player.sourceUrl)
        assertEquals(12.0, player.seekOffsetSeconds)
        assertEquals(300.0, player.durationOverrideSeconds)
        assertEquals(12_000L, fragment.positionMs)
        assertEquals(300_000L, fragment.durationMs)
        assertEquals(300.0, lastDuration)

        player.play()
        assertTrue(player.isPlaying)
        assertTrue(fragment.videoPlayer.isPlaying)
        assertEquals(true, lastPlaying)
        assertTrue(playChanges >= 1)

        player.pause()
        assertFalse(player.isPlaying)
        assertEquals(false, lastPlaying)

        player.togglePlayPause()
        assertTrue(player.isPlaying)

        player.seekTo(45.5)
        assertEquals(45.5, player.currentTimeSeconds)
        assertEquals(45.5, lastTimeUpdate)
        assertEquals(45_500L, fragment.positionMs)

        player.skipDelta(4.5)
        assertEquals(50.0, player.currentTimeSeconds)
        assertEquals(50_000L, fragment.positionMs)

        player.volume = 0.45f
        assertEquals(0.45f, fragment.videoPlayer.volume)
        player.isMuted = true
        assertTrue(fragment.videoPlayer.isMuted)
        player.playbackSpeed = 1.5f
        assertEquals(1.5f, fragment.videoPlayer.playbackSpeed)
        player.preloadMode = MediaPreloadMode.METADATA
        assertEquals(MediaPreloadMode.METADATA, fragment.videoPlayer.preloadMode)
        player.adaptivePreloadBufferingEnabled = false
        assertFalse(fragment.videoPlayer.adaptivePreloadBufferingEnabled)
        player.keepScreenOn = false
        assertFalse(fragment.videoPlayer.keepScreenOn)
        fragment.resizeMode = VideoResizeMode.FILL
        assertEquals(VideoResizeMode.FILL, fragment.videoPlayer.resizeMode)

        player.setBufferingState(true)
        assertTrue(player.isBuffering)
        assertEquals(true, lastBuffering)
        player.updateBufferedRanges(listOf(0.0 to 90.0))
        assertEquals(listOf(0.0 to 90.0), player.bufferedRanges)
        assertEquals(90.0, player.bufferedEndSeconds)

        // Track & subtitle helpers delegate through VideoPlayer / VideoOverlayHost
        fragment.setAudioTracks(
            listOf(
                MediaTrackInfo(id = "en", label = "English", language = "en", isSelected = true),
                MediaTrackInfo(id = "es", label = "Spanish", language = "es", isSelected = false)
            )
        )
        assertEquals(2, fragment.audioTracks.size)
        assertEquals("en", fragment.getSelectedAudioTrackId())
        assertTrue(fragment.selectAudioTrack("es"))
        assertEquals("es", fragment.getSelectedAudioTrackId())

        fragment.addExternalSubtitleTrack("English CC", "en", "https://example.com/en.vtt")
        assertEquals(1, fragment.subtitleTracks.size)
        fragment.loadSubtitleContent(
            """
            WEBVTT

            1
            00:00:40.000 --> 00:00:55.000
            Delegated caption cue
            """.trimIndent()
        )
        assertEquals("Delegated caption cue", fragment.getActiveSubtitleCueText())
        assertTrue(fragment.subtitleCues.isNotEmpty())
        assertTrue(fragment.selectSubtitleTrack(null))
        assertNull(fragment.getSelectedSubtitleTrackId())

        player.onEnded?.invoke()
        assertTrue(lastEnded)
        player.onError?.invoke("network")
        assertEquals("network", lastError)

        player.stop()
        assertFalse(player.isPlaying)
        assertEquals(0.0, player.currentTimeSeconds)

        fragment.dispose()
        assertTrue(fragment.isDisposed)
        assertTrue(fragment.overlayHost.isDisposed)
        assertTrue(fragment.videoPlayer.isDisposed)
    }

    @Test
    fun testUndecoratedWindowIntegrationDefaultAndCustomOverlaysAndWindowedVsFullscreenTitleBar() {
        val window = createTestWindow(title = "Cinema Player").apply {
            setTitleBar(TitleBar())
        }
        val fragment = VideoFragment(sourceUrl = "https://example.com/cinema.mp4")

        // Detached: windowed titlebar remains hidden
        assertFalse(fragment.windowTitleBar.isVisible)
        assertEquals(0, fragment.windowTitleBar.effectiveHeightPx)

        // Hosting VideoFragment sets undecorated=true and clears native TitleBar while embedding overlay titlebar
        window.setFragment(fragment)
        assertTrue(window.isUndecorated)
        assertNull(window.getTitleBar())
        assertTrue(fragment.windowTitleBarEnabled)
        assertTrue(fragment.windowTitleBar.isVisible)
        assertEquals(32, fragment.windowTitleBar.effectiveHeightPx)
        assertEquals("Cinema Player", fragment.windowTitleBar.titleLabel.text)
        fragment.applyStyleSheet(OverlayExtension.defaultStyleSheet())
        assertTrue(fragment.windowTitleBar.hasClass("gui-overlay-titlebar-blurred"))
        assertEquals(CssLength.px(22f), fragment.windowTitleBar.computedStyle?.backdropFilter?.blur)
        assertEquals(TitleBarControlsPlacement.LEADING, fragment.windowTitleBar.resolvedControlsPlacement)
        assertNull(fragment.overlay)
        val mountedDefault = fragment.overlayHost.overlayRoot.children.firstOrNull() as? DefaultWindowTitleBarOverlay
        assertNotNull(mountedDefault)

        // Attach YoutubePlayerOverlay via normal overlay API -> embeds same windowed titlebar and offsets topBar
        val ytOverlay = YoutubePlayerOverlay()
        val returnedHost = fragment.attachVideoOverlay(ytOverlay)
        assertSame(fragment.overlayHost, returnedHost)
        assertSame(ytOverlay, fragment.overlay)
        assertSame(ytOverlay.windowTitleBar, fragment.windowTitleBar)
        assertTrue(ytOverlay.windowTitleBarEnabled)
        assertTrue(ytOverlay.windowTitleBar.isVisible)
        assertEquals("32", ytOverlay.topBar.getAttribute("data-titlebar-offset-px"))
        assertEquals(CssLength.px(32), ytOverlay.topBar.style.marginTop)

        // Fullscreen via window.setFullscreen(true) hides embedded titlebar & collapses topBar offset to 0px
        window.setFullscreen(true)
        assertFalse(fragment.windowTitleBar.isVisible)
        assertEquals(0, fragment.windowTitleBar.effectiveHeightPx)
        assertEquals("0", ytOverlay.topBar.getAttribute("data-titlebar-offset-px"))
        assertEquals(CssLength.px(0), ytOverlay.topBar.style.marginTop)

        // Exit fullscreen restores embedded titlebar & 32px topBar offset
        window.setFullscreen(false)
        assertTrue(fragment.windowTitleBar.isVisible)
        assertEquals(32, fragment.windowTitleBar.effectiveHeightPx)
        assertEquals("32", ytOverlay.topBar.getAttribute("data-titlebar-offset-px"))
        assertEquals(CssLength.px(32), ytOverlay.topBar.style.marginTop)

        // Fragment.setFullscreen(true/false) and VideoPlayer.setFullscreen(true/false) also update cleanly
        fragment.setFullscreen(true)
        assertFalse(fragment.windowTitleBar.isVisible)
        fragment.setFullscreen(false)
        assertTrue(fragment.windowTitleBar.isVisible)

        fragment.videoPlayer.setFullscreen(true)
        assertFalse(fragment.windowTitleBar.isVisible)
        assertEquals(0, fragment.windowTitleBar.effectiveHeightPx)
        fragment.setFullscreen(false)
        assertTrue(fragment.windowTitleBar.isVisible)
        assertEquals(32, fragment.windowTitleBar.effectiveHeightPx)

        // WindowState.FULLSCREEN also hides the embedded titlebar
        window.setWindowState(WindowState.FULLSCREEN)
        assertFalse(fragment.windowTitleBar.isVisible)
        window.setWindowState(WindowState.NORMAL)
        assertTrue(fragment.windowTitleBar.isVisible)

        // Detaching overlay preserves DefaultWindowTitleBarOverlay in windowed mode
        fragment.detachVideoOverlay()
        assertNull(fragment.overlay)
        assertTrue(fragment.windowTitleBar.isVisible)
        assertEquals(32, fragment.windowTitleBar.effectiveHeightPx)

        // withOverlay returns VideoFragment for fluent chaining & custom config updates live
        val chained = fragment.withOverlay(YoutubePlayerOverlay())
        assertSame(fragment, chained)
        fragment.windowTitleBarConfig = OverlayWindowTitleBarConfig(
            enabled = true,
            heightPx = 38,
            title = "Custom Stream Header"
        )
        assertEquals(38, fragment.windowTitleBar.effectiveHeightPx)
        assertEquals("Custom Stream Header", fragment.windowTitleBar.titleLabel.text)

        // Transitioning back to a normal Fragment restores decorated window state
        val regularFragment = Fragment()
        window.setFragment(regularFragment)
        assertFalse(window.isUndecorated)

        window.close()
    }
}
