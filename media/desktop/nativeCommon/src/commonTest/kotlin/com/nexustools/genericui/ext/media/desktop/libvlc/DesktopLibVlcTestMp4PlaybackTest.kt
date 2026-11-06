@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.AudioAnalyzerConfig
import com.nexustools.genericui.component.media.AudioFftSize
import com.nexustools.genericui.component.media.AudioPlayer
import com.nexustools.genericui.component.media.Player
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.core.Component
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
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
            "D:\\IdeaProjects\\GenericUI\\extensions\\media\\test.mp4",
            "..\\..\\test.mp4",
            "..\\..\\..\\test.mp4",
            "extensions\\media\\test.mp4",
            "test.mp4",
            "../test.mp4",
            "../../test.mp4",
            "../../../test.mp4",
            "extensions/media/test.mp4"
        )
        for (path in candidates) {
            val fp = fopen(path, "rb")
            if (fp != null) {
                fclose(fp)
                return path
            }
        }
        return "D:\\IdeaProjects\\GenericUI\\extensions\\media\\test.mp4"
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
    fun testDesktopLibVlcAudioPlayerWorksWithoutDesktopNativeWidgetAndPlaysTestMp4AsPlayer() {
        DesktopVlcMediaExtensionProvider.install("Desktop")
        val path = resolveMediaExtensionTestMp4Path()
        val audioImpl = DesktopLibVlcAudioPlayerImpl()
        assertIs<AudioPlayerNativeImpl>(audioImpl)

        val audioPlayer = AudioPlayer()
        assertIs<Player>(audioPlayer)
        assertFalse((audioPlayer as Any) is Component)

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
}
