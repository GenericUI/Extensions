package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.MediaPreloadMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlayerInterfaceTest {

    @Test
    fun testVideoPlayerAndAudioPlayerImplementPlayerInterfaceAndAudioPlayerIsNonComponent() {
        val videoPlayer = VideoPlayer()
        val audioPlayer = AudioPlayer()

        assertIs<Player>(videoPlayer)
        assertIs<Player>(audioPlayer)
        assertIs<AbstractComponent>(videoPlayer)
        assertFalse((audioPlayer as Any) is AbstractComponent, "AudioPlayer must not extend AbstractComponent")

        videoPlayer.dispose()
        audioPlayer.dispose()
    }

    @Test
    fun testPolymorphicPlayerOperationsAcrossVideoPlayerAndAudioPlayer() {
        val players: List<Player> = listOf(VideoPlayer(), AudioPlayer())
        val analyzer = AudioAnalyzer(AudioAnalyzerConfig(fftSize = AudioFftSize.FFT_1024))

        for (player in players) {
            var lastTime = -1.0
            var lastBuffered: List<Pair<Double, Double>> = emptyList()
            player.onTimeUpdate = { lastTime = it }
            player.onBufferedRangesChanged = { lastBuffered = it }

            assertEquals(MediaPreloadMode.AUTO, player.preloadMode)
            assertTrue(player.adaptivePreloadBufferingEnabled)

            player.preloadMode = MediaPreloadMode.METADATA
            assertEquals(MediaPreloadMode.METADATA, player.preloadMode)
            player.adaptivePreloadBufferingEnabled = false
            assertFalse(player.adaptivePreloadBufferingEnabled)

            player.volume = 0.75f
            assertEquals(0.75f, player.volume, 0.001f)

            player.setSource("https://example.com/media-stream.mp4", seekOffsetSeconds = 12.5)
            assertEquals("https://example.com/media-stream.mp4", player.sourceUrl)
            assertEquals(12.5, player.seekOffsetSeconds)
            assertEquals(12.5, player.currentTimeSeconds)

            player.play()
            assertTrue(player.isPlaying)

            player.seekTo(42.0)
            assertEquals(42.0, player.currentTimeSeconds)
            assertEquals(42.0, lastTime)

            player.updateBufferedRanges(listOf(0.0 to 60.0))
            assertEquals(listOf(0.0 to 60.0), player.bufferedRanges)
            assertEquals(60.0, player.bufferedEndSeconds)
            assertEquals(listOf(0.0 to 60.0), lastBuffered)
            assertTrue(player.getRequiredSafeBufferSeconds() >= 0.0)

            assertTrue(player.getSupportedAudioCodecs().isEmpty())
            assertFalse(player.isAudioCodecSupported(AudioCodec.MP3))

            player.attachAudioAnalyzer(analyzer)
            player.attachAudioAnalyzer(null)

            player.pause()
            assertFalse(player.isPlaying)

            player.stop()
            assertFalse(player.isPlaying)
            assertEquals(0.0, player.currentTimeSeconds)

            player.dispose()
        }

        analyzer.dispose()
    }

    @Test
    fun testSeekBarAndExpandingVolumeSliderBindToAudioPlayer() {
        val audioPlayer = AudioPlayer()
        audioPlayer.setSource("https://example.com/podcast.mp3", durationOverrideSeconds = 180.0)
        val seekBar = SeekBar(value = 0.0, min = 0.0, max = 100.0)
        val volumeSlider = ExpandingVolumeSlider(value = 100.0)

        val seekBinding = seekBar.bindTo(audioPlayer)
        val volBinding = volumeSlider.bindTo(audioPlayer)

        assertEquals(180.0, seekBar.max)
        audioPlayer.seekTo(45.0)
        assertEquals(45.0, seekBar.value)

        seekBar.value = 90.0
        assertEquals(90.0, audioPlayer.currentTimeSeconds)

        audioPlayer.volume = 0.5f
        assertEquals(50.0, volumeSlider.value, 0.5)

        seekBinding.dispose()
        volBinding.dispose()
        audioPlayer.dispose()
    }

    @Test
    fun testVideoPlayerSelectAudioTrackByIndexAndWebVttOptionalHoursAndTagStripping() {
        val videoPlayer = VideoPlayer()
        assertTrue(videoPlayer.internalSubtitlesEnabled)
        videoPlayer.internalSubtitlesEnabled = false
        assertFalse(videoPlayer.internalSubtitlesEnabled)

        assertTrue(videoPlayer.selectAudioTrackByIndex(0))
        assertEquals("index:0", videoPlayer.getSelectedAudioTrackId())
        assertTrue(videoPlayer.selectAudioTrackByIndex(2))
        assertEquals("index:2", videoPlayer.getSelectedAudioTrackId())

        val vttContent = """
            WEBVTT

            00:05.000 --> 00:09.500 align:middle line:90%
            <i>Hello &amp; welcome</i> to <b>Episode Caster</b>!

            2
            01:02:03.250 --> 01:02:06.750
            <c.yellow>Line one</c>
            Line &lt;two&gt; &quot;quoted&quot; &#39;ok&#39;
        """.trimIndent()

        val cues = videoPlayer.loadSubtitleContent(vttContent)
        assertEquals(2, cues.size)
        assertEquals(5.0, cues[0].startTimeSeconds, 0.001)
        assertEquals(9.5, cues[0].endTimeSeconds, 0.001)
        assertEquals("Hello & welcome to Episode Caster!", cues[0].text)

        assertEquals(3723.25, cues[1].startTimeSeconds, 0.001)
        assertEquals(3726.75, cues[1].endTimeSeconds, 0.001)
        assertEquals("Line one\nLine <two> \"quoted\" 'ok'", cues[1].text)

        videoPlayer.dispose()
    }
}
