package com.nexustools.genericui.ext.media.dsl

import com.nexustools.genericui.component.media.AudioPlayer
import com.nexustools.genericui.component.media.SeekBar
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.media.overlay.VideoOverlayHost
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MediaDslTest {

    @Test
    fun testMediaDsl() {
        val customOverlay = object : AbstractOverlay("dsl-overlay") {}
        val root = buildDiv {
            val analyzer = audioAnalyzer(fftSize = 1024)
            videoPlayer(sourceUrl = "https://example.com/video.mp4", id = "vid") {
                attachAudioAnalyzer(analyzer)
            }
            audioPlayer(sourceUrl = "https://example.com/audio.mp3", id = "aud") {
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
        assertEquals(4, root.children.size)
        val vid = root.children[0] as VideoPlayer
        val aud = root.children[1] as AudioPlayer
        val host = root.children[2] as VideoOverlayHost
        val bar = root.children[3] as SeekBar
        assertEquals("vid", vid.id)
        assertEquals("aud", aud.id)
        assertEquals("vid-host", host.id)
        assertEquals("seek", bar.id)
        assertEquals(12.5, bar.value)
        assertEquals(45.0, bar.bufferedValue)
        assertSame(host, host.videoPlayer.attachedOverlayHost)
        assertSame(customOverlay, host.activeOverlay)
    }
}
