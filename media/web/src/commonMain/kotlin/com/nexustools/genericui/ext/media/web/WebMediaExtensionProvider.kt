package com.nexustools.genericui.ext.media.web

import com.nexustools.genericui.ext.gpu.web.WebGpuProvider
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.web.WebAudioAnalyzerImpl
import com.nexustools.genericui.web.WebAudioPlayerImpl
import com.nexustools.genericui.web.WebMediaSessionManagerImpl
import com.nexustools.genericui.web.WebVideoPlayerImpl

/**
 * Wires `:web`'s HTML5 `<video>` / `<audio>` / WebAudio `AnalyserNode` implementations and
 * `:extensions:gpu:web` ([WebGpuProvider] WebGPU primary + WebGL 2 fallback) into [MediaExtension].
 */
object WebMediaExtensionProvider {
    fun install(
        enableWebGpu: Boolean = true,
        enableWebGl2: Boolean = true
    ) {
        WebGpuProvider.install(enableWebGpu = enableWebGpu, enableWebGl2 = enableWebGl2)

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
