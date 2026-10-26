package com.nexustools.genericui.ext.media.web

import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.web.WebAudioAnalyzerImpl
import com.nexustools.genericui.web.WebAudioPlayerImpl
import com.nexustools.genericui.web.WebMediaSessionManagerImpl
import com.nexustools.genericui.web.WebVideoPlayerImpl

/**
 * Wires `:web`'s native HTML5 `<video>`, `<audio>`, and WebAudio `AnalyserNode` implementations
 * into [MediaExtension].
 */
object WebMediaExtensionProvider {
    fun install() {
        MediaExtension.registerProviders(
            videoPlayer = { WebVideoPlayerImpl() },
            audioPlayer = { WebAudioPlayerImpl() },
            audioAnalyzer = { fftSize ->
                WebAudioAnalyzerImpl().also { it.fftSize = fftSize }
            },
            mediaSessionManager = { WebMediaSessionManagerImpl() }
        )
    }
}
