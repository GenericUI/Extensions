@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.AudioAnalyzerConfig
import com.nexustools.genericui.component.media.AudioFftSize
import com.nexustools.genericui.component.media.AudioPlayer
import com.nexustools.genericui.component.media.Player
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.ext.media.desktop.DesktopVlcMediaExtensionProvider
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.usleep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests `:extensions:media` desktop LibVLC playback, BGRA frame pool acquisition, and
 * [AudioAnalyzer] attachment using the shared `extensions/media/test.mp4` asset.
 */
class DesktopLibVlcTestMp4PlaybackTest {

    private fun resolveMediaExtensionTestMp4Path(): String {
        val candidates = listOf(
            "test.mp4",
            "extensions/media/media/test.mp4",
            "../extensions/media/media/test.mp4",
            "../../extensions/media/media/test.mp4",
            "../../../extensions/media/media/test.mp4",
            "extensions\\media\\media\\test.mp4",
            "..\\..\\test.mp4",
            "..\\..\\..\\test.mp4",
            "../test.mp4",
            "../../test.mp4",
            "../../../test.mp4"
        )
        for (path in candidates) {
            val fp = fopen(path, "rb")
            if (fp != null) {
                fclose(fp)
                return path
            }
        }
        return "extensions/media/media/test.mp4"
    }

    @Test
    fun testMediaExtensionTestMp4AssetExistsAndHasValidIsoBmffHeader() = memScoped {
        val path = resolveMediaExtensionTestMp4Path()
        val fp = fopen(path, "rb")
        assertNotNull(fp, "Expected test.mp4 to exist in :extensions:media at $path")
        try {
            fseek(fp, 0, SEEK_END)
            val fileSize = ftell(fp)
            assertTrue(fileSize > 100_000L, "Expected test.mp4 to be > 100 KB, got $fileSize bytes")

            fseek(fp, 0, SEEK_SET)
            val header = allocArray<ByteVar>(12)
            val readCount = fread(header, 1u, 12u, fp)
            assertEquals(12u, readCount, "Expected to read 12-byte ISO-BMFF header from test.mp4")
            // Bytes 4..7 must be 'ftyp'
            assertEquals('f'.code.toByte(), header[4])
            assertEquals('t'.code.toByte(), header[5])
            assertEquals('y'.code.toByte(), header[6])
            assertEquals('p'.code.toByte(), header[7])
        } finally {
            fclose(fp)
        }
    }

    @Test
    fun testDesktopVlcProviderHasNoClassOrTagFastPathsAndVideoWidgetMarksStageRoot() {
        DesktopVlcMediaExtensionProvider.install("Desktop")
        val genericControlWidget = DesktopNativeWidget("Desktop", "div").apply {
            attributes["htmlTag"] = "genericui:expanding-volume-slider"
            rebuildClassTokensCache("seek-bar expanding-volume-slider expanding-volume-pill desktop-web-video-overlay")
        }
        assertFalse(
            genericControlWidget.isInsideIsolatedStageSubtree(),
            "DesktopVlcMediaExtensionProvider must not register class/tag stage fast paths"
        )

        val videoWidget = DesktopNativeWidget("Desktop", "libvlc-video")
        val videoImpl = DesktopLibVlcVideoPlayerImpl(nativeWidget = videoWidget, platformName = "Desktop")
        try {
            assertTrue(videoWidget.isIsolatedStageSubtreeRoot)
            assertTrue(videoWidget.isInsideIsolatedStageSubtree())
        } finally {
            videoImpl.dispose()
        }
    }

