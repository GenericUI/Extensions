package com.nexustools.genericui.ext.media.dsl

import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.media.AudioPlayerExt
import com.nexustools.genericui.ext.media.VideoPlayerExt
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaDslTest {

    @Test
    fun testMediaDsl() {
        val root = buildDiv {
            val analyzer = audioAnalyzerExt(fftSize = 1024)
            videoPlayerExt(sourceUrl = "https://example.com/video.mp4", id = "vid") {
                attachAudioAnalyzer(analyzer.analyzer)
            }
            audioPlayerExt(sourceUrl = "https://example.com/audio.mp3", id = "aud") {
                attachAudioAnalyzer(analyzer)
            }
        }
        assertEquals(2, root.children.size)
        val vid = root.children[0] as VideoPlayerExt
        val aud = root.children[1] as AudioPlayerExt
        assertEquals("vid", vid.id)
        assertEquals("aud", aud.id)
    }
}
