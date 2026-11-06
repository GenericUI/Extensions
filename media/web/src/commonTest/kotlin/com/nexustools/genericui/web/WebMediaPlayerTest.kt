package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.AudioAnalyzerConfig
import com.nexustools.genericui.component.media.AudioFftSize
import com.nexustools.genericui.component.media.AudioPlayer
import com.nexustools.genericui.component.media.Player
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.ext.media.web.WebMediaExtensionProvider
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebMediaPlayerTest {

    @Test
    fun testWebAudioPlayerImplIsDirectAudioPlayerNativeImplWithDetachedAudioElement() {
        val impl = WebAudioPlayerImpl()
        assertIs<AudioPlayerNativeImpl>(impl)
        assertFalse((impl as Any) is NativeImpl<*>, "WebAudioPlayerImpl must not implement NativeImpl<W>")

        assertEquals("audio", impl.audioElement.tagName)
        assertEquals("anonymous", impl.audioElement.getAttribute("crossorigin"))
        assertEquals("auto", impl.audioElement.getAttribute("preload"))
        assertEquals("none", impl.audioElement.style["display"])

        var readyFired = false
        var timeUpdated = -1.0
        var endedFired = false
        impl.setOnMediaReady { readyFired = true }
        impl.setOnTimeUpdate { timeUpdated = it }
        impl.setOnEnded { endedFired = true }

        val analyzer = AudioAnalyzer(AudioAnalyzerConfig(fftSize = AudioFftSize.FFT_1024))
        impl.attachAudioAnalyzer(analyzer)

        impl.setVolume(0.65f)
        assertEquals(0.65f, impl.getVolume(), 0.001f)

        impl.setSource("https://example.com/track.mp3", seekOffsetSeconds = 5.0)
        assertEquals("https://example.com/track.mp3", impl.audioElement.getAttribute("src"))
        assertEquals(5.0, impl.getCurrentTimeSeconds())
        if (WebBrowserDomBridge.isBrowserEnvironment) {
            impl.audioElement.dispatchFromBrowser("loadeddata", null)
        }
        assertTrue(impl.isMediaReady())
        assertTrue(readyFired)

        impl.play()
        assertTrue(impl.isPlaying())
        assertEquals("true", impl.audioElement.getAttribute("data-playing"))

        impl.seekTo(18.0)
        assertEquals(18.0, impl.getCurrentTimeSeconds())
        assertEquals(18.0, timeUpdated)

        impl.audioElement.dispatchFromBrowser("ended", null)
        assertFalse(impl.isPlaying())
        assertTrue(endedFired)

        impl.play()
        assertTrue(impl.isPlaying())
        impl.pause()
        assertFalse(impl.isPlaying())

        impl.stop()
        assertFalse(impl.isPlaying())
        assertEquals(0.0, impl.getCurrentTimeSeconds())

        impl.dispose()
        analyzer.dispose()
    }

    @Test
    fun testWebVideoPlayerImplPreloadAndLoadedDataSeekedMediaReadyEvents() {
        val videoImpl = WebVideoPlayerImpl()
        assertEquals("auto", videoImpl.nativeWidget.getAttribute("preload"))

        var readyCallbacks = 0
        videoImpl.setOnMediaReady { readyCallbacks++ }

        // Dispatching loadeddata or seeked marks media ready
        videoImpl.nativeWidget.dispatchFromBrowser("loadeddata", null)
        assertTrue(videoImpl.isMediaReady())
        assertEquals("true", videoImpl.nativeWidget.getAttribute("data-media-ready"))
        assertEquals(1, readyCallbacks)

        // Reset via setSource with blank URL then test seeked event
        videoImpl.setSource("", 0.0, null)
        assertFalse(videoImpl.isMediaReady())
        videoImpl.nativeWidget.dispatchFromBrowser("seeked", null)
        assertTrue(videoImpl.isMediaReady())
        assertEquals(2, readyCallbacks)

        videoImpl.dispose()
    }

    @Test
    fun testWebMediaExtensionProviderAndRequestAnimationFrameBridge() {
        val prevFactory = GenericUI.activeFactory
        try {
            GenericUI.initialize(WebNativeFactory())
            WebMediaExtensionProvider.install()

            val audio: Player = AudioPlayer("https://example.com/song.ogg")
            val video: Player = VideoPlayer("https://example.com/clip.mp4")

            assertEquals("https://example.com/song.ogg", audio.sourceUrl)
            assertEquals("https://example.com/clip.mp4", video.sourceUrl)

            val rafHandle = WebBrowserDomBridge.requestAnimationFrame {}
            WebBrowserDomBridge.cancelAnimationFrame(rafHandle)

            audio.dispose()
            video.dispose()
        } finally {
            if (prevFactory != null) {
                GenericUI.initialize(prevFactory)
            }
        }
    }
}