    @Test
    fun testDesktopLibVlcVideoPlayerDecodesFramesAndPopulatesBgraSurfaceFromTestMp4() {
        DesktopVlcMediaExtensionProvider.install("Desktop")
        val path = resolveMediaExtensionTestMp4Path()
        val widget = DesktopNativeWidget("Desktop", "libvlc-video")
        val playerImpl = DesktopLibVlcVideoPlayerImpl(nativeWidget = widget, platformName = "Desktop")
        val analyzer = AudioAnalyzer(AudioAnalyzerConfig(fftSize = AudioFftSize.FFT_1024))
        try {
            playerImpl.setMuted(true)
            playerImpl.attachAudioAnalyzer(analyzer)
            playerImpl.setSource(path, 0.0, null)
            playerImpl.play()

            var attempts = 0
            while (attempts < 120 && playerImpl.frameSwapCounter <= 0L) {
                usleep(25_000u)
                val vblank = widget.attributes["vblankDispatch"] as? ((Double?) -> Unit)
                vblank?.invoke(attempts * 25.0)
                attempts++
            }

            assertTrue(
                playerImpl.frameSwapCounter > 0L,
                "Expected DesktopLibVlcVideoPlayerImpl to decode real video frames from $path (swapCount=${playerImpl.frameSwapCounter})"
            )

            val surfaceSpec = widget.interactiveSurfaceSpec
            assertNotNull(surfaceSpec, "Expected DesktopInteractiveSurfaceSpec to be attached to VideoPlayer widget")
            assertTrue(surfaceSpec.surfaceWidth() > 0 && surfaceSpec.surfaceHeight() > 0)
            assertTrue(surfaceSpec.swapCount() > 0L)

            val bgraPtr = surfaceSpec.acquireBgraPtr?.invoke()
            assertNotNull(bgraPtr, "Expected acquireBgraPtr to return non-null decoded frame buffer pointer")

            val freqBytes = ByteArray(analyzer.frequencyBinCount)
            val timeBytes = ByteArray(analyzer.fftSize)
            analyzer.getByteFrequencyData(freqBytes)
            analyzer.getByteTimeDomainData(timeBytes)
            assertEquals(512, freqBytes.size)
            assertEquals(1024, timeBytes.size)
        } finally {
            playerImpl.pause()
            playerImpl.dispose()
            analyzer.dispose()
        }
    }

    @Test
    fun testDesktopLibVlcVideoPlayerSetSourceWithoutPlayPrerollsFirstFrameWhilePaused() {
        DesktopVlcMediaExtensionProvider.install("Desktop")
        val path = resolveMediaExtensionTestMp4Path()
        val widget = DesktopNativeWidget("Desktop", "libvlc-video")
        val playerImpl = DesktopLibVlcVideoPlayerImpl(nativeWidget = widget, platformName = "Desktop")
        try {
            var mediaReadyCalled = false
            playerImpl.setOnMediaReady { mediaReadyCalled = true }

            // Call setSource WITH durationOverrideSeconds but WITHOUT calling play()
            playerImpl.setSource(path, 0.0, 10.0)
            assertFalse(playerImpl.isPlaying(), "Expected playerImpl.isPlaying() to remain false during first-frame preroll")

            var attempts = 0
            while (attempts < 120 && playerImpl.frameSwapCounter <= 0L) {
                usleep(25_000u)
                // Even if acquireReadFrameBgraPtr() is called before tickVBlank(), tickVBlank() must still dirty vblank
                playerImpl.acquireReadFrameBgraPtr()
                playerImpl.tickVBlank()
                assertFalse(playerImpl.isPlaying(), "Expected playerImpl.isPlaying() to stay false throughout silent preroll")
                attempts++
            }

            if (LibVlcBindings.isAvailable) {
                assertTrue(
                    playerImpl.frameSwapCounter >= 1L,
                    "Expected setSource without play() to preroll at least 1 frame from $path (swapCount=${playerImpl.frameSwapCounter})"
                )
            }
            assertTrue(playerImpl.isMediaReady(), "Expected isMediaReady() to be true once first frame is decoded")
            assertTrue(mediaReadyCalled, "Expected onMediaReady listener to have been invoked")
            assertFalse(playerImpl.isPlaying(), "Expected playerImpl.isPlaying() to be false after first-frame preroll pauses")
            if (LibVlcBindings.isAvailable) {
                assertEquals(true, widget.attributes["vblankDirtied"], "Expected tickVBlank() to mark vblankDirtied = true")
            }
        } finally {
            playerImpl.dispose()
        }
    }

