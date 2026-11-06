package com.nexustools.genericui.ext.media.dsl

import com.nexustools.genericui.component.media.AudioPlayer
import com.nexustools.genericui.component.media.Player
import com.nexustools.genericui.component.media.SeekBar
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.media.overlay.VideoOverlayHost
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class MediaDslTest {

    @Test
    fun testMediaDsl() {
        val customOverlay = object : AbstractOverlay("dsl-overlay") {}
        lateinit var scopedAudio: AudioPlayer
        val root = buildDiv {
            val analyzer = audioAnalyzer(fftSize = 1024)
            videoPlayer(sourceUrl = "https://example.com/video.mp4", id = "vid") {
                attachAudioAnalyzer(analyzer)
            }
            scopedAudio = audioPlayer(sourceUrl = "https://example.com/audio.mp3", volume = 0.8f) {
                attachAudioAnalyzer(analyzer)
            }
            videoOverlayHost(
                sourceUrl = "https://example.com/stream.mp4",
                overlay = customOverlay,
                id = "vid-host"
            )
            seekBar(value = 12.5, min = 0.0, max = 120.0, step = 0.5, id = "seek") {
                bufferedValue = 45.0
            }
        }
        val standaloneAudio = audioPlayer(sourceUrl = "https://example.com/standalone.mp3", volume = 0.6f)
        assertEquals(3, root.children.size)
        val vid = root.children[0] as VideoPlayer
        val host = root.children[1] as VideoOverlayHost
        val bar = root.children[2] as SeekBar
        assertIs<Player>(vid)
        assertIs<Player>(scopedAudio)
        assertIs<Player>(standaloneAudio)
        assertFalse((scopedAudio as Any) is AbstractComponent)
        assertEquals("vid", vid.id)
        assertEquals("https://example.com/audio.mp3", scopedAudio.sourceUrl)
        assertEquals(0.8f, scopedAudio.volume)
        assertEquals("https://example.com/standalone.mp3", standaloneAudio.sourceUrl)
        assertEquals(0.6f, standaloneAudio.volume)
        assertEquals("vid-host", host.id)
        assertEquals("seek", bar.id)
        assertEquals(12.5, bar.value)
        assertEquals(45.0, bar.bufferedValue)
        assertSame(host, host.videoPlayer.attachedOverlayHost)
        assertSame(customOverlay, host.activeOverlay)
    }
}
