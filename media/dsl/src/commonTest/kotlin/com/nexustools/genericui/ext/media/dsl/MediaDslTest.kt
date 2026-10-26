package com.nexustools.genericui.ext.media.dsl

import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.media.AudioPlayerExt
import com.nexustools.genericui.ext.media.VideoPlayerExt
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
            val analyzer = audioAnalyzerExt(fftSize = 1024)
            videoPlayerExt(sourceUrl = "https://example.com/video.mp4", id = "vid") {
                attachAudioAnalyzer(analyzer.analyzer)
            }
            audioPlayerExt(sourceUrl = "https://example.com/audio.mp3", id = "aud") {
                attachAudioAnalyzer(analyzer)
            }
            videoOverlayHost(
                sourceUrl = "https://example.com/stream.mp4",
                overlay = customOverlay,
                id = "vid-host"
            )
        }
        assertEquals(3, root.children.size)
        val vid = root.children[0] as VideoPlayerExt
        val aud = root.children[1] as AudioPlayerExt
        val host = root.children[2] as VideoOverlayHost
        assertEquals("vid", vid.id)
        assertEquals("aud", aud.id)
        assertEquals("vid-host", host.id)
        assertEquals("vid-host", host.videoPlayer.fullscreenTargetElementId)
        assertSame(customOverlay, host.activeOverlay)
    }
}