    @Test
    fun testDesktopLibVlcVideoPlayerPlaysFromZeroAfterPrerollAndAcrossSetSourceReloadsWithoutSeeking() {
        DesktopVlcMediaExtensionProvider.install("Desktop")
        val path = resolveMediaExtensionTestMp4Path()
        val widget = DesktopNativeWidget("Desktop", "libvlc-video")
        val playerImpl = DesktopLibVlcVideoPlayerImpl(nativeWidget = widget, platformName = "Desktop")
        try {
            playerImpl.setMuted(false)
            playerImpl.setVolume(0.0f)
            playerImpl.setSource(path, 0.0, 10.0)
            // Internal preroll mute must never leak through isMuted()
            assertFalse(playerImpl.isMuted(), "Expected logical isMuted() to remain false during silent preroll")

            var prerollTicks = 0
            while (prerollTicks < 120 && playerImpl.frameSwapCounter <= 0L) {
                usleep(25_000u)
                playerImpl.tickVBlank()
                assertFalse(playerImpl.isMuted(), "Expected isMuted() to stay false during preroll")
                prerollTicks++
            }
            val swapAfterPreroll = playerImpl.frameSwapCounter

            // Start playback at 0:00 without calling seekTo()
            playerImpl.play()
            assertTrue(playerImpl.isPlaying(), "Expected isPlaying() to be true immediately after play() at 0:00")

            var playTicks = 0
            while (playTicks < 120 && playerImpl.frameSwapCounter <= swapAfterPreroll + 3L) {
                usleep(25_000u)
                playerImpl.tickVBlank()
                playTicks++
            }
            if (LibVlcBindings.isAvailable) {
                assertTrue(
                    playerImpl.frameSwapCounter > swapAfterPreroll + 2L,
                    "Expected frames to advance from 0:00 without seeking (before=$swapAfterPreroll, after=${playerImpl.frameSwapCounter})"
                )
            }
            assertTrue(playerImpl.isPlaying(), "Expected player to remain playing from 0:00")
            assertFalse(playerImpl.isMuted(), "Expected player to remain unmuted while playing")

            // Reloading a new/next item at 0:00 while already playing must auto-start without requiring seekTo()
            playerImpl.stop()
            playerImpl.play()
            playerImpl.setSource(path, 0.0, 10.0)
            var reloadTicks = 0
            while (reloadTicks < 120 && playerImpl.frameSwapCounter < 3L) {
                usleep(25_000u)
                playerImpl.tickVBlank()
                reloadTicks++
            }
            if (LibVlcBindings.isAvailable) {
                assertTrue(
                    playerImpl.frameSwapCounter >= 3L,
                    "Expected setSource(0.0) while playing to decode frames without seeking (swapCount=${playerImpl.frameSwapCounter})"
                )
            }
            assertTrue(playerImpl.isPlaying(), "Expected player to remain playing after setSource(0.0) reload")
        } finally {
            playerImpl.dispose()
        }
    }

    @Test
    fun testDesktopLibVlcAudioPlayerWorksWithoutDesktopNativeWidgetAndPlaysTestMp4AsPlayer() {
        DesktopVlcMediaExtensionProvider.install("Desktop")
        val path = resolveMediaExtensionTestMp4Path()
        val audioImpl = DesktopLibVlcAudioPlayerImpl()
        assertIs<AudioPlayerNativeImpl>(audioImpl)

        val audioPlayer = AudioPlayer()
        assertIs<Player>(audioPlayer)
        assertFalse((audioPlayer as Any) is AbstractComponent)

        try {
            audioPlayer.volume = 0.0f
            audioPlayer.setSource(path, seekOffsetSeconds = 0.0)
            assertTrue(audioPlayer.isMediaReady)
            audioPlayer.play()
            usleep(50_000u)
            audioPlayer.pause()
            assertFalse(audioPlayer.isPlaying)
            audioPlayer.stop()
            assertEquals(0.0, audioPlayer.currentTimeSeconds)
        } finally {
            audioPlayer.dispose()
            audioImpl.dispose()
        }
    }

