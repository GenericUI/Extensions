package com.nexustools.genericui.ext.media.swing

import com.nexustools.genericui.ext.gpu.swing.SwingGpuProvider
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.swing.SwingAudioAnalyzerImpl
import com.nexustools.genericui.swing.SwingAudioPlayerImpl
import com.nexustools.genericui.swing.SwingMediaSessionManagerImpl
import com.nexustools.genericui.swing.SwingVideoPlayerImpl

/**
 * Wires JVM/Swing media playback and `:extensions:gpu:swing` ([SwingGpuProvider] LWJGL 3 Vulkan primary +
 * LWJGL 3 OpenGL ES / Google ANGLE fallback) into [MediaExtension].
 */
object SwingMediaExtensionProvider {
    fun install() {
        SwingGpuProvider.install()
        MediaExtension.registerProviders(
            videoPlayer = { SwingVideoPlayerImpl() },
            audioPlayer = { SwingAudioPlayerImpl() },
            audioAnalyzer = { fftSize ->
                SwingAudioAnalyzerImpl().also { it.fftSize = fftSize }
            },
            mediaSessionManager = { SwingMediaSessionManagerImpl() }
        )
    }
}