    @Test
    fun testDesktopLibVlcVideoPlayerWiresResizeModeObjectFitBackdropAndDetectedAspectRatio() {
        DesktopVlcMediaExtensionProvider.install("Desktop")
        val path = resolveMediaExtensionTestMp4Path()
        val widget = DesktopNativeWidget("Desktop", "libvlc-video")
        val playerImpl = DesktopLibVlcVideoPlayerImpl(nativeWidget = widget, platformName = "Desktop")
        try {
            val surfaceSpec = widget.interactiveSurfaceSpec
            assertNotNull(surfaceSpec, "Expected DesktopInteractiveSurfaceSpec to be attached to VideoPlayer widget")
            assertFalse(surfaceSpec.preferHardwareStretchBlit, "Expected preferHardwareStretchBlit to be false")
            assertEquals(ObjectFit.CONTAIN, surfaceSpec.resolveContentFitMode())
            assertTrue(surfaceSpec.shouldPaintBlackBackdrop(), "Expected shouldPaintBlackBackdrop() to be true")

            playerImpl.setResizeMode(VideoResizeMode.FILL)
            assertEquals(ObjectFit.COVER, surfaceSpec.resolveContentFitMode())

            playerImpl.setResizeMode(VideoResizeMode.STRETCH)
            assertEquals(ObjectFit.FILL, surfaceSpec.resolveContentFitMode())

            playerImpl.setResizeMode(VideoResizeMode.FIT)
            assertEquals(ObjectFit.CONTAIN, surfaceSpec.resolveContentFitMode())

            playerImpl.setMuted(true)
            playerImpl.setSource(path, 0.0, null)
            playerImpl.play()

            var attempts = 0
            while (attempts < 120 && playerImpl.frameSwapCounter <= 0L) {
                usleep(25_000u)
                playerImpl.tickVBlank(attempts * 25.0)
                attempts++
            }
            playerImpl.tickVBlank((attempts + 1) * 25.0)

            assertTrue(
                playerImpl.frameSwapCounter > 0L,
                "Expected DesktopLibVlcVideoPlayerImpl to decode frames from $path (swapCount=${playerImpl.frameSwapCounter})"
            )

            val resolvedAspectRatio = surfaceSpec.resolveContentAspectRatio(1920, 1080)
            assertTrue(
                resolvedAspectRatio.isFinite() && resolvedAspectRatio > 0.5f,
                "Expected resolved content aspect ratio to be finite and > 0.5f, got $resolvedAspectRatio"
            )
            if (LibVlcBindings.isAvailable) {
                val detected = playerImpl.detectedVideoAspectRatio
                assertNotNull(detected, "Expected libvlc_video_get_size to detect video aspect ratio")
                assertEquals(detected, resolvedAspectRatio)
            }
        } finally {
            playerImpl.pause()
            playerImpl.dispose()
        }
    }

    @Test
    fun testDesktopLibVlcPreferredFrameRateUsesFullVBlankWhilePlayingAndThrottlesWhenPausedOrIdle() {
        DesktopVlcMediaExtensionProvider.install("Desktop")
        val path = resolveMediaExtensionTestMp4Path()
        val widget = DesktopNativeWidget("Desktop", "libvlc-video")
        val playerImpl = DesktopLibVlcVideoPlayerImpl(nativeWidget = widget, platformName = "Desktop")
        try {
            val spec = assertNotNull(widget.interactiveSurfaceSpec)
            val rateProvider = assertNotNull(spec.preferredFrameRateHz)

            // Idle (no source): throttle to 0.0
            assertEquals(0.0, rateProvider())

            playerImpl.setMuted(true)
            playerImpl.setSource(path, 0.0, null)
            playerImpl.play()

            var attempts = 0
            while (attempts < 120 && playerImpl.frameSwapCounter < 3L) {
                usleep(25_000u)
                playerImpl.tickVBlank(attempts * 25.0)
                attempts++
            }
            playerImpl.tickVBlank((attempts + 1) * 25.0)

            // Active playback must return null (full native display VSync rate) rather than throttling to sub-refresh source FPS
            assertEquals(null, rateProvider(), "Active video playback must run at full display VBlank rate (null)")

            // Paused after media ready: throttle down to 2.0 Hz
            playerImpl.pause()
            playerImpl.tickVBlank((attempts + 2) * 25.0)
            assertEquals(2.0, rateProvider(), "Paused ready player must throttle VBlank to 2.0 Hz")
        } finally {
            playerImpl.pause()
            playerImpl.dispose()
        }
    }
}
